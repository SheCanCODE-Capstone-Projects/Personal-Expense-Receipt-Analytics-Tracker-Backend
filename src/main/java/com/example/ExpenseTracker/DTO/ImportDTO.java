package com.example.ExpenseTracker.DTO;

import java.time.LocalDate;
import java.util.List;

public  final class ImportDTO {
    private ImportDTO() {}

     record PreviewRow(
             int line, LocalDate date, String description, Long amount, String type,
             Long categoryId, String categoryName, String paymentMethod, String currency,
             List<String> errors, boolean duplicate, String duplicateReason, boolean include) {}

    public record ImportPreview(Long batchId, String fileType, String fileName, List<PreviewRow> rows) {}
    public record ConfirmRow(
            boolean include, LocalDate date, String description, Long amount, String type,
            Long categoryId, String paymentMethod, boolean allowDuplicate) {}

    public record ConfirmRequest(List<ConfirmRow> rows) {}

    public record ConfirmResult(Long batchId, int imported, int skipped, List<String> messages) {}

    public record BatchSummary(Long id, String fileName, String fileType, String status,
                               int rowsTotal, int rowsImported, int rowsSkipped, String createdAt) {}
}


