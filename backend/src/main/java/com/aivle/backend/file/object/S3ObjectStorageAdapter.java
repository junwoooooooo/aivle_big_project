package com.aivle.backend.file.object;

import com.aivle.backend.common.entity.StorageType;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    name = "app.object-storage.provider",
    havingValue = "s3"
)
public class S3ObjectStorageAdapter implements ObjectStoragePort {
    private final ObjectStorageProperties properties;
    private final S3Client client;
    private final S3Presigner presigner;

    @Override
    public StoredObject store(
        InputStream input,
        long expectedSize,
        String contentType,
        String objectKey
    ) throws IOException {
        validateObjectKey(objectKey);
        MessageDigest digest = sha256();
        try {
            client.putObject(
                PutObjectRequest.builder()
                    .bucket(properties.bucket())
                    .key(objectKey)
                    .contentType(contentType)
                    .contentLength(expectedSize)
                    .ifNoneMatch("*")
                    .build(),
                RequestBody.fromInputStream(
                    new DigestInputStream(input, digest),
                    expectedSize
                )
            );
            return new StoredObject(
                objectKey,
                expectedSize,
                contentType,
                HexFormat.of().formatHex(digest.digest())
            );
        } catch (S3Exception exception) {
            throw new IOException("S3 object store failed", exception);
        }
    }

    @Override
    public InputStream open(String objectKey) throws IOException {
        validateObjectKey(objectKey);
        try {
            return client.getObject(
                GetObjectRequest.builder()
                    .bucket(properties.bucket())
                    .key(objectKey)
                    .build()
            );
        } catch (S3Exception exception) {
            throw new IOException("S3 object open failed", exception);
        }
    }

    @Override
    public void delete(String objectKey) throws IOException {
        validateObjectKey(objectKey);
        try {
            client.deleteObject(builder -> builder
                .bucket(properties.bucket())
                .key(objectKey)
            );
        } catch (S3Exception exception) {
            throw new IOException("S3 object delete failed", exception);
        }
    }

    @Override
    public boolean exists(String objectKey) {
        validateObjectKey(objectKey);
        try {
            client.headObject(builder -> builder
                .bucket(properties.bucket())
                .key(objectKey)
            );
            return true;
        } catch (NoSuchKeyException exception) {
            return false;
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) {
                return false;
            }
            throw exception;
        }
    }

    @Override
    public ObjectMetadata metadata(String objectKey)
        throws IOException {
        validateObjectKey(objectKey);
        try {
            var response = client.headObject(
                HeadObjectRequest.builder()
                    .bucket(properties.bucket())
                    .key(objectKey)
                    .build()
            );
            return new ObjectMetadata(
                objectKey,
                response.contentLength(),
                response.contentType()
            );
        } catch (S3Exception exception) {
            throw new IOException(
                "S3 object metadata lookup failed",
                exception
            );
        }
    }

    @Override
    public URI createPresignedGet(String objectKey) {
        validateObjectKey(objectKey);
        var objectRequest = GetObjectRequest.builder()
            .bucket(properties.bucket())
            .key(objectKey)
            .build();
        return URI.create(presigner.presignGetObject(
            GetObjectPresignRequest.builder()
                .signatureDuration(properties.presignedGetExpiry())
                .getObjectRequest(objectRequest)
                .build()
        ).url().toString());
    }

    @Override
    public URI createPresignedPut(
        String objectKey,
        String contentType
    ) {
        validateObjectKey(objectKey);
        var objectRequest = PutObjectRequest.builder()
            .bucket(properties.bucket())
            .key(objectKey)
            .contentType(contentType)
            .build();
        return URI.create(presigner.presignPutObject(
            PutObjectPresignRequest.builder()
                .signatureDuration(properties.presignedPutExpiry())
                .putObjectRequest(objectRequest)
                .build()
        ).url().toString());
    }

    @Override
    public StorageType storageType() {
        return StorageType.S3_COMPATIBLE;
    }

    private void validateObjectKey(String objectKey) {
        if (
            objectKey == null
            || objectKey.isBlank()
            || objectKey.startsWith("/")
            || objectKey.contains("\\")
            || objectKey.contains("..")
        ) {
            throw new IllegalArgumentException("invalid object key");
        }
    }

    private MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                "SHA-256 is unavailable",
                exception
            );
        }
    }
}
