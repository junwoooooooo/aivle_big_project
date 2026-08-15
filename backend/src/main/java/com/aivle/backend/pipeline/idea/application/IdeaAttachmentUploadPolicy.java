package com.aivle.backend.pipeline.idea.application;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.file.validation.ValidatedUpload;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
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
import org.springframework.stereotype.Component;

@Component
public class IdeaAttachmentUploadPolicy {
    public static final long MAX_SIZE_BYTES = 20L * 1024 * 1024;
    private static final Map<String, String> MEDIA_TYPES = Map.of(
        "docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "txt", "text/plain",
        "md", "text/markdown"
    );

    public ValidatedUpload validate(String suppliedFilename, InputStream input) throws IOException {
        if (input == null) throw new BusinessException(ErrorCode.FILE_REQUIRED);
        String filename = sanitize(suppliedFilename);
        String extension = extension(filename);
        if (!MEDIA_TYPES.containsKey(extension)) throw new BusinessException(ErrorCode.FILE_TYPE_UNSUPPORTED);
        byte[] content = readBounded(input);
        boolean valid = "docx".equals(extension) ? validDocx(content) : validUtf8(content);
        if (!valid) throw new BusinessException(ErrorCode.FILE_SIGNATURE_INVALID);
        return new ValidatedUpload(content, filename, extension, MEDIA_TYPES.get(extension), sha256(content));
    }

    private String sanitize(String supplied) {
        if (supplied == null) throw new BusinessException(ErrorCode.FILE_NAME_INVALID);
        String normalized = Normalizer.normalize(supplied.strip(), Normalizer.Form.NFC).replace('\\', '/');
        String basename = normalized.substring(normalized.lastIndexOf('/') + 1)
            .replaceAll("[\\p{Cntrl}]", "_").strip();
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
            if (total > MAX_SIZE_BYTES) throw new BusinessException(ErrorCode.FILE_TOO_LARGE);
            output.write(buffer, 0, read);
        }
        if (total == 0) throw new BusinessException(ErrorCode.FILE_EMPTY);
        return output.toByteArray();
    }

    private boolean validDocx(byte[] content) {
        if (content.length < 4 || content[0] != 0x50 || content[1] != 0x4b
                || content[2] != 0x03 || content[3] != 0x04) return false;
        boolean contentTypes = false; boolean wordDocument = false;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(content))) {
            ZipEntry entry; int count = 0;
            while ((entry = zip.getNextEntry()) != null && count++ < 10000) {
                String name = entry.getName().replace('\\', '/');
                if (name.startsWith("/") || name.contains("../")) return false;
                if ("[Content_Types].xml".equals(name)) contentTypes = true;
                if ("word/document.xml".equals(name)) wordDocument = true;
            }
        } catch (IOException exception) { return false; }
        return contentTypes && wordDocument;
    }

    private boolean validUtf8(byte[] content) {
        for (byte value : content) if (value == 0) return false;
        try { StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(content)); return true; }
        catch (CharacterCodingException exception) { return false; }
    }

    private String sha256(byte[] content) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content)); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }
}
