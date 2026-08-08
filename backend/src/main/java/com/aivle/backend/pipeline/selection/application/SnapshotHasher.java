package com.aivle.backend.pipeline.selection.application;

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
    public SnapshotHasher(ObjectMapper mapper) { this.mapper = mapper; }

    public String hash(JsonNode node) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical(node).getBytes(StandardCharsets.UTF_8));
            return "sha256:" + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private String canonical(JsonNode node) {
        if (node.isObject()) {
            Map<String, JsonNode> values = new TreeMap<>();
            for (String key : node.propertyNames()) values.put(Normalizer.normalize(key, Normalizer.Form.NFC), node.get(key));
            StringBuilder result = new StringBuilder("{");
            int index = 0;
            for (var entry : values.entrySet()) {
                if (index++ > 0) result.append(',');
                result.append(quote(entry.getKey())).append(':').append(canonical(entry.getValue()));
            }
            return result.append('}').toString();
        }
        if (node.isArray()) {
            StringBuilder result = new StringBuilder("[");
            for (int index = 0; index < node.size(); index++) {
                if (index > 0) result.append(',');
                result.append(canonical(node.get(index)));
            }
            return result.append(']').toString();
        }
        if (node.isTextual()) return quote(node.asText());
        if (node.isIntegralNumber() || node.isBoolean() || node.isNull()) return node.toString();
        if (node.isFloatingPointNumber()) return node.decimalValue().stripTrailingZeros().toPlainString();
        throw new IllegalArgumentException("snapshot JSON에 지원하지 않는 값이 있습니다.");
    }

    private String quote(String value) { return mapper.writeValueAsString(Normalizer.normalize(value, Normalizer.Form.NFC)); }
}
