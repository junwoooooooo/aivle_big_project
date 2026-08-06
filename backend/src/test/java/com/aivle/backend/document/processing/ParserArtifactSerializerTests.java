package com.aivle.backend.document.processing;

import static org.assertj.core.api.Assertions.assertThat;

import com.aivle.backend.common.entity.FileStatus;
import com.aivle.backend.common.entity.StorageType;
import com.aivle.backend.document.application.processing.DocumentJobContext;
import com.aivle.backend.document.application.processing.ParserArtifactSerializer;
import com.aivle.backend.document.parsing.ParsedBlockType;
import com.aivle.backend.document.parsing.ParsedDocument;
import com.aivle.backend.document.parsing.ParsedDocumentBlock;
import com.aivle.backend.document.parsing.ParsedDocumentType;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class ParserArtifactSerializerTests {
    private final ParserArtifactSerializer serializer =
        new ParserArtifactSerializer(new ObjectMapper());

    @Test
    void serializesDeterministicCanonicalArtifact() {
        DocumentJobContext context = context();
        ParsedDocument first = parsed(Instant.parse(
            "2026-01-01T00:00:00Z"
        ));
        ParsedDocument second = parsed(Instant.parse(
            "2026-02-01T00:00:00Z"
        ));

        var firstArtifact = serializer.serialize(context, first);
        var secondArtifact = serializer.serialize(context, second);

        assertThat(firstArtifact.bytes())
            .containsExactly(secondArtifact.bytes());
        assertThat(firstArtifact.checksumSha256())
            .isEqualTo(secondArtifact.checksumSha256())
            .matches("[0-9a-f]{64}");
        assertThat(new String(
            firstArtifact.bytes(),
            java.nio.charset.StandardCharsets.UTF_8
        )).isEqualTo(
            "{\"schemaVersion\":\"document-blocks-v1\","
                + "\"parser\":{\"name\":\"apache-poi-xwpf\","
                + "\"version\":\"spring-docx-blocks-v2\"},"
                + "\"document\":{\"documentVersionId\":\"3\","
                + "\"sourceChecksumSha256\":\"" + "a".repeat(64)
                + "\",\"languageHint\":\"ko\"},"
                + "\"summary\":{\"blockCount\":2,"
                + "\"totalCharacters\":6,\"paragraphCount\":1,"
                + "\"headingCount\":0,\"listItemCount\":0,"
                + "\"tableCellCount\":1},\"warnings\":[],"
                + "\"blocks\":[{\"blockId\":\"b-000001\","
                + "\"sequence\":1,\"type\":\"PARAGRAPH\","
                + "\"text\":\"개요\",\"headingLevel\":null,"
                + "\"table\":null,\"location\":"
                + "{\"path\":\"body/paragraph[1]\"}},"
                + "{\"blockId\":\"b-000002\",\"sequence\":2,"
                + "\"type\":\"TABLE_CELL\",\"text\":\"시장규모\","
                + "\"headingLevel\":null,\"table\":{\"tableIndex\":1,"
                + "\"rowIndex\":1,\"columnIndex\":1},\"location\":"
                + "{\"path\":\"body/table[1]/row[1]/cell[1]\"}}]}"
        );
    }

    private DocumentJobContext context() {
        return new DocumentJobContext(
            1L,
            1L,
            2L,
            3L,
            StorageType.S3_COMPATIBLE,
            "source.docx",
            "plan.docx",
            "application/vnd.openxmlformats-officedocument"
                + ".wordprocessingml.document",
            10,
            "a".repeat(64),
            FileStatus.AVAILABLE,
            false
        );
    }

    private ParsedDocument parsed(Instant parsedAt) {
        return ParsedDocument.fromBlocks(
            "plan.docx",
            ParsedDocumentType.DOCX,
            "apache-poi-xwpf",
            "spring-docx-blocks-v2",
            parsedAt,
            Map.of(),
            List.of(
                new ParsedDocumentBlock(
                    ParsedBlockType.PARAGRAPH,
                    1,
                    "개요",
                    "body/paragraph[1]",
                    null,
                    null,
                    null,
                    null
                ),
                new ParsedDocumentBlock(
                    ParsedBlockType.TABLE_CELL,
                    2,
                    "시장규모",
                    "body/table[1]/row[1]/cell[1]",
                    1,
                    1,
                    1,
                    null
                )
            ),
            List.of()
        );
    }
}
