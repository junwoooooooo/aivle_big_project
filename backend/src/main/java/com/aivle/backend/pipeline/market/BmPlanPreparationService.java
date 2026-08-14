package com.aivle.backend.pipeline.market;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * BM 실행 계획 — 사용자가 캔버스 앞에서 채우는 칸을 보관하고 정규화한다.
 *
 * <p><b>정규화가 한 곳에 있어야 하는 이유:</b> 「비었다」의 정의가 층마다 다르면 화면은
 * 「썼다」고 하고 AI 는 「없다」고 읽는다. 빈 문자열·공백·빈 배열을 <b>여기서 한 번</b>
 * 떨어뜨리고, 그 뒤로는 「있으면 사용자가 쓴 것」이 성립한다.
 */
@Service
public class BmPlanPreparationService {

    /**
     * 화면이 채우는 칸. <b>컨셉 계약이 주지 않는 넷</b>이다(입구계약서 §1).
     *
     * <p>수익모델·채널·차별점은 여기 없다 — 가설 4({@code _hypotheses_v2})가 이미 사용자
     * 승인을 거친다. 두 번 물으면 사용자가 아이디어 단계에서 친 것을 다시 치게 된다.
     *
     * <p>⚠ 키 이름은 AI 쪽 {@code bm_adapter.PLAN_FIELDS} 와 같아야 한다. 다르면
     * 조용히 안 실린다 — 예외도 로그도 안 남는다.
     */
    static final List<String> LIST_KEYS = List.of("key_activities", "key_resources", "key_partners");
    static final String SENTENCE_KEY = "customer_relationship";

    /** 비용 구조 칸의 재료. <b>정수만</b> — taskInput 부동소수점 금지. */
    static final List<String> CONSTRAINT_KEYS = List.of("budget_krw", "months", "team");

    private final BmPlanPreparationRepository preparations;
    private final ObjectMapper mapper;

    public BmPlanPreparationService(BmPlanPreparationRepository preparations, ObjectMapper mapper) {
        this.preparations = preparations;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public PlanView current(Long projectId) {
        return preparations.findByProjectIdAndDeletedAtIsNull(projectId)
            .map(this::view)
            .orElseGet(() -> new PlanView(mapper.createObjectNode(),
                mapper.createObjectNode(), 0));
    }

    @Transactional
    public PlanView save(Long projectId, Long userId, JsonNode plan, JsonNode constraints) {
        String planJson = normalizePlan(plan).toString();
        String constraintJson = normalizeConstraints(constraints).toString();
        BmPlanPreparation saved = preparations.findByProjectIdAndDeletedAtIsNull(projectId)
            .map(existing -> {
                existing.update(planJson, constraintJson, userId);
                return existing;
            })
            .orElseGet(() -> preparations.save(BmPlanPreparation.create(
                UUID.randomUUID().toString(), projectId, planJson, constraintJson, userId)));
        return view(saved);
    }

    /** BM 실행이 읽는 자리. 준비가 없으면 {@code empty} — 그때는 기존 경로가 그대로 돈다. */
    @Transactional(readOnly = true)
    public Optional<PlanView> forExecution(Long projectId) {
        return preparations.findByProjectIdAndDeletedAtIsNull(projectId).map(this::view);
    }

    private PlanView view(BmPlanPreparation entity) {
        return new PlanView(readObject(entity.getPlanJson()),
            readObject(entity.getConstraintJson()), entity.getRevision());
    }

    private ObjectNode readObject(String json) {
        JsonNode parsed = mapper.readTree(json);
        return parsed != null && parsed.isObject() ? (ObjectNode) parsed : mapper.createObjectNode();
    }

    /**
     * 목록 셋 + 문장 하나. <b>빈 값은 칸 자체를 만들지 않는다.</b>
     *
     * <p>빈 배열·빈 문자열을 저장하면 「사용자가 안 썼다」와 「사용자가 비웠다」가 같아지고,
     * 뒷단(견본 {@code _bm_plan}·컨셉 파생)이 채울 기회를 조용히 뺏는다.
     */
    ObjectNode normalizePlan(JsonNode raw) {
        ObjectNode out = mapper.createObjectNode();
        if (raw == null || !raw.isObject()) return out;
        for (String key : LIST_KEYS) {
            JsonNode value = raw.get(key);
            if (value == null || !value.isArray()) continue;
            var items = mapper.createArrayNode();
            for (JsonNode item : value) {
                String text = item.isTextual() ? item.stringValue().trim() : "";
                if (!text.isEmpty()) items.add(text);
            }
            if (!items.isEmpty()) out.set(key, items);
        }
        JsonNode sentence = raw.get(SENTENCE_KEY);
        if (sentence != null && sentence.isTextual() && !sentence.stringValue().isBlank()) {
            out.put(SENTENCE_KEY, sentence.stringValue().trim());
        }
        return out;
    }

    /**
     * 예산·기간·인원. <b>정수가 아니면 400 으로 돌려준다.</b>
     *
     * <p>안 막으면 {@code CanonicalInputHasher} 가 감싸이지 않은 예외를 던져 사용자에게
     * <b>500</b> 으로 나간다. 그리고 반올림해서 통과시키지 않는다 — 사용자가 쓰지 않은
     * 정밀도를 지어내는 것이기 때문이다.
     */
    ObjectNode normalizeConstraints(JsonNode raw) {
        ObjectNode out = mapper.createObjectNode();
        if (raw == null || !raw.isObject()) return out;
        for (String key : CONSTRAINT_KEYS) {
            JsonNode value = raw.get(key);
            if (value == null || value.isNull()) continue;
            if (!value.isIntegralNumber()) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    key + " 는 정수여야 합니다 — 받은 값: " + value.toString());
            }
            long number = value.longValue();
            if (number < 0) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    key + " 는 0 이상이어야 합니다 — 받은 값: " + number);
            }
            out.put(key, number);
        }
        return out;
    }

    /** 화면·실행이 함께 쓰는 모양. {@code revision} 0 은 「아직 저장한 적 없다」다. */
    public record PlanView(ObjectNode plan, ObjectNode constraints, int revision) { }
}
