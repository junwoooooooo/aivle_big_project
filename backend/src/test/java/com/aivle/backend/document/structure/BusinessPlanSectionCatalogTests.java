package com.aivle.backend.document.structure;

import com.aivle.backend.common.entity.PlanSectionType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessPlanSectionCatalogTests {
    private final BusinessPlanSectionCatalog catalog = new BusinessPlanSectionCatalog();

    @Test
    void containsExactlyTwelveCanonicalItems() {
        assertThat(catalog.all()).hasSize(12);
    }

    @Test
    void hasNoDuplicateCodes() {
        assertThat(catalog.all()).extracting(BusinessPlanSectionDefinition::code)
                .doesNotHaveDuplicates();
    }

    @Test
    void fixesUniqueSequenceFromOneToTwelve() {
        assertThat(catalog.all()).extracting(BusinessPlanSectionDefinition::sequence)
                .containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12)
                .doesNotHaveDuplicates();
    }

    @Test
    void marksEveryRemoteRequiredItemAsRequired() {
        assertThat(catalog.all()).allSatisfy(definition -> {
            assertThat(definition.required()).isTrue();
            assertThat(definition.allowedMissingPolicy())
                    .isEqualTo(AllowedMissingPolicy.USER_INPUT_REQUIRED);
        });
    }

    @Test
    void mapsToExistingPlanSectionTypesWithoutUsingThemAsCanonicalIdentity() {
        assertThat(catalog.require(BusinessPlanSectionCode.COST_PROFITABILITY)
                .mappedPlanSectionTypes()).containsExactly(PlanSectionType.FINANCIAL);
        assertThat(catalog.require(BusinessPlanSectionCode.SALES_GOALS_FINANCIAL_PROJECTIONS)
                .mappedPlanSectionTypes()).containsExactly(PlanSectionType.FINANCIAL);
        assertThat(catalog.require(BusinessPlanSectionCode.SCHEDULE_RISK)
                .mappedPlanSectionTypes()).containsExactlyInAnyOrder(
                        PlanSectionType.SCHEDULE,
                        PlanSectionType.RISK
                );
    }
}
