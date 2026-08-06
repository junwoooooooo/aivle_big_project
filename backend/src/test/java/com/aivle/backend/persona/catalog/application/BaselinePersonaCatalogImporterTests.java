package com.aivle.backend.persona.catalog.application;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class BaselinePersonaCatalogImporterTests {
    @Test
    void parsesQuotedCommasAndEscapedQuotes() {
        assertThat(BaselinePersonaCatalogImporter.parseCsvLine(
            "20대,여,386,\"유형, A\",23.4%,\"\"\"핵심\"\" 특징\""))
            .containsExactly("20대", "여", "386", "유형, A", "23.4%", "\"핵심\" 특징");
    }

    @Test
    void rejectsUnterminatedCsvFields() {
        assertThatThrownBy(() ->
            BaselinePersonaCatalogImporter.parseCsvLine("\"broken"))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
