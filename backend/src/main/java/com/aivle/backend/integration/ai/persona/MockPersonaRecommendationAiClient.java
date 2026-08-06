package com.aivle.backend.integration.ai.persona;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.stream.Collectors;
import static com.aivle.backend.persona.recommendation.entity.PersonaRecommendationTypes.*;

@Component
@ConditionalOnProperty(
    prefix = "app.ai", name = "enabled", havingValue = "false", matchIfMissing = true)
public class MockPersonaRecommendationAiClient implements PersonaRecommendationAiClient {
    private static final List<String> MATCH_TOKENS = List.of(
        "10대", "20대", "30대", "40대", "50대", "60대", "70대",
        "AI", "구독", "채널", "온라인", "디지털", "라이브", "직구");

    @Override
    public PersonaRecommendationAiResponse analyze(PersonaRecommendationAiRequest request) {
        String projectText = request.sections().stream()
            .map(item -> Optional.ofNullable(item.content()).orElse(""))
            .collect(Collectors.joining(" "));
        List<Scored> selected = request.personas().stream()
            .map(persona -> score(persona, projectText))
            .sorted(Comparator.comparingInt(Scored::score).reversed()
                .thenComparing(item -> item.persona().personaCode()))
            .limit(3).toList();
        List<PersonaRecommendationAiResponse.Item> items = new ArrayList<>();
        for (int index = 0; index < selected.size(); index++) {
            Scored scored = selected.get(index);
            items.add(new PersonaRecommendationAiResponse.Item(
                scored.persona().personaCode(), index + 1, scored.score(),
                scored.matches().isEmpty() ? PersonaConfidence.LOW : PersonaConfidence.MEDIUM,
                scored.matches().isEmpty()
                    ? List.of("문서와 기준 군집에서 직접 일치하는 표현이 부족해 우선 검증 대상으로만 제안합니다.")
                    : scored.matches().stream()
                        .map(token -> "프로젝트 입력과 기준 군집 설명에서 '" + token + "' 관련 단서가 함께 나타납니다.")
                        .toList(),
                List.of("집계 군집은 개별 고객을 대표하지 않아 실제 고객 접점에서 불일치할 수 있습니다."),
                List.of("키워드 일치는 고객 적합성의 충분조건이 아니며 Mock 분석 규칙입니다."),
                List.of(new PersonaRecommendationAiResponse.Evidence(
                    "AI_INFERENCE", "문서와 집계 군집 설명의 제한적 키워드 일치",
                    scored.persona().personaCode())),
                List.of("이 군집이 실제 문제를 반복해서 경험했는가?",
                    "현재 대안과 전환 장벽은 무엇인가?"),
                "Mock 규칙으로 만든 적합도 해석이며 모집단 통계나 구매 확률이 아닙니다."));
        }
        List<PersonaRecommendationAiResponse.Hypothesis> hypotheses = new ArrayList<>();
        List<PersonaRecommendationAiResponse.ValidationPlan> plans = new ArrayList<>();
        for (Scored scored : selected.stream().limit(2).toList()) {
            String code = scored.persona().personaCode();
            hypotheses.add(hypothesis(code, HypothesisType.CUSTOMER_SEGMENT,
                "이 기준 고객군은 계획서의 문제를 반복적으로 경험할 것이다.", ValidationPriority.HIGH));
            hypotheses.add(hypothesis(code, HypothesisType.CHANNEL,
                "이 기준 고객군에 도달하는 채널 가설은 과거 이용 행동으로 검증할 수 있다.",
                ValidationPriority.MEDIUM));
            plans.add(plan(request, scored.persona()));
        }
        return new PersonaRecommendationAiResponse(
            "mock", "mock-persona-recommendation-v1",
            "mock-persona-" + request.structuredPlanId() + "-" + request.feasibilityAssessmentId(),
            "확정 계획과 기준 페르소나 카탈로그를 비교해 우선 검증할 고객군과 질문을 구성했습니다.",
            PersonaConfidence.LOW, List.copyOf(items), List.copyOf(hypotheses),
            List.copyOf(plans));
    }

    private PersonaRecommendationAiResponse.Hypothesis hypothesis(
        String code, HypothesisType type, String statement, ValidationPriority priority
    ) {
        return new PersonaRecommendationAiResponse.Hypothesis(
            code, type, statement,
            "확정 계획과 집계 군집 설명을 비교한 가설이며 실제 응답으로 검증되지 않았습니다.",
            HypothesisSourceType.AI_INFERENCE, "persona:" + code,
            PersonaConfidence.LOW, priority);
    }

    private Scored score(
        PersonaRecommendationAiRequest.BaselinePersona persona, String projectText
    ) {
        String basis = persona.displayName() + " " + persona.keyTraitsJson();
        List<String> matches = MATCH_TOKENS.stream()
            .filter(token -> projectText.contains(token) && basis.contains(token)).toList();
        return new Scored(persona, Math.min(85, 45 + matches.size() * 8), matches);
    }

    private PersonaRecommendationAiResponse.ValidationPlan plan(
        PersonaRecommendationAiRequest request,
        PersonaRecommendationAiRequest.BaselinePersona persona
    ) {
        String code = persona.personaCode();
        List<PersonaRecommendationAiResponse.InterviewQuestion> interviews = List.of(
            interview(1, "최근 이 문제를 겪었던 상황을 처음부터 설명해 주세요.", "실제 문제 경험", "CUSTOMER_SEGMENT", "당시 어떤 대안을 사용했나요?"),
            interview(2, "현재 해결 방법에서 가장 불편하거나 비용이 드는 부분은 무엇인가요?", "대안 비용", "CUSTOMER_SEGMENT", "얼마나 자주 발생하나요?"),
            interview(3, "관련 정보를 찾거나 서비스를 고를 때 어떤 경로를 사용하나요?", "채널 행동", "CHANNEL", "마지막 선택 과정을 설명해 주세요."),
            interview(4, "비슷한 해결책에 실제로 비용이나 시간을 쓴 경험이 있나요?", "지불 행동", "CUSTOMER_SEGMENT", "선택하지 않은 대안은 무엇인가요?"),
            interview(5, "새 해결책을 시험하기 전에 반드시 확인하는 조건은 무엇인가요?", "전환 장벽", "CUSTOMER_SEGMENT", "누가 결정에 영향을 주나요?"));
        List<PersonaRecommendationAiResponse.SurveyQuestion> surveys = List.of(
            survey(1, SurveyQuestionType.SINGLE_CHOICE, "최근 3개월 동안 관련 문제를 얼마나 자주 경험했나요?",
                List.of("경험 없음", "월 1회 미만", "월 1~3회", "주 1회 이상"), "문제 빈도", "CUSTOMER_SEGMENT"),
            survey(2, SurveyQuestionType.MULTIPLE_CHOICE, "현재 사용하는 해결 방법을 모두 선택해 주세요.",
                List.of("직접 처리", "기존 서비스", "주변 도움", "해결하지 않음", "기타"), "현재 대안", "CUSTOMER_SEGMENT"),
            survey(3, SurveyQuestionType.SCALE, "현재 해결 방법의 불편 정도를 선택해 주세요.",
                List.of("1", "2", "3", "4", "5"), "불편 강도", "CUSTOMER_SEGMENT"),
            survey(4, SurveyQuestionType.SINGLE_CHOICE, "관련 정보를 가장 자주 찾는 경로는 무엇인가요?",
                List.of("검색", "커뮤니티", "SNS", "지인", "오프라인", "기타"), "채널", "CHANNEL"),
            survey(5, SurveyQuestionType.SHORT_TEXT, "새 해결책을 검토할 때 가장 중요한 조건은 무엇인가요?",
                List.of(), "선택 기준", "CUSTOMER_SEGMENT"));
        return new PersonaRecommendationAiResponse.ValidationPlan(
            code, ValidationMethod.INTERVIEW,
            "문제 경험, 현재 대안, 접근 채널 가설을 실제 고객 인터뷰와 설문으로 검증합니다.",
            persona.displayName() + "과 유사한 특성을 가진 실제 참여자",
            null, "개인정보 최소 수집 원칙에 따라 팀이 모집 채널을 결정",
            List.of("가설별로 반증 가능한 과거 행동 근거를 확보합니다."),
            List.of("동의받은 인터뷰 기록과 익명 설문 응답 원본"),
            interviews, surveys,
            request.feasibility().validationTasks().stream()
                .map(PersonaRecommendationAiRequest.ValidationTask::id).toList(),
            ValidationPriority.HIGH);
    }

    private PersonaRecommendationAiResponse.InterviewQuestion interview(
        int order, String question, String purpose, String hypothesis, String followUp
    ) {
        return new PersonaRecommendationAiResponse.InterviewQuestion(
            order, question, purpose, hypothesis, true, followUp,
            "구체적인 과거 행동, 상황, 대안과 비용");
    }

    private PersonaRecommendationAiResponse.SurveyQuestion survey(
        int order, SurveyQuestionType type, String question, List<String> options,
        String purpose, String hypothesis
    ) {
        return new PersonaRecommendationAiResponse.SurveyQuestion(
            order, type, question, options, true, purpose, hypothesis,
            type == SurveyQuestionType.SCALE ? "전혀 불편하지 않음" : null,
            type == SurveyQuestionType.SCALE ? "매우 불편함" : null);
    }

    private record Scored(
        PersonaRecommendationAiRequest.BaselinePersona persona, int score, List<String> matches
    ) {}
}
