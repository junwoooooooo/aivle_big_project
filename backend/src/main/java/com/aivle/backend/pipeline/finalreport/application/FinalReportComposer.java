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

    public ArrayNode manifest(List<ReportSource> sources) {
        ArrayNode result = mapper.createArrayNode();
        sources.stream().sorted(java.util.Comparator.comparing(ReportSource::type)).forEach(source -> {
            ObjectNode item = result.addObject();
            item.put("type", source.type());
            item.put("id", source.id());
            if (source.version() != null) item.put("version", source.version());
            if (source.revision() != null) item.put("revision", source.revision());
            if (source.hash() != null) item.put("resultHash", source.hash());
            if (source.generatedAt() != null) item.put("generatedAt", source.generatedAt().toString());
        });
        return result;
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
        section(sections, "1", "Executive Summary", sources, "PROJECT", "IDEA", "SELECTED_CONCEPT");
        section(sections, "2", "사업 개요 및 기획", sources, "IDEA", "SELECTED_CONCEPT", "LEGAL");
        section(sections, "3", "시장 및 사업성 검증", sources, "MARKET", "BUSINESS_MODEL");
        section(sections, "4", "출시 준비", sources, "TECH_OPS", "FINANCE", "FINANCE_REPORT");
        section(sections, "5", "가상 인터뷰 결과", sources, "TWIN_SURVEY");
        section(sections, "6", "마케팅 전략", sources, "MARKETING", "MARKETING_ASSETS");
        section(sections, "7", "주요 위험 및 대응", sources, "LEGAL", "MARKET", "TECH_OPS", "FINANCE");
        section(sections, "8", "종합 판단 및 권고사항", sources, "MARKET", "BUSINESS_MODEL", "FINANCE", "TWIN_SURVEY");
        report.put("caveat", "이 문서는 각 업무 단계의 저장된 정본 결과를 결정적으로 편집한 것입니다. 자료가 없는 항목은 추정하지 않습니다.");
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
                item.put("label", "자료 없음 · 미완료");
            } else {
                item.put("status", "AVAILABLE");
                item.put("sourceId", source.id());
                item.set("data", source.data());
            }
        }
    }

    public record ReportSource(String type, String id, Integer version, Integer revision,
                               String hash, Instant generatedAt, JsonNode data) {}
}
