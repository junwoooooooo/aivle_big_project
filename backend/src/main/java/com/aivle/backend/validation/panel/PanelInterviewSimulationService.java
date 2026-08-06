package com.aivle.backend.validation.panel;

import com.aivle.backend.validation.PersonaValidationSourceService.Context;
import com.aivle.backend.validation.PersonaValidationSourceService.PersonaView;
import com.aivle.backend.validation.PersonaValidationTypes.InterviewPurpose;
import com.aivle.backend.validation.PersonaValidationTypes.Sentiment;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class PanelInterviewSimulationService {
    public SimulationResult simulate(
        Context context,
        InterviewPurpose purpose,
        List<String> questions
    ) {
        List<ExpectedAnswer> answers = new ArrayList<>();
        for (PersonaView persona : context.selected()) {
            for (int index = 0; index < questions.size(); index++) {
                String question = questions.get(index);
                Sentiment sentiment = sentiment(persona.code(), question, purpose);
                answers.add(new ExpectedAnswer(
                    persona.id(),
                    persona.name(),
                    index + 1,
                    question,
                    answer(context, persona, question, purpose, sentiment),
                    sentiment,
                    keyPoints(persona, purpose, sentiment)
                ));
            }
        }
        Summary summary = summary(context, purpose);
        return new SimulationResult(answers, summary);
    }

    private String answer(
        Context context,
        PersonaView persona,
        String question,
        InterviewPurpose purpose,
        Sentiment sentiment
    ) {
        String trait = persona.keyTraits().stream().findFirst().orElse("실용적인 가치");
        String project = context.project().getTitle();
        String direction = switch (sentiment) {
            case POSITIVE -> "관심을 보이지만 실제 효용을 확인하고 싶어 할 가능성이 높습니다.";
            case NEGATIVE -> "필요성과 근거가 충분하지 않으면 선택을 보류할 가능성이 높습니다.";
            case MIXED -> "가치는 이해하지만 비용과 전환 부담을 함께 비교할 가능성이 높습니다.";
            case NEUTRAL -> "구체적인 사용 상황과 조건이 제시되면 판단을 시작할 가능성이 높습니다.";
        };
        return persona.name() + " 관점에서는 " + project + "를 "
            + trait + " 기준으로 살펴볼 수 있습니다. " + direction
            + " 이 답변은 질문 ‘" + abbreviated(question) + "’과 "
            + purposeLabel(purpose) + " 목적을 바탕으로 한 예상입니다.";
    }

    private Sentiment sentiment(String code, String question, InterviewPurpose purpose) {
        int value = Math.floorMod(Objects.hash(code, question, purpose.name()), 4);
        return Sentiment.values()[value];
    }

    private List<String> keyPoints(
        PersonaView persona,
        InterviewPurpose purpose,
        Sentiment sentiment
    ) {
        List<String> values = new ArrayList<>();
        values.add(persona.keyTraits().stream().findFirst().orElse("구체적인 효용"));
        values.add(switch (purpose) {
            case PROBLEM_DISCOVERY -> "현재 대안의 불편";
            case VALUE_PROPOSITION -> "가치의 구체성";
            case PURCHASE_MOTIVATION -> "가격과 전환 부담";
            case MESSAGE_REACTION -> "표현의 신뢰 근거";
            case CUSTOM -> "추가 확인 필요";
        });
        if (sentiment == Sentiment.NEGATIVE || sentiment == Sentiment.MIXED) {
            values.add("결정 장벽 확인");
        }
        return values.stream().limit(3).toList();
    }

    private Summary summary(Context context, InterviewPurpose purpose) {
        List<String> names = context.selected().stream().map(PersonaView::name).toList();
        return new Summary(
            List.of("구체적인 사용 상황", "현재 대안보다 명확한 효용", "판단 가능한 정보"),
            names.stream().map(name -> name + "의 우선 판단 기준을 구분해 확인").toList(),
            List.of("가격·도입 부담", "성과 근거 부족", "기존 방식 전환 비용"),
            List.of("즉시 체감 가능한 편의", "시간 또는 비용 절감", "낮은 시작 부담"),
            List.of("모호한 혜택", "검증되지 않은 단정", "복잡한 시작 절차"),
            List.of(purposeLabel(purpose) + "에 맞춘 구체적인 사용 사례"),
            List.of("정확한 확률·성과를 보장하는 표현", "근거 없는 최상급 표현"),
            List.of("실제 고객에게 현재 대안과 지불 의사를 확인하세요.")
        );
    }

    private String purposeLabel(InterviewPurpose purpose) {
        return switch (purpose) {
            case PROBLEM_DISCOVERY -> "고객 문제 확인";
            case VALUE_PROPOSITION -> "가치 제안 검증";
            case PURCHASE_MOTIVATION -> "구매 동기 확인";
            case MESSAGE_REACTION -> "마케팅 메시지 반응";
            case CUSTOM -> "직접 설정";
        };
    }

    private String abbreviated(String value) {
        return value.length() <= 40 ? value : value.substring(0, 40) + "…";
    }

    public record SimulationResult(List<ExpectedAnswer> answers, Summary summary) { }
    public record ExpectedAnswer(
        Long personaId,
        String personaName,
        int questionOrder,
        String question,
        String answer,
        Sentiment sentiment,
        List<String> keyPoints
    ) { }
    public record Summary(
        List<String> commonNeeds,
        List<String> personaDifferences,
        List<String> concerns,
        List<String> purchaseMotivations,
        List<String> rejectionFactors,
        List<String> effectiveMessages,
        List<String> avoidedExpressions,
        List<String> followUpQuestions
    ) { }
}
