package com.aivle.backend.pipeline.market;

import com.aivle.backend.pipeline.conceptportfolio.selection.domain.ConceptPortfolioSelection;
import com.aivle.backend.pipeline.marketseed.domain.MarketAnalysisSeedSnapshot;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/** CPV2 Market Seed를 donor Concept/MarketJoin 실행 계약으로 옮기는 유일한 입력 어댑터. */
@Component
public class MarketResearchInputFactory {
    private static final int CHUNK_CHARACTERS = 16_000;
    private final ObjectMapper mapper;
    public MarketResearchInputFactory(ObjectMapper mapper) { this.mapper = mapper; }

    public String full(MarketAnalysisSeedSnapshot snapshot, ConceptPortfolioSelection selection, String asOf) {
        JsonNode seed = mapper.readTree(snapshot.getSnapshotJson());
        ObjectNode concept = donorConcept(seed, selection.getConceptId());
        ObjectNode root = mapper.createObjectNode();
        root.set("textContents", textContents("market-analysis-seed", snapshot.getSnapshotJson()));
        root.put("conceptId", selection.getConceptId());
        root.put("asOf", asOf);
        root.put("mode", "FULL");
        root.put("llmBudget", 3);
        root.put("conceptSnapshotJson", mapper.writeValueAsString(concept));
        root.put("marketSeedSnapshotJson", snapshot.getSnapshotJson());
        ObjectNode source = root.putObject("source");
        source.put("portfolioSelectionId", selection.getId());
        source.put("selectionRunId", selection.getRunId());
        source.put("selectionRevision", selection.getHypothesisRevision());
        source.put("marketSeedSnapshotId", snapshot.getId());
        source.put("marketSeedSnapshotHash", snapshot.getSnapshotHash());
        return finish(root);
    }

    public String bm(MarketResearchVersion marketVersion, JsonNode fullInput,
                     JsonNode plan, JsonNode constraints, int planRevision) {
        ObjectNode root = mapper.createObjectNode();
        root.set("textContents", textContents("market-version-ref",
            "marketResearchVersionId=" + marketVersion.getId()));
        root.put("conceptId", fullInput.path("conceptId").asText());
        root.put("asOf", mapper.readTree(marketVersion.getResultJson()).path("asOf").asText());
        root.put("mode", "BM");
        root.put("llmBudget", 1);
        root.put("marketResultJson", marketVersion.getResultJson());
        root.put("conceptSnapshotJson", fullInput.path("conceptSnapshotJson").asText());
        root.put("legalContextJson", legalContext(
            mapper.readTree(fullInput.path("marketSeedSnapshotJson").asText()),
            fullInput.path("conceptId").asText()).toString());
        ObjectNode source = root.putObject("source");
        source.put("marketResearchVersionId", marketVersion.getId());
        source.put("marketResearchRunId", marketVersion.getSourceRun().getId());
        source.put("bmPlanRevision", planRevision);
        if (plan != null && plan.isObject() && !plan.isEmpty()) root.set("planMaterial", plan);
        if (constraints != null && constraints.isObject() && !constraints.isEmpty()) {
            assertIntegers(constraints); root.set("executionConstraints", constraints);
        }
        return finish(root);
    }

    private ObjectNode donorConcept(JsonNode seed, String conceptId) {
        JsonNode selected = seed.path("selectedConcept");
        JsonNode identity = selected.path("identity");
        JsonNode solution = selected.path("solution");
        JsonNode operation = selected.path("operation");
        JsonNode hypotheses = seed.path("finalHypotheses");
        ObjectNode out = mapper.createObjectNode();
        out.put("concept_id", conceptId);
        out.put("name", identity.path("conceptName").asText());
        out.put("problem", solution.path("problemScenario").asText());
        out.put("target", identity.path("targetUsers").asText());
        out.put("solution", solution.path("solutionMechanism").asText());
        out.put("region", valueText(hypotheses.path("targetRegion")));
        out.putArray("hypotheses");
        String price = valueText(hypotheses.path("price")).replaceAll("[^0-9]", "");
        if (!price.isBlank()) {
            try { out.put("price_hypothesis_krw", Long.parseLong(price)); }
            catch (NumberFormatException ignored) { out.putNull("price_hypothesis_krw"); }
        } else out.putNull("price_hypothesis_krw");
        out.set("constraint", mapper.createObjectNode());
        ObjectNode plan = out.putObject("_bm_plan");
        copyValue(plan, "revenue_model", hypotheses.path("revenueModel"));
        copyValue(plan, "channel", hypotheses.path("channels"));
        copyValue(plan, "differentiation", hypotheses.path("differentiators"));
        copyPlan(plan, "key_activities", operation, "operatingModel", "transactionFlow");
        copyPlan(plan, "key_resources", operation, "platformRole", "featureSet");
        copyPlan(plan, "key_partners", operation, "partnerModel", "partnerRequirements");
        out.set("_hypotheses_v2", hypotheses.deepCopy());
        out.set("_target_operation", operation.deepCopy());
        out.set("_target_legal", seed.path("legalResult").deepCopy());
        return out;
    }

    private ObjectNode legalContext(JsonNode seed, String conceptId) {
        JsonNode legal = seed.path("legalResult");
        ObjectNode out = mapper.createObjectNode();
        out.put("concept_id", conceptId);
        String status = legal.path("legalStatus").asText(legal.path("status").asText("UNVERIFIED"));
        if (!java.util.Set.of("PASS", "CONDITIONAL", "BLOCKED", "UNVERIFIED").contains(status)) status = "UNVERIFIED";
        out.put("status", status);
        out.put("summary", legal.path("safeSummary").asText(legal.path("summary").asText("")));
        copyStrings(out.putArray("risks"), legal.path("risks"));
        JsonNode actions = legal.has("requiredActions") ? legal.path("requiredActions") : legal.path("controls");
        copyStrings(out.putArray("required_actions"), actions);
        return out;
    }

    private void copyValue(ObjectNode target, String key, JsonNode wrapper) {
        JsonNode value = wrapper.path("value");
        if (value.isArray()) target.set(key, value.deepCopy());
        else if (!value.asText("").isBlank()) target.put(key, value.asText());
    }
    private void copyPlan(ObjectNode target, String key, JsonNode source, String... fields) {
        ArrayNode values = mapper.createArrayNode();
        for (String field : fields) {
            JsonNode value = source.path(field);
            if (value.isArray()) copyStrings(values, value);
            else if (!value.asText("").isBlank()) values.add(value.asText());
        }
        if (!values.isEmpty()) target.set(key, values);
    }
    private static String valueText(JsonNode wrapper) {
        JsonNode value = wrapper.path("value");
        if (value.isTextual()) return value.asText();
        return value.isMissingNode() || value.isNull() ? "" : value.toString();
    }
    private static void copyStrings(ArrayNode target, JsonNode source) {
        if (source.isArray()) for (JsonNode item : source) {
            String text = item.isTextual() ? item.asText() : item.path("text").asText("");
            if (!text.isBlank()) target.add(text);
        }
    }
    private static void assertIntegers(JsonNode constraints) {
        for (String name : constraints.propertyNames()) {
            JsonNode value = constraints.get(name);
            if (value != null && !value.isNull() && !value.isIntegralNumber())
                throw new IllegalArgumentException("실행 제약 " + name + " 은 정수여야 한다");
        }
    }
    private String finish(ObjectNode root) { assertNoFloatingPoint(root, "input"); return root.toString(); }
    private ArrayNode textContents(String key, String text) {
        ObjectNode content = mapper.createObjectNode();
        content.put("contentKey", key); content.put("contentType", "TEXT"); content.put("language", "ko-KR");
        content.put("totalCharacters", text.codePointCount(0, text.length())); content.put("contentHash", sha256(text));
        ArrayNode chunks = content.putArray("chunks"); int offset=0,index=0;
        while (offset < text.length()) {
            int count=Math.min(CHUNK_CHARACTERS,text.codePointCount(offset,text.length()));
            int end=text.offsetByCodePoints(offset,count); String value=text.substring(offset,end);
            ObjectNode chunk=chunks.addObject(); chunk.put("index",index++); chunk.put("text",value);
            chunk.put("characterCount",count); chunk.put("chunkHash",sha256(value)); offset=end;
        }
        ArrayNode all=mapper.createArrayNode(); all.add(content); return all;
    }
    static void assertNoFloatingPoint(JsonNode node, String path) {
        if (node.isFloatingPointNumber()) throw new IllegalArgumentException("taskInput 부동소수점: "+path);
        if (node.isObject()) for (String name:node.propertyNames()) assertNoFloatingPoint(node.get(name),path+"/"+name);
        else if (node.isArray()) for (int i=0;i<node.size();i++) assertNoFloatingPoint(node.get(i),path+"["+i+"]");
    }
    private static String sha256(String text) {
        try { return "sha256:"+HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
            .digest(text.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception failure) { throw new IllegalStateException(failure); }
    }
}
