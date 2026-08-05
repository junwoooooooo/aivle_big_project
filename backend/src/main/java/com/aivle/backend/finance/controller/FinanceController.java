package com.aivle.backend.finance.controller;

import com.aivle.backend.finance.dto.FinancialAnalysisResponseDTO;
import com.aivle.backend.finance.dto.FinancialInputDTO;
import com.aivle.backend.finance.service.FinanceService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/finance")
public class FinanceController {

    private final FinanceService financeService;

    public FinanceController(FinanceService financeService) {
        this.financeService = financeService;
    }

    @PostMapping("/analysis")
    public ResponseEntity<FinancialAnalysisResponseDTO> analyzeFinancials(@RequestBody FinancialInputDTO input) {
        FinancialAnalysisResponseDTO response = financeService.analyze(input);
        return ResponseEntity.ok(response);
    }
}
