package com.aivle.backend.file.entity;

import com.aivle.backend.common.entity.*;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity @Table(name = "stored_files")
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StoredFile extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private StorageType storageType;
    @Column(nullable = false, unique = true, length = 500) private String storageKey;
    @Column(nullable = false, length = 255) private String originalFilename;
    @Column(nullable = false, length = 255) private String storedFilename;
    @Column(nullable = false, length = 20) private String extension;
    @Column(nullable = false, length = 150) private String mimeType;
    @Column(nullable = false) private Long sizeBytes;
    @Column(nullable = false, length = 64) private String checksumSha256;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private FileStatus status;
    @Column(nullable = false) private Boolean encrypted;
    private LocalDateTime retentionUntil;

    private StoredFile(
        StorageType storageType,
        String storageKey,
        String originalFilename,
        String storedFilename,
        String extension,
        String mimeType,
        long sizeBytes,
        String checksumSha256
    ) {
        this.storageType = storageType;
        this.storageKey = storageKey;
        this.originalFilename = originalFilename;
        this.storedFilename = storedFilename;
        this.extension = extension;
        this.mimeType = mimeType;
        this.sizeBytes = sizeBytes;
        this.checksumSha256 = checksumSha256;
        this.status = FileStatus.AVAILABLE;
        this.encrypted = false;
    }

    public static StoredFile available(
        String storageKey,
        String originalFilename,
        String storedFilename,
        String extension,
        String mimeType,
        long sizeBytes,
        String checksumSha256
    ) {
        return new StoredFile(
            StorageType.LOCAL,
            storageKey,
            originalFilename,
            storedFilename,
            extension,
            mimeType,
            sizeBytes,
            checksumSha256
        );
    }

    public static StoredFile available(
        StorageType storageType,
        String storageKey,
        String originalFilename,
        String storedFilename,
        String extension,
        String mimeType,
        long sizeBytes,
        String checksumSha256
    ) {
        return new StoredFile(
            storageType,
            storageKey,
            originalFilename,
            storedFilename,
            extension,
            mimeType,
            sizeBytes,
            checksumSha256
        );
    }

    public void assignStorageKey(String storageKey, String storedFilename) {
        if (storageKey == null || storageKey.isBlank()
            || storedFilename == null || storedFilename.isBlank()) {
            throw new IllegalArgumentException(
                "storage key and stored filename are required"
            );
        }
        this.storageKey = storageKey;
        this.storedFilename = storedFilename;
    }
}
