package com.aivle.backend.pipeline.idea.domain;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class IdeaBriefFieldCatalog {
    private static final Set<IdeaQuestionType> CORE_QUESTION_TYPES = Set.of(
        IdeaQuestionType.FREE_TEXT, IdeaQuestionType.SINGLE_SELECT, IdeaQuestionType.MULTI_SELECT
    );
    private static final Set<IdeaQuestionType> NO_QUESTIONS = Set.of();

    private static final List<FieldDefinition> FIELDS = List.of(
        required("ideaOverview", "아이디어 개요"),
        required("problem", "해결하려는 문제"),
        required("targetUsers", "예상 사용자"),
        optional("targetRegion", "대상 지역"),
        optional("knownCompetitors", "알려진 경쟁자"),
        optional("revenueModel", "수익 모델"),
        optional("price", "가격"),
        optional("channels", "채널"),
        optional("differentiators", "차별점"),
        optional("budgetConstraint", "예산 제약"),
        optional("teamConstraint", "팀 제약"),
        optional("timelineConstraint", "일정 제약"),
        optional("otherConstraint", "기타 제약")
    );
    private static final Map<String, FieldDefinition> BY_KEY = FIELDS.stream()
        .collect(Collectors.toUnmodifiableMap(FieldDefinition::key, Function.identity()));

    private IdeaBriefFieldCatalog() { }

    public static List<FieldDefinition> fields() { return FIELDS; }

    public static FieldDefinition require(String key) {
        FieldDefinition definition = BY_KEY.get(key);
        if (definition == null) throw new IllegalArgumentException("unknown market seed field");
        return definition;
    }

    public static boolean contains(String key) { return BY_KEY.containsKey(key); }

    private static FieldDefinition required(String key, String label) {
        return new FieldDefinition(key, label, true, IdeaDecisionState.LOCKED, false, CORE_QUESTION_TYPES);
    }

    private static FieldDefinition optional(String key, String label) {
        return new FieldDefinition(key, label, false, IdeaDecisionState.OPEN, false, NO_QUESTIONS);
    }

    public record FieldDefinition(
        String key,
        String label,
        boolean requiredForConcept,
        IdeaDecisionState defaultDecisionState,
        boolean regulatorySensitive,
        Set<IdeaQuestionType> allowedQuestionTypes
    ) {
        public FieldDefinition { allowedQuestionTypes = Set.copyOf(allowedQuestionTypes); }
    }
}
