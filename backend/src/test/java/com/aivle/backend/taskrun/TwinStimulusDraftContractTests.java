package com.aivle.backend.taskrun;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aivle.backend.pipeline.market.TwinSurveyStimulusDraftService;
import com.aivle.backend.taskrun.contract.TwinStimulusDraftContract;
import com.aivle.backend.taskrun.integration.InternalAiExecutionClient.ExecutionFailure;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

class TwinStimulusDraftContractTests {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void goldenFixturePasses() throws Exception {
        assertThatCode(() -> TwinStimulusDraftContract.validate(payload())).doesNotThrowAnyException();
    }

    @Test
    void differingPriceAndEmptyPairsAreRejected() throws Exception {
        ObjectNode differing = payload();
        ((ObjectNode) differing.path("pairs").path(0).path("Y")).put("priceKrw", 8900);
        assertThatThrownBy(() -> TwinStimulusDraftContract.validate(differing)).isInstanceOf(ExecutionFailure.class);

        ObjectNode empty = payload();
        empty.putArray("pairs");
        assertThatThrownBy(() -> TwinStimulusDraftContract.validate(empty)).isInstanceOf(ExecutionFailure.class);
    }

    @Test
    void onlyUnambiguousKrwTextIsParsed() {
        assertThat(TwinSurveyStimulusDraftService.priceKrw("9,900\uC6D0")).isEqualTo(9900L);
        assertThat(TwinSurveyStimulusDraftService.priceKrw("3\uB9CC\uC6D0")).isNull();
        assertThat(TwinSurveyStimulusDraftService.priceKrw("free")).isNull();
        assertThat(TwinSurveyStimulusDraftService.priceKrw("")).isNull();
    }

    private static ObjectNode payload() throws Exception {
        Path root = Paths.get("").toAbsolutePath();
        for (int depth = 0; depth < 5 && root != null; depth++, root = root.getParent()) {
            Path candidate = root.resolve("ai/tests/fixtures/twin_survey/stimulus_draft.json");
            if (Files.exists(candidate)) return (ObjectNode) MAPPER.readTree(Files.readString(candidate));
        }
        throw new IllegalStateException("Twin draft fixture not found");
    }
}
