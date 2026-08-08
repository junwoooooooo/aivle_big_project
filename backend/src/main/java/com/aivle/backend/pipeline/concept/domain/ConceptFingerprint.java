package com.aivle.backend.pipeline.concept.domain;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import tools.jackson.databind.JsonNode;

public final class ConceptFingerprint {
    private static final List<String> FIELDS = List.of(
        "targetUsers", "problemScenario", "coreValue", "solutionMechanism", "revenueModel",
        "channels", "platformRole", "operatingModel", "partnerModel", "transactionFlow",
        "providerRole", "sellerRole", "intermediaryRole"
    );

    private ConceptFingerprint() {}

    public static Value from(JsonNode candidate) {
        List<String> values = FIELDS.stream().map(field -> normalize(stringValue(candidate.path(field)))).toList();
        return new Value(ConceptCanonicalizer.hash(values.toArray(String[]::new)),
            ConceptCanonicalizer.hash(values.get(0), values.get(1), values.get(2), values.get(3), values.get(6)),
            values);
    }

    public static boolean duplicates(JsonNode left, JsonNode right) {
        return classify(left, right) == Classification.DUPLICATE;
    }

    public static Classification classify(JsonNode left, JsonNode right) {
        Value first = from(left);
        Value second = from(right);
        if (first.canonicalHash().equals(second.canonicalHash()) || first.majorFieldHash().equals(second.majorFieldHash())) {
            return Classification.DUPLICATE;
        }
        int materiallySimilarFields = 0;
        double total = 0;
        for (int index = 0; index < first.values().size(); index++) {
            double score = similarity(first.values().get(index), second.values().get(index));
            total += score;
            if (score >= 0.72) materiallySimilarFields++;
        }
        double aggregate = similarity(String.join(" ", first.values()), String.join(" ", second.values()));
        double average = total / first.values().size();
        if (aggregate >= 0.76 || (materiallySimilarFields >= 8 && average >= 0.64)) {
            return Classification.DUPLICATE;
        }
        if (aggregate >= 0.28 || materiallySimilarFields >= 3 || average >= 0.30) {
            return Classification.AMBIGUOUS;
        }
        return Classification.DISTINCT;
    }

    public static java.util.Map<String, Object> businessSummary(JsonNode candidate) {
        java.util.LinkedHashMap<String, Object> result = new java.util.LinkedHashMap<>();
        for (String field : FIELDS) {
            JsonNode value = candidate.path(field);
            if (value.isArray()) {
                result.put(field, java.util.stream.StreamSupport.stream(value.spliterator(), false)
                    .map(JsonNode::asText).toList());
            } else result.put(field, value.asText(""));
        }
        return java.util.Map.copyOf(result);
    }

    private static String stringValue(JsonNode value) {
        if (!value.isArray()) return value.asText("");
        return java.util.stream.StreamSupport.stream(value.spliterator(), false)
            .map(JsonNode::asText).collect(java.util.stream.Collectors.joining(" "));
    }

    static double similarity(String left, String right) {
        if (left.equals(right)) return 1.0;
        Set<String> first = ngrams(left.replace(" ", ""), 3);
        Set<String> second = ngrams(right.replace(" ", ""), 3);
        if (first.isEmpty() || second.isEmpty()) return 0;
        Set<String> intersection = new HashSet<>(first);
        intersection.retainAll(second);
        Set<String> union = new HashSet<>(first);
        union.addAll(second);
        return (double) intersection.size() / union.size();
    }

    private static Set<String> ngrams(String value, int size) {
        Set<String> result = new HashSet<>();
        if (value.length() < size) {
            if (!value.isBlank()) result.add(value);
            return result;
        }
        for (int index = 0; index <= value.length() - size; index++) {
            result.add(value.substring(index, index + size));
        }
        return result;
    }

    static String normalize(String value) {
        if (value == null) return "";
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC)
            .toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").trim().replaceAll("\\s+", " ");
        List<String> meaningful = new ArrayList<>();
        for (String token : normalized.split(" ")) {
            if (!Set.of("서비스", "플랫폼", "솔루션", "시스템", "기반", "제공").contains(token)) meaningful.add(token);
        }
        return String.join(" ", meaningful);
    }

    public record Value(String canonicalHash, String majorFieldHash, List<String> values) {}
    public enum Classification { DUPLICATE, AMBIGUOUS, DISTINCT }
}
