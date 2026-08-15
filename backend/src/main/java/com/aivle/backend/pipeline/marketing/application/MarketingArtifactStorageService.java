package com.aivle.backend.pipeline.marketing.application;

import com.aivle.backend.file.object.ObjectKeyGenerator;
import com.aivle.backend.file.object.ObjectStoragePort;
import com.aivle.backend.file.validation.ValidatedUpload;
import com.aivle.backend.pipeline.artifact.application.EvidenceArtifactUploadPolicy;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MarketingArtifactStorageService {
    private final EvidenceArtifactUploadPolicy uploadPolicy;
    private final ObjectStoragePort storage;
    private final ObjectKeyGenerator keys;

    public String storeGeneratedJpeg(byte[] content) {
        String objectKey = null;
        try {
            ValidatedUpload validated = uploadPolicy.validate("generated-marketing-image.jpg", "image/jpeg",
                new ByteArrayInputStream(content == null ? new byte[0] : content));
            objectKey = keys.aiArtifactImage("jpg");
            ObjectStoragePort.StoredObject stored = storage.store(validated.openStream(), validated.sizeBytes(),
                validated.contentType(), objectKey);
            if (stored.sizeBytes() != validated.sizeBytes()
                    || !stored.checksumSha256().equals(validated.checksumSha256())) {
                throw new IOException("stored marketing image integrity mismatch");
            }
            return objectKey;
        } catch (IOException | RuntimeException failure) {
            if (objectKey != null) try { storage.delete(objectKey); }
            catch (IOException | RuntimeException ignored) { /* reconciliation fallback */ }
            throw new IllegalStateException("marketing image storage failed", failure);
        }
    }
}
