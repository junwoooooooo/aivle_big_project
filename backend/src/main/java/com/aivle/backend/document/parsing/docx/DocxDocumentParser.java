package com.aivle.backend.document.parsing.docx;

import com.aivle.backend.document.parsing.DocumentParseErrorCode;
import com.aivle.backend.document.parsing.DocumentParseException;
import com.aivle.backend.document.parsing.DocumentParseRequest;
import com.aivle.backend.document.parsing.DocumentParser;
import com.aivle.backend.document.parsing.ParsedBlockType;
import com.aivle.backend.document.parsing.ParsedDocument;
import com.aivle.backend.document.parsing.ParsedDocumentBlock;
import com.aivle.backend.document.parsing.ParsedDocumentType;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.compress.utils.SeekableInMemoryByteChannel;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class DocxDocumentParser implements DocumentParser {
    static final String DOCX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    static final String PARSER_NAME = "apache-poi-xwpf";
    static final String PARSER_VERSION = "spring-docx-blocks-v2";

    private static final byte[] ZIP_SIGNATURE = {0x50, 0x4b, 0x03, 0x04};
    private static final byte[] OLE2_SIGNATURE = {
            (byte) 0xd0, (byte) 0xcf, 0x11, (byte) 0xe0,
            (byte) 0xa1, (byte) 0xb1, 0x1a, (byte) 0xe1
    };
    private static final Pattern HEADING_LEVEL = Pattern.compile(".*?([1-9])$");
    private static final int BUFFER_SIZE = 8192;

    private final DocxParserProperties properties;

    public DocxDocumentParser(DocxParserProperties properties) {
        this.properties = properties;
    }

    @Override
    public boolean supports(DocumentParseRequest request) {
        String fileName = request.originalFileName().toLowerCase(Locale.ROOT);
        String contentType = request.declaredContentType();
        return fileName.endsWith(".docx")
                && (contentType.isBlank() || DOCX_CONTENT_TYPE.equalsIgnoreCase(contentType));
    }

    @Override
    public ParsedDocument parse(InputStream source, DocumentParseRequest request) {
        if (source == null) {
            throw failure(DocumentParseErrorCode.DOCUMENT_EMPTY, "문서 내용이 비어 있습니다.");
        }
        validateFileName(request.originalFileName());
        if (!supports(request)) {
            throw failure(
                    DocumentParseErrorCode.DOCUMENT_FORMAT_UNSUPPORTED,
                    "지원하지 않는 문서 형식입니다."
            );
        }

        byte[] content = readBounded(source);
        validateContainerSignature(content);
        ArchiveInspection archive = inspectArchive(content);
        Set<String> warnings = new LinkedHashSet<>(archive.warnings());
        List<ParsedDocumentBlock> blocks = extractBlocks(content, warnings);
        if (blocks.isEmpty()) {
            throw failure(
                    DocumentParseErrorCode.DOCUMENT_EMPTY,
                    archive.hasMedia()
                            ? "텍스트를 추출할 수 없는 이미지 기반 문서입니다."
                            : "문서에 추출 가능한 텍스트가 없습니다."
            );
        }

        Map<String, String> metadata = new HashMap<>(request.metadata());
        metadata.put("archiveEntries", Integer.toString(archive.entryCount()));
        metadata.put("uncompressedBytes", Long.toString(archive.uncompressedBytes()));
        metadata.put("headerFooterPolicy", "NOT_EXTRACTED");
        metadata.put("footnotePolicy", "NOT_EXTRACTED");
        metadata.put("textboxPolicy", "BEST_EFFORT_BODY_TEXT_ONLY");
        metadata.put("ocrPolicy", "NOT_SUPPORTED");

        return ParsedDocument.fromBlocks(
                request.originalFileName(),
                ParsedDocumentType.DOCX,
                PARSER_NAME,
                PARSER_VERSION,
                Instant.now(),
                metadata,
                blocks,
                List.copyOf(warnings)
        );
    }

    private byte[] readBounded(InputStream source) {
        long limit = properties.maxFileSize().toBytes();
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[BUFFER_SIZE];
            int read;
            while ((read = source.read(buffer)) != -1) {
                if ((long) output.size() + read > limit) {
                    throw failure(
                            DocumentParseErrorCode.DOCUMENT_TOO_LARGE,
                            "문서 크기가 허용 한도를 초과했습니다."
                    );
                }
                output.write(buffer, 0, read);
            }
            if (output.size() == 0) {
                throw failure(DocumentParseErrorCode.DOCUMENT_EMPTY, "문서 내용이 비어 있습니다.");
            }
            return output.toByteArray();
        } catch (DocumentParseException exception) {
            throw exception;
        } catch (IOException exception) {
            throw failure(
                    DocumentParseErrorCode.DOCUMENT_PARSE_FAILED,
                    "문서를 읽는 중 오류가 발생했습니다.",
                    exception
            );
        }
    }

    private void validateContainerSignature(byte[] content) {
        if (startsWith(content, OLE2_SIGNATURE)) {
            throw failure(
                    DocumentParseErrorCode.DOCUMENT_ENCRYPTED,
                    "암호화되었거나 레거시 형식인 문서는 지원하지 않습니다."
            );
        }
        if (!startsWith(content, ZIP_SIGNATURE)) {
            throw failure(
                    DocumentParseErrorCode.DOCUMENT_FORMAT_UNSUPPORTED,
                    "DOCX 파일 시그니처가 올바르지 않습니다."
            );
        }
    }

    private ArchiveInspection inspectArchive(byte[] content) {
        Set<String> warnings = new LinkedHashSet<>();
        boolean hasContentTypes = false;
        boolean hasDocumentXml = false;
        boolean hasMedia = false;
        int entryCount = 0;
        long totalUncompressed = 0;

        try (SeekableInMemoryByteChannel channel = new SeekableInMemoryByteChannel(content);
             ZipFile zipFile = ZipFile.builder().setSeekableByteChannel(channel).get()) {
            Enumeration<ZipArchiveEntry> entries = zipFile.getEntries();
            while (entries.hasMoreElements()) {
                ZipArchiveEntry entry = entries.nextElement();
                entryCount++;
                if (entryCount > properties.maxArchiveEntries()) {
                    throw limitExceeded("압축 항목 수가 허용 한도를 초과했습니다.");
                }
                validateArchiveEntryName(entry.getName());
                if (entry.getGeneralPurposeBit().usesEncryption()) {
                    throw failure(
                            DocumentParseErrorCode.DOCUMENT_ENCRYPTED,
                            "암호화된 DOCX는 지원하지 않습니다."
                    );
                }

                long compressedSize = entry.getCompressedSize();
                if (entry.getSize() < 0 || compressedSize < 0) {
                    throw failure(
                            DocumentParseErrorCode.DOCUMENT_CORRUPTED,
                            "DOCX 압축 항목 정보를 확인할 수 없습니다."
                    );
                }
                long actualSize = readUncompressedSize(
                        zipFile,
                        entry,
                        properties.maxUncompressedSize().toBytes() - totalUncompressed
                );
                totalUncompressed = Math.addExact(totalUncompressed, actualSize);
                if (actualSize != entry.getSize()) {
                    throw failure(
                            DocumentParseErrorCode.DOCUMENT_CORRUPTED,
                            "DOCX 압축 항목 크기가 일치하지 않습니다."
                    );
                }
                if (actualSize > 0) {
                    double ratio = (double) compressedSize / (double) actualSize;
                    if (ratio < properties.minInflateRatio()) {
                        throw limitExceeded("비정상적인 압축 비율이 감지되었습니다.");
                    }
                }

                String normalizedName = entry.getName().toLowerCase(Locale.ROOT);
                hasContentTypes |= "[content_types].xml".equals(normalizedName);
                hasDocumentXml |= "word/document.xml".equals(normalizedName);
                hasMedia |= normalizedName.startsWith("word/media/");
                if ("word/document.xml".equals(normalizedName)
                    && containsToken(
                        zipFile,
                        entry,
                        "txbxContent"
                    )) {
                    warnings.add("TEXTBOXES_NOT_EXTRACTED");
                }
                if (normalizedName.endsWith("vbaproject.bin")) {
                    throw failure(
                            DocumentParseErrorCode.DOCUMENT_FORMAT_UNSUPPORTED,
                            "매크로 포함 문서는 지원하지 않습니다."
                    );
                }
                if (normalizedName.startsWith("word/embeddings/")
                        || normalizedName.contains("oleobject")) {
                    warnings.add("EMBEDDED_OBJECTS_NOT_EXTRACTED");
                }
                if (normalizedName.equals("word/footnotes.xml")
                        || normalizedName.equals("word/endnotes.xml")) {
                    warnings.add("FOOTNOTES_ENDNOTES_NOT_EXTRACTED");
                }
                if (normalizedName.startsWith("word/externallinks/")) {
                    warnings.add("EXTERNAL_LINKS_NOT_FOLLOWED");
                }
                if (normalizedName.startsWith("word/media/")) {
                    warnings.add("IMAGES_NOT_EXTRACTED");
                }
            }
        } catch (DocumentParseException exception) {
            throw exception;
        } catch (ArithmeticException exception) {
            throw limitExceeded("압축 해제 크기가 허용 한도를 초과했습니다.");
        } catch (IOException | RuntimeException exception) {
            throw failure(
                    DocumentParseErrorCode.DOCUMENT_CORRUPTED,
                    "손상되었거나 올바르지 않은 DOCX입니다.",
                    exception
            );
        }

        if (!hasContentTypes || !hasDocumentXml) {
            throw failure(
                    DocumentParseErrorCode.DOCUMENT_CORRUPTED,
                    "필수 DOCX 구조를 찾을 수 없습니다."
            );
        }
        return new ArchiveInspection(entryCount, totalUncompressed, hasMedia, List.copyOf(warnings));
    }

    private long readUncompressedSize(
            ZipFile zipFile,
            ZipArchiveEntry entry,
            long remainingBudget
    ) throws IOException {
        if (entry.isDirectory()) {
            return 0;
        }
        if (remainingBudget < 0) {
            throw limitExceeded("압축 해제 크기가 허용 한도를 초과했습니다.");
        }
        long size = 0;
        byte[] buffer = new byte[BUFFER_SIZE];
        try (InputStream entryStream = zipFile.getInputStream(entry)) {
            int read;
            while ((read = entryStream.read(buffer)) != -1) {
                size = Math.addExact(size, read);
                if (size > remainingBudget) {
                    throw limitExceeded("압축 해제 크기가 허용 한도를 초과했습니다.");
                }
            }
        }
        return size;
    }

    private boolean containsToken(
        ZipFile zipFile,
        ZipArchiveEntry entry,
        String token
    ) throws IOException {
        try (InputStream input = zipFile.getInputStream(entry)) {
            return new String(
                input.readAllBytes(),
                StandardCharsets.UTF_8
            ).contains(token);
        }
    }

    private List<ParsedDocumentBlock> extractBlocks(byte[] content, Set<String> warnings) {
        List<ParsedDocumentBlock> blocks = new ArrayList<>();
        ExtractionBudget budget = new ExtractionBudget();
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(content))) {
            if (!document.getHeaderList().isEmpty() || !document.getFooterList().isEmpty()) {
                warnings.add("HEADER_FOOTER_NOT_EXTRACTED");
            }
            int paragraphIndex = 0;
            int tableIndex = 0;
            for (IBodyElement element : document.getBodyElements()) {
                if (element instanceof XWPFParagraph paragraph) {
                    paragraphIndex++;
                    addParagraph(blocks, paragraph, paragraphIndex, budget);
                } else if (element instanceof XWPFTable table) {
                    tableIndex++;
                    addTable(blocks, table, tableIndex, budget);
                }
            }
            return blocks;
        } catch (DocumentParseException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw failure(
                    DocumentParseErrorCode.DOCUMENT_CORRUPTED,
                    "DOCX 본문을 읽을 수 없습니다.",
                    exception
            );
        }
    }

    private void addParagraph(
            List<ParsedDocumentBlock> blocks,
            XWPFParagraph paragraph,
            int paragraphIndex,
            ExtractionBudget budget
    ) {
        String text = normalizedText(paragraph.getText());
        if (text.isBlank()) {
            return;
        }
        if (text.length() > properties.maxParagraphCharacters()) {
            throw limitExceeded("단일 문단의 문자 수가 허용 한도를 초과했습니다.");
        }
        Integer headingLevel = headingLevel(paragraph);
        ParsedBlockType type;
        if (headingLevel != null) {
            type = ParsedBlockType.HEADING;
        } else if (paragraph.getNumID() != null) {
            type = ParsedBlockType.LIST_ITEM;
        } else {
            type = ParsedBlockType.PARAGRAPH;
        }
        addBlock(
                blocks,
                new ParsedDocumentBlock(
                        type,
                        blocks.size() + 1,
                        text,
                        "body/paragraph[" + paragraphIndex + "]",
                        null,
                        null,
                        null,
                        headingLevel
                ),
                budget
        );
    }

    private void addTable(
            List<ParsedDocumentBlock> blocks,
            XWPFTable table,
            int tableIndex,
            ExtractionBudget budget
    ) {
        int rowIndex = 0;
        for (XWPFTableRow row : table.getRows()) {
            rowIndex++;
            int columnIndex = 0;
            for (XWPFTableCell cell : row.getTableCells()) {
                columnIndex++;
                budget.tableCells++;
                if (budget.tableCells > properties.maxTableCells()) {
                    throw limitExceeded("표 셀 수가 허용 한도를 초과했습니다.");
                }
                String text = normalizedText(cell.getText());
                if (text.isBlank()) {
                    continue;
                }
                if (text.length() > properties.maxParagraphCharacters()) {
                    throw limitExceeded("표 셀의 문자 수가 허용 한도를 초과했습니다.");
                }
                addBlock(
                        blocks,
                        new ParsedDocumentBlock(
                                ParsedBlockType.TABLE_CELL,
                                blocks.size() + 1,
                                text,
                                "body/table[" + tableIndex + "]/row[" + rowIndex
                                        + "]/cell[" + columnIndex + "]",
                                tableIndex,
                                rowIndex,
                                columnIndex,
                                null
                        ),
                        budget
                );
            }
        }
    }

    private void addBlock(
            List<ParsedDocumentBlock> blocks,
            ParsedDocumentBlock block,
            ExtractionBudget budget
    ) {
        if (blocks.size() + 1 > properties.maxBlocks()) {
            throw limitExceeded("추출 블록 수가 허용 한도를 초과했습니다.");
        }
        int separatorLength = blocks.isEmpty() ? 0 : 1;
        budget.characters = Math.addExact(
                budget.characters,
                block.text().length() + separatorLength
        );
        if (budget.characters > properties.maxCharacters()) {
            throw limitExceeded("추출 문자 수가 허용 한도를 초과했습니다.");
        }
        blocks.add(block);
    }

    private Integer headingLevel(XWPFParagraph paragraph) {
        String style = paragraph.getStyle();
        if (style == null) {
            return null;
        }
        String normalized = style.toLowerCase(Locale.ROOT);
        if (!normalized.contains("heading")
                && !normalized.contains("title")
                && !normalized.contains("제목")) {
            return null;
        }
        Matcher matcher = HEADING_LEVEL.matcher(normalized);
        return matcher.matches() ? Integer.parseInt(matcher.group(1)) : 1;
    }

    private void validateFileName(String fileName) {
        if (fileName == null
                || fileName.isBlank()
                || fileName.length() > 255
                || fileName.contains("/")
                || fileName.contains("\\")
                || fileName.chars().anyMatch(character -> Character.isISOControl(character))) {
            throw failure(
                    DocumentParseErrorCode.DOCUMENT_FILENAME_UNSAFE,
                    "안전하지 않은 원본 파일명입니다."
            );
        }
    }

    private void validateArchiveEntryName(String entryName) {
        if (entryName == null
                || entryName.isBlank()
                || entryName.startsWith("/")
                || entryName.startsWith("\\")
                || entryName.contains("\\")
                || entryName.contains("../")
                || entryName.contains("/..")) {
            throw failure(
                    DocumentParseErrorCode.DOCUMENT_CORRUPTED,
                    "DOCX 내부 경로가 올바르지 않습니다."
            );
        }
    }

    private boolean startsWith(byte[] value, byte[] signature) {
        if (value.length < signature.length) {
            return false;
        }
        for (int index = 0; index < signature.length; index++) {
            if (value[index] != signature[index]) {
                return false;
            }
        }
        return true;
    }

    private String normalizedText(String value) {
        return value == null
            ? ""
            : value.replace("\r\n", "\n")
                .replace('\r', '\n')
                .strip();
    }

    private DocumentParseException limitExceeded(String message) {
        return failure(DocumentParseErrorCode.DOCUMENT_LIMIT_EXCEEDED, message);
    }

    private DocumentParseException failure(DocumentParseErrorCode code, String message) {
        return new DocumentParseException(code, message);
    }

    private DocumentParseException failure(
            DocumentParseErrorCode code,
            String message,
            Throwable cause
    ) {
        return new DocumentParseException(code, message, cause);
    }

    private record ArchiveInspection(
            int entryCount,
            long uncompressedBytes,
            boolean hasMedia,
            List<String> warnings
    ) {
    }

    private static final class ExtractionBudget {
        private int characters;
        private int tableCells;
    }
}
