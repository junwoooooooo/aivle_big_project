package com.aivle.backend.pipeline.concept.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.HexFormat;
import java.util.Locale;

public final class ConceptCanonicalizer {
    private ConceptCanonicalizer() {}

    public static String hash(String... values) {
        StringBuilder canonical = new StringBuilder();
        for (String value : values) canonical.append(normalize(value)).append('\u001f');
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            return "sha256:" + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    public static boolean duplicates(Concept left, Concept right) {
        return left.getCanonicalHash().equals(right.getCanonicalHash())
            || left.getMajorFieldHash().equals(right.getMajorFieldHash());
    }

    static String requireHash(String value) {
        if (value == null || !value.matches("sha256:[0-9a-f]{64}")) throw new IllegalArgumentException("canonical hash is invalid");
        return value;
    }

    private static String normalize(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFKC)
            .toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", " ");
    }
}
