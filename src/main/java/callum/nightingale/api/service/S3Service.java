package callum.nightingale.api.service;

import callum.nightingale.api.dto.audit.model.AuditEventType;
import callum.nightingale.api.dto.s3.model.Bucket;
import callum.nightingale.api.dto.s3.model.BucketObject;
import callum.nightingale.api.dto.s3.response.ListBucketsResponse;
import callum.nightingale.api.dto.s3.response.ListContentsResponse;
import callum.nightingale.api.dto.s3.response.PreUploadObjectResponse;
import callum.nightingale.api.exception.ForbiddenException;
import callum.nightingale.api.exception.NotFoundException;
import callum.nightingale.api.properties.S3Properties;
import com.github.difflib.DiffUtils;
import com.github.difflib.UnifiedDiffUtils;
import com.github.difflib.patch.Patch;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Service
@RequiredArgsConstructor
public class S3Service {

  private static final String userName = "admin";
  private final S3Client s3Client;
  private final AuditService auditService;
  private final S3Properties s3Properties;

  public ListBucketsResponse listBuckets() {
    return ListBucketsResponse.builder()
        .buckets(s3Properties.getBuckets()
            .stream()
            .map(bucket -> Bucket.builder()
                .name(bucket)
                .build())
            .sorted(Comparator.comparing(Bucket::getName))
            .toList())
        .build();
  }

  public ListContentsResponse listContents(String bucketName) {
    if (!s3Properties.getBuckets().contains(bucketName)) {
      throw new ForbiddenException(String.format("Forbidden to access bucket %s", bucketName));
    }
    HeadBucketRequest headBucketRequest = HeadBucketRequest.builder()
        .bucket(bucketName)
        .build();
    try {
      s3Client.headBucket(headBucketRequest);
    } catch (NoSuchBucketException e) {
      throw new NotFoundException(String.format("Bucket %s not found", bucketName));
    }

    ListObjectsV2Request listObjectsV2Request = ListObjectsV2Request.builder()
        .bucket(bucketName)
        .build();
    return ListContentsResponse.builder()
        .objects(s3Client.listObjectsV2(listObjectsV2Request)
            .contents()
            .stream()
            .map(object -> BucketObject.builder()
                .objectKey(object.key())
                .objectSize(object.size())
                .lastModifiedTimestamp(
                    LocalDateTime.ofInstant(object.lastModified(), ZoneId.of("Europe/London")))
                .build())
            .sorted((a, b) -> b.getLastModifiedTimestamp().compareTo(a.getLastModifiedTimestamp()))
            .toList())
        .build();
  }

  public ResponseEntity<InputStreamResource> getFile(String bucketName, String key) {
    if (!s3Properties.getBuckets().contains(bucketName)) {
      throw new ForbiddenException(String.format("Forbidden to access bucket %s", bucketName));
    }
    InputStreamResource resource = new InputStreamResource(getObject(bucketName, key));

    HttpHeaders headers = new HttpHeaders();
    headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + key);
    headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);

    auditService.writeAuditDiff(AuditEventType.VIEW, bucketName, key, userName,
        Collections.emptyList());

    return new ResponseEntity<>(resource, headers, HttpStatus.OK);
  }

  public void uploadFile(String bucketName, MultipartFile file, String objectKey) {
    if (!s3Properties.getBuckets().contains(bucketName)) {
      throw new ForbiddenException(String.format("Forbidden to access bucket %s", bucketName));
    }
    PreUploadObjectResponse preUploadObjectResponse = preUploadFile(bucketName, file, objectKey);

    PutObjectRequest putObjectRequest = PutObjectRequest.builder()
        .bucket(bucketName)
        .key(objectKey)
        .build();
    try {
      s3Client.putObject(putObjectRequest,
          RequestBody.fromInputStream(file.getInputStream(), file.getInputStream().available()));
    } catch (Exception e) {
      throw new RuntimeException("Failed to upload file", e);
    }

    if (preUploadObjectResponse.isNewUpload()) {
      auditService.writeAuditDiff(AuditEventType.UPLOAD, bucketName, objectKey, userName,
          Collections.emptyList());
    } else {
      auditService.writeAuditDiff(AuditEventType.MODIFY, bucketName, objectKey, userName,
          preUploadObjectResponse.getUnifiedDiff());
    }
  }

  public PreUploadObjectResponse preUploadFile(String bucketName, MultipartFile file,
      String objectKey) {
    if (!s3Properties.getBuckets().contains(bucketName)) {
      throw new ForbiddenException(String.format("Forbidden to access bucket %s", bucketName));
    }
    ResponseInputStream<GetObjectResponse> object;
    try {
      object = getObject(bucketName, objectKey);
    } catch (NotFoundException e) {
      return PreUploadObjectResponse.builder()
          .newUpload(true)
          .build();
    }

    List<String> originalLines;
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(object))) {
      originalLines = reader.lines().toList();
    } catch (IOException e) {
      throw new RuntimeException("Failed to read object content", e);
    }

    List<String> newLines;
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream()))) {
      newLines = reader.lines().toList();
    } catch (IOException e) {
      throw new RuntimeException("Failed to read file content", e);
    }

    Patch<String> patch = DiffUtils.diff(originalLines, newLines);
    List<String> unifiedDiff = UnifiedDiffUtils.generateUnifiedDiff(objectKey, objectKey,
        originalLines, patch, Integer.MAX_VALUE);

    return PreUploadObjectResponse.builder()
        .newUpload(false)
        .unifiedDiff(unifiedDiff)
        .build();
  }

  private ResponseInputStream<GetObjectResponse> getObject(String bucketName, String key) {
    GetObjectRequest getObjectRequest = GetObjectRequest.builder()
        .bucket(bucketName)
        .key(key)
        .build();
    try {
      return s3Client.getObject(getObjectRequest);
    } catch (NoSuchKeyException e) {
      throw new NotFoundException(String.format("Object %s not found", key));
    }
  }
}
