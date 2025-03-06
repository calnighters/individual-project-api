package callum.nightingale.api.service;

import callum.nightingale.api.dto.audit.model.AuditDiff;
import callum.nightingale.api.dto.audit.model.AuditEventType;
import callum.nightingale.api.dto.audit.model.AuditInfo;
import callum.nightingale.api.dto.audit.request.AuditSearchRequest;
import callum.nightingale.api.dto.audit.response.AuditSearchResponse;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.GetObjectTaggingRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.model.Tag;
import software.amazon.awssdk.services.s3.model.Tagging;

@Service
@RequiredArgsConstructor
public class AuditService {

  private final S3Client s3Client;

  @Value("${s3.audit.bucketName}")
  private String auditBucketName;
  @Value("${s3.audit.enabled:true}")
  private boolean auditEnabled;
  @Value("${s3.audit.maxRecords:20}")
  private int maxAuditRecords;

  public void auditDiff(AuditEventType eventType, String bucketName, String objectKey,
      String userName, List<String> unifiedDiff) {
    if (!auditEnabled) {
      return;
    }

    String diffContent = String.join("\n", unifiedDiff);
    String auditKey = String.format("%s/%s", eventType.name(), UUID.randomUUID());

    PutObjectRequest putObjectRequest = PutObjectRequest.builder()
        .bucket(auditBucketName)
        .key(auditKey)
        .tagging(Tagging.builder()
            .tagSet(List.of(
                Tag.builder()
                    .key("objectKey")
                    .value(objectKey)
                    .build(),
                Tag.builder()
                    .key("bucketName")
                    .value(bucketName)
                    .build(),
                Tag.builder()
                    .key("userName")
                    .value(userName)
                    .build()
            ))
            .build())
        .build();

    s3Client.putObject(putObjectRequest,
        RequestBody.fromInputStream(
            new ByteArrayInputStream(diffContent.getBytes(StandardCharsets.UTF_8)),
            diffContent.length()
        ));
  }

  public AuditSearchResponse searchObjectsByMetadata(AuditSearchRequest searchRequest) {

    List<S3Object> matchingObjects = new ArrayList<>();
    String continuationToken = null;

    do {
      ListObjectsV2Request.Builder listObjectsV2RequestBuilder = ListObjectsV2Request.builder()
          .bucket(auditBucketName);

      if (continuationToken != null) {
        listObjectsV2RequestBuilder.continuationToken(continuationToken);
      }

      if (searchRequest.getEventType() != null) {
        listObjectsV2RequestBuilder.prefix(searchRequest.getEventType().name());
      }

      ListObjectsV2Response listObjectsV2Response = s3Client.listObjectsV2(
          listObjectsV2RequestBuilder.build());

      List<S3Object> filteredObjects = listObjectsV2Response.contents().stream()
          .filter(s3Object -> {
            boolean matches = true;

            LocalDateTime lastModified = LocalDateTime.ofInstant(s3Object.lastModified(),
                ZoneId.of("Europe/London"));
            if (searchRequest.getFromDate() != null && lastModified.isBefore(
                searchRequest.getFromDate())) {
              matches = false;
            }
            if (searchRequest.getToDate() != null && lastModified.isAfter(
                searchRequest.getToDate())) {
              matches = false;
            }

            if (searchRequest.getObjectKey() != null || searchRequest.getBucketName() != null
                || searchRequest.getUserName() != null) {
              GetObjectTaggingRequest getObjectTaggingRequest = GetObjectTaggingRequest.builder()
                  .bucket(auditBucketName)
                  .key(s3Object.key())
                  .build();
              Map<String, String> tags = s3Client.getObjectTagging(getObjectTaggingRequest)
                  .tagSet()
                  .stream()
                  .collect(Collectors.toMap(Tag::key, Tag::value));

              if (searchRequest.getBucketName() != null && !tags.get("bucketName").toLowerCase()
                  .contains(searchRequest.getBucketName().toLowerCase())) {
                matches = false;
              }

              if (searchRequest.getObjectKey() != null && !tags.get("objectKey")
                  .contains(searchRequest.getObjectKey())) {
                matches = false;
              }

              if (searchRequest.getUserName() != null && !searchRequest.getUserName().toLowerCase()
                  .contains(tags.get("userName").toLowerCase())) {
                matches = false;
              }
            }

            return matches;
          })
          .toList();

      matchingObjects.addAll(filteredObjects);
      continuationToken = listObjectsV2Response.nextContinuationToken();
    } while (continuationToken != null && matchingObjects.size() <= maxAuditRecords);

    return AuditSearchResponse.builder()
        .auditRecords(matchingObjects.subList(0, maxAuditRecords > matchingObjects.size() ? matchingObjects.size() : maxAuditRecords)
            .stream().map(s3Object -> {
              GetObjectTaggingRequest getObjectTaggingRequest = GetObjectTaggingRequest.builder()
                  .bucket(auditBucketName)
                  .key(s3Object.key())
                  .build();
              Map<String, String> tags = s3Client.getObjectTagging(getObjectTaggingRequest)
                  .tagSet()
                  .stream()
                  .collect(Collectors.toMap(Tag::key, Tag::value));
              return AuditInfo.builder()
                  .auditDate(
                      LocalDateTime.ofInstant(s3Object.lastModified(), ZoneId.of("Europe/London")))
                  .bucketName(tags.get("bucketName"))
                  .objectKey(tags.get("objectKey"))
                  .userName(tags.get("userName"))
                  .eventType(AuditEventType.valueOf(s3Object.key().split("/")[0]))
                  .auditObjectKey(s3Object.key())
                  .build();
            }).toList())
        .build();
  }

  public AuditDiff getAuditDiff(String auditObjectKey) {
    ResponseInputStream<GetObjectResponse> objectResponse = s3Client.getObject(
        GetObjectRequest.builder()
            .bucket(auditBucketName)
            .key(auditObjectKey)
            .build());

    try (BufferedReader reader = new BufferedReader(new InputStreamReader(objectResponse))) {
      return AuditDiff.builder()
          .unifiedDiff(reader.lines().collect(Collectors.toList()))
          .build();
    } catch (IOException e) {
      throw new RuntimeException("Failed to read audit diff", e);
    }
  }
}
