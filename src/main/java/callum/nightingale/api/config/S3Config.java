package callum.nightingale.api.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
@RequiredArgsConstructor
public class S3Config {

  @Bean
  public S3Client s3Client() {
    return S3Client.builder()
        .region(Region.EU_WEST_2)
        .credentialsProvider(DefaultCredentialsProvider.create())
        .build();
  }
}
