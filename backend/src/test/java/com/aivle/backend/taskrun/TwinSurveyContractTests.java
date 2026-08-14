package com.aivle.backend.taskrun;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aivle.backend.taskrun.contract.TwinSurveyContract;
import com.aivle.backend.taskrun.integration.InternalAiExecutionClient.ExecutionFailure;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

class TwinSurveyContractTests {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void goldenFixturePasses() throws Exception {
        assertThatCode(() -> TwinSurveyContract.validate(payload())).doesNotThrowAnyException();
    }

    @Test
    void emptyCaveatsAreRejected() throws Exception {
        ObjectNode result = payload();
        ((ObjectNode) result.path("pairs").path(0)).putArray("caveats");
        assertThatThrownBy(() -> TwinSurveyContract.validate(result)).isInstanceOf(ExecutionFailure.class);
    }

    @Test
    void priceTaskTypeIsRejectedByThePreservedGate() throws Exception {
        ObjectNode result = payload();
        ((ObjectNode) result.path("pairs").path(0)).put("taskType", "PRICE");
        assertThatThrownBy(() -> TwinSurveyContract.validate(result)).isInstanceOf(ExecutionFailure.class);
    }

    private static ObjectNode payload() throws Exception {
        Path root = Paths.get("").toAbsolutePath();
        for (int depth = 0; depth < 5 && root != null; depth++, root = root.getParent()) {
            Path candidate = root.resolve("ai/tests/fixtures/twin_survey/survey.json");
            if (Files.exists(candidate)) return (ObjectNode) MAPPER.readTree(Files.readString(candidate));
        }
        throw new IllegalStateException("Twin survey fixture not found");
    }
}
