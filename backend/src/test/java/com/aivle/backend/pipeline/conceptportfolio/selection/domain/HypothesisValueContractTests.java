package com.aivle.backend.pipeline.conceptportfolio.selection.domain;

import static org.assertj.core.api.Assertions.*;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.ObjectMapper;

class HypothesisValueContractTests {
    private final ObjectMapper mapper = new ObjectMapper();

    static Stream<PortfolioHypothesisType> textTypes() {
        return Stream.of(PortfolioHypothesisType.TARGET_REGION, PortfolioHypothesisType.REVENUE_MODEL,
            PortfolioHypothesisType.PRICE, PortfolioHypothesisType.CHANNELS,
            PortfolioHypothesisType.DIFFERENTIATORS);
    }

    @ParameterizedTest @MethodSource("textTypes")
    void fiveLegalSensitiveHypothesesRequireCanonicalStrings(PortfolioHypothesisType type) {
        assertThat(HypothesisValueContract.canonicalize(mapper, type,
            mapper.getNodeFactory().textNode("  기존 정상 문자열  ")).asText())
            .isEqualTo("  기존 정상 문자열  ");
        assertThatThrownBy(() -> HypothesisValueContract.canonicalize(mapper, type,
            mapper.createObjectNode().put("value", "silent stringify forbidden")))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void channelsAndDifferentiatorsNormalizeOnlyFlatNonblankStringLists() {
        assertThat(HypothesisValueContract.canonicalize(mapper, PortfolioHypothesisType.CHANNELS,
            mapper.readTree("[\" 웹 \",\"파트너\\n판매\"]")).asText()).isEqualTo("웹, 파트너 판매");
        assertThat(HypothesisValueContract.canonicalize(mapper, PortfolioHypothesisType.DIFFERENTIATORS,
            mapper.readTree("[\"빠른 설정\",\"기존 연동\"]")).asText()).isEqualTo("빠른 설정, 기존 연동");
        assertThatThrownBy(() -> HypothesisValueContract.canonicalize(mapper,
            PortfolioHypothesisType.CHANNELS, mapper.readTree("[\"웹\",{\"nested\":true}]")))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void somStructuredObjectContractsRemainStrictAndUnchanged() {
        var share = mapper.readTree("{\"targetSharePercent\":1.0,\"horizonYears\":3,"
            + "\"rationale\":\"근거\",\"assumptions\":[\"가정\"]}");
        var som = mapper.readTree("{\"amount\":100000000,\"currency\":\"KRW\","
            + "\"period\":\"연간\",\"calculationBasis\":\"산식\","
            + "\"assumptions\":[\"가정\"],\"confidence\":\"MEDIUM\"}");
        assertThat(HypothesisValueContract.canonicalize(mapper,
            PortfolioHypothesisType.PRE_MARKET_SOM_SHARE, share)).isEqualTo(share);
        assertThat(HypothesisValueContract.canonicalize(mapper,
            PortfolioHypothesisType.PRE_MARKET_SOM, som)).isEqualTo(som);
        assertThatThrownBy(() -> HypothesisValueContract.canonicalize(mapper,
            PortfolioHypothesisType.PRE_MARKET_SOM, mapper.readTree("{\"amount\":1}")))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
