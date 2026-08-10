package com.aivle.backend.pipeline.concept;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConceptFactorySqlContractTests {
    @Test
    void migrationContainsAllTablesAndIsolationConstraints() throws IOException {
        String sql = Files.readString(Path.of("src/main/resources/db/migration/V1__new_pipeline_baseline.sql"));
        List.of("legal_context_packs", "legal_evidence", "concept_factory_runs", "concept_slots", "concept_attempts",
            "concepts", "concept_legal_assessments", "concept_legal_evidence_links", "concept_rejection_summaries")
            .forEach(table -> assertThat(sql).contains("CREATE TABLE " + table));
        assertThat(sql).contains("FOREIGN KEY (source_idea_brief_snapshot_id, project_id)");
        assertThat(sql).contains("source_snapshot_hash LIKE 'sha256:%'");
        assertThat(sql).contains("replacement_rounds BETWEEN 0 AND 2");
        assertThat(sql).contains("inspected_candidate_count BETWEEN 0 AND 15");
        assertThat(sql).contains("VALIDATING_DISTINCTNESS");
        assertThat(sql).contains("LOCKED_CONSTRAINT_INVALID");
        assertThat(sql).contains("DUPLICATE_CONCEPT");
        assertThat(sql).contains("INSUFFICIENT_DISTINCT_CONCEPTS");
        assertThat(sql).contains("official_identifier VARCHAR(100) NOT NULL");
        assertThat(sql).contains("article_reference VARCHAR(200) NOT NULL");
        assertThat(sql).contains("retrieved_at TIMESTAMP NOT NULL");
        assertThat(sql).contains("query_key VARCHAR(71) NOT NULL");
        assertThat(sql).contains("official_source_uri <> 'https://www.law.go.kr/'");
    }
}
