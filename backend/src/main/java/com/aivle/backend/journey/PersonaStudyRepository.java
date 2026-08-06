package com.aivle.backend.journey;
import java.util.Optional; import org.springframework.data.jpa.repository.*;
public interface PersonaStudyRepository extends JpaRepository<PersonaStudy,Long>{
 @EntityGraph(attributePaths={"project","ideaVersion","conceptVersion","conceptVersion.concept","generationTaskRun"})
 Optional<PersonaStudy> findTopByProjectIdAndConceptVersionIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(Long projectId,Long conceptVersionId);
}
