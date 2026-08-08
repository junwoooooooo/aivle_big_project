package com.aivle.backend.pipeline.concept;

import static org.assertj.core.api.Assertions.assertThat;

import com.aivle.backend.pipeline.concept.domain.ConceptGenerationStrategy;
import com.aivle.backend.pipeline.concept.domain.ConceptGenerationStrategyPolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ConceptGenerationStrategyPolicyTests {
    @Test
    void minimalSeedRemainsExplore() {
        assertThat(ConceptGenerationStrategyPolicy.decide(seed())).isEqualTo(ConceptGenerationStrategy.EXPLORE);
    }

    @Test
    void partialConfirmedStructureIsRefine() {
        var fields = new ArrayList<>(seed());
        fields.add(field("price", "월 9,900원", "LOCKED"));
        assertThat(ConceptGenerationStrategyPolicy.decide(fields)).isEqualTo(ConceptGenerationStrategy.REFINE);
    }

    @Test
    void concreteOriginalWithSeveralCommitmentsIsAsIs() {
        var fields = new ArrayList<>(seed());
        fields.add(field("revenueModel", "월 구독", "LOCKED"));
        fields.add(field("price", "월 9,900원", "LOCKED"));
        fields.add(field("channels", "제휴 풋살장", "LOCKED"));
        assertThat(ConceptGenerationStrategyPolicy.decide(fields)).isEqualTo(ConceptGenerationStrategy.AS_IS);
    }

    private List<Map<String, String>> seed() {
        return List.of(
            field("ideaOverview", "서울 직장인을 제휴 풋살장의 즉석 경기 팀으로 연결하는 구독 서비스", "LOCKED"),
            field("problem", "혼자서는 풋살 경기 인원을 모으기 어렵다", "LOCKED"),
            field("targetUsers", "서울 직장인", "LOCKED")
        );
    }

    private Map<String, String> field(String key, String value, String authority) {
        return Map.of("fieldKey", key, "value", value, "source", "USER_INPUT", "authority", authority);
    }
}
