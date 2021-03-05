package com.book.mark.controller;

import com.book.mark.constants.PdfBookmarkConstants;
import com.book.mark.model.*;
import com.book.mark.service.CreateBookmarksService;
import com.book.mark.service.DataProcessingService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.QueryParam;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@RestController
@RequestMapping(value = "/bookmark")
public class CreateBookmarksController implements PdfBookmarkConstants {

	Logger logger = LoggerFactory.getLogger(CreateBookmarksController.class);

	@Autowired
	private CreateBookmarksService createBookmarksService;

	@Autowired
	DataProcessingService dataProcessingService;

	@Value("${Merged_Document_Path}")
	private String mergedDocumentPath;

	@Value("${Extract_FormNames_Path}")
	private String extractFormnamesPath;

	@Value("${Gen_CsvFile_Path}")
	private String genCsvFilePath;

	@Value("${Json_Files_Path}")
	private String jsonFilesPath;

	@Autowired
	private Environment env;

	/*
	 * @PostMapping(value = "/readPayload") public void getPayload(@RequestBody
	 * DataRequestPayload dataRequestPayload) {
	 * createBookmarksService.readPayload(dataRequestPayload); }
	 * 
	 * @GetMapping(value = "/genBookmark") public void
	 * genBookmark(@RequestParam("submissionId") String submission_id) {
	 * createBookmarksService.genBookmarks(submission_id); }
	 */

	@GetMapping(value = "/genBookmarks/{submissionId}")
	public String genBookmarks(@PathVariable("submissionId") String submission_id) {
		return createBookmarksService.genBookmarks(submission_id, "bookMarkPdf");
	}

	@PostMapping(value = "/presenceFormNames")
	public ResponseEntity<?> presenceFormNames(@RequestBody PresenceFormNames presenceFormNames) {
		RespPayload respPayload = new RespPayload();
		if (presenceFormNames == null || StringUtils.isEmpty(presenceFormNames.getExternalId())
				|| presenceFormNames.getExternalId().trim().equals("")) {
			respPayload.setMessage("External Id is required");
			return new ResponseEntity<RespPayload>(respPayload, HttpStatus.BAD_REQUEST);
		}
		String resp = createBookmarksService.genPresenceOrExtractionFormNames(presenceFormNames, "presenceFormNames");
		String[] ar = resp.split("@@");
		PresenceFormResp presenceFormPayload = new PresenceFormResp();
		presenceFormPayload.setMessage("Success");
		if (ar != null && ar.length > 0)
			presenceFormPayload.setValidLayouts(ar[0]);
		else
			presenceFormPayload.setValidLayouts("");
		if (ar != null && ar.length > 1)
			presenceFormPayload.setInvalidLayouts(ar[1]);
		else
			presenceFormPayload.setInvalidLayouts("");
		return new ResponseEntity<PresenceFormResp>(presenceFormPayload, HttpStatus.OK);
	}

	// ONLY FOR EXTRACTION FORM NAMES API IS CHANGED FROM extractionFormNames to
	// extractionFormNamesOnly
	@PostMapping(value = "/extractionFormNamesOnly")
	public ResponseEntity<RespPayload> extractionFormNames(@RequestBody PresenceFormNames presenceFormNames) {
		RespPayload respPayload = new RespPayload();
		if (presenceFormNames == null || StringUtils.isEmpty(presenceFormNames.getExternalId())
				|| presenceFormNames.getExternalId().trim().equals("")) {
			respPayload.setMessage("External Id is required");
			return new ResponseEntity<RespPayload>(respPayload, HttpStatus.BAD_REQUEST);
		}
		String resp = createBookmarksService.genPresenceOrExtractionFormNames(presenceFormNames, "extractionFormNames");
		respPayload.setMessage(resp);
		return new ResponseEntity<RespPayload>(respPayload, HttpStatus.OK);
	}

	@PostMapping(value = "/getSubmiId")
	public void getSubmissionId() {
		createBookmarksService.genSubmissionId();
	}
	// API NAME CHANGED from getStatus to getStatusResults


	@GetMapping(value = "/genCsv")
	public ResponseEntity<RespPayload> genCsvByExternalId(@QueryParam("externalId") String externalId) {
		if (StringUtils.isEmpty(externalId) || externalId.trim().equals("")) {
			RespPayload respPayload = new RespPayload();
			respPayload.setMessage("External Id is required");
			return new ResponseEntity<RespPayload>(respPayload, HttpStatus.BAD_REQUEST);
		}
		return createBookmarksService.genCsvByExternalId(externalId);
	}

	// API NAME CHANGED from getSubmiIdGenBookmark to generateBookmarkedPdf
	@GetMapping(value = "/generateBookmarkedPdf")
	public ResponseEntity<RespPayload> getSubmisIdGenBookmark1(@QueryParam("externalId") String externalId) {
		if (StringUtils.isEmpty(externalId) || externalId.trim().equals("")) {
			RespPayload respPayload = new RespPayload();
			respPayload.setMessage("External Id is required");
			return new ResponseEntity<RespPayload>(respPayload, HttpStatus.BAD_REQUEST);
		}
		ResponseEntity<RespPayload> respEnt = createBookmarksService.getSubmisIdGenBookmark(externalId, "bookMarkPdf");
		return respEnt;
	}

	@PostMapping(value = "/genPdfBkmrkPrsncForm")
	public ResponseEntity genPdfBkmrkPrsncForm(@RequestBody PresenceFormNames presenceFormNames) {
		RespPayload respPayload = new RespPayload();
		if (presenceFormNames == null || StringUtils.isEmpty(presenceFormNames.getExternalId())
				|| presenceFormNames.getExternalId().trim().equals("")) {
			respPayload.setMessage("External Id is required");
			return new ResponseEntity<RespPayload>(respPayload, HttpStatus.BAD_REQUEST);
		}
		if (presenceFormNames == null || StringUtils.isEmpty(presenceFormNames.getLayoutNames())
				|| (presenceFormNames.getLayoutNames() != null && presenceFormNames.getLayoutNames().size() == 0)) {
			respPayload.setMessage("Layout Names are Empty");
			return new ResponseEntity<RespPayload>(respPayload, HttpStatus.BAD_REQUEST);
		}
		ResponseEntity<RespPayload> respEnt = createBookmarksService
				.getSubmisIdGenBookmark(presenceFormNames.getExternalId().trim(), "bookMarkPdf");

		if (respEnt != null && respEnt.getBody() != null
				&& respEnt.getBody().getMessage().equalsIgnoreCase(PdfBookmarkConstants.success_message)) {
			String resp = createBookmarksService.genPresenceOrExtractionFormNames(presenceFormNames,
					"presenceFormNames");
			String[] ar = resp.split("@@");
			PresenceFormResp presenceFormPayload = new PresenceFormResp();
			presenceFormPayload.setMessage(respEnt.getBody().getMessage());
			if (ar != null && ar.length > 0)
				presenceFormPayload.setValidLayouts(ar[0]);
			else
				presenceFormPayload.setValidLayouts("");
			if (ar != null && ar.length > 1)
				presenceFormPayload.setInvalidLayouts(ar[1]);
			else
				presenceFormPayload.setInvalidLayouts("");
			return new ResponseEntity<PresenceFormResp>(presenceFormPayload, HttpStatus.OK);
		} else {
			PresenceFormResp presenceFormPayload = new PresenceFormResp();
			presenceFormPayload.setMessage(respEnt.getBody().getMessage());
			presenceFormPayload.setValidLayouts("");
			presenceFormPayload.setInvalidLayouts("");
			return new ResponseEntity<PresenceFormResp>(presenceFormPayload, HttpStatus.OK);
		}

	}

	// ONLY FOR EXTRACTION FORM NAMES API IS CHANGED FROM genPdfBkmrkExtractForm to
	// extractionFormNames
	@PostMapping(value = "/extractionFormNames")
	public ResponseEntity genPdfBkmrkExtractForm(@RequestBody PresenceFormNames presenceFormNames) {
		RespPayload respPayload = new RespPayload();
		if (presenceFormNames == null || StringUtils.isEmpty(presenceFormNames.getExternalId())
				|| presenceFormNames.getExternalId().trim().equals("")) {
			respPayload.setMessage("External Id is required");
			return new ResponseEntity<RespPayload>(respPayload, HttpStatus.BAD_REQUEST);
		}
		if (presenceFormNames == null || StringUtils.isEmpty(presenceFormNames.getLayoutNames())
				|| (presenceFormNames.getLayoutNames() != null && presenceFormNames.getLayoutNames().size() == 0)) {
			respPayload.setMessage("Layout Names are Empty");
			return new ResponseEntity<RespPayload>(respPayload, HttpStatus.BAD_REQUEST);
		}
		ResponseEntity<RespPayload> respEnt = createBookmarksService
				.getSubmisIdGenBookmark(presenceFormNames.getExternalId().trim(), "bookMarkPdf");

		if (respEnt != null && respEnt.getBody() != null
				&& respEnt.getBody().getMessage().equalsIgnoreCase(PdfBookmarkConstants.success_message)) {
			String resp = createBookmarksService.genPresenceOrExtractionFormNames(presenceFormNames,
					"extractionFormNames");
			ExtractFormResp extractFormResp = new ExtractFormResp();
			extractFormResp.setGenPdfResp(respEnt.getBody().getMessage());
			extractFormResp.setExtractFormResp(resp);
			return new ResponseEntity<ExtractFormResp>(extractFormResp, HttpStatus.OK);
		} else {
			ExtractFormResp extractFormResp = new ExtractFormResp();
			extractFormResp.setGenPdfResp(respEnt.getBody().getMessage());
			extractFormResp.setExtractFormResp("");
			return new ResponseEntity<ExtractFormResp>(extractFormResp, HttpStatus.OK);
		}

	}

	@GetMapping(value = "/getSubmiIdGenCsv")
	public ResponseEntity<RespPayload> getSubmisIdGenCsv(@QueryParam("externalId") String externalId) {
		if (StringUtils.isEmpty(externalId) || externalId.trim().equals("")) {
			RespPayload respPayload = new RespPayload();
			respPayload.setMessage("External Id is required");
			return new ResponseEntity<RespPayload>(respPayload, HttpStatus.BAD_REQUEST);
		}
		ResponseEntity<RespPayload> respEnt = createBookmarksService.getSubmisIdGenBookmark(externalId, "GenCSV");
		return respEnt;
	}

	@GetMapping(value = "/genPdfGenCsv")
	public ResponseEntity<RespPayload> getSubmisIdGenPdfGenCsv(@QueryParam("externalId") String externalId) {
		if (StringUtils.isEmpty(externalId) || externalId.trim().equals("")) {
			RespPayload respPayload = new RespPayload();
			respPayload.setMessage("External Id is required");
			return new ResponseEntity<RespPayload>(respPayload, HttpStatus.BAD_REQUEST);
		}
		ResponseEntity<RespPayload> respEnt = createBookmarksService.getSubmisIdGenBookmark(externalId, "Both");
		return respEnt;
	}

	@PostMapping(value = "/submitPdf")
	public ResponseEntity<?> processData(@RequestParam("file") MultipartFile file, DataProcessingReq dataProcReq) {
		String s3BucketPdfFilesLocation = env.getProperty("s3_bucket_pdf_files_location");

		if (file.isEmpty() && file.getOriginalFilename().contains(".pdf")) {
			List<String> list = new ArrayList<String>();
			list.add("File is Required.");
			DataProcessingResp dataProcResp = new DataProcessingResp();
			dataProcResp.setMessage(list);
			return new ResponseEntity<DataProcessingResp>(dataProcResp, HttpStatus.BAD_REQUEST);
		}

		if (StringUtils.isEmpty(dataProcReq) || StringUtils.isEmpty(dataProcReq.getExternalId())) {
			// Map<String,List<String>> data = new HashMap<String, List<String>>();
			List<String> list = new ArrayList<String>();
			list.add("External Id is Required.");
			DataProcessingResp dataProcResp = new DataProcessingResp();
			dataProcResp.setMessage(list);
			return new ResponseEntity<DataProcessingResp>(dataProcResp, HttpStatus.BAD_REQUEST);
		}
		ResponseEntity<?> response = dataProcessingService.processData(file, dataProcReq);

		return response;
	}

	@GetMapping(value = "/pdfTable")
	public ResponseEntity<?> pdfTable() throws Exception {

		dataProcessingService.pdfTable();
		return null;
	}

	@GetMapping(value = "/deleteMe")
	public ResponseEntity<?> deleteMe() throws Exception {

		dataProcessingService.createFolder();
		return null;
	}


	@GetMapping(value = "/getStatus")
	public ResponseEntity<?> genStateByExternalId(@QueryParam("externalId") String externalId) {
		if (StringUtils.isEmpty(externalId) || externalId.trim().equals("")) {
			RespPayload respPayload = new RespPayload();
			respPayload.setMessage("External Id is required");
			return new ResponseEntity<RespPayload>(respPayload, HttpStatus.BAD_REQUEST);
		}

		//List<File> resultList = dataProcessingService.getResultList(externalId);

		ResponseEntity<ExternalIdStatus> respEnt = createBookmarksService.genStateByExternalId(externalId);
		if (respEnt.getBody().getExternal_id() == null ) {
			RespPayload respPayload = new RespPayload();
		respPayload.setMessage("External ID doesn't exist");
		
			 return new ResponseEntity<RespPayload>(respPayload, HttpStatus.OK) ;
		}
		else
		    return respEnt;
	}



	@GetMapping(value = "/getResultsFiles") public ResponseEntity
	getResultList(@RequestParam(name = "externalId") String externalId,
			HttpServletResponse response) throws JsonProcessingException {

		ObjectMapper objectMapper = new ObjectMapper();
		if(StringUtils.isEmpty(externalId)){ return new
				ResponseEntity("External Id is Mandatory",HttpStatus.BAD_REQUEST); }

		/*
		 * Map<String,String> map = objectMapper.readValue(externalId, new
		 * TypeReference<Map<String,String>>() { });
		 * 
		 * 
		 * if(StringUtils.isEmpty(externalId)){ return new
		 * ResponseEntity("External Id is Mandatory",HttpStatus.BAD_REQUEST); }
		 */


		List<File> resultList = dataProcessingService.getResultList(externalId);

		if(!resultList.isEmpty() || !(resultList.size() ==0)){
			response.setContentType("application/octet-stream");
			response.setHeader("Content-Disposition",
					"attachment;filename="+externalId+".zip");
			response.setStatus(HttpServletResponse.SC_OK);

			dataProcessingService.convertFilestoZip(response, resultList); }else { return
					new ResponseEntity("No files found for the Given ExternalId"
							,HttpStatus.BAD_REQUEST); }


		return new ResponseEntity("SUccess",HttpStatus.OK); }


}
