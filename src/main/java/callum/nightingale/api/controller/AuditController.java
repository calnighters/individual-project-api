package callum.nightingale.api.controller;

import callum.nightingale.api.dto.audit.model.AuditDiff;
import callum.nightingale.api.dto.audit.request.AuditDiffRequest;
import callum.nightingale.api.dto.audit.request.AuditSearchRequest;
import callum.nightingale.api.dto.audit.response.AuditSearchResponse;
import callum.nightingale.api.service.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/audit")
@RequiredArgsConstructor
public class AuditController {

  private final AuditService auditService;

  @PostMapping("/search")
  public AuditSearchResponse searchAuditRecords(@RequestBody AuditSearchRequest request) {
    return auditService.searchObjectsByMetadata(request);
  }

  @PostMapping("/diff")
  public AuditDiff getAuditDiff(@RequestBody AuditDiffRequest request) {
    return auditService.getAuditDiff(request.getAuditObjectKey());
  }
}
