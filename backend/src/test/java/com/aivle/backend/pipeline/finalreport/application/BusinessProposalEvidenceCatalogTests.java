package com.aivle.backend.pipeline.finalreport.application;

import static com.aivle.backend.pipeline.finalreport.application.FinalReportComposer.ReportSource;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class BusinessProposalEvidenceCatalogTests {
    private final ObjectMapper mapper = new ObjectMapper();
    private final BusinessProposalEvidenceCatalog catalog = new BusinessProposalEvidenceCatalog(mapper);

    @Test
    void extractsTraceableMarketInterviewEvidenceWithoutInventingCountsOrQuotes() {
        var source = new ReportSource("MARKET_INTERVIEW", "run-1", null, 1, null,
            Instant.parse("2026-08-18T00:00:00Z"), mapper.readTree("""
                {"themes":[{"title":"기술 사용 장벽","mentionCount":15,
                  "participantIds":["R005","R007"],"quote":"설치 절차가 복잡해요."}],
                 "limitations":["실제 고객 조사가 아닙니다."]}
                """));

        var result = catalog.build(List.of(source));

        assertThat(result).allSatisfy(item -> assertThat(item.path("evidenceKey").asText())
            .matches("EV-[0-9a-f]{24}"));
        assertThat(result.toString()).contains("기술 사용 장벽", "15", "설치 절차가 복잡해요.",
            "가상 정성 탐색이며 실제 고객 조사나 모집단 결과가 아닙니다.", "respondentIds");
    }
}
