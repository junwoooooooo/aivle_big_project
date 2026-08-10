package com.aivle.backend.pipeline.conceptportfolio;

import static org.assertj.core.api.Assertions.assertThat;

import com.aivle.backend.pipeline.conceptportfolio.application.ConceptPortfolioJsonHasher;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class ConceptPortfolioProductionHashTests {
    @Test
    void marketSeedHashMatchesPythonCanonicalNumbersAndUnicode() {
        ObjectMapper mapper = new ObjectMapper();
        var value = mapper.readTree("{\"amount\":240000000.0,\"currency\":\"KRW\",\"label\":\"e\\u0301\"}");
        assertThat(new ConceptPortfolioJsonHasher(mapper).productionCompatibleHash(value))
            .isEqualTo("sha256:bb4e2b987fd7244ba7280354ff88e0740afa31123243ea4778904ab5c4ee51f9");
    }
}
