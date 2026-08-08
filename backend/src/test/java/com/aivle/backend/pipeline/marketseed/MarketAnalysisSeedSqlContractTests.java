package com.aivle.backend.pipeline.marketseed;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class MarketAnalysisSeedSqlContractTests {
    @Test
    void baselineCreatesImmutableSeedIdentityAndMarketForeignKeys() throws Exception {
        String sql = Files.readString(Path.of("src", "main", "resources", "db", "migration", "V1__new_pipeline_baseline.sql"))
            .toLowerCase(Locale.ROOT);
        assertThat(sql).contains("create table market_analysis_seed_snapshots", "uk_market_seed_snapshot_selection unique (selection_id)",
            "schema_version varchar(20) not null", "snapshot_hash varchar(71) not null", "finalized_at timestamp with time zone not null");
        assertThat(sql).contains("references market_analysis_seed_snapshots(id, project_id)");
    }
}
