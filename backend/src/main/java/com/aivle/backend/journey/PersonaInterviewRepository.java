package com.aivle.backend.journey;
import java.util.List; import org.springframework.data.jpa.repository.*;
public interface PersonaInterviewRepository extends JpaRepository<PersonaInterview,Long>{
 @EntityGraph(attributePaths={"study","conceptVersion","personaCardVersion","personaCardVersion.personaCard","taskRun"})
 List<PersonaInterview> findByStudyIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(Long studyId);
}
