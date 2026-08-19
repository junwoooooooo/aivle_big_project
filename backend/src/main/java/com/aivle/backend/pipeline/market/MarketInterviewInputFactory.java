package com.aivle.backend.pipeline.market;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * MARKET_INTERVIEW 의 taskInput 을 만든다.
 *
 * <p><b>{@code textContents} 봉투를 쓰지 않는다.</b> 그것은 시장조사 전용이고, 인터뷰 쪽
 * 입력 모델은 {@code extra="forbid"} 라 없는 필드를 넣으면 400 이다. 계약 그대로 보낸다.
 *
 * <p>지뢰 하나를 시장조사와 공유한다 — <b>부동소수점 금지</b>다.
 * {@code CanonicalInputHasher} 가 던지는 예외는 감싸이지 않아 사용자에게 500 으로 나가고,
 * 컴파일도 단위 테스트도 그것을 못 잡는다. 인터뷰 입력은 설계상 실수가 없다(가격은 원 단위
 * 정수, 표본은 정수, 나머지는 문자열) — 그 설계가 지켜지는지를 <b>보내기 전에</b> 확인한다.
 *
 * <p>컨셉보드의 칸 집합도 여기서 못박는다. 화면이 보낸 것을 그대로 흘려보내면 AI 쪽
 * {@code extra="forbid"} 가 400 을 내는데, 그 400 은 「사용자가 무엇을 잘못했는지」를
 * 말해 주지 못한다.
 */
@Component
public class MarketInterviewInputFactory {

    private static final Set<String> BOARD_FIELDS = Set.of(
        "conceptName", "targetUsers", "problemScenario", "featureSet",
        "differentiators", "priceKrw");

    private static final List<String> TEXT_FIELDS = List.of(
        "conceptName", "targetUsers", "problemScenario", "differentiators");

    private final ObjectMapper mapper;

    public MarketInterviewInputFactory(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public String build(JsonNode conceptBoard, int sampleSize) {
        if (conceptBoard == null || !conceptBoard.isObject()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "컨셉보드가 없다");
        }
        if (!BOARD_FIELDS.equals(Set.copyOf(conceptBoard.propertyNames()))) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                "컨셉보드는 여섯 칸이다 — conceptName·targetUsers·problemScenario·"
                + "featureSet·differentiators·priceKrw");
        }
        for (String field : TEXT_FIELDS) {
            if (!conceptBoard.path(field).isTextual()) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST, field + " 는 문자열이다");
            }
        }
        if (conceptBoard.path("conceptName").asText().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                "컨셉 이름이 비어 있다 — 응답자에게 보일 자극이 아니다");
        }
        if (!conceptBoard.path("featureSet").isArray()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "featureSet 은 배열이다");
        }
        JsonNode price = conceptBoard.get("priceKrw");
        if (!price.isNull() && !price.isIntegralNumber()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                "가격은 원 단위 정수이거나 비어 있어야 한다");
        }

        ObjectNode root = mapper.createObjectNode();
        root.set("conceptBoard", conceptBoard);
        root.put("sampleSize", sampleSize);
        MarketResearchInputFactory.assertNoFloatingPoint(root, "input");
        return root.toString();
    }
}
