package com.aivle.backend.pipeline.concept.domain;

import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ConceptGenerationStrategyPolicy {
    private static final Set<String> OPTIONAL_COMMITMENTS = Set.of(
        "targetRegion", "knownCompetitors", "revenueModel", "price", "channels", "differentiators",
        "budgetConstraint", "teamConstraint", "timelineConstraint", "otherConstraint"
    );
    private static final Set<String> COMMERCIAL = Set.of("revenueModel", "price");
    private static final Set<String> OPERATIONAL = Set.of(
        "targetRegion", "budgetConstraint", "teamConstraint", "timelineConstraint", "otherConstraint"
    );
    private static final List<String> MECHANISM_MARKERS = List.of(
        "방식", "통해", "연결", "매칭", "자동", "운영", "제공", "구독", "제휴", "플랫폼", "앱", "서비스"
    );

    private ConceptGenerationStrategyPolicy() {}

    public static ConceptGenerationStrategy decide(List<Map<String, String>> fields) {
        Map<String, String> values = fields.stream().collect(java.util.stream.Collectors.toMap(
            value -> value.get("fieldKey"), value -> value.getOrDefault("value", ""), (left, right) -> left));
        Set<String> locked = fields.stream()
            .filter(value -> "LOCKED".equals(value.get("authority")))
            .map(value -> value.get("fieldKey"))
            .filter(OPTIONAL_COMMITMENTS::contains)
            .collect(java.util.stream.Collectors.toSet());

        // The three required Seed values alone always remain a valid EXPLORE input.
        if (locked.isEmpty()) return ConceptGenerationStrategy.EXPLORE;

        boolean concreteProblem = concrete(values.get("problem"), 8);
        boolean concreteUsers = concrete(values.get("targetUsers"), 4);
        String original = values.getOrDefault("ideaOverview", "") + " "
            + values.getOrDefault("conciseIdeaDefinition", "");
        boolean concreteMechanism = concrete(original, 20)
            && MECHANISM_MARKERS.stream().anyMatch(original::contains);
        boolean commercial = locked.stream().anyMatch(COMMERCIAL::contains);
        boolean channel = locked.contains("channels");
        boolean operational = locked.stream().anyMatch(OPERATIONAL::contains);

        if (concreteProblem && concreteUsers && concreteMechanism && locked.size() >= 3
                && commercial && (channel || operational)) {
            return ConceptGenerationStrategy.AS_IS;
        }
        return ConceptGenerationStrategy.REFINE;
    }

    private static boolean concrete(String value, int minimumNormalizedLength) {
        return value != null && ConceptFingerprint.normalize(value).length() >= minimumNormalizedLength;
    }
}
