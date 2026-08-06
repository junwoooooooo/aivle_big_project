package com.aivle.backend.file.repository;
import com.aivle.backend.file.entity.StoredFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface StoredFileRepository extends JpaRepository<StoredFile, Long> {
    Optional<StoredFile> findByStorageKey(String storageKey);

    @Query("""
        select file.storageKey
        from StoredFile file
        where file.deletedAt is null
        """)
    List<String> findAllReferencedStorageKeys();
}
