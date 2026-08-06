package com.aivle.backend.file.object;

import com.aivle.backend.common.entity.StorageType;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;

public interface ObjectStoragePort {
    StoredObject store(
        InputStream input,
        long expectedSize,
        String contentType,
        String objectKey
    ) throws IOException;

    InputStream open(String objectKey) throws IOException;

    void delete(String objectKey) throws IOException;

    boolean exists(String objectKey);

    ObjectMetadata metadata(String objectKey) throws IOException;

    URI createPresignedGet(String objectKey);

    URI createPresignedPut(String objectKey, String contentType);

    StorageType storageType();

    record StoredObject(
        String objectKey,
        long sizeBytes,
        String contentType,
        String checksumSha256
    ) {
    }

    record ObjectMetadata(
        String objectKey,
        long sizeBytes,
        String contentType
    ) {
    }
}
