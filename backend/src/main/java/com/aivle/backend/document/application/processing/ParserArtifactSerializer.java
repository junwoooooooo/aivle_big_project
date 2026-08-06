package com.aivle.backend.document.application.processing;

import com.aivle.backend.document.parsing.ParsedBlockType;
import com.aivle.backend.document.parsing.ParsedDocument;
import com.aivle.backend.document.parsing.ParsedDocumentBlock;
import com.aivle.backend.job.runner.JobProcessingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class ParserArtifactSerializer {
    public static final String SCHEMA_VERSION = "document-blocks-v1";
    private static final int MAX_WARNINGS = 20;

    private final ObjectMapper objectMapper;

    public ParserArtifactPayload serialize(
        DocumentJobContext context,
        ParsedDocument parsed
    ) {
        validateBlocks(parsed.blocks());
        int totalCharacters = parsed.blocks().stream()
            .mapToInt(block -> block.text().length())
            .sum();

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("schemaVersion", SCHEMA_VERSION);
        root.put("parser", parser(parsed));
        root.put("document", document(context));
        root.put("summary", summary(parsed, totalCharacters));
        root.put("warnings", boundedWarnings(parsed.warnings()));
        root.put(
            "blocks",
            parsed.blocks().stream().map(this::block).toList()
        );

        try {
            byte[] bytes = objectMapper.writeValueAsBytes(root);
            return new ParserArtifactPayload(
                bytes,
                sha256(bytes),
                parsed.blocks().size(),
                totalCharacters,
                SCHEMA_VERSION
            );
        } catch (JacksonException exception) {
            throw JobProcessingException.nonRetryable(
                "PARSER_ARTIFACT_SERIALIZATION_FAILED",
                "파서 artifact를 직렬화할 수 없습니다.",
                exception
            );
        }
    }

    private Map<String, Object> parser(ParsedDocument parsed) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("name", parsed.parserName());
        value.put("version", parsed.parserVersion());
        return value;
    }

    private Map<String, Object> document(DocumentJobContext context) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put(
            "documentVersionId",
            context.documentVersionId().toString()
        );
        value.put(
            "sourceChecksumSha256",
            context.checksumSha256()
        );
        value.put("languageHint", "ko");
        return value;
    }

    private Map<String, Object> summary(
        ParsedDocument parsed,
        int totalCharacters
    ) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("blockCount", parsed.blocks().size());
        value.put("totalCharacters", totalCharacters);
        value.put(
            "paragraphCount",
            count(parsed, ParsedBlockType.PARAGRAPH)
        );
        value.put(
            "headingCount",
            count(parsed, ParsedBlockType.HEADING)
        );
        value.put(
            "listItemCount",
            count(parsed, ParsedBlockType.LIST_ITEM)
        );
        value.put(
            "tableCellCount",
            count(parsed, ParsedBlockType.TABLE_CELL)
        );
        return value;
    }

    private long count(
        ParsedDocument parsed,
        ParsedBlockType type
    ) {
        return parsed.blocks().stream()
            .filter(block -> block.blockType() == type)
            .count();
    }

    private Map<String, Object> block(ParsedDocumentBlock block) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("blockId", block.blockId());
        value.put("sequence", block.sequence());
        value.put("type", block.blockType().name());
        value.put("text", block.text());
        value.put("headingLevel", block.headingLevel());
        value.put("table", table(block));
        Map<String, Object> location = new LinkedHashMap<>();
        location.put("path", block.sourceLocation());
        value.put("location", location);
        return value;
    }

    private Map<String, Object> table(ParsedDocumentBlock block) {
        if (block.blockType() != ParsedBlockType.TABLE_CELL) {
            return null;
        }
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("tableIndex", block.tableIndex());
        value.put("rowIndex", block.tableRow());
        value.put("columnIndex", block.tableColumn());
        return value;
    }

    private List<String> boundedWarnings(List<String> warnings) {
        List<String> sorted = warnings.stream()
            .filter(value -> value != null && !value.isBlank())
            .distinct()
            .sorted()
            .toList();
        if (sorted.size() <= MAX_WARNINGS) {
            return sorted;
        }
        List<String> bounded = new ArrayList<>(
            sorted.subList(0, MAX_WARNINGS - 1)
        );
        bounded.add("WARNINGS_TRUNCATED");
        return List.copyOf(bounded);
    }

    private void validateBlocks(List<ParsedDocumentBlock> blocks) {
        if (blocks.isEmpty()) {
            throw JobProcessingException.nonRetryable(
                "DOCUMENT_EMPTY",
                "문서에 저장할 parser block이 없습니다.",
                null
            );
        }
        Set<String> blockIds = new HashSet<>();
        Set<String> tableCoordinates = new HashSet<>();
        for (int index = 0; index < blocks.size(); index++) {
            ParsedDocumentBlock block = blocks.get(index);
            int expectedSequence = index + 1;
            if (block.sequence() != expectedSequence
                || !blockIds.add(block.blockId())) {
                throw invalidBlocks();
            }
            if (block.blockType() == ParsedBlockType.TABLE_CELL) {
                String coordinate = block.tableIndex()
                    + ":" + block.tableRow()
                    + ":" + block.tableColumn();
                if (block.tableIndex() == null
                    || block.tableRow() == null
                    || block.tableColumn() == null
                    || !tableCoordinates.add(coordinate)) {
                    throw invalidBlocks();
                }
            }
        }
    }

    private JobProcessingException invalidBlocks() {
        return JobProcessingException.nonRetryable(
            "INVALID_DOCUMENT_BLOCKS",
            "파서 block 순서 또는 좌표가 올바르지 않습니다.",
            null
        );
    }

    private String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(value)
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                "SHA-256 is unavailable",
                exception
            );
        }
    }
}
