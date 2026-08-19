package com.aivle.backend.taskrun;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aivle.backend.taskrun.contract.MarketInterviewContract;
import com.aivle.backend.taskrun.integration.InternalAiExecutionClient.ExecutionFailure;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 계약 검증기 — <b>AI 쪽과 같은 골든 픽스처</b>를 읽는다
 * ({@code ai/tests/test_interview_golden.py},
 * {@code marketInterviewResult.test.js} 와 같은 파일).
 *
 * <p>파일이 하나이므로 한쪽만 고치면 반대쪽이 즉시 빨개진다. 「AI 는 맞다는데 백엔드가 거부」
 * 루프를 끊는 장치다.
 */
class MarketInterviewContractTests {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static ObjectNode payload() throws Exception {
        Path root = Paths.get("").toAbsolutePath();
        for (int depth = 0; depth < 4; depth++) {
            Path candidate = root.resolve("ai/tests/fixtures/market_interview/interview.json");
            if (Files.exists(candidate)) return (ObjectNode) MAPPER.readTree(Files.readString(candidate));
            root = root.getParent();
        }
        throw new IllegalStateException("골든 픽스처를 찾지 못했다: market_interview/interview.json");
    }

    @Test
    @DisplayName("골든 픽스처가 계약을 통과한다 — 오해한 응답자와 얕은 층이 섞여 있어도")
    void goldenPasses() throws Exception {
        assertThatCode(() -> MarketInterviewContract.validate(payload())).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("유효 응답은 보존하고 분류 주제 0개도 성공 결과로 받는다")
    void emptyThemesAreAccepted() throws Exception {
        ObjectNode result = payload();
        result.putArray("themes");
        result.putArray("segments");
        result.putArray("contrast");
        result.putArray("suggestionLinks");
        ObjectNode homogeneity = (ObjectNode) result.path("telemetry").path("homogeneity");
        homogeneity.set("axisLabelCounts", MAPPER.createObjectNode());
        homogeneity.set("maxMentionByAxis", MAPPER.createObjectNode());
        homogeneity.putArray("saturatedThemes");
        assertThatCode(() -> MarketInterviewContract.validate(result)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("경계가 비면 거부한다 — 빈 배열은 «경계 없음»이 아니라 «경계 소실»이다")
    void emptyCaveatsRejected() throws Exception {
        ObjectNode result = payload();
        result.putArray("caveats");
        assertThatThrownBy(() -> MarketInterviewContract.validate(result))
            .isInstanceOf(ExecutionFailure.class);
    }

    @Test
    @DisplayName("언급 수가 표본을 넘으면 거부한다 — 그 수는 LLM 이 센 것이다")
    void mentionCountAboveSampleRejected() throws Exception {
        ObjectNode result = payload();
        ((ObjectNode) result.get("themes").get(0)).put("mentionCount", 21);
        assertThatThrownBy(() -> MarketInterviewContract.validate(result))
            .isInstanceOf(ExecutionFailure.class);
    }

    // ── 2026-08-12 의 40/40 을 되받아치는 검사들 ─────────────────────────
    @Test
    @DisplayName("언급 수가 명단 길이와 다르면 거부한다 — AI 가 센 수를 받지 않는다")
    void mentionCountMustMatchTheRespondentList() throws Exception {
        ObjectNode result = payload();
        ObjectNode theme = (ObjectNode) result.get("themes").get(0);
        theme.put("mentionCount", theme.get("respondentIds").size() - 1);
        assertThatThrownBy(() -> MarketInterviewContract.validate(result))
            .isInstanceOf(ExecutionFailure.class);
    }

    @Test
    @DisplayName("명단에 같은 사람이 두 번 들면 거부한다 — 언급 수가 부푼다")
    void duplicateRespondentIdRejected() throws Exception {
        ObjectNode result = payload();
        ObjectNode theme = (ObjectNode) result.get("themes").get(0);
        ArrayNode ids = (ArrayNode) theme.get("respondentIds");
        ids.add(ids.get(0).asText());
        theme.put("mentionCount", ids.size());
        assertThatThrownBy(() -> MarketInterviewContract.validate(result))
            .isInstanceOf(ExecutionFailure.class);
    }

    @Test
    @DisplayName("대안 언급 수 합계가 응답자 수를 넘으면 거부한다 — 1인 1대안이다")
    void alternativesSummingPastTheSampleRejected() throws Exception {
        // 관측된 고장 그 자체: 대안 3개가 «동시에» 40/40 이었다. 한 사람이 셋을 다 할 수는 없다.
        ObjectNode result = payload();
        int answered = result.get("telemetry").get("answered").asInt();
        for (var alternative : result.get("alternatives")) {
            ((ObjectNode) alternative).put("mentionCount", answered);
        }
        assertThatThrownBy(() -> MarketInterviewContract.validate(result))
            .isInstanceOf(ExecutionFailure.class);
    }

    @Test
    @DisplayName("세그먼트 버킷 합이 언급 수와 다르면 거부한다 — 화면의 두 수가 어긋난다")
    void segmentBucketsMustAddUp() throws Exception {
        ObjectNode result = payload();
        ObjectNode bucket = (ObjectNode) result.get("segments").get(0)
            .get("breakdown").get(0).get("buckets").get(0);
        bucket.put("count", bucket.get("count").asInt() + 1);
        assertThatThrownBy(() -> MarketInterviewContract.validate(result))
            .isInstanceOf(ExecutionFailure.class);
    }

    @Test
    @DisplayName("최상위에 모르는 칸이 붙으면 거부한다 — 봉투는 정확 집합이다")
    void unknownEnvelopeFieldRejected() throws Exception {
        ObjectNode result = payload();
        result.put("purchaseIntentRate", 0.62);
        assertThatThrownBy(() -> MarketInterviewContract.validate(result))
            .isInstanceOf(ExecutionFailure.class);
    }

    @Test
    @DisplayName("포화 진단이 빠지면 거부한다 — 40/40 을 다시 조용히 지나가게 두지 않는다")
    void telemetryWithoutHomogeneityRejected() throws Exception {
        ObjectNode result = payload();
        ((ObjectNode) result.get("telemetry")).remove("homogeneity");
        assertThatThrownBy(() -> MarketInterviewContract.validate(result))
            .isInstanceOf(ExecutionFailure.class);
    }

    @Test
    @DisplayName("타겟 조건식이 빠지면 거부한다 — 누구에게 물었는지 못 보이는 결과는 해석할 수 없다")
    void targetingWithoutCriteriaRejected() throws Exception {
        ObjectNode result = payload();
        ((ObjectNode) result.get("targeting")).remove("criteria");
        assertThatThrownBy(() -> MarketInterviewContract.validate(result))
            .isInstanceOf(ExecutionFailure.class);
    }

    @Test
    @DisplayName("전원 응답에 모르는 필드가 붙으면 거부한다 — 사람 수가 카드의 16배다")
    void unknownTranscriptFieldRejected() throws Exception {
        ObjectNode result = payload();
        ((ObjectNode) result.get("transcripts").get(0)).put("pidHash", "9f2c...");
        assertThatThrownBy(() -> MarketInterviewContract.validate(result))
            .isInstanceOf(ExecutionFailure.class);
    }

    @Test
    @DisplayName("언급 수 0 인 주제는 거부한다 — 아무도 말하지 않은 주제는 실리면 안 된다")
    void zeroMentionRejected() throws Exception {
        ObjectNode result = payload();
        ((ObjectNode) result.get("themes").get(0)).put("mentionCount", 0);
        assertThatThrownBy(() -> MarketInterviewContract.validate(result))
            .isInstanceOf(ExecutionFailure.class);
    }

    @Test
    @DisplayName("모르는 축은 거부한다 — 화면이 그릴 절이 없다")
    void unknownAxisRejected() throws Exception {
        ObjectNode result = payload();
        ((ObjectNode) result.get("themes").get(0)).put("axis", "PRICE_SENSITIVITY");
        assertThatThrownBy(() -> MarketInterviewContract.validate(result))
            .isInstanceOf(ExecutionFailure.class);
    }

    @Test
    @DisplayName("같은 축에 같은 이름표가 둘이면 거부한다 — 화면이 같은 막대를 두 번 그린다")
    void duplicateThemeRejected() throws Exception {
        ObjectNode result = payload();
        ArrayNode themes = (ArrayNode) result.get("themes");
        themes.add(themes.get(0).deepCopy());
        assertThatThrownBy(() -> MarketInterviewContract.validate(result))
            .isInstanceOf(ExecutionFailure.class);
    }

    @Test
    @DisplayName("이해도 합이 표본을 넘으면 거부한다 — 어딘가에서 사람이 늘어난 것이다")
    void comprehensionAboveSampleRejected() throws Exception {
        ObjectNode result = payload();
        ((ObjectNode) result.get("comprehension")).put("accurate", 30);
        assertThatThrownBy(() -> MarketInterviewContract.validate(result))
            .isInstanceOf(ExecutionFailure.class);
    }

    @Test
    @DisplayName("표본 크기는 20·40·80 뿐이다 — 우열 조사의 50/100/300 이 아니다")
    void legacySampleSizeRejected() throws Exception {
        ObjectNode result = payload();
        result.put("sampleSize", 300);
        assertThatThrownBy(() -> MarketInterviewContract.validate(result))
            .isInstanceOf(ExecutionFailure.class);
    }

    @Test
    @DisplayName("말 없는 카드는 거부한다 — 9칸이 전부 비면 화면에 얼굴만 앉는다")
    void silentInterviewCardRejected() throws Exception {
        ObjectNode result = payload();
        ObjectNode card = (ObjectNode) result.get("interviews").get(0);
        for (String field : new String[] {"firstImpression", "restatement", "like", "concern",
            "differentiation", "relevance", "usageScene", "barrier", "suggestion"}) {
            card.putNull(field);
        }
        assertThatThrownBy(() -> MarketInterviewContract.validate(result))
            .isInstanceOf(ExecutionFailure.class);
    }

    @Test
    @DisplayName("카드에 모르는 필드가 붙으면 거부한다 — 카드 원문이 실려 오는 회귀를 막는다")
    void unknownInterviewFieldRejected() throws Exception {
        ObjectNode result = payload();
        ((ObjectNode) result.get("interviews").get(0)).put("cardText", "저는 만 34세 여성입니다...");
        assertThatThrownBy(() -> MarketInterviewContract.validate(result))
            .isInstanceOf(ExecutionFailure.class);
    }

    @Test
    @DisplayName("자극을 못 보이는 결과는 거부한다 — rendered 없이는 답을 해석할 수 없다")
    void boardWithoutRenderedRejected() throws Exception {
        ObjectNode result = payload();
        ((ObjectNode) result.get("conceptBoard")).remove("rendered");
        assertThatThrownBy(() -> MarketInterviewContract.validate(result))
            .isInstanceOf(ExecutionFailure.class);
    }

    @Test
    @DisplayName("가격은 원 단위 정수이거나 «미정»이다 — 실수는 거부한다")
    void floatingPointPriceRejected() throws Exception {
        ObjectNode result = payload();
        ((ObjectNode) result.get("conceptBoard")).put("priceKrw", 39000.5);
        assertThatThrownBy(() -> MarketInterviewContract.validate(result))
            .isInstanceOf(ExecutionFailure.class);
    }

    @Test
    @DisplayName("가격 «미정»(null)은 통과한다 — 확정 가설이 자유문장이면 못 읽는다")
    void nullPriceAccepted() throws Exception {
        ObjectNode result = payload();
        ((ObjectNode) result.get("conceptBoard")).putNull("priceKrw");
        assertThatCode(() -> MarketInterviewContract.validate(result)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("얕은 층 보고는 통과한다 — shortCells 값은 정수가 아니라 객체다")
    void shortCellsObjectAccepted() throws Exception {
        ObjectNode result = payload();
        ObjectNode shortCells = (ObjectNode) result.get("sampling").get("shortCells");
        ObjectNode cell = shortCells.putObject("남 20대");
        cell.put("quota", 2);
        cell.put("available", 0);
        assertThatCode(() -> MarketInterviewContract.validate(result)).doesNotThrowAnyException();
    }
}
