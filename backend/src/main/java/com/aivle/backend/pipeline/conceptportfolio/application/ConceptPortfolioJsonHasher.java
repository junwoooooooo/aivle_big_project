package com.aivle.backend.pipeline.conceptportfolio.application;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
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
}
