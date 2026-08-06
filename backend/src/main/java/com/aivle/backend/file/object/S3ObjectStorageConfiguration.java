package com.aivle.backend.file.object;

import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
    name = "app.object-storage.provider",
    havingValue = "s3"
)
public class S3ObjectStorageConfiguration {

    @Bean
    S3Client objectStorageS3Client(
        ObjectStorageProperties properties
    ) {
        var credentials = credentials(properties);
        return S3Client.builder()
            .endpointOverride(properties.endpoint())
            .region(Region.of(properties.region()))
            .credentialsProvider(credentials)
            .serviceConfiguration(serviceConfiguration(properties))
            .httpClientBuilder(
                UrlConnectionHttpClient.builder()
                    .connectionTimeout(properties.connectTimeout())
                    .socketTimeout(properties.readTimeout())
            )
            .build();
    }

    @Bean
    S3Presigner objectStorageS3Presigner(
        ObjectStorageProperties properties
    ) {
        return S3Presigner.builder()
            .endpointOverride(properties.publicEndpoint())
            .region(Region.of(properties.region()))
            .credentialsProvider(credentials(properties))
            .serviceConfiguration(serviceConfiguration(properties))
            .build();
    }

    private StaticCredentialsProvider credentials(
        ObjectStorageProperties properties
    ) {
        String accessKey = requireCredential(
            properties.accessKey(),
            "access-key"
        );
        String secretKey = requireCredential(
            properties.secretKey(),
            "secret-key"
        );
        return StaticCredentialsProvider.create(
            AwsBasicCredentials.create(accessKey, secretKey)
        );
    }

    private S3Configuration serviceConfiguration(
        ObjectStorageProperties properties
    ) {
        return S3Configuration.builder()
            .pathStyleAccessEnabled(properties.pathStyleAccess())
            .checksumValidationEnabled(false)
            .build();
    }

    private String requireCredential(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                "app.object-storage." + name
                    + " is required for the s3 provider"
            );
        }
        return Objects.requireNonNull(value).trim();
    }
}
