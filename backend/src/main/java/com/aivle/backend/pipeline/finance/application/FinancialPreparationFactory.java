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
        for (String key : ALL_KEYS) {
            estimateAssistance(assistance, key);
        }
        return new InitialPreparation(fields, references, assistance);
    }

    /** Carries market/BM evidence forward; market assumptions stay editable until the user confirms them. */
    public InitialPreparation createFromBusinessModel(JsonNode marketResult, JsonNode businessModelResult,
            JsonNode conceptHypotheses, Long businessModelRunId) {
        ObjectNode fields = mapper.createObjectNode();
        for (String key : ALL_KEYS) open(fields, key);
        JsonNode price = marketResult.path("market").path("price");
        if (price.path("base").isNumber() && price.path("base").asDouble() > 0) {
            assumedMoney(fields, "monthlySubscriptionPrice", price.path("base"), price,
                "market.price.base", "시장 가격 가설 — 확인 후 재무 가정으로 확정 필요");
            assumedText(fields, "revenueModel", "SUBSCRIPTION", "market.price", "시장 가격 구조를 바탕으로 한 구독 모델 가설");
        }
        ObjectNode references = mapper.createObjectNode();
        ObjectNode market = references.putObject("marketAnalysis");
        market.set("tam", marketResult.path("market").path("tam").deepCopy());
        market.set("sam", marketResult.path("market").path("sam").deepCopy());
        market.set("growth", marketResult.path("market").path("growth").deepCopy());
        market.set("price", price.deepCopy());
        market.put("label", "시장 규모·성장률·가격·계산 근거");
        market.put("provenance", "marketResearchVersion.result.market");
        ObjectNode bm = references.putObject("businessModel");
        bm.put("sourceRunId", businessModelRunId);
        bm.set("value", businessModelResult.deepCopy());
        bm.put("label", "시장→BM 분석 결과");
        bm.put("provenance", "marketResearchVersion.result");
        applyConceptDefaults(fields, references, conceptHypotheses);
        applyMarketDefaults(fields, marketResult);
        applyBusinessModelDefaults(fields, businessModelResult);
        ObjectNode assistance = mapper.createObjectNode();
        for (String key : ALL_KEYS) estimateAssistance(assistance, key);
        return new InitialPreparation(fields, references, assistance);
    }

    /** Applies the second-level default: market analysis overrides a concept hypothesis, never a user value. */
    public boolean applyMarketDefaults(ObjectNode fields, JsonNode marketResult) {
        JsonNode price = marketResult.path("market").path("price");
        if (!price.path("base").isNumber() || price.path("base").asDouble() <= 0) return false;
        boolean changed = false;
        if (canApplyMarketDefault(fields.path("monthlySubscriptionPrice"))) {
            assumedMoney(fields, "monthlySubscriptionPrice", price.path("base"), price,
                "market.price.base", "시장 분석의 가격 가설이며 재무 입력에서 수정할 수 있습니다.");
            changed = true;
        }
        if (canApplyMarketDefault(fields.path("revenueModel"))) {
            assumedText(fields, "revenueModel", "SUBSCRIPTION", "market.price",
                "시장 분석의 가격 가설을 바탕으로 한 수익 모델 가정입니다.");
            changed = true;
        }
        return changed;
    }

    /** Applies the highest automatic default when BM supplies an explicit price or revenue stream. */
    public boolean applyBusinessModelDefaults(ObjectNode fields, JsonNode businessModelResult) {
        String revenueModel = businessModelRevenueModel(businessModelResult);
        BigDecimal price = businessModelPrice(businessModelResult);
        boolean changed = false;
        if (revenueModel != null && canApplyBusinessModelDefault(fields.path("revenueModel"))) {
            assumedText(fields, "revenueModel", revenueModel, "businessModel.revenueModel",
                "BM 분석에서 제안한 수익 모델 가정이며 재무 입력에서 수정할 수 있습니다.");
            fields.withObject("revenueModel").put("source", "BUSINESS_MODEL_ASSUMPTION");
            changed = true;
        }
        if (price == null || price.signum() <= 0) return changed;
        String effectiveModel = revenueModel == null ? fields.path("revenueModel").path("value").asText("") : revenueModel;
        String priceField = "ONE_TIME".equals(effectiveModel) ? "unitPrice" : "monthlySubscriptionPrice";
        if (canApplyBusinessModelDefault(fields.path(priceField))) {
            assumedMoney(fields, priceField, mapper.valueToTree(price), mapper.createObjectNode(),
                "businessModel.price", "BM 분석에서 제안한 가격 가정이며 재무 입력에서 수정할 수 있습니다.");
            fields.withObject(priceField).put("source", "BUSINESS_MODEL_ASSUMPTION");
            changed = true;
        }
        return changed;
    }

    /** Uses accepted concept hypotheses without overwriting a user-entered or accepted finance value. */
    public boolean applyConceptDefaults(ObjectNode fields, ObjectNode references, JsonNode conceptHypotheses) {
        if (conceptHypotheses == null || !conceptHypotheses.isObject() || conceptHypotheses.isEmpty()) return false;
        boolean changed = !references.path("conceptHypotheses").isObject();
        ObjectNode reference = references.putObject("conceptHypotheses");
        reference.set("values", conceptHypotheses.deepCopy());
        reference.put("label", "컨셉 단계에서 확정한 검증 가정");
        reference.put("provenance", "marketAnalysisSeedSnapshot.finalHypotheses");

        String revenueModel = financialRevenueModel(conceptHypotheses.path("revenueModel").path("value").asText(""));
        if (revenueModel != null && canApplyConceptDefault(fields.path("revenueModel"))) {
            assumedText(fields, "revenueModel", revenueModel, "concept.finalHypotheses.revenueModel",
                "컨셉 단계에서 확정한 수익 모델 가정이며 재무 입력에서 수정할 수 있습니다.");
            fields.withObject("revenueModel").put("source", "CONCEPT_HYPOTHESIS");
            changed = true;
        }
        BigDecimal price = conceptPrice(conceptHypotheses.path("price").path("value"));
        if (price == null || price.signum() <= 0) return changed;
        if (("ONE_TIME".equals(revenueModel) || "HYBRID".equals(revenueModel))
                && canApplyConceptDefault(fields.path("unitPrice"))) {
            assumedMoney(fields, "unitPrice", mapper.valueToTree(price), conceptHypotheses.path("price").path("value"),
                "concept.finalHypotheses.price", "컨셉 단계에서 확정한 가격 가정이며 재무 입력에서 수정할 수 있습니다.");
            fields.withObject("unitPrice").put("source", "CONCEPT_HYPOTHESIS");
            changed = true;
        }
        if (("SUBSCRIPTION".equals(revenueModel) || "HYBRID".equals(revenueModel))
                && canApplyConceptDefault(fields.path("monthlySubscriptionPrice"))) {
            assumedMoney(fields, "monthlySubscriptionPrice", mapper.valueToTree(price), conceptHypotheses.path("price").path("value"),
                "concept.finalHypotheses.price", "컨셉 단계에서 확정한 가격 가정이며 재무 입력에서 수정할 수 있습니다.");
            fields.withObject("monthlySubscriptionPrice").put("source", "CONCEPT_HYPOTHESIS");
            changed = true;
        }
        return changed;
    }

    private boolean canApplyConceptDefault(JsonNode field) {
        if (!field.isObject() || field.path("readOnly").asBoolean(false)) return false;
        String source = field.path("source").asText("");
        return !present(field.path("value"));
    }

    private boolean canApplyMarketDefault(JsonNode field) {
        if (!field.isObject() || field.path("readOnly").asBoolean(false)) return false;
        String source = field.path("source").asText("");
        return !present(field.path("value")) || "CONCEPT_HYPOTHESIS".equals(source);
    }

    private boolean canApplyBusinessModelDefault(JsonNode field) {
        if (!field.isObject() || field.path("readOnly").asBoolean(false)) return false;
        String source = field.path("source").asText("");
        return !present(field.path("value")) || "CONCEPT_HYPOTHESIS".equals(source)
            || "MARKET_ANALYSIS_ASSUMPTION".equals(source);
    }

    private String businessModelRevenueModel(JsonNode result) {
        String direct = result.path("revenueModel").asText("");
        if (!direct.isBlank()) return financialRevenueModel(direct);
        for (JsonNode cell : result.path("canvas").path("cells")) {
            if (!"REVENUE_STREAMS".equals(cell.path("canvasCell").asText())) continue;
            StringBuilder content = new StringBuilder();
            for (JsonNode item : cell.path("content")) content.append(item.asText()).append(' ');
            String model = financialRevenueModel(content.toString());
            if (model != null) return model;
        }
        return null;
    }

    private BigDecimal businessModelPrice(JsonNode result) {
        JsonNode candidate = result.path("pricing").path("price");
        if (candidate.isMissingNode() || candidate.isNull()) candidate = result.path("price");
        if (candidate.isMissingNode() || candidate.isNull()) candidate = result.path("market").path("price");
        if (candidate.path("base").isNumber()) return candidate.path("base").decimalValue();
        if (candidate.path("amount").isNumber()) return candidate.path("amount").decimalValue();
        return conceptPrice(candidate);
    }

    private String financialRevenueModel(String conceptValue) {
        String value = conceptValue.toLowerCase(Locale.ROOT);
        if (value.isBlank()) return null;
        boolean subscription = value.contains("구독") || value.contains("subscription") || value.contains("saas");
        boolean oneTime = value.contains("직접 판매") || value.contains("일회") || value.contains("판매")
            || value.contains("구매") || value.contains("product");
        if (subscription && oneTime) return "HYBRID";
        if (subscription) return "SUBSCRIPTION";
        return oneTime ? "ONE_TIME" : null;
    }

    private BigDecimal conceptPrice(JsonNode value) {
        if (value.isNumber()) return value.decimalValue();
        if (!value.isTextual()) return null;
        Matcher matcher = Pattern.compile("([0-9][0-9,]*)").matcher(value.asText());
        if (!matcher.find()) return null;
        try { return new BigDecimal(matcher.group(1).replace(",", "")); }
        catch (NumberFormatException ignored) { return null; }
    }

    private void assumedMoney(ObjectNode fields, String key, JsonNode amount, JsonNode source, String path, String note) {
        ObjectNode item = fields.putObject(key); ObjectNode value = item.putObject("value");
        value.put("amount", amount.decimalValue()); value.put("currency", source.path("currency").asText("KRW"));
        item.put("source", "MARKET_ANALYSIS_ASSUMPTION"); item.put("decision", "ASSUMPTION"); item.put("readOnly", false);
        item.put("provenance", path); item.put("sourceNote", note);
    }

    private void assumedText(ObjectNode fields, String key, String value, String path, String note) {
        ObjectNode item = fields.putObject(key); item.put("value", value);
        item.put("source", "MARKET_ANALYSIS_ASSUMPTION"); item.put("decision", "ASSUMPTION"); item.put("readOnly", false);
        item.put("provenance", path); item.put("sourceNote", note);
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
        item.putArray("externalEvidence");
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
