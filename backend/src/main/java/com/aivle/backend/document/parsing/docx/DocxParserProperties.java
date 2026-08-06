package com.aivle.backend.document.parsing.docx;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

@ConfigurationProperties(prefix = "app.document.parser.docx")
public record DocxParserProperties(
        DataSize maxFileSize,
        DataSize maxUncompressedSize,
        int maxArchiveEntries,
        double minInflateRatio,
        int maxCharacters,
        int maxBlocks,
        int maxTableCells,
        int maxParagraphCharacters
) {
    private static final long MEBIBYTE = 1024L * 1024L;

    public DocxParserProperties {
        if (maxFileSize == null
                || maxUncompressedSize == null
                || maxArchiveEntries <= 0
                || minInflateRatio <= 0
                || minInflateRatio > 1
                || maxCharacters <= 0
                || maxBlocks <= 0
                || maxTableCells <= 0
                || maxParagraphCharacters <= 0) {
            throw new IllegalArgumentException("DOCX parser limits must be positive");
        }
    }

    public static DocxParserProperties defaults() {
        return new DocxParserProperties(
                DataSize.ofBytes(20L * MEBIBYTE),
                DataSize.ofBytes(100L * MEBIBYTE),
                10_000,
                0.01,
                2_000_000,
                10_000,
                20_000,
                50_000
        );
    }
}
