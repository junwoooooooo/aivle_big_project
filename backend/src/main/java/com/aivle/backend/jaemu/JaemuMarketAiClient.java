package com.aivle.backend.jaemu;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class JaemuMarketAiClient {
    private static final Logger log = LoggerFactory.getLogger(JaemuMarketAiClient.class);
    private final ObjectMapper mapper;
    private final RestClient restClient;
    private final String openAiKey;
    private final String openAiModel;
    private final String openAiUrl;
    private final String tavilyKey;

    public JaemuMarketAiClient(
        ObjectMapper mapper,
        @Value("${app.ai.api-key:}") String openAiKey,
        @Value("${app.ai.model:gpt-4o-mini}") String openAiModel,
        @Value("${app.ai.base-url:https://api.openai.com/v1/chat/completions}") String openAiUrl,
        @Value("${tavily.key:}") String tavilyKey
    ) {
        this.mapper = mapper;
        this.openAiKey = openAiKey == null ? "" : openAiKey.trim();
        this.openAiModel = blank(openAiModel) ? "gpt-4o-mini" : openAiModel.trim();
        this.openAiUrl = normalizeOpenAiUrl(openAiUrl);
        this.tavilyKey = tavilyKey == null ? "" : tavilyKey.trim();
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(35));
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    public Optional<MarketAiHints> analyze(JaemuPipelineRequest request, String category, String modelType) {
        if (blank(openAiKey) || blank(tavilyKey)) {
            log.info("Jaemu market AI skipped: openAiKeySet={} tavilyKeySet={}", !blank(openAiKey), !blank(tavilyKey));
            return Optional.empty();
        }
        try {
            List<SearchSource> sources = search(request, category);
            if (sources.isEmpty()) return Optional.empty();
            String content = complete(request, category, modelType, sources);
            JsonNode root = mapper.readTree(content);
            return Optional.of(new MarketAiHints(
                number(root, "tam"),
                number(root, "cagr"),
                number(root, "recommendedPrice"),
                number(root, "unitCost"),
                strings(root, "supplyDemandNotes"),
                products(root.get("competitorProducts")),
                strings(root, "differentiationCandidates"),
                strings(root, "warnings"),
                sources
            ));
        } catch (Exception failure) {
            log.warn("Jaemu market AI fallback: {}", failure.toString());
            return Optional.empty();
        }
    }

    private List<SearchSource> search(JaemuPipelineRequest request, String category) {
        String query = String.join(" ",
            request.productName(), category, request.targetCustomer(), "시장규모 성장률 경쟁제품 가격 차별화");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("api_key", tavilyKey);
        body.put("query", query);
        body.put("search_depth", "advanced");
        body.put("max_results", 6);
        body.put("include_answer", false);
        String raw = restClient.post()
            .uri("https://api.tavily.com/search")
            .header(HttpHeaders.CONTENT_TYPE, "application/json")
            .body(body)
            .retrieve()
            .body(String.class);
        JsonNode results = mapper.readTree(raw).get("results");
        List<SearchSource> sources = new ArrayList<>();
        if (results == null || !results.isArray()) return sources;
        for (JsonNode item : results) {
            sources.add(new SearchSource(text(item, "title"), text(item, "url"), text(item, "content")));
        }
        return sources;
    }

    private String complete(JaemuPipelineRequest request, String category, String modelType, List<SearchSource> sources) {
        String prompt = """
            당신은 시장분석 엔진입니다. 입력 컨셉과 Tavily 검색 결과를 바탕으로 BM/재무에 넘길 JSON만 작성하세요.
            숫자를 모르면 null을 사용하세요. 가격 0원은 유효 가격으로 쓰지 마세요.
            JSON 스키마:
            {
              "tam": number|null,
              "cagr": number|null,
              "recommendedPrice": number|null,
              "unitCost": number|null,
              "supplyDemandNotes": ["시장 공급·수요 정량 근거"],
              "competitorProducts": [{"company":"", "model":"", "price":number|null, "features":[""], "sourceUrl":""}],
              "differentiationCandidates": [""],
              "warnings": [""]
            }
            """;
        Map<String, Object> user = new LinkedHashMap<>();
        user.put("idea_detail", Map.of(
            "original_idea", request.productName(),
            "clarified_problem", request.problem(),
            "intended_value", request.valueProposition()
        ));
        user.put("concept", Map.of(
            "concept_name", request.productName(),
            "problem", request.problem(),
            "target_customer", request.targetCustomer(),
            "solution", request.solution(),
            "core_value", request.valueProposition(),
            "category", category,
            "alternatives", request.competitors(),
            "differentiation", request.valueProposition(),
            "revenue_model", modelType
        ));
        user.put("sources", sources);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", openAiModel);
        body.put("response_format", Map.of("type", "json_object"));
        body.put("messages", List.of(
            Map.of("role", "system", "content", prompt),
            Map.of("role", "user", "content", mapper.writeValueAsString(user))
        ));
        String raw = restClient.post()
            .uri(openAiUrl)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + openAiKey)
            .header(HttpHeaders.CONTENT_TYPE, "application/json")
            .body(body)
            .retrieve()
            .body(String.class);
        JsonNode root = mapper.readTree(raw);
        return root.get("choices").get(0).get("message").get("content").asText();
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String normalizeOpenAiUrl(String value) {
        if (blank(value)) return "https://api.openai.com/v1/chat/completions";
        String trimmed = value.trim();
        if (trimmed.endsWith("/chat/completions")) return trimmed;
        if (trimmed.endsWith("/")) return trimmed + "chat/completions";
        if (trimmed.endsWith("/v1")) return trimmed + "/chat/completions";
        return trimmed;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? "" : value.asText();
    }

    private static Double number(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || !value.isNumber() ? null : value.asDouble();
    }

    private static List<String> strings(JsonNode node, String field) {
        JsonNode array = node == null ? null : node.get(field);
        List<String> values = new ArrayList<>();
        if (array == null || !array.isArray()) return values;
        for (JsonNode item : array) values.add(item.asText());
        return values;
    }

    private static List<AiProduct> products(JsonNode array) {
        List<AiProduct> values = new ArrayList<>();
        if (array == null || !array.isArray()) return values;
        for (JsonNode item : array) {
            Long price = null;
            JsonNode priceNode = item.get("price");
            if (priceNode != null && priceNode.isNumber() && priceNode.asLong() > 0) price = priceNode.asLong();
            values.add(new AiProduct(
                text(item, "company"),
                text(item, "model"),
                price,
                strings(item, "features"),
                text(item, "sourceUrl")
            ));
        }
        return values;
    }

    public record MarketAiHints(
        Double tam,
        Double cagr,
        Double recommendedPrice,
        Double unitCost,
        List<String> supplyDemandNotes,
        List<AiProduct> competitorProducts,
        List<String> differentiationCandidates,
        List<String> warnings,
        List<SearchSource> sources
    ) { }

    public record AiProduct(String company, String model, Long price, List<String> features, String sourceUrl) { }

    public record SearchSource(String title, String url, String content) { }
}
