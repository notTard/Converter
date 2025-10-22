package com.example.converter.conv.service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.itextpdf.io.font.PdfEncodings;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;


@Service
public class FileService {

    @Value("${file.upload-dir:uploads}")
    private String uploadDir;

    public String saveUploadedFile(MultipartFile file) throws IOException {
        Path uploadPath = Paths.get(uploadDir);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        String fileName = UUID.randomUUID().toString() + "_" + file.getOriginalFilename();
        Path filePath = uploadPath.resolve(fileName);
        
        Files.copy(file.getInputStream(), filePath);
        
        return fileName;
    }

    public Resource convertToPdf(String fileName) throws IOException {
        Path filePath = Paths.get(uploadDir).resolve(fileName);
        
        String pdfFileName = fileName.replaceFirst("\\.[^.]+$", "") + ".pdf";
        Path pdfPath = Paths.get(uploadDir).resolve(pdfFileName);

        try (OutputStream os = new FileOutputStream(pdfPath.toFile());
             PdfWriter writer = new PdfWriter(os);
             PdfDocument pdf = new PdfDocument(writer);
             Document document = new Document(pdf)) {

            String content = readFileContentWithDetection(filePath);
            
            // Создаем шрифт, поддерживающий кириллицу
            PdfFont font = createRussianFont();
            
            Paragraph paragraph = new Paragraph(content)
                .setFont(font)
                .setFontSize(12);
                
            document.add(paragraph);
        }

        return new UrlResource(pdfPath.toUri());
    }

    private PdfFont createRussianFont() throws IOException {
        try {
            // Пробуем загрузить шрифт из ресурсов
            InputStream fontStream = getClass().getClassLoader().getResourceAsStream("fonts/arial.ttf");
            if (fontStream != null) {
                // Правильный способ создать шрифт из потока
                byte[] fontData = fontStream.readAllBytes();
                return PdfFontFactory.createFont(fontData, PdfEncodings.IDENTITY_H);
            }
        } catch (Exception e) {
            System.out.println("Не удалось загрузить шрифт из ресурсов: " + e.getMessage());
        }
        
        // Пробуем системные шрифты
        try {
            // Для Windows
            File windowsFont = new File("C:\\Windows\\Fonts\\arial.ttf");
            if (windowsFont.exists()) {
                return PdfFontFactory.createFont(windowsFont.getAbsolutePath(), PdfEncodings.IDENTITY_H);
            }
            
            // Для Linux
            File linuxFont = new File("/usr/share/fonts/truetype/freefont/FreeSans.ttf");
            if (linuxFont.exists()) {
                return PdfFontFactory.createFont(linuxFont.getAbsolutePath(), PdfEncodings.IDENTITY_H);
            }
        } catch (Exception e) {
            System.out.println("Не удалось загрузить системный шрифт: " + e.getMessage());
        }
        
        // Последний вариант - используем встроенный шрифт с Identity-H кодировкой
        try {
            return PdfFontFactory.createFont("Helvetica", PdfEncodings.IDENTITY_H);
        } catch (Exception e) {
            // Если ничего не работает, создаем стандартный шрифт
            return PdfFontFactory.createFont();
        }
    }

    private String readFileContentWithDetection(Path filePath) throws IOException {
        String encoding = detectEncoding(filePath);
        System.out.println("Определена кодировка: " + encoding);
        
        StringBuilder content = new StringBuilder();
        
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(filePath.toFile()), encoding))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
        }
        
        // Проверяем, что кириллица сохранилась
        System.out.println("Первые 100 символов содержимого: " + 
                          (content.length() > 100 ? content.substring(0, 100) : content.toString()));
        
        return content.toString();
    }

    private String detectEncoding(Path filePath) throws IOException {
        byte[] buf = new byte[4096];
        try (InputStream is = new FileInputStream(filePath.toFile())) {
            org.mozilla.universalchardet.UniversalDetector detector = 
                new org.mozilla.universalchardet.UniversalDetector(null);
            
            int nread;
            while ((nread = is.read(buf)) > 0 && !detector.isDone()) {
                detector.handleData(buf, 0, nread);
            }
            detector.dataEnd();
            
            String encoding = detector.getDetectedCharset();
            detector.reset();
            
            if (encoding != null) {
                return encoding;
            }
        }
        
        return "UTF-8";
    }
}