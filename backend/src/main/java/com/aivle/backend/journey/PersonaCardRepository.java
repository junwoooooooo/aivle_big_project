package com.aivle.backend.journey;
import java.util.List; import org.springframework.data.jpa.repository.*;
public interface PersonaCardRepository extends JpaRepository<PersonaCard,Long>{
 @EntityGraph(attributePaths={"study","conceptVersion"}) List<PersonaCard> findByStudyIdAndDeletedAtIsNullOrderByDisplayOrder(Long studyId);
}
