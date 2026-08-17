package com.aivle.backend.taskrun;

import static org.assertj.core.api.Assertions.*;

import com.aivle.backend.taskrun.contract.MarketInterviewContract;
import com.aivle.backend.taskrun.integration.InternalAiExecutionClient.ExecutionFailure;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

class MarketInterviewContractTests {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test void profileBankV2ContractPasses() {
        assertThatCode(() -> MarketInterviewContract.validate(valid())).doesNotThrowAnyException();
    }

    @Test void literalIndividualPercentagesAreAllowed() {
        for (String phrase : List.of("20% 할인이라면 써볼 수 있다.", "수수료 8%는 부담스럽다.",
                "가격이 10% 내려가면 검토한다.")) {
            ObjectNode value = valid();
            ((ObjectNode) value.path("interviews").path(0).path("questions").path(0)).put("answer", phrase);
            assertThatCode(() -> MarketInterviewContract.validate(value)).doesNotThrowAnyException();
        }
    }

    @Test void populationGeneralizationsAreRejected() {
        for (String phrase : List.of("응답자의 75%가 구매한다.", "75%의 응답자가 구매한다.",
                "고객의 80%가 선호한다.", "80%의 고객이 선호한다.",
                "대부분의 고객이 구매한다.", "실제 사용자들은 만족한다.",
                "구매 확률은 70%다.", "구매 전환율은 35%다.")) {
            ObjectNode value = valid();
            ((ObjectNode) value.path("themes").path(0)).put("description", phrase);
            assertThatThrownBy(() -> MarketInterviewContract.validate(value)).isInstanceOf(ExecutionFailure.class);
        }
    }

    @Test void unsupportedSampleSizeIsRejected() {
        ObjectNode value = valid(); ((ObjectNode) value.path("targeting")).put("requestedSampleSize", 30);
        assertThatThrownBy(() -> MarketInterviewContract.validate(value)).isInstanceOf(ExecutionFailure.class);
    }

    @Test void mentionCountMustEqualUniqueRespondentIds() {
        ObjectNode value = valid(); ((ObjectNode) value.path("themes").path(0)).put("mentionCount", 19);
        assertThatThrownBy(() -> MarketInterviewContract.validate(value)).isInstanceOf(ExecutionFailure.class);
    }

    @Test void unknownThemeAssignmentFailsClosed() {
        ObjectNode value = valid(); ((ArrayNode) value.path("codingTrace").path(0).path("themeTitles")).removeAll().add("없는 주제");
        assertThatThrownBy(() -> MarketInterviewContract.validate(value)).isInstanceOf(ExecutionFailure.class);
    }

    @Test void partialRespondentFailureKeepsOnlyUsableResponses() {
        ObjectNode value = valid();
        ObjectNode targeting = (ObjectNode) value.path("targeting");
        targeting.put("usableCount", 19).put("failedCount", 1)
            .put("targetCount", 16).put("nonTargetCount", 3);
        ((ArrayNode) value.path("transcriptProvenance")).remove(19);
        ((ArrayNode) value.path("codingTrace")).remove(19);
        ObjectNode theme = (ObjectNode) value.path("themes").path(0);
        ((ArrayNode) theme.path("participantIds")).remove(19);
        theme.put("mentionCount", 19).put("targetCount", 16).put("nonTargetCount", 3);
        ((ObjectNode) value.path("comprehension")).put("accurate", 19);
        ((ObjectNode) value.path("differentiation")).put("different", 19);
        ObjectNode saturation = (ObjectNode) value.path("saturation");
        saturation.put("participantCount", 19).put("codedParticipantCount", 19).put("alternativeSum", 19);
        ((ObjectNode) saturation.path("maxMentionByAxis")).put("CONCERN", 19);
        ((ArrayNode) value.path("respondentFailures")).addObject().put("participantId", "R020")
            .put("group", "COMPARISON").put("attempts", 1).put("code", "PERMANENT_PROVIDER_FAILURE");
        assertThatCode(() -> MarketInterviewContract.validate(value)).doesNotThrowAnyException();
    }

    private ObjectNode valid() {
        ObjectNode root = mapper.createObjectNode();
        root.put("contract", "market-interview-result-v2").put("schemaVersion", "2.0").put("synthetic", true);
        root.putObject("source").put("marketSeedSnapshotId", "seed-1").put("selectionId", 31)
            .put("selectionRevision", 4).put("marketSeedSnapshotHash", "sha256:" + "a".repeat(64))
            .put("bmPlanRevision", 3);
        ObjectNode targeting = root.putObject("targeting");
        ObjectNode criteria = targeting.putObject("criteria");
        criteria.put("ageMin", 0).put("ageMax", 0); criteria.putArray("genders");
        criteria.put("householdSizeMin", 0).put("householdSizeMax", 0);
        criteria.putArray("regions").add("서울"); criteria.putArray("incomeKeywords");
        criteria.putArray("jobKeywords"); criteria.put("hasChildren", 0); criteria.putArray("householdRoles");
        targeting.put("criteriaText", "서울 조건 교집합 80명").put("requestedSampleSize", 20)
            .put("drawnSampleSize", 20).put("attemptedCount", 20).put("usableCount", 20)
            .put("failedCount", 0).put("targetCount", 16).put("nonTargetCount", 4)
            .putNull("targetCoverageWarning");
        ArrayNode participants = root.putArray("participants");
        ArrayNode interviews = root.putArray("interviews");
        for (int i = 1; i <= 3; i++) {
            String id = "R%03d".formatted(i);
            ObjectNode participant = participants.addObject().put("participantId", id).put("label", "가상 패널 " + id)
                .put("profile", "서울 · 매장 운영").put("context", "타겟 조건 일치");
            participant.putArray("needs"); participant.put("group", "TARGET");
            ObjectNode interview = interviews.addObject().put("participantId", id);
            ArrayNode questions = interview.putArray("questions");
            for (int q = 0; q < 9; q++) questions.addObject().put("question", "질문 " + q)
                .put("answer", "개별 응답입니다.").put("uncertainty", "실제 고객 확인 필요");
            interview.putArray("concerns").add("가격 조건"); interview.putArray("purchaseTriggers").add("조건 해소");
            interview.putArray("objections"); interview.putArray("unmetNeeds");
        }
        ArrayNode ids = mapper.createArrayNode(); for (int i = 1; i <= 20; i++) ids.add("R%03d".formatted(i));
        ObjectNode theme = root.putArray("themes").addObject().put("axis", "CONCERN").put("title", "가격 조건")
            .put("description", "가격 조건을 확인하려는 관점");
        theme.set("participantIds", ids); theme.put("mentionCount", 20).put("targetCount", 16)
            .put("nonTargetCount", 4).put("quote", "20% 할인이라면 고려할 수 있다.");
        root.putArray("crossRelationships");
        summary(root.putObject("comprehension"), "accurate", "partial", "misunderstood");
        summary(root.putObject("differentiation"), "different", "similar", "unclear");
        root.putArray("objections"); root.putArray("unmetNeeds"); root.putArray("purchaseTriggers");
        root.putArray("followUpQuestions").add("현재 방식은 무엇인가요?").add("무엇이 걸리나요?").add("무엇을 확인할까요?");
        root.putArray("limitations").add("실제 고객 조사 결과가 아닙니다.")
            .add("통계적 대표성이 없으며 시장 모집단으로 일반화할 수 없습니다.")
            .add("실제 고객 인터뷰로 다시 확인해야 합니다.");
        ArrayNode provenance = root.putArray("transcriptProvenance");
        ArrayNode coding = root.putArray("codingTrace");
        for (int i = 1; i <= 20; i++) {
            String id = "R%03d".formatted(i); String group = i <= 16 ? "TARGET" : "COMPARISON";
            provenance.addObject().put("transcriptId", "T-" + id).put("participantId", id)
                .put("answerCount", 9).put("group", group);
            ObjectNode coded = coding.addObject().put("participantId", id);
            coded.putArray("themeTitles").add("가격 조건"); coded.put("comprehension", "accurate")
                .put("differentiation", "unclear").put("alternativeLabel", "수기").put("group", group);
        }
        root.putArray("respondentFailures");
        ObjectNode saturation = root.putObject("saturation");
        saturation.put("participantCount", 20).put("codedParticipantCount", 20).put("themeCount", 1);
        axisMap(saturation.putObject("axisLabelCounts"), 0); axisMap(saturation.putObject("maxMentionByAxis"), 0);
        ((ObjectNode) saturation.path("axisLabelCounts")).put("CONCERN", 1);
        ((ObjectNode) saturation.path("maxMentionByAxis")).put("CONCERN", 20);
        saturation.putArray("saturatedThemes").add("CONCERN: 가격 조건");
        saturation.put("alternativeSum", 20).put("assessment", "EXPLORATORY_ONLY")
            .put("limitation", "사람 수 진단이며 시장 대표성이 아닙니다.");
        return root;
    }

    private void summary(ObjectNode node, String first, String second, String third) {
        node.put(first, 20).put(second, 0).put(third, 0);
    }
    private void axisMap(ObjectNode node, int value) {
        for (String axis : List.of("LIKE", "CONCERN", "DIFFERENTIATION", "USAGE_SCENE", "BARRIER", "SUGGESTION"))
            node.put(axis, value);
    }
}
