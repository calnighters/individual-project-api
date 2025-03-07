package callum.nightingale.api.service;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import callum.nightingale.api.dto.s3.response.ListBucketsResponse;
import callum.nightingale.api.dto.s3.response.ListContentsResponse;
import callum.nightingale.api.dto.s3.response.PreUploadObjectResponse;
import callum.nightingale.api.exception.ForbiddenException;
import callum.nightingale.api.properties.S3Properties;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;

@ExtendWith(MockitoExtension.class)
@DisplayName("Testing S3Service")
class S3ServiceTest {

  private S3Client s3Client;
  private AuditService auditService;
  private S3Properties s3Properties;
  private S3Service s3Service;

  @BeforeEach
  void setUp() {
    s3Client = mock(S3Client.class);
    auditService = mock(AuditService.class);
    s3Properties = mock(S3Properties.class);
    s3Service = new S3Service(s3Client, auditService, s3Properties);
  }

  @Nested
  @DisplayName("listBuckets")
  class ListBuckets {

    @Test
    @DisplayName("When buckets are configured, then return list of buckets")
    void shouldReturnListOfBuckets() {
      when(s3Properties.getBuckets()).thenReturn(List.of("bucket1", "bucket2"));

      ListBucketsResponse response = s3Service.listBuckets();

      assertAll(
          () -> assertEquals(2, response.getBuckets().size()),
          () -> assertEquals("bucket1", response.getBuckets().get(0).getName()),
          () -> assertEquals("bucket2", response.getBuckets().get(1).getName())
      );
    }
  }

  @Nested
  @DisplayName("listContents")
  class ListContents {

    @Test
    @DisplayName("When bucket is not in list, then throw ForbiddenException")
    void shouldThrowForbiddenExceptionWhenBucketNotInList() {
      when(s3Properties.getBuckets()).thenReturn(List.of("bucket1"));

      assertThrows(ForbiddenException.class, () -> s3Service.listContents("bucket2"));
    }

    @Test
    @DisplayName("When bucket is in list, then return list of objects")
    void shouldReturnListOfObjects() {
      when(s3Properties.getBuckets()).thenReturn(List.of("bucket1"));
      Instant now = Instant.now();
      when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
          .thenReturn(ListObjectsV2Response.builder()
              .contents(S3Object.builder().key("object1").size(100L).lastModified(now).build())
              .build());

      ListContentsResponse response = s3Service.listContents("bucket1");

      assertAll(
          () -> assertEquals(1, response.getObjects().size()),
          () -> assertEquals("object1", response.getObjects().get(0).getObjectKey())
      );
    }
  }

  @Nested
  @DisplayName("getFile")
  class GetFile {

    @Test
    @DisplayName("When bucket is not in list, then throw ForbiddenException")
    void shouldThrowForbiddenExceptionWhenBucketNotInList() {
      when(s3Properties.getBuckets()).thenReturn(List.of("bucket1"));

      assertThrows(ForbiddenException.class, () -> s3Service.getFile("bucket2", "objectKey"));
    }

    @Test
    @DisplayName("When bucket is in list, then return file")
    void shouldReturnFile() {
      when(s3Properties.getBuckets()).thenReturn(List.of("bucket1"));
      String file = """
          hello
          world
          """;
      ResponseInputStream<GetObjectResponse> responseInputStream = new ResponseInputStream<>(
          mock(GetObjectResponse.class), new ByteArrayInputStream(file.getBytes()));
      when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(responseInputStream);

      ResponseEntity<InputStreamResource> response = s3Service.getFile("bucket1", "objectKey");

      assertAll(
          () -> assertEquals("attachment; filename=objectKey", response.getHeaders().get(
              HttpHeaders.CONTENT_DISPOSITION).get(0)),
          () -> assertEquals("application/octet-stream",
              response.getHeaders().get(HttpHeaders.CONTENT_TYPE).get(0)),
          () -> assertEquals(file, new String(response.getBody().getInputStream().readAllBytes()))

      );
    }
  }

  @Nested
  @DisplayName("uploadFile")
  class UploadFile {

    @Test
    @DisplayName("When bucket is not in list, then throw ForbiddenException")
    void shouldThrowForbiddenExceptionWhenBucketNotInList() {
      when(s3Properties.getBuckets()).thenReturn(List.of("bucket1"));
      MultipartFile file = mock(MultipartFile.class);

      assertThrows(ForbiddenException.class,
          () -> s3Service.uploadFile("bucket2", file, "objectKey"));
    }

    @Test
    @DisplayName("When bucket is in list, then upload file")
    void shouldUploadFile() throws IOException {
      when(s3Properties.getBuckets()).thenReturn(List.of("bucket1"));
      MultipartFile file = mock(MultipartFile.class);
      InputStream inputStream = mock(InputStream.class);
      when(file.getInputStream()).thenReturn(inputStream);
      when(inputStream.available()).thenReturn(0);
      when(s3Client.getObject(any(GetObjectRequest.class))).thenThrow(NoSuchKeyException.class);

      s3Service.uploadFile("bucket1", file, "objectKey");

      verify(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }
  }

  @Nested
  @DisplayName("preUploadFile")
  class PreUploadFile {

    @Test
    @DisplayName("When bucket is not in list, then throw ForbiddenException")
    void shouldThrowForbiddenExceptionWhenBucketNotInList() {
      when(s3Properties.getBuckets()).thenReturn(List.of("bucket1"));
      MultipartFile file = mock(MultipartFile.class);

      assertThrows(ForbiddenException.class,
          () -> s3Service.preUploadFile("bucket2", file, "objectKey"));
    }

    @Test
    @DisplayName("When object does not exist, then return new upload")
    void shouldReturnNewUpload() {
      when(s3Properties.getBuckets()).thenReturn(List.of("bucket1"));
      when(s3Client.getObject(any(GetObjectRequest.class))).thenThrow(NoSuchKeyException.class);

      PreUploadObjectResponse response = s3Service.preUploadFile("bucket1",
          mock(MultipartFile.class), "objectKey");

      assertEquals(true, response.isNewUpload());
    }

    @Test
    @DisplayName("When object exists, then return existing upload")
    void shouldReturnExistingUpload() throws IOException {
      when(s3Properties.getBuckets()).thenReturn(List.of("bucket1"));

      MultipartFile file = mock(MultipartFile.class);
      InputStream inputStream = new ByteArrayInputStream("file content".getBytes());
      when(file.getInputStream()).thenReturn(inputStream);

      ResponseInputStream<GetObjectResponse> responseInputStream = new ResponseInputStream<>(
          mock(GetObjectResponse.class), new ByteArrayInputStream("file content".getBytes()));
      when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(responseInputStream);

      PreUploadObjectResponse response = s3Service.preUploadFile("bucket1", file, "objectKey");

      assertEquals(false, response.isNewUpload());
    }
  }
}
