package com.aivle.backend.persona.catalog.repository;

import com.aivle.backend.persona.catalog.entity.BaselinePersona;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;

public interface BaselinePersonaRepository extends JpaRepository<BaselinePersona, Long> {
    long countByCatalogVersionAndDeletedAtIsNull(String catalogVersion);
    List<BaselinePersona> findByCatalogVersionAndDeletedAtIsNullOrderByDisplayOrder(
        String catalogVersion);
    Optional<BaselinePersona> findByPersonaCodeAndCatalogVersionAndDeletedAtIsNull(
        String personaCode, String catalogVersion);
    Optional<BaselinePersona> findByIdAndCatalogVersionAndDeletedAtIsNull(
        Long id, String catalogVersion);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select p from BaselinePersona p
        where p.catalogVersion = :catalogVersion
          and p.deletedAt is null
        order by p.id
        """)
    List<BaselinePersona> lockActiveCatalog(
        @Param("catalogVersion") String catalogVersion
    );
}
