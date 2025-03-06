package callum.nightingale.api.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import callum.nightingale.api.dto.audit.model.AuditEventType;
import callum.nightingale.api.dto.audit.model.AuditInfo;
import callum.nightingale.api.dto.audit.request.AuditSearchRequest;
import callum.nightingale.api.dto.audit.response.AuditSearchResponse;
import callum.nightingale.api.service.AuditService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@ExtendWith(MockitoExtension.class)
@AutoConfigureMockMvc
@DisplayName("Integration Testing Audit Controller")
public class AuditControllerIT {

  @Autowired
  private MockMvc mvc;

  @MockitoBean
  private AuditService auditService;

  @Nested
  @DisplayName("POST /api/v1/audit/search")
  class SearchAudit {

    @Test
    @DisplayName("When audit logs are found then a response containing the audit logs is returned")
    void auditLogsFound() throws Exception {
      AuditSearchRequest request = AuditSearchRequest.builder()
          .bucketName("bucketName")
          .objectKey("objectKey")
          .userName("userName")
          .fromDate(LocalDateTime.of(2023, 1, 1, 0, 0, 0))
          .toDate(LocalDateTime.of(2024, 12, 31, 23, 59, 59))
          .eventType(AuditEventType.MODIFY)
          .build();
      when(auditService.searchObjectsByMetadata(request)).thenReturn(AuditSearchResponse.builder()
          .auditRecords(List.of(
              AuditInfo.builder()
                  .auditDate(LocalDateTime.MIN)
                  .auditObjectKey("auditObjectKey")
                  .bucketName("bucketName")
                  .build(),
              AuditInfo.builder()
                  .auditDate(LocalDateTime.MAX)
                  .auditObjectKey("auditObjectKey")
                  .bucketName("bucketName")
                  .build()
          ))
          .build());

      mvc.perform(post("/api/v1/audit/search")
          .content("""
              {
                "bucketName": "bucketName",
                "objectKey": "objectKey",
                "userName": "userName",
                "fromDate": "2023-01-01T00:00:00",
                "toDate": "2024-12-31T23:59:59",
                "eventType": "MODIFY_OBJECT"
              }
              """)
          .contentType(MediaType.APPLICATION_JSON))
          .andExpect(status().isOk());
    }
  }
}
