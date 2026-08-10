package com.aivle.backend.journey;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * MARKET_RESEARCH 의 taskInput 을 만든다.
 *
 * <p>두 가지를 동시에 지켜야 한다.
 *
 * <ol>
 *   <li><b>{@code textContents} 는 taskType 과 무관하게 필수다.</b>
 *       {@code ai/app/api/executions.py} 가 모든 요청에 대해 검사하고, 청크 해시가
 *       한 글자만 어긋나도 400 이다.</li>
 *   <li><b>부동소수점을 넣으면 런타임에 500 이 난다.</b>
 *       {@code CanonicalInputHasher:64} 가 float 를 거부하는데 그 예외는
 *       {@code TaskRunFailure} 로 감싸이지 않아 컨트롤러까지 올라간다 —
 *       컴파일도 테스트도 안 잡는다.</li>
 * </ol>
 *
 * <p>⚠ 「숫자를 아예 넣지 않는다」는 <b>불가능하다</b> — {@code index}·{@code characterCount}·
 * {@code totalCharacters} 가 정수로 들어가야 한다. 해셔는 정수를 허용하고 <b>부동소수점만</b>
 * 거부한다. 그래서 규칙은 「숫자 금지」가 아니라 <b>「부동소수점 금지」</b>이고,
 * 그것을 {@link #assertNoFloatingPoint} 가 보낸 뒤가 아니라 <b>보내기 전에</b> 확인한다.
 *
 * <p>컨셉 스냅샷에 실제로 float 가 있다(예: {@code 가정_침투율 0.005}). 그래서 컨셉은
 * <b>JSON 문자열로 직렬화해 {@code textContents} 안에</b> 넣는다 — 문자열 안의 숫자는
 * 해셔가 보지 않는다.
 */
@Component
public class MarketResearchInputFactory {

    private static final int CHUNK_CHARACTERS = 16_000;

    private final ObjectMapper mapper;

    public MarketResearchInputFactory(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 1단계 — 컨셉 전체를 문자열로 실어 보낸다.
     *
     * <p>{@code conceptId} 는 AI 쪽 {@code pipeline.CONCEPTS} 의 <b>이름표</b>다. 그 표가
     * 이름표 하나로 (컨셉 파일, 원장) 을 정하므로 백엔드는 {@code sourceRun} 을 모른다 —
     * 알 필요도 없다. 목록은 AI 가 정본이고 여기서는 문자열을 나르기만 한다.
     *
     * <p>{@code llmBudget} 없이 보내면 AI 쪽이 {@code Budget(total=0)} 으로 떨어져
     * 요약이 {@code BUDGET_EXHAUSTED} 로 조용히 빠진다. 요약은 최소 3회를 요구한다.
     */
    public String full(JsonNode concept, String conceptId, String asOf) {
        ObjectNode root = mapper.createObjectNode();
        root.set("textContents", textContents("concept", mapper.writeValueAsString(concept)));
        root.put("conceptId", conceptId);
        root.put("asOf", asOf);
        root.put("mode", "FULL");
        root.put("llmBudget", 3);
        return finish(root);
    }

    /**
     * 2단계 — <b>이름표 하나만</b> 넘긴다.
     *
     * <p>1단계 결과({@code MarketJoinData})를 그대로 실으면 그 안의 부동소수점 31개가
     * 해셔에서 터진다. AI 서버가 원장에서 직접 읽게 하는 편이 안전하고, 그러면
     * 결과가 <b>계약 경계를 안 넘는다</b>.
     *
     * <p>{@code sourceRun} 을 싣지 않는다 — 이전에는 1단계 결과의 {@code runId} 를 넘겼는데
     * 그것은 {@code taskAttemptId} 이지 {@code runs/} 밑 디렉터리가 아니라서 400 이 났다.
     * 원장은 이름표로 정해진다.
     */
    public String bm(String conceptId, String asOf) {
        ObjectNode root = mapper.createObjectNode();
        // textContents 는 모드와 무관하게 필수다. BM 은 컨셉 식별자만 있으면 되지만
        // **빈 배열은 통과하지 못한다**(1~64개).
        root.set("textContents", textContents("concept-ref", "conceptId=" + conceptId));
        root.put("conceptId", conceptId);
        root.put("asOf", asOf);
        root.put("mode", "BM");
        root.put("llmBudget", 1);
        return finish(root);
    }

    private String finish(ObjectNode root) {
        assertNoFloatingPoint(root, "input");
        return root.toString();
    }

    private ArrayNode textContents(String contentKey, String text) {
        ObjectNode content = mapper.createObjectNode();
        content.put("contentKey", contentKey);
        content.put("contentType", "TEXT");
        content.put("language", "ko-KR");
        content.put("totalCharacters", text.codePointCount(0, text.length()));
        content.put("contentHash", sha256(text));
        ArrayNode chunks = content.putArray("chunks");
        int offset = 0;
        int index = 0;
        while (offset < text.length()) {
            int count = Math.min(CHUNK_CHARACTERS, text.codePointCount(offset, text.length()));
            int end = text.offsetByCodePoints(offset, count);
            String value = text.substring(offset, end);
            ObjectNode chunk = chunks.addObject();
            chunk.put("index", index++);
            chunk.put("text", value);
            chunk.put("characterCount", count);
            chunk.put("chunkHash", sha256(value));
            offset = end;
        }
        ArrayNode contents = mapper.createArrayNode();
        contents.add(content);
        return contents;
    }

    /**
     * 부동소수점이 하나라도 있으면 <b>여기서</b> 막는다.
     *
     * <p>안 막으면 {@code CanonicalInputHasher} 가 던지는데, 그건 감싸이지 않은
     * {@code IllegalArgumentException} 이라 사용자에게 500 으로 나간다.
     * 여기서 막으면 어느 경로에 있는 값인지가 메시지에 남는다.
     */
    static void assertNoFloatingPoint(JsonNode node, String path) {
        if (node.isFloatingPointNumber()) {
            throw new IllegalArgumentException(
                "taskInput 에 부동소수점이 있다: " + path + " = " + node.asText()
                + " — 문자열이나 정수(basis point)로 바꿔라");
        }
        if (node.isObject()) {
            for (String name : node.propertyNames()) {
                assertNoFloatingPoint(node.get(name), path + "/" + name);
            }
        } else if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) {
                assertNoFloatingPoint(node.get(i), path + "[" + i + "]");
            }
        }
    }

    private static String sha256(String text) {
        try {
            return "sha256:" + HexFormat.of().formatHex(java.security.MessageDigest
                .getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
    }
}
