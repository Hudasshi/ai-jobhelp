package com.qznu.jobhelper.support.file;

import com.qznu.jobhelper.common.exception.BizException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * PDF/DOCX/TXT 文本抽取。
 */
@Component
public class TextExtractor {

    public String extract(Path file, String extension) {
        try {
            return switch (extension.toLowerCase()) {
                case "pdf" -> extractPdf(file);
                case "docx" -> extractDocx(file);
                case "txt" -> normalize(Files.readString(file, StandardCharsets.UTF_8));
                default -> throw BizException.badRequest("不支持的简历格式");
            };
        } catch (BizException exception) {
            throw exception;
        } catch (Exception exception) {
            throw BizException.badRequest("简历文件无法解析，请检查文件是否损坏");
        }
    }

    private String extractPdf(Path file) throws Exception {
        try (PDDocument document = Loader.loadPDF(file.toFile())) {
            if (document.isEncrypted()) {
                throw BizException.badRequest("暂不支持加密 PDF");
            }
            return normalize(new PDFTextStripper().getText(document));
        }
    }

    private String extractDocx(Path file) throws Exception {
        try (InputStream input = Files.newInputStream(file);
             XWPFDocument document = new XWPFDocument(input);
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            return normalize(extractor.getText());
        }
    }

    private String normalize(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\u0000", "")
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("[\\t ]+", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }
}
