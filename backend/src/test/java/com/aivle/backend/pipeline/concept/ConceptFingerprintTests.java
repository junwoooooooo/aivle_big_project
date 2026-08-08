package com.aivle.backend.pipeline.concept;

import static org.assertj.core.api.Assertions.assertThat;

import com.aivle.backend.pipeline.concept.domain.ConceptFingerprint;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class ConceptFingerprintTests {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void renamedOnlyCandidateIsDuplicateBecauseNameIsNotFingerprintInput() {
        JsonNode first = candidate("동네 재고 연결", "남은 식재료를 당일 연결한다");
        JsonNode renamed = candidate("마감 식재료 브릿지", "남은 식재료를 당일 연결한다");

        assertThat(ConceptFingerprint.duplicates(first, renamed)).isTrue();
    }

    @Test
    void semanticallySameStructureWithSmallWordingChangesIsDuplicate() {
        JsonNode first = candidate("동네 재고 연결", "남은 식재료를 당일 연결한다");
        JsonNode paraphrased = candidate("지역 재고 연결", "남은 식재료를 당일 바로 연결한다");

        assertThat(ConceptFingerprint.duplicates(first, paraphrased)).isTrue();
    }

    private JsonNode candidate(String name, String mechanism) {
        return mapper.readTree("""
            {"conceptName":"%s","targetUsers":"동네 소형 식품점","problemScenario":"마감 재고가 매일 폐기된다",
             "coreValue":"당일 소량 거래","solutionMechanism":"%s","revenueModel":"월 구독",
             "channels":"상인회 직접 영업","platformRole":"재고와 주문 연결 중개",
             "operatingModel":"가게가 등록하고 직접 인도","partnerModel":"상인회 제휴"}
            """.formatted(name, mechanism));
    }
}
