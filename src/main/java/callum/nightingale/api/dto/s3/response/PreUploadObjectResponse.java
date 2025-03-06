package callum.nightingale.api.dto.s3.response;

import com.github.difflib.patch.AbstractDelta;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PreUploadObjectResponse {

  boolean newUpload;
  List<String> unifiedDiff;
}
