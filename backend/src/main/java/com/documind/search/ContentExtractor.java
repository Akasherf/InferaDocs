package com.documind.search;

import com.documind.common.exception.ApiException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;

@Component
public class ContentExtractor {
    public String extract(byte[] bytes, String contentType) {
        try {
            String text;
            switch (contentType) {
                case "application/pdf" -> {
                    try (var pdf = Loader.loadPDF(bytes)) {
                        if (pdf.isEncrypted()) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "ENCRYPTED_PDF", "Encrypted PDFs are not supported");
                        if (pdf.getNumberOfPages() > 150) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "TOO_MANY_PAGES", "PDF exceeds the 150-page indexing limit");
                        text = new PDFTextStripper().getText(pdf);
                    }
                }
                case "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> {
                    try (var doc = new XWPFDocument(new ByteArrayInputStream(bytes)); var extractor = new XWPFWordExtractor(doc)) {
                        text = extractor.getText();
                    }
                }
                case "text/plain" -> {
                    text = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
                }
                default -> throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_FILE", "Unsupported document format");
            }
            if (text.length() > 2_000_000) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "TEXT_TOO_LONG", "Extracted text exceeds the 2-million-character limit");
            return text.replace('\u0000', ' ').replaceAll("[\\p{Cntrl}&&[^\\r\\n\\t]]", " ").trim();
        } catch (ApiException e) { throw e;
        } catch (Exception e) { throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "EXTRACTION_FAILED", "Document text could not be extracted"); }
    }
}
