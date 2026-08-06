package com.aivle.backend.marketing.content;

import com.aivle.backend.analysis.feasibility.repository.FeasibilityAssessmentRepository;
import com.aivle.backend.analysis.legal.repository.LegalReviewRepository;
import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.persona.catalog.entity.BaselinePersona;
import com.aivle.backend.project.entity.Project;
import com.aivle.backend.validation.PersonaValidationTypes.ValidationStatus;
import com.aivle.backend.validation.market.MarketResponsePrediction;
import com.aivle.backend.validation.market.MarketResponseRepository;
import com.aivle.backend.validation.panel.PanelInterview;
import com.aivle.backend.validation.panel.PanelInterviewRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class MarketingSourceSnapshotService {
    private static final TypeReference<Map<String, Object>> OBJECT_MAP =
        new TypeReference<>() { };

    private final PanelInterviewRepository panels;
    private final MarketResponseRepository markets;
    private final LegalReviewRepository legalReviews;
    private final FeasibilityAssessmentRepository feasibility;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public SnapshotResult capture(
        Project project,
        BaselinePersona persona,
        String recommendedPersonaCode,
        UserInput input,
        Long panelInterviewId,
        Long marketResponseId,
        int snapshotVersion
    ) {
        MarketResponsePrediction market = market(
            project.getId(),
            marketResponseId
        );
        Long effectivePanelId = panelInterviewId;
        if (effectivePanelId == null && market != null
            && market.getPanelInterview() != null) {
            effectivePanelId = market.getPanelInterview().getId();
        }
        PanelInterview panel = panel(project.getId(), effectivePanelId);
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("sourceSnapshotVersion", snapshotVersion);
        snapshot.put("capturedAt", LocalDateTime.now(clock).toString());
        snapshot.put("project", project(project));
        snapshot.put("persona", persona(persona, recommendedPersonaCode));
        snapshot.put("legalReview", legal(project));
        snapshot.put("feasibility", feasibility(project));
        snapshot.put("panelInterview", panel(panel));
        snapshot.put("marketResponse", market(market));
        snapshot.put("userInput", inputMap(input));
        return new SnapshotResult(
            json(snapshot),
            panel == null ? null : panel.getId(),
            market == null ? null : market.getId(),
            snapshotVersion
        );
    }

    public Guidance guidance(String snapshotJson) {
        Map<String, Object> snapshot = parse(snapshotJson);
        Map<String, Object> persona = map(snapshot.get("persona"));
        Map<String, Object> panel = map(snapshot.get("panelInterview"));
        Map<String, Object> market = map(snapshot.get("marketResponse"));
        Map<String, Object> legal = map(snapshot.get("legalReview"));
        Map<String, Object> panelSummary = map(panel.get("summary"));
        Map<String, Object> marketSummary = map(market.get("summary"));
        List<String> evidence = new ArrayList<>();
        addEvidence(evidence, "패널 인터뷰", first(panelSummary, "commonNeeds"));
        addEvidence(evidence, "시장 반응", text(market.get("bestMessageText")));
        addEvidence(evidence, "Persona", text(persona.get("name")));
        String bestMessage = text(market.get("bestMessageText"));
        String effectiveMessage = first(panelSummary, "effectiveMessages");
        String commonNeed = first(panelSummary, "commonNeeds");
        String motivation = first(panelSummary, "purchaseMotivations");
        String positive = first(marketSummary, "commonPositiveFactors");
        String recommendedCta = text(marketSummary.get("recommendedCta"));
        List<String> avoid = new ArrayList<>();
        avoid.addAll(strings(panelSummary.get("rejectionFactors")));
        avoid.addAll(strings(panelSummary.get("avoidedExpressions")));
        avoid.addAll(strings(marketSummary.get("commonNegativeFactors")));
        String legalSummary = text(legal.get("summary"));
        if (!legalSummary.isBlank()) avoid.add(legalSummary);
        return new Guidance(
            firstNonBlank(bestMessage, effectiveMessage),
            joinNonBlank(commonNeed, motivation, positive),
            recommendedCta,
            List.copyOf(avoid.stream().filter(value -> !value.isBlank()).limit(5).toList()),
            List.copyOf(evidence),
            recommendedPresets(marketSummary, panelSummary)
        );
    }

    public String legalNotice(String snapshotJson) {
        Map<String, Object> legal = map(parse(snapshotJson).get("legalReview"));
        if (!Boolean.TRUE.equals(legal.get("available"))) {
            return "법률 검토 결과가 없어 광고 표현 주의사항이 자동 반영되지 않았습니다.";
        }
        String summary = text(legal.get("summary"));
        return summary.isBlank()
            ? "법률 검토 결과가 있습니다. 광고 표현의 근거와 필수 고지 가능성을 확인해 주세요."
            : summary;
    }

    public Map<String, Object> userInput(String snapshotJson) {
        return map(parse(snapshotJson).get("userInput"));
    }

    private PanelInterview panel(Long projectId, Long id) {
        if (id == null) return null;
        PanelInterview value = panels.findByIdAndProjectIdAndDeletedAtIsNull(id, projectId)
            .orElseThrow(() -> new BusinessException(
                ErrorCode.MARKETING_PANEL_INTERVIEW_INVALID
            ));
        completed(value.getStatus());
        return value;
    }

    private MarketResponsePrediction market(Long projectId, Long id) {
        if (id == null) return null;
        MarketResponsePrediction value = markets
            .findByIdAndProjectIdAndDeletedAtIsNull(id, projectId)
            .orElseThrow(() -> new BusinessException(
                ErrorCode.MARKETING_MARKET_RESPONSE_INVALID
            ));
        completed(value.getStatus());
        return value;
    }

    private void completed(ValidationStatus status) {
        if (status != ValidationStatus.COMPLETED) {
            throw new BusinessException(
                ErrorCode.MARKETING_VALIDATION_RESULT_NOT_COMPLETED
            );
        }
    }

    private Map<String, Object> project(Project value) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", value.getId());
        result.put("title", value.getTitle());
        result.put("summary", Optional.ofNullable(value.getDescription()).orElse(""));
        result.put("industryCategory", Optional.ofNullable(value.getIndustryCategory()).orElse(""));
        return result;
    }

    private Map<String, Object> persona(
        BaselinePersona value,
        String recommendedPersonaCode
    ) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("available", value != null);
        result.put("recommendedPersonaCode", Optional
            .ofNullable(recommendedPersonaCode).orElse(""));
        if (value != null) {
            result.put("id", value.getId());
            result.put("code", value.getPersonaCode());
            result.put("name", value.getDisplayName());
            result.put("summary", Optional.ofNullable(value.getDescription()).orElse(""));
            result.put("keyTraits", stringsJson(value.getKeyTraitsJson()));
        }
        return result;
    }

    private Map<String, Object> legal(Project project) {
        return legalReviews
            .findTopByProjectIdAndProjectOwnerIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(
                project.getId(), project.getOwner().getId()
            )
            .map(value -> {
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("available", true);
                result.put("riskLevel", value.getRiskLevel() == null
                    ? "" : value.getRiskLevel().name());
                result.put("summary", Optional.ofNullable(value.getSummary()).orElse(""));
                return result;
            })
            .orElseGet(() -> Map.of("available", false));
    }

    private Map<String, Object> feasibility(Project project) {
        return feasibility
            .findTopByProjectIdAndProjectOwnerIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(
                project.getId(), project.getOwner().getId()
            )
            .map(value -> {
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("available", true);
                result.put("score", Optional.ofNullable(value.getOverallScore())
                    .map(String::valueOf).orElse(""));
                result.put("summary", Optional.ofNullable(value.getSummary()).orElse(""));
                return result;
            })
            .orElseGet(() -> Map.of("available", false));
    }

    private Map<String, Object> panel(PanelInterview value) {
        if (value == null) return Map.of("status", "NOT_INCLUDED");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "INCLUDED");
        result.put("interviewId", value.getId());
        result.put("title", value.getTitle());
        result.put("purpose", value.getPurpose().name());
        result.put("personaNames", personaNames(value.getSourceSnapshotJson()));
        result.put("summary", parse(value.getSummaryJson()));
        result.put("completedAt", value.getCompletedAt() == null
            ? "" : value.getCompletedAt().toString());
        return result;
    }

    private Map<String, Object> market(MarketResponsePrediction value) {
        if (value == null) return Map.of("status", "NOT_INCLUDED");
        Map<String, Object> summary = parse(value.getSummaryJson());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "INCLUDED");
        result.put("predictionId", value.getId());
        result.put("title", value.getTitle());
        result.put("summary", summary);
        result.put("bestMessageText", bestMessageText(value, summary));
        result.put("completedAt", value.getCompletedAt() == null
            ? "" : value.getCompletedAt().toString());
        return result;
    }

    private String bestMessageText(
        MarketResponsePrediction value,
        Map<String, Object> summary
    ) {
        String bestId = text(summary.get("bestMessage"));
        Object raw = parse(value.getSourceSnapshotJson()).get("messages");
        if (raw instanceof List<?> messages) {
            for (Object item : messages) {
                Map<String, Object> message = map(item);
                if (bestId.equals(text(message.get("id")))) {
                    return text(message.get("text"));
                }
            }
        }
        return "";
    }

    private List<String> personaNames(String json) {
        Object raw = parse(json).get("personas");
        if (!(raw instanceof List<?> values)) return List.of();
        return values.stream()
            .map(this::map)
            .map(value -> text(value.get("name")))
            .filter(value -> !value.isBlank())
            .toList();
    }

    private Map<String, Object> inputMap(UserInput input) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("targetOffer", input.targetOffer());
        result.put("emphasisMessage", input.emphasisMessage());
        result.put("requiredText", input.requiredText());
        result.put("avoidedText", input.avoidedText());
        result.put("brandName", input.brandName());
        result.put("callToAction", input.callToAction());
        result.put("tone", input.tone());
        return result;
    }

    private List<String> recommendedPresets(
        Map<String, Object> market,
        Map<String, Object> panel
    ) {
        List<String> result = new ArrayList<>();
        String negative = String.join(" ", strings(market.get("commonNegativeFactors")));
        String positive = String.join(" ", strings(market.get("commonPositiveFactors")));
        String needs = String.join(" ", strings(panel.get("commonNeeds")));
        if (negative.contains("근거") || negative.contains("신뢰")) result.add("TRUST");
        if (positive.contains("행동") || !text(market.get("recommendedCta")).isBlank()) {
            result.add("ENERGY");
        }
        if (needs.contains("간편") || needs.contains("명확")) result.add("MINIMAL");
        for (String fallback : List.of("TRUST", "ENERGY", "MINIMAL")) {
            if (!result.contains(fallback)) result.add(fallback);
        }
        return result.stream().limit(3).toList();
    }

    private void addEvidence(List<String> target, String source, String value) {
        if (!value.isBlank()) target.add(source + ": " + value);
    }

    private String first(Map<String, Object> value, String key) {
        return strings(value.get(key)).stream().findFirst().orElse("");
    }

    private String firstNonBlank(String... values) {
        for (String value : values) if (!value.isBlank()) return value;
        return "";
    }

    private String joinNonBlank(String... values) {
        return String.join(" ", java.util.Arrays.stream(values)
            .filter(value -> value != null && !value.isBlank()).toList());
    }

    private List<String> strings(Object value) {
        if (value instanceof List<?> values) {
            return values.stream().map(this::text)
                .filter(item -> !item.isBlank()).toList();
        }
        return List.of();
    }

    private List<String> stringsJson(String json) {
        try {
            return List.of(objectMapper.readValue(json, String[].class));
        } catch (JacksonException exception) {
            return List.of();
        }
    }

    private Map<String, Object> parse(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(json, OBJECT_MAP);
        } catch (JacksonException exception) {
            return Map.of();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?>
            ? (Map<String, Object>) value : Map.of();
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new BusinessException(ErrorCode.MARKETING_CONTENT_SOURCE_UNAVAILABLE);
        }
    }

    public record UserInput(
        String targetOffer,
        String emphasisMessage,
        String requiredText,
        String avoidedText,
        String brandName,
        String callToAction,
        String tone
    ) {
        public UserInput {
            targetOffer = safe(targetOffer);
            emphasisMessage = safe(emphasisMessage);
            requiredText = safe(requiredText);
            avoidedText = safe(avoidedText);
            brandName = safe(brandName);
            callToAction = safe(callToAction);
            tone = safe(tone);
        }

        private static String safe(String value) {
            return value == null ? "" : value;
        }
    }

    public record SnapshotResult(
        String json,
        Long panelInterviewId,
        Long marketResponseId,
        int version
    ) { }

    public record Guidance(
        String headlineSeed,
        String bodySeed,
        String cta,
        List<String> avoidedExpressions,
        List<String> evidence,
        List<String> recommendedPresets
    ) { }
}
