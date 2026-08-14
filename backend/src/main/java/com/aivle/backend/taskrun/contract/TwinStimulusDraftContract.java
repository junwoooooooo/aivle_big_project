package com.aivle.backend.taskrun.contract;

import com.aivle.backend.taskrun.integration.InternalAiExecutionClient.ExecutionFailure;
import java.util.HashSet;
import java.util.Set;
import tools.jackson.databind.JsonNode;

/**
 * TWIN_STIMULUS_DRAFT 결과 계약. {@link TwinSurveyContract} 의 앞 단계다.
 *
 * <p><b>여기서 지키는 것은 스키마만이 아니다.</b> 초안이 그대로 조사 입력이 되므로,
 * 조사 쪽이 거절할 모양이 초안 단계에서 통과하면 사용자는 「초안은 만들어졌는데
 * 실행은 거절되는」 막다른 길을 본다. 그래서 조사 입력의 제약을 여기서 미리 건다:
 *
 * <ol>
 *   <li><b>양쪽 속성 이름이 같고 딱 하나다</b> — 다속성 경합은 측정 한계 이하다.</li>
 *   <li><b>가격은 양쪽이 같다</b> — 가격이 걸리면 지불의사가 되고, 그 유형은
 *       계기를 바꾸면 방향까지 뒤집히는 것이 실측돼 제공하지 않는다.</li>
 *   <li><b>부동소수점이 없다</b> — 가격은 원 단위 정수다.</li>
 * </ol>
 *
 * <p>{@code dropped} 는 게이트가 버린 후보다. <b>비어 있어도 된다</b> — 버린 것이 없다는
 * 뜻이지 경계가 없다는 뜻이 아니다.
 */
public final class TwinStimulusDraftContract {

    private static final Set<String> ENVELOPE = Set.of("situation", "pairs", "dropped");
    private static final Set<String> PAIR = Set.of("pairId", "axis", "rationale", "X", "Y");
    private static final Set<String> SIDE = Set.of("label", "attrs", "priceKrw");
    private static final Set<String> DROPPED = Set.of("axis", "taskType", "reason");
    private static final int PAIRS_MAX = 4;

    private TwinStimulusDraftContract() { }

    public static void validate(JsonNode result) {
        exact(result, ENVELOPE);
        text(result, "situation");

        JsonNode pairs = result.get("pairs");
        if (pairs == null || !pairs.isArray() || pairs.isEmpty() || pairs.size() > PAIRS_MAX) invalid();
        Set<String> seen = new HashSet<>();
        for (JsonNode pair : pairs) {
            if (!seen.add(pair(pair))) invalid();
        }

        JsonNode dropped = result.get("dropped");
        if (dropped == null || !dropped.isArray()) invalid();
        for (JsonNode item : dropped) {
            exact(item, DROPPED);
            for (String field : DROPPED) text(item, field);
        }
    }

    /** @return pairId — 호출자가 중복을 센다. */
    private static String pair(JsonNode pair) {
        exact(pair, PAIR);
        String pairId = text(pair, "pairId");
        String axis = text(pair, "axis");
        text(pair, "rationale");

        JsonNode x = side(pair.get("X"), axis);
        JsonNode y = side(pair.get("Y"), axis);
        // 가격이 양쪽 같아야 우열형이다. 다르면 지불의사가 되고, 그건 못 판다.
        if (!x.get("priceKrw").equals(y.get("priceKrw"))) invalid();
        // 두 값이 같으면 잴 차이가 없다(음성대조 자극이다).
        if (x.get("attrs").get(axis).asText().equals(y.get("attrs").get(axis).asText())) invalid();
        return pairId;
    }

    private static JsonNode side(JsonNode side, String axis) {
        exact(side, SIDE);
        text(side, "label");
        JsonNode attrs = side.get("attrs");
        // 속성은 **정확히 하나**이고 그 이름이 axis 다. 둘이면 다속성 경합이다.
        if (attrs == null || !attrs.isObject() || !Set.copyOf(attrs.propertyNames()).equals(Set.of(axis))) invalid();
        text(attrs, axis);
        JsonNode price = side.get("priceKrw");
        // ⚠ 실수는 여기서 막는다 — 통과시키면 canonical hash 가 런타임에만 터진다.
        if (price == null || (!price.isNull() && (!price.isIntegralNumber() || price.asLong() < 0))) invalid();
        return side;
    }

    // ── 원시 검사 ────────────────────────────────────────────────────────
    private static void exact(JsonNode value, Set<String> fields) {
        if (value == null || !value.isObject() || !Set.copyOf(value.propertyNames()).equals(fields)) invalid();
    }
    private static String text(JsonNode value, String field) {
        JsonNode item = value == null ? null : value.get(field);
        if (item == null || !item.isTextual() || item.asText().isBlank()) invalid();
        return item.asText();
    }
    private static void invalid() {
        throw new ExecutionFailure("RESULT_SCHEMA_INVALID", "RESULT_FIELD_CONSTRAINT_VIOLATION", false);
    }
}
