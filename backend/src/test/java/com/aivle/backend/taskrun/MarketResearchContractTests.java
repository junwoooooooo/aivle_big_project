package com.aivle.backend.taskrun;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aivle.backend.taskrun.contract.MarketResearchContract;
import com.aivle.backend.taskrun.integration.InternalAiExecutionClient.ExecutionFailure;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

class MarketResearchContractTests {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void fullAndBmGoldenFixturesPass() throws Exception {
        assertThatCode(() -> MarketResearchContract.validate(payload("full.json"))).doesNotThrowAnyException();
        assertThatCode(() -> MarketResearchContract.validate(payload("bm.json"))).doesNotThrowAnyException();
    }

    @Test
    void unknownEnvelopeFieldIsRejected() throws Exception {
        ObjectNode node = payload("full.json");
        node.put("unexpected", true);
        assertThatThrownBy(() -> MarketResearchContract.validate(node)).isInstanceOf(ExecutionFailure.class);
    }

    @Test
    void incompleteBmcIsRejected() throws Exception {
        ObjectNode node = payload("bm.json");
        ((ArrayNode) node.path("canvas").path("cells")).remove(0);
        assertThatThrownBy(() -> MarketResearchContract.validate(node)).isInstanceOf(ExecutionFailure.class);
    }

    private static ObjectNode payload(String name) throws Exception {
        ObjectNode node = (ObjectNode) fixture("market_research/" + name);
        node.propertyNames().stream().filter(key -> key.startsWith("_")).toList().forEach(node::remove);
        return node;
    }

    private static ObjectNode fixture(String relative) throws Exception {
        Path root = Paths.get("").toAbsolutePath();
        for (int depth = 0; depth < 5 && root != null; depth++, root = root.getParent()) {
            Path candidate = root.resolve("ai/tests/fixtures/" + relative);
            if (Files.exists(candidate)) return (ObjectNode) MAPPER.readTree(Files.readString(candidate));
        }
        throw new IllegalStateException("Fixture not found: " + relative);
    }
}
