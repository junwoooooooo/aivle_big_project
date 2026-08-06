package com.aivle.backend.document.parsing.docx;

import com.aivle.backend.document.parsing.DocumentParseErrorCode;
import com.aivle.backend.document.parsing.DocumentParseException;
import com.aivle.backend.document.parsing.DocumentParseRequest;
import com.aivle.backend.document.parsing.ParsedBlockType;
import com.aivle.backend.document.parsing.ParsedDocument;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.wp.usermodel.HeaderFooterType;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocxDocumentParserTests {
    private final DocxDocumentParser parser =
            new DocxDocumentParser(DocxParserProperties.defaults());

    @Test
    void extractsNormalParagraphs() {
        ParsedDocument parsed = parse(document(doc -> {
            doc.createParagraph().createRun().setText("첫 문단");
            doc.createParagraph().createRun().setText("둘째 문단");
        }));

        assertThat(parsed.blocks()).extracting(block -> block.text())
                .containsExactly("첫 문단", "둘째 문단");
        assertThat(parsed.blocks()).extracting(block -> block.blockId())
                .containsExactly("b-000001", "b-000002");
        assertThat(parsed.blocks()).extracting(block -> block.sequence())
                .containsExactly(1, 2);
        assertThat(parsed.parserVersion())
                .isEqualTo("spring-docx-blocks-v2");
    }

    @Test
    void preservesParagraphAndTableOrder() {
        ParsedDocument parsed = parse(document(doc -> {
            doc.createParagraph().createRun().setText("앞 문단");
            doc.createTable(1, 1).getRow(0).getCell(0).setText("표 내용");
            doc.createParagraph().createRun().setText("뒤 문단");
        }));

        assertThat(parsed.blocks()).extracting(block -> block.text())
                .containsExactly("앞 문단", "표 내용", "뒤 문단");
    }

    @Test
    void extractsTableCellsWithCoordinates() {
        ParsedDocument parsed = parse(document(doc -> {
            XWPFTable table = doc.createTable(1, 2);
            table.getRow(0).getCell(0).setText("시장");
            table.getRow(0).getCell(1).setText("100억");
        }));

        assertThat(parsed.blocks()).hasSize(2);
        assertThat(parsed.blocks().get(1).blockType()).isEqualTo(ParsedBlockType.TABLE_CELL);
        assertThat(parsed.blocks().get(1).tableIndex()).isEqualTo(1);
        assertThat(parsed.blocks().get(1).tableRow()).isEqualTo(1);
        assertThat(parsed.blocks().get(1).tableColumn()).isEqualTo(2);
    }

    @Test
    void excludesEmptyParagraphs() {
        ParsedDocument parsed = parse(document(doc -> {
            doc.createParagraph();
            doc.createParagraph().createRun().setText("내용");
            doc.createParagraph().createRun().setText("   ");
        }));

        assertThat(parsed.blocks()).hasSize(1);
    }

    @Test
    void infersHeadingFromParagraphStyle() {
        ParsedDocument parsed = parse(document(doc -> {
            XWPFParagraph heading = doc.createParagraph();
            heading.setStyle("Heading2");
            heading.createRun().setText("시장 분석");
        }));

        assertThat(parsed.blocks().get(0).blockType()).isEqualTo(ParsedBlockType.HEADING);
        assertThat(parsed.blocks().get(0).headingLevel()).isEqualTo(2);
    }

    @Test
    void preservesKoreanUtf8Text() {
        ParsedDocument parsed = parse(document(doc ->
                doc.createParagraph().createRun().setText("저당 고단백 간식 사업")));

        assertThat(parsed.plainText()).isEqualTo("저당 고단백 간식 사업");
        assertThat(parsed.totalCharacters()).isEqualTo(12);
    }

    @Test
    void rejectsEmptyFile() {
        assertParseError(new byte[0], DocumentParseErrorCode.DOCUMENT_EMPTY);
    }

    @Test
    void rejectsNonDocxSelection() {
        byte[] content = document(doc ->
                doc.createParagraph().createRun().setText("내용"));

        assertThatThrownBy(() -> parser.parse(
                new ByteArrayInputStream(content),
                DocumentParseRequest.of("plan.pdf", "application/pdf")
        )).isInstanceOfSatisfying(
                DocumentParseException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(DocumentParseErrorCode.DOCUMENT_FORMAT_UNSUPPORTED)
        );
    }

    @Test
    void rejectsCorruptedZipOrOoxml() {
        assertParseError(
                new byte[]{0x50, 0x4b, 0x03, 0x04, 0x01, 0x02},
                DocumentParseErrorCode.DOCUMENT_CORRUPTED
        );
    }

    @Test
    void rejectsMaximumFileSizeExceeded() {
        byte[] content = document(doc ->
                doc.createParagraph().createRun().setText("내용"));
        DocxDocumentParser limited = new DocxDocumentParser(properties(64, 2_000_000, 10_000, 0.01));

        assertThatThrownBy(() -> limited.parse(
                new ByteArrayInputStream(content),
                request()
        )).isInstanceOfSatisfying(
                DocumentParseException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(DocumentParseErrorCode.DOCUMENT_TOO_LARGE)
        );
    }

    @Test
    void rejectsMaximumCharacterCountExceeded() {
        byte[] content = document(doc ->
                doc.createParagraph().createRun().setText("12345"));
        DocxDocumentParser limited = new DocxDocumentParser(properties(
                20 * 1024 * 1024,
                4,
                10_000,
                0.01
        ));

        assertLimitExceeded(limited, content);
    }

    @Test
    void rejectsMaximumBlockCountExceeded() {
        byte[] content = document(doc -> {
            doc.createParagraph().createRun().setText("하나");
            doc.createParagraph().createRun().setText("둘");
        });
        DocxDocumentParser limited = new DocxDocumentParser(properties(
                20 * 1024 * 1024,
                2_000_000,
                1,
                0.01
        ));

        assertLimitExceeded(limited, content);
    }

    @Test
    void rejectsEncryptedOrLegacyOleContainer() {
        byte[] oleSignature = {
                (byte) 0xd0, (byte) 0xcf, 0x11, (byte) 0xe0,
                (byte) 0xa1, (byte) 0xb1, 0x1a, (byte) 0xe1
        };

        assertParseError(oleSignature, DocumentParseErrorCode.DOCUMENT_ENCRYPTED);
    }

    @Test
    void rejectsPathTraversalInOriginalFileName() {
        byte[] content = document(doc ->
                doc.createParagraph().createRun().setText("내용"));

        assertThatThrownBy(() -> parser.parse(
                new ByteArrayInputStream(content),
                DocumentParseRequest.of("../secret.docx", DocxDocumentParser.DOCX_CONTENT_TYPE)
        )).isInstanceOfSatisfying(
                DocumentParseException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(DocumentParseErrorCode.DOCUMENT_FILENAME_UNSAFE)
        );
    }

    @Test
    void rejectsSuspiciousCompressionRatioWithoutChangingPoiGlobals() {
        byte[] content = document(doc ->
                doc.createParagraph().createRun().setText("반복".repeat(10_000)));
        DocxDocumentParser strict = new DocxDocumentParser(properties(
                20 * 1024 * 1024,
                2_000_000,
                10_000,
                0.90
        ));

        assertLimitExceeded(strict, content);
    }

    @Test
    void rejectsTableCellCountExceeded() {
        byte[] content = document(doc -> {
            XWPFTable table = doc.createTable(1, 2);
            table.getRow(0).getCell(0).setText("하나");
            table.getRow(0).getCell(1).setText("둘");
        });
        DocxDocumentParser limited = new DocxDocumentParser(new DocxParserProperties(
                DataSize.ofMegabytes(20),
                DataSize.ofMegabytes(100),
                10_000,
                0.01,
                2_000_000,
                10_000,
                1,
                50_000
        ));

        assertLimitExceeded(limited, content);
    }

    @Test
    void rejectsArchiveEntryCountExceeded() {
        byte[] content = document(doc ->
                doc.createParagraph().createRun().setText("내용"));
        DocxDocumentParser limited = new DocxDocumentParser(new DocxParserProperties(
                DataSize.ofMegabytes(20),
                DataSize.ofMegabytes(100),
                1,
                0.01,
                2_000_000,
                10_000,
                20_000,
                50_000
        ));

        assertLimitExceeded(limited, content);
    }

    @Test
    void rejectsTotalUncompressedSizeExceeded() {
        byte[] content = document(doc ->
                doc.createParagraph().createRun().setText("내용"));
        DocxDocumentParser limited = new DocxDocumentParser(new DocxParserProperties(
                DataSize.ofMegabytes(20),
                DataSize.ofBytes(100),
                10_000,
                0.01,
                2_000_000,
                10_000,
                20_000,
                50_000
        ));

        assertLimitExceeded(limited, content);
    }

    @Test
    void rejectsVeryLongSingleParagraph() {
        byte[] content = document(doc ->
                doc.createParagraph().createRun().setText("12345"));
        DocxDocumentParser limited = new DocxDocumentParser(new DocxParserProperties(
                DataSize.ofMegabytes(20),
                DataSize.ofMegabytes(100),
                10_000,
                0.01,
                100,
                10_000,
                20_000,
                4
        ));

        assertLimitExceeded(limited, content);
    }

    @Test
    void recordsHeaderFooterAsExplicitlyUnsupported() {
        byte[] withBody = document(doc -> {
            doc.createHeader(HeaderFooterType.DEFAULT)
                    .createParagraph()
                    .createRun()
                    .setText("머리글");
            doc.createParagraph().createRun().setText("본문");
        });

        assertThat(parse(withBody).warnings()).contains("HEADER_FOOTER_NOT_EXTRACTED");
    }

    @Test
    void rejectsMacroEnabledExtensionAtSelectionBoundary() {
        byte[] content = document(doc ->
                doc.createParagraph().createRun().setText("내용"));

        assertThatThrownBy(() -> parser.parse(
                new ByteArrayInputStream(content),
                DocumentParseRequest.of("business-plan.docm", "application/vnd.ms-word.document.macroEnabled.12")
        )).isInstanceOfSatisfying(
                DocumentParseException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(DocumentParseErrorCode.DOCUMENT_FORMAT_UNSUPPORTED)
        );
    }

    @Test
    void doesNotCloseCallerOwnedInputStream() {
        byte[] content = document(doc ->
                doc.createParagraph().createRun().setText("내용"));
        CloseTrackingInputStream input = new CloseTrackingInputStream(content);

        parser.parse(input, request());

        assertThat(input.closed).isFalse();
    }

    private ParsedDocument parse(byte[] content) {
        return parser.parse(new ByteArrayInputStream(content), request());
    }

    private void assertParseError(byte[] content, DocumentParseErrorCode expected) {
        assertThatThrownBy(() -> parser.parse(new ByteArrayInputStream(content), request()))
                .isInstanceOfSatisfying(
                        DocumentParseException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(expected)
                );
    }

    private void assertLimitExceeded(DocxDocumentParser limited, byte[] content) {
        assertThatThrownBy(() -> limited.parse(new ByteArrayInputStream(content), request()))
                .isInstanceOfSatisfying(
                        DocumentParseException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(DocumentParseErrorCode.DOCUMENT_LIMIT_EXCEEDED)
                );
    }

    private DocumentParseRequest request() {
        return new DocumentParseRequest(
                "business-plan.docx",
                DocxDocumentParser.DOCX_CONTENT_TYPE,
                null,
                Map.of("fixture", "generated")
        );
    }

    private DocxParserProperties properties(
            long maxFileBytes,
            int maxCharacters,
            int maxBlocks,
            double minInflateRatio
    ) {
        return new DocxParserProperties(
                DataSize.ofBytes(maxFileBytes),
                DataSize.ofMegabytes(100),
                10_000,
                minInflateRatio,
                maxCharacters,
                maxBlocks,
                20_000,
                50_000
        );
    }

    private byte[] document(Consumer<XWPFDocument> writer) {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            writer.accept(document);
            document.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("test document creation failed", exception);
        }
    }

    private static final class CloseTrackingInputStream extends ByteArrayInputStream {
        private boolean closed;

        private CloseTrackingInputStream(byte[] content) {
            super(content);
        }

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }
    }
}
