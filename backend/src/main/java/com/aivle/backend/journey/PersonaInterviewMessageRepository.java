package com.aivle.backend.journey;
import java.util.List; import org.springframework.data.jpa.repository.JpaRepository;
public interface PersonaInterviewMessageRepository extends JpaRepository<PersonaInterviewMessage,Long>{
 List<PersonaInterviewMessage> findByInterviewIdAndDeletedAtIsNullOrderBySequenceNumber(Long interviewId);
}
