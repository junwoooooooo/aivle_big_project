package com.aivle.backend.pipeline.refinement;

import com.aivle.backend.pipeline.market.BmPlanPreparationService;
import com.aivle.backend.pipeline.conceptportfolio.selection.api.ConceptPortfolioSelectionApiModels;
import com.aivle.backend.pipeline.conceptportfolio.selection.application.ConceptPortfolioSelectionService;
import com.aivle.backend.pipeline.conceptportfolio.selection.domain.HypothesisValueContract;
import com.aivle.backend.pipeline.conceptportfolio.selection.domain.PortfolioHypothesisType;
import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * 계약을 통과한 제안을 <b>실제로 적용</b>한다. 루프의 4단계다.
 *
 * <p>제안은 세 갈래로 갈린다. 갈래마다 <b>가는 문이 다르다</b>:
 * <ul>
 *   <li><b>가설 7개</b> — {@code CONFIRM_HYPOTHESES} 로 간다. 법률 민감이라 이 문을 지나야
 *       {@code deltaLegalRequired} 가 서고 {@code DELTA_LEGAL} 이 따라붙는다.</li>
 *   <li><b>BM 4칸</b> — {@code bm_plan_preparations} 에 저장한다. 법률과 무관하다.</li>
 *   <li><b>그 밖</b>({@code featureSet}·{@code targetUsers}) — <b>오버레이</b>로 남긴다
 *       ({@link ConceptRefinementFinal}). 가설도 BM 계획도 아니라 갈 문이 없지만,
 *       컨셉 원본을 덮는 대신 최종 확정 때 <b>시드 스냅샷에만</b> 얹는다.
 *       컨셉 원본은 캐노니컬 해시와 계보 때문에 덮지 않는다.</li>
 * </ul>
 *
 * <p>⚠ <b>셋째 갈래를 조용히 버리지 않는다.</b> 예전에는 이 갈래가 반환만 되고 호출자가
 * 그것을 버려서, 사용자는 「제안은 통과했는데 아무것도 안 바뀌었다」를 보고 이유를 알 수
 * 없었다. 지금은 오버레이로 남아 최종 컨셉에 실제로 반영된다.
 */
@Service
public class ConceptRefinementApplyService {
    private static final Logger log = LoggerFactory.getLogger(ConceptRefinementApplyService.class);

    /**
     * 제안의 칸 이름 → 가설 종류.
     *
     * <p>드리프트 계약({@code ConceptDriftContract.REFINABLE_FIELDS})의 일곱 칸 중
     * <b>다섯</b>만 가설로 갈 문이 있다. {@code featureSet}·{@code targetUsers} 는 없다.
     */
    private static final Map<String, String> HYPOTHESIS_OF_FIELD = Map.of(
        "price", "PRICE",
        "channels", "CHANNELS",
        "differentiators", "DIFFERENTIATORS",
        "targetRegion", "TARGET_REGION",
        "revenueModel", "REVENUE_MODEL",
        "preMarketSomShare", "PRE_MARKET_SOM_SHARE",
        "preMarketSom", "PRE_MARKET_SOM");

    /** BM 계획으로 가는 칸. 이름은 계획 저장소가 쓰는 것과 같아야 한다. */
    private static final List<String> BM_PLAN_FIELDS = List.of(
        "keyActivities", "keyResources", "keyPartners", "customerRelationships");

    /**
     * 오버레이로 갈 칸 — <b>시드 스냅샷에만</b> 얹힌다.
     *
     * <p>드리프트 계약의 일곱 칸 중 가설로 갈 문이 있는 것은 다섯이고, 이 둘은 없다.
     * ⚠ 이름은 AI 쪽 {@code _OVERLAY_SLOTS}({@code selection_service.py})와 <b>짝</b>이다 —
     * 한쪽만 고치면 오버레이가 조용히 안 얹힌다.
     */
    private static final List<String> OVERLAY_FIELDS = List.of("targetUsers", "featureSet");

    /**
     * 이 칸을 고치면 <b>법률을 다시 봐야 하는가</b>.
     *
     * <p>사람이 고른 것만 적용하는 문({@code ConceptRefinementService.decide})이 라운드를
     * 언제 닫을지 정할 때 쓴다. 가설 칸이 하나라도 섞이면 {@code confirm()} 이 델타 법률을
     * 걸고 <b>그 결과가</b> 라운드를 닫는다. 안 섞이면 다시 볼 법이 없으니 그 자리에서 닫는다.
     */
    public static boolean isHypothesisField(String field) {
        return HYPOTHESIS_OF_FIELD.containsKey(field);
    }

    private final ConceptPortfolioSelectionService selectionService;
    private final BmPlanPreparationService bmPlans;
    private final ConceptRefinementFinalRepository finals;
    private final ObjectMapper mapper;

    public ConceptRefinementApplyService(ConceptPortfolioSelectionService selectionService,
            BmPlanPreparationService bmPlans, ConceptRefinementFinalRepository finals,
            ObjectMapper mapper) {
        this.selectionService = selectionService;
        this.bmPlans = bmPlans;
        this.finals = finals;
        this.mapper = mapper;
    }

    /**
     * 한 라운드의 통과분을 적용한다.
     *
     * <p>⚠ <b>순서가 있다.</b> BM 계획을 <b>먼저</b> 저장하고 가설 확정을 뒤에 건다.
     * 가설 확정은 {@code staleDependents()} 를 부르고 그것이 시장조사 시드를 STALE 로 만든다 —
     * 계획을 뒤에 저장하면 이번 라운드의 계획이 이미 STALE 이 된 시드 뒤에 붙는다.
     *
     * @return 적용하지 못한 제안(문이 없는 칸). 최종 화면이 「못 적용한 것」으로 보인다.
     */
    @Transactional
    public List<JsonNode> apply(Long ownerId, Long projectId, Long selectionId,
            JsonNode proposals, String idempotencyKey) {
        ObjectNode hypothesisEdits = mapper.createObjectNode();
        Map<String, JsonNode> planEdits = new LinkedHashMap<>();
        ObjectNode overlayEdits = mapper.createObjectNode();
        List<JsonNode> unapplied = new java.util.ArrayList<>();

        for (JsonNode proposal : proposals) {
            String field = proposal.path("fieldKey").asText();
            JsonNode value = proposal.path("proposedValue");
            String hypothesis = HYPOTHESIS_OF_FIELD.get(field);
            if (hypothesis != null) {
                try {
                    hypothesisEdits.set(hypothesis, HypothesisValueContract.canonicalize(
                        mapper, PortfolioHypothesisType.valueOf(hypothesis), value));
                } catch (IllegalArgumentException invalid) {
                    throw new BusinessException(ErrorCode.HYPOTHESIS_VALUE_INVALID);
                }
            } else if (BM_PLAN_FIELDS.contains(field)) {
                planEdits.put(field, value);
            } else if (OVERLAY_FIELDS.contains(field)) {
                overlayEdits.set(field, value);
            } else {
                log.info("Refinement proposal has no place to land selectionId={} field={}",
                    selectionId, field);
                unapplied.add(proposal);
            }
        }

        if (!overlayEdits.isEmpty()) {
            // 라운드마다 다른 칸이 올 수 있어 **누적**한다. 덮어쓰면 앞 라운드가 고친 칸이 사라진다.
            ConceptRefinementFinal row = finals.findBySelectionIdAndDeletedAtIsNull(selectionId)
                .orElseGet(() -> finals.save(ConceptRefinementFinal.of(projectId, selectionId)));
            ObjectNode merged = row.getOverlayJson() == null ? mapper.createObjectNode()
                : (ObjectNode) mapper.readTree(row.getOverlayJson());
            overlayEdits.properties().forEach(entry -> merged.set(entry.getKey(), entry.getValue()));
            row.mergeOverlay(mapper.writeValueAsString(merged));
            finals.save(row);
        }

        if (!planEdits.isEmpty()) {
            ObjectNode plan = mapper.createObjectNode();
            planEdits.forEach(plan::set);
            // 제약(비용 3칸)은 다듬기가 건드리지 않는다 — 사용자가 쓴 정수다.
            bmPlans.save(projectId, ownerId, plan, null);
        }

        if (!hypothesisEdits.isEmpty()) {
            // 여기서 법률이 따라붙는다. 우회하지 않고 순서대로 통과한다.
            selectionService.confirm(ownerId, projectId, selectionId,
                new ConceptPortfolioSelectionApiModels.ConfirmHypothesesRequest(
                    hypothesisEdits, true, idempotencyKey));
        }
        return unapplied;
    }
}
