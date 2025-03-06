package callum.nightingale.api.dto.audit.model;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AuditInfo {

  AuditEventType eventType;
  String objectKey;
  String bucketName;
  String userName;
  LocalDateTime auditDate;
  String auditObjectKey;
}
