package com.aivle.backend.pipeline.idea.repository;

import com.aivle.backend.pipeline.idea.domain.IdeaAnswer;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdeaAnswerRepository extends JpaRepository<IdeaAnswer, Long> {
    List<IdeaAnswer> findAllByBriefIdOrderById(String briefId);
    Optional<IdeaAnswer> findByBriefIdAndQuestionIdAndIdempotencyKey(String briefId, String questionId, String idempotencyKey);
    void deleteAllByBriefId(String briefId);
}
