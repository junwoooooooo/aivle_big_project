package com.aivle.backend.journey;

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

    public String build(String situation, JsonNode pairs, int sampleSize) {
        ObjectNode root = mapper.createObjectNode();
        root.put("situation", situation);
        root.set("pairs", pairs);
        root.put("sampleSize", sampleSize);
        MarketResearchInputFactory.assertNoFloatingPoint(root, "input");
        return root.toString();
    }
}
