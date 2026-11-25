package com.example.ExpenseTracker.DTO;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class ImportDTO {
    private ImportDTO() {}

    public record PreviewRow(
            int line, LocalDate date, String description, Long amount, String type,
            UUID categoryId, String categoryName, String paymentMethod, String currency,
            List<String> errors, boolean duplicate, String duplicateReason, boolean include) {}

    public record ImportPreview(UUID batchId, String fileType, String fileName, List<PreviewRow> rows) {}

    public record ConfirmRow(
            boolean include, LocalDate date, String description, Long amount, String type,
            UUID categoryId, String paymentMethod, boolean allowDuplicate) {}

    public record ConfirmRequest(List<ConfirmRow> rows) {}

    public record ConfirmResult(UUID batchId, int imported, int skipped, List<String> messages) {}

    public record BatchSummary(UUID id, String fileName, String fileType, String status,
                               int rowsTotal, int rowsImported, int rowsSkipped, String createdAt) {}
}