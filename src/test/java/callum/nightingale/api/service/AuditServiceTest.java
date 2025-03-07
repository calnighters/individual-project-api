package callum.nightingale.api.service;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.refEq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import callum.nightingale.api.dto.audit.model.AuditDiff;
import callum.nightingale.api.dto.audit.model.AuditEventType;
import callum.nightingale.api.dto.audit.request.AuditSearchRequest;
import callum.nightingale.api.dto.audit.response.AuditSearchResponse;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.GetObjectTaggingRequest;
import software.amazon.awssdk.services.s3.model.GetObjectTaggingResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.model.Tag;

@ExtendWith(MockitoExtension.class)
@DisplayName("Testing AuditService")
public class AuditServiceTest {

  @Nested
  @DisplayName("writeAuditDiff")
  class WriteAuditDiff {

    @Test
    @DisplayName("When audit is not enabled, then the method is returned from")
    void whenAuditIsNotEnabledThenMethodIsReturnedFrom() {
      S3Client s3Client = mock(S3Client.class);
      AuditService auditService = new AuditService(s3Client);
      ReflectionTestUtils.setField(auditService, "auditEnabled", false);

      AuditEventType eventType = AuditEventType.UPLOAD;
      String bucketName = "bucketName";
      String objectKey = "objectKey";
      String userName = "userName";
      List<String> unifiedDiff = new ArrayList<>();

      auditService.writeAuditDiff(eventType, bucketName, objectKey, userName, unifiedDiff);

      verifyNoInteractions(s3Client);
    }

    @Test
    @DisplayName("When audit is enabled, then the method is executed")
    void whenAuditIsEnabledThenMethodIsExecuted() {
      S3Client s3Client = mock(S3Client.class);
      AuditService auditService = new AuditService(s3Client);
      ReflectionTestUtils.setField(auditService, "auditEnabled", true);
      ReflectionTestUtils.setField(auditService, "auditBucketName", "auditBucketName");

      AuditEventType eventType = AuditEventType.UPLOAD;
      String bucketName = "bucketName";
      String objectKey = "objectKey";
      String userName = "userName";
      List<String> unifiedDiff = new ArrayList<>();

      auditService.writeAuditDiff(eventType, bucketName, objectKey, userName, unifiedDiff);

      ArgumentCaptor<PutObjectRequest> putObjectRequestArgumentCaptor = ArgumentCaptor.forClass(
          PutObjectRequest.class);
      verify(s3Client).putObject(putObjectRequestArgumentCaptor.capture(), any(RequestBody.class));

      PutObjectRequest putObjectRequest = putObjectRequestArgumentCaptor.getValue();
      Map<String, String> tags = Arrays.stream(putObjectRequest.tagging().split("&"))
          .map(tag -> tag.split("="))
          .collect(Collectors.toMap(tag -> tag[0], tag -> tag[1]));

      assertAll(
          () -> assertEquals("auditBucketName", putObjectRequest.bucket()),
          () -> assertTrue(putObjectRequest.key().startsWith(eventType.name() + "/")),
          () -> assertEquals(objectKey, tags.get("objectKey")),
          () -> assertEquals(bucketName, tags.get("bucketName")),
          () -> assertEquals(userName, tags.get("userName"))
      );
    }
  }

  @Nested
  @DisplayName("searchObjectsByMetadata")
  class SearchObjectsByMetadata {

    @Test
    @DisplayName("When there are objects in the bucket, then a list is returned")
    void whenThereAreObjectsInBucketThenListIsReturned() {
      S3Client s3Client = mock(S3Client.class);
      AuditService auditService = new AuditService(s3Client);
      ReflectionTestUtils.setField(auditService, "auditBucketName", "auditBucketName");
      ReflectionTestUtils.setField(auditService, "maxAuditRecords", 100);

      AuditSearchRequest searchRequest = AuditSearchRequest.builder()
          .eventType(AuditEventType.MODIFY)
          .objectKey("objectKey")
          .bucketName("bucketName")
          .userName("userName")
          .fromDate(LocalDateTime.MIN)
          .toDate(LocalDateTime.MAX)
          .build();

      LocalDateTime timeNow = LocalDateTime.now();
      ListObjectsV2Request listObjectsV2Request = ListObjectsV2Request.builder()
          .bucket("auditBucketName")
          .prefix("MODIFY")
          .build();
      when(s3Client.listObjectsV2(refEq(listObjectsV2Request))).thenReturn(
          ListObjectsV2Response.builder()
              .contents(List.of(
                  S3Object.builder()
                      .key("MODIFY/1")
                      .lastModified(timeNow.toInstant(ZoneOffset.UTC))
                      .build()
              ))
              .build());

      GetObjectTaggingRequest getObjectTaggingRequest = GetObjectTaggingRequest.builder()
          .bucket("auditBucketName")
          .key("MODIFY/1")
          .build();
      when(s3Client.getObjectTagging(refEq(getObjectTaggingRequest))).thenReturn(
          GetObjectTaggingResponse.builder()
              .tagSet(Tag.builder()
                      .key("objectKey")
                      .value("objectKey")
                      .build(),
                  Tag.builder()
                      .key("bucketName")
                      .value("bucketName")
                      .build(),
                  Tag.builder()
                      .key("userName")
                      .value("userName")
                      .build())
              .build()
      );

      AuditSearchResponse searchResponse = auditService.searchObjectsByMetadata(searchRequest);

      assertAll(
          () -> assertEquals(1, searchResponse.getAuditRecords().size()),
          () -> assertEquals(AuditEventType.MODIFY, searchResponse.getAuditRecords().get(0).getEventType()),
          () -> assertEquals("objectKey", searchResponse.getAuditRecords().get(0).getObjectKey()),
          () -> assertEquals("bucketName", searchResponse.getAuditRecords().get(0).getBucketName()),
          () -> assertEquals("userName", searchResponse.getAuditRecords().get(0).getUserName()),
          () -> assertEquals(timeNow, searchResponse.getAuditRecords().get(0).getAuditDate()),
          () -> assertEquals("MODIFY/1", searchResponse.getAuditRecords().get(0).getAuditObjectKey())
      );
    }
  }

  @Nested
  @DisplayName("getAuditDiff")
  class GetAuditDiff {

    @Test
    @DisplayName("When the file is found, then the diff is returned")
    void fileFoundDiffReturned() {
      S3Client s3Client = mock(S3Client.class);
      AuditService auditService = new AuditService(s3Client);
      ReflectionTestUtils.setField(auditService, "auditBucketName", "auditBucketName");

      String diffContent = "diff content line 1\ndiff content line 2";

      // Create a mock ResponseInputStream
      GetObjectResponse getObjectResponse = mock(GetObjectResponse.class);
      ResponseInputStream<GetObjectResponse> responseInputStream = new ResponseInputStream<>(
          getObjectResponse, new ByteArrayInputStream(diffContent.getBytes(StandardCharsets.UTF_8))
      );

      String auditObjectKey = "auditObjectKey";
      when(s3Client.getObject(any(GetObjectRequest.class)))
          .thenReturn(responseInputStream);

      AuditDiff auditDiff = auditService.getAuditDiff(auditObjectKey);

      assertAll(
          () -> assertEquals(2, auditDiff.getUnifiedDiff().size()),
          () -> assertEquals("diff content line 1", auditDiff.getUnifiedDiff().get(0)),
          () -> assertEquals("diff content line 2", auditDiff.getUnifiedDiff().get(1))
      );

    }
  }
}
