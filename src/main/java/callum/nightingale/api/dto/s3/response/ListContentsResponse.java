package callum.nightingale.api.dto.s3.response;

import callum.nightingale.api.dto.s3.model.BucketObject;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ListContentsResponse {

  List<BucketObject> objects;
}
