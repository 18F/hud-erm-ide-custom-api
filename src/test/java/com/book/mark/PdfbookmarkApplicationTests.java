package com.book.mark;

import org.junit.jupiter.api.Test;

import org.springframework.boot.test.context.SpringBootTest;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Random;
import software.amazon.awssdk.core.waiters.WaiterResponse;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.paginators.ListObjectsV2Iterable;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteBucketRequest;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadResponse;
import software.amazon.awssdk.services.s3.model.CompletedMultipartUpload;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.CompletedPart;
import software.amazon.awssdk.services.s3.model.CreateBucketConfiguration;
import software.amazon.awssdk.services.s3.model.UploadPartRequest;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadRequest;
import software.amazon.awssdk.services.s3.waiters.S3Waiter;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketResponse;

import software.amazon.awssdk.core.waiters.WaiterResponse;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.waiters.S3Waiter;

@SpringBootTest
class PdfbookmarkApplicationTests {

	@Test
	void contextLoads() throws S3Exception, AwsServiceException, SdkClientException, IOException {
		
		 Region region = Region.US_GOV_WEST_1;
//	        S3Client s3 = S3Client.builder()
//	                .region(region)
//	                .build();
//	        
//		
//		s3 = S3Client.builder()
//		        .region(region)
//		        .build();
		 
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
		        .key("key")
		        .build();

	
		s3.putObject(objectRequest, RequestBody.fromByteBuffer(getRandomByteBuffer(10_000)));
	}

	private ByteBuffer getRandomByteBuffer(int size) throws IOException {
        byte[] b = new byte[size];
        new Random().nextBytes(b);
        return ByteBuffer.wrap(b);
    }
	
}
