package callum.nightingale.api.dto.s3.response;

import callum.nightingale.api.dto.s3.model.Bucket;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ListBucketsResponse {

  List<Bucket> buckets;
}
