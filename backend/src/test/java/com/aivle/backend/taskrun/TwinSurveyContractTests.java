package com.aivle.backend.taskrun;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aivle.backend.taskrun.contract.TwinSurveyContract;
import com.aivle.backend.taskrun.integration.InternalAiExecutionClient.ExecutionFailure;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * 계약 검증기 — <b>AI 쪽과 같은 골든 픽스처</b>를 읽는다
 * ({@code ai/tests/test_twin_golden.py}, {@code twinSurveyResult.test.js} 와 같은 파일).
 *
 * <p>파일이 하나이므로 한쪽만 고치면 반대쪽이 즉시 빨개진다. 「AI 는 맞다는데 백엔드가 거부」
 * 루프를 끊는 장치다.
 */
class TwinSurveyContractTests {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static ObjectNode payload() throws Exception {
        Path root = Paths.get("").toAbsolutePath();
        for (int depth = 0; depth < 4; depth++) {
            Path candidate = root.resolve("ai/tests/fixtures/twin_survey/survey.json");
            if (Files.exists(candidate)) return (ObjectNode) MAPPER.readTree(Files.readString(candidate));
            root = root.getParent();
        }
        throw new IllegalStateException("골든 픽스처를 찾지 못했다: twin_survey/survey.json");
    }

    @Test
    @DisplayName("골든 픽스처가 계약을 통과한다 — 못 잰 쌍이 섞여 있어도")
    void goldenPasses() throws Exception {
        assertThatCode(() -> TwinSurveyContract.validate(payload())).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("경계가 빈 쌍은 거부한다 — 빈 배열은 «경계 없음»이 아니라 «경계 소실»이다")
    void emptyCaveatsRejected() throws Exception {
        ObjectNode result = payload();
        ((ObjectNode) result.get("pairs").get(0)).putArray("caveats");
        assertThatThrownBy(() -> TwinSurveyContract.validate(result))
            .isInstanceOf(ExecutionFailure.class);
    }

    @Test
    @DisplayName("성적이 없는 유형은 거부한다 — 윤리·가치형이 DB 에 들어오면 근거 없는 수치가 된다")
    void nonServiceableTaskTypeRejected() throws Exception {
        ObjectNode result = payload();
        ((ObjectNode) result.get("pairs").get(0)).put("taskType", "ETHICS");
        assertThatThrownBy(() -> TwinSurveyContract.validate(result))
            .isInstanceOf(ExecutionFailure.class);
    }

    @Test
    @DisplayName("«못 잼»인데 이긴 쪽이 있으면 거부한다 — 두 문장이 서로를 부정한다")
    void measurableAndWinnerMustAgree() throws Exception {
        ObjectNode result = payload();
        ((ObjectNode) result.get("pairs").get(0)).put("measurable", false);
        assertThatThrownBy(() -> TwinSurveyContract.validate(result))
            .isInstanceOf(ExecutionFailure.class);
    }

    @Test
    @DisplayName("표에 없는 표본 크기는 거부한다 — 표기할 측정 한계가 없다")
    void unknownSampleSizeRejected() throws Exception {
        ObjectNode result = payload();
        result.put("sampleSize", 77);
        assertThatThrownBy(() -> TwinSurveyContract.validate(result))
            .isInstanceOf(ExecutionFailure.class);
    }

    @Test
    @DisplayName("2파 생략은 계측에 실려도 통과한다 — 예산 고갈은 정상 경로다")
    void wave2SkippedIsAllowed() throws Exception {
        ObjectNode result = payload();
        ((ObjectNode) result.get("telemetry")).put("wave2Skipped", 12);
        assertThatCode(() -> TwinSurveyContract.validate(result)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("전원 미결정도 통과한다 — 응답자 분류는 나온 것만 실린다")
    void onlyPresentRespondentClassesAreRequired() throws Exception {
        // 실스택에서 실제로 나온 모양이다. 다섯 분류를 다 요구하면 이 실행이 폐기된다.
        ObjectNode result = payload();
        ObjectNode pair = (ObjectNode) result.get("pairs").get(1);   // 못 잰 쪽 쌍
        ObjectNode classes = MAPPER.createObjectNode();
        classes.put("undecided", 100);
        pair.set("respondentClasses", classes);
        assertThatCode(() -> TwinSurveyContract.validate(result)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("모르는 응답자 분류는 거부한다")
    void unknownRespondentClassRejected() throws Exception {
        ObjectNode result = payload();
        ((ObjectNode) result.get("pairs").get(0).get("respondentClasses")).put("guessing", 3);
        assertThatThrownBy(() -> TwinSurveyContract.validate(result))
            .isInstanceOf(ExecutionFailure.class);
    }

    @Test
    @DisplayName("계측에 모르는 칸이 있으면 거부한다")
    void unknownTelemetryFieldRejected() throws Exception {
        ObjectNode result = payload();
        ((ObjectNode) result.get("telemetry")).put("costUsd", 3);
        assertThatThrownBy(() -> TwinSurveyContract.validate(result))
            .isInstanceOf(ExecutionFailure.class);
    }
}
