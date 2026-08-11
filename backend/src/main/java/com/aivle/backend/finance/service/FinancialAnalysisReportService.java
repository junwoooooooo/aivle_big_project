package com.aivle.backend.finance.service;

import com.aivle.backend.finance.dto.FinancialModuleResponse;
import com.aivle.backend.finance.entity.FinancialAnalysisReport;
import com.aivle.backend.finance.repository.FinancialAnalysisReportRepository;
import com.aivle.backend.pipeline.finance.api.FinancialApiModels.SnapshotView;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class FinancialAnalysisReportService {
    private final FinancialAnalysisReportRepository reports;
    private final ObjectMapper mapper;

    @Transactional
    public FinancialModuleResponse save(Long ownerId, Long projectId, SnapshotView snapshot, FinancialModuleResponse result) {
        reports.findFirstByProjectIdAndInputSnapshotIdAndDeletedAtIsNullOrderByCompletedAtDesc(projectId, snapshot.snapshotId())
            .ifPresent(FinancialAnalysisReport::softDelete);
        Long businessModelRunId = snapshot.snapshot().path("sourceMarketResearchRunId").isNumber()
            ? snapshot.snapshot().path("sourceMarketResearchRunId").asLong() : null;
        reports.save(FinancialAnalysisReport.create(UUID.randomUUID().toString(), projectId, snapshot.snapshotId(),
            snapshot.snapshotHash(), businessModelRunId, mapper.writeValueAsString(result), ownerId, Instant.now()));
        return result;
    }

    @Transactional(readOnly = true)
    public FinancialModuleResponse current(Long projectId, String snapshotId) {
        return reports.findFirstByProjectIdAndInputSnapshotIdAndDeletedAtIsNullOrderByCompletedAtDesc(projectId, snapshotId)
            .map(value -> mapper.readValue(value.getReportJson(), FinancialModuleResponse.class)).orElse(null);
    }
}
