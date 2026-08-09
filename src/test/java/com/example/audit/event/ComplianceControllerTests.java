package com.example.audit.event;

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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ComplianceControllerTests {

    @Autowired
    private MockMvc mockMvc;

    private static final String VALID_EVENT = """
            {
              "eventType": "ACCOUNT_VIEWED",
              "actorId": "employee-789",
              "resourceType": "ACCOUNT",
              "resourceId": "account-compliance-1",
              "payload": {"reason": "support ticket"},
              "timestamp": "2026-08-08T12:00:00Z"
            }
            """;

    @Test
    void rejectsAccessReportWithoutCredentials() throws Exception {
        mockMvc.perform(get("/api/v1/compliance/access-report")
                        .param("resourceId", "account-compliance-1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsWriterCredentialsForAccessReport() throws Exception {
        mockMvc.perform(get("/api/v1/compliance/access-report")
                        .with(SecurityMockMvcRequestPostProcessors.httpBasic("audit_writer_test", "audit_writer_test"))
                        .param("resourceId", "account-compliance-1"))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsAuditorCredentialsForWrite() throws Exception {
        mockMvc.perform(post("/api/v1/events")
                        .with(SecurityMockMvcRequestPostProcessors.httpBasic("audit_reader_test", "audit_reader_test"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_EVENT))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsAccessReportWithoutResourceId() throws Exception {
        mockMvc.perform(get("/api/v1/compliance/access-report")
                        .with(SecurityMockMvcRequestPostProcessors.httpBasic("audit_reader_test", "audit_reader_test")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void auditorCanRetrieveAccessTrailForResource() throws Exception {
        mockMvc.perform(post("/api/v1/events")
                        .with(SecurityMockMvcRequestPostProcessors.httpBasic("audit_writer_test", "audit_writer_test"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_EVENT))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/compliance/access-report")
                        .with(SecurityMockMvcRequestPostProcessors.httpBasic("audit_reader_test", "audit_reader_test"))
                        .param("resourceId", "account-compliance-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].resourceId").value("account-compliance-1"))
                .andExpect(jsonPath("$[0].eventType").value("ACCOUNT_VIEWED"))
                .andExpect(jsonPath("$[0].actorId").value("employee-789"));
    }
}
