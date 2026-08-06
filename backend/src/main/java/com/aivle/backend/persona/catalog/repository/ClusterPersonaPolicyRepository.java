package com.aivle.backend.persona.catalog.repository;

import com.aivle.backend.persona.catalog.entity.ClusterPersonaPolicy;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

public interface ClusterPersonaPolicyRepository
    extends JpaRepository<ClusterPersonaPolicy, Long> {

    @EntityGraph(attributePaths = "persona")
    List<ClusterPersonaPolicy> findAllByOrderByDisplayOrderAsc();

    @EntityGraph(attributePaths = "persona")
    List<ClusterPersonaPolicy> findByEnabledTrueOrderByDisplayOrderAsc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = "persona")
    Optional<ClusterPersonaPolicy> findByPersonaId(Long personaId);

    Optional<ClusterPersonaPolicy> findByPersonaIdAndEnabledTrue(Long personaId);

    long countByEnabledTrue();
}
