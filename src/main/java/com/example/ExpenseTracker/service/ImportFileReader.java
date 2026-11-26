package com.example.ExpenseTracker.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;

/** Works out what an uploaded file is and returns its text, or rejects it with a clear reason. */
@Component
public class ImportFileReader {

    public enum Kind { CSV, PDF }

    public record Extracted(Kind kind, String text) {}

    private static final byte[] PDF_MAGIC = "%PDF-".getBytes(StandardCharsets.US_ASCII);
    private static final Charset WINDOWS_1252 = Charset.forName("windows-1252");

    public Extracted read(String fileName, String contentType, byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("Uploaded file is empty");
        }
        String name = fileName != null ? fileName.toLowerCase(Locale.ROOT) : "";
        String type = contentType != null ? contentType.toLowerCase(Locale.ROOT) : "";

        if (startsWith(bytes, PDF_MAGIC)) {
            return new Extracted(Kind.PDF, readPdf(bytes));
        }
        if (type.startsWith("image/") || name.matches(".*\\.(png|jpe?g|gif|webp|heic|bmp|tiff?)$") || looksLikeImage(bytes)) {
            throw unsupported("Image receipts need OCR, which is not supported. "
                    + "Upload a CSV or a text-based PDF, or enter the receipt manually.");
        }
        if (name.matches(".*\\.(xlsx?|docx?|zip)$")) {
            throw unsupported("Unsupported file type. Export the statement as CSV or PDF.");
        }
        return new Extracted(Kind.CSV, decodeText(bytes));
    }

    private String readPdf(byte[] bytes) {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            if (document.isEncrypted()) {
                throw unsupported("Password-protected PDFs are not supported. Remove the password and upload again.");
            }
            String text = new PDFTextStripper().getText(document);
            if (text == null || text.isBlank()) {
                throw unsupported("This PDF has no text layer (it looks like a scanned receipt). "
                        + "OCR is not supported; enter the receipt manually or upload a text-based PDF.");
            }
            return text;
        } catch (IOException ex) {
            throw new IllegalArgumentException("The PDF could not be read: it may be damaged");
        }
    }

    /** UTF-8 (with or without BOM) or UTF-16 with BOM; anything else is read as Windows-1252, the usual Excel export. */
    static String decodeText(byte[] bytes) {
        if (startsWith(bytes, new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF})) {
            return new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8);
        }
        if (startsWith(bytes, new byte[]{(byte) 0xFF, (byte) 0xFE})) {
            return new String(bytes, 2, bytes.length - 2, StandardCharsets.UTF_16LE);
        }
        if (startsWith(bytes, new byte[]{(byte) 0xFE, (byte) 0xFF})) {
            return new String(bytes, 2, bytes.length - 2, StandardCharsets.UTF_16BE);
        }
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException ex) {
            return new String(bytes, WINDOWS_1252);
        }
    }

    private static boolean looksLikeImage(byte[] b) {
        return startsWith(b, new byte[]{(byte) 0x89, 'P', 'N', 'G'})
                || startsWith(b, new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF})
                || startsWith(b, "GIF8".getBytes(StandardCharsets.US_ASCII));
    }

    private static boolean startsWith(byte[] bytes, byte[] prefix) {
        return bytes.length >= prefix.length && Arrays.equals(bytes, 0, prefix.length, prefix, 0, prefix.length);
    }

    private static ResponseStatusException unsupported(String reason) {
        return new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, reason);
    }
}
