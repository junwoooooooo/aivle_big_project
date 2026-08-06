package com.aivle.backend.journey.conversation;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/** Strict, versioned contract shared by persistence and the frontend boundary. */
public final class IdeaMessageContract {
    public enum Type { TEXT, QUESTION_SET, BRIEF_REVIEW, ATTACHMENT_SUMMARY, JOB_STATUS, ERROR }
    public enum QuestionType { FREE_TEXT, SINGLE_SELECT, MULTI_SELECT, UNDECIDED }
    public record Question(String id, String fieldKey, String prompt, QuestionType type,
                           List<String> options, boolean allowUndecided) { }

    public static final String SCHEMA_VERSION = "1.0";
    private static final Set<String> ENVELOPE_FIELDS = Set.of("schemaVersion", "messageType", "payload");
    private static final Set<String> QUESTION_FIELDS = Set.of("id", "fieldKey", "prompt", "type", "options", "allowUndecided");
    private IdeaMessageContract() { }

    public static Envelope assistant(ObjectMapper mapper, Type type, String text,
            List<Question> questions, List<String> contradictions, String readiness) {
        ObjectNode payload = mapper.createObjectNode();
        payload.put("text", text == null ? "" : text);
        if (type == Type.QUESTION_SET) payload.set("questions", mapper.valueToTree(questions == null ? List.of() : questions));
        if (type == Type.QUESTION_SET || type == Type.BRIEF_REVIEW) {
            payload.set("contradictions", mapper.valueToTree(contradictions == null ? List.of() : contradictions));
            payload.put("readiness", readiness == null ? "NEEDS_INPUT" : readiness);
        }
        Envelope envelope = new Envelope(SCHEMA_VERSION, type, payload);
        validate(mapper, mapper.valueToTree(envelope));
        return envelope;
    }

    public static String serializePayload(ObjectMapper mapper, Envelope envelope) {
        validate(mapper, mapper.valueToTree(envelope));
        return mapper.writeValueAsString(envelope.payload());
    }

    public static View view(ObjectMapper mapper, IdeaMessage message) {
        try {
            if (message.getRole() == IdeaMessage.Role.USER) {
                return new View(message.getId(), message.getSequenceNumber(), "USER", Type.TEXT,
                    message.getContent(), List.of(), List.of(), null, null, utc(message.getCreatedAt()));
            }
            ObjectNode root = mapper.createObjectNode();
            root.put("schemaVersion", message.getSchemaVersion());
            root.put("messageType", message.getMessageType().name());
            root.set("payload", mapper.readTree(message.getPayloadJson()));
            Envelope envelope = validate(mapper, root);
            JsonNode payload = envelope.payload();
            List<Question> questions = mapper.convertValue(payload.path("questions"),
                mapper.getTypeFactory().constructCollectionType(List.class, Question.class));
            List<String> contradictions = mapper.convertValue(payload.path("contradictions"),
                mapper.getTypeFactory().constructCollectionType(List.class, String.class));
            return new View(message.getId(), message.getSequenceNumber(), "ASSISTANT", envelope.messageType(),
                payload.path("text").asText(""), questions, contradictions,
                payload.hasNonNull("readiness") ? payload.get("readiness").asText() : null,
                envelope, utc(message.getCreatedAt()));
        } catch (RuntimeException invalid) {
            throw new InvalidEnvelopeException();
        }
    }

    public static Envelope validate(ObjectMapper mapper, JsonNode root) {
        if (root == null || !root.isObject() || !Set.copyOf(root.propertyNames()).equals(ENVELOPE_FIELDS)
                || !root.path("schemaVersion").isTextual() || !SCHEMA_VERSION.equals(root.path("schemaVersion").asText())
                || !root.path("messageType").isTextual() || !root.path("payload").isObject()) throw new InvalidEnvelopeException();
        final Type type;
        try { type = Type.valueOf(root.path("messageType").asText()); }
        catch (IllegalArgumentException invalid) { throw new InvalidEnvelopeException(); }
        JsonNode payload = root.get("payload");
        Set<String> expected = switch (type) {
            case TEXT -> Set.of("text");
            case QUESTION_SET -> Set.of("text", "questions", "contradictions", "readiness");
            case BRIEF_REVIEW -> Set.of("text", "contradictions", "readiness");
            case ATTACHMENT_SUMMARY -> Set.of("text", "attachmentId");
            case JOB_STATUS -> Set.of("messageKey", "messageParams");
            case ERROR -> Set.of("messageKey");
        };
        if (!Set.copyOf(payload.propertyNames()).equals(expected)) throw new InvalidEnvelopeException();
        if (payload.has("text") && !payload.get("text").isTextual()) throw new InvalidEnvelopeException();
        if (type == Type.QUESTION_SET) {
            if (!payload.get("questions").isArray() || !payload.get("contradictions").isArray()
                    || !payload.get("readiness").isTextual()) throw new InvalidEnvelopeException();
            for (JsonNode question : payload.get("questions")) {
                if (!question.isObject() || !Set.copyOf(question.propertyNames()).equals(QUESTION_FIELDS)) throw new InvalidEnvelopeException();
                mapper.convertValue(question, Question.class);
            }
        }
        if (type == Type.BRIEF_REVIEW && (!payload.get("contradictions").isArray() || !payload.get("readiness").isTextual())) throw new InvalidEnvelopeException();
        if (type == Type.ATTACHMENT_SUMMARY && !payload.get("attachmentId").canConvertToLong()) throw new InvalidEnvelopeException();
        if (type == Type.JOB_STATUS && (!payload.get("messageKey").isTextual() || !payload.get("messageParams").isObject())) throw new InvalidEnvelopeException();
        if (type == Type.ERROR && !payload.get("messageKey").isTextual()) throw new InvalidEnvelopeException();
        return new Envelope(SCHEMA_VERSION, type, payload.deepCopy());
    }

    public record Envelope(String schemaVersion, Type messageType, JsonNode payload) { }

    private static String utc(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC).toString();
    }
    public static final class InvalidEnvelopeException extends RuntimeException {
        public InvalidEnvelopeException() { super("구조화 메시지를 표시할 수 없습니다."); }
    }

    public record View(Long id, int sequence, String role, Type type, String text,
                       List<Question> questions, List<String> contradictions,
                       String readiness, Envelope envelope, String occurredAt) { }
}
