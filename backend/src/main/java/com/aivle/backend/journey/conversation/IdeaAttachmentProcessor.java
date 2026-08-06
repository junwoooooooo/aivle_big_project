package com.aivle.backend.journey.conversation;

import com.aivle.backend.document.parsing.DocumentParseRequest;
import com.aivle.backend.document.parsing.DocumentParser;
import com.aivle.backend.file.storage.FileStorage;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class IdeaAttachmentProcessor {
    private static final int TXT_MAX_BYTES = 5 * 1024 * 1024;
    private final IdeaAttachmentStateService state;
    private final FileStorage storage;
    private final DocumentParser documentParser;

    public IdeaAttachmentProcessor(IdeaAttachmentStateService state, FileStorage storage,
            DocumentParser documentParser) {
        this.state = state; this.storage = storage; this.documentParser = documentParser;
    }

    public String process(Long projectId, Long conversationId, Long attachmentId, String jobId) {
        try {
            IdeaAttachmentStateService.ProcessingInput file = state.start(projectId, conversationId, attachmentId, jobId);
            String text;
            try (InputStream input = storage.open(file.storageKey())) {
                if ("txt".equals(file.extension())) {
                    byte[] content = input.readNBytes(TXT_MAX_BYTES + 1);
                    if (content.length > TXT_MAX_BYTES) throw new IllegalArgumentException("ATTACHMENT_TOO_LARGE");
                    text = new String(content, StandardCharsets.UTF_8);
                } else {
                    DocumentParseRequest request = new DocumentParseRequest(
                        file.originalFilename(), file.mimeType(), file.sizeBytes(), Map.of());
                    text = documentParser.parse(input, request).plainText();
                }
            }
            if (text.isBlank()) throw new IllegalArgumentException("ATTACHMENT_EMPTY");
            String textHash = hash(text);
            state.complete(projectId, conversationId, attachmentId, jobId, textHash);
            return textHash;
        } catch (Exception failure) {
            throw new AttachmentProcessingException(failure);
        }
    }

    public static final class AttachmentProcessingException extends RuntimeException {
        AttachmentProcessingException(Throwable cause) { super("ATTACHMENT_PARSE_FAILED", cause); }
    }

    private String hash(String text) {
        try {
            return "sha256:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception impossible) { throw new IllegalStateException(impossible); }
    }
}
