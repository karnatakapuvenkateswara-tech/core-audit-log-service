package com.example.audit.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuditVerificationControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EventRecordRepository eventRecordRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private static final String VALID_EVENT = """
            {
              "eventType": "USER_LOGIN",
              "actorId": "user-verify",
              "resourceType": "SESSION",
              "resourceId": "session-verify",
              "payload": {"ip": "10.0.0.1"},
              "timestamp": "2026-08-08T12:00:00Z"
            }
            """;

    @Test
    void rejectsVerifyWithoutCredentials() throws Exception {
        mockMvc.perform(get("/audit/verify"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void reportsIntactChainAfterValidWrites() throws Exception {
        mockMvc.perform(post("/api/v1/events")
                        .with(SecurityMockMvcRequestPostProcessors.httpBasic("audit_writer_test", "audit_writer_test"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_EVENT))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/audit/verify")
                        .with(SecurityMockMvcRequestPostProcessors.httpBasic("audit_writer_test", "audit_writer_test")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intact").value(true))
                .andExpect(jsonPath("$.brokenRecordId").doesNotExist());
    }

    @Test
    void reportsContentHashMismatchWhenRecordTamperedDirectlyInDb() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();

        MvcResult result = mockMvc.perform(post("/api/v1/events")
                        .with(SecurityMockMvcRequestPostProcessors.httpBasic("audit_writer_test", "audit_writer_test"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_EVENT))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode created = objectMapper.readTree(result.getResponse().getContentAsString());
        String tamperedId = created.get("id").asText();

        jdbcTemplate.update(
                "UPDATE event_records SET actor_id = ? WHERE id = ?",
                "tampered-actor", tamperedId);

        try {
            mockMvc.perform(get("/audit/verify")
                            .with(SecurityMockMvcRequestPostProcessors.httpBasic("audit_writer_test", "audit_writer_test")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.intact").value(false))
                    .andExpect(jsonPath("$.brokenRecordId").value(tamperedId))
                    .andExpect(jsonPath("$.violationType").value("CONTENT_HASH_MISMATCH"));
        } finally {
            // The hash chain is intentionally immutable once written, so the tampered
            // row can't be "un-tampered" - remove it directly so it doesn't permanently
            // break the chain for every other test that shares this in-memory database.
            jdbcTemplate.update("DELETE FROM event_records WHERE id = ?", tamperedId);
        }
    }

    @Test
    void archivingARecordDoesNotBreakVerification() throws Exception {
        mockMvc.perform(post("/api/v1/events")
                        .with(SecurityMockMvcRequestPostProcessors.httpBasic("audit_writer_test", "audit_writer_test"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_EVENT))
                .andExpect(status().isCreated());

        int archived = transactionTemplate.execute(status -> eventRecordRepository.archiveEligibleRecords(
                Instant.now().plus(1, ChronoUnit.HOURS), Instant.now()));
        assertThat(archived).isGreaterThan(0);

        mockMvc.perform(get("/audit/verify")
                        .with(SecurityMockMvcRequestPostProcessors.httpBasic("audit_writer_test", "audit_writer_test")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intact").value(true))
                .andExpect(jsonPath("$.brokenRecordId").doesNotExist());

        mockMvc.perform(get("/api/v1/events")
                        .with(SecurityMockMvcRequestPostProcessors.httpBasic("audit_writer_test", "audit_writer_test"))
                        .param("actorId", "user-verify")
                        .param("archived", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].archived").value(true))
                .andExpect(jsonPath("$.content[0].archivedAt").exists());
    }

    @Test
    void redactingARecordDoesNotBreakVerification() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();

        MvcResult created = mockMvc.perform(post("/api/v1/events")
                        .with(SecurityMockMvcRequestPostProcessors.httpBasic("audit_writer_test", "audit_writer_test"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_EVENT))
                .andExpect(status().isCreated())
                .andReturn();
        String id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(post("/api/v1/events/" + id + "/redact")
                        .with(SecurityMockMvcRequestPostProcessors.httpBasic("audit_writer_test", "audit_writer_test")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.redacted").value(true))
                .andExpect(jsonPath("$.payload").value("[REDACTED]"));

        mockMvc.perform(get("/audit/verify")
                        .with(SecurityMockMvcRequestPostProcessors.httpBasic("audit_writer_test", "audit_writer_test")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intact").value(true))
                .andExpect(jsonPath("$.brokenRecordId").doesNotExist());
    }

    @Test
    void reportsPayloadHashMismatchWhenPayloadTamperedWithoutRedaction() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();

        MvcResult created = mockMvc.perform(post("/api/v1/events")
                        .with(SecurityMockMvcRequestPostProcessors.httpBasic("audit_writer_test", "audit_writer_test"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_EVENT))
                .andExpect(status().isCreated())
                .andReturn();
        String id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();

        jdbcTemplate.update("UPDATE event_records SET payload = ? WHERE id = ?", "{\"ip\":\"tampered\"}", id);

        try {
            mockMvc.perform(get("/audit/verify")
                            .with(SecurityMockMvcRequestPostProcessors.httpBasic("audit_writer_test", "audit_writer_test")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.intact").value(false))
                    .andExpect(jsonPath("$.brokenRecordId").value(id))
                    .andExpect(jsonPath("$.violationType").value("PAYLOAD_HASH_MISMATCH"));
        } finally {
            jdbcTemplate.update("DELETE FROM event_records WHERE id = ?", id);
        }
    }
}
