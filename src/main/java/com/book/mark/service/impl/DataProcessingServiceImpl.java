package com.book.mark.service.impl;

import com.book.mark.constants.PdfBookmarkConstants;
import com.book.mark.model.*;
import com.book.mark.service.CreateBookmarksService;
import com.book.mark.service.DataProcessingService;
import com.book.mark.util.*;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import javax.servlet.http.HttpServletResponse;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class DataProcessingServiceImpl implements DataProcessingService,PdfBookmarkConstants {

	Logger logger = LoggerFactory.getLogger(DataProcessingServiceImpl.class);

	static List<String> listData = new ArrayList<String>();

	String presenceFormNamesOutput = "";

	/*@Value("${Merged_Document_Path}")
	private String s3BucketPdfFilesLocation;

	
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

	static boolean genSubmissionIdDone = false;

	@Autowired
	private CreateBookmarksService createBookmarkService;



	private S3Client initiateAWSConnection() {
		S3Client s3 = null;
		try {
			Region region = Region.US_GOV_WEST_1;
			AwsSessionCredentials awsCreds = AwsSessionCredentials.create(ACCESS_KEY, SECRET_KEY, SESSION_TOKEN);
			s3 = S3Client.builder()
					.credentialsProvider(StaticCredentialsProvider.create(awsCreds))
					.region(region)
					.build();
		}catch(Exception e) {
			logger.error("Error Occurred while initating AWS Connection ", e);
		}
		return s3;
	}

	public ResponseEntity processData(MultipartFile file, DataProcessingReq dataProcReq) {
		DataProcessingResp dataProcessingResp = new DataProcessingResp();
		//Map<String,List<String>> data = new HashMap<String, List<String>>();
		List<String> list = new ArrayList<String>();
		try {


			String hyperScienceResponse = getSubmissionId(file,dataProcReq.getExternalId(),dataProcReq.getAcceptPartialResults());
			if(hyperScienceResponse == null || (hyperScienceResponse != null && hyperScienceResponse.equalsIgnoreCase("Failure")) ||
					(hyperScienceResponse != null && hyperScienceResponse.startsWith("A submission"))) {
				dataProcessingResp.setExternalId(dataProcReq.getExternalId());
				list.add(hyperScienceResponse);
				dataProcessingResp.setMessage(list);
				return new ResponseEntity(dataProcessingResp, HttpStatus.OK);
			}
			UnMarshaller unMarshaller = new UnMarshaller();
			SubmissionId submissionId = unMarshaller.unMarshallSubmissionId(hyperScienceResponse);
			logger.info("Generated Submission ID value is "+submissionId.getSubmissionId());
			
			if(dataProcReq.getAcceptPartialResults().equalsIgnoreCase("true")) {
				TimeUnit.SECONDS.sleep(MACHINE_ONLY_PDF_TIMEOUT);
			}else {
				TimeUnit.SECONDS.sleep(PDF_TIMEOUT);
			}
			
			

			String build_url = hostName_genSubId+submissionId.getSubmissionId()+urlAppender;
			logger.info("Building url is done for given submissionId as "+build_url);

			HttpHeaders headers = new HttpHeaders();
			headers.set("Authorization", Authorization_Token);

			HttpEntity<String> httpReqEntity = new HttpEntity<String>(headers);
			RestTemplate restTemplate = new RestTemplate();

			/*if(dataProcReq.getGenerateCsv() != null && dataProcReq.getGenerateCsv().equalsIgnoreCase("true"))
				TimeUnit.SECONDS.sleep(90);//FOR GENCSV WE NEED TO WAIT UNTIL JSON IS FORMED PROPERLY
			*/
			ResponseEntity<String> response = restTemplate.exchange(build_url, HttpMethod.GET, httpReqEntity, String.class);

			DataRequestPayload dataRequestPayload = null;
			if(response != null) {
				UnMarshaller unMarshaller1 = new UnMarshaller();
				dataRequestPayload = unMarshaller1.unmarshall(response.getBody());
				logger.info("Response from "+build_url+" parsed successfully");
			}

			boolean forJsonOrder = false;
			try {
				S3Client s3 = initiateAWSConnection();

				PutObjectRequest objectRequest = PutObjectRequest.builder()
						.bucket(AWS_BUCKET_NAME)
						.key("APIdocs/"+dataProcReq.getExternalId()+"/Json_Data/"+dataProcReq.getExternalId()+".json")
						.build();
				s3.putObject(objectRequest, software.amazon.awssdk.core.sync.RequestBody.fromByteBuffer(writeJsonDatatoS3(dataProcReq.getExternalId(), response.getBody())));
				forJsonOrder = true;
			}catch(Exception e) {
				logger.error("Exception occurred while AWS connection establishment", e);
			}

			String pdfResp = "";
			String csvResp = "";
			String prsncFormNames = "";
			String extractFormNames = "";

			if(dataProcReq.getGenerateBookmarkedPdf() != null && dataProcReq.getGenerateBookmarkedPdf().equalsIgnoreCase("true")) {
				pdfResp = executeTask(dataRequestPayload,"BookMarkPdf");
				if(pdfResp.equalsIgnoreCase(SUCCESS))
					list.add("Pdf Bookmarked Successfully");
				else
					list.add("Error while Pdf Bookmark");
			}
			if(dataProcReq.getGenerateCsv() != null && dataProcReq.getGenerateCsv().equalsIgnoreCase("true")) {
				csvResp = executeTask(dataRequestPayload,"GenCSV");
				if(csvResp.equalsIgnoreCase(SUCCESS))
					list.add("CSV Generated Successfully");
				else
					list.add("Error while CSV Generation");
			}
			if(forJsonOrder)
				list.add("Json File Created successfully");
			else
				list.add("Error while Json File Creation");
			if(dataProcReq.getPresenceFormNames() != null && dataProcReq.getPresenceFormNames().size() > 0) {
				listData = dataProcReq.getPresenceFormNames();
				prsncFormNames = executeTask(dataRequestPayload,"presenceFormNames");
				if(prsncFormNames.equalsIgnoreCase(SUCCESS)) {
					String[] ar = presenceFormNamesOutput.split("@@");

					if(ar != null && ar.length>0)
						list.add("Valid Layout : "+ar[0]);
					else
						list.add("Valid Layout : ");
					if(ar != null && ar.length>1)
						list.add("InValid Layout : "+ar[1]);
					else
						list.add("InValid Layout : ");
					//list.add(presenceFormNamesOutput);
				}else
					list.add("Error while Presence Form Names");
			}
			if(dataProcReq.getExtractionFormNames() != null && dataProcReq.getExtractionFormNames().size() > 0) {
				String	externalId = dataProcReq.getExternalId();
				listData = dataProcReq.getExtractionFormNames();
				extractFormNames = executeTask(dataRequestPayload,"extractionFormNames");
				if(extractFormNames.equalsIgnoreCase(SUCCESS))
					list.add("Successfully extracted pdf forms");
				else
					list.add("Error while Extract Form Names");
			}

			dataProcessingResp.setExternalId(dataProcReq.getExternalId());
			dataProcessingResp.setMessage(list);

		}catch(Exception e) {
			logger.error("Error occurred while processing data ", e);
		}
		return new ResponseEntity(dataProcessingResp, HttpStatus.OK);
	}

	public String getSubmissionId( MultipartFile uploadfile,String extId,String machineOnly){

		String s3BucketPdfFilesLocation = env.getProperty("s3_bucket_pdf_files_location");
		String mergedDocumentPath=env.getProperty("Merged_Document_Path");

		s3BucketPdfFilesLocation=s3BucketPdfFilesLocation.replace("externalId",extId);
		mergedDocumentPath=mergedDocumentPath.replace("externalId",extId);

		logger.info("Generating Submission Id Started");
		try {
			setDirectories(extId);
			/*S3Client s3 = initiateAWSConnection();

			ListObjectsRequest req = ListObjectsRequest.builder().bucket(AWS_BUCKET_NAME).prefix("APIdocs/SourceFiles/").build();
			ListObjectsResponse response1 = s3.listObjects(req);
			List<S3Object> listOfS3Obj = response1.contents();//List of Data
			for(int i=0;i<listOfS3Obj.size();i++) {
				if(i!=0) {//SKIPPING THE FIRST ITERATION AS IT DOES NOT HAVE ANY FILE DATA
					S3Object s3Object= listOfS3Obj.get(i);
					String s3objKey = s3Object.key();
					String fileName = s3objKey.substring(s3objKey.lastIndexOf("/"));

					GetObjectRequest getObjectRequest = GetObjectRequest.builder()
							.bucket(AWS_BUCKET_NAME)
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
			}*/

			File f= new File(s3BucketPdfFilesLocation+uploadfile.getOriginalFilename());

			if(!f.exists()){
				f.createNewFile();
			}

			Path path = Paths.get(s3BucketPdfFilesLocation + uploadfile.getOriginalFilename());
			Files.copy(uploadfile.getInputStream(),path,StandardCopyOption.REPLACE_EXISTING);

			if(!f.exists()) {
				throw new RuntimeException(" File does not exists");
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
			body.add("file", new FileSystemResource(mergedDocPath));
			body.add("external_id", extId);
			body.add("machine_only", machineOnly);

			HttpHeaders headers = new HttpHeaders();
			headers.setContentType(MediaType.MULTIPART_FORM_DATA);
			headers.set("Authorization", Authorization_Token);
			HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
			RestTemplate restTemplate = new RestTemplate();
			logger.info("Rest API Call Started...");
			ResponseEntity<String> response = null;
			logger.info("Request Params while making request to HS "+body.toString());
			response = restTemplate.postForEntity(hostName+"?flat=false", requestEntity, String.class);

			logger.info("Rest API Call Finished...");
			String responseStr = "";
			if(response != null) {
				if(response.getBody() != null) {
					responseStr = response.getBody();
				} else {
					responseStr = "Failure";
					logger.error("API Response BODY from URL - "+hostName+" is null, cannot proceed further.");
				}
			} else {
				responseStr = "Failure";
				logger.error("API Response from URL - "+hostName+" is null, cannot proceed further.");
			}

			logger.info("Generating Submission Id Finished");
			return responseStr;

		}catch(HttpClientErrorException hceex) {
			logger.error(hceex.getMessage());
			logger.info("the path for the s3 is  ",s3BucketPdfFilesLocation);

			if(hceex.getMessage() != null) {
				String[] message = hceex.getMessage().toString().split("\"submission_id\"");
				return "A submission with this external identifier already exists"+message[1].replace("}", "").replace("]", "").replace("\"", "");
			}
			return "Failure";
		}catch(Exception ex) {
			logger.error("Exception occurred while generating submission id ", ex);
			return "Failure";
		}

	}

	public String executeTask(DataRequestPayload dataRequestPayload, String todoItem) {
		boolean flag = false;
		try {
			/*String build_file_path = "http://13.72.109.50";
			List<SubmissionFiles> submission_files = dataRequestPayload.getSubmission_files();
			if(submission_files != null && submission_files.size()>0) {
				build_file_path += submission_files.get(0).getUrl();
			}
			System.out.println("Path to download URL "+build_file_path);*/

			if(dataRequestPayload != null) {
				if(todoItem != null && todoItem.equalsIgnoreCase(bookMarkPdf)) {
					createBookmarkService.readPayloadGenPdfBookmark(dataRequestPayload);
				}else if(todoItem != null && todoItem.equalsIgnoreCase(generateCSV)){
					generateCSV(dataRequestPayload);
				}else if(todoItem != null && todoItem.equalsIgnoreCase(genPdfgenCsv)){
					//createBookmarkService.readPayloadGenPdfBookmark(dataRequestPayload);
					//createBookmarkService.readPayloadGenCSV(dataRequestPayload);
				}else if(todoItem != null && todoItem.equalsIgnoreCase(presenceFormNames)) {
					presenceFormNamesOutput = presenceFormNames(dataRequestPayload,listData);
				}else if(todoItem != null && todoItem.equalsIgnoreCase(extractionFormNames)) {
					extractionFormNames(dataRequestPayload,listData);
				}
				flag = true;//NEED THIS FLAG TO RETURN SUCCESS OR FAILURE MESSAGE
			}else {
				logger.error("Expected JSON Object from response is null, cannot proceed further.");
			}
		}catch(Exception e) {
			logger.error("Error occurred while executeTask - todoItem :: ", e);
		}
		if(flag)
			return SUCCESS;
		else
			return FAILED;
	}

	private ByteBuffer writeJsonDatatoS3(String externalId,String respData) throws IOException {
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		JsonParser jp = new JsonParser();
		JsonElement je = jp.parse(respData);
		String prettyJsonString = gson.toJson(je);
		byte[] bytes = prettyJsonString.getBytes();
		//byte[] bytes = Files.readAllBytes(Paths.get("C:\\Users\\Administrator\\Documents\\GenBookmarks\\Json_Data\\Sam10025.json"));
		return ByteBuffer.wrap(bytes);
	}

	private void setDirectories(String extId) {

		String s3BucketPdfFilesLocation = env.getProperty("s3_bucket_pdf_files_location");
		String mergedDocumentPath=env.getProperty("Merged_Document_Path");
		String extractFormnamesPath=env.getProperty("Extract_FormNames_Path");
		String genCsvFilePath=env.getProperty("Gen_CsvFile_Path");

		s3BucketPdfFilesLocation=s3BucketPdfFilesLocation.replace("externalId",extId);
		mergedDocumentPath=mergedDocumentPath.replace("externalId",extId);
		extractFormnamesPath=extractFormnamesPath.replace("externalId",extId);
		genCsvFilePath=genCsvFilePath.replace("externalId",extId);


		try {

			Files.createDirectories(Paths.get(s3BucketPdfFilesLocation));
			Files.createDirectories(Paths.get(mergedDocumentPath));
			Files.createDirectories(Paths.get(extractFormnamesPath));
			Files.createDirectories(Paths.get(genCsvFilePath));
		} catch (IOException e) {
			logger.error("Cannot create directories - " + e);
		}
	}

	public void generateCSV(DataRequestPayload dataRequestPayload) {
		try {
			logger.info("CSV File Creation Started");
			String extId = dataRequestPayload.getExternal_id();

			String genCsvFilePath=env.getProperty("Gen_CsvFile_Path");

			genCsvFilePath=genCsvFilePath.replace("externalId",extId);

			String build_fileName = "CsvData_"+extId+".csv";
			String modifiedCsvFileName = genCsvFilePath+build_fileName;
			//String modifiedCsvFileName = generateCSVFileName(genCsvFilePath, extId);
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
					bw.write(name+","+normalized+NEWLINE);
				}
				bw.write(NEWLINE);
			}
			bw.close();
			try {
				S3Client s3 = initiateAWSConnection();
				PutObjectRequest objectRequest = PutObjectRequest.builder()
						.bucket(AWS_BUCKET_NAME)
						.key("APIdocs/"+extId+"/CSVFiles/CsvData_"+extId+".csv")
						.build();
				s3.putObject(objectRequest, software.amazon.awssdk.core.sync.RequestBody.fromFile(Paths.get(genCsvFilePath+"CsvData_"+extId+".csv")));
			}catch(Exception e) {
				logger.error("Exception occurred while AWS connection establishment", e);
			}
			logger.info("CSV File Creation Finished");
		}catch(Exception e) {
			logger.error("Exception in generateCSV() ", e);
		}
	}

	public String presenceFormNames(DataRequestPayload dataRequestPayload,List<String> dataList) {
		String finalResponse = "";
		try {
			logger.info("presenceFormNames Data Processing Started");
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
				finalResponse2 += " , UnAssigned pages "+unassignedPages.size();

			finalResponse = finalResponse1+"@@"+finalResponse2;
			logger.info("presenceFormNames Data Processing Finished");
			//return finalResponse.trim();
		}catch(Exception e) {
			logger.error("Exception in presenceFormNames() ", e);
		}
		return finalResponse.trim();
	}

	public String extractionFormNames(DataRequestPayload dataRequestPayload,List<String> dataList) {

		String externalId = dataRequestPayload.getExternal_id();


		String s3BucketPdfFilesLocation = env.getProperty("s3_bucket_pdf_files_location");
		String extractFormnamesPath=env.getProperty("Extract_FormNames_Path");

		s3BucketPdfFilesLocation=s3BucketPdfFilesLocation.replace("externalId",externalId);
		extractFormnamesPath=extractFormnamesPath.replace("externalId",externalId);
		try {
			logger.info("Extaction form names Started");
			//String build_file_path = "";
			//build_file_path += url_const;
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
					S3Client s3 = initiateAWSConnection();
					PutObjectRequest objectRequest = PutObjectRequest.builder()
							.bucket(AWS_BUCKET_NAME)
							.key("APIdocs/"+externalId+"/ExtractFormNames/"+title+"_"+externalId+".pdf")
							.build();
					s3.putObject(objectRequest, software.amazon.awssdk.core.sync.RequestBody.fromFile(Paths.get(extractFormnamesPath+title+"_"+externalId+".pdf")));
				}catch(Exception e) {
					logger.error("Exception occurred while AWS connection establishment", e);
				}
			}
			readDoc.close();
			logger.info("Extaction form names Finished");
		} catch (Exception e) {
			logger.error("Exception in extractionFormNames() :: ",e);
		}

		return "Successfully extracted pdf forms";
	}

	private static final PDRectangle PAGE_SIZE = PDRectangle.A4;
	private static final float MARGIN = 20;
	private static final boolean IS_LANDSCAPE = true;

	private static final PDFont TEXT_FONT = PDType1Font.HELVETICA;
	private static final float FONT_SIZE = 10;

	private static final float ROW_HEIGHT = 15;
	private static final float CELL_MARGIN = 2;

	public void pdfTable() throws Exception {
		String path = "C:\\Users\\Jack frost\\Pictures\\PDF_Table\\TablePdf.pdf";  //location to store the pdf file
		PDDocument doc = null;
		try {
			List<Column> columns = new ArrayList<Column>();
			columns.add(new Column("Column1", 400));
			columns.add(new Column("Column2", 50));
			columns.add(new Column("Column3", 50));
			columns.add(new Column("Column4", 50));
			columns.add(new Column("Column5", 0));

			String[][] content = {
					{ "FannieMaeForm-1025_SmallResidentialIncomePropertyAppraisalReport", "1000", "1000", "1000",""},
					{ "FannieMaeForm-1025_SmallResidentialIncomePropertyAppraisalReport", "1000", "1000", "1000",""},
					{ "FannieMaeForm-1025_SmallResidentialIncomePropertyAppraisalReport", "1000", "1000", "1000",""},
					{ "FannieMaeForm-1025_SmallResidentialIncomePropertyAppraisalReport", "1000", "1000", "1000",""},
					{ "FannieMaeForm-1025_SmallResidentialIncomePropertyAppraisalReport", "1000", "1000", "1000",""},
					{ "FannieMaeForm-1025_SmallResidentialIncomePropertyAppraisalReport", "1000", "1000", "1000",""},
					{ "FannieMaeForm-1025_SmallResidentialIncomePropertyAppraisalReport", "1000", "1000", "1000",""},
					{ "FannieMaeForm-1025_SmallResidentialIncomePropertyAppraisalReport", "1000", "1000", "1000",""},
					{ "FannieMaeForm-1025_SmallResidentialIncomePropertyAppraisalReport", "1000", "1000", "1000",""},
					{ "FannieMaeForm-1025_SmallResidentialIncomePropertyAppraisalReport", "1000", "1000", "1000",""},
					{ "FannieMaeForm-1025_SmallResidentialIncomePropertyAppraisalReport", "1000", "1000", "1000",""},
					{ "FannieMaeForm-1025_SmallResidentialIncomePropertyAppraisalReport", "1000", "1000", "1000",""},
					{ "FannieMaeForm-1025_SmallResidentialIncomePropertyAppraisalReport", "1000", "1000", "1000",""},
					{ "FannieMaeForm-1025_SmallResidentialIncomePropertyAppraisalReport", "1000", "1000", "1000",""},
					{ "FannieMaeForm-1025_SmallResidentialIncomePropertyAppraisalReport", "1000", "1000", "1000",""},
					{ "FannieMaeForm-1025_SmallResidentialIncomePropertyAppraisalReport", "1000", "1000", "1000",""},
					{ "FannieMaeForm-1025_SmallResidentialIncomePropertyAppraisalReport", "1000", "1000", "1000",""},
					{ "FannieMaeForm-1025_SmallResidentialIncomePropertyAppraisalReport", "1000", "1000", "1000",""},
					{ "FannieMaeForm-1025_SmallResidentialIncomePropertyAppraisalReport", "1000", "1000", "1000",""},
					{ "FannieMaeForm-1025_SmallResidentialIncomePropertyAppraisalReport", "1000", "1000", "1000",""},
					{ "FannieMaeForm-1025_SmallResidentialIncomePropertyAppraisalReport", "1000", "1000", "1000",""},
					{ "FannieMaeForm-1025_SmallResidentialIncomePropertyAppraisalReport", "1000", "1000", "1000",""},
					{ "FannieMaeForm-1025_SmallResidentialIncomePropertyAppraisalReport", "1000", "1000", "1000",""},
					{ "FannieMaeForm-1025_SmallResidentialIncomePropertyAppraisalReport", "1000", "1000", "1000",""},
					{ "FannieMaeForm-1025_SmallResidentialIncomePropertyAppraisalReport", "1000", "1000", "1000",""},
					{ "FannieMaeForm-1025_SmallResidentialIncomePropertyAppraisalReport", "1000", "1000", "1000",""},
					{ "FannieMaeForm-1025_SmallResidentialIncomePropertyAppraisalReport", "1000", "1000", "1000",""},
					{ "FannieMaeForm-1025_SmallResidentialIncomePropertyAppraisalReport", "1000", "1000", "1000",""},
					{ "FannieMaeForm-1025_SmallResidentialIncomePropertyAppraisalReport", "1000", "1000", "1000",""},
					{ "FannieMaeForm-1025_SmallResidentialIncomePropertyAppraisalReport", "1000", "1000", "1000",""},
					{ "FannieMaeForm-1025_SmallResidentialIncomePropertyAppraisalReport", "1000", "1000", "1000",""},
					{ "FannieMaeForm-1025_SmallResidentialIncomePropertyAppraisalReport", "1000", "1000", "1000",""},
					{ "FannieMaeForm-1025_SmallResidentialIncomePropertyAppraisalReport", "1000", "1000", "1000",""},
					{ "FannieMaeForm-1025_SmallResidentialIncomePropertyAppraisalReport", "1000", "1000", "1000",""},
					{ "FannieMaeForm-1025_SmallResidentialIncomePropertyAppraisalReport", "1000", "1000", "1000",""},
					{ "FannieMaeForm-1025_SmallResidentialIncomePropertyAppraisalReport", "1000", "1000", "1000",""},
					{ "FannieMaeForm-1025_SmallResidentialIncomePropertyAppraisalReport", "1000", "1000", "1000",""},
					{ "FannieMaeForm-1025_SmallResidentialIncomePropertyAppraisalReport", "1000", "1000", "1000",""},
					{ "FannieMaeForm-1025_SmallResidentialIncomePropertyAppraisalReport", "1000", "1000", "1000",""},
					{ "FannieMaeForm-1025_SmallResidentialIncomePropertyAppraisalReport", "1000", "1000", "1000",""},
					{ "FannieMaeForm-1025_SmallResidentialIncomePropertyAppraisalReport", "1000", "1000", "1000",""},
					{ "FannieMaeForm-1025_SmallResidentialIncomePropertyAppraisalReport--", "1111", "1111", "1111",""},
			};

			float tableHeight = IS_LANDSCAPE ? PAGE_SIZE.getWidth() - (2 * MARGIN) : PAGE_SIZE.getHeight() - (2 * MARGIN);

			Table table = new TableBuilder()
					.setCellMargin(CELL_MARGIN)
					.setColumns(columns)
					.setContent(content)
					.setHeight(tableHeight)
					.setNumberOfRows(content.length)
					.setRowHeight(ROW_HEIGHT)
					.setMargin(MARGIN)
					.setPageSize(PAGE_SIZE)
					.setLandscape(IS_LANDSCAPE)
					.setTextFont(TEXT_FONT)
					.setFontSize(FONT_SIZE)
					.build();

			doc = new PDDocument();
			PdfTableGenerator pdfTabGen = new PdfTableGenerator();
			pdfTabGen.drawTable(doc, table);
			doc.save(path);
		} finally {
			if (doc != null) {
				doc.close();
			}
		}
		/*PDDocument doc = new PDDocument();
	    PDPage page = new PDPage();
	    doc.addPage( page );
	 
	    PDPageContentStream contentStream =
	                    new PDPageContentStream(doc, page);
	    String[][] content = {{"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","1", "1","1"},
	                          {"b","1", "1","1"},
	                          {"c","1", "1","1"},
	                          {"d","1", "1","1"},
	                          {"e","1", "1","1"},
	                          {"f","1", "1","1"},{"g","1", "1","1"},{"h","1", "1","1"},{"i","1", "1","1"},{"j","1", "1","1"},{"k","1", "1","1"},{"l","1", "1","1"},{"m","1", "1","1"}};
	 
	    drawTable(page, contentStream, 700, 100, content);
	    contentStream.close();
	    
	    PDPage page1 = new PDPage();
	    doc.addPage( page1 );
	 
	    PDPageContentStream contentStream1 =
	                    new PDPageContentStream(doc, page1);
	 
	    String[][] content1 = {{"k","l", "1"},
	                          {"m","n", "2"},
	                          {"o","p", "3"},
	                          {"q","r", "4"},
	                          {"s","t", "5"}};
	 
	    drawTable(page1, contentStream1, 700, 100, content1);
	    contentStream1.close();
	    
	    doc.save(path);*/
	}

	public static void drawTable(PDPage page, PDPageContentStream contentStream,
								 float y, float margin,
								 String[][] content) throws IOException {
		final int rows = content.length;
		final int cols = content[0].length;
		final float rowHeight = 20f;
		final float tableWidth = page.getMediaBox().getWidth()-(2*margin);
		final float tableHeight = rowHeight * rows;
		final float colWidth = tableWidth/(float)cols;
		final float cellMargin=5f;

		//draw the rows
		float nexty = y ;
		for (int i = 0; i <= rows; i++) {
			contentStream.drawLine(margin,nexty,margin+tableWidth,nexty);
			nexty-= rowHeight;
		}

		//draw the columns
		float nextx = margin;
		for (int i = 0; i <= cols; i++) {
			contentStream.drawLine(nextx,y,nextx,y-tableHeight);
			nextx += colWidth;
		}

		//now add the text
		contentStream.setFont(PDType1Font.HELVETICA_BOLD,12);

		float textx = margin+cellMargin;
		float texty = y-15;
		for(int i = 0; i < content.length; i++){
			for(int j = 0 ; j < content[i].length; j++){
				String text = content[i][j];
				contentStream.beginText();
				contentStream.moveTextPositionByAmount(textx,texty);
				contentStream.drawString(text);
				contentStream.endText();
				textx += colWidth;
			}
			texty-=rowHeight;
			textx = margin+cellMargin;
		}
	}


	public void createFolder() {
		//String bucketName, String folderName, AmazonS3 client
		/*String SUFFIX="/";
		// create meta-data for your folder and set content-length to 0
		ObjectMetadata metadata = new ObjectMetadata();
		metadata.setContentLength(0);

		// create empty content
		InputStream emptyContent = new ByteArrayInputStream(new byte[0]);

		// create a PutObjectRequest passing the folder name suffixed by /
		PutObjectRequest putObjectRequest = new PutObjectRequest(bucketName,
				folderName + SUFFIX, emptyContent, metadata);

		// send request to S3 to create folder
		client.putObject(putObjectRequest);*/

		try {
			S3Client s3 = initiateAWSConnection();
			PutObjectRequest objectRequest = PutObjectRequest.builder()
					.bucket(AWS_BUCKET_NAME)
					.key("TestFolder/")
					.build();
			s3.putObject(objectRequest, software.amazon.awssdk.core.sync.RequestBody.empty());
			//s3.putObject(objectRequest, software.amazon.awssdk.core.sync.RequestBody.fromFile(Paths.get(genCsvFilePath+"CsvData_"+extId+".csv")));
		}catch(Exception e) {
			logger.error("Exception occurred while AWS connection establishment", e);
		}
	}

	@Override
	public List<File> getResultList(String extenalId) {

		StringBuilder s3BucketPdfFilesLocation =new StringBuilder();

		StringBuilder location= new StringBuilder();
		location.append("/var/tmp/resultList/");
		location.append(extenalId+"/");
		logger.info("Loaction to store the files after retreiving the files from the AWS -->"+  location);

		S3Client s3 = initiateAWSConnection();

		List<File> files= new ArrayList<>();

		List<GetObjectResponse> s3Objects = new ArrayList<>();

		ListObjectsRequest req = ListObjectsRequest.builder().bucket(AWS_BUCKET_NAME).prefix("APIdocs/"+extenalId+"/").build();
		ListObjectsResponse response1 = s3.listObjects(req);
		List<S3Object> listOfS3Obj = response1.contents();//List of Data
		for(int i=0;i<listOfS3Obj.size();i++) {

			S3Object s3Object = listOfS3Obj.get(i);
			String s3objKey = s3Object.key();
			String fileName = s3objKey.substring(s3objKey.lastIndexOf("/"));

			GetObjectRequest getObjectRequest = GetObjectRequest.builder()
					.bucket(AWS_BUCKET_NAME)
					.key(s3objKey)
					.build();

			createResultListDirectory(String.valueOf(location));

			File f = new File(location + fileName);
			if (f.exists()) {
				f.delete();
			}
			s3Objects.add(s3.getObject(getObjectRequest, Paths.get(location + fileName)));

			//adding Files to the list
			f = getFiles(String.valueOf(location), fileName);
			files.add(f);
		}
		logger.info("added a total of "+files.size()+" files to the List");
		return files;

	}

	@Override
	public HttpServletResponse convertFilestoZip(HttpServletResponse response, List<File> resultList) {

		try (ZipOutputStream zippedOut = new ZipOutputStream(response.getOutputStream())) {
			for (File file : resultList) {
				FileSystemResource resource = new FileSystemResource(file);

				ZipEntry e = new ZipEntry(resource.getFilename());
				// Configure the zip entry, the properties of the file
				e.setSize(resource.contentLength());
				e.setTime(System.currentTimeMillis());
				// etc.
				zippedOut.putNextEntry(e);
				// And the content of the resource:
				StreamUtils.copy(resource.getInputStream(), zippedOut);
				zippedOut.closeEntry();
			}
			zippedOut.finish();
		} catch (Exception e) {
			logger.error("Error While Converting the files to ZIP format" + e);
		}
		return response;
	}

	void  createResultListDirectory(String location) {
		try {
			Files.createDirectories(Paths.get(location));

		} catch (IOException e) {
			logger.error("Cannot create directories - " + e);
		}
	}


	private File getFiles(String location,String fileName){
		logger.info("file  --"+location+""+fileName +"  added to the List");

		List<File> files= new ArrayList<>();
		File f= new File(location+fileName);
		if(!f.exists()) {
			throw new RuntimeException(" File does not exists");
		}
		files.add(f);
		return  f;
	}

}
