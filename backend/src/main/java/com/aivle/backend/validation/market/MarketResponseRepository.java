package com.aivle.backend.validation.market;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketResponseRepository extends JpaRepository<MarketResponsePrediction, Long> {
    List<MarketResponsePrediction> findAllByProjectIdAndDeletedAtIsNullOrderByUpdatedAtDesc(Long projectId);
    Optional<MarketResponsePrediction> findByIdAndProjectIdAndDeletedAtIsNull(Long id, Long projectId);
}
