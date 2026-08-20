package com.aivle.backend.file.object;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
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
        return S3Client.builder()
            .endpointOverride(properties.endpoint())
            .region(Region.of(properties.region()))
            .credentialsProvider(credentials(properties))
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

    /**
     * 로컬 MinIO처럼 명시적 access/secret key가 둘 다 제공되면
     * 기존 static credentials 방식을 그대로 사용한다.
     *
     * Production AWS에서는 두 값을 모두 비워 두고
     * AWS SDK Default Credentials Provider Chain을 사용한다.
     * EC2에서는 Instance Profile(IAM Role)의 임시 자격증명을 사용하게 된다.
     */
    private AwsCredentialsProvider credentials(
        ObjectStorageProperties properties
    ) {
        String accessKey = normalize(properties.accessKey());
        String secretKey = normalize(properties.secretKey());

        boolean hasAccessKey = accessKey != null;
        boolean hasSecretKey = secretKey != null;

        if (hasAccessKey != hasSecretKey) {
            throw new IllegalStateException(
                "app.object-storage.access-key and secret-key "
                    + "must either both be configured or both be omitted"
            );
        }

        if (hasAccessKey) {
            return StaticCredentialsProvider.create(
                AwsBasicCredentials.create(accessKey, secretKey)
            );
        }

        return DefaultCredentialsProvider.create();
    }

    private S3Configuration serviceConfiguration(
        ObjectStorageProperties properties
    ) {
        return S3Configuration.builder()
            .pathStyleAccessEnabled(properties.pathStyleAccess())
            .checksumValidationEnabled(false)
            .build();
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}