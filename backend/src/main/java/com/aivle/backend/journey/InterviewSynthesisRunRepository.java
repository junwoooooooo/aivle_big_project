package com.aivle.backend.journey;
import java.util.Optional; import org.springframework.data.jpa.repository.*;
public interface InterviewSynthesisRunRepository extends JpaRepository<InterviewSynthesisRun,Long>{
 @EntityGraph(attributePaths={"study","conceptVersion","taskRun"})
 Optional<InterviewSynthesisRun> findTopByStudyIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(Long studyId);
}
