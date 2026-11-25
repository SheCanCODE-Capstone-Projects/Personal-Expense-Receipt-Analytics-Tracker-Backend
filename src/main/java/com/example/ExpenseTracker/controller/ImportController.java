package com.example.ExpenseTracker.controller;
import com.example.ExpenseTracker.service.ImportFileReader;
import com.example.ExpenseTracker.service.ImportService;
import com.example.ExpenseTracker.service.ImportService.ConfirmImportRequest;
import com.example.ExpenseTracker.service.ImportService.ImportPreviewResponse;
import com.example.ExpenseTracker.service.ImportService.ImportResultResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;
@RestController
@RequestMapping("/api/imports")
@io.swagger.v3.oas.annotations.tags.Tag(name = "Imports", description = "Preview a CSV or text-based PDF, correct the rows, then confirm. Scanned or image receipts get 415.")
public class ImportController {
public record CsvPreviewRequest(String csvContent) {}

        private final ImportService importService;
        private final ImportFileReader fileReader;

        public ImportController(ImportService importService, ImportFileReader fileReader) {
            this.importService = importService;
            this.fileReader = fileReader;
        }

        @PostMapping(value = "/preview", consumes = MediaType.APPLICATION_JSON_VALUE)
        public ResponseEntity<ImportPreviewResponse> previewJson(
                @AuthenticationPrincipal UUID userId,
                @RequestParam(required = false) String currency,
                @RequestParam(required = false) UUID accountId,
                @RequestParam(required = false) UUID categoryId,
                @RequestBody CsvPreviewRequest request) {
            String content = request != null ? request.csvContent() : null;
            return ResponseEntity.ok(
                    importService.previewCsv(userId, content, currency, accountId, categoryId));
        }

        @PostMapping(value = "/preview", consumes = MediaType.TEXT_PLAIN_VALUE)
        public ResponseEntity<ImportPreviewResponse> previewPlainText(
                @AuthenticationPrincipal UUID userId,
                @RequestParam(required = false) String currency,
                @RequestParam(required = false) UUID accountId,
                @RequestParam(required = false) UUID categoryId,
                @RequestBody String csvContent) {
            return ResponseEntity.ok(
                    importService.previewCsv(userId, csvContent, currency, accountId, categoryId));
        }

        @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        public ResponseEntity<ImportPreviewResponse> previewMultipart(
                @AuthenticationPrincipal UUID userId,
                @RequestParam(required = false) String currency,
                @RequestParam(required = false) UUID accountId,
                @RequestParam(required = false) UUID categoryId,
                @RequestParam("file") MultipartFile file) {
            if (file == null || file.isEmpty()) {
                throw new IllegalArgumentException("Uploaded file is empty");
            }
            ImportFileReader.Extracted extracted;
            try {
                extracted = fileReader.read(file.getOriginalFilename(), file.getContentType(), file.getBytes());
            } catch (IOException e) {
                throw new IllegalArgumentException("Failed to read the uploaded file");
            }
            return ResponseEntity.ok(extracted.kind() == ImportFileReader.Kind.PDF
                    ? importService.previewPdfText(userId, extracted.text(), currency, accountId, categoryId)
                    : importService.previewCsv(userId, extracted.text(), currency, accountId, categoryId));
        }

        @PostMapping("/confirm")
        public ResponseEntity<ImportResultResponse> confirmImport(
                @AuthenticationPrincipal UUID userId,
                @Valid @RequestBody ConfirmImportRequest request) {
            ImportResultResponse result = importService.confirmImport(userId, request);
            return ResponseEntity.status(HttpStatus.CREATED).body(result);
        }
    };

