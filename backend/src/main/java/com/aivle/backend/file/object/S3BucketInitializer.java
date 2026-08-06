package com.aivle.backend.file.object;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    name = "app.object-storage.provider",
    havingValue = "s3"
)
public class S3BucketInitializer implements ApplicationRunner {
    private final S3Client client;
    private final ObjectStorageProperties properties;

    @Override
    public void run(ApplicationArguments arguments) {
        try {
            client.headBucket(builder ->
                builder.bucket(properties.bucket())
            );
        } catch (NoSuchBucketException exception) {
            createBucket();
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) {
                createBucket();
                return;
            }
            throw exception;
        }
    }

    private void createBucket() {
        client.createBucket(builder ->
            builder.bucket(properties.bucket())
        );
    }
}
