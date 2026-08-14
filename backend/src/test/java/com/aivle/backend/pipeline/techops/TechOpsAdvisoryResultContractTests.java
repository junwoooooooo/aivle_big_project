package com.aivle.backend.pipeline.techops;

import static org.assertj.core.api.Assertions.*;

import com.aivle.backend.pipeline.techops.application.TechOpsAdvisoryResultContract;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

class TechOpsAdvisoryResultContractTests {
    private final ObjectMapper mapper = new ObjectMapper();
    private final TechOpsAdvisoryResultContract contract = new TechOpsAdvisoryResultContract();

    @Test void acceptsExactSevenAdviceFourReadinessAndKnownBasisIds() {
        assertThatCode(() -> contract.validate(valid())).doesNotThrowAnyException();
    }

    @Test void rejectsMissingAdviceAreaDuplicateReadinessAndUnknownBasisId() {
        JsonNode missing = valid(); ((ArrayNode) missing.path("advice")).remove(0);
        assertThatThrownBy(() -> contract.validate(missing)).isInstanceOf(IllegalStateException.class);
        JsonNode duplicate = valid(); ((ObjectNode) duplicate.path("readiness").path(1)).put("topic", "DATA_AI");
        assertThatThrownBy(() -> contract.validate(duplicate)).isInstanceOf(IllegalStateException.class);
        JsonNode unknown = valid(); ((ArrayNode) unknown.path("gates").path(0).path("basisIds")).set(0, mapper.getNodeFactory().textNode("FACT-999"));
        assertThatThrownBy(() -> contract.validate(unknown)).isInstanceOf(IllegalStateException.class);
    }

    @Test void additiveMigrationKeepsRerunsAndCanonicalTaskBinding() throws Exception {
        String sql = Files.readString(Path.of("src", "main", "resources", "db", "migration",
            "V22__tech_ops_advisory_reports.sql")).toLowerCase();
        assertThat(sql).contains("unique (task_run_id)", "tech_ops_input_snapshot_id",
            "source_market_research_version_id", "source_business_model_version_id",
            "selected_concept_hash").doesNotContain("unique (project_id, source");
    }

    private JsonNode valid() {
        ObjectNode result = mapper.createObjectNode(); result.put("productName", "서비스");
        result.put("decision", "CONDITIONAL_GO"); result.put("summary", "근거 기반 요약");
        ArrayNode advice = result.putArray("advice");
        for (String area : new String[]{"MARKET_BM","PRODUCT_TECH","OPERATIONS","RISK_GATE","PARTNER_SUPPLY","PILOT","SCALE"})
            advice.add(item("area", area));
        ArrayNode gates = result.putArray("gates"); for (int i=0;i<6;i++) gates.add(item("status", "OPEN"));
        ArrayNode costs = result.putArray("operatingCosts"); for (int i=0;i<5;i++) costs.add(item("behavior", "VARIABLE"));
        ArrayNode readiness = result.putArray("readiness");
        for (String topic : new String[]{"DATA_AI","CUSTOMER_TRUST","OBSERVABILITY_SLA","SCALABILITY"}) readiness.add(item("topic", topic));
        ObjectNode pilot=result.putObject("pilotPlan");pilot.put("objective","검증");
        for(String key:new String[]{"scope","metrics","stopConditions","scaleConditions"})pilot.putArray(key).add("항목");
        result.put("disclaimer","보증이 아닙니다"); result.putArray("layer1Facts").addObject().put("factId","FACT-001");
        result.putArray("layer2Evidence").addObject().put("evidenceId","EXT-001"); return result;
    }
    private ObjectNode item(String field, String value) { ObjectNode item=mapper.createObjectNode();
        item.put(field,value);item.putArray("basisIds").add("FACT-001");return item; }
}
