package callum.nightingale.api.dto.audit.request;

import callum.nightingale.api.dto.audit.model.AuditEventType;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditSearchRequest {

  AuditEventType eventType;
  String objectKey;
  String bucketName;
  String userName;
  LocalDateTime fromDate;
  LocalDateTime toDate;
}
