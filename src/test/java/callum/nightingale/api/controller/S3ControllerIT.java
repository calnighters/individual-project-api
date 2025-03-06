package callum.nightingale.api.controller;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import callum.nightingale.api.dto.s3.model.Bucket;
import callum.nightingale.api.dto.s3.model.BucketObject;
import callum.nightingale.api.dto.s3.response.ListBucketsResponse;
import callum.nightingale.api.dto.s3.response.ListContentsResponse;
import callum.nightingale.api.dto.s3.response.PreUploadObjectResponse;
import callum.nightingale.api.service.S3Service;
import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

@SpringBootTest
@ExtendWith(MockitoExtension.class)
@AutoConfigureMockMvc
@DisplayName("Integration Testing S3 Controller")
public class S3ControllerIT {

  @Autowired
  private MockMvc mvc;

  @MockitoBean
  private S3Service s3Service;

  @Nested
  @DisplayName("GET /api/v1/s3/buckets")
  class GetBuckets {

    @Test
    @DisplayName("When buckets are found then a response containing the buckets is returned")
    void bucketsFound() throws Exception {

      when(s3Service.listBuckets()).thenReturn(ListBucketsResponse.builder()
          .buckets(List.of(
              Bucket.builder()
                  .name("bucket1")
                  .build(),
              Bucket.builder()
                  .name("bucket2")
                  .build()))
          .build());

      mvc.perform(MockMvcRequestBuilders
              .get("/api/v1/s3/buckets"))
          .andExpect(status().isOk())
          .andExpect(content().json("""
              {
                "buckets": [
                  {
                    "name": "bucket1"
                  },
                  {
                    "name": "bucket2"
                  }
                ]
              }
              """));
    }

    @Test
    @DisplayName("When buckets are not found then a response containing no buckets is returned")
    void bucketsNotFound() throws Exception {

      when(s3Service.listBuckets()).thenReturn(ListBucketsResponse.builder()
          .buckets(Collections.emptyList())
          .build());

      mvc.perform(MockMvcRequestBuilders
              .get("/api/v1/s3/buckets"))
          .andExpect(status().isOk())
          .andExpect(content().json("""
              {
                "buckets": []
              }
              """));
    }
  }

  @Nested
  @DisplayName("GET /api/v1/s3/contents/{bucketName}")
  class GetContents {

    @Test
    @DisplayName("When contents are found then a response containing the contents is returned")
    void contentsFound() throws Exception {

      when(s3Service.listContents("bucket1")).thenReturn(ListContentsResponse.builder()
          .objects(List.of(
              BucketObject.builder()
                  .objectKey("file1.txt")
                  .objectSize(12345L)
                  .lastModifiedTimestamp(LocalDateTime.of(2021, 1, 1, 12, 0, 13))
                  .build(),
              BucketObject.builder()
                  .objectKey("file2.txt")
                  .objectSize(67890L)
                  .lastModifiedTimestamp(LocalDateTime.of(2021, 1, 1, 12, 0, 14))
                  .build()
          ))
          .build());

      mvc.perform(MockMvcRequestBuilders
              .get("/api/v1/s3/buckets/bucket1/contents"))
          .andExpect(status().isOk())
          .andExpect(content().json("""
              {
                "objects": [
                  {
                    "objectKey": "file1.txt",
                    "objectSize": 12345,
                    "lastModifiedTimestamp": "2021-01-01T12:00:13"
                  },
                  {
                    "objectKey": "file2.txt",
                    "objectSize": 67890,
                    "lastModifiedTimestamp": "2021-01-01T12:00:14"
                  }
                ]
              }
              """));
    }

    @Test
    @DisplayName("When contents are not found then a response containing no contents is returned")
    void contentsNotFound() throws Exception {

      when(s3Service.listContents("bucket1")).thenReturn(ListContentsResponse.builder()
          .objects(Collections.emptyList())
          .build());

      mvc.perform(MockMvcRequestBuilders
              .get("/api/v1/s3/buckets/bucket1/contents"))
          .andExpect(status().isOk())
          .andExpect(content().json("""
              {
                "objects": []
              }
              """));
    }
  }

  @Nested
  @DisplayName("POST /api/v1/s3/contents/{bucketName}")
  class GetFile {

    @Test
    @DisplayName("When file is found then a response containing the file is returned")
    void fileFound() throws Exception {
      InputStreamResource resource = new InputStreamResource(
          new ByteArrayInputStream("file content".getBytes()));

      when(s3Service.getFile("bucket1", "file1.txt")).thenReturn(ResponseEntity.ok(resource));

      mvc.perform(MockMvcRequestBuilders
              .post("/api/v1/s3/buckets/bucket1/file")
              .contentType(MediaType.APPLICATION_JSON)
              .content("""
                  {
                    "objectKey": "file1.txt"
                  }
                  """))
          .andExpect(status().isOk())
          .andExpect(content().string("file content"));
    }

    @Test
    @DisplayName("When file is not found then a 404 status is returned")
    void fileNotFound() throws Exception {

      when(s3Service.getFile("bucket1", "file1.txt")).thenReturn(ResponseEntity.notFound().build());

      mvc.perform(MockMvcRequestBuilders
              .post("/api/v1/s3/buckets/bucket1/file")
              .contentType(MediaType.APPLICATION_JSON)
              .content("""
                  {
                    "objectKey": "file1.txt"
                  }
                  """))
          .andExpect(status().isNotFound());
    }
  }

  @Nested
  @DisplayName("POST /api/v1/s3/buckets/{bucketName}/pre-upload")
  class PreUploadObject {

    @Test
    @DisplayName("When pre-upload is successful then a response containing the pre-upload details is returned")
    void preUploadSuccessful() throws Exception {
      MockMultipartFile file = new MockMultipartFile("file", "test.txt", "text/plain",
          "test content".getBytes());
      PreUploadObjectResponse response = PreUploadObjectResponse.builder()
          .newUpload(false)
          .unifiedDiff(List.of(
              "diff1",
              "diff2"
          ))
          .build();

      when(s3Service.preUploadFile("bucket1", file, "test.txt")).thenReturn(response);

      mvc.perform(MockMvcRequestBuilders.multipart("/api/v1/s3/buckets/bucket1/pre-upload")
              .file(file)
              .param("objectKey", "test.txt"))
          .andExpect(status().isOk())
          .andExpect(content().json("""
              {
                "newUpload": false,
                "unifiedDiff": [
                  "diff1",
                  "diff2"
                ]
              }
              """));
    }
  }

  @Nested
  @DisplayName("POST /api/v1/s3/buckets/{bucketName}/upload")
  class UploadObject {

    @Test
    @DisplayName("When upload is successful then a 200 status is returned")
    void uploadSuccessful() throws Exception {
      MockMultipartFile file = new MockMultipartFile("file", "test.txt", "text/plain",
          "test content".getBytes());

      doNothing().when(s3Service).uploadFile("bucket1", file, "test.txt");

      mvc.perform(MockMvcRequestBuilders.multipart("/api/v1/s3/buckets/bucket1/upload")
              .file(file)
              .param("objectKey", "test.txt"))
          .andExpect(status().isOk());

    }
  }
}
