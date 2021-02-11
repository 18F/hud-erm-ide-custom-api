package com.book.mark.service;

import org.springframework.http.ResponseEntity;

import com.book.mark.model.DataRequestPayload;
import com.book.mark.model.ExternalIdStatus;
import com.book.mark.model.RespPayload;

public interface CreateBookmarksService {

	public void readPayloadGenPdfBookmark(DataRequestPayload dataRequestPayload);
	public String genBookmarks(String submission_id,String temp);
	public ResponseEntity<RespPayload> genCsvByExternalId(String externalId);
	public void genSubmissionId();
	public ResponseEntity<ExternalIdStatus> genStateByExternalId(String ext_id);
	public ResponseEntity<RespPayload> getSubmisIdGenBookmark(String extId, String todo_item);
	
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
public String getSubmissionId( MultipartFile uploadfile,String extId){

		String s3BucketPdfFilesLocation = env.getProperty("s3_bucket_pdf_files_location");
		String mergedDocumentPath=env.getProperty("Merged_Document_Path");

		s3BucketPdfFilesLocation=s3BucketPdfFilesLocation.replace("externalId",extId);
		mergedDocumentPath=mergedDocumentPath.replace("externalId",extId);

		logger.info("Generating Submission Id Started");
		try {
			setDirectories(extId);
			S3Client s3 = initiateAWSConnection();

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
			}

			File f= new File(s3BucketPdfFilesLocation+uploadfile.getOriginalFilename());

			if(!f.exists()){
				f.createNewFile();
			}

			Path path = Paths.get(s3BucketPdfFilesLocation + uploadfile.getOriginalFilename());
			Files.copy(uploadfile.getInputStream(),path,StandardCopyOption.REPLACE_EXISTING);

		
			//}
			MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
			body.add("file", new FileSystemResource(mergedDocPath));
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
