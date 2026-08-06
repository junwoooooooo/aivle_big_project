package com.aivle.backend.pipeline.idea.repository;

import com.aivle.backend.pipeline.idea.domain.IdeaQuestion;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdeaQuestionRepository extends JpaRepository<IdeaQuestion, String> {
    List<IdeaQuestion> findAllByBriefIdAndActiveTrueOrderByDisplayOrder(String briefId);
    Optional<IdeaQuestion> findByIdAndBriefId(String id, String briefId);
    long countByBriefIdAndActiveTrueAndAnsweredFalse(String briefId);
}
