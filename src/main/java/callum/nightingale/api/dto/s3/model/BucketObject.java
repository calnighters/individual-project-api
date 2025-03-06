package callum.nightingale.api.dto.s3.model;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BucketObject {

  String objectKey;
  Long objectSize;
  LocalDateTime lastModifiedTimestamp;
}
