package com.example.ExpenseTracker.controller;
import com.example.ExpenseTracker.Exception.ResourceNotFoundException;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
@RestController
@RequestMapping("/api/imports")
public class ImportController {
public record CsvPreviewRequest(String csvContent) {}

        private final ImportService importService;

        public ImportController(ImportService importService) {
            this.importService = importService;
        }

        @PostMapping(value = "/preview", consumes = MediaType.APPLICATION_JSON_VALUE)
        public ResponseEntity<ImportPreviewResponse> previewJson(
                @RequestHeader(value = "X-User-Id", defaultValue = "1") Long userId,
                @RequestParam(required = false) String currency,
                @RequestParam(required = false) Long accountId,
                @RequestParam(required = false) Long categoryId,
                @RequestBody CsvPreviewRequest request) {
            String content = request != null ? request.csvContent() : null;
            return ResponseEntity.ok(
                    importService.previewCsv(userId, content, currency, accountId, categoryId));
        }

        @PostMapping(value = "/preview", consumes = MediaType.TEXT_PLAIN_VALUE)
        public ResponseEntity<ImportPreviewResponse> previewPlainText(
                @RequestHeader(value = "X-User-Id", defaultValue = "1") Long userId,
                @RequestParam(required = false) String currency,
                @RequestParam(required = false) Long accountId,
                @RequestParam(required = false) Long categoryId,
                @RequestBody String csvContent) {
            return ResponseEntity.ok(
                    importService.previewCsv(userId, csvContent, currency, accountId, categoryId));
        }

        @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        public ResponseEntity<ImportPreviewResponse> previewMultipart(
                @RequestHeader(value = "X-User-Id", defaultValue = "1") Long userId,
                @RequestParam(required = false) String currency,
                @RequestParam(required = false) Long accountId,
                @RequestParam(required = false) Long categoryId,
                @RequestParam("file") MultipartFile file) {
            if (file == null || file.isEmpty()) {
                throw new IllegalArgumentException("CSV file is empty");
            }
            String content;
            try {
                content = new String(file.getBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new ResourceNotFoundException("Failed to read uploaded CSV file");
            }
            return ResponseEntity.ok(
                    importService.previewCsv(userId, content, currency, accountId, categoryId));
        }

        @PostMapping("/confirm")
        public ResponseEntity<ImportResultResponse> confirmImport(
                @RequestHeader(value = "X-User-Id", defaultValue = "1") Long userId,
                @Valid @RequestBody ConfirmImportRequest request) {
            ImportResultResponse result = importService.confirmImport(userId, request);
            return ResponseEntity.status(HttpStatus.CREATED).body(result);
        }
    };

