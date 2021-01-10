package com.book.mark.constants;

public interface PdfBookmarkConstants {
	
	String SUCCESS = "SUCCESS";
	String FAILED = "FAILED";

	//String url_const = "http://13.72.109.50";
	String url_const = "https://sandb-ide.appsquared.io/";
	String url_const_getstatus = "https://sandb-ide.appsquared.io";
	
	
	//String readPdfPathNew = "C:\\Users\\Administrator\\Documents\\Raj\\Bookmarks\\1004_Scanned_0914.pdf";
	//String writePdfPathNew = "C:\\Users\\Administrator\\Documents\\Raj\\Bookmarks\\HyperScience_Testing_0929_After.pdf";
	
	
	//String hostName = "http://13.72.109.50/api/v5/submissions";
	String hostName = "https://sandb-ide.appsquared.io/api/v5/submissions";
	String urlAppender = "?flat=false";
	
	//String hostName_genSubId = "http://13.72.109.50/api/v5/submissions/";
	String hostName_genSubId = "https://sandb-ide.appsquared.io/api/v5/submissions/";
	
	//String hostName_external = "http://13.72.109.50/api/v5/submissions/external/";
	String hostName_external = "https://sandb-ide.appsquared.io/api/v5/submissions/external/";
	
	String success_message = "Successfully Bookmarked Pdf Document";
	
	//String s3_bucket_pdf_files_location = "C:\\Users\\Administrator\\Documents\\GenBookmarks\\MultipleDocs\\";
	//String s3_bucket_pdf_files_location = "C:\\Users\\Administrator\\Documents\\GenBookmarks\\NewTestFile\\";
	//ABOVE LINE IS MOVED TO APPLICATION_PROPERTIES FILE--DEC20_2020
	//String MergedDocumentPath = "C:\\Users\\Administrator\\Documents\\GenBookmarks\\MergeMultipleDocs\\MergedPdf.pdf";//How to generate the Merged File Name(As of now Hardcoding)
	//String MergedDocumentPath = "C:\\Users\\Administrator\\Documents\\GenBookmarks\\MergeMultipleDocs\\";
	//ABOVE LINE IS MOVED TO APPLICATION_PROPERTIES FILE--DEC20_2020
	//String ExtractFormNamesLocation = "C:\\Users\\Administrator\\Documents\\GenBookmarks\\ExtractFormNames\\";
	//ABOVE LINE IS MOVED TO APPLICATION_PROPERTIES FILE--DEC20_2020
	String generateCSV = "GenCSV";
	String bookMarkPdf = "BookMarkPdf";
	String genPdfgenCsv = "Both";
	String presenceFormNames = "presenceFormNames";
	String extractionFormNames = "extractionFormNames";
	//String genCsvFilePath = "C:\\Users\\Administrator\\Documents\\GenBookmarks\\CSVFiles\\CsvData.csv";
	//ABOVE LINE IS MOVED TO APPLICATION_PROPERTIES FILE--DEC20_2020
	String NEWLINE = "\n";
	String COMPLETE = "COMPLETE";
	
	long PDF_TIMEOUT=80;
	
	String Authorization_Token = "Token 984910a508f30eb63dfe404aa8e2d4acf505ae14";
	
	String AWS_BUCKET_NAME = "ide-sandb-temp-microservice-bucket";
	
	String ACCESS_KEY = "AKIAXM6VM7LNUSMVFKRH";
	String SECRET_KEY = "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";
	String SESSION_TOKEN = "";
	
}
