package com.aivle.backend.pipeline.conceptportfolio;

import static org.assertj.core.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConceptPortfolioMigrationContractTests {
    private static final Path MIGRATION = Path.of(
        "src/main/resources/db/migration/V10__add_concept_portfolio_v2_product.sql");

    @Test
    void v10IsAdditiveAndDefinesTheFiveCutoverTables() throws IOException {
        String sql = Files.readString(MIGRATION).toLowerCase();

        assertThat(sql).doesNotContain("drop table", "truncate table", "alter table");
        assertThat(List.of(
            "concept_portfolio_runs",
            "concept_portfolio_concepts",
            "concept_portfolio_continuations",
            "concept_input_requests",
            "concept_input_responses"
        )).allSatisfy(table -> assertThat(sql).contains("create table " + table));
        assertThat(sql).contains("requested_max_concepts between 1 and 5");
        assertThat(sql).contains("where is_current = true and deleted_at is null");
        assertThat(sql).doesNotContain("slot");
    }

    @Test
    void v11AddsOnlyRunLineageUniqueness() throws IOException {
        String sql = Files.readString(Path.of(
            "src/main/resources/db/migration/V11__harden_concept_portfolio_lineage.sql"))
            .toLowerCase();
        assertThat(sql).contains("alter table concept_portfolio_concepts");
        assertThat(sql).contains("unique (run_id, lineage_id)");
        assertThat(sql).doesNotContain("create table", "drop table", "truncate table");
    }
}
