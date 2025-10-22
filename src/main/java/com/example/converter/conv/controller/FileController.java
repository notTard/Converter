package com.example.converter.conv.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.example.converter.conv.service.FileService;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Controller
public class FileController {

    @Autowired
    private FileService fileConverterService;

    @GetMapping("/")
    public String index() {
        return "index";
    }

    @PostMapping("/upload")
    public String uploadFile(@RequestParam("file") MultipartFile file, Model model) {
        if (file.isEmpty()) {
            model.addAttribute("error", "Пожалуйста, выберите файл");
            return "index";
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename != null && 
            !(originalFilename.endsWith(".txt") || originalFilename.endsWith(".fb2"))) {
            model.addAttribute("error", "Поддерживаются только файлы .txt и .fb2");
            return "index";
        }

        try {
            String savedFileName = fileConverterService.saveUploadedFile(file);
            model.addAttribute("uploadedFile", savedFileName);
            model.addAttribute("originalFileName", originalFilename);
            return "convert";
        } catch (IOException e) {
            model.addAttribute("error", "Ошибка при загрузке файла: " + e.getMessage());
            return "index";
        }
    }

    @PostMapping("/convert")
    public ResponseEntity<Resource> convertToPdf(@RequestParam("fileName") String fileName,
                                                 @RequestParam("originalFileName") String originalFileName) {
        try {
            Resource pdfResource = fileConverterService.convertToPdf(fileName);
            
            // Формируем правильное имя для скачивания
            String downloadFileName = getDownloadFileName(originalFileName);
            
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION, 
                           "attachment; filename=\"" + downloadFileName + "\"")
                    .body(pdfResource);
                    
        } catch (IOException e) {
            throw new RuntimeException("Ошибка конвертации: " + e.getMessage());
        }
    }

    private String getDownloadFileName(String originalFileName) {
        // Убираем оригинальное расширение и добавляем .pdf
        String baseName = originalFileName.replaceFirst("\\.[^.]+$", "");
        String safeFileName = baseName + ".pdf";
        
        // Кодируем имя файла для безопасного использования в HTTP-заголовках
        return URLEncoder.encode(safeFileName, StandardCharsets.UTF_8)
                .replace("+", "%20");
    }
}