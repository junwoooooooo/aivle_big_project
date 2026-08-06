package com.aivle.backend.journey;
import java.util.Optional; import org.springframework.data.jpa.repository.*;
public interface InterviewSynthesisRepository extends JpaRepository<InterviewSynthesis,Long>{
 @EntityGraph(attributePaths={"run","study","conceptVersion"}) Optional<InterviewSynthesis> findByRunIdAndDeletedAtIsNull(Long runId);
}
