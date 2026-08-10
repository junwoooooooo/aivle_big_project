package com.aivle.backend.pipeline.selection.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

public final class SelectionRequestFingerprint {
    private SelectionRequestFingerprint() {}
    public static String create(String conceptId, String sourceConceptHash, String reason) {
        String value = conceptId.strip() + "\n" + sourceConceptHash + "\n" + reason.strip();
        try {
            return "sha256:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
