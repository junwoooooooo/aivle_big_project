package com.aivle.backend.persona.recommendation.application;

import com.aivle.backend.integration.ai.persona.PersonaRecommendationAiResponse;
import org.springframework.stereotype.Component;
import java.util.*;
import static com.aivle.backend.persona.recommendation.entity.PersonaRecommendationTypes.*;

@Component
public class PersonaFitScorePolicy {
    public Outcome evaluate(List<PersonaRecommendationAiResponse.Item> items) {
        if (items == null || items.size() < 2) {
            throw new IllegalArgumentException("at least two persona recommendations are required");
        }
        Set<String> codes = new HashSet<>();
        Set<Integer> ranks = new HashSet<>();
        Map<String, RecommendationLevel> levels = new HashMap<>();
        for (var item : items) {
            if (item == null || item.personaCode() == null || !codes.add(item.personaCode())
                || item.rank() == null || item.rank() < 1 || !ranks.add(item.rank())
                || item.confidence() == null
                || (item.fitScore() != null
                    && (item.fitScore() < 0 || item.fitScore() > 100))) {
                throw new IllegalArgumentException("persona recommendation rank is invalid");
            }
            RecommendationLevel level = item.fitScore() == null
                ? RecommendationLevel.INSUFFICIENT_INFORMATION
                : item.rank() == 1 ? RecommendationLevel.PRIMARY
                : item.rank() == 2 ? RecommendationLevel.SECONDARY
                : RecommendationLevel.LOW_PRIORITY;
            levels.put(item.personaCode(), level);
        }
        if (!ranks.containsAll(List.of(1, 2))) {
            throw new IllegalArgumentException("primary and secondary ranks are required");
        }
        String primary = items.stream().filter(item -> item.rank() == 1)
            .map(PersonaRecommendationAiResponse.Item::personaCode).findFirst().orElseThrow();
        String secondary = items.stream().filter(item -> item.rank() == 2)
            .map(PersonaRecommendationAiResponse.Item::personaCode).findFirst().orElseThrow();
        PersonaConfidence confidence = items.stream().allMatch(
            item -> item.confidence() == PersonaConfidence.HIGH)
                ? PersonaConfidence.HIGH
                : items.stream().anyMatch(
                    item -> item.confidence() == PersonaConfidence.LOW)
                    ? PersonaConfidence.LOW : PersonaConfidence.MEDIUM;
        return new Outcome(primary, secondary, confidence, Map.copyOf(levels));
    }

    public record Outcome(
        String primaryPersonaCode,
        String secondaryPersonaCode,
        PersonaConfidence confidence,
        Map<String, RecommendationLevel> levels
    ) {}
}
