package com.aivle.backend.integration.ai.legal;

import com.aivle.backend.analysis.legal.entity.*;
import com.aivle.backend.common.entity.RiskLevel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

@Component
@ConditionalOnProperty(prefix = "app.ai", name = "enabled", havingValue = "false", matchIfMissing = true)
public class MockLegalReviewAiClient implements LegalReviewAiClient {
    @Override
    public LegalReviewAiResponse review(LegalReviewAiRequest request) {
        List<LegalReviewAiResponse.Finding> findings = Arrays.stream(LegalCategory.values())
            .map(category -> new LegalReviewAiResponse.Finding(
                category,
                category == LegalCategory.INDUSTRY_SPECIFIC
                    ? LegalApplicability.INSUFFICIENT_INFORMATION
                    : LegalApplicability.POSSIBLY_APPLICABLE,
                category == LegalCategory.PRIVACY_AND_DATA ? RiskLevel.HIGH : RiskLevel.MEDIUM,
                displayName(category),
                "확정된 사업계획의 정보만으로 사전 확인이 필요한 영역입니다.",
                "구체적인 운영 방식과 적용 지역에 따라 의무가 달라질 수 있습니다.",
                "출시 전 담당 기관 또는 자격 있는 전문가에게 적용 여부를 확인하세요.",
                List.of("Mock 결과: 확정 계획 스냅샷 기반"),
                request.sections().stream().limit(2).map(LegalReviewAiRequest.Section::code).toList(),
                category == LegalCategory.PRIVACY_AND_DATA,
                new BigDecimal("0.7500")
            )).toList();
        return new LegalReviewAiResponse(
            "mock", "mock-legal-review-v1", "mock-legal-" + request.structuredPlanId(),
            RiskLevel.HIGH,
            "확정된 계획에서 우선 확인할 법률·규제 영역을 식별했습니다. 이는 자문 또는 적법성 판정이 아닙니다.",
            findings,
            List.of(new LegalReviewAiResponse.Question(
                "서비스를 실제로 제공할 국가와 지역은 어디인가요?",
                "관할에 따라 등록·허가·개인정보 의무가 달라집니다."))
        );
    }

    private String displayName(LegalCategory category) {
        return category.name().replace('_', ' ');
    }
}
