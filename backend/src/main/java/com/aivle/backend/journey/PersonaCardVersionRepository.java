package com.aivle.backend.journey;
import java.util.*; import org.springframework.data.jpa.repository.*; import org.springframework.data.repository.query.Param;
public interface PersonaCardVersionRepository extends JpaRepository<PersonaCardVersion,Long>{
 @EntityGraph(attributePaths={"personaCard","personaCard.study","conceptVersion"})
 @Query("select v from PersonaCardVersion v where v.personaCard.study.id=:studyId and v.deletedAt is null order by v.personaCard.displayOrder,v.versionNumber desc")
 List<PersonaCardVersion> findCurrentByStudy(@Param("studyId") Long studyId);
 @EntityGraph(attributePaths={"personaCard","personaCard.study","conceptVersion"})
 List<PersonaCardVersion> findByIdInAndProjectIdAndConceptVersionIdAndDeletedAtIsNull(Collection<Long> ids,Long projectId,Long conceptVersionId);
}
