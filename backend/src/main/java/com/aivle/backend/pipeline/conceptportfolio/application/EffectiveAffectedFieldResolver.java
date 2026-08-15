package com.aivle.backend.pipeline.conceptportfolio.application;

import com.aivle.backend.pipeline.conceptportfolio.domain.ConceptInputRequest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;

@Component
public class EffectiveAffectedFieldResolver {
    private static final Set<String> ALLOWED = Set.of(
        "sellerRole", "providerRole", "intermediaryRole", "transactionFlow",
        "paymentFlow", "partnerRequirements", "personalDataUsage", "physicalActivities");
    private static final List<Mapping> MAPPINGS = List.of(
        mapping("personalDataUsage", "personal data", "personal information", "privacy",
            "\uac1c\uc778\uc815\ubcf4", "\uac1c\uc778 \ub370\uc774\ud130", "\uc218\uc9d1 \uc815\ubcf4"),
        mapping("paymentFlow", "payment method", "payment processing", "settlement",
            "\uacb0\uc81c \uc218\ub2e8", "\uacb0\uc81c \ucc98\ub9ac", "\uacb0\uc81c", "\uc218\ucde8", "\uc815\uc0b0"),
        mapping("transactionFlow", "transaction flow", "order flow", "contract flow",
            "order / contract", "\uac70\ub798 \ud750\ub984", "\uc8fc\ubb38\u00b7\uacc4\uc57d", "\uc8fc\ubb38 \uacc4\uc57d"),
        mapping("sellerRole", "seller role", "selling entity", "\ud310\ub9e4 \uc8fc\uccb4", "\ud310\ub9e4\uc790"),
        mapping("providerRole", "service provider", "providing entity", "\uc11c\ube44\uc2a4 \uc81c\uacf5 \uc8fc\uccb4"),
        mapping("intermediaryRole", "intermediary", "broker role", "matching entity",
            "\uc911\uac1c \uc8fc\uccb4", "\ub9e4\uce6d \uc8fc\uccb4"),
        mapping("partnerRequirements", "partner requirement", "qualification requirement",
            "required license", "required qualification", "\ud30c\ud2b8\ub108 \uc694\uac74", "\uc790\uaca9 \uc694\uac74", "\uc778\ud5c8\uac00"),
        mapping("physicalActivities", "physical activity", "delivery activity", "site visit",
            "installation activity", "\ubb3c\ub9ac \ud65c\ub3d9", "\ubc30\uc1a1 \ud65c\ub3d9", "\ud604\uc7a5 \ubc29\ubb38", "\uc124\uce58 \ud65c\ub3d9"));

    private final ObjectMapper mapper;

    public EffectiveAffectedFieldResolver(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public ArrayNode resolve(JsonNode requiredInput, JsonNode artifact) {
        LinkedHashSet<String> resolved = new LinkedHashSet<>();
        addArray(resolved, requiredInput == null ? null : requiredInput.get("affectedFields"));
        addArray(resolved, artifact == null ? null : artifact.get("affectedFields"));
        JsonNode legalDiagnostics = artifact == null ? null
            : artifact.path("latestLegalReview").path("evidenceDiagnostics");
        addArray(resolved, legalDiagnostics == null ? null : legalDiagnostics.get("affectedFields"));
        addCanonicalNames(resolved, legalDiagnostics);
        if (requiredInput != null) {
            addCanonicalNames(resolved, requiredInput.get("evidenceDiagnostics"));
            addCanonicalNames(resolved, requiredInput.get("structuredCompletionDiagnostics"));
        }
        if (resolved.isEmpty()) addMappedText(resolved, textInput(requiredInput));
        return array(resolved);
    }

    public ArrayNode resolve(ConceptInputRequest request) {
        JsonNode artifact = request.getArtifactJson() == null
            ? mapper.missingNode() : mapper.readTree(request.getArtifactJson());
        var required = mapper.createObjectNode();
        required.set("affectedFields", mapper.readTree(request.getAffectedFieldsJson()));
        required.put("question", request.getSourceQuestion());
        required.put("reason", request.getReason());
        required.put("safeSummary", request.getSafeSummary());
        required.set("unknownFacts", mapper.readTree(request.getUnknownFactsJson()));
        return resolve(required, artifact);
    }

    private void addArray(LinkedHashSet<String> resolved, JsonNode values) {
        if (values == null || !values.isArray()) return;
        values.forEach(value -> {
            if (value.isTextual() && ALLOWED.contains(value.asText())) resolved.add(value.asText());
        });
    }

    private void addCanonicalNames(LinkedHashSet<String> resolved, JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) return;
        if (node.isTextual()) {
            if (ALLOWED.contains(node.asText())) resolved.add(node.asText());
        } else if (node.isArray()) {
            node.forEach(value -> addCanonicalNames(resolved, value));
        } else if (node.isObject()) {
            for (String name : node.propertyNames()) {
                if (ALLOWED.contains(name)) resolved.add(name);
                addCanonicalNames(resolved, node.get(name));
            }
        }
    }

    private String textInput(JsonNode input) {
        if (input == null) return "";
        StringBuilder value = new StringBuilder();
        append(value, input.get("question"));
        append(value, input.get("unknownFacts"));
        append(value, input.get("reason"));
        append(value, input.get("safeSummary"));
        return value.toString().toLowerCase(Locale.ROOT);
    }

    private void append(StringBuilder target, JsonNode value) {
        if (value == null || value.isNull()) return;
        if (value.isTextual()) target.append(' ').append(value.asText());
        else if (value.isArray()) value.forEach(item -> append(target, item));
    }

    private void addMappedText(LinkedHashSet<String> resolved, String text) {
        List<Match> matches = new ArrayList<>();
        for (Mapping mapping : MAPPINGS) {
            int earliest = Integer.MAX_VALUE;
            for (String phrase : mapping.phrases()) {
                int index = text.indexOf(phrase);
                if (index >= 0) earliest = Math.min(earliest, index);
            }
            if (earliest != Integer.MAX_VALUE) matches.add(new Match(mapping.field(), earliest));
        }
        matches.stream().sorted(Comparator.comparingInt(Match::index))
            .forEach(match -> resolved.add(match.field()));
    }

    private ArrayNode array(Set<String> resolved) {
        ArrayNode value = mapper.createArrayNode();
        resolved.forEach(value::add);
        return value;
    }

    private static Mapping mapping(String field, String... phrases) {
        return new Mapping(field, List.of(phrases));
    }

    private record Mapping(String field, List<String> phrases) { }
    private record Match(String field, int index) { }
}
