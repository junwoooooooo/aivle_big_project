package com.aivle.backend.pipeline.conceptportfolio;

import static org.assertj.core.api.Assertions.*;

import com.aivle.backend.pipeline.conceptportfolio.selection.domain.*;
import com.aivle.backend.pipeline.marketseed.domain.MarketAnalysisSeedSnapshot;
import com.aivle.backend.taskrun.domain.TaskType;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ConceptPortfolioSelectionP5P6ContractTests {
    private static final Path MIGRATION = Path.of(
        "src/main/resources/db/migration/V12__add_concept_portfolio_selection_and_legal_handoff.sql");
    private static final Path REVISION_MIGRATION = Path.of(
        "src/main/resources/db/migration/V13__bind_concept_portfolio_delta_legal_revision.sql");

    @Test
    void migrationCreatesDedicatedPortfolioAuthoritiesAndSevenHypotheses() throws Exception {
        String sql = Files.readString(MIGRATION, StandardCharsets.UTF_8);
        assertThat(sql).contains(
            "CREATE TABLE concept_portfolio_selections",
            "CREATE TABLE concept_portfolio_hypothesis_decisions",
            "CREATE TABLE concept_portfolio_delta_legal_reviews",
            "CREATE TABLE concept_legal_regulatory_reports",
            "'TARGET_REGION'", "'PRE_MARKET_SOM_SHARE'", "'PRE_MARKET_SOM'",
            "uk_cp_selection_current", "uk_cp_hypothesis_version");
        assertThat(sql).contains("source_type = 'LEGACY'", "source_type = 'CONCEPT_PORTFOLIO_V2'",
            "portfolio_selection_id", "portfolio_concept_id", "legal_report_id");
        assertThat(sql.toLowerCase()).doesNotContain("insert into concepts", "insert into concept_selections");
    }

    @Test
    void hypothesisAuthorityIsExactlySevenAndOnlyFiveAreLegalSensitive() {
        assertThat(Arrays.stream(PortfolioHypothesisType.values()).map(Enum::name)).containsExactly(
            "TARGET_REGION", "REVENUE_MODEL", "PRICE", "CHANNELS", "DIFFERENTIATORS",
            "PRE_MARKET_SOM_SHARE", "PRE_MARKET_SOM");
        assertThat(Arrays.stream(PortfolioHypothesisType.values()).filter(PortfolioHypothesisType::legalSensitive)
            .map(Enum::name)).containsExactly(
                "TARGET_REGION", "REVENUE_MODEL", "PRICE", "CHANNELS", "DIFFERENTIATORS");
    }

    @Test
    void additiveMigrationBindsDeltaReviewToHypothesisRevision() throws Exception {
        String sql = Files.readString(REVISION_MIGRATION, StandardCharsets.UTF_8);
        assertThat(sql).contains("hypothesis_revision", "NOT NULL", "concept_portfolio_delta_legal_reviews");
    }

    @Test
    void explicitSelectionNeverDerivesAuthorityFromEngineDefaultAndRejectsLateResult() {
        ConceptPortfolioSelection selection = selection();
        selection.attachTask("prepare-task", "PREPARE_HYPOTHESES");
        assertThat(selection.getStatus()).isEqualTo(ConceptPortfolioSelectionStatus.HYPOTHESES_PREPARING);
        assertThatThrownBy(() -> selection.completeTask("old-task",
            ConceptPortfolioSelectionStatus.PENDING_HYPOTHESIS_CONFIRMATION, true))
            .isInstanceOf(IllegalStateException.class);
        selection.completeTask("prepare-task",
            ConceptPortfolioSelectionStatus.PENDING_HYPOTHESIS_CONFIRMATION, true);
        assertThat(selection.isCurrent()).isTrue();
        assertThat(selection.getHypothesisRevision()).isEqualTo(1);
        selection.markStale();
        assertThat(selection.isCurrent()).isFalse();
        assertThat(selection.getStatus()).isEqualTo(ConceptPortfolioSelectionStatus.STALE);
    }

    @Test
    void marketSeedV2UsesPortfolioForeignKeysWithoutSyntheticLegacyIds() {
        MarketAnalysisSeedSnapshot value = MarketAnalysisSeedSnapshot.createPortfolio(
            "seed-v2", 42L, 17L, "portfolio-concept", "legal-report", "2.0",
            hash('a'), hash('b'), "{}", 7L, Instant.parse("2026-08-11T00:00:00Z"));
        assertThat(value.getSourceType()).isEqualTo("CONCEPT_PORTFOLIO_V2");
        assertThat(value.getSelectionId()).isNull();
        assertThat(value.getConceptId()).isNull();
        assertThat(value.getPortfolioSelectionId()).isEqualTo(17L);
        assertThat(value.getPortfolioConceptId()).isEqualTo("portfolio-concept");
        assertThat(value.getLegalReportId()).isEqualTo("legal-report");
        value.markStale(Instant.parse("2026-08-11T00:01:00Z"));
        assertThat(value.getStaleAt()).isNotNull();
    }

    @Test
    void oneSelectionActionTaskTypeCoversAllPostSelectionActions() {
        assertThat(TaskType.valueOf("CONCEPT_PORTFOLIO_V2_SELECTION_ACTION")).isNotNull();
    }

    @Test
    void finalLegalReportIsCurrentHistoryThenBecomesStaleWithoutMutation() {
        ConceptPortfolioSelection selection = selection();
        ConceptLegalRegulatoryReport report = ConceptLegalRegulatoryReport.create(
            "report", selection, hash('f'), hash('1'), hash('2'), "{\"officialEvidenceReferences\":[]}",
            hash('3'), 7L, LocalDate.of(2026, 8, 11));
        assertThat(report.getStatus()).isEqualTo("CURRENT");
        assertThat(report.getSelectionId()).isEqualTo(17L);
        assertThat(report.getSelectedConceptHash()).isEqualTo(selection.getSelectedConceptHash());
        report.markStale();
        assertThat(report.getStatus()).isEqualTo("STALE");
        assertThat(report.getReportJson()).contains("officialEvidenceReferences");
    }

    private ConceptPortfolioSelection selection() {
        ConceptPortfolioSelection value = ConceptPortfolioSelection.create(42L, "run", "concept",
            "candidate", hash('c'), hash('d'), "사용자 명시 선택", hash('e'), "selection-key",
            7L, Instant.parse("2026-08-11T00:00:00Z"));
        ReflectionTestUtils.setField(value, "id", 17L);
        return value;
    }
    private String hash(char value) { return "sha256:" + String.valueOf(value).repeat(64); }
}
