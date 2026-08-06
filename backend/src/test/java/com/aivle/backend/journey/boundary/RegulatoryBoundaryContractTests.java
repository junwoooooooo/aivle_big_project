package com.aivle.backend.journey.boundary;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

class RegulatoryBoundaryContractTests {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void acceptsExecutableReadyRuleWithOfficialEvidence() {
        assertThatCode(() -> RegulatoryBoundaryContract.validate(valid())).doesNotThrowAnyException();
    }

    @Test
    void rejectsUnknownRuleTypeEvidenceFreeRuleAndRawProviderField() {
        ObjectNode unknown = valid(); ((ObjectNode) unknown.withArray("rules").get(0)).put("ruleType", "PASS");
        assertThatThrownBy(() -> RegulatoryBoundaryContract.validate(unknown)).isInstanceOf(IllegalArgumentException.class);
        ObjectNode evidenceFree = valid(); ((ObjectNode) evidenceFree.withArray("rules").get(0)).putArray("evidenceIds");
        assertThatThrownBy(() -> RegulatoryBoundaryContract.validate(evidenceFree)).isInstanceOf(IllegalArgumentException.class);
        ObjectNode raw = valid(); raw.put("providerBody", "secret");
        assertThatThrownBy(() -> RegulatoryBoundaryContract.validate(raw)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsDuplicateCanonicalRuleAndSummaryCopiedAsRequirement() {
        ObjectNode duplicate = valid(); duplicate.withArray("rules").add(duplicate.withArray("rules").get(0).deepCopy());
        ((ObjectNode) duplicate.withArray("rules").get(1)).put("ruleId", "RULE-2");
        assertThatThrownBy(() -> RegulatoryBoundaryContract.validate(duplicate)).isInstanceOf(IllegalArgumentException.class);
        ObjectNode copied = valid(); ((ObjectNode) copied.withArray("rules").get(0)).put("normalizedRequirement", "필요한 정보만 수집한다.");
        assertThatThrownBy(() -> RegulatoryBoundaryContract.validate(copied)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void requiresLockedConflictShapeForBlockedAndQuestionsForNeedsInput() {
        ObjectNode blocked = valid(); blocked.put("status", "BLOCKED");
        assertThatThrownBy(() -> RegulatoryBoundaryContract.validate(blocked)).isInstanceOf(IllegalArgumentException.class);
        ObjectNode needs = valid(); needs.put("status", "NEEDS_INPUT");
        ObjectNode question = needs.withArray("questions").addObject();
        question.put("questionId", "Q-1").put("fieldKey", "targetRegion")
            .put("question", "어느 지역입니까?").put("reason", "적용 범위 확인").put("answerType", "TEXT");
        question.putArray("options");
        question.put("required", true); question.putArray("relatedRuleIds"); question.putArray("relatedEvidenceIds");
        assertThatCode(() -> RegulatoryBoundaryContract.validate(needs)).doesNotThrowAnyException();
    }

    private ObjectNode valid() {
        return (ObjectNode) mapper.readTree("""
            {"taskType":"REGULATORY_BOUNDARY_GENERATION","sourceStatus":"COMPLETE","registryVersion":"legal-registry-v1",
             "routes":[],"evidence":[{"evidenceId":"EVD-001","sourceType":"OFFICIAL_LAW","lawName":"개인정보 보호법",
             "article":"제15조","title":"개인정보의 수집","effectiveDate":"2026-01-01","officialUrl":"https://www.law.go.kr/a",
             "excerpt":"공식 발췌","plainSummary":"필요한 정보만 수집한다.","whyRelevant":"위치정보 처리",
             "sourceStatus":"COMPLETE","retrievedAt":"2026-08-05T00:00:00Z","contentHash":"sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"}],
             "rules":[{"ruleId":"RULE-1","ruleType":"REQUIRED_CONTROL","structureKey":"locationData","title":"위치정보 최소 처리",
             "description":"추천 기능의 통제","normalizedRequirement":"추천에 필요한 최소 위치정보만 목적 범위에서 처리한다.",
             "evidenceIds":["EVD-001"],"severity":"HIGH","sourceStatus":"COMPLETE","appliesWhen":{"collects":true},
             "userFacingReason":"불필요한 추적 방지","alternatives":[],"requiredQualifications":[],"requiredPartnerRole":null,
             "requiredDisclosure":"수집 목적 고지","affectedBriefFields":["regulatorySensitiveActivities"],
             "professionalReviewRecommended":false,"userActionOptions":[]}],"questions":[],"conflicts":[],"status":"READY",
             "userActionOptions":[],"sourceWarnings":[]}
            """);
    }
}
