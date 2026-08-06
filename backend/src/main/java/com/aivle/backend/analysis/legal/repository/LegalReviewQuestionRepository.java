package com.aivle.backend.analysis.legal.repository;

import com.aivle.backend.analysis.legal.entity.LegalReviewQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface LegalReviewQuestionRepository extends JpaRepository<LegalReviewQuestion, Long> {
    List<LegalReviewQuestion> findByLegalReviewIdAndDeletedAtIsNullOrderByDisplayOrder(Long reviewId);
}
