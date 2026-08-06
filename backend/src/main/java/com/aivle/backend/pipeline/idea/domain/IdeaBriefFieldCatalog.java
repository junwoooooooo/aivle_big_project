package com.aivle.backend.pipeline.idea.domain;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class IdeaBriefFieldCatalog {
    private static final Set<IdeaQuestionType> TEXT = Set.of(
        IdeaQuestionType.FREE_TEXT, IdeaQuestionType.SINGLE_SELECT,
        IdeaQuestionType.MULTI_SELECT, IdeaQuestionType.UNDECIDED
    );
    private static final Set<IdeaQuestionType> FACT = Set.of(
        IdeaQuestionType.FREE_TEXT, IdeaQuestionType.SINGLE_SELECT, IdeaQuestionType.UNDECIDED
    );
    private static final List<FieldDefinition> FIELDS = List.of(
        field("problem", "해결 문제", true, IdeaDecisionState.PREFERRED, false, TEXT),
        field("targetCustomers", "대상 고객", true, IdeaDecisionState.PREFERRED, false, TEXT),
        field("beneficiaries", "수혜자", true, IdeaDecisionState.PREFERRED, false, TEXT),
        field("usageContext", "사용 상황", true, IdeaDecisionState.PREFERRED, false, TEXT),
        field("expectedOutcome", "기대 결과", true, IdeaDecisionState.PREFERRED, false, TEXT),
        field("targetRegion", "대상 지역", true, IdeaDecisionState.PREFERRED, true, FACT),
        field("fixedConditions", "반드시 유지", false, IdeaDecisionState.LOCKED, false, TEXT),
        field("preferredConditions", "선호 조건", false, IdeaDecisionState.PREFERRED, false, TEXT),
        field("openDecisions", "열어 두기", false, IdeaDecisionState.OPEN, false, TEXT),
        field("assumptions", "가정", false, IdeaDecisionState.ASSUMPTION, false, TEXT),
        field("prohibitedMethods", "금지 방식", false, IdeaDecisionState.LOCKED, true, TEXT),
        field("physicalActivity", "물리 활동", true, IdeaDecisionState.PREFERRED, true, FACT),
        field("personalData", "개인정보", true, IdeaDecisionState.PREFERRED, true, FACT),
        field("payment", "결제", true, IdeaDecisionState.PREFERRED, true, FACT),
        field("requiredPartners", "필요 파트너·자격", true, IdeaDecisionState.PREFERRED, true, TEXT)
    );
    private static final Map<String, FieldDefinition> BY_KEY = FIELDS.stream()
        .collect(Collectors.toUnmodifiableMap(FieldDefinition::key, Function.identity()));

    private IdeaBriefFieldCatalog() { }

    public static List<FieldDefinition> fields() { return FIELDS; }

    public static FieldDefinition require(String key) {
        FieldDefinition definition = BY_KEY.get(key);
        if (definition == null) throw new IllegalArgumentException("unknown idea brief field");
        return definition;
    }

    public static boolean contains(String key) { return BY_KEY.containsKey(key); }

    private static FieldDefinition field(String key, String label, boolean required,
            IdeaDecisionState defaultState, boolean regulatorySensitive,
            Set<IdeaQuestionType> questionTypes) {
        return new FieldDefinition(key, label, required, defaultState, regulatorySensitive, questionTypes);
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
