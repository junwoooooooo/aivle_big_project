package com.aivle.backend.pipeline.integration.repository;

import com.aivle.backend.pipeline.integration.domain.ModuleHandoff;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ModuleHandoffRepository extends JpaRepository<ModuleHandoff, String> {
    Optional<ModuleHandoff> findByIdempotencyKeyAndDeletedAtIsNull(String idempotencyKey);
}
