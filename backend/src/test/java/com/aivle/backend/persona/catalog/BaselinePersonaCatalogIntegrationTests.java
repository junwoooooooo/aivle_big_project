package com.aivle.backend.persona.catalog;

import com.aivle.backend.persona.catalog.repository.BaselinePersonaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class BaselinePersonaCatalogIntegrationTests {
    @Autowired BaselinePersonaRepository repository;

    @Test
    void importsOnlyTheVersionedFiftySixRowAggregateCatalog() {
        var personas = repository
            .findByCatalogVersionAndDeletedAtIsNullOrderByDisplayOrder(
                BaselinePersonaCatalog.VERSION);
        assertThat(personas).hasSize(56);
        assertThat(personas).extracting(item -> item.getPersonaCode()).doesNotHaveDuplicates();
        assertThat(personas).extracting(item -> item.getClusterId()).doesNotHaveDuplicates();
        assertThat(personas).allSatisfy(item -> {
            assertThat(item.getCatalogVersion()).isEqualTo("persona-catalog-v1");
            assertThat(item.getDataVersion()).isEqualTo("p25v32");
            assertThat(item.getSourceHash())
                .isEqualTo("3EAC16287BC591531CC04AA2AE578BBAEC8239CDF067F79919DC891AB47C2D82");
            assertThat(item.getWeightedShare())
                .isBetween(BigDecimal.ZERO, BigDecimal.ONE);
            assertThat(item.getSampleSize()).isNull();
            assertThat(item.getEvidenceMetricsJson()).contains("DERIVED_FROM_DATA");
            assertThat(item.getLimitationsJson()).contains("개별 군집 표본수");
            assertThat(item.getKeyTraitsJson()).doesNotContain("\"pid\"", "\"p25wt\"");
        });
    }
}
