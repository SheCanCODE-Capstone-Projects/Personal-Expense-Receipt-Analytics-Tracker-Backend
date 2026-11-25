package com.example.ExpenseTracker;

import com.example.ExpenseTracker.Model.ETransactionType;
import com.example.ExpenseTracker.core.StatementTextParser;
import com.example.ExpenseTracker.service.ImportFileReader;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class ImportParsingTests {

    private final ImportFileReader reader = new ImportFileReader();

    @Test
    void statementLinesUseTheTransactionAmountNotTheRunningBalance() {
        var rows = StatementTextParser.parse("""
                Bank of Kigali - Statement
                Date        Description            Amount      Balance
                02/11/2026  Simba Supermarket      12,500.00   237,500.00
                2026-11-05  Salary credit          450,000.00  687,500.00
                Opening balance 250,000.00
                """);
        assertEquals(2, rows.size());
        assertEquals(LocalDate.of(2026, 11, 2), rows.get(0).date());
        assertEquals(12_500L, rows.get(0).amount());
        assertEquals(ETransactionType.EXPENSE, rows.get(0).type());
        assertEquals("Simba Supermarket", rows.get(0).description());
        assertEquals(ETransactionType.INCOME, rows.get(1).type());
    }

    @Test
    void receiptsBecomeOneRowFromTheTotalLine() {
        var rows = StatementTextParser.parse("""
                Simba Supermarket
                Kigali, KN 4 Ave
                Date: 14 Nov 2026
                Milk 2 x 800
                Subtotal 4,400
                TOTAL RWF 4400
                """);
        assertEquals(1, rows.size());
        assertEquals(4_400L, rows.getFirst().amount());
        assertEquals(LocalDate.of(2026, 11, 14), rows.getFirst().date());
        assertEquals("Simba Supermarket", rows.getFirst().merchant());
    }

    @Test
    void decodesUtf8BomAndWindows1252() {
        byte[] bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF, 'd', 'a', 't', 'e'};
        assertEquals("date", reader.read("a.csv", "text/csv", bom).text());

        byte[] latin = "2026-11-01,5000,EXPENSE,Café Néo".getBytes(Charset.forName("windows-1252"));
        assertTrue(reader.read("a.csv", "text/csv", latin).text().endsWith("Café Néo"));

        byte[] utf8 = "2026-11-01,5000,EXPENSE,Café Néo".getBytes(StandardCharsets.UTF_8);
        assertTrue(reader.read("a.csv", "text/csv", utf8).text().endsWith("Café Néo"));
    }

    @Test
    void bundledSamplePdfsParse() throws Exception {
        var statement = reader.read("s.pdf", "application/pdf",
                Files.readAllBytes(Path.of("samples/statement-november-2026.pdf")));
        assertEquals(ImportFileReader.Kind.PDF, statement.kind());
        var rows = StatementTextParser.parse(statement.text());
        assertEquals(5, rows.size());
        assertEquals(12_500L, rows.getFirst().amount());
        assertEquals(1, rows.stream().filter(r -> r.type() == ETransactionType.INCOME).count());

        var receipt = StatementTextParser.parse(reader.read("r.pdf", "application/pdf",
                Files.readAllBytes(Path.of("samples/receipt-simba.pdf"))).text());
        assertEquals(1, receipt.size());
        assertEquals(4_400L, receipt.getFirst().amount());
    }

    @Test
    void imagesAreRejectedWithAClearReason() {
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A};
        var ex = assertThrows(ResponseStatusException.class, () -> reader.read("receipt.png", "image/png", png));
        assertEquals(415, ex.getStatusCode().value());
        assertTrue(ex.getReason().contains("OCR"));
    }
}
