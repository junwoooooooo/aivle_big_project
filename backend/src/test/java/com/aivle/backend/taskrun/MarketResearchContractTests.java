package com.aivle.backend.taskrun;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aivle.backend.taskrun.contract.MarketResearchContract;
import com.aivle.backend.taskrun.integration.InternalAiExecutionClient.ExecutionFailure;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 계약 검증기 — <b>AI 쪽과 같은 골든 픽스처</b>를 읽는다.
 *
 * <p>「AI 는 맞다는데 백엔드가 거부」 루프를 끊는 장치다. 파일이 하나이므로
 * 한쪽만 고치면 반대쪽 테스트가 즉시 빨개진다.
 */
class MarketResearchContractTests {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static JsonNode fixture(String name) throws Exception {
        // backend/ 에서 저장소 루트로 올라가 ai/tests/fixtures 를 읽는다.
        Path root = Paths.get("").toAbsolutePath();
        for (int depth = 0; depth < 4; depth++) {
            Path candidate = root.resolve("ai/tests/fixtures/market_research/" + name);
            if (Files.exists(candidate)) return MAPPER.readTree(Files.readString(candidate));
            root = root.getParent();
        }
        throw new IllegalStateException("골든 픽스처를 찾지 못했다: " + name);
    }

    /** 픽스처의 `_` 주석 키는 계약 밖이다 — 검증 전에 벗긴다(AI 쪽도 같은 규칙). */
    private static ObjectNode payload(String name) throws Exception {
        ObjectNode node = (ObjectNode) fixture(name);
        node.propertyNames().stream().filter(key -> key.startsWith("_")).toList()
            .forEach(node::remove);
        return node;
    }

    @Test
    @DisplayName("FULL 골든 픽스처가 계약을 통과한다")
    void fullFixturePasses() throws Exception {
        assertThatCode(() -> MarketResearchContract.validate(payload("full.json")))
            .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("BM 골든 픽스처가 계약을 통과한다")
    void bmFixturePasses() throws Exception {
        assertThatCode(() -> MarketResearchContract.validate(payload("bm.json")))
            .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("봉투에 모르는 필드가 있으면 거부한다")
    void unknownEnvelopeFieldRejected() throws Exception {
        ObjectNode node = payload("full.json");
        node.put("extra", "x");
        assertThatThrownBy(() -> MarketResearchContract.validate(node))
            .isInstanceOf(ExecutionFailure.class);
    }

    @Test
    @DisplayName("칸이 8개면 거부한다 — 빠진 칸은 «없다» 가 아니라 «안 봤다» 다")
    void missingCanvasCellRejected() throws Exception {
        ObjectNode node = payload("bm.json");
        ArrayNode cells = (ArrayNode) node.get("canvas").get("cells");
        cells.remove(0);
        assertThatThrownBy(() -> MarketResearchContract.validate(node))
            .isInstanceOf(ExecutionFailure.class);
    }

    @Test
    @DisplayName("없는 근거 id 를 인용하면 거부한다 — 고아 참조 차단")
    void orphanEvidenceReferenceRejected() throws Exception {
        ObjectNode node = payload("bm.json");
        ObjectNode cell = (ObjectNode) node.get("canvas").get("cells").get(0);
        ((ArrayNode) cell.get("marketEvidenceIds")).add("C-DOES-NOT-EXIST");
        assertThatThrownBy(() -> MarketResearchContract.validate(node))
            .isInstanceOf(ExecutionFailure.class);
    }

    @Test
    @DisplayName("⭐ 인용한 근거의 경계를 칸이 안 실으면 거부한다 — 이 계약의 본체")
    void droppedCaveatRejected() throws Exception {
        ObjectNode node = payload("bm.json");
        ObjectNode cell = (ObjectNode) node.get("canvas").get("cells").get(0);
        assertThat(cell.get("caveats")).isNotEmpty();       // 픽스처 전제 확인
        ((ArrayNode) cell.get("caveats")).removeAll();
        assertThatThrownBy(() -> MarketResearchContract.validate(node))
            .isInstanceOf(ExecutionFailure.class);
    }

    @Test
    @DisplayName("price_base 의 성격 표시가 바뀌면 거부한다 — 잠정값이 확정 단가로 읽히면 안 된다")
    void priceBaseKindPinned() throws Exception {
        ObjectNode node = payload("full.json");
        ((ObjectNode) node.get("market").get("price")).put("baseKind", "MEAN");
        assertThatThrownBy(() -> MarketResearchContract.validate(node))
            .isInstanceOf(ExecutionFailure.class);
    }

    @Test
    @DisplayName("등급 어휘 밖의 값은 거부한다")
    void unknownGradeRejected() throws Exception {
        ObjectNode node = payload("full.json");
        ((ObjectNode) node.get("evidence").get(0)).put("grade", "VERIFIED");
        assertThatThrownBy(() -> MarketResearchContract.validate(node))
            .isInstanceOf(ExecutionFailure.class);
    }

    @Test
    @DisplayName("7과목 중 하나가 빠지면 거부한다")
    void incompleteScorecardRejected() throws Exception {
        ObjectNode node = payload("full.json");
        ((ArrayNode) node.get("scorecard")).remove(0);
        assertThatThrownBy(() -> MarketResearchContract.validate(node))
            .isInstanceOf(ExecutionFailure.class);
    }

    @Test
    @DisplayName("BM 이 null 이어도 통과한다 — BM 실패로 시장조사 결과를 버리지 않는다")
    void nullBmAccepted() throws Exception {
        ObjectNode node = payload("bm.json");
        node.putNull("bm");
        assertThatCode(() -> MarketResearchContract.validate(node))
            .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("FULL 인데 canvas 가 차 있으면 거부한다 — 모드가 섞이면 안 된다")
    void modeMixRejected() throws Exception {
        ObjectNode node = payload("full.json");
        node.set("canvas", payload("bm.json").get("canvas"));
        assertThatThrownBy(() -> MarketResearchContract.validate(node))
            .isInstanceOf(ExecutionFailure.class);
    }
}
