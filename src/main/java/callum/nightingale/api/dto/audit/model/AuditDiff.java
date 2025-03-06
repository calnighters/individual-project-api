package callum.nightingale.api.dto.audit.model;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AuditDiff {

  List<String> unifiedDiff;
}
