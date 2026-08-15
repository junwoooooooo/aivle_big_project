package com.aivle.backend.pipeline.conceptportfolio.application;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@Component
public class ConceptPortfolioJsonHasher {
    private final ObjectMapper mapper;

    public ConceptPortfolioJsonHasher(ObjectMapper mapper) { this.mapper = mapper; }

    public String hash(JsonNode value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(mapper.writeValueAsBytes(canonical(value)));
            return "sha256:" + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    /** Python production_compatible_snapshot_hash와 동일한 교차언어 정본 hash. */
    public String productionCompatibleHash(JsonNode value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(canonicalText(value).getBytes(StandardCharsets.UTF_8));
            return "sha256:" + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private JsonNode canonical(JsonNode value) {
        if (value.isObject()) {
            ObjectNode result = mapper.createObjectNode();
            TreeMap<String, JsonNode> sorted = new TreeMap<>();
            for (String name : value.propertyNames()) sorted.put(name, value.get(name));
            sorted.forEach((name, child) -> result.set(name, canonical(child)));
            return result;
        }
        if (value.isArray()) {
            ArrayNode result = mapper.createArrayNode();
            value.forEach(child -> result.add(canonical(child)));
            return result;
        }
        return value.deepCopy();
    }

    private String canonicalText(JsonNode value) {
        if (value.isObject()) {
            Map<String, JsonNode> properties = new TreeMap<>(ConceptPortfolioJsonHasher::compareCodePoints);
            for (String name : value.propertyNames()) {
                String normalized = Normalizer.normalize(name, Normalizer.Form.NFC);
                if (properties.putIfAbsent(normalized, value.get(name)) != null) {
                    throw new IllegalArgumentException("normalized JSON object key collision");
                }
            }
            StringBuilder result = new StringBuilder("{");
            int index = 0;
            for (Map.Entry<String, JsonNode> property : properties.entrySet()) {
                if (index++ > 0) result.append(',');
                result.append(quote(property.getKey())).append(':').append(canonicalText(property.getValue()));
            }
            return result.append('}').toString();
        }
        if (value.isArray()) {
            StringBuilder result = new StringBuilder("[");
            for (int index = 0; index < value.size(); index++) {
                if (index > 0) result.append(',');
                result.append(canonicalText(value.get(index)));
            }
            return result.append(']').toString();
        }
        if (value.isTextual()) return quote(value.asText());
        if (value.isNumber()) {
            if (value.isFloatingPointNumber() && !Double.isFinite(value.doubleValue())) {
                throw new IllegalArgumentException("non-finite JSON number");
            }
            BigDecimal decimal = value.decimalValue();
            return decimal.signum() == 0 ? "0" : decimal.stripTrailingZeros().toPlainString();
        }
        if (value.isBoolean() || value.isNull()) return value.toString();
        throw new IllegalArgumentException("unsupported canonical JSON value");
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
