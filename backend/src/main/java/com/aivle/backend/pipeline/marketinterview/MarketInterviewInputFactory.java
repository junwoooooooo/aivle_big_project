package com.aivle.backend.pipeline.marketinterview;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.pipeline.market.BmPlanPreparationService;
import com.aivle.backend.pipeline.marketseed.domain.MarketAnalysisSeedSnapshot;
import com.aivle.backend.pipeline.conceptportfolio.selection.domain.ConceptPortfolioSelection;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Component
public class MarketInterviewInputFactory {
    static final String INPUT_CONTRACT = "market-interview-input-v2";
    static final String INPUT_SCHEMA_VERSION = "2.0";
    private final ObjectMapper mapper;

    public MarketInterviewInputFactory(ObjectMapper mapper) { this.mapper = mapper; }

    public String build(MarketAnalysisSeedSnapshot seed, ConceptPortfolioSelection selection,
            BmPlanPreparationService.PlanView bm, int sampleSize) {
        if (sampleSize != 20 && sampleSize != 40 && sampleSize != 80) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "표본 크기는 20, 40, 80 중 하나여야 합니다.");
        }
        JsonNode snapshot;
        try { snapshot = mapper.readTree(seed.getSnapshotJson()); }
        catch (RuntimeException invalidJson) {
            throw new BusinessException(ErrorCode.MODULE_INPUT_STALE,
                "현재 Market Seed를 시장 인터뷰 입력으로 읽을 수 없습니다.");
        }
        if (snapshot == null || !snapshot.isObject()
                || !"market-analysis-seed-snapshot-v1".equals(snapshot.path("contract").asText())
                || !"2.0".equals(snapshot.path("schemaVersion").asText())
                || !snapshot.path("selectedConcept").isObject()
                || !snapshot.path("finalHypotheses").isObject()) {
            throw new BusinessException(ErrorCode.MODULE_INPUT_STALE,
                "현재 Market Seed의 시장 인터뷰 입력 계약이 올바르지 않습니다.");
        }
        ObjectNode root = mapper.createObjectNode();
        root.put("contract", INPUT_CONTRACT);
        root.put("schemaVersion", INPUT_SCHEMA_VERSION);
        root.put("synthetic", true);
        root.put("sampleSize", sampleSize);
        ObjectNode source = root.putObject("source");
        source.put("marketSeedSnapshotId", seed.getId());
        source.put("selectionId", selection.getId());
        source.put("selectionRevision", selection.getHypothesisRevision());
        source.put("marketSeedSnapshotHash", seed.getSnapshotHash());
        source.put("bmPlanRevision", bm.revision());
        root.set("selectedConcept", snapshot.get("selectedConcept"));
        root.set("validatedHypotheses", snapshot.get("finalHypotheses"));
        ObjectNode businessModel = root.putObject("businessModel");
        businessModel.set("plan", bm.plan());
        businessModel.set("constraints", bm.constraints());
        root.putArray("boundaries")
            .add("가상의 고객 관점 시뮬레이션이며 실제 고객 조사나 시장 근거가 아니다.")
            .add("통계, 대표성, 구매율 또는 모집단 비율을 추론하지 않는다.")
            .add("결과는 사업안을 자동으로 변경하지 않는다.");
        return root.toString();
    }
}
