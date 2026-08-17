package com.aivle.backend.pipeline.finance.application;

import com.aivle.backend.pipeline.techops.domain.TechOpsInputSnapshot;
import com.aivle.backend.pipeline.shared.ThreeYearTargetsContract;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Component
public class FinancialPreparationFactory {
    public static final String CONTRACT = "financial-input-preparation-v1";
    public static final String SCHEMA_VERSION = "2.0";
    public static final List<String> FIXED_COST_KEYS = List.of("annualFixedLaborCost",
        "annualFixedRentAndManagementCost", "annualFixedInfrastructureCost");
    public static final List<String> INITIAL_INVESTMENT_KEYS = List.of("initialDevelopmentAndRnDCost",
        "initialEquipmentAndInfrastructureCost", "initialPatentAndLicensingCost");
    public static final List<String> CAC_INPUT_KEYS = List.of("totalMarketingCost", "totalSalesCost", "newCustomerCount");
    public static final List<String> REVENUE_INPUT_KEYS = List.of("revenueModel", "unitPrice",
        "monthlySubscriptionPrice", "monthlyChurnRate");
    public static final List<String> CONDITIONAL_COST_KEYS = List.of("unitVariableCost", "paymentFee",
        "partnerPayout", "shippingCost", "customerIncrementalInfraCost");
    public static final List<String> REQUIRED_KEYS = List.of("annualFixedLaborCost",
        "annualFixedRentAndManagementCost", "annualFixedInfrastructureCost", "initialDevelopmentAndRnDCost",
        "initialEquipmentAndInfrastructureCost", "initialPatentAndLicensingCost", "threeYearTargets",
        "totalMarketingCost", "totalSalesCost", "newCustomerCount", "revenueModel");
    public static final List<String> ALL_KEYS = List.of("annualFixedLaborCost",
        "annualFixedRentAndManagementCost", "annualFixedInfrastructureCost", "initialDevelopmentAndRnDCost",
        "initialEquipmentAndInfrastructureCost", "initialPatentAndLicensingCost", "threeYearTargets",
        "totalMarketingCost", "totalSalesCost", "newCustomerCount", "unitVariableCost", "paymentFee",
        "partnerPayout", "shippingCost", "customerIncrementalInfraCost", "revenueModel", "unitPrice",
        "monthlySubscriptionPrice", "monthlyChurnRate");

    private final ObjectMapper mapper;
    public FinancialPreparationFactory(ObjectMapper mapper) { this.mapper = mapper; }

    public InitialPreparation create(TechOpsInputSnapshot snapshot) {
        JsonNode source = mapper.readTree(snapshot.getSnapshotJson());
        JsonNode facts = source.path("requiredFacts");
        JsonNode provenance = source.path("requiredFactProvenance");
        ObjectNode fields = mapper.createObjectNode();
        for (String key : FIXED_COST_KEYS) inheritMoneyOrOpen(fields, key, facts.path("fixedOperatingCost"),
            provenance.path("fixedOperatingCost"), snapshot);
        for (String key : INITIAL_INVESTMENT_KEYS) inheritMoneyOrOpen(fields, key, facts.path("initialInvestment"),
            provenance.path("initialInvestment"), snapshot);
        inheritTargetsOrOpen(fields, facts.path("threeYearTargets"), provenance.path("threeYearTargets"), snapshot);
        for (String key : CAC_INPUT_KEYS) inheritDirectOrOpen(fields, key, facts, provenance, snapshot);
        for (String key : CONDITIONAL_COST_KEYS) inheritDirectOrOpen(fields, key, facts, provenance, snapshot);
        for (String key : REVENUE_INPUT_KEYS) open(fields, key);

        ObjectNode references = mapper.createObjectNode();
        reference(references, "fixedOperatingCost", facts.path("fixedOperatingCost"),
            provenance.path("fixedOperatingCost"), snapshot);
        reference(references, "initialInvestment", facts.path("initialInvestment"),
            provenance.path("initialInvestment"), snapshot);
        reference(references, "threeYearTargets", facts.path("threeYearTargets"),
            provenance.path("threeYearTargets"), snapshot);

        ObjectNode assistance = mapper.createObjectNode();
        assistance(assistance, "fixedOperatingCosts", "연간 인건비·임차관리비·인프라비를 각각 입력하세요.",
            "예: 급여와 회사 부담금의 연간 합계는 인건비에 포함합니다.");
        assistance(assistance, "initialInvestment", "분석 시작 전에 한 번 투입되는 개발·설비·특허 비용을 구분하세요.",
            "예: 초기 제품 개발 외주비는 개발·R&D 비용에 포함합니다.");
        assistance(assistance, "threeYearTargets", "사업 유형에 맞는 하나의 지표와 1~3년차 목표를 선택하세요.",
            "예: 구독 서비스는 subscriberCount, 거래 플랫폼은 transactionCount를 사용할 수 있습니다.");
        assistance(assistance, "cac", "마케팅비·영업비·신규 고객 수를 입력하면 CAC를 시스템이 계산합니다.",
            "CAC = (총 마케팅비 + 총 영업비) / 신규 고객 수");
        assistance(assistance, "conditionalCosts", "외부 분석 계약에 필요한 경우에만 조건부 단위원가를 입력하세요.",
            "배송이 없는 서비스라면 shippingCost는 비워 둡니다.");
        for (String key : ALL_KEYS) estimateAssistance(assistance, key);
        return new InitialPreparation(fields, references, assistance);
    }

    /** TechOps 확정 사실은 잠그고 Market/BM 근거와 가설은 검토 가능한 입력으로 합친다. */
    public InitialPreparation create(TechOpsInputSnapshot techOps, JsonNode marketResult,
            JsonNode businessModelResult, Long marketVersionId, Long businessModelVersionId) {
        InitialPreparation base = create(techOps);
        ObjectNode fields = base.financialFields();
        ObjectNode references = base.upstreamReferences();
        JsonNode market = marketResult.path("market");
        JsonNode handoff = businessModelResult.path("bm").path("financialHandoff");
        JsonNode price = market.path("price");
        if (price.path("base").isNumber() && price.path("base").asDouble() > 0) {
            assumedMoneyIfOpen(fields, "monthlySubscriptionPrice", price.path("base"), price,
                "market.price.base", "시장 가격 가설 — 확인 후 재무 가정으로 확정 필요");
            assumedMoneyIfOpen(fields, "unitPrice", price.path("base"), price,
                "market.price.base", "시장 가격 가설 — 확인 후 재무 가정으로 확정 필요");
        }
        String revenueModel = revenueModel(handoff.path("revenueModel").asText(""));
        if (revenueModel != null) assumedTextIfOpen(fields, "revenueModel", revenueModel,
            "bm.financialHandoff.revenueModel", "BM 재무 전달정보의 수익모델 가설");

        ObjectNode marketReference = references.putObject("marketAnalysis");
        marketReference.put("sourceVersionId", marketVersionId);
        marketReference.set("tam", market.path("tam").deepCopy());
        marketReference.set("sam", market.path("sam").deepCopy());
        marketReference.set("growth", market.path("growth").deepCopy());
        marketReference.set("price", price.deepCopy());
        marketReference.set("evidence", marketResult.path("evidence").deepCopy());
        marketReference.set("scorecard", marketResult.path("scorecard").deepCopy());
        marketReference.put("label", "시장 규모·성장률·가격·계산 근거");
        marketReference.put("provenance", "marketResearchVersion.result");
        ObjectNode bmReference = references.putObject("businessModel");
        bmReference.put("sourceVersionId", businessModelVersionId);
        bmReference.set("result", businessModelResult.deepCopy());
        bmReference.set("financialHandoff", handoff.deepCopy());
        bmReference.put("label", "시장→BM 분석 결과와 재무 전달정보");
        bmReference.put("provenance", "businessModelVersion.result");
        ObjectNode techOpsReference = references.putObject("techOpsSnapshot");
        techOpsReference.put("sourceSnapshotId", techOps.getId());
        techOpsReference.put("sourceMarketSeedSnapshotId", techOps.getSourceMarketSeedSnapshotId());
        techOpsReference.put("snapshotHash", techOps.getSnapshotHash());
        techOpsReference.put("label", "기술·운영 확정 Snapshot");
        return new InitialPreparation(fields, references, base.assistance());
    }

    /** 새 Finance authority는 TechOps 없이 current Market/BM 근거만으로 준비값을 만든다. */
    public InitialPreparation createFromMarketAndBusinessModel(JsonNode marketResult,
            JsonNode businessModelResult, JsonNode conceptHypotheses, Long marketVersionId,
            Long businessModelVersionId) {
        ObjectNode fields = mapper.createObjectNode();
        for (String key : ALL_KEYS) open(fields, key);

        ObjectNode references = mapper.createObjectNode();
        ObjectNode marketReference = references.putObject("marketAnalysis");
        JsonNode market = marketResult.path("market");
        marketReference.put("sourceVersionId", marketVersionId);
        marketReference.set("tam", market.path("tam").deepCopy());
        marketReference.set("sam", market.path("sam").deepCopy());
        marketReference.set("growth", market.path("growth").deepCopy());
        marketReference.set("price", market.path("price").deepCopy());
        marketReference.set("evidence", marketResult.path("evidence").deepCopy());
        marketReference.set("scorecard", marketResult.path("scorecard").deepCopy());
        marketReference.put("label", "시장 규모·성장률·가격·계산 근거");
        marketReference.put("provenance", "marketResearchVersion.result");

        ObjectNode bmReference = references.putObject("businessModel");
        JsonNode handoff = businessModelResult.path("bm").path("financialHandoff");
        bmReference.put("sourceVersionId", businessModelVersionId);
        bmReference.set("result", businessModelResult.deepCopy());
        bmReference.set("financialHandoff", handoff.deepCopy());
        bmReference.put("label", "시장→BM 분석 결과와 재무 전달정보");
        bmReference.put("provenance", "businessModelVersion.result");

        applyConceptDefaults(fields, references, conceptHypotheses);
        applyMarketDefaults(fields, marketResult);
        applyBusinessModelDefaults(fields, businessModelResult);

        ObjectNode assistance = mapper.createObjectNode();
        assistance(assistance, "fixedOperatingCosts", "연간 인건비·임차관리비·인프라비를 각각 입력하세요.",
            "예: 급여와 회사 부담금의 연간 합계는 인건비에 포함합니다.");
        assistance(assistance, "initialInvestment", "분석 시작 전에 한 번 투입되는 개발·설비·특허 비용을 구분하세요.",
            "예: 초기 제품 개발 외주비는 개발·R&D 비용에 포함합니다.");
        assistance(assistance, "threeYearTargets", "사업 유형에 맞는 하나의 지표와 1~3년차 목표를 선택하세요.",
            "예: 구독 서비스는 subscriberCount, 거래 플랫폼은 transactionCount를 사용할 수 있습니다.");
        assistance(assistance, "cac", "마케팅비·영업비·신규 고객 수를 입력하면 CAC를 시스템이 계산합니다.",
            "CAC = (총 마케팅비 + 총 영업비) / 신규 고객 수");
        assistance(assistance, "conditionalCosts", "필요한 경우에만 조건부 단위원가를 입력하세요.",
            "배송이 없는 서비스라면 shippingCost는 비워 둡니다.");
        for (String key : ALL_KEYS) estimateAssistance(assistance, key);
        return new InitialPreparation(fields, references, assistance);
    }

    public InitialPreparation createIndependent() {
        return createFromMarketAndBusinessModel(mapper.createObjectNode(), mapper.createObjectNode(),
            mapper.createObjectNode(), null, null);
    }

    public boolean applyConceptDefaults(ObjectNode fields, ObjectNode references, JsonNode hypotheses) {
        if (hypotheses == null || !hypotheses.isObject() || hypotheses.isEmpty()) return false;
        boolean changed = !references.path("conceptHypotheses").isObject();
        ObjectNode reference = references.putObject("conceptHypotheses");
        reference.set("values", hypotheses.deepCopy());
        reference.put("label", "컨셉 단계에서 확정한 검증 가정");
        reference.put("provenance", "marketAnalysisSeedSnapshot.finalHypotheses");

        String revenueModel = financialRevenueModel(hypothesisValue(hypotheses, "revenueModel").asText(""));
        if (revenueModel != null && canApplyConceptDefault(fields.path("revenueModel"))) {
            assumedText(fields, "revenueModel", revenueModel, "CONCEPT_HYPOTHESIS",
                "concept.finalHypotheses.revenueModel", "컨셉 단계에서 확정한 수익 모델 가정입니다.");
            changed = true;
        }
        BigDecimal price = numericPrice(hypothesisValue(hypotheses, "price"));
        if (price == null || price.signum() <= 0) return changed;
        if (("ONE_TIME".equals(revenueModel) || "HYBRID".equals(revenueModel))
                && canApplyConceptDefault(fields.path("unitPrice"))) {
            assumedMoney(fields, "unitPrice", price, "KRW", "CONCEPT_HYPOTHESIS",
                "concept.finalHypotheses.price", "컨셉 단계에서 확정한 가격 가정입니다.");
            changed = true;
        }
        if (("SUBSCRIPTION".equals(revenueModel) || "HYBRID".equals(revenueModel))
                && canApplyConceptDefault(fields.path("monthlySubscriptionPrice"))) {
            assumedMoney(fields, "monthlySubscriptionPrice", price, "KRW", "CONCEPT_HYPOTHESIS",
                "concept.finalHypotheses.price", "컨셉 단계에서 확정한 가격 가정입니다.");
            changed = true;
        }
        return changed;
    }

    public boolean applyMarketDefaults(ObjectNode fields, JsonNode marketResult) {
        JsonNode price = marketResult.path("market").path("price");
        if (!price.path("base").isNumber() || price.path("base").decimalValue().signum() <= 0) return false;
        boolean changed = false;
        for (String key : List.of("unitPrice", "monthlySubscriptionPrice")) {
            if (canApplyMarketDefault(fields.path(key))) {
                assumedMoney(fields, key, price.path("base").decimalValue(), price.path("currency").asText("KRW"),
                    "MARKET_ANALYSIS_ASSUMPTION", "market.price.base",
                    "시장 분석의 가격 가설이며 재무 입력에서 수정할 수 있습니다.");
                changed = true;
            }
        }
        return changed;
    }

    public boolean applyBusinessModelDefaults(ObjectNode fields, JsonNode businessModelResult) {
        JsonNode handoff = businessModelResult.path("bm").path("financialHandoff");
        String revenueModel = financialRevenueModel(handoff.path("revenueModel").asText(""));
        boolean changed = false;
        if (revenueModel != null && canApplyBusinessModelDefault(fields.path("revenueModel"))) {
            assumedText(fields, "revenueModel", revenueModel, "BUSINESS_MODEL_ASSUMPTION",
                "bm.financialHandoff.revenueModel", "BM 분석에서 제안한 수익 모델 가정입니다.");
            changed = true;
        }
        BigDecimal price = handoff.path("priceBase").isNumber()
            ? handoff.path("priceBase").decimalValue() : numericPrice(handoff.path("pricingLogic"));
        if (price == null || price.signum() <= 0) return changed;
        String effectiveModel = revenueModel == null
            ? fields.path("revenueModel").path("value").asText("") : revenueModel;
        for (String key : "HYBRID".equals(effectiveModel)
                ? List.of("unitPrice", "monthlySubscriptionPrice")
                : List.of("ONE_TIME".equals(effectiveModel) ? "unitPrice" : "monthlySubscriptionPrice")) {
            if (canApplyBusinessModelDefault(fields.path(key))) {
                assumedMoney(fields, key, price, "KRW", "BUSINESS_MODEL_ASSUMPTION",
                    "bm.financialHandoff.priceBase", "BM 분석에서 제안한 가격 가정입니다.");
                changed = true;
            }
        }
        return changed;
    }

    private JsonNode hypothesisValue(JsonNode hypotheses, String key) {
        JsonNode value = hypotheses.path(key).path("value");
        return value.isMissingNode() ? hypotheses.path(key) : value;
    }

    private boolean canApplyConceptDefault(JsonNode field) {
        return field.isObject() && !field.path("readOnly").asBoolean(false) && !present(field.path("value"));
    }

    private boolean canApplyMarketDefault(JsonNode field) {
        if (!field.isObject() || field.path("readOnly").asBoolean(false)) return false;
        return !present(field.path("value")) || "CONCEPT_HYPOTHESIS".equals(field.path("source").asText());
    }

    private boolean canApplyBusinessModelDefault(JsonNode field) {
        if (!field.isObject() || field.path("readOnly").asBoolean(false)) return false;
        String source = field.path("source").asText();
        return !present(field.path("value")) || "CONCEPT_HYPOTHESIS".equals(source)
            || "MARKET_ANALYSIS_ASSUMPTION".equals(source);
    }

    private String financialRevenueModel(String value) {
        String normalized = value == null ? "" : value.toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) return null;
        boolean subscription = normalized.contains("구독") || normalized.contains("subscription")
            || normalized.contains("saas");
        boolean oneTime = normalized.contains("직접 판매") || normalized.contains("일회")
            || normalized.contains("판매") || normalized.contains("구매") || normalized.contains("product");
        if (subscription && oneTime) return "HYBRID";
        if (subscription) return "SUBSCRIPTION";
        return oneTime ? "ONE_TIME" : null;
    }

    private BigDecimal numericPrice(JsonNode value) {
        if (value == null || value.isNull() || value.isMissingNode()) return null;
        if (value.isNumber()) return value.decimalValue();
        if (value.path("amount").isNumber()) return value.path("amount").decimalValue();
        if (!value.isTextual()) return null;
        Matcher matcher = Pattern.compile("([0-9][0-9,]*)").matcher(value.asText());
        if (!matcher.find()) return null;
        try { return new BigDecimal(matcher.group(1).replace(",", "")); }
        catch (NumberFormatException ignored) { return null; }
    }

    private void assumedMoney(ObjectNode fields, String key, BigDecimal amount, String currency,
            String source, String path, String note) {
        ObjectNode item = fields.putObject(key);
        ObjectNode value = item.putObject("value");
        value.put("amount", amount);
        value.put("currency", currency);
        item.put("source", source);
        item.put("decision", "ASSUMPTION");
        item.put("readOnly", false);
        item.putNull("sourceSnapshotId");
        item.put("provenance", path);
        item.put("sourceNote", note);
    }

    private void assumedText(ObjectNode fields, String key, String value, String source,
            String path, String note) {
        ObjectNode item = fields.putObject(key);
        item.put("value", value);
        item.put("source", source);
        item.put("decision", "ASSUMPTION");
        item.put("readOnly", false);
        item.putNull("sourceSnapshotId");
        item.put("provenance", path);
        item.put("sourceNote", note);
    }

    private void assumedMoneyIfOpen(ObjectNode fields, String key, JsonNode amount, JsonNode source,
            String path, String note) {
        if (fields.path(key).path("readOnly").asBoolean(false)) return;
        ObjectNode item = fields.putObject(key); ObjectNode value = item.putObject("value");
        value.put("amount", amount.decimalValue()); value.put("currency", source.path("currency").asText("KRW"));
        item.put("source", "MARKET_ANALYSIS_ASSUMPTION"); item.put("decision", "ASSUMPTION");
        item.put("readOnly", false); item.put("provenance", path); item.put("sourceNote", note);
    }

    private void assumedTextIfOpen(ObjectNode fields, String key, String value, String path, String note) {
        if (fields.path(key).path("readOnly").asBoolean(false)) return;
        ObjectNode item = fields.putObject(key); item.put("value", value);
        item.put("source", "BUSINESS_MODEL_ASSUMPTION"); item.put("decision", "ASSUMPTION");
        item.put("readOnly", false); item.put("provenance", path); item.put("sourceNote", note);
    }

    private String revenueModel(String value) {
        String normalized = value == null ? "" : value.toUpperCase(java.util.Locale.ROOT);
        if (normalized.contains("구독") || normalized.contains("SUBSCRIPTION")) return "SUBSCRIPTION";
        if (normalized.contains("혼합") || normalized.contains("HYBRID") || normalized.contains("MIXED")) return "HYBRID";
        if (!normalized.isBlank()) return "ONE_TIME";
        return null;
    }

    private void inheritMoneyOrOpen(ObjectNode fields, String key, JsonNode aggregate, JsonNode provenance,
            TechOpsInputSnapshot snapshot) {
        JsonNode candidate = nested(aggregate, key);
        if (validMoney(candidate)) inherited(fields, key, candidate, provenance, snapshot, "requiredFacts." + parent(key) + "." + key);
        else open(fields, key);
    }

    private void inheritTargetsOrOpen(ObjectNode fields, JsonNode targets, JsonNode provenance,
            TechOpsInputSnapshot snapshot) {
        if (ThreeYearTargetsContract.valid(targets)) inherited(fields, "threeYearTargets", targets, provenance, snapshot,
            "requiredFacts.threeYearTargets");
        else open(fields, "threeYearTargets");
    }

    private void inheritDirectOrOpen(ObjectNode fields, String key, JsonNode facts, JsonNode provenance,
            TechOpsInputSnapshot snapshot) {
        JsonNode candidate = facts.path(key);
        boolean valid = "newCustomerCount".equals(key) ? candidate.isNumber() && candidate.asDouble() >= 0 : validMoney(candidate);
        if (valid) inherited(fields, key, candidate, provenance.path(key), snapshot, "requiredFacts." + key);
        else open(fields, key);
    }

    private JsonNode nested(JsonNode aggregate, String key) {
        JsonNode direct = aggregate.path(key);
        if (!direct.isMissingNode()) return direct;
        return aggregate.path("breakdown").path(key);
    }

    private void inherited(ObjectNode fields, String key, JsonNode value, JsonNode sourceProvenance,
            TechOpsInputSnapshot snapshot, String path) {
        ObjectNode item = fields.putObject(key);
        item.set("value", value.deepCopy());
        item.put("source", sourceProvenance.path("source").asText("TECH_OPS_INPUT"));
        item.put("decision", sourceProvenance.path("decision").asText("LOCKED"));
        item.put("readOnly", true);
        item.put("sourceSnapshotId", snapshot.getId());
        item.put("provenance", path);
    }

    private void open(ObjectNode fields, String key) {
        ObjectNode item = fields.putObject(key);
        item.putNull("value");
        item.put("source", "USER_INPUT");
        item.put("decision", "OPEN");
        item.put("readOnly", false);
        item.putNull("sourceSnapshotId");
        item.putNull("provenance");
    }

    private void reference(ObjectNode references, String key, JsonNode value, JsonNode provenance,
            TechOpsInputSnapshot snapshot) {
        ObjectNode item = references.putObject(key);
        item.set("value", value.isMissingNode() ? mapper.nullNode() : value.deepCopy());
        item.set("provenance", provenance.isMissingNode() ? mapper.nullNode() : provenance.deepCopy());
        item.put("sourceSnapshotId", snapshot.getId());
        item.put("label", "기술·운영 단계에서 가져옴");
        if ("fixedOperatingCost".equals(key) && value.path("amount").isNumber()
                && "MONTHLY".equalsIgnoreCase(value.path("period").asText(""))) {
            ObjectNode annual = item.putObject("annualEquivalent");
            annual.put("amount", value.path("amount").decimalValue().multiply(BigDecimal.valueOf(12)));
            annual.put("currency", value.path("currency").asText("KRW"));
            annual.put("source", "SYSTEM_CALCULATION");
        }
    }

    private void assistance(ObjectNode root, String key, String explanation, String example) {
        ObjectNode item = root.putObject(key);
        item.put("explanation", explanation);
        item.put("example", example);
        item.putNull("proposalValue");
        item.put("source", "AI_ESTIMATE");
        item.put("decision", "PROPOSED");
        item.put("providerStatus", "NOT_CONNECTED");
    }

    private void estimateAssistance(ObjectNode root, String key) {
        ObjectNode item = root.withObject(key);
        if (!item.has("explanation")) item.put("explanation", "값 입력이 어려우면 AI 추천을 요청할 수 있습니다.");
        if (!item.has("example")) item.put("example", "추천값은 사용자 확인 전까지 재무 입력으로 사용되지 않습니다.");
        item.putNull("proposalValue"); item.putNull("assumptions"); item.putNull("confidence");
        item.put("source", "AI_ESTIMATE"); item.put("decision", "PROPOSED");
        item.put("proposalVersion", 0); item.put("estimateStatus", "NONE");
        item.putNull("activeTaskRunId"); item.putNull("safeError");
    }

    private boolean validMoney(JsonNode value) {
        return value.isObject() && value.path("amount").isNumber() && value.path("amount").asDouble() >= 0
            && !value.path("currency").asText("").isBlank();
    }

    private String parent(String key) { return key.startsWith("annualFixed") ? "fixedOperatingCost" : "initialInvestment"; }
    public static boolean present(JsonNode value) {
        if (value == null || value.isNull() || value.isMissingNode()) return false;
        if (value.isTextual()) return !value.asText().isBlank();
        if (value.isArray()) return !value.isEmpty();
        return !value.isObject() || !value.isEmpty();
    }

    public record InitialPreparation(ObjectNode financialFields, ObjectNode upstreamReferences, ObjectNode assistance) {}
}
