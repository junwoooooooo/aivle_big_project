package com.aivle.backend.pipeline.concept.application;

import com.aivle.backend.pipeline.concept.domain.ConceptCanonicalizer;
import com.aivle.backend.pipeline.legal.application.LegalJurisdictionResolver.Jurisdiction;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Component
public class ConceptLegalFactPatternMapper {
    private final ObjectMapper mapper;
    private final com.aivle.backend.pipeline.legal.application.LegalJurisdictionResolver jurisdictions;

    @Autowired
    public ConceptLegalFactPatternMapper(ObjectMapper mapper,
            com.aivle.backend.pipeline.legal.application.LegalJurisdictionResolver jurisdictions) {
        this.mapper = mapper;
        this.jurisdictions = jurisdictions;
    }

    public ConceptLegalFactPatternMapper(ObjectMapper mapper) {
        this(mapper, new com.aivle.backend.pipeline.legal.application.LegalJurisdictionResolver());
    }

    public Result map(JsonNode candidate) {
        if (!"2.0".equals(candidate.path("schemaVersion").asText())) {
            throw new IllegalArgumentException("ConceptCandidateV2 is required");
        }
        Map<String, JsonNode> semantics = semantics(candidate.path("valueSemantics"));
        ObjectNode pattern = mapper.createObjectNode();
        pattern.put("schemaVersion", "2.0");
        Jurisdiction jurisdiction = jurisdictions.resolve(candidate.path("targetRegion").asText());
        if (jurisdiction != Jurisdiction.KR) {
            throw new IllegalArgumentException("LEGAL_JURISDICTION_UNSUPPORTED");
        }
        pattern.put("jurisdiction", jurisdiction.name());
        pattern.set("actorRoles", governedList(candidate, semantics, "actorRoles"));
        pattern.set("platformRole", governedText(candidate, semantics, "platformRole"));

        ObjectNode roles = pattern.putObject("commercialRoles");
        roles.set("providerRole", governedText(candidate, semantics, "providerRole"));
        roles.set("sellerRole", governedText(candidate, semantics, "sellerRole"));
        roles.set("intermediaryRole", governedText(candidate, semantics, "intermediaryRole"));

        pattern.set("transactionFlow", governedList(candidate, semantics, "transactionFlow"));
        pattern.set("paymentFlow", governedList(candidate, semantics, "paymentFlow"));
        pattern.set("personalDataUsage", governedList(candidate, semantics, "personalDataUsage"));
        pattern.set("physicalActivities", governedList(candidate, semantics, "physicalActivities"));
        pattern.set("partnerRoles", partnerRoles(candidate, semantics));
        pattern.set("qualificationRequirements", governedList(candidate, semantics, "qualificationRequirements"));
        pattern.set("advertisingClaims", governedList(candidate, semantics, "advertisingClaims"));
        pattern.set("operatingModel", governedText(candidate, semantics, "operatingModel"));

        ObjectNode hypotheses = pattern.putObject("hypotheses");
        hypotheses.set("targetRegion", legalSensitive(candidate, semantics, "targetRegion", "LEGAL_SENSITIVE"));
        hypotheses.set("revenueModel", legalSensitive(candidate, semantics, "revenueModel", "LEGAL_SENSITIVE"));
        hypotheses.set("price", legalSensitive(candidate, semantics, "price", "LEGAL_SENSITIVE"));
        hypotheses.set("channels", legalSensitive(candidate, semantics, "channels", "POTENTIALLY_LEGAL_SENSITIVE"));
        hypotheses.set("differentiators", legalSensitive(candidate, semantics, "differentiators", "POTENTIALLY_LEGAL_SENSITIVE"));

        String canonicalJson = mapper.writeValueAsString(pattern);
        if (canonicalJson.contains("preMarketSom")) {
            throw new IllegalStateException("pre-market SOM must not enter legal fact pattern");
        }
        return new Result(pattern, ConceptCanonicalizer.hash(canonicalJson));
    }

    private ObjectNode governedText(JsonNode candidate, Map<String, JsonNode> semantics, String field) {
        String value = candidate.path(field).asText();
        if (value.isBlank()) throw new IllegalArgumentException("legal fact field is missing: " + field);
        ObjectNode result = governance(semantics, field);
        result.put("value", value);
        return result;
    }

    private ObjectNode governedList(JsonNode candidate, Map<String, JsonNode> semantics, String field) {
        JsonNode value = candidate.path(field);
        if (!value.isArray()) throw new IllegalArgumentException("legal fact list is missing: " + field);
        ObjectNode result = governance(semantics, field);
        result.set("value", value.deepCopy());
        return result;
    }

    private ObjectNode partnerRoles(JsonNode candidate, Map<String, JsonNode> semantics) {
        ObjectNode result = mapper.createObjectNode();
        result.set("partnerModel", governedText(candidate, semantics, "partnerModel"));
        result.set("partnerRequirements", governedList(candidate, semantics, "partnerRequirements"));
        return result;
    }

    private ObjectNode legalSensitive(JsonNode candidate, Map<String, JsonNode> semantics,
            String field, String sensitivity) {
        ObjectNode result = governedText(candidate, semantics, field);
        result.put("legalSensitivity", sensitivity);
        return result;
    }

    private ObjectNode governance(Map<String, JsonNode> semantics, String field) {
        JsonNode semantic = semantics.get(field);
        if (semantic == null) throw new IllegalArgumentException("value semantics is missing: " + field);
        ObjectNode result = mapper.createObjectNode();
        for (String key : new String[] {"source", "authority", "decision"}) {
            String value = semantic.path(key).asText();
            if (value.isBlank()) throw new IllegalArgumentException("value semantics is incomplete: " + field);
            result.put(key, value);
        }
        return result;
    }

    private Map<String, JsonNode> semantics(JsonNode values) {
        if (!values.isArray()) throw new IllegalArgumentException("valueSemantics is required");
        Map<String, JsonNode> result = new HashMap<>();
        for (JsonNode value : values) {
            String field = value.path("fieldKey").asText();
            if (field.isBlank() || result.put(field, value) != null) {
                throw new IllegalArgumentException("valueSemantics contains an invalid field");
            }
        }
        return result;
    }

    public record Result(JsonNode factPattern, String factPatternHash) {}
}
