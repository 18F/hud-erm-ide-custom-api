package com.book.mark.model;

import java.util.ArrayList;
import java.util.List;

public class DataProcessingReq {
	
	private String externalId;
	private String generateBookmarkedPdf;
	private String pdfFilesLocation;
	private String resultsFileLocation;
	private String generateCsv;
	private String acceptPartialResults;
	private String extractFullResults;
	private List<String> presenceFormNames = new ArrayList<String>();
	private List<String> extractionFormNames = new ArrayList<String>();
	
	public String getExternalId() {
		return externalId;
	}
	public void setExternalId(String externalId) {
		this.externalId = externalId;
	}
	public String getGenerateBookmarkedPdf() {
		return generateBookmarkedPdf;
	}
	public void setGenerateBookmarkedPdf(String generateBookmarkedPdf) {
		this.generateBookmarkedPdf = generateBookmarkedPdf;
	}
	public String getPdfFilesLocation() {
		return pdfFilesLocation;
	}
	public void setPdfFilesLocation(String pdfFilesLocation) {
		this.pdfFilesLocation = pdfFilesLocation;
	}
	public String getResultsFileLocation() {
		return resultsFileLocation;
	}
	public void setResultsFileLocation(String resultsFileLocation) {
		this.resultsFileLocation = resultsFileLocation;
	}
	public String getGenerateCsv() {
		return generateCsv;
	}
	public void setGenerateCsv(String generateCsv) {
		this.generateCsv = generateCsv;
	}
	public String getAcceptPartialResults() {
		return acceptPartialResults;
	}
	public void setAcceptPartialResults(String acceptPartialResults) {
		this.acceptPartialResults = acceptPartialResults;
	}
	public String getExtractFullResults() {
		return extractFullResults;
	}
	public void setExtractFullResults(String extractFullResults) {
		this.extractFullResults = extractFullResults;
	}
	public List<String> getPresenceFormNames() {
		return presenceFormNames;
	}
	public void setPresenceFormNames(List<String> presenceFormNames) {
		this.presenceFormNames = presenceFormNames;
	}
	public List<String> getExtractionFormNames() {
		return extractionFormNames;
	}
	public void setExtractionFormNames(List<String> extractionFormNames) {
		this.extractionFormNames = extractionFormNames;
	}

}
