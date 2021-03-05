package com.book.mark.service;

import org.springframework.http.ResponseEntity;

import com.book.mark.model.DataProcessingReq;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.util.List;

public interface DataProcessingService {

	public ResponseEntity processData(MultipartFile file,DataProcessingReq dataProcReq);

	public void pdfTable() throws Exception;
	public void createFolder() throws Exception;

	public List<File> getResultList(String extenalId);
	public  HttpServletResponse convertFilestoZip(HttpServletResponse response,List<File> resultList);

}
