package com.aivle.backend.pipeline.concept.domain;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.aivle.backend.pipeline.legal.application.LegalJurisdictionResolver;
import com.aivle.backend.pipeline.legal.application.LegalJurisdictionResolver.Jurisdiction;
import tools.jackson.databind.JsonNode;

public final class ConceptCandidateV2Validator {
    private static final Set<String> REQUIRED_TEXT = Set.of(
        "conceptName", "conceptDefinition", "introduction", "coreValue", "targetUsers",
        "industryCategory", "researchScope", "targetRegion", "revenueModel", "price", "channels",
        "differentiators", "problemScenario", "solutionMechanism", "platformRole", "operatingModel", "partnerModel",
        "providerRole", "sellerRole", "intermediaryRole"
    );
    private static final Set<String> LOCKED_DIRECT = Set.of(
        "targetRegion", "revenueModel", "price", "channels", "differentiators"
    );
    private static final Set<String> HYPOTHESES = Set.of(
        "targetRegion", "revenueModel", "price", "channels", "differentiators");
    private static final Set<String> CONSTRAINTS = Set.of(
        "budgetConstraint", "teamConstraint", "timelineConstraint", "otherConstraint"
    );
    private static final Set<String> SEMANTIC_FIELDS = Set.of(
        "conceptName", "conceptDefinition", "introduction", "coreValue", "targetUsers",
        "industryCategory", "researchScope", "targetRegion", "revenueModel", "price", "channels",
        "differentiators", "preMarketSomShareHypothesis", "preMarketSomHypothesis", "problemScenario",
        "solutionMechanism", "featureSet", "actorRoles", "platformRole", "operatingModel", "partnerModel",
        "providerRole", "sellerRole", "intermediaryRole",
        "transactionFlow", "paymentFlow", "personalDataUsage", "physicalActivities", "partnerRequirements",
        "qualificationRequirements", "advertisingClaims"
    );

    private ConceptCandidateV2Validator() {}

    public static Result validate(JsonNode candidate, ConceptGenerationStrategy strategy, int candidateIndex,
            List<Map<String, String>> fields) {
        if (!"2.0".equals(candidate.path("schemaVersion").asText())
            || !strategy.name().equals(candidate.path("generationStrategy").asText())
            || candidate.path("candidateIndex").asInt(-1) != candidateIndex
            || candidate.path("originalCandidate").asBoolean() != (strategy == ConceptGenerationStrategy.AS_IS && candidateIndex == 1)) {
            return Result.originInvalid("CANDIDATE_METADATA_INVALID");
        }
        for (String field : REQUIRED_TEXT) {
            if (candidate.path(field).asText().isBlank()) return Result.originInvalid("CANDIDATE_FIELD_MISSING");
        }
        for (String field : List.of("featureSet", "actorRoles", "transactionFlow", "paymentFlow")) {
            if (!candidate.path(field).isArray() || candidate.path(field).isEmpty()) {
                return Result.originInvalid("CANDIDATE_FIELD_MISSING");
            }
        }
        if (!validSom(candidate.path("preMarketSomShareHypothesis"), candidate.path("preMarketSomHypothesis"))) {
            return Result.originInvalid("PRE_MARKET_SOM_INVALID");
        }
        Map<String, JsonNode> semantics = semantics(candidate.path("valueSemantics"));
        if (!semantics.keySet().equals(SEMANTIC_FIELDS)) return Result.originInvalid("VALUE_SEMANTICS_INVALID");
        for (String som : List.of("preMarketSomShareHypothesis", "preMarketSomHypothesis")) {
            if (!matches(semantics.get(som), "AI_HYPOTHESIS", "OPEN", "PROPOSED")) {
                return Result.originInvalid("PRE_MARKET_SOM_SEMANTICS_INVALID");
            }
        }

        Map<String, String> seed = new HashMap<>();
        Map<String, String> authorities = new HashMap<>();
        Map<String, String> sources = new HashMap<>();
        for (Map<String, String> field : fields) {
            seed.put(field.get("fieldKey"), field.getOrDefault("value", ""));
            authorities.put(field.get("fieldKey"), field.getOrDefault("authority", "OPEN"));
            sources.put(field.get("fieldKey"), field.getOrDefault("source", "AI_DERIVED"));
        }
        for (String field : LOCKED_DIRECT) {
            String value = seed.get(field);
            if (value != null && !value.isBlank() && "LOCKED".equals(authorities.get(field))) {
                if (!same(value, candidate.path(field).asText())
                    || !matchesAnyUserSource(semantics.get(field), sources.get(field), "LOCKED", "ACCEPTED")) {
                    return Result.lockedInvalid(field);
                }
            } else if (HYPOTHESES.contains(field)
                && !matches(semantics.get(field), "AI_HYPOTHESIS", "OPEN", "PROPOSED")) {
                return Result.originInvalid("MISSING_SEED_HYPOTHESIS_SEMANTICS_INVALID");
            }
        }
        if (new LegalJurisdictionResolver().resolve(candidate.path("targetRegion").asText()) != Jurisdiction.KR) {
            return Result.originInvalid("LEGAL_JURISDICTION_UNSUPPORTED");
        }
        String compliance = candidate.path("constraintCompliance").toString();
        for (String constraint : CONSTRAINTS) {
            String value = seed.get(constraint);
            if (value != null && !value.isBlank() && "LOCKED".equals(authorities.get(constraint))
                && !ConceptFingerprint.normalize(compliance).contains(ConceptFingerprint.normalize(value))) {
                return Result.lockedInvalid(constraint);
            }
        }
        if (strategy == ConceptGenerationStrategy.AS_IS && candidateIndex == 1) {
            if (!preserves(seed.get("problem"), candidate.path("problemScenario").asText())
                || !preserves(seed.get("targetUsers"), candidate.path("targetUsers").asText())
                || !preserves(seed.get("ideaOverview"), candidate.path("conceptDefinition").asText())) {
                return Result.originInvalid("AS_IS_ORIGINAL_NOT_PRESERVED");
            }
            Map<String, String> originalSources = Map.of(
                "conceptDefinition", sources.getOrDefault("ideaOverview", "USER_INPUT"),
                "problemScenario", sources.getOrDefault("problem", "USER_INPUT"),
                "targetUsers", sources.getOrDefault("targetUsers", "USER_INPUT"));
            for (String field : List.of("conceptDefinition", "problemScenario", "targetUsers")) {
                if (!matchesAnyUserSource(semantics.get(field), originalSources.get(field),
                        "LOCKED", "ACCEPTED")) {
                    return Result.originInvalid("AS_IS_ORIGINAL_SEMANTICS_INVALID");
                }
            }
        }
        return Result.valid();
    }

    private static boolean validSom(JsonNode share, JsonNode som) {
        return share.isObject() && share.path("targetSharePercent").asDouble(0) > 0
            && share.path("horizonYears").asInt(0) > 0 && !share.path("rationale").asText().isBlank()
            && share.path("assumptions").isArray() && !share.path("assumptions").isEmpty()
            && som.isObject() && som.path("amount").isNumber() && !som.path("currency").asText().isBlank()
            && !som.path("period").asText().isBlank() && !som.path("calculationBasis").asText().isBlank()
            && som.path("assumptions").isArray() && !som.path("assumptions").isEmpty()
            && Set.of("LOW", "MEDIUM", "HIGH").contains(som.path("confidence").asText());
    }

    private static Map<String, JsonNode> semantics(JsonNode values) {
        Map<String, JsonNode> result = new HashMap<>();
        if (!values.isArray()) return result;
        for (JsonNode value : values) {
            String key = value.path("fieldKey").asText();
            if (key.isBlank() || result.put(key, value) != null) return Map.of();
        }
        return result;
    }

    private static boolean matches(JsonNode value, String source, String authority, String decision) {
        return value != null && source.equals(value.path("source").asText())
            && authority.equals(value.path("authority").asText()) && decision.equals(value.path("decision").asText());
    }

    private static boolean matchesAnyUserSource(JsonNode value, String expectedSource,
            String authority, String decision) {
        if (!("USER_INPUT".equals(expectedSource) || "USER_CONFIRMED".equals(expectedSource))) return false;
        return matches(value, expectedSource, authority, decision);
    }

    private static boolean same(String expected, String actual) {
        return ConceptFingerprint.normalize(expected).equals(ConceptFingerprint.normalize(actual));
    }

    private static boolean preserves(String expected, String actual) {
        if (expected == null || expected.isBlank()) return false;
        String source = ConceptFingerprint.normalize(expected);
        String candidate = ConceptFingerprint.normalize(actual);
        return candidate.contains(source) || ConceptFingerprint.similarity(source, candidate) >= 0.68;
    }

    public record Result(boolean accepted, ConceptAttemptError error, String safeCode) {
        static Result valid() { return new Result(true, null, null); }
        static Result originInvalid(String code) { return new Result(false, ConceptAttemptError.ORIGIN_INVALID, code); }
        static Result lockedInvalid(String field) {
            return new Result(false, ConceptAttemptError.LOCKED_CONSTRAINT_INVALID,
                "LOCKED_CONSTRAINT_INVALID:" + field);
        }
    }
}
