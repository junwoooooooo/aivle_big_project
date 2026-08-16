package com.aivle.backend.pipeline.market;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.pipeline.currentconcept.CurrentConceptSourceResolver.Source;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * TWIN_SURVEY 의 taskInput 을 만든다.
 *
 * <p><b>{@code textContents} 봉투를 쓰지 않는다.</b> 그것은 시장조사 전용이고
 * ({@code ai/app/api/executions.py:150}), 트윈 쪽 입력 모델은 {@code extra="forbid"} 라
 * 없는 필드를 넣으면 400 이다. 그래서 봉투가 아니라 <b>계약 그대로</b> 보낸다.
 *
 * <p>대신 시장조사와 같은 지뢰를 하나 공유한다 — <b>부동소수점 금지</b>다.
 * {@code CanonicalInputHasher} 가 던지는 예외는 감싸이지 않아 사용자에게 500 으로 나가고,
 * 컴파일도 테스트도 그것을 못 잡는다. 트윈 입력은 설계상 실수가 없다(가격은 원 단위 정수,
 * 표본은 정수, 나머지는 문자열) — 그 설계가 지켜지는지를 <b>보내기 전에</b> 확인한다.
 */
@Component
public class TwinSurveyInputFactory {

    private final ObjectMapper mapper;

    public TwinSurveyInputFactory(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public String build(Source source, String situation, JsonNode pairs, int sampleSize) {
        JsonNode snapshot;
        try { snapshot = mapper.readTree(source.seed().getSnapshotJson()); }
        catch (RuntimeException invalidJson) {
            throw new BusinessException(ErrorCode.MODULE_INPUT_STALE,
                "현재 Market Seed를 트윈 패널 조사 입력으로 읽을 수 없습니다.");
        }
        if (snapshot == null || !snapshot.isObject()
                || !"market-analysis-seed-snapshot-v1".equals(snapshot.path("contract").asText())
                || !"2.0".equals(snapshot.path("schemaVersion").asText())
                || !snapshot.path("selectedConcept").isObject()
                || !snapshot.path("finalHypotheses").isObject()) {
            throw new BusinessException(ErrorCode.MODULE_INPUT_STALE,
                "현재 Market Seed의 트윈 패널 조사 입력 계약이 올바르지 않습니다.");
        }
        ObjectNode root = mapper.createObjectNode();
        root.put("contract", "twin-panel-survey-input-v1");
        root.put("schemaVersion", "1.0");
        root.put("synthetic", true);
        ObjectNode binding = root.putObject("source");
        binding.put("marketSeedSnapshotId", source.seed().getId());
        binding.put("selectionId", source.selection().getId());
        binding.put("selectionRevision", source.selection().getHypothesisRevision());
        binding.put("marketSeedSnapshotHash", source.seed().getSnapshotHash());
        binding.put("bmPlanRevision", source.bm().revision());
        ObjectNode concept = root.putObject("concept");
        concept.set("selectedConcept", snapshot.get("selectedConcept"));
        concept.set("validatedHypotheses", snapshot.get("finalHypotheses"));
        ObjectNode businessModel = concept.putObject("businessModel");
        businessModel.set("plan", source.bm().plan());
        businessModel.set("constraints", source.bm().constraints());
        root.put("situation", situation);
        root.set("pairs", pairs);
        root.put("sampleSize", sampleSize);
        root.putArray("boundaries")
            .add("AI 가상 패널의 정량 시뮬레이션이며 실제 소비자 설문조사 결과가 아니다.")
            .add("가상 패널 내 비교를 실제 시장 모집단의 비율이나 대표성으로 일반화하지 않는다.")
            .add("결과는 사업안이나 시장 근거를 자동으로 변경하지 않는다.");
        MarketResearchInputFactory.assertNoFloatingPoint(root, "input");
        return root.toString();
    }
}
