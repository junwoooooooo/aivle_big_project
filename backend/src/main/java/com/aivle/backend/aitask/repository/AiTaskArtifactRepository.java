package com.aivle.backend.aitask.repository;

import com.aivle.backend.aitask.entity.AiArtifactRole;
import com.aivle.backend.aitask.entity.AiTaskArtifact;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AiTaskArtifactRepository
    extends JpaRepository<AiTaskArtifact, Long> {

    @Query("""
        select artifact
        from AiTaskArtifact artifact
        join fetch artifact.storedFile
        where artifact.analysisJob.id = :jobId
          and artifact.role = :role
          and artifact.deletedAt is null
        """)
    Optional<AiTaskArtifact> findByJobIdAndRole(
        @Param("jobId") Long jobId,
        @Param("role") AiArtifactRole role
    );

    @Query("""
        select artifact
        from AiTaskArtifact artifact
        join fetch artifact.storedFile
        join fetch artifact.analysisJob job
        join fetch artifact.project project
        where artifact.id = :artifactId
          and project.id = :projectId
          and project.owner.id = :userId
          and artifact.deletedAt is null
        """)
    Optional<AiTaskArtifact> findOwned(
        @Param("artifactId") Long artifactId,
        @Param("projectId") Long projectId,
        @Param("userId") Long userId
    );

    @Query("""
        select artifact
        from AiTaskArtifact artifact
        join fetch artifact.storedFile
        where artifact.analysisJob.id = :jobId
          and artifact.project.id = :projectId
          and artifact.project.owner.id = :userId
          and artifact.role = :role
          and artifact.deletedAt is null
        """)
    Optional<AiTaskArtifact> findOwnedByJobAndRole(
        @Param("jobId") Long jobId,
        @Param("projectId") Long projectId,
        @Param("userId") Long userId,
        @Param("role") AiArtifactRole role
    );
}
