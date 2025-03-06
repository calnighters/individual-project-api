package callum.nightingale.api.dto.audit.response;

import callum.nightingale.api.dto.audit.model.AuditInfo;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AuditSearchResponse {

  List<AuditInfo> auditRecords;
}
