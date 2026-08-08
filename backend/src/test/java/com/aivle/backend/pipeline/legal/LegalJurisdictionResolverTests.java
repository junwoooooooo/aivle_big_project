package com.aivle.backend.pipeline.legal;

import static org.assertj.core.api.Assertions.assertThat;

import com.aivle.backend.pipeline.legal.application.LegalJurisdictionResolver;
import com.aivle.backend.pipeline.legal.application.LegalJurisdictionResolver.Jurisdiction;
import org.junit.jupiter.api.Test;

class LegalJurisdictionResolverTests {
    private final LegalJurisdictionResolver resolver = new LegalJurisdictionResolver();

    @Test
    void resolvesOnlyExplicitKrCompatibleRegions() {
        assertThat(resolver.resolve("대한민국 전국")).isEqualTo(Jurisdiction.KR);
        assertThat(resolver.resolve("서울 및 경기")).isEqualTo(Jurisdiction.KR);
        assertThat(resolver.resolve("KR")).isEqualTo(Jurisdiction.KR);
    }

    @Test
    void foreignAmbiguousAndMixedRegionsNeverDefaultToKr() {
        assertThat(resolver.resolve("미국 캘리포니아")).isEqualTo(Jurisdiction.UNSUPPORTED);
        assertThat(resolver.resolve("대한민국 및 일본")).isEqualTo(Jurisdiction.UNSUPPORTED);
        assertThat(resolver.resolve("온라인 글로벌")).isEqualTo(Jurisdiction.UNSUPPORTED);
        assertThat(resolver.resolve("미정")).isEqualTo(Jurisdiction.UNSUPPORTED);
    }
}
