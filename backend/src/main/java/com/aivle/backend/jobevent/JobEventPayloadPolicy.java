package com.aivle.backend.jobevent;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class JobEventPayloadPolicy {
    private static final int MAX_JSON_LENGTH = 2_048;
    private static final int MAX_TEXT_LENGTH = 256;
    private static final int MAX_CONTAINER_SIZE = 20;
    private static final int MAX_DEPTH = 3;
    private static final Pattern MESSAGE_KEY = Pattern.compile("[a-z0-9][a-z0-9._-]{0,119}");
    private static final Pattern TECHNICAL_CODE = Pattern.compile("[A-Z0-9][A-Z0-9._-]{0,79}");
    private static final Set<String> SENSITIVE_KEY_PARTS = Set.of(
        "authorization", "apikey", "accesstoken", "refreshtoken", "internaltoken",
        "prompt", "rawmessage", "rawbody", "requestbody", "providerbody",
        "userinput", "fullusertext", "fulluserinput", "originaltext", "usermessage"
    );

    private final ObjectMapper mapper;

    public JobEventPayloadPolicy(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public String serialize(String messageKey, Map<String, ?> params, String technicalCode) {
        if (messageKey == null || !MESSAGE_KEY.matcher(messageKey).matches()) {
            throw new IllegalArgumentException("job event message key is invalid");
        }
        if (technicalCode != null && !TECHNICAL_CODE.matcher(technicalCode).matches()) {
            throw new IllegalArgumentException("job event technical code is invalid");
        }
        JsonNode node = mapper.valueToTree(params == null ? Map.of() : params);
        validate(node, null, 0);
        String json = mapper.writeValueAsString(node);
        if (json.length() > MAX_JSON_LENGTH) {
            throw new IllegalArgumentException("job event message params are too large");
        }
        return json;
    }

    private void validate(JsonNode node, String key, int depth) {
        if (depth > MAX_DEPTH) throw new IllegalArgumentException("job event message params are too deeply nested");
        if (key != null && sensitive(key)) {
            throw new IllegalArgumentException("sensitive job event message param is not allowed");
        }
        if (node.isTextual()) {
            if (node.asText().length() > MAX_TEXT_LENGTH) {
                throw new IllegalArgumentException("job event message param text is too long");
            }
            return;
        }
        if (node.isObject()) {
            if (node.size() > MAX_CONTAINER_SIZE) {
                throw new IllegalArgumentException("too many job event message params");
            }
            for (String property : node.propertyNames()) {
                validate(node.get(property), property, depth + 1);
            }
            return;
        }
        if (node.isArray()) {
            if (node.size() > MAX_CONTAINER_SIZE) {
                throw new IllegalArgumentException("too many job event message param values");
            }
            for (JsonNode item : node) validate(item, key, depth + 1);
            return;
        }
        if (!node.isNumber() && !node.isBoolean() && !node.isNull()) {
            throw new IllegalArgumentException("unsupported job event message param");
        }
    }

    private boolean sensitive(String key) {
        String normalized = key.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        return SENSITIVE_KEY_PARTS.stream().anyMatch(normalized::contains);
    }
}
