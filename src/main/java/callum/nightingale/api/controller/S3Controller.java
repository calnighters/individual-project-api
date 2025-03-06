package callum.nightingale.api.controller;

import callum.nightingale.api.dto.s3.request.GetFileRequest;
import callum.nightingale.api.dto.s3.response.ListBucketsResponse;
import callum.nightingale.api.dto.s3.response.ListContentsResponse;
import callum.nightingale.api.dto.s3.response.PreUploadObjectResponse;
import callum.nightingale.api.service.S3Service;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/s3")
@RequiredArgsConstructor
public class S3Controller {

  private final S3Service s3Service;

  @GetMapping("/buckets")
  public ListBucketsResponse listBuckets() {
    return s3Service.listBuckets();
  }

  @GetMapping("/buckets/{bucketName}/contents")
  public ListContentsResponse listContents(@PathVariable String bucketName) {
    return s3Service.listContents(bucketName);
  }

  @PostMapping("/buckets/{bucketName}/file")
  public ResponseEntity<InputStreamResource> getFile(@PathVariable String bucketName,
      @RequestBody GetFileRequest request) {
    return s3Service.getFile(bucketName, request.getObjectKey());
  }

  @PostMapping("/buckets/{bucketName}/pre-upload")
  public PreUploadObjectResponse preUploadObject(
      @PathVariable String bucketName,
      @RequestParam("file") MultipartFile file,
      @RequestParam("objectKey") String objectKey) {
    return s3Service.preUploadFile(bucketName, file, objectKey);
  }

  @PostMapping("/buckets/{bucketName}/upload")
  public void uploadObject(
      @PathVariable String bucketName,
      @RequestParam("file") MultipartFile file,
      @RequestParam("objectKey") String objectKey) {
    s3Service.uploadFile(bucketName, file, objectKey);
  }
}
