package com.book.mark.service.impl;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageDestination;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageFitWidthDestination;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import com.book.mark.constants.PdfBookmarkConstants;
import com.book.mark.model.DataRequestPayload;
import com.book.mark.model.DocumentFields;
import com.book.mark.model.Documents;
import com.book.mark.model.ExternalIdStatus;
import com.book.mark.model.Pages;
import com.book.mark.model.PresenceFormNames;
import com.book.mark.model.RespPayload;
import com.book.mark.model.SubmissionId;
import com.book.mark.model.Transcription;
import com.book.mark.service.CreateBookmarksService;
import com.book.mark.util.Column;
import com.book.mark.util.PdfTableGenerator;
import com.book.mark.util.Table;
import com.book.mark.util.TableBuilder;
import com.book.mark.util.UnMarshaller;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;

@Service
public class CreateBookmarksServiceImpl implements CreateBookmarksService,PdfBookmarkConstants{

	Logger logger = LoggerFactory.getLogger(CreateBookmarksServiceImpl.class);

	static List<String> listData = new ArrayList<String>();

	/*@Value("${s3_bucket_pdf_files_location}")
	private StringBuilder s3BucketPdfFilesLocation;

	@Value("${Merged_Document_Path}")
	private String mergedDocumentPath;

	@Value("${Extract_FormNames_Path}")
	private String extractFormnamesPath;

	@Value("${Gen_CsvFile_Path}")
	private String genCsvFilePath;

	@Value("${Json_Files_Path}")
	private String jsonFilesPath;*/

	@Autowired
	private Environment env;


	public String readPayloadGenPresenceFormNames(DataRequestPayload dataRequestPayload,List<String> dataList) {

		String finalResponse = "";
		String finalResponse1 = "";
		String finalResponse2 = "";
		HashMap<String, Integer> mapIncrementedValues = new HashMap<String, Integer>();
		List<Documents> documents = dataRequestPayload.getDocuments();
		for(Documents docs : documents) {
			String layout_name = docs.getLayout_name();
			if(mapIncrementedValues.containsKey(layout_name)) {
				int lastIncrementedValue = mapIncrementedValues.get(layout_name);
				mapIncrementedValues.put(layout_name, lastIncrementedValue+1);
			} else {
				mapIncrementedValues.put(layout_name, 1);
			}
		}
		int p=0;
		int q=0;
		if(dataList != null) {
			for(int i=0;i<dataList.size();i++) {
				String givenLayoutName = dataList.get(i);
				if(mapIncrementedValues.containsKey(givenLayoutName)) {
					int count = mapIncrementedValues.get(givenLayoutName);
					if(p!=0)
						finalResponse1 += ", "+count+" - "+givenLayoutName+" layout forms ";
					else
						finalResponse1 += count+" - "+givenLayoutName+" layout forms ";
					p++;
				}else {
					if(q!=0)
						finalResponse2 += ", "+givenLayoutName+" layout not present ";
					else
						finalResponse2 += givenLayoutName+" layout not present ";
					q++;
				}
				/*for(Map.Entry<String, Integer> entry : mapIncrementedValues.entrySet()) {
					String layoutName = entry.getKey();
					int count = entry.getValue();
					finalResponse += count+" - "+layoutName+" layout forms.\n";
				}*/
			}
		}
		finalResponse = finalResponse1+"@@"+finalResponse2;

		List<Pages> unassignedPages = dataRequestPayload.getUnassigned_pages();
		if(unassignedPages != null && unassignedPages.size() > 0)
			finalResponse2 += " , Unassigned pages "+unassignedPages.size();

		finalResponse = finalResponse1+"@@"+finalResponse2;

		return finalResponse.trim();
	}

	public String readPayloadExtractionFormNames(DataRequestPayload dataRequestPayload,List<String> dataList) {

		String externalId = dataRequestPayload.getExternal_id();

		String s3BucketPdfFilesLocation = env.getProperty("s3_bucket_pdf_files_location");
		String mergedDocumentPath=env.getProperty("Merged_Document_Path");
		String extractFormnamesPath=env.getProperty("Extract_FormNames_Path");
		String genCsvFilePath=env.getProperty("Gen_CsvFile_Path");

		s3BucketPdfFilesLocation=s3BucketPdfFilesLocation.replace("externalId",externalId);
		mergedDocumentPath=mergedDocumentPath.replace("externalId",externalId);
		extractFormnamesPath=extractFormnamesPath.replace("externalId",externalId);
		genCsvFilePath=genCsvFilePath.replace("externalId",externalId);

		try {
			logger.info("JSON Parsing Started");
			String build_file_path = "";
			build_file_path += url_const;
			HashMap<String, Integer> mapIncrementedValues = new HashMap<String, Integer>();
			LinkedHashMap<String, List<String>> orderedMap = new LinkedHashMap<String, List<String>>();
			List<Documents> documents = dataRequestPayload.getDocuments();
			for(Documents docs : documents) {
				String layout_name = docs.getLayout_name();
				//ArrayList<String> layout_list = new ArrayList<String>();
				ArrayList<String> pages_list = new ArrayList<String>();
				List<Pages> pages = docs.getPages();
				for(Pages p : pages) {
					pages_list.add(String.valueOf(p.getFile_page_number())+"-"+String.valueOf(p.getLayout_page_number()));
					//layout_list.add(String.valueOf(p.getLayout_page_number()));
				}
				if(orderedMap.containsKey(layout_name)) {
					int lastIncrementedValue = mapIncrementedValues.get(layout_name);
					orderedMap.put(layout_name+"_"+(lastIncrementedValue+1), pages_list);
					mapIncrementedValues.put(layout_name, lastIncrementedValue+1);
					//orderedMap.put(layout_name+"_Duplicate", pages_list);
				}else {
					mapIncrementedValues.put(layout_name, 0);
					orderedMap.put(layout_name, pages_list);
				}
			}
			/** CODE ADDED FOR UNASSIGNED PAGES*/
			List<Pages> unassignedPages = dataRequestPayload.getUnassigned_pages();
			if(unassignedPages != null && unassignedPages.size() > 0) {
				ArrayList<String> unAssigPageList = new ArrayList<String>();
				for(int p=0;p<unassignedPages.size();p++) {
					Pages page = unassignedPages.get(p);
					unAssigPageList.add(String.valueOf(page.getFile_page_number())+"-DUMMYSTRING");
				}
				orderedMap.put("Unassigned", unAssigPageList);
			}
			/** CODE ADDED FOR UNASSIGNED PAGES*/

			File fileTemp = new File(String.valueOf(s3BucketPdfFilesLocation));
			String[] fileList1 = fileTemp.list();
			String getName = "";
			for(String name : fileList1) {
				getName += name;
			}
			String initialFileLocation = s3BucketPdfFilesLocation+getName;

			//PDDocument readDoc = PDDocument.load(new File(mergedDocPath));
			PDDocument readDoc = PDDocument.load(new File(initialFileLocation));


			for(Map.Entry<String, List<String>> entry: orderedMap.entrySet()) {
				String title = entry.getKey();
				List<String> page_list = entry.getValue();

				List<String> pagesList = new ArrayList<String>();
				List<String> layoutPageNumList = new ArrayList<String>();

				for(int index=0;index<page_list.size();index++) {
					String[] splitData = page_list.get(index).split("-");
					pagesList.add(String.valueOf(splitData[0]));//FILE_PAGE_NUMBER
					layoutPageNumList.add(String.valueOf(splitData[1]));//LAYOUT_PAGE_NUMBER
				}

				int pageNum = 1;//INDEX FOR ADDING PDF
				PDDocument writeDoc = new PDDocument();
				for(PDPage page : readDoc.getPages()) {
					if(pagesList.contains(String.valueOf(pageNum))) {
						writeDoc.addPage(page);
					}
					pageNum++;
				}
				writeDoc.save(extractFormnamesPath+title+"_"+externalId+".pdf");
				writeDoc.close();

				try {
					Region region = Region.US_GOV_WEST_1;
					AwsSessionCredentials awsCreds = AwsSessionCredentials.create(
							"AKIAXM6VM7LNUSMVFKRH",
							"FEkOmLrH+dp61BcSfMl+VfwuzxqlmMk7WlhnRxVE",
							"");
					S3Client s3 = S3Client.builder()
							.credentialsProvider(StaticCredentialsProvider.create(awsCreds))
							.region(region)
							.build();
					PutObjectRequest objectRequest = PutObjectRequest.builder()
							.bucket("ide-sandb-temp-microservice-bucket")
							.key("APIdocs/ExtractFormNames/"+title+"_"+externalId+".pdf")
							.build();
					s3.putObject(objectRequest, software.amazon.awssdk.core.sync.RequestBody.fromFile(Paths.get(extractFormnamesPath+title+"_"+externalId+".pdf")));

				}catch(Exception e) {
					logger.error("Exception occurred while AWS connection establishment", e);
				}

			}

			readDoc.close();

		} catch (Exception e) {
			logger.error("Exception in readPayloadExtractionFormNames() :: ",e);
		}



		return "Successfully extracted pdf forms";
	}

	private static final PDRectangle PAGE_SIZE = PDRectangle.A3;
	private static final float MARGIN = 20;
	private static final boolean IS_LANDSCAPE = false;

	private static final PDFont TEXT_FONT = PDType1Font.HELVETICA;
	private static final float FONT_SIZE = 10;

	private static final float ROW_HEIGHT = 15;
	private static final float CELL_MARGIN = 2;

	public void readPayloadGenPdfBookmark(DataRequestPayload dataRequestPayload) {


		try {
			String externalId = dataRequestPayload.getExternal_id();

			String s3BucketPdfFilesLocation = env.getProperty("s3_bucket_pdf_files_location");
			String mergedDocumentPath=env.getProperty("Merged_Document_Path");


			s3BucketPdfFilesLocation=s3BucketPdfFilesLocation.replace("externalId",externalId);
			mergedDocumentPath=mergedDocumentPath.replace("externalId",externalId);

			logger.info("JSON Parsing Started");

			HashMap<String, Integer> mapIncrementedValues = new HashMap<String, Integer>();

			LinkedHashMap<String, List<String>> orderedMap = new LinkedHashMap<String, List<String>>();
			List<Documents> documents = dataRequestPayload.getDocuments();
			for(Documents docs : documents) {
				String layout_name = docs.getLayout_name();
				ArrayList<String> pages_list = new ArrayList<String>();
				List<Pages> pages = docs.getPages();
				for(Pages p : pages) {
					pages_list.add(String.valueOf(p.getFile_page_number())+"-"+String.valueOf(p.getLayout_page_number()));
				}
				if(orderedMap.containsKey(layout_name)) {
					int lastIncrementedValue = mapIncrementedValues.get(layout_name);
					orderedMap.put(layout_name+"_"+(lastIncrementedValue+1), pages_list);
					mapIncrementedValues.put(layout_name, lastIncrementedValue+1);
				}else {
					mapIncrementedValues.put(layout_name, 0);
					orderedMap.put(layout_name, pages_list);
				}
			}
			List<Pages> unAssignedPages = dataRequestPayload.getUnassigned_pages();
			if(unAssignedPages != null && unAssignedPages.size()>0) {
				ArrayList<String> pages_list = new ArrayList<String>();
				for(Pages p : unAssignedPages) {
					pages_list.add(String.valueOf(p.getFile_page_number())+"-"+String.valueOf(p.getFile_page_number()));
				}
				orderedMap.put("Unassigned", pages_list);
				mapIncrementedValues.put("Unassigned",0);
			}
			logger.info("Required Mapping List - ",orderedMap);

			File fileTemp = new File(String.valueOf(s3BucketPdfFilesLocation));
			String[] fileList1 = fileTemp.list();
			String genName = "";
			for(String name : fileList1) {
				genName += name.replace(".pdf", "")+"_";
			}
			genName += externalId+"_BookMarked.pdf";
			String mergedDocPath = mergedDocumentPath+genName;

			logger.info("Read data for the pdf without bookmarks started at location "+mergedDocPath);
			PDDocument writeDoc = new PDDocument();
			PDDocument readDoc = PDDocument.load(new File(mergedDocPath));
			int noOfPages = readDoc.getNumberOfPages();
			logger.info("No of Pages observed in the pdf are - "+noOfPages);

			PDDocumentOutline outline = new PDDocumentOutline();
			writeDoc.getDocumentCatalog().setDocumentOutline(outline);
			PDOutlineItem pagesOutline = new PDOutlineItem();
			pagesOutline.setTitle("All Pages");
			outline.addLast(pagesOutline);

			//DYNAMIC PAGE LAYOUT DATA PAGE CODE CHANGES
			PDOutlineItem coverPageOut = new PDOutlineItem();
			coverPageOut.setTitle("Cover Page");
			PDPageDestination contextDest = new PDPageFitWidthDestination();

			//PDPage coverPage = new PDPage();

			int rowCnt = 0;
			for(Map.Entry<String, List<String>> entry: orderedMap.entrySet()) {
				if(entry.getKey() != null && mapIncrementedValues.containsKey(entry.getKey())) {
					rowCnt++;
				}
			}
			String[][] tableData = new String[rowCnt][5];

			int x=0,y=0;
			LinkedHashMap<String, List<String>> dummyMap = orderedMap;
			for(Map.Entry<String, List<String>> entry: orderedMap.entrySet()) {
				if(entry.getKey() != null && mapIncrementedValues.containsKey(entry.getKey())) {
					int layoutCount = mapIncrementedValues.get(entry.getKey());
					tableData[x][0] = entry.getKey();
					tableData[x][1] = String.valueOf(layoutCount+1);
					if(layoutCount == 0) {
						List<String> page_list = entry.getValue();
						tableData[x][2] = String.valueOf(Integer.parseInt(page_list.get(0).split("-")[0])+1);//+1 ADDED FOR INCORPORATING COVER PAGE CHANGE
						tableData[x][3] = String.valueOf(page_list.size());
					} else {
						String pages = "";
						String pagescount = "";
						List<String> page_list = entry.getValue();
						String keyValue = entry.getKey();
						for(int m=0;m<=layoutCount;m++) {
							if(m==0)
								page_list = dummyMap.get(keyValue);
							else
								page_list = dummyMap.get(keyValue+"_"+m);
							pages += ","+(Integer.parseInt(page_list.get(0).split("-")[0])+1);
							pagescount += ","+page_list.size();
						}
						tableData[x][2] = pages.replaceFirst(",", "");
						tableData[x][3] = pagescount.replaceFirst(",", "");
					}
					tableData[x][4] = "";//THIS IS REQUIRED AS IF NOT WE ARE UNABLE TO SEE LAST COLUMN
					x++;
				}
				
			}

			List<Column> columns = new ArrayList<Column>();
			columns.add(new Column("LayoutNames", 400));
			columns.add(new Column("LayoutCount", 100));
			columns.add(new Column("FirstPage", 100));
			columns.add(new Column("LayoutPageCount", 100));
			columns.add(new Column("", 0));//INTENTIONALLY KEPT THIS

			float tableHeight = IS_LANDSCAPE ? PAGE_SIZE.getWidth() - (2 * MARGIN) : PAGE_SIZE.getHeight() - (2 * MARGIN);

			Table table = new TableBuilder()
					.setCellMargin(CELL_MARGIN)
					.setColumns(columns)
					.setContent(tableData)
					.setHeight(tableHeight)
					.setNumberOfRows(tableData.length)
					.setRowHeight(ROW_HEIGHT)
					.setMargin(MARGIN)
					.setPageSize(PAGE_SIZE)
					.setLandscape(IS_LANDSCAPE)
					.setTextFont(TEXT_FONT)
					.setFontSize(FONT_SIZE)
					.build();

			PdfTableGenerator pdfTabGen = new PdfTableGenerator();
			PDPage coverPage = pdfTabGen.drawTable(writeDoc, table);
			//----------------------------------------------------------------------------------------------------------------

			PDPageDestination coverPageDest = new PDPageFitWidthDestination();
			coverPageDest.setPage(coverPage);
			PDOutlineItem coverPageBkmrk = new PDOutlineItem();
			coverPageBkmrk.setDestination(coverPageDest);
			coverPageBkmrk.setTitle("Context");
			coverPageOut.addLast(coverPageBkmrk);
			//writeDoc.addPage(coverPage);

			coverPageOut.openNode();
			pagesOutline.addLast(coverPageOut);
			//DYNAMIC PAGE LAYOUT DATA PAGE CODE CHANGES

			for(Map.Entry<String, List<String>> entry: orderedMap.entrySet()) {
				String title = entry.getKey();
				List<String> page_list = entry.getValue();

				PDOutlineItem bookmark = new PDOutlineItem();
				bookmark.setTitle(title);
				PDPageDestination dest = new PDPageFitWidthDestination();
				boolean flag = true;

				List<String> pagesList = new ArrayList<String>();
				List<String> layoutList = new ArrayList<String>();

				for(int index=0;index<page_list.size();index++) {
					String[] splitData = page_list.get(index).split("-");
					pagesList.add(String.valueOf(splitData[0]));//FILE_PAGE_NUMBER
					layoutList.add(String.valueOf(splitData[1]));//LAYOUT_PAGE_NUMBER
				}

				int pageNum = 1;//INDEX FOR ADDING PDF
				int layoutIndex = 0;//INDEX FOR LAYOUT
				for(PDPage page : readDoc.getPages()) {

					if(pagesList.contains(String.valueOf(pageNum))) {
						if(flag) {
							//dest.setPage(page);
							//bookmark.setDestination(dest);
							PDPageDestination dest2 = new PDPageFitWidthDestination();
							dest2.setPage(page);
							PDOutlineItem subbookmark1 = new PDOutlineItem();
							subbookmark1.setDestination(dest2);
							//subbookmark1.setTitle(String.valueOf(pageNum));
							subbookmark1.setTitle(layoutList.get(layoutIndex));
							bookmark.addLast(subbookmark1);

							flag = false;
							writeDoc.addPage(page);
						}else {
							//dest.setPage(page);
							PDPageDestination dest2 = new PDPageFitWidthDestination();
							dest2.setPage(page);
							PDOutlineItem subbookmark1 = new PDOutlineItem();
							subbookmark1.setDestination(dest2);
							//subbookmark1.setTitle(String.valueOf(pageNum));
							subbookmark1.setTitle(layoutList.get(layoutIndex));
							bookmark.addLast(subbookmark1);
							writeDoc.addPage(page);
						}
						layoutIndex++;
					}
					pageNum++;
				}
				bookmark.openNode();
				pagesOutline.addLast(bookmark);
				logger.info("Bookmark added for ["+title+"] with page id's "+page_list);
			}

			pagesOutline.openNode();
			outline.openNode();

			//String writeToPath = generateFileName(MergedDocumentPath);
			String writeToPath = mergedDocPath;

			writeDoc.save(new File(writeToPath));
			logger.info("Write Data to the pdf with bookmarks completed at location "+writeToPath);
			//System.out.println("Write Data to the pdf with bookmarks completed at location "+writePdfPathNew);
			writeDoc.close();
			readDoc.close();

			try {
				Region region = Region.US_GOV_WEST_1;
				AwsSessionCredentials awsCreds = AwsSessionCredentials.create(
						"AKIAXM6VM7LNUSMVFKRH",
						"FEkOmLrH+dp61BcSfMl+VfwuzxqlmMk7WlhnRxVE",
						"");
				S3Client s3 = S3Client.builder()
						.credentialsProvider(StaticCredentialsProvider.create(awsCreds))
						.region(region)
						.build();
				PutObjectRequest objectRequest = PutObjectRequest.builder()
						.bucket("ide-sandb-temp-microservice-bucket")
						.key("APIdocs/"+externalId+"/Bookmark/"+genName)
						.build();
				s3.putObject(objectRequest, software.amazon.awssdk.core.sync.RequestBody.fromFile(Paths.get(mergedDocumentPath+genName)));

			}catch(Exception e) {
				logger.error("Exception occurred while AWS connection establishment", e);
			}

			logger.info("JSON Parsing Finished");
		}catch(Exception e) {
			logger.error("Exception in readPayload() ", e);
		}
	}

	public void readPayloadGenCSV(DataRequestPayload dataRequestPayload) {
		String externalId = dataRequestPayload.getExternal_id();

		String genCsvFilePath=env.getProperty("Gen_CsvFile_Path");


		genCsvFilePath=genCsvFilePath.replace("externalId",externalId);

		try {
			logger.info("JSON Parsing Started");

			String modifiedCsvFileName = generateCSVFileName(genCsvFilePath, externalId);
			logger.info(modifiedCsvFileName);

			BufferedWriter bw = new BufferedWriter(new FileWriter(new File(modifiedCsvFileName)));
			List<Documents> documents = dataRequestPayload.getDocuments();
			for(Documents docs : documents) {
				String layout_name = docs.getLayout_name();
				List<DocumentFields> documentFields = docs.getDocument_fields();
				bw.write("LayoutName,"+layout_name+NEWLINE);
				for(DocumentFields docField : documentFields) {
					String name = docField.getName();
					Transcription transcription = docField.getTranscription();
					String normalized = transcription.getNormalized();
					//bw.write((name != null ? name : "")+","+(normalized != null ? normalized : "")+NEWLINE);
					bw.write(name+","+normalized+NEWLINE);
				}
				bw.write(NEWLINE);
			}
			bw.close();
			try {
				Region region = Region.US_GOV_WEST_1;
				AwsSessionCredentials awsCreds = AwsSessionCredentials.create(
						"AKIAXM6VM7LNUSMVFKRH",
						"FEkOmLrH+dp61BcSfMl+VfwuzxqlmMk7WlhnRxVE",
						"");
				S3Client s3 = S3Client.builder()
						.credentialsProvider(StaticCredentialsProvider.create(awsCreds))
						.region(region)
						.build();
				PutObjectRequest objectRequest = PutObjectRequest.builder()
						.bucket("ide-sandb-temp-microservice-bucket")
						.key("APIdocs/"+externalId+"/CSVFiles/CsvData_"+externalId+".csv")
						.build();
				s3.putObject(objectRequest, software.amazon.awssdk.core.sync.RequestBody.fromFile(Paths.get(genCsvFilePath+"CsvData_"+externalId+".csv")));

			}catch(Exception e) {
				logger.error("Exception occurred while AWS connection establishment", e);
			}
			logger.info("JSON Parsing Finished");
		}catch(Exception e) {
			logger.error("Exception in readPayload() ", e);
		}
	}

	public String genBookmarks(String submission_id, String todoItem) {
		boolean flag = false;
		try {
			//39 VALUE MAKE IT AS PARAM(submission_id)
			//String url="http://13.72.109.50/api/v5/submissions/39?flat=false";
			if(submission_id != null && !submission_id.equals("") && !submission_id.equalsIgnoreCase("null")) {
				String build_url = hostName_genSubId+submission_id+urlAppender;
				logger.info("Building url is done for given submissionId as "+build_url);

				HttpHeaders headers = new HttpHeaders();
				//headers.set("Authorization", "Token bca5a45db3ad094449bb6569a69f705ba2a8a5c3");
				headers.set("Authorization", Authorization_Token);

				HttpEntity<String> httpReqEntity = new HttpEntity<String>(headers);
				RestTemplate restTemplate = new RestTemplate();

				/*if(todoItem.equalsIgnoreCase(generateCSV) || todoItem.equalsIgnoreCase(genPdfgenCsv))
					TimeUnit.SECONDS.sleep(90);*/
				ResponseEntity<String> response = restTemplate.exchange(build_url, HttpMethod.GET, httpReqEntity, String.class);
				if(response != null) {
					logger.info("Parsing the response recieved from "+build_url);
					UnMarshaller unMarshaller = new UnMarshaller();
					DataRequestPayload dataRequestPayload = unMarshaller.unmarshall(response.getBody());

					/*String build_file_path = "http://13.72.109.50";
					List<SubmissionFiles> submission_files = dataRequestPayload.getSubmission_files();
					if(submission_files != null && submission_files.size()>0) {
						build_file_path += submission_files.get(0).getUrl();
					}
					System.out.println("Path to download URL "+build_file_path);*/

					if(dataRequestPayload != null) {

						try {
							Region region = Region.US_GOV_WEST_1;
							AwsSessionCredentials awsCreds = AwsSessionCredentials.create(
									"AKIAXM6VM7LNUSMVFKRH",
									"FEkOmLrH+dp61BcSfMl+VfwuzxqlmMk7WlhnRxVE",
									"");
							S3Client s3 = S3Client.builder()
									.credentialsProvider(StaticCredentialsProvider.create(awsCreds))
									.region(region)
									.build();
							PutObjectRequest objectRequest = PutObjectRequest.builder()
									.bucket("ide-sandb-temp-microservice-bucket")
									.key("APIdocs/Json_Data/"+dataRequestPayload.getExternal_id()+".json")
									.build();
							s3.putObject(objectRequest, software.amazon.awssdk.core.sync.RequestBody.fromByteBuffer(writeJsonDatatoS3(dataRequestPayload.getExternal_id(), response.getBody())));
						}catch(Exception e) {
							logger.error("Exception occurred while AWS connection establishment", e);
						}

						if(todoItem != null && todoItem.equalsIgnoreCase(bookMarkPdf)) {
							readPayloadGenPdfBookmark(dataRequestPayload);
						}else if(todoItem != null && todoItem.equalsIgnoreCase(generateCSV)){
							readPayloadGenCSV(dataRequestPayload);
						}else if(todoItem != null && todoItem.equalsIgnoreCase(genPdfgenCsv)){
							readPayloadGenPdfBookmark(dataRequestPayload);
							readPayloadGenCSV(dataRequestPayload);
						}else if(todoItem != null && todoItem.equalsIgnoreCase(presenceFormNames)) {
							return readPayloadGenPresenceFormNames(dataRequestPayload,listData);
						}else if(todoItem != null && todoItem.equalsIgnoreCase(extractionFormNames)) {
							return readPayloadExtractionFormNames(dataRequestPayload,listData);
						}
						flag = true;//NEED THIS FLAG TO RETURN SUCCESS OR FAILURE MESSAGE
					}else {
						logger.error("Expected JSON Object from response is null, cannot proceed further.");
					}
				}else {
					logger.error("Response from "+build_url+" is null, cannot proceed further.");
				}
			}else {
				logger.error("Submission Id is null, cannot proceed further.");
			}
		}catch(Exception e) {
			logger.error("Exception", e);
		}
		if(flag)
			return success_message;
		else
			return "Issue Occurred while Generating Bookmark";
	}

	private ByteBuffer getRandomByteBuffer(int size) throws IOException {
		byte[] b = new byte[size];
		new Random().nextBytes(b);
		return ByteBuffer.wrap(b);
	}

	private ByteBuffer writeJsonDatatoS3(String externalId,String respData) throws IOException {
		//String path = jsonFilesPath+externalId+".json";
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		JsonParser jp = new JsonParser();
		JsonElement je = jp.parse(respData);
		String prettyJsonString = gson.toJson(je);
		byte[] bytes = prettyJsonString.getBytes();
		//byte[] bytes = Files.readAllBytes(Paths.get("C:\\Users\\Administrator\\Documents\\GenBookmarks\\Json_Data\\Sam10025.json"));
		return ByteBuffer.wrap(bytes);
	}

	private String generateFileName(String fileReadLocation) {
		fileReadLocation = fileReadLocation.replace("MergeMultipleDocs", "MergedDoc");
		String pattern = "MMddyyyy";
		SimpleDateFormat simpleDateFormat = new SimpleDateFormat(pattern);
		String date = simpleDateFormat.format(new Date());

		String fileName = new File(fileReadLocation).getName();
		String[] name = fileName.split("\\.");
		//String build_fileName = name[0].concat("_"+date.toString());
		String build_fileName = name[0].concat("_Bookmarked");
		String newPath = fileReadLocation.replace(fileName, "");
		return newPath+build_fileName+".pdf";
	}

	private String generateCSVFileName(String fileReadLocation,String extId) {
		/*String pattern = "MMddyyyy";
		SimpleDateFormat simpleDateFormat = new SimpleDateFormat(pattern);
		String date = simpleDateFormat.format(new Date());*/

		String fileName = new File(fileReadLocation).getName();
		//String[] name = fileName.split("\\.");
		//String build_fileName = name[0].concat("_"+date.toString());
		//String build_fileName = name[0].concat("_"+extId);
		String build_fileName = "CsvData_"+extId+".csv";
		//String newPath = fileReadLocation.replace(fileName, "");
		return fileReadLocation+build_fileName;
	}

	public void genSubmissionId() {

		String externalId=null;
		String s3BucketPdfFilesLocation = env.getProperty("s3_bucket_pdf_files_location");


		s3BucketPdfFilesLocation=s3BucketPdfFilesLocation.replace("externalId",externalId);

		try {
			HttpHeaders headers = new HttpHeaders();
			headers.setContentType(MediaType.MULTIPART_FORM_DATA);
			//headers.set("Authorization", "Token bca5a45db3ad094449bb6569a69f705ba2a8a5c3");
			headers.set("Authorization", Authorization_Token);
			logger.info("HttpHeader values added...");

			MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
			List<Resource> resourceList = getUserFileResourceList(String.valueOf(s3BucketPdfFilesLocation));
			for(Resource resource : resourceList) {
				body.add("file", resource);
			}
			logger.info("Files dynamically added from location - "+s3BucketPdfFilesLocation);

			HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
			RestTemplate restTemplate = new RestTemplate();
			logger.info("Rest API Call Started...");
			ResponseEntity<String> response = restTemplate.postForEntity(hostName, requestEntity, String.class);
			logger.info("Rest API Call Finished...");
			if(response != null) {
				if(response.getBody() != null) {
					UnMarshaller unMarshaller = new UnMarshaller();
					SubmissionId submissionId = unMarshaller.unMarshallSubmissionId(response.getBody());
					logger.info("Generated Submission ID value is "+submissionId.getSubmissionId());
					//genBookmarks(submissionId.getSubmissionId());
				} else {
					logger.error("API Response BODY from URL - "+hostName+" is null, cannot proceed further.");
				}
			} else {
				logger.error("API Response from URL - "+hostName+" is null, cannot proceed further.");
			}
		}catch(Exception e) {
			logger.error("Exception", e);
		}
	}

	public ResponseEntity<RespPayload> genCsvByExternalId(String extId){
		boolean isExecutionDone = false;
		RespPayload respPayload = new RespPayload();
		try {
			ResponseEntity<ExternalIdStatus> respObj = genStateByExternalId(extId);
			if(respObj != null) {
				ExternalIdStatus extObj = (ExternalIdStatus)respObj.getBody();
				if(extObj == null || extObj.getState() == null) {
					respPayload.setMessage("External Id Not Found");
					return new ResponseEntity<RespPayload>(respPayload, HttpStatus.OK);
				}
				if(extObj != null) {
					if(extObj.getState() != null && !extObj.getState().equalsIgnoreCase(COMPLETE)) {
						respPayload.setMessage("Submission State is not Complete but found "+extObj.getState());
						return new ResponseEntity<RespPayload>(respPayload, HttpStatus.OK);
					}
					genBookmarks(String.valueOf(extObj.getId()), generateCSV);
					isExecutionDone = true;
				}
			}
			if(isExecutionDone)
				respPayload.setMessage("Success");
			else
				respPayload.setMessage("Failed");
		}catch(Exception e) {
			logger.error("Exception occurred while genCsvByExternalId: ",e);
		}
		return new ResponseEntity<RespPayload>(respPayload, HttpStatus.OK);
	}

	public ResponseEntity<ExternalIdStatus> genStateByExternalId(String ext_id) {
		ExternalIdStatus extObj = new ExternalIdStatus();
		RespPayload respPayload = new RespPayload();
		boolean isErrorOccurred = false;
		try {
			String build_url = hostName_external+ext_id;
			HttpHeaders headers = new HttpHeaders();
			//headers.set("Authorization", "Token bca5a45db3ad094449bb6569a69f705ba2a8a5c3");
			headers.set("Authorization", Authorization_Token);
			logger.info("HttpHeader values added...");

			Map<String, String> body = new HashMap<String, String>();
			//body.put("Authorization", "Token bca5a45db3ad094449bb6569a69f705ba2a8a5c3");
			headers.set("Authorization", Authorization_Token);
			HttpEntity<String> requestEntity = new HttpEntity<>(headers);
			RestTemplate restTemplate = new RestTemplate();
			ResponseEntity<?> response = restTemplate.exchange(build_url, HttpMethod.GET, requestEntity, String.class);
			if(response != null) {
				String respBody = (String)response.getBody();
				if(respBody != null) {
					UnMarshaller unMarshaller = new UnMarshaller();
					extObj = unMarshaller.unMarshallExternalIdResponse(respBody);
					if(extObj != null) {
						String url = extObj.getSupervision_url();
						if(url == null) {
							extObj.setSupervision_url(url);
						}
						else
						{
							url = url_const_getstatus+url;
							extObj.setSupervision_url(url);
						}
						logger.info(extObj.toString());
					} else {
						logger.error("unMarshallExternalIdResponse is null, cannot proceed further.");
					}
				}else {
					logger.error("API Response BODY from URL - "+build_url+" is null, cannot proceed further.");
				}
			}else {
				logger.error("API Response from URL - "+build_url+" is null, cannot proceed further.");
			}
		}catch(Exception e) {
			isErrorOccurred = true;
			logger.error("Exception occurred in genStateByExternalId() ", e);
		}
		if(isErrorOccurred)
			return new ResponseEntity<ExternalIdStatus>(extObj, HttpStatus.OK);
		else
			 
		    return new ResponseEntity<ExternalIdStatus>(extObj, HttpStatus.OK);
	}

	public static List<Resource> getUserFileResourceList(String location) throws IOException {
		List<Resource> resourceList = new ArrayList<Resource>();
		File file = new File(location);
		String[] fileList = file.list();
		for(String name : fileList) {
			System.out.println(name);
			java.io.File file1 = new java.io.File(location+name);
			resourceList.add(new FileSystemResource(file1));
		}
		return resourceList;
	}

	@SuppressWarnings("deprecation")
	public ResponseEntity<RespPayload> getSubmisIdGenBookmark(String extId,String todoItem){

		String s3BucketPdfFilesLocation = env.getProperty("s3_bucket_pdf_files_location");
		String mergedDocumentPath=env.getProperty("Merged_Document_Path");
		String extractFormnamesPath=env.getProperty("Extract_FormNames_Path");
		String genCsvFilePath=env.getProperty("Gen_CsvFile_Path");

		s3BucketPdfFilesLocation=s3BucketPdfFilesLocation.replace("externalId",extId);
		mergedDocumentPath=mergedDocumentPath.replace("externalId",extId);
		extractFormnamesPath=extractFormnamesPath.replace("externalId",extId);
		genCsvFilePath=genCsvFilePath.replace("externalId",extId);

		try {

			setDirectories(extId);

			Region region = Region.US_GOV_WEST_1;

			AwsSessionCredentials awsCreds = AwsSessionCredentials.create(
					"AKIAXM6VM7LNUSMVFKRH", "FEkOmLrH+dp61BcSfMl+VfwuzxqlmMk7WlhnRxVE", "");

			S3Client s3 = S3Client.builder()
					.credentialsProvider(StaticCredentialsProvider.create(awsCreds))
					.region(region)
					.build();

			ListObjectsRequest req = ListObjectsRequest.builder().bucket("ide-sandb-temp-microservice-bucket").prefix("APIdocs/SourceFiles/").build();
			ListObjectsResponse response1 = s3.listObjects(req);
			List<S3Object> listOfS3Obj = response1.contents();//List of Data
			for(int i=0;i<listOfS3Obj.size();i++) {
				if(i!=0) {//SKIPPING THE FIRST ITERATION AS IT DOES NOT HAVE ANY FILE DATA
					S3Object s3Object= listOfS3Obj.get(i);
					String s3objKey = s3Object.key();
					String fileName = s3objKey.substring(s3objKey.lastIndexOf("/"));

					GetObjectRequest getObjectRequest = GetObjectRequest.builder()
							.bucket("ide-sandb-temp-microservice-bucket")
							.key("APIdocs/SourceFiles"+fileName)
							.build();
					File f= new File(s3BucketPdfFilesLocation+fileName);
					if(f.exists()) {
						f.delete();
					}

					s3.getObject(getObjectRequest, Paths.get(s3BucketPdfFilesLocation+fileName));

					f= new File(s3BucketPdfFilesLocation+fileName);
					if(!f.exists()) {
						throw new RuntimeException(" File does not exists");
					}
				}
			}

			File fileTemp = new File(String.valueOf(s3BucketPdfFilesLocation));
			String[] fileList1 = fileTemp.list();
			String genName = "";
			for(String name : fileList1) {
				if(name != null && name.contains(".pdf"))
					genName += name.replace(".pdf", "")+"_";
			}
			genName += extId+"_BookMarked.pdf";
			String mergedDocPath = mergedDocumentPath+genName;

			File file = new File(String.valueOf(s3BucketPdfFilesLocation));
			String[] fileList = file.list();
			//if(fileList != null && fileList.length>1) {
			PDFMergerUtility PDFMerger = new PDFMergerUtility();
			PDFMerger.setDestinationFileName(mergedDocPath);
			for(String name : fileList) {
				java.io.File file1 = new java.io.File(s3BucketPdfFilesLocation+name);
				PDDocument doc = PDDocument.load(file1);
				PDFMerger.addSource(file1);
				doc.close();
			}
			PDFMerger.mergeDocuments();
			//}


			MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
			//body.add("file", new FileSystemResource(s3BucketPdfFilesLocation+ "/HyperScience_Testing_0929_10052020.pdf"));
			body.add("file", new FileSystemResource(mergedDocPath));

			//body.add("file", new FileSystemResource(new java.io.File("C:/Users/Administrator/Documents/GenBookmarks/NewTestFile/HyperScience_Testing_0929_10052020.pdf")));
			body.add("external_id", extId);

			HttpHeaders headers = new HttpHeaders();
			headers.setContentType(MediaType.MULTIPART_FORM_DATA);
			headers.set("Authorization", Authorization_Token);
			HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
			RestTemplate restTemplate = new RestTemplate();
			logger.info("Rest API Call Started...");
			ResponseEntity<String> response = null;

			response = restTemplate.postForEntity(hostName+"?flat=false", requestEntity, String.class);

			logger.info("Rest API Call Finished...");
			String responseMessage = "";
			if(response != null) {
				if(response.getBody() != null) {
					UnMarshaller unMarshaller = new UnMarshaller();
					SubmissionId submissionId = unMarshaller.unMarshallSubmissionId(response.getBody());
					logger.info("Generated Submission ID value is "+submissionId.getSubmissionId());
					TimeUnit.SECONDS.sleep(PDF_TIMEOUT);
					String externalId = extId;
					responseMessage = genBookmarks(submissionId.getSubmissionId(),todoItem);
				} else {
					logger.error("API Response BODY from URL - "+hostName+" is null, cannot proceed further.");
				}
			} else {
				logger.error("API Response from URL - "+hostName+" is null, cannot proceed further.");
			}
			RespPayload respPayload = new RespPayload();
			respPayload.setMessage(responseMessage);
			return new ResponseEntity<RespPayload>(respPayload, HttpStatus.OK);

		}catch(HttpClientErrorException hceex) {
			logger.error(hceex.getMessage());
			if(hceex.getMessage() != null) {
				String[] message = hceex.getMessage().toString().split("\"submission_id\"");
				//UnMarshaller unMarshaller = new UnMarshaller();
				//ErrMsg errMsg=unMarshaller.unMarshallErrMessage(message);
				RespPayload respPayload = new RespPayload();
				respPayload.setMessage("A submission with this external identifier already exists"+message[1].replace("}", "").replace("]", "").replace("\"", ""));
				return new ResponseEntity<RespPayload>(respPayload, HttpStatus.CONFLICT);
			}
		}catch(Exception ex) {
			logger.error("Exception occurred while AWS connection establishment", ex);
			RespPayload respPayload = new RespPayload();
			respPayload.setMessage("Error occurred");
			return new ResponseEntity<RespPayload>(respPayload, HttpStatus.CONFLICT);
		}

		RespPayload respPayload = new RespPayload();
		respPayload.setMessage("Error occurred");
		return new ResponseEntity<RespPayload>(respPayload, HttpStatus.CONFLICT);

	}

	public String genPresenceOrExtractionFormNames(PresenceFormNames presenceFormNames,String todo) {
		String finalResp = "";
		ExternalIdStatus extObj = new ExternalIdStatus();
		try {
			String build_url = hostName_external+presenceFormNames.getExternalId();
			HttpHeaders headers = new HttpHeaders();
			headers.set("Authorization", "Token bca5a45db3ad094449bb6569a69f705ba2a8a5c3");
			logger.info("HttpHeader values added...");

			Map<String, String> body = new HashMap<String, String>();
			body.put("Authorization", "Token bca5a45db3ad094449bb6569a69f705ba2a8a5c3");
			HttpEntity<String> requestEntity = new HttpEntity<>(headers);
			RestTemplate restTemplate = new RestTemplate();
			ResponseEntity<?> response = restTemplate.exchange(build_url, HttpMethod.GET, requestEntity, String.class);
			if(response != null) {
				String respBody = (String)response.getBody();
				if(respBody != null) {
					UnMarshaller unMarshaller = new UnMarshaller();
					extObj = unMarshaller.unMarshallExternalIdResponse(respBody);
					if(extObj != null) {
						logger.info(extObj.toString());
						listData = presenceFormNames.getLayoutNames();
						String	externalId = presenceFormNames.getExternalId();
						finalResp = genBookmarks(String.valueOf(extObj.getId()), todo);
					} else {
						logger.error("unMarshallExternalIdResponse is null, cannot proceed further.");
					}
				}else {
					logger.error("API Response BODY from URL - "+build_url+" is null, cannot proceed further.");
				}
			}else {
				logger.error("API Response from URL - "+build_url+" is null, cannot proceed further.");
			}
		}catch(Exception e) {
			finalResp = "ExternalId Does Not Exist";
			logger.error("Exception occurred in genStateByExternalId() ", e);
		}
		return finalResp;
	}

	private void setDirectories(String externalId) {

		String s3BucketPdfFilesLocation = env.getProperty("s3_bucket_pdf_files_location");
		String mergedDocumentPath=env.getProperty("Merged_Document_Path");
		String extractFormnamesPath=env.getProperty("Extract_FormNames_Path");
		String genCsvFilePath=env.getProperty("Gen_CsvFile_Path");


		s3BucketPdfFilesLocation=s3BucketPdfFilesLocation.replace("externalId",externalId);
		mergedDocumentPath=mergedDocumentPath.replace("externalId",externalId);
		extractFormnamesPath=extractFormnamesPath.replace("externalId",externalId);
		genCsvFilePath=genCsvFilePath.replace("externalId",externalId);

		try {
			Files.createDirectories(Paths.get(String.valueOf(s3BucketPdfFilesLocation)));
			Files.createDirectories(Paths.get(mergedDocumentPath));
			Files.createDirectories(Paths.get(extractFormnamesPath));
			Files.createDirectories(Paths.get(genCsvFilePath));
		} catch (IOException e) {
			logger.error("Cannot create directories - " + e);
		}
	}

}