package com.aivle.backend.pipeline.market;

import com.aivle.backend.pipeline.conceptportfolio.selection.domain.ConceptPortfolioSelection;
import com.aivle.backend.pipeline.marketseed.domain.MarketAnalysisSeedSnapshot;
import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/** CPV2 Market Seed를 donor Concept/MarketJoin 실행 계약으로 옮기는 유일한 입력 어댑터. */
@Component
public class MarketResearchInputFactory {
    private static final int CHUNK_CHARACTERS = 16_000;
    /**
     * 시장조사 FULL 의 모델 호출 상한.
     *
     * <p><b>판 ㊸ — 90 → 270.</b> 절 체인(문서를 절 단위로 다시 읽어 2·8·9절을 만드는 걸음)이
     * 붙었다. 90 이면 수집이 83 을 써서 남는 7 이 최소 소요 30 에 못 미쳐
     * <b>절 체인이 통째로 안 돌고</b> {@code judgment}·{@code prescriptions}·{@code synthesis}
     * 가 셋 다 {@code null} 로 나갔다(실측).
     *
     * <p>★ <b>판 ㊺ — 270 → 500.</b> 270 은 재질문이 문서의 <b>절반에 못 미치는</b> 수였다.
     * 산수는 {@code pipeline._sections} 에서 그대로 나온다(문서 94건 기준):
     *
     * <pre>
     *   270 → 여유 266 → 읽기 94회        → 남은 176
     *       → 남은 172 → 문서상한 43      → 재질문 43×4 = 172회
     * </pre>
     *
     * 즉 재질문이 문서 94건 중 43건(46%)에만 닿고 나머지 51건은 경고조차 안 뜬다
     * ({@code 문서상한 > 0} 이라 {@code REASK_SKIPPED} 가 안 걸린다). 전량을 덮으려면
     * {@code 94 + 94×4 + 9절 1 + 요약 3 = 474} 이고, 문서가 더 많은 원장을 위해 <b>500</b>으로 둔다.
     *
     * <p>⚠ <b>상한이지 지출이 아니다.</b> 문서가 적은 사업안은 적게 쓴다. 모자라면
     * {@code SECTIONS_TRUNCATED} 로 <b>덜 읽었다는 사실이 원장에 남는다</b>.
     *
     * <p>⚠ 실행 1회가 ≈1,200원에서 <b>≈1,600원</b>이 된다. 사람이 정했다(2026-08-15).
     */
    private static final int LLM_BUDGET_FULL = 500;
    private static final java.util.regex.Pattern SAFE_LABEL =
        java.util.regex.Pattern.compile("[A-Za-z0-9._-]{1,64}");
    private static final List<String> CONSTRAINT_KEYS = List.of("budget_krw", "months", "team");
    private final ObjectMapper mapper;
    public MarketResearchInputFactory(ObjectMapper mapper) { this.mapper = mapper; }

    String full(MarketAnalysisSeedSnapshot snapshot, ConceptPortfolioSelection selection, String asOf) {
        return full(snapshot, selection, asOf, null);
    }

    public String full(MarketAnalysisSeedSnapshot snapshot, ConceptPortfolioSelection selection, String asOf,
                       JsonNode competitorSeeds) {
        return full(snapshot, selection, asOf, competitorSeeds, null);
    }

    public String full(MarketAnalysisSeedSnapshot snapshot, ConceptPortfolioSelection selection, String asOf,
                       JsonNode competitorSeeds, JsonNode constraints) {
        JsonNode seed = mapper.readTree(snapshot.getSnapshotJson());
        ObjectNode concept = donorConcept(seed, selection.getConceptId(), constraints, competitorSeeds);
        ObjectNode root = mapper.createObjectNode();
        root.set("textContents", textContents("market-analysis-seed", snapshot.getSnapshotJson()));
        root.put("conceptId", selection.getConceptId());
        root.put("asOf", asOf);
        root.put("mode", "FULL");
        root.put("llmBudget", LLM_BUDGET_FULL);
        root.put("conceptSnapshotJson", mapper.writeValueAsString(concept));
        root.put("marketSeedSnapshotJson", snapshot.getSnapshotJson());
        ObjectNode source = root.putObject("source");
        source.put("projectId", selection.getProjectId());
        source.put("portfolioSelectionId", selection.getId());
        source.put("selectionRunId", selection.getRunId());
        source.put("selectionRevision", selection.getHypothesisRevision());
        source.put("marketSeedSnapshotId", snapshot.getId());
        source.put("marketSeedSnapshotHash", snapshot.getSnapshotHash());
        source.put("selectedConceptHash", selection.getSelectedConceptHash());
        return finish(root);
    }

    public String recollect(MarketAnalysisSeedSnapshot snapshot, ConceptPortfolioSelection selection,
            String asOf, JsonNode competitorSeeds, JsonNode constraints,
            MarketResearchVersion sourceVersion, String artifactId, String manifestHash,
            String sourceRunId, String sourceTaskRunId, String sourceAttemptId,
            String sourceCanonicalInputHash, String sourceConceptSnapshotHash, String sourceAsOf,
            String slots, String from, String slotsFrom) {
        ObjectNode root = (ObjectNode) mapper.readTree(
            full(snapshot, selection, asOf, competitorSeeds, constraints));
        root.put("sourceRun", sourceRunId);
        ObjectNode recollect = root.putObject("recollect");
        recollect.put("slots", slots == null ? "" : slots.trim());
        recollect.put("from", from == null || from.isBlank() ? "a4" : from.trim());
        recollect.put("slotsFrom", slotsFrom == null || slotsFrom.isBlank() ? "source" : slotsFrom.trim());
        ObjectNode artifact = root.putObject("ledgerArtifact");
        artifact.put("artifactId", artifactId);
        artifact.put("manifestHash", manifestHash);
        artifact.put("sourceMarketResearchVersionId", sourceVersion.getId());
        artifact.put("sourceMarketTaskRunId", sourceTaskRunId);
        artifact.put("sourceTaskAttemptId", sourceAttemptId);
        artifact.put("sourceCanonicalInputHash", sourceCanonicalInputHash);
        artifact.put("sourceConceptSnapshotHash", sourceConceptSnapshotHash);
        artifact.put("sourceAsOf", sourceAsOf);
        ObjectNode source = (ObjectNode) root.path("source");
        source.put("sourceMarketResearchVersionId", sourceVersion.getId());
        source.put("sourceMarketTaskRunId", sourceVersion.getSourceRun().getTaskRun().getId());
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

    private ObjectNode donorConcept(JsonNode seed, String conceptId, JsonNode constraints,
                                    JsonNode competitorSeeds) {
        JsonNode selected = seed.path("selectedConcept");
        JsonNode identity = selected.path("identity");
        JsonNode solution = selected.path("solution");
        JsonNode operation = selected.path("operation");
        JsonNode hypotheses = seed.path("finalHypotheses");
        String name = text(identity.path("conceptName"));
        if (conceptId == null || !SAFE_LABEL.matcher(conceptId).matches() || name.isBlank()) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                "사업안 스냅샷의 이름 또는 식별자가 시장조사 원장 계약에 맞지 않습니다.");
        }
        String region = text(hypotheses.path("targetRegion").path("value"));
        ObjectNode out = mapper.createObjectNode();
        out.put("concept_id", conceptId);
        out.put("name", name);
        out.put("problem", text(solution.path("problemScenario")));
        out.put("target", target(text(identity.path("targetUsers")), region));
        out.put("solution", text(solution.path("solutionMechanism")));
        if (!region.isBlank()) out.put("region", region);
        out.putArray("hypotheses");
        Long price = TwinSurveyStimulusDraftService.priceKrw(text(hypotheses.path("price").path("value")));
        if (price == null) out.putNull("price_hypothesis_krw"); else out.put("price_hypothesis_krw", price);
        out.set("constraint", constraints(constraints));

        ObjectNode series = out.putObject("_계열");
        series.put("계열", "C");
        series.put("왜", "시장 거래액 × 점유율로 TAM을 산정한다.");
        series.put("_고정_사유", "채울 수 있는 구조를 기준으로 C를 고정한다. 개인 대상 서비스는 거래액 통계가 없어 TAM이 미확보될 수 있고, 거래액은 매출과 다르다.");
        ObjectNode refinement = out.putObject("_다듬기5");
        refinement.put("3_핵심_가치", text(identity.path("coreValue")));
        ObjectNode industry = refinement.putObject("4_업종_분류");
        industry.put("명칭", text(identity.path("industryCategory")));
        industry.put("_확인_필요", "KSIC 코드는 확정되지 않았으며 추측하지 않는다.");
        out.set("_hypotheses_v2", hypothesesV2(hypotheses));
        out.set("_bm_plan", bmPlan(selected, hypotheses));
        if (competitorSeeds != null && competitorSeeds.isObject() && !competitorSeeds.isEmpty())
            out.set("_경쟁_씨앗", competitorSeeds.deepCopy());
        out.set("_target_legal", seed.path("legalResult").deepCopy());
        return out;
    }

    private ObjectNode constraints(JsonNode raw) {
        ObjectNode out = mapper.createObjectNode();
        if (raw == null || !raw.isObject()) return out;
        for (String key : CONSTRAINT_KEYS) {
            JsonNode value = raw.get(key);
            if (value != null && value.isIntegralNumber()) out.put(key, value.longValue());
        }
        return out;
    }

    private ObjectNode hypothesesV2(JsonNode hypotheses) {
        ObjectNode out = mapper.createObjectNode();
        String priceText = text(hypotheses.path("price").path("value"));
        ObjectNode revenue = out.putObject("6_수익_가격");
        revenue.put("수익_방식", text(hypotheses.path("revenueModel").path("value")));
        Long price = TwinSurveyStimulusDraftService.priceKrw(priceText);
        if (price == null) revenue.putNull("제안값_krw_월"); else revenue.put("제안값_krw_월", price);
        revenue.put("_확정_가격_원문", priceText);
        ObjectNode channel = out.putObject("7_채널");
        putList(channel, "제안값", lines(hypotheses.path("channels").path("value")));
        String channelText = text(hypotheses.path("channels").path("value"));
        if (!channelText.isBlank()) channel.put("주_채널_가정", channelText);
        ObjectNode differentiation = out.putObject("8_차별점");
        differentiation.putArray("비교축");
        differentiation.put("_확정_차별점_원문", text(hypotheses.path("differentiators").path("value")));
        JsonNode share = hypotheses.path("preMarketSomShare").path("value");
        ObjectNode som = out.putObject("9_SOM_초기점유");
        JsonNode percent = share.path("targetSharePercent");
        if (percent.isNumber()) som.put("가정_침투율", percent.decimalValue().divide(BigDecimal.valueOf(100)));
        else som.putNull("가정_침투율");
        if (share.path("horizonYears").isIntegralNumber())
            som.put("가정_기간", "출시 " + share.path("horizonYears").intValue() + "년차");
        return out;
    }

    private ObjectNode bmPlan(JsonNode concept, JsonNode hypotheses) {
        JsonNode operation = concept.path("operation");
        ObjectNode out = mapper.createObjectNode();
        put(out, "revenue_model", text(hypotheses.path("revenueModel").path("value")));
        putList(out, "channel", lines(hypotheses.path("channels").path("value")));
        putList(out, "differentiation", lines(hypotheses.path("differentiators").path("value")));
        putList(out, "key_activities", merge(operation.path("operatingModel"), operation.path("transactionFlow")));
        putList(out, "key_resources", merge(operation.path("platformRole"), concept.path("solution").path("featureSet")));
        putList(out, "key_partners", merge(operation.path("partnerModel"), operation.path("partnerRequirements")));
        out.put("_출처", "확정 사업안의 가설과 운영 서술에서 파생한 가정이다.");
        return out;
    }

    private static String target(String users, String region) {
        if (region.isBlank() || users.contains(region)) return users;
        return users.isBlank() ? region : users + " (" + region + ")";
    }

    private static String text(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) return "";
        return (node.isTextual() ? node.stringValue() : node.asText("")).trim();
    }

    private static List<String> lines(JsonNode node) { return merge(node); }
    private static List<String> merge(JsonNode... nodes) {
        List<String> out = new ArrayList<>();
        for (JsonNode node : nodes) {
            if (node == null) continue;
            if (node.isArray()) for (JsonNode item : node) { String value=text(item); if(!value.isBlank()) out.add(value); }
            else { String value=text(node); if(!value.isBlank()) out.add(value); }
        }
        return out;
    }
    private static void put(ObjectNode target, String key, String value) {
        if (!value.isBlank()) target.put(key, value);
    }
    private void putList(ObjectNode target, String key, List<String> values) {
        if (values.isEmpty()) return;
        ArrayNode array=target.putArray(key); for(String value:values) array.add(value);
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
