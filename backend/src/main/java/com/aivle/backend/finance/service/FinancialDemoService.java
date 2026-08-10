package com.aivle.backend.finance.service;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.finance.dto.FinancialModels.Assumptions;
import com.aivle.backend.finance.dto.FinancialModuleRequest;
import com.aivle.backend.finance.dto.FinancialModuleResponse;
import com.aivle.backend.finance.entity.RevenueModel;
import com.aivle.backend.project.repository.ProjectRepository;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;

/** Local-test fixture. It never creates or alters an upstream/financial DB snapshot. */
@Service
public class FinancialDemoService {
    private final ProjectRepository projects;
    private final FinancialModuleService module;

    public FinancialDemoService(ProjectRepository projects, FinancialModuleService module) {
        this.projects = projects;
        this.module = module;
    }

    public FinancialModuleResponse run(Long ownerId, Long projectId, FinancialModuleRequest request) {
        projects.findByIdAndOwnerIdAndDeletedAtIsNull(projectId, ownerId)
            .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));
        // The web client posts an empty JSON object for the default test action.
        // Treat absent or incomplete input as a request for the deterministic local fixture.
        return module.preview(request == null || request.assumptions() == null ? fixture() : request);
    }

    private FinancialModuleRequest fixture() {
        Assumptions values = new Assumptions(RevenueModel.ONE_TIME,
            money(85000), decimal(120), decimal(4), money(28000), decimal(3), money(1500),
            money(8000000), money(2500000), money(1200000), money(1000000), money(500000),
            money(30000000), money(8000000), money(5000000), money(2000000),
            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        return new FinancialModuleRequest(values, 36, FinancialModuleRequest.MoneyUnit.KRW,
            null, 2000, 15, 5, 10, 20260810L);
    }

    private BigDecimal money(long value) { return BigDecimal.valueOf(value); }
    private BigDecimal decimal(long value) { return BigDecimal.valueOf(value); }
}
