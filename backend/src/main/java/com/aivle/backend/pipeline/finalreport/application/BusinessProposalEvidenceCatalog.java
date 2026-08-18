package com.aivle.backend.pipeline.finalreport.application;

import static com.aivle.backend.pipeline.finalreport.application.FinalReportComposer.ReportSource;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/** Canonical, source-owned evidence presented to the proposal model and UI. */
@Component
@RequiredArgsConstructor
public class BusinessProposalEvidenceCatalog {
    private static final int MAX_PER_SOURCE = 80;
    private static final Set<String> ROOTS = Set.of(
        "name", "description", "industryCategory", "conceptName", "conceptDefinition", "targetUsers",
        "problemScenario", "coreValue", "solutionMechanism", "revenueModel", "price", "channels",
        "tam", "sam", "som", "growth", "competitors", "alternatives", "demand", "assumptions",
        "decision", "overallDecision", "marketFit", "internalConsistency", "canvas", "strengths",
        "weaknesses", "risks", "themes", "mentionCount", "targetCount", "nonTargetCount", "quote",
        "limitations", "executiveSummary", "targetCustomers", "positioning", "coreMessages",
        "channelStrategies", "campaignRoadmap", "budgetGuidelines", "actions", "kpis", "summary",
        "score", "unresolvedItems", "revenue", "operatingProfit", "workingCapital", "bep",
        "breakEven", "sensitiveVariables", "requiredControls", "requiredDisclosures", "prohibitedVariants",
        "requiredPartnersAndQualifications", "unknownFacts", "officialEvidenceReferences");
    private static final Set<String> SKIP = Set.of(
        "contract", "schemaVersion", "resultHash", "inputHash", "sourceHash", "participantId",
        "transcriptId", "participantIds", "question", "answer", "uncertainty", "artifactRef");
    private static final Map<String, String> LABELS = Map.ofEntries(
        Map.entry("targetUsers", "대상 고객"), Map.entry("problemScenario", "문제·기회"),
        Map.entry("coreValue", "핵심 가치"), Map.entry("solutionMechanism", "제공 방식"),
        Map.entry("revenueModel", "수익 구조"), Map.entry("tam", "시장 규모 관측"),
        Map.entry("sam", "접근시장 관측"), Map.entry("growth", "성장 참고 지표"),
        Map.entry("competitors", "경쟁·대체재"), Map.entry("demand", "수요 근거"),
        Map.entry("marketFit", "시장 적합성"), Map.entry("internalConsistency", "내부 일관성"),
        Map.entry("strengths", "강점"), Map.entry("weaknesses", "약점"), Map.entry("risks", "위험"),
        Map.entry("themes", "인터뷰 테마"), Map.entry("quote", "대표 원문"),
        Map.entry("positioning", "포지셔닝"), Map.entry("coreMessages", "핵심 메시지"),
        Map.entry("campaignRoadmap", "캠페인 로드맵"), Map.entry("budgetGuidelines", "예산 운영 기준"),
        Map.entry("operatingProfit", "영업이익"), Map.entry("workingCapital", "운전자금"),
        Map.entry("bep", "손익분기점"), Map.entry("requiredControls", "필요 조치"),
        Map.entry("requiredDisclosures", "필수 고지"), Map.entry("prohibitedVariants", "금지 표현"));

    private final ObjectMapper mapper;

    public ArrayNode build(List<ReportSource> sources) {
        ArrayNode result = mapper.createArrayNode();
        for (ReportSource source : sources) {
            int before = result.size();
            collect(source, source.data(), "", null, false, result, before + MAX_PER_SOURCE);
        }
        return result;
    }

    private void collect(ReportSource source, JsonNode node, String path, String rootLabel,
            boolean relevant, ArrayNode result, int limit) {
        if (node == null || node.isNull() || result.size() >= limit) return;
        if (node.isValueNode()) {
            String value = node.asText().strip();
            if (relevant && !value.isBlank()) add(source, path, rootLabel, value, null, null, result);
            return;
        }
        if (node.isArray()) {
            int index = 0;
            for (JsonNode item : node) {
                if (result.size() >= limit) return;
                collect(source, item, path + "/" + index++, rootLabel, relevant, result, limit);
            }
            return;
        }
        for (Map.Entry<String, JsonNode> entry : node.properties()) {
            String key = entry.getKey();
            if (SKIP.contains(key) || key.startsWith("_")) continue;
            boolean childRelevant = relevant || ROOTS.contains(key);
            String label = childRelevant && !relevant ? LABELS.getOrDefault(key, humanize(key)) : rootLabel;
            JsonNode child = entry.getValue();
            String childPath = path + "/" + key;
            if (childRelevant && child.isValueNode()) {
                String actualQuote = "quote".equals(key) ? child.asText() : null;
                JsonNode ids = node.path("participantIds");
                add(source, childPath, label, child.asText(), actualQuote,
                    ids.isArray() ? ids : null, result);
            } else collect(source, child, childPath, label, childRelevant, result, limit);
            if (result.size() >= limit) return;
        }
    }

    private void add(ReportSource source, String path, String label, String value, String actualQuote,
            JsonNode respondentIds, ArrayNode result) {
        String summary = abbreviate(value, 700);
        if (summary.isBlank()) return;
        ObjectNode item = result.addObject();
        item.put("evidenceKey", key(source.type(), source.id(), path, summary));
        item.put("sourceType", source.type()); item.put("sourceId", source.id());
        item.put("label", sourceLabel(source.type()) + " · " + (label == null ? "확인된 근거" : label));
        item.put("summary", summary); item.put("value", summary);
        item.put("sourcePath", sourceLabel(source.type()) + " · " + path.replace('/', ' ').strip());
        if (source.generatedAt() != null) item.put("asOf", source.generatedAt().toString());
        if (actualQuote != null && !actualQuote.isBlank()) item.put("actualQuote", abbreviate(actualQuote, 500));
        if (respondentIds != null) item.set("respondentIds", respondentIds.deepCopy());
        if ("MARKET_INTERVIEW".equals(source.type()))
            item.put("limitation", "가상 정성 탐색이며 실제 고객 조사나 모집단 결과가 아닙니다.");
    }

    private String key(String type, String id, String path, String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest((type + "|" + id + "|" + path + "|" + value).getBytes(StandardCharsets.UTF_8));
            return "EV-" + HexFormat.of().formatHex(digest).substring(0, 24);
        } catch (Exception failure) { throw new IllegalStateException(failure); }
    }

    private String humanize(String key) {
        return key.replaceAll("([a-z0-9])([A-Z])", "$1 $2").replace('_', ' ').strip();
    }

    private String abbreviate(String value, int maximum) {
        String text = value == null ? "" : value.strip();
        return text.length() <= maximum ? text : text.substring(0, maximum - 1) + "…";
    }

    private String sourceLabel(String type) {
        return new LinkedHashMap<>(Map.ofEntries(
            Map.entry("PROJECT", "프로젝트"), Map.entry("CURRENT_CONCEPT", "현재 사업안"),
            Map.entry("MARKET", "시장 분석"), Map.entry("BUSINESS_MODEL", "비즈니스 모델"),
            Map.entry("MARKET_INTERVIEW", "시장 인터뷰"), Map.entry("MARKETING_STRATEGY", "마케팅 전략"),
            Map.entry("LAUNCH_TECHNOLOGY", "기술 분석"), Map.entry("LAUNCH_OPERATIONS", "운영 분석"),
            Map.entry("FINANCE", "재무 분석"), Map.entry("FINANCE_REPORT", "재무 분석 보고서"),
            Map.entry("LEGAL", "법률·규제"))).getOrDefault(type, type);
    }
}
