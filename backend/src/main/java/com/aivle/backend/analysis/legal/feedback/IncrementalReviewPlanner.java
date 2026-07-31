package com.aivle.backend.analysis.legal.feedback;

import com.aivle.backend.analysis.legal.entity.LegalApplicability;
import com.aivle.backend.analysis.legal.entity.LegalCategory;
import com.aivle.backend.analysis.legal.entity.LegalFinding;
import com.aivle.backend.document.entity.StructuredPlanSection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.*;

/**
 * 증분 재검토 계획 산출(순수 로직).
 * 직전 리뷰 finding의 sourceSectionCodes를 역인덱스로 뒤집어,
 * 변경된 섹션에 걸린 범주 + 새 확정 정보에 연결된 범주만 재실행 대상으로 고른다.
 */
@Component
public class IncrementalReviewPlanner {
    private static final Logger log = LoggerFactory.getLogger(IncrementalReviewPlanner.class);

    private final ObjectMapper objectMapper;

    public IncrementalReviewPlanner(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public record IncrementalPlan(
        List<String> changedSections,
        Set<LegalCategory> rerunCategories,
        Set<LegalCategory> carriedCategories,
        boolean degradedToFull
    ) {}

    /**
     * @param newSections            새 plan 버전의 섹션
     * @param parentSections         직전 리뷰가 본 plan 버전의 섹션
     * @param parentFindings         직전 리뷰의 finding (승계본 포함 — sourceSectionCodesJson 보존됨)
     * @param newFactCategoriesJson  직전 리뷰 이후 추가된 확정 정보의 질문 categoriesJson 목록
     */
    public IncrementalPlan plan(
        List<StructuredPlanSection> newSections,
        List<StructuredPlanSection> parentSections,
        List<LegalFinding> parentFindings,
        List<String> newFactCategoriesJson
    ) {
        Map<String, String> parentTexts = new HashMap<>();
        for (StructuredPlanSection section : parentSections) {
            parentTexts.put(section.getSectionCode().name(), section.getSourceText());
        }
        List<String> changedSections = new ArrayList<>();
        for (StructuredPlanSection section : newSections) {
            String code = section.getSectionCode().name();
            if (!Objects.equals(section.getSourceText(), parentTexts.get(code))) {
                changedSections.add(code);
            }
        }

        // 역인덱스: sectionCode → 그 섹션을 근거로 삼은 범주들
        Map<String, Set<LegalCategory>> sectionToCategories = new HashMap<>();
        for (LegalFinding finding : parentFindings) {
            for (String code : parseCodes(finding.getSourceSectionCodesJson())) {
                sectionToCategories.computeIfAbsent(code, key -> EnumSet.noneOf(LegalCategory.class))
                    .add(finding.getCategory());
            }
        }

        Set<LegalCategory> rerun = EnumSet.noneOf(LegalCategory.class);
        for (String code : changedSections) {
            Set<LegalCategory> hit = sectionToCategories.get(code);
            if (hit == null) {
                log.info("변경 섹션 {}은 역인덱스에 없어 재실행 범주에 기여하지 않습니다", code);
            } else {
                rerun.addAll(hit);
            }
        }

        for (String categoriesJson : newFactCategoriesJson) {
            List<LegalCategory> linked = parseCategories(categoriesJson);
            if (linked.isEmpty()) {
                // 질문에 범주 연결이 없으면 결정론적 안전 상한: 정보 부족 범주 전부
                for (LegalFinding finding : parentFindings) {
                    if (finding.getApplicability() == LegalApplicability.INSUFFICIENT_INFORMATION) {
                        rerun.add(finding.getCategory());
                    }
                }
            } else {
                rerun.addAll(linked);
            }
        }

        boolean degraded = rerun.isEmpty();
        if (degraded) {
            log.info("증분 재검토 대상 범주가 없어 FULL로 강등합니다 (changedSections={})", changedSections);
        }
        Set<LegalCategory> carried = EnumSet.allOf(LegalCategory.class);
        carried.removeAll(rerun);
        return new IncrementalPlan(
            List.copyOf(changedSections), rerun, carried, degraded);
    }

    private List<String> parseCodes(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (JacksonException exception) {
            return List.of();
        }
    }

    private List<LegalCategory> parseCategories(String json) {
        List<LegalCategory> categories = new ArrayList<>();
        for (String name : parseCodes(json)) {
            try {
                categories.add(LegalCategory.valueOf(name));
            } catch (IllegalArgumentException ignored) {
                // 알 수 없는 범주명은 무시
            }
        }
        return categories;
    }
}
