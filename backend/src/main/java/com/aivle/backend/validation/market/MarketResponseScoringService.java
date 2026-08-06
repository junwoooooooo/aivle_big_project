package com.aivle.backend.validation.market;

import com.aivle.backend.validation.PersonaValidationSourceService.Context;
import com.aivle.backend.validation.PersonaValidationSourceService.PersonaView;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class MarketResponseScoringService {
    private static final List<String> METRICS = List.of(
        "interest", "clarity", "trust", "usageIntent", "conversionIntent", "sharingIntent"
    );

    public ScoringResult score(
        Context context,
        List<MessageVariant> messages,
        String priceContext,
        String channel,
        boolean hasPanelResult
    ) {
        List<PersonaMessageResult> results = new ArrayList<>();
        for (PersonaView persona : context.selected()) {
            for (MessageVariant message : messages) {
                Map<String, Integer> scores = new LinkedHashMap<>();
                for (String metric : METRICS) {
                    int value = 45 + Math.floorMod(
                        Objects.hash(persona.code(), message.text(), metric),
                        31
                    );
                    value += keywordMatch(persona, message.text()) * 3;
                    if (hasPanelResult) value += 2;
                    if ("trust".equals(metric) && blank(priceContext)) value -= 4;
                    scores.put(metric, clamp(value));
                }
                results.add(new PersonaMessageResult(
                    persona.id(),
                    persona.name(),
                    message.id(),
                    message.text(),
                    scores,
                    positiveFactors(persona, message, channel),
                    negativeFactors(priceContext, message.text()),
                    recommendations(priceContext, message.text())
                ));
            }
        }
        return new ScoringResult(results, summarize(results, channel, priceContext));
    }

    private int keywordMatch(PersonaView persona, String message) {
        String normalized = message.toLowerCase(Locale.ROOT);
        return (int) persona.keyTraits().stream()
            .filter(value -> normalized.contains(value.toLowerCase(Locale.ROOT)))
            .limit(3)
            .count();
    }

    private List<String> positiveFactors(
        PersonaView persona,
        MessageVariant message,
        String channel
    ) {
        List<String> values = new ArrayList<>();
        values.add(persona.keyTraits().stream().findFirst().orElse("Persona 핵심 특성과의 연관성"));
        values.add(message.text().length() <= 90 ? "핵심 메시지의 간결성" : "구체적인 설명 제공");
        if (!blank(channel)) values.add(channel + " 채널 맥락");
        return values.stream().limit(3).toList();
    }

    private List<String> negativeFactors(String priceContext, String message) {
        List<String> values = new ArrayList<>();
        if (blank(priceContext)) values.add("가격·비용 정보 부족");
        if (!containsEvidence(message)) values.add("구체적인 근거 부족");
        if (message.length() > 180) values.add("핵심 메시지가 길어 이해 부담 가능");
        if (values.isEmpty()) values.add("실제 고객 반응 확인 필요");
        return values;
    }

    private List<String> recommendations(String priceContext, String message) {
        List<String> values = new ArrayList<>();
        if (!containsEvidence(message)) values.add("검증 가능한 성과 근거 또는 사용 사례를 추가하세요.");
        if (blank(priceContext)) values.add("가격대나 시작 비용을 명확하게 안내하세요.");
        values.add("실제 고객에게 표현 이해도와 선택 이유를 확인하세요.");
        return values.stream().limit(3).toList();
    }

    private boolean containsEvidence(String message) {
        String value = message.toLowerCase(Locale.ROOT);
        return value.contains("근거") || value.contains("사례")
            || value.contains("검증") || value.matches(".*\\d+.*");
    }

    private OverallSummary summarize(
        List<PersonaMessageResult> results,
        String channel,
        String priceContext
    ) {
        PersonaMessageResult best = results.stream()
            .max(Comparator.comparingDouble(this::average))
            .orElseThrow();
        return new OverallSummary(
            best.personaName(),
            best.messageId(),
            List.of("메시지의 구체성", "Persona 핵심 특성과의 연결"),
            blank(priceContext)
                ? List.of("가격·비용 정보 부족", "실제 반응 근거 필요")
                : List.of("실제 반응 근거 필요"),
            best.recommendedChanges(),
            "자세히 보기",
            blank(channel) ? "Persona가 주로 사용하는 채널을 추가 확인" : channel,
            blank(priceContext) ? "가격 정보가 없어 비용 우려를 별도로 확인해야 합니다." : priceContext
        );
    }

    private double average(PersonaMessageResult value) {
        return value.scores().values().stream().mapToInt(Integer::intValue).average().orElse(0);
    }

    private int clamp(int value) {
        return Math.max(20, Math.min(95, value));
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    public record MessageVariant(String id, String text) { }
    public record ScoringResult(
        List<PersonaMessageResult> results,
        OverallSummary summary
    ) { }
    public record PersonaMessageResult(
        Long personaId,
        String personaName,
        String messageId,
        String message,
        Map<String, Integer> scores,
        List<String> positiveFactors,
        List<String> negativeFactors,
        List<String> recommendedChanges
    ) { }
    public record OverallSummary(
        String bestPersona,
        String bestMessage,
        List<String> commonPositiveFactors,
        List<String> commonNegativeFactors,
        List<String> messageImprovements,
        String recommendedCta,
        String recommendedChannel,
        String priceConcern
    ) { }
}
