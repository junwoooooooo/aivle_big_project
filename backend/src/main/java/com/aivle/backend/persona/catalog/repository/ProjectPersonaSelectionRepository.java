package com.aivle.backend.persona.catalog.repository;

import com.aivle.backend.persona.catalog.entity.ProjectPersonaSelection;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface ProjectPersonaSelectionRepository
    extends JpaRepository<ProjectPersonaSelection, Long> {

    @EntityGraph(attributePaths = "persona")
    Optional<ProjectPersonaSelection> findByProjectId(Long projectId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ProjectPersonaSelection s where s.project.id = :projectId")
    Optional<ProjectPersonaSelection> findByProjectIdForUpdate(
        @Param("projectId") Long projectId
    );
}
