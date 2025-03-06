package callum.nightingale.api.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
@RequiredArgsConstructor
public class S3Config {

  @Bean
  public S3Client s3Client() {
    return S3Client.builder()
        .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(
            "accesskey",
            "secretkey"
        )))
        .build();
  }
}
