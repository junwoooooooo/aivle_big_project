package com.aivle.backend.pipeline.concept.domain;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import tools.jackson.databind.JsonNode;

public final class ConceptFingerprint {
    private static final List<String> FIELDS = List.of(
        "targetUsers", "problemScenario", "coreValue", "solutionMechanism", "revenueModel",
        "channels", "platformRole", "operatingModel", "partnerModel", "transactionFlow",
        "providerRole", "sellerRole", "intermediaryRole", "featureSet", "actorRoles", "price",
        "paymentFlow", "personalDataUsage", "physicalActivities", "partnerRequirements",
        "qualificationRequirements"
    );
    private static final Set<String> ARRAY_FIELDS = Set.of(
        "transactionFlow", "featureSet", "actorRoles", "paymentFlow", "personalDataUsage",
        "physicalActivities", "partnerRequirements", "qualificationRequirements"
    );
    private static final List<String> MECHANICS = List.of(
        "solutionMechanism", "revenueModel", "channels", "platformRole", "operatingModel",
        "partnerModel", "transactionFlow", "providerRole", "sellerRole", "intermediaryRole",
        "price", "paymentFlow", "personalDataUsage", "physicalActivities", "partnerRequirements",
        "qualificationRequirements"
    );
    private static final Map<VariationFocus, List<String>> FOCUS_FIELDS = Map.of(
        VariationFocus.CUSTOMER_EXPERIENCE,
            List.of("problemScenario", "solutionMechanism", "featureSet", "coreValue"),
        VariationFocus.OPERATING_MODEL_AND_PARTNERS,
            List.of("actorRoles", "operatingModel", "partnerModel", "providerRole", "sellerRole",
                "intermediaryRole", "transactionFlow"),
        VariationFocus.REVENUE_AND_PRICING,
            List.of("revenueModel", "price", "paymentFlow"),
        VariationFocus.CHANNEL_AND_SCALE,
            List.of("channels", "platformRole", "transactionFlow", "operatingModel"),
        VariationFocus.LOW_RISK_FAST_EXECUTION,
            List.of("personalDataUsage", "physicalActivities", "partnerRequirements",
                "qualificationRequirements", "operatingModel")
    );

    private ConceptFingerprint() {}

    public static Value from(JsonNode candidate) {
        List<String> values = FIELDS.stream().map(field -> normalize(stringValue(candidate.path(field)))).toList();
        return new Value(ConceptCanonicalizer.hash(values.toArray(String[]::new)),
            ConceptCanonicalizer.hash(MECHANICS.stream()
                .map(field -> normalize(stringValue(candidate.path(field)))).toArray(String[]::new)),
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

    public static Classification classify(JsonNode candidate, JsonNode existing, VariationFocus focus) {
        return evaluate(candidate, existing, focus).classification();
    }

    public static DistinctnessEvaluation evaluate(JsonNode candidate, JsonNode existing, VariationFocus focus) {
        Value first = from(candidate);
        Value second = from(existing);
        List<String> focusFields = FOCUS_FIELDS.get(focus);
        List<String> overlapping = new ArrayList<>();
        List<String> different = new ArrayList<>();
        for (String field : focusFields) {
            double fieldSimilarity = similarity(normalize(stringValue(candidate.path(field))),
                normalize(stringValue(existing.path(field))));
            if (fieldSimilarity >= 0.72) overlapping.add(field);
            if (fieldSimilarity <= 0.35) different.add(field);
        }
        double focusSimilarity = averageSimilarity(candidate, existing, focusFields);
        double mechanicsSimilarity = averageSimilarity(candidate, existing, MECHANICS);
        Classification classification;
        if (first.canonicalHash().equals(second.canonicalHash())
                || first.majorFieldHash().equals(second.majorFieldHash())) {
            classification = Classification.DUPLICATE;
        } else if (focusSimilarity >= 0.86 && mechanicsSimilarity >= 0.78 && different.size() < 2) {
            classification = Classification.DUPLICATE;
        } else if (focusSimilarity <= 0.45 || different.size() >= 2) {
            classification = Classification.DISTINCT;
        } else if (focusSimilarity >= 0.50 || mechanicsSimilarity >= 0.55) {
            classification = Classification.AMBIGUOUS;
        } else {
            classification = Classification.DISTINCT;
        }
        List<String> required = new ArrayList<>();
        for (String field : overlapping) {
            if (required.size() == 2) break;
            required.add(field);
        }
        for (String field : focusFields) {
            if (required.size() == 2) break;
            if (!required.contains(field)) required.add(field);
        }
        return new DistinctnessEvaluation(classification, focusSimilarity, mechanicsSimilarity,
            List.copyOf(overlapping), List.copyOf(different), List.copyOf(required));
    }

    private static int materiallyDifferentFields(JsonNode left, JsonNode right, List<String> fields) {
        int different = 0;
        for (String field : fields) {
            String first = normalize(stringValue(left.path(field)));
            String second = normalize(stringValue(right.path(field)));
            if ((!first.isBlank() || !second.isBlank()) && similarity(first, second) <= 0.35) different++;
        }
        return different;
    }

    private static double averageSimilarity(JsonNode left, JsonNode right, List<String> fields) {
        double total = 0;
        int populated = 0;
        for (String field : fields) {
            String first = normalize(stringValue(left.path(field)));
            String second = normalize(stringValue(right.path(field)));
            if (first.isBlank() && second.isBlank()) continue;
            total += similarity(first, second);
            populated++;
        }
        return populated == 0 ? 1.0 : total / populated;
    }

    public static java.util.Map<String, Object> businessSummary(JsonNode candidate) {
        java.util.LinkedHashMap<String, Object> result = new java.util.LinkedHashMap<>();
        for (String field : FIELDS) {
            JsonNode value = candidate.path(field);
            if (ARRAY_FIELDS.contains(field)) {
                if (value.isArray()) {
                    result.put(field, java.util.stream.StreamSupport.stream(value.spliterator(), false)
                        .map(JsonNode::asText).toList());
                } else if (value.isMissingNode() || value.isNull() || value.asText("").isBlank()) {
                    result.put(field, List.of());
                } else {
                    result.put(field, List.of(value.asText()));
                }
            } else result.put(field, value.asText(""));
        }
        return java.util.Map.copyOf(result);
    }

    public static List<String> businessFieldNames() {
        return FIELDS;
    }

    public static List<String> focusFieldNames(VariationFocus focus) {
        return FOCUS_FIELDS.get(focus);
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
    public record DistinctnessEvaluation(Classification classification, double focusSimilarity,
                                         double mechanicsSimilarity, List<String> overlappingDimensions,
                                         List<String> materiallyDifferentDimensions,
                                         List<String> requiredChangeDimensions) {}
    public enum Classification { DUPLICATE, AMBIGUOUS, DISTINCT }
}
