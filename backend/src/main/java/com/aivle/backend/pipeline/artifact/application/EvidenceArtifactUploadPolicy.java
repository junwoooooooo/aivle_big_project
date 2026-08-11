package com.aivle.backend.pipeline.artifact.application;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.file.validation.ValidatedUpload;
import com.aivle.backend.pipeline.artifact.config.EvidenceArtifactProperties;
import java.io.*;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EvidenceArtifactUploadPolicy {
    private static final Map<String, String> MEDIA_TYPES = Map.ofEntries(
        Map.entry("pdf", "application/pdf"), Map.entry("csv", "text/csv"),
        Map.entry("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
        Map.entry("xls", "application/vnd.ms-excel"),
        Map.entry("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
        Map.entry("txt", "text/plain"), Map.entry("png", "image/png"), Map.entry("webp", "image/webp"),
        Map.entry("jpg", "image/jpeg"), Map.entry("jpeg", "image/jpeg"));
    private final EvidenceArtifactProperties properties;

    public ValidatedUpload validate(String suppliedFilename, String suppliedContentType, InputStream input) throws IOException {
        if (input == null) throw new BusinessException(ErrorCode.FILE_REQUIRED);
        String filename = sanitizeFilename(suppliedFilename);
        String extension = extension(filename);
        if (!properties.allowedExtensions().contains(extension) || !MEDIA_TYPES.containsKey(extension)) {
            throw new BusinessException(ErrorCode.FILE_TYPE_UNSUPPORTED);
        }
        byte[] content = readBounded(input);
        validateContent(extension, content);
        return new ValidatedUpload(content, filename, extension, MEDIA_TYPES.get(extension), sha256(content));
    }

    String sanitizeFilename(String supplied) {
        if (supplied == null) throw new BusinessException(ErrorCode.FILE_NAME_INVALID);
        String normalized = Normalizer.normalize(supplied.strip(), Normalizer.Form.NFC).replace('\\', '/');
        int separator = normalized.lastIndexOf('/');
        String basename = separator >= 0 ? normalized.substring(separator + 1) : normalized;
        basename = basename.replaceAll("[\\p{Cntrl}]", "_").strip();
        if (basename.isBlank() || basename.length() > 255 || ".".equals(basename) || "..".equals(basename)) {
            throw new BusinessException(ErrorCode.FILE_NAME_INVALID);
        }
        return basename;
    }

    private String extension(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot <= 0 || dot == filename.length() - 1) throw new BusinessException(ErrorCode.FILE_TYPE_UNSUPPORTED);
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private byte[] readBounded(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192]; long total = 0; int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > properties.maxSizeBytes()) throw new BusinessException(ErrorCode.FILE_TOO_LARGE);
            output.write(buffer, 0, read);
        }
        if (total == 0) throw new BusinessException(ErrorCode.FILE_EMPTY);
        return output.toByteArray();
    }

    private void validateContent(String extension, byte[] content) {
        boolean valid = switch (extension) {
            case "pdf" -> starts(content, 0x25, 0x50, 0x44, 0x46, 0x2d);
            case "png" -> starts(content, 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a);
            case "jpg", "jpeg" -> starts(content, 0xff, 0xd8, 0xff);
            case "webp" -> content.length >= 12 && starts(content, 0x52, 0x49, 0x46, 0x46)
                && content[8] == 0x57 && content[9] == 0x45 && content[10] == 0x42 && content[11] == 0x50;
            case "xls" -> starts(content, 0xd0, 0xcf, 0x11, 0xe0, 0xa1, 0xb1, 0x1a, 0xe1);
            case "docx" -> validOoxml(content, "word/");
            case "xlsx" -> validOoxml(content, "xl/");
            case "csv", "txt" -> validText(content);
            default -> false;
        };
        if (!valid) throw new BusinessException(ErrorCode.FILE_SIGNATURE_INVALID);
    }

    private boolean starts(byte[] content, int... expected) {
        if (content.length < expected.length) return false;
        for (int index = 0; index < expected.length; index++) {
            if ((content[index] & 0xff) != expected[index]) return false;
        }
        return true;
    }

    private boolean validOoxml(byte[] content, String requiredPrefix) {
        if (!starts(content, 0x50, 0x4b, 0x03, 0x04)) return false;
        boolean contentTypes = false; boolean required = false;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(content))) {
            ZipEntry entry; int count = 0;
            while ((entry = zip.getNextEntry()) != null && count++ < 10000) {
                String name = entry.getName().replace('\\', '/');
                if (name.startsWith("/") || name.contains("../")) return false;
                if ("[Content_Types].xml".equals(name)) contentTypes = true;
                if (name.startsWith(requiredPrefix)) required = true;
            }
        } catch (IOException exception) { return false; }
        return contentTypes && required;
    }

    private boolean validText(byte[] content) {
        for (byte value : content) if (value == 0) return false;
        try {
            StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(content));
            return true;
        } catch (CharacterCodingException exception) { return false; }
    }

    private String sha256(byte[] content) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content)); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-256 unavailable", exception); }
    }
}
