package com.example.audit.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EventControllerTests {

    @Autowired
    private MockMvc mockMvc;

    private static final String VALID_EVENT = """
            {
              "eventType": "USER_LOGIN",
              "actorId": "user-123",
              "resourceType": "SESSION",
              "resourceId": "session-456",
              "payload": {"ip": "10.0.0.1"},
              "timestamp": "2026-08-08T12:00:00Z"
            }
            """;

    @Test
    void rejectsRequestWithoutCredentials() throws Exception {
        mockMvc.perform(post("/api/v1/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_EVENT))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void acceptsValidEventWithCredentials() throws Exception {
        mockMvc.perform(post("/api/v1/events")
                        .with(SecurityMockMvcRequestPostProcessors.httpBasic("audit_writer_test", "audit_writer_test"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_EVENT))
                .andExpect(status().isCreated());
    }

    @Test
    void rejectsInvalidCredentials() throws Exception {
        mockMvc.perform(post("/api/v1/events")
                        .with(SecurityMockMvcRequestPostProcessors.httpBasic("audit_writer_test", "wrong-password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_EVENT))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsMissingRequiredFields() throws Exception {
        String invalidEvent = """
                {
                  "eventType": "",
                  "payload": {"ip": "10.0.0.1"},
                  "timestamp": "2026-08-08T12:00:00Z"
                }
                """;

        mockMvc.perform(post("/api/v1/events")
                        .with(SecurityMockMvcRequestPostProcessors.httpBasic("audit_writer_test", "audit_writer_test"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidEvent))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsQueryWithoutCredentials() throws Exception {
        mockMvc.perform(get("/api/v1/events"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void queryReturnsMatchingEventsForActor() throws Exception {
        mockMvc.perform(post("/api/v1/events")
                        .with(SecurityMockMvcRequestPostProcessors.httpBasic("audit_writer_test", "audit_writer_test"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_EVENT))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/events")
                        .with(SecurityMockMvcRequestPostProcessors.httpBasic("audit_writer_test", "audit_writer_test"))
                        .param("actorId", "user-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].actorId").value("user-123"));
    }

    @Test
    void rejectsResourceIdWithoutResourceType() throws Exception {
        mockMvc.perform(get("/api/v1/events")
                        .with(SecurityMockMvcRequestPostProcessors.httpBasic("audit_writer_test", "audit_writer_test"))
                        .param("resourceId", "session-456"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void chainsSecondEventHashToFirstEventHash() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();

        MvcResult firstResult = mockMvc.perform(post("/api/v1/events")
                        .with(SecurityMockMvcRequestPostProcessors.httpBasic("audit_writer_test", "audit_writer_test"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_EVENT))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode first = objectMapper.readTree(firstResult.getResponse().getContentAsString());

        MvcResult secondResult = mockMvc.perform(post("/api/v1/events")
                        .with(SecurityMockMvcRequestPostProcessors.httpBasic("audit_writer_test", "audit_writer_test"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_EVENT))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode second = objectMapper.readTree(secondResult.getResponse().getContentAsString());

        assertThat(first.get("contentHash").asText()).hasSize(64);
        assertThat(first.get("previousHash").asText()).hasSize(64);
        assertThat(second.get("previousHash").asText()).isEqualTo(first.get("contentHash").asText());
        assertThat(second.get("contentHash").asText()).isNotEqualTo(first.get("contentHash").asText());
    }
}
