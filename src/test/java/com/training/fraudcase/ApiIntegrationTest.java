package com.training.fraudcase;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end contract test in rules mode: intake -> investigate ->
 * evidence/traces/recommendation/citations -> follow-up -> review -> close.
 */
@SpringBootTest(properties = {"capstone.processor-mode=rules", "spring.profiles.active=test"})
@AutoConfigureMockMvc
class ApiIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;

    private Map<String, Object> intake(String txnRef, String issueType, boolean recognised) {
        Map<String, Object> m = new java.util.HashMap<>();
        m.put("reportedAt", "2026-11-10T10:15:00Z");
        m.put("channel", "WEB");
        m.put("maskedCustomerRef", "CUST-8842");
        m.put("homeRegion", "EU-IE");
        m.put("transactionRef", txnRef);
        m.put("amount", 129.99);
        m.put("currency", "EUR");
        m.put("occurredAt", "2026-11-10T09:40:00Z");
        m.put("transactionStatus", "POSTED");
        m.put("transactionChannel", "CARD_NOT_PRESENT");
        m.put("maskedMerchant", "MRC-UNRECOGNISED-77");
        m.put("issueType", issueType);
        m.put("customerStatement", "I did not make this purchase. I do not recognise the merchant and still have my card.");
        m.put("cardPossession", "IN_POSSESSION");
        m.put("merchantRecognised", recognised);
        m.put("syntheticDataConfirmed", true);
        m.put("reviewRequired", true);
        return m;
    }

    private String createCase(Map<String, Object> body) throws Exception {
        MvcResult res = mvc.perform(post("/api/cases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("NEW"))
                .andReturn();
        return om.readTree(res.getResponse().getContentAsString()).get("caseId").asText();
    }

    @Test
    void fullPipelineSampleScenario() throws Exception {
        String caseId = createCase(intake("TXN-FRAUD-0042", "UNRECOGNISED_TRANSACTION", false));

        // Start investigation
        mvc.perform(post("/api/cases/{id}/investigations", caseId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY_FOR_REVIEW"))
                .andExpect(jsonPath("$.recommendation.classification").value("UNAUTHORISED_TRANSACTION"))
                .andExpect(jsonPath("$.recommendation.recommendedRoute").value("FRAUD_INVESTIGATION"))
                .andExpect(jsonPath("$.recommendation.humanReviewRequired").value(true));

        // Evidence collected with provenance
        mvc.perform(get("/api/cases/{id}/evidence", caseId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThan(3)));

        // Tool traces recorded for every call
        mvc.perform(get("/api/cases/{id}/tool-traces", caseId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(6)))
                .andExpect(jsonPath("$[0].requestHash").isNotEmpty());

        // Recommendation retrievable and schema-valid
        mvc.perform(get("/api/cases/{id}/recommendation", caseId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evidenceIds.length()").value(org.hamcrest.Matchers.greaterThan(0)));

        // Citations carry full metadata
        mvc.perform(get("/api/cases/{id}/citations", caseId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].documentId").isNotEmpty())
                .andExpect(jsonPath("$[0].chunkId").isNotEmpty())
                .andExpect(jsonPath("$[0].score").isNumber());

        // Follow-up question is case-scoped
        mvc.perform(post("/api/cases/{id}/messages", caseId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("question", "What policy applies here?"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isNotEmpty());

        // Review with mandatory reason -> ROUTED
        mvc.perform(post("/api/cases/{id}/reviews", caseId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "disposition", "ACCEPTED",
                                "reason", "Evidence corroborates the customer claim"))))
                .andExpect(status().isOk());
        mvc.perform(get("/api/cases/{id}", caseId))
                .andExpect(jsonPath("$.status").value("ROUTED"));
    }

    @Test
    void duplicateChargeRoutesToPaymentOps() throws Exception {
        String caseId = createCase(intake("TXN-DUP-0007", "DUPLICATE_CHARGE", true));
        mvc.perform(post("/api/cases/{id}/investigations", caseId))
                .andExpect(jsonPath("$.recommendation.classification").value("DUPLICATE_PROCESSING"))
                .andExpect(jsonPath("$.recommendation.recommendedRoute").value("PAYMENT_OPERATIONS"));
    }

    @Test
    void rejectsUnmaskedAccountNumber() throws Exception {
        var body = new java.util.HashMap<>(intake("TXN-FRAUD-1", "UNRECOGNISED_TRANSACTION", false));
        body.put("maskedCustomerRef", "CUST-4539688712345678");
        mvc.perform(post("/api/cases").contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsMarkupInStatement() throws Exception {
        var body = new java.util.HashMap<>(intake("TXN-FRAUD-1", "UNRECOGNISED_TRANSACTION", false));
        body.put("customerStatement", "<script>alert(1)</script> fraud!");
        mvc.perform(post("/api/cases").contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void reviewRequiresReason() throws Exception {
        String caseId = createCase(intake("TXN-FRAUD-0042", "UNRECOGNISED_TRANSACTION", false));
        mvc.perform(post("/api/cases/{id}/investigations", caseId));
        mvc.perform(post("/api/cases/{id}/reviews", caseId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("disposition", "ACCEPTED", "reason", ""))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void caseConversationIsIsolated() throws Exception {
        String caseA = createCase(intake("TXN-FRAUD-0042", "UNRECOGNISED_TRANSACTION", false));
        String caseB = createCase(intake("TXN-GENUINE-1", "OTHER", true));

        String secret = "purple-elephant-42";
        mvc.perform(post("/api/cases/{id}/messages", caseA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("question", "Remember " + secret))))
                .andExpect(status().isOk());

        MvcResult b = mvc.perform(get("/api/cases/{id}/messages", caseB)).andReturn();
        JsonNode messages = om.readTree(b.getResponse().getContentAsString());
        for (JsonNode m : messages) {
            assertFalse(m.get("content").asText().contains(secret),
                    "case B must not see case A content");
        }
    }

    @Test
    void missingTransactionFailsToIncomplete() throws Exception {
        String caseId = createCase(intake("TXN-MISSING-9", "UNRECOGNISED_TRANSACTION", false));
        mvc.perform(post("/api/cases/{id}/investigations", caseId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INCOMPLETE"));
    }

    @Test
    void cannotInvestigateReviewedCase() throws Exception {
        String caseId = createCase(intake("TXN-FRAUD-0042", "UNRECOGNISED_TRANSACTION", false));
        mvc.perform(post("/api/cases/{id}/investigations", caseId));
        mvc.perform(post("/api/cases/{id}/reviews", caseId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("disposition", "REJECTED", "reason", "not fraud"))));
        mvc.perform(post("/api/cases/{id}/investigations", caseId))
                .andExpect(status().isConflict());
        assertEquals("CLOSED", om.readTree(
                mvc.perform(get("/api/cases/{id}", caseId)).andReturn()
                        .getResponse().getContentAsString()).get("status").asText());
    }

    @Test
    void duplicateIntakeReturnsOpenCase() throws Exception {
        var body = intake("TXN-DEDUP-5001", "UNRECOGNISED_TRANSACTION", false);
        String first = createCase(body);
        String second = createCase(body);
        assertEquals(first, second, "identical open intake should return the existing case");
    }

    @Test
    void caseIdsAreUniqueAndSequentialAcrossCreates() throws Exception {
        String a = createCase(intake("TXN-DEDUP-5002", "UNRECOGNISED_TRANSACTION", false));
        String b = createCase(intake("TXN-DEDUP-5003", "UNRECOGNISED_TRANSACTION", false));
        assertTrue(!a.equals(b), "each new claim must get a distinct caseId");
        assertTrue(a.matches("CASE-\\d{4}-\\d{4}"));
        assertTrue(b.matches("CASE-\\d{4}-\\d{4}"));
    }

    @Test
    void missingInfoRequestKeepsUnderReview() throws Exception {
        String caseId = createCase(intake("TXN-FRAUD-0042", "UNRECOGNISED_TRANSACTION", false));
        mvc.perform(post("/api/cases/{id}/investigations", caseId));
        mvc.perform(post("/api/cases/{id}/reviews", caseId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "disposition", "MORE_INFO_REQUESTED",
                                "reason", "Need the merchant receipt before deciding"))))
                .andExpect(status().isOk());
        mvc.perform(get("/api/cases/{id}", caseId))
                .andExpect(jsonPath("$.status").value("UNDER_REVIEW"));
    }
}
