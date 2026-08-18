package com.aivle.backend.pipeline.finalreport.application;

import com.aivle.backend.pipeline.selection.application.SnapshotHasher;
import com.aivle.backend.project.entity.Project;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class FinalReportComposer {
    private final ObjectMapper mapper;
    private final SnapshotHasher hasher;

    public FinalReportComposer(ObjectMapper mapper) {
        this.mapper = mapper;
        this.hasher = new SnapshotHasher(mapper);
    }

    public String hash(JsonNode manifest) { return hasher.hash(manifest); }

    public ObjectNode manifest(JsonNode currentConceptBinding, List<ReportSource> sources) {
        ObjectNode manifest = mapper.createObjectNode();
        manifest.put("schemaVersion", 2);
        manifest.set("currentConcept", currentConceptBinding == null ? mapper.nullNode() : currentConceptBinding.deepCopy());
        ArrayNode result = manifest.putArray("sources");
        sources.stream().sorted(java.util.Comparator.comparing(ReportSource::type)).forEach(source -> {
            ObjectNode item = result.addObject();
            item.put("type", source.type());
            item.put("id", source.id());
            if (source.version() != null) item.put("version", source.version());
            if (source.revision() != null) item.put("revision", source.revision());
            if (source.hash() != null) item.put("resultHash", source.hash());
            if (source.generatedAt() != null) item.put("generatedAt", source.generatedAt().toString());
            JsonNode sourceMetadata = source.data().path("_sourceMetadata");
            if (sourceMetadata.isObject()) item.set("metadata", sourceMetadata.deepCopy());
        });
        return manifest;
    }

    public ArrayNode manifest(List<ReportSource> sources) {
        return (ArrayNode) manifest(null, sources).path("sources");
    }

    public ObjectNode compose(Project project, int version, Instant generatedAt, List<ReportSource> sources) {
        ObjectNode report = mapper.createObjectNode();
        report.put("title", "사업 타당성 검토 보고서");
        ObjectNode metadata = report.putObject("metadata");
        metadata.put("projectName", project.getTitle());
        metadata.put("industryCategory", project.getIndustryCategory() == null ? "자료 없음" : project.getIndustryCategory());
        metadata.put("projectDescription", project.getDescription() == null ? "자료 없음" : project.getDescription());
        metadata.put("generatedAt", generatedAt.toString());
        metadata.put("analysisBaseAt", generatedAt.toString());
        metadata.put("version", version);

        ArrayNode sections = report.putArray("sections");
        section(sections, "1", "핵심 요약", sources, "PROJECT", "CURRENT_CONCEPT");
        section(sections, "2", "현재 확정 사업안", sources, "CURRENT_CONCEPT");
        section(sections, "3", "사업성 검증", sources, "MARKET", "BUSINESS_MODEL");
        section(sections, "4", "시장 인터뷰", sources, "MARKET_INTERVIEW");
        section(sections, "5", "마케팅 실행", sources, "MARKETING", "MARKETING_ASSETS");
        section(sections, "6", "출시 준비", sources, "LAUNCH_TECHNOLOGY", "LAUNCH_OPERATIONS", "FINANCE", "FINANCE_REPORT");
        section(sections, "7", "주요 위험·근거·주의사항", sources, "MARKET", "MARKET_INTERVIEW",
            "LAUNCH_TECHNOLOGY", "LAUNCH_OPERATIONS", "FINANCE");
        section(sections, "8", "종합 판단 및 다음 행동", sources, "CURRENT_CONCEPT", "MARKET", "BUSINESS_MODEL",
            "MARKET_INTERVIEW", "MARKETING", "LAUNCH_TECHNOLOGY", "LAUNCH_OPERATIONS", "FINANCE");
        report.put("caveat", "이 보고서는 현재 확정된 사업안과 각 단계의 현재 유효 결과를 종합한 의사결정 지원 자료입니다. 시장 인터뷰는 AI 가상 참여자를 활용한 탐색이며 실제 소비자 조사 결과를 의미하지 않습니다. 실행하지 않은 단계의 내용은 추정하여 채우지 않습니다.");
        return report;
    }

    private void section(ArrayNode sections, String number, String title, List<ReportSource> sources, String... types) {
        ObjectNode section = sections.addObject();
        section.put("number", number);
        section.put("title", title);
        ArrayNode items = section.putArray("sources");
        for (String type : types) {
            ReportSource source = sources.stream().filter(value -> value.type().equals(type)).findFirst().orElse(null);
            ObjectNode item = items.addObject();
            item.put("type", type);
            if (source == null) {
                item.put("status", "MISSING");
                item.put("label", missingLabel(type));
            } else {
                item.put("status", "AVAILABLE");
                item.set("data", source.data());
            }
        }
    }

    private String missingLabel(String type) {
        return switch (type) {
            case "MARKET_INTERVIEW" -> "아직 시장 인터뷰를 진행하지 않았습니다.";
            case "MARKETING", "MARKETING_ASSETS" -> "마케팅 콘텐츠가 아직 확정되지 않았습니다.";
            case "LAUNCH_TECHNOLOGY" -> "기술 준비 분석이 아직 없습니다.";
            case "LAUNCH_OPERATIONS" -> "운영 준비 분석이 아직 없습니다.";
            case "FINANCE", "FINANCE_REPORT" -> "현재 유효한 재무 분석 결과가 없습니다.";
            default -> "현재 유효한 결과가 없습니다.";
        };
    }

    public record ReportSource(String type, String id, Integer version, Integer revision,
                               String hash, Instant generatedAt, JsonNode data) {}
}
