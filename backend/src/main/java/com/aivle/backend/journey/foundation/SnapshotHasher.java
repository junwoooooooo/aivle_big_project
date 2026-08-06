package com.aivle.backend.journey.foundation;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class SnapshotHasher {
    private final ObjectMapper mapper;

    public SnapshotHasher(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public String hash(String snapshotJson) {
        if (snapshotJson == null || snapshotJson.isBlank()) {
            throw new IllegalArgumentException("snapshot JSON is required");
        }
        JsonNode snapshot = mapper.readTree(snapshotJson);
        String canonical = canonical(snapshot);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(canonical.getBytes(StandardCharsets.UTF_8));
            return "sha256:" + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private String canonical(JsonNode node) {
        if (node.isObject()) {
            Map<String, JsonNode> properties = new TreeMap<>(SnapshotHasher::compareCodePoints);
            for (String originalName : node.propertyNames()) {
                String normalizedName = Normalizer.normalize(originalName, Normalizer.Form.NFC);
                if (properties.putIfAbsent(normalizedName, node.get(originalName)) != null) {
                    throw new IllegalArgumentException("normalized JSON object key collision");
                }
            }
            StringBuilder value = new StringBuilder("{");
            int index = 0;
            for (Map.Entry<String, JsonNode> property : properties.entrySet()) {
                if (index++ > 0) value.append(',');
                value.append(quote(property.getKey())).append(':').append(canonical(property.getValue()));
            }
            return value.append('}').toString();
        }
        if (node.isArray()) {
            StringBuilder value = new StringBuilder("[");
            for (int index = 0; index < node.size(); index++) {
                if (index > 0) value.append(',');
                value.append(canonical(node.get(index)));
            }
            return value.append(']').toString();
        }
        if (node.isTextual()) return quote(node.asText());
        if (node.isNumber()) return canonicalNumber(node.decimalValue());
        if (node.isBoolean() || node.isNull()) return node.toString();
        throw new IllegalArgumentException("unsupported JSON node in snapshot");
    }

    private String canonicalNumber(BigDecimal number) {
        BigDecimal normalized = number.stripTrailingZeros();
        return normalized.signum() == 0 ? "0" : normalized.toPlainString();
    }

    private String quote(String value) {
        return mapper.writeValueAsString(Normalizer.normalize(value, Normalizer.Form.NFC));
    }

    private static int compareCodePoints(String left, String right) {
        int leftIndex = 0;
        int rightIndex = 0;
        while (leftIndex < left.length() && rightIndex < right.length()) {
            int leftPoint = left.codePointAt(leftIndex);
            int rightPoint = right.codePointAt(rightIndex);
            if (leftPoint != rightPoint) return Integer.compare(leftPoint, rightPoint);
            leftIndex += Character.charCount(leftPoint);
            rightIndex += Character.charCount(rightPoint);
        }
        return Integer.compare(left.length() - leftIndex, right.length() - rightIndex);
    }
}
