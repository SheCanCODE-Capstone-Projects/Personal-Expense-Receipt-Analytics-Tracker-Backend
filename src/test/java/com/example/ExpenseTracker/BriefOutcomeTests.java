package com.example.ExpenseTracker;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** End-to-end checks of the outcomes the project brief asks for, through the public API. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BriefOutcomeTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockitoBean JavaMailSender mail;

    private static final String STATEMENT = """
            date,amount,type,merchant,description
            2026-10-02,12500,EXPENSE,Simba Supermarket,Groceries
            2026-10-03,"3,000",EXPENSE,Moto,Commute
            2026-10-05,450000,INCOME,Employer Ltd,Salary
            """;

    // ---- helpers ----

    private String signIn(String email) throws Exception {
        mvc.perform(post("/api/auth/otp/request").contentType("application/json")
                .content(json.writeValueAsBytes(Map.of("email", email)))).andExpect(status().isAccepted());
        var sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mail, atLeastOnce()).send(sent.capture());
        var matcher = Pattern.compile("[0-9]{6}").matcher(sent.getValue().getText());
        assertTrue(matcher.find());
        String body = mvc.perform(post("/api/auth/otp/verify").contentType("application/json")
                        .content(json.writeValueAsBytes(Map.of("email", email, "code", matcher.group(),
                                "fullName", "Test User", "password", "Str0ngPass"))))
                .andExpect(status().isOk()).andReturn().getResponse().getCookie("access_token").getValue();
        return body;
    }

    private static String email(String name) {
        return name + "-" + UUID.randomUUID() + "@example.test";
    }

    private JsonNode call(String token, AbstractMockHttpServletRequestBuilder<?> request, int expectedStatus) throws Exception {
        var response = mvc.perform(request.header("Authorization", "Bearer " + token)).andReturn().getResponse();
        String body = response.getContentAsString();
        assertEquals(expectedStatus, response.getStatus(), body);
        return body.isEmpty() ? null : json.readTree(body);
    }

    private JsonNode send(String token, AbstractMockHttpServletRequestBuilder<?> request, Object body, int expectedStatus) throws Exception {
        return call(token, request.contentType("application/json").content(json.writeValueAsBytes(body)), expectedStatus);
    }

    private JsonNode upload(String token, String name, String type, byte[] content, int expectedStatus) throws Exception {
        return call(token, multipart("/api/imports/preview").file(new MockMultipartFile("file", name, type, content)), expectedStatus);
    }

    private JsonNode confirm(String token, JsonNode preview) throws Exception {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (JsonNode row : preview.get("rows")) {
            Map<String, Object> input = new HashMap<>();
            input.put("date", row.get("date").asString());
            input.put("amount", row.get("amount").asLong());
            input.put("type", row.get("type").asString());
            input.put("merchant", row.get("merchant").isNull() ? null : row.get("merchant").asString());
            input.put("description", row.get("description").isNull() ? null : row.get("description").asString());
            rows.add(input);
        }
        return send(token, post("/api/imports/confirm"), Map.of("fileName", "statement.csv", "rows", rows), 201);
    }

    private int transactionCount(String token, String from, String to) throws Exception {
        return call(token, get("/api/transactions").param("from", from).param("to", to), 200).size();
    }

    // ---- brief outcome 1: re-importing a statement adds no duplicates ----

    @Test
    void importingTheSameStatementTwiceAddsNoDuplicateTransactions() throws Exception {
        String token = signIn(email("importer"));
        byte[] csv = STATEMENT.getBytes();

        JsonNode first = upload(token, "statement.csv", "text/csv", csv, 200);
        assertEquals(3, first.get("validRows").asInt());
        assertEquals(0, first.get("duplicateRows").asInt());
        assertEquals(3000, first.get("rows").get(1).get("amount").asLong());
        assertEquals(3, confirm(token, first).get("importedCount").asInt());
        assertEquals(3, transactionCount(token, "2026-10-01", "2026-10-31"));

        JsonNode second = upload(token, "statement.csv", "text/csv", csv, 200);
        assertEquals(3, second.get("duplicateRows").asInt());
        for (JsonNode row : second.get("rows")) {
            assertEquals("Already in your transactions", row.get("duplicateReason").asString());
        }
        JsonNode result = confirm(token, second);
        assertEquals(0, result.get("importedCount").asInt());
        assertEquals(3, result.get("skippedDuplicates").asInt());
        assertEquals(3, transactionCount(token, "2026-10-01", "2026-10-31"));
    }

    @Test
    void textPdfStatementsAreParsedAndScannedPdfsAreRejected() throws Exception {
        String token = signIn(email("pdf"));

        JsonNode preview = upload(token, "statement.pdf", "application/pdf", pdf(
                "Statement October 2026",
                "02/10/2026 Simba Supermarket 12,500.00 237,500.00",
                "05/10/2026 Salary credit 450,000.00 687,500.00"), 200);
        assertEquals("PDF", preview.get("fileType").asString());
        assertEquals(2, preview.get("validRows").asInt());
        assertEquals(12500, preview.get("rows").get(0).get("amount").asLong());
        assertEquals("INCOME", preview.get("rows").get(1).get("type").asString());
        assertEquals(2, confirm(token, preview).get("importedCount").asInt());

        JsonNode scanned = upload(token, "scan.pdf", "application/pdf", pdf(), 415);
        assertTrue(scanned.get("message").asString().contains("no text layer"));
        upload(token, "receipt.jpg", "image/jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0}, 415);
    }

    private static byte[] pdf(String... lines) throws Exception {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            if (lines.length > 0) {
                try (PDPageContentStream content = new PDPageContentStream(doc, page)) {
                    content.beginText();
                    content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 11);
                    content.setLeading(14);
                    content.newLineAtOffset(50, 700);
                    for (String line : lines) {
                        content.showText(line);
                        content.newLine();
                    }
                    content.endText();
                }
            }
            doc.save(out);
            return out.toByteArray();
        }
    }

    // ---- brief outcome 2: a RWF 30,000 equal split among three settles to zero ----

    @Test
    void settlingAThreeWayRwf30000SplitLeavesEveryBalanceAtZero() throws Exception {
        String bEmail = email("bea"), cEmail = email("chris");
        String a = signIn(email("alice"));
        String b = signIn(bEmail);
        signIn(cEmail);

        JsonNode owner = send(a, post("/api/groups"), Map.of("name", "Flatmates"), 201);
        String groupId = call(a, get("/api/groups"), 200).get(0).get("id").asString();
        String bMember = send(a, post("/api/groups/" + groupId + "/members"), Map.of("email", bEmail), 201).get("id").asString();
        send(a, post("/api/groups/" + groupId + "/members"), Map.of("email", cEmail), 201);

        JsonNode expense = send(a, post("/api/groups/" + groupId + "/expenses"), Map.of(
                "description", "Dinner", "totalAmount", 30_000, "paidByMemberId", owner.get("id").asString()), 201);
        assertEquals("EQUAL", expense.get("splitType").asString());
        for (JsonNode share : expense.get("shares")) {
            assertEquals(10_000, share.get("amount").asLong());
        }

        JsonNode balances = call(b, get("/api/groups/" + groupId + "/balances"), 200);
        assertEquals(List.of(20_000L, -10_000L, -10_000L), nets(balances));

        JsonNode transfers = call(b, get("/api/groups/" + groupId + "/settlements/suggestions"), 200);
        assertEquals(2, transfers.size());
        for (JsonNode t : transfers) {
            assertEquals(owner.get("id").asString(), t.get("toId").asString());
            assertEquals(10_000, t.get("amount").asLong());
            // Each debtor records their own payment.
            send(b, post("/api/groups/" + groupId + "/settlements"), Map.of(
                    "fromMemberId", t.get("fromId").asString(), "toMemberId", t.get("toId").asString(),
                    "amount", t.get("amount").asLong()), 201);
        }

        assertEquals(List.of(0L, 0L, 0L), nets(call(a, get("/api/groups/" + groupId + "/balances"), 200)));
        assertEquals(0, call(a, get("/api/groups/" + groupId + "/settlements/suggestions"), 200).size());
        assertEquals(2, call(a, get("/api/groups/" + groupId + "/settlements"), 200).size());
        assertNotNull(bMember);
    }

    @Test
    void groupBalancesDoNotMixCurrencies() throws Exception {
        String bEmail = email("bea");
        String a = signIn(email("alice"));
        signIn(bEmail);
        String payer = send(a, post("/api/groups"), Map.of("name", "Trip"), 201).get("id").asString();
        String groupId = call(a, get("/api/groups"), 200).get(0).get("id").asString();
        send(a, post("/api/groups/" + groupId + "/members"), Map.of("email", bEmail), 201);

        send(a, post("/api/groups/" + groupId + "/expenses"),
                Map.of("description", "Hotel", "totalAmount", 30_000, "paidByMemberId", payer), 201);
        send(a, post("/api/groups/" + groupId + "/expenses").param("currency", "USD"),
                Map.of("description", "Museum", "totalAmount", 40, "paidByMemberId", payer), 201);

        assertEquals(List.of(15_000L, -15_000L), nets(call(a, get("/api/groups/" + groupId + "/balances"), 200)));
        JsonNode usd = call(a, get("/api/groups/" + groupId + "/balances").param("currency", "USD"), 200);
        assertEquals(List.of(20L, -20L), nets(usd));
        assertEquals("USD", usd.get(0).get("currency").asString());
    }

    private static List<Long> nets(JsonNode balances) {
        List<Long> nets = new ArrayList<>();
        balances.forEach(b -> nets.add(b.get("net").asLong()));
        return nets;
    }

    // ---- login, categories and the personal dashboard work for a brand-new member ----

    @Test
    void aNewMemberCanUseCategoriesAndOpenTheirOwnReconciledDashboard() throws Exception {
        String token = signIn(email("member"));

        JsonNode categories = call(token, get("/api/categories").param("kind", "EXPENSE"), 200);
        assertTrue(categories.size() >= 5);
        String food = null;
        for (JsonNode c : categories) {
            if (c.get("name").asString().equals("Food & Groceries")) {
                food = c.get("id").asString();
            }
        }
        assertNotNull(food);
        String gym = send(token, post("/api/categories"), Map.of("name", "Gym", "kind", "EXPENSE"), 201).get("id").asString();
        send(token, post("/api/categories"), Map.of("name", "gym", "kind", "EXPENSE"), 400);

        send(token, post("/api/transactions"), tx("INCOME", 500_000, "2026-10-01", null), 201);
        send(token, post("/api/transactions"), tx("EXPENSE", 60_000, "2026-10-03", food), 201);
        send(token, post("/api/transactions"), tx("EXPENSE", 40_000, "2026-10-20", food), 201);
        send(token, post("/api/transactions"), tx("EXPENSE", 15_000, "2026-10-21", gym), 201);
        JsonNode uncategorised = send(token, post("/api/transactions"), tx("EXPENSE", 10_000, "2026-10-25", null), 201);
        assertEquals("Other", uncategorised.get("categoryName").asString());
        send(token, post("/api/transactions"), tx("INCOME", 1_000, "2026-10-26", food), 400);
        send(token, post("/api/budgets"), Map.of("month", "2026-10", "categoryId", food, "limitAmount", 120_000), 200);

        JsonNode dashboard = call(token, get("/api/dashboard").param("month", "2026-10"), 200);
        assertEquals(500_000, dashboard.get("incomeTotal").asLong());
        assertEquals(125_000, dashboard.get("expenseTotal").asLong());
        assertEquals(375_000, dashboard.get("net").asLong());
        long categorySum = 0;
        for (JsonNode c : dashboard.get("expensesByCategory")) {
            categorySum += c.get("amount").asLong();
        }
        assertEquals(dashboard.get("expenseTotal").asLong(), categorySum);
        assertEquals(100_000, dashboard.get("expensesByCategory").get(0).get("amount").asLong());

        JsonNode budget = dashboard.get("budgets").get(0);
        assertEquals(100_000, budget.get("spent").asLong());
        assertEquals("WARNING", budget.get("status").asString());
        // October has finished, so the forecast equals actual spending.
        assertEquals(125_000, dashboard.get("forecast").get("projectedTotalExpense").asLong());
    }

    private static Map<String, Object> tx(String type, long amount, String date, String categoryId) {
        Map<String, Object> body = new HashMap<>(Map.of("type", type, "amount", amount, "date", date, "currency", "RWF"));
        if (categoryId != null) {
            body.put("categoryId", categoryId);
        }
        return body;
    }
}
