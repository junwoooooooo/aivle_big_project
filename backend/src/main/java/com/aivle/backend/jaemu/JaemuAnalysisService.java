package com.aivle.backend.jaemu;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import org.springframework.stereotype.Service;

@Service
public class JaemuAnalysisService {
    private static final double OPTIMAL_PRICE = 50_000;
    private static final double MAX_TOLERANCE_PRICE = 110_000;
    private final JaemuMarketAiClient marketAiClient;

    public JaemuAnalysisService(JaemuMarketAiClient marketAiClient) {
        this.marketAiClient = marketAiClient;
    }

    public JaemuPipelineResponse pipeline(JaemuPipelineRequest request) {
        String category = category(request);
        String modelType = modelType(request.businessModelType(), category);
        var aiHints = marketAiClient.analyze(request, category, modelType);
        double targetPrice = pick(request.targetPrice(),
            pick(aiHints.map(JaemuMarketAiClient.MarketAiHints::recommendedPrice).orElse(null), recommendedPrice(category, modelType)));
        double unitCogs = pick(request.unitCogs(),
            pick(aiHints.map(JaemuMarketAiClient.MarketAiHints::unitCost).orElse(null), unitCost(category, modelType, targetPrice)));
        long tam = Math.round(pick(request.marketSizeTam(),
            pick(aiHints.map(JaemuMarketAiClient.MarketAiHints::tam).orElse(null), defaultTam(category))));
        double cagr = clamp(pick(request.cagr(),
            pick(aiHints.map(JaemuMarketAiClient.MarketAiHints::cagr).orElse(null), defaultCagr(category))), 0, .8);
        double annualLabor = pick(request.annualLaborCost(), defaultLaborCost(category, modelType));
        double annualOffice = pick(request.annualOfficeCost(), defaultOfficeCost(category));
        double annualInfra = pick(request.annualInfraCost(), defaultInfraCost(category, modelType));
        double initialInvestment = pick(request.initialDevelopmentCost(), defaultDevelopmentCost(category, modelType))
            + pick(request.initialFacilityCost(), defaultFacilityCost(category, modelType))
            + pick(request.initialLicenseCost(), defaultLicenseCost(category));
        double cac = cac(request, targetPrice, modelType);
        List<Integer> targetSales = listOrDefault(request.targetSalesQ(), defaultTargetSales(tam, targetPrice, modelType));
        List<Integer> targetUsers = listOrDefault(request.targetUsers(), defaultTargetUsers(targetSales, modelType));

        JaemuPipelineResponse.IdeaSummary idea = new JaemuPipelineResponse.IdeaSummary(
            request.productName(),
            category,
            request.targetCustomer(),
            request.problem(),
            request.valueProposition()
        );
        JaemuPipelineResponse.LegalReview legal = legalReview(category);
        JaemuPipelineResponse.ConceptInput concept = conceptInput(request, category, modelType);
        JaemuPipelineResponse.MarketJoinData marketJoinData = marketJoinData(request, category, modelType, tam, cagr, targetPrice, aiHints.orElse(null));
        JaemuPipelineResponse.MarketAnalysis market = marketAnalysis(request, category, tam, cagr, targetPrice);
        List<JaemuPipelineResponse.ConceptOption> concepts = concepts(request, category, modelType);
        JaemuPipelineResponse.ConceptOption selected = concepts.get(0);
        JaemuPipelineResponse.BusinessModelCanvas canvas = canvas(request, category, modelType, selected);
        JaemuPipelineResponse.BmAnalysis bm = new JaemuPipelineResponse.BmAnalysis(
            concepts,
            selected,
            canvas,
            bmScore(market, targetPrice, unitCogs, modelType),
            bmDecision(market, targetPrice, unitCogs),
            List.of(
                "단위당 권장가격은 시장 포지셔닝과 BM 수익모델을 기준으로 산출했습니다.",
                "최근 시장성장률은 입력값이 있으면 우선 사용하고, 없으면 카테고리 벤치마크 CAGR을 사용했습니다.",
                modelType.equals("SERVICE")
                    ? "서비스형 원가는 MAU x 유저당 월간 서버/API 원가 x 12 방식으로 계산됩니다."
                    : "실물/단품 판매 원가는 연간 판매수량 x 단위당 제조원가 방식으로 계산됩니다."
            )
        );
        JaemuAnalysisRequest financialInput = new JaemuAnalysisRequest(
            request.productName(),
            category,
            modelType,
            tam,
            cagr,
            targetPrice,
            unitCogs,
            annualLabor,
            annualOffice,
            annualInfra,
            initialInvestment,
            targetSales,
            targetUsers,
            cac,
            modelType.equals("SERVICE") ? 6.0 : 3.5,
            .10
        );
        return new JaemuPipelineResponse(
            idea,
            legal,
            concept,
            pipelineStates(marketJoinData, aiHints.isPresent()),
            marketJoinData,
            market,
            bm,
            financialSources(request, modelType, aiHints.isPresent()),
            financialInput,
            analyze(financialInput)
        );
    }

    public JaemuAnalysisResponse analyze(JaemuAnalysisRequest input) {
        String modelType = modelType(input.businessModelType(), input.category());
        double demand = demandFactor(input.targetPrice());
        double retention = Math.max(0.5, Math.pow(1 - input.monthlyChurnRate() / 100 * 0.4, 3));
        double factor = demand * retention;
        long fixedAnnualCost = Math.round(input.annualLaborCost() + input.annualOfficeCost() + input.annualServerCost());
        List<Long> quantities = new ArrayList<>();
        List<JaemuAnalysisResponse.YearlyResult> yearly = new ArrayList<>();
        List<Long> operatingIncomes = new ArrayList<>();
        for (int year = 0; year < 3; year++) {
            double marketTrend = Math.pow(1 + input.cagr(), year);
            long quantity = Math.round(input.targetSalesQ().get(year) * marketTrend * factor);
            long activeUsers = Math.max(10, Math.round(input.targetUsers().get(year) * factor));
            long revenue = Math.round((modelType.equals("SERVICE") ? activeUsers * 12.0 : quantity) * input.targetPrice());
            long cogs = Math.round(modelType.equals("SERVICE") ? activeUsers * input.unitCogs() * 12.0 : quantity * input.unitCogs());
            long grossProfit = revenue - cogs;
            long sga = Math.round(fixedAnnualCost + activeUsers * input.cac());
            long operatingIncome = grossProfit - sga;
            long pretax = operatingIncome + 2_000_000;
            long netIncome = pretax - Math.round(Math.max(pretax, 0) * 0.2);
            quantities.add(quantity);
            operatingIncomes.add(operatingIncome);
            yearly.add(new JaemuAnalysisResponse.YearlyResult(year + 1, quantity, revenue, cogs, grossProfit,
                sga, operatingIncome, netIncome, revenue == 0 ? 0 : operatingIncome * 100.0 / revenue));
        }
        List<JaemuAnalysisResponse.Scenario> scenarios = List.of(
            scenario("기준", 1.0, 1.0, input, quantities, fixedAnnualCost, modelType),
            scenario("낙관", 1.15, 1.0, input, quantities, fixedAnnualCost, modelType),
            scenario("비관", 0.7, 1.2, input, quantities, fixedAnnualCost, modelType)
        );
        List<Double> distribution = npvDistribution(input, fixedAnnualCost, modelType);
        long baseNpv = Math.round(npv(input.initialInvestment(), operatingIncomes, input.discountRate()));
        double probability = distribution.stream().filter(value -> value > 0).count() * 100.0 / distribution.size();
        String grade = probability >= 70 ? "양호" : probability >= 40 ? "보통" : "고위험";
        return new JaemuAnalysisResponse(input.productName(), input.category(), yearly, scenarios, distribution,
            new JaemuAnalysisResponse.Metrics(probability, baseNpv, scenarios.get(0).breakEvenMonth(), fixedAnnualCost, demand, retention),
            new JaemuAnalysisResponse.Report(grade,
                List.of("3년 차 예상 영업이익률: " + String.format("%.1f", yearly.get(2).operatingMargin()) + "%",
                    "기준 시나리오 손익분기점: " + scenarios.get(0).breakEvenMonth() + "개월",
                    "몬테카르로 NPV 양수 확률: " + String.format("%.1f", probability) + "%"),
                actions(input, probability),
                List.of(modelType.equals("SERVICE") ? "서비스형 원가는 MAU x 월간 서버/API 원가 x 12로 계산합니다." : "실물형 원가는 판매수량 x 단위당 제조원가로 계산합니다.",
                    "가격 민감도 기준 가격은 50,000원으로 설정했습니다.",
                    "법인세율은 흑자 발생 시 20%, 영업외수익은 연 200만 원으로 가정합니다.")));
    }

    private JaemuAnalysisResponse.Scenario scenario(String name, double salesFactor, double cogsFactor,
            JaemuAnalysisRequest input, List<Long> quantities, long fixedCost, String modelType) {
        List<Long> cash = new ArrayList<>();
        List<Long> revenue = new ArrayList<>();
        List<Long> cost = new ArrayList<>();
        double balance = input.initialInvestment();
        int bep = 24;
        for (int month = 1; month <= 24; month++) {
            int yearIndex = month <= 12 ? 0 : 1;
            double q = quantities.get(yearIndex) / 12.0 * salesFactor;
            double users = Math.max(10, input.targetUsers().get(yearIndex) * salesFactor);
            long monthlyRevenue = Math.round((modelType.equals("SERVICE") ? users : q) * input.targetPrice());
            long monthlyCost = Math.round((modelType.equals("SERVICE") ? users * input.unitCogs() : q * input.unitCogs() * cogsFactor)
                + fixedCost / 12.0 + users * input.cac() / 12.0);
            long net = monthlyRevenue - monthlyCost;
            balance += net;
            if (net >= 0 && bep == 24) bep = month;
            revenue.add(monthlyRevenue);
            cost.add(monthlyCost);
            cash.add(Math.round(balance));
        }
        return new JaemuAnalysisResponse.Scenario(name, cash, revenue, cost, bep);
    }

    private List<Double> npvDistribution(JaemuAnalysisRequest input, long fixedCost, String modelType) {
        Random random = new Random(42);
        List<Double> values = new ArrayList<>();
        for (int run = 0; run < 1000; run++) {
            double price = Math.max(0, input.targetPrice() * (1 + random.nextGaussian() * .06));
            double unitCost = Math.max(0, input.unitCogs() * (1 + random.nextGaussian() * .06));
            double factor = demandFactor(price) * Math.max(.5, Math.pow(1 - input.monthlyChurnRate() / 100 * .4, 3));
            List<Long> operating = new ArrayList<>();
            for (int year = 0; year < 3; year++) {
                long q = Math.round(input.targetSalesQ().get(year) * Math.pow(1 + input.cagr(), year) * factor);
                long users = Math.max(10, Math.round(input.targetUsers().get(year) * factor));
                long revenue = Math.round((modelType.equals("SERVICE") ? users * 12.0 : q) * price);
                long cogs = Math.round(modelType.equals("SERVICE") ? users * unitCost * 12.0 : q * unitCost);
                long sga = Math.round(fixedCost + users * input.cac());
                operating.add(revenue - cogs - sga);
            }
            values.add(npv(input.initialInvestment(), operating, input.discountRate()));
        }
        return values;
    }

    private String category(JaemuPipelineRequest request) {
        String text = (request.productName() + " " + request.solution() + " " + request.industryHint()).toLowerCase(Locale.ROOT);
        if (containsAny(text, "안경", "eyeglass", "glasses", "웨어러블", "거치대", "스마트안경")) return "스마트 웨어러블 액세서리";
        if ((text.contains("전기차") || text.contains("ev")) && text.contains("충전")) return "전기차 인프라";
        if (containsAny(text, "ai", "인공지능")) return "AI 서비스";
        if (containsAny(text, "구독", "saas", "앱", "app")) return "디지털 구독 서비스";
        if (containsAny(text, "교육", "학습")) return "에듀테크";
        if (containsAny(text, "헬스", "건강", "의료")) return "헬스케어";
        if (containsAny(text, "스마트", "iot", "디바이스")) return "스마트 디바이스";
        return "신규 소비재/서비스";
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) if (text.contains(keyword)) return true;
        return false;
    }

    private String modelType(String value, String category) {
        if (value != null && value.toLowerCase(Locale.ROOT).contains("service")) return "SERVICE";
        if (value != null && (value.contains("구독") || value.contains("SaaS") || value.contains("앱"))) return "SERVICE";
        if (category.contains("AI") || category.contains("구독")) return "SERVICE";
        return "PRODUCT";
    }

    private double pick(Double value, double fallback) {
        return value == null || value <= 0 ? fallback : value;
    }

    private List<Integer> listOrDefault(List<Integer> value, List<Integer> fallback) {
        return value == null || value.size() != 3 ? fallback : value;
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private double recommendedPrice(String category, String modelType) {
        if (modelType.equals("SERVICE")) return category.contains("AI") ? 49_000 : 29_000;
        if (category.contains("웨어러블")) return 50_000;
        if (category.contains("전기차")) return 2_500_000;
        if (category.contains("디바이스")) return 80_000;
        return 50_000;
    }

    private double unitCost(String category, String modelType, double price) {
        if (modelType.equals("SERVICE")) return category.contains("AI") ? 3_500 : 1_800;
        if (category.contains("웨어러블")) return Math.min(15_000, price * .35);
        if (category.contains("전기차")) return price * .58;
        return price * .35;
    }

    private long defaultTam(String category) {
        if (category.contains("웨어러블")) return 50_000_000_000L;
        if (category.contains("전기차")) return 800_000_000_000L;
        if (category.contains("AI")) return 300_000_000_000L;
        if (category.contains("구독")) return 150_000_000_000L;
        return 80_000_000_000L;
    }

    private double defaultCagr(String category) {
        if (category.contains("웨어러블")) return .15;
        if (category.contains("전기차")) return .18;
        if (category.contains("AI")) return .22;
        if (category.contains("구독")) return .14;
        return .12;
    }

    private double defaultLaborCost(String category, String modelType) {
        return modelType.equals("SERVICE") ? 96_000_000 : 80_000_000;
    }

    private double defaultOfficeCost(String category) {
        return 24_000_000;
    }

    private double defaultInfraCost(String category, String modelType) {
        if (modelType.equals("SERVICE")) return category.contains("AI") ? 36_000_000 : 18_000_000;
        return 12_000_000;
    }

    private double defaultDevelopmentCost(String category, String modelType) {
        return modelType.equals("SERVICE") ? 90_000_000 : 120_000_000;
    }

    private double defaultFacilityCost(String category, String modelType) {
        return modelType.equals("SERVICE") ? 20_000_000 : 60_000_000;
    }

    private double defaultLicenseCost(String category) {
        return category.contains("웨어러블") ? 20_000_000 : 10_000_000;
    }

    private double cac(JaemuPipelineRequest request, double price, String modelType) {
        if (request.totalMarketingCost() != null && request.totalSalesCost() != null
            && request.newCustomers() != null && request.newCustomers() > 0) {
            return (request.totalMarketingCost() + request.totalSalesCost()) / request.newCustomers();
        }
        return Math.max(3_000, price * (modelType.equals("SERVICE") ? .18 : .10));
    }

    private List<Integer> defaultTargetSales(long tam, double price, String modelType) {
        int base = Math.max(300, (int) Math.round(tam / price * .0015));
        if (modelType.equals("SERVICE")) base = Math.max(1_000, base);
        return List.of(base, Math.round(base * 2.2f), Math.round(base * 4.5f));
    }

    private List<Integer> defaultTargetUsers(List<Integer> sales, String modelType) {
        if (modelType.equals("SERVICE")) return sales;
        return List.of(Math.max(100, sales.get(0) / 3), Math.max(300, sales.get(1) / 3), Math.max(600, sales.get(2) / 3));
    }

    private JaemuPipelineResponse.MarketAnalysis marketAnalysis(JaemuPipelineRequest request, String category, long tam, double cagr, double price) {
        return new JaemuPipelineResponse.MarketAnalysis(
            category + " 시장은 '" + request.targetCustomer() + "'의 명확한 문제를 기준으로 TAM을 좁혀 검증하는 방식이 적합합니다.",
            tam,
            cagr,
            request.targetCustomer(),
            List.of("초기 구매자는 기능 수보다 문제 해결의 확실성과 사용 편의성을 더 크게 봅니다.", "권장가격 " + Math.round(price) + "원은 경쟁 대안 대비 체감 가치가 설명되어야 합니다.", "최근 성장률은 직접 시장 통계가 없으면 인접 시장 CAGR로 보정해야 합니다."),
            competitors(request),
            List.of("시장 범위를 넓게 잡으면 TAM이 과대 추정될 수 있습니다.", "실물 제품은 제조원가와 불량/AS 비용이 마진을 흔들 수 있습니다.", "구독형 서비스는 서버/API 원가와 이탈률이 장기 수익성을 좌우합니다."),
            List.of(request.marketSizeTam() == null ? "TAM은 카테고리 벤치마크로 임시 산출했습니다." : "TAM은 사용자가 입력한 사업계획서 값을 사용했습니다.",
                request.cagr() == null ? "CAGR은 카테고리 벤치마크로 임시 산출했습니다." : "CAGR은 사용자가 입력한 최근 시장성장률을 사용했습니다.")
        );
    }

    private JaemuPipelineResponse.LegalReview legalReview(String category) {
        return new JaemuPipelineResponse.LegalReview(
            "CONDITIONAL",
            category.contains("웨어러블")
                ? List.of("전자파 적합성, 충전 안전성, 배터리/전원부 안전 기준 확인 필요")
                : List.of("제품/서비스 제공 관련 인증·표시·개인정보 처리 기준 확인 필요"),
            category.contains("웨어러블")
                ? List.of("KC 전자파 적합등록", "전기용품 안전 확인", "무선충전 적용 시 관련 인증")
                : List.of("서비스 약관", "개인정보 처리방침", "결제/환불 정책"),
            List.of("초기 인증 리스크를 낮춘 MVP 사양으로 시장 테스트 가능")
        );
    }

    private JaemuPipelineResponse.ConceptInput conceptInput(JaemuPipelineRequest request, String category, String modelType) {
        return new JaemuPipelineResponse.ConceptInput(
            request.productName(),
            request.problem(),
            request.targetCustomer(),
            request.solution() == null || request.solution().isBlank() ? request.valueProposition() : request.solution(),
            request.valueProposition(),
            category,
            competitors(request),
            differentiationFeatures(request),
            modelType.equals("SERVICE") ? "월 구독료 + 프리미엄 기능" : "단품 판매 + 액세서리/AS 옵션"
        );
    }

    private List<String> differentiationFeatures(JaemuPipelineRequest request) {
        List<String> result = new ArrayList<>();
        if (request.valueProposition() != null) result.add(request.valueProposition());
        String solution = request.solution() == null ? "" : request.solution();
        if (solution.contains("충전")) result.add("충전 통합");
        if (solution.contains("상태")) result.add("상태 표시");
        if (solution.contains("보관")) result.add("보관 편의성");
        if (result.size() < 2) result.add("사용 편의성 개선");
        return result.stream().distinct().toList();
    }

    private JaemuPipelineResponse.MarketJoinData marketJoinData(
        JaemuPipelineRequest request,
        String category,
        String modelType,
        long tam,
        double cagr,
        double price,
        JaemuMarketAiClient.MarketAiHints aiHints
    ) {
        long customerBase = Math.max(10_000, Math.round(tam / Math.max(price, 1) * .08));
        List<JaemuPipelineResponse.MarketMetric> supplyMetrics = new ArrayList<>(List.of(
            metric("시장 공급·도입 추정", Math.round(customerBase * .18), "개/명", "최근 기준", aiHints == null ? "시장분석 추정" : "시장분석 추정 + LIVE 관찰 보정", ""),
            metric("고객·이용자 기반", customerBase, "명", "최근 기준", "타깃 고객 기반 추정", ""),
            metric("최근 수요 변화", cagr * 100, "%", "최근 2~3년", aiHints == null ? "CAGR 입력/벤치마크" : "Tavily/OpenAI + 입력/벤치마크", "")
        ));
        if (aiHints != null && aiHints.supplyDemandNotes() != null && !aiHints.supplyDemandNotes().isEmpty()) {
            supplyMetrics.add(metric("Tavily/OpenAI 공급·수요 관찰", aiHints.supplyDemandNotes().size(), "건", "검색 시점", "Tavily + OpenAI", ""));
        }
        List<JaemuPipelineResponse.MarketMetric> sizeMetrics = List.of(
            metric("현재 시장 금액", tam, "원", "현재", aiHints == null ? "TAM 입력/벤치마크" : "Tavily/OpenAI + 입력/벤치마크", ""),
            metric("1년 후 시장 금액", Math.round(tam * (1 + cagr)), "원", "1년 후", "성장률 적용", ""),
            metric("2년 후 시장 금액", Math.round(tam * Math.pow(1 + cagr, 2)), "원", "2년 후", "성장률 적용", "")
        );
        List<JaemuPipelineResponse.CompetitorProduct> products = competitorProducts(request, category, price, aiHints);
        List<Long> prices = products.stream().filter(p -> p.price() != null && p.price() > 0).map(JaemuPipelineResponse.CompetitorProduct::price).sorted().toList();
        JaemuPipelineResponse.PriceSummary priceSummary = prices.isEmpty()
            ? new JaemuPipelineResponse.PriceSummary(0, 0, 0, 0, "원")
            : new JaemuPipelineResponse.PriceSummary(prices.size(), prices.get(0), prices.get(prices.size() / 2), prices.get(prices.size() - 1), "원");
        List<JaemuPipelineResponse.DifferentiationRow> diffRows = differentiationRows(request, products);
        List<String> candidates = diffRows.stream().filter(row -> row.verdict().equals("차별화 후보"))
            .map(JaemuPipelineResponse.DifferentiationRow::conceptFeature).toList();
        String range = priceSummary.pricedProductCount() == 0
            ? "가격 확인 필요"
            : priceSummary.minimum() + "~" + priceSummary.maximum() + "원";
        return new JaemuPipelineResponse.MarketJoinData(
            new JaemuPipelineResponse.MarketSupplyDemand(supplyMetrics,
                aiHints == null
                    ? List.of("OpenAI/Tavily LIVE 호출이 실패했거나 키가 런타임에 전달되지 않아 벤치마크로 표시합니다.", "공급/수요 원문 숫자 회수 실패 시 USER_REQUIRED로 표시해야 합니다.")
                    : mergeWarnings(List.of("Tavily/OpenAI 검색 관찰값을 반영했습니다."), aiHints.warnings())),
            new JaemuPipelineResponse.MarketSizeGrowth(sizeMetrics,
                List.of(new JaemuPipelineResponse.GrowthCalculation("시장 성장률", cagr * 100, "%", "(최근 시장값 - 과거 시장값) / 과거 시장값"))),
            new JaemuPipelineResponse.CompetitorPrice(products, priceSummary,
                List.of("가격 0원은 유효 가격에서 제외합니다.", "가격이 있는 링크에서 0원으로 읽히는 경우 원문 가격 파서 보강 대상입니다.")),
            new JaemuPipelineResponse.Differentiation(diffRows, candidates),
            new JaemuPipelineResponse.MarketFinalSummary(
                Math.round(tam / 100_000_000.0) + "억 원",
                cagr,
                request.targetCustomer(),
                products.size(),
                range,
                candidates,
                "대표 시장규모는 " + Math.round(tam / 100_000_000.0) + "억 원, 성장률은 " + String.format("%.1f", cagr * 100)
                    + "%입니다. 경쟁제품 " + products.size() + "개 중 가격 확인 대상은 " + priceSummary.pricedProductCount()
                    + "개이며, 차별화 후보는 " + (candidates.isEmpty() ? "추가 검증 필요" : String.join(", ", candidates)) + "입니다."
            )
        );
    }

    private JaemuPipelineResponse.MarketMetric metric(String name, double value, String unit, String period, String source, String url) {
        return new JaemuPipelineResponse.MarketMetric(name, value, unit, period, source, url);
    }

    private List<JaemuPipelineResponse.CompetitorProduct> competitorProducts(
        JaemuPipelineRequest request,
        String category,
        double price,
        JaemuMarketAiClient.MarketAiHints aiHints
    ) {
        if (aiHints != null && aiHints.competitorProducts() != null && !aiHints.competitorProducts().isEmpty()) {
            List<JaemuPipelineResponse.CompetitorProduct> aiProducts = new ArrayList<>();
            for (JaemuMarketAiClient.AiProduct product : aiHints.competitorProducts()) {
                if (product.company() == null || product.company().isBlank()) continue;
                aiProducts.add(new JaemuPipelineResponse.CompetitorProduct(
                    product.company(),
                    product.model() == null || product.model().isBlank() ? product.company() + " 제품/서비스" : product.model(),
                    product.price(),
                    product.price() == null ? "가격 확인 필요" : "Tavily/OpenAI 확인 가격",
                    product.features() == null ? List.of() : product.features(),
                    product.sourceUrl(),
                    product.price() == null ? "FEATURE_ONLY" : "PRICE_VERIFIED"
                ));
            }
            if (!aiProducts.isEmpty()) return aiProducts;
        }
        List<String> competitors = competitors(request);
        List<JaemuPipelineResponse.CompetitorProduct> result = new ArrayList<>();
        for (int i = 0; i < competitors.size(); i++) {
            String company = competitors.get(i);
            long competitorPrice = Math.max(1, Math.round(price * (0.75 + i * 0.18)));
            result.add(new JaemuPipelineResponse.CompetitorProduct(
                company,
                company + " 비교 제품",
                competitorPrice,
                "공개 판매가/동급 제품 추정",
                i % 2 == 0 ? List.of("충전", "보관") : List.of("보관", "디자인"),
                "",
                "PRICE_VERIFIED_OR_ESTIMATED"
            ));
        }
        return result;
    }

    private List<String> mergeWarnings(List<String> base, List<String> extra) {
        List<String> values = new ArrayList<>(base);
        if (extra != null) values.addAll(extra);
        return values;
    }

    private List<JaemuPipelineResponse.DifferentiationRow> differentiationRows(
        JaemuPipelineRequest request,
        List<JaemuPipelineResponse.CompetitorProduct> products
    ) {
        List<JaemuPipelineResponse.DifferentiationRow> rows = new ArrayList<>();
        for (String feature : differentiationFeatures(request)) {
            int supported = 0;
            for (JaemuPipelineResponse.CompetitorProduct product : products) {
                if (product.features().stream().anyMatch(feature::contains)
                    || product.features().stream().anyMatch(item -> item.contains(feature))) {
                    supported++;
                }
            }
            int compared = Math.max(1, products.size());
            double rate = Math.round(supported * 1000.0 / compared) / 10.0;
            String verdict = rate >= 60 ? "기본 경쟁요소" : "차별화 후보";
            rows.add(new JaemuPipelineResponse.DifferentiationRow(feature, supported, compared, rate, verdict));
        }
        return rows;
    }

    private List<JaemuPipelineResponse.PipelineState> pipelineStates(JaemuPipelineResponse.MarketJoinData marketJoinData, boolean liveSearchUsed) {
        String collectionStatus = liveSearchUsed ? "LIVE" : "PARTIAL";
        String collectionOutput = liveSearchUsed
            ? "Tavily 원문 검색과 OpenAI 구조화 분석을 반영"
            : "LIVE 호출 실패/미설정으로 벤치마크와 사용자 입력 기반 처리";
        return List.of(
            state("input", "입력 통합", "DONE", "사용자+컨셉", "idea_detail/legal_review/concept 입력 묶음 생성", List.of("필수 컨셉 8개 필드 확인")),
            state("criteria", "분석 기준 설정", "DONE", "시장분석", "시장 대상·수요·규모·경쟁·차별성 기준 생성", List.of("4개 분석 영역 생성")),
            state("collection", "시장 데이터 수집", collectionStatus, "시장분석", collectionOutput, marketJoinData.marketSupplyDemand().warnings()),
            state("quant", "정량 비교·분석", "DONE", "시장분석", "시장규모·성장률·가격범위·차별성 후보 계산", List.of("0원 가격 제외", "성장률 계산식 보존")),
            state("validation", "검증 및 결과 생성", "DONE", "시장분석", "market_join_data 생성 후 BM에 전달", List.of("BM/재무 핸드오프 가능")),
            state("bm", "BM분석", "DONE", "BM", "컨셉 3안과 BM Canvas 생성", List.of("수익모델/비용구조 재무 연결")),
            state("tech", "기술분석", "SKIPPED", "기술", "현재 미구현: 재무로 건너뜀", List.of("추후 구현 예정")),
            state("finance", "재무분석", "DONE", "재무", "시장/BM/사업계획서 값을 재무 모델에 연결", List.of("가정 수정 시 자동 재계산"))
        );
    }

    private JaemuPipelineResponse.PipelineState state(String id, String label, String status, String owner, String output, List<String> checks) {
        return new JaemuPipelineResponse.PipelineState(id, label, status, owner, output, checks);
    }

    private List<String> competitors(JaemuPipelineRequest request) {
        if (request.competitors() != null && !request.competitors().isBlank()) {
            return List.of(request.competitors().split("\\s*,\\s*"));
        }
        return List.of("기존 대체 제품", "수작업/무구매 대안", "저가형 유사 제품");
    }

    private List<JaemuPipelineResponse.ConceptOption> concepts(JaemuPipelineRequest request, String category, String modelType) {
        String product = request.productName();
        String revenue = modelType.equals("SERVICE") ? "월 구독료 + 프리미엄 기능" : "단품 판매 + 액세서리/AS 옵션";
        return List.of(
            new JaemuPipelineResponse.ConceptOption("concept-a", product + " Core", request.problem() + "을 가장 단순한 제품으로 해결", revenue, 84),
            new JaemuPipelineResponse.ConceptOption("concept-b", product + " Bundle", "제품과 초기 온보딩/보증을 묶어 구매 리스크 완화", revenue + " + 보증/관리 옵션", 78),
            new JaemuPipelineResponse.ConceptOption("concept-c", product + " Platform", category + " 사용 데이터를 기반으로 확장 서비스화", "데이터 기반 프리미엄 기능 + 제휴 수익", 72)
        );
    }

    private JaemuPipelineResponse.BusinessModelCanvas canvas(JaemuPipelineRequest request, String category, String modelType, JaemuPipelineResponse.ConceptOption concept) {
        return new JaemuPipelineResponse.BusinessModelCanvas(
            List.of("제조/개발 파트너", "판매 채널 파트너", "AS/운영 파트너"),
            List.of("문제 검증", "제품/서비스 공급", "사용 데이터 기반 개선"),
            List.of(request.productName() + " 핵심 제품/기술", "고객 피드백 데이터", "운영/CS 역량"),
            List.of(request.valueProposition(), concept.positioning()),
            List.of("초기 구매 지원", "보증/업데이트", "정기 사용 리포트"),
            List.of("직접 판매", "온라인 채널", "제휴 판매"),
            List.of(request.targetCustomer(), "동일 문제를 가진 인접 고객군"),
            modelType.equals("SERVICE")
                ? List.of("개발 인건비", "서버/API 원가", "CAC와 고객지원 비용")
                : List.of("단위당 제조원가", "물류/포장/AS 비용", "CAC와 채널 수수료"),
            List.of(concept.revenueModel(), modelType.equals("SERVICE") ? "월 반복 매출" : "단품 판매 매출", "프리미엄/부가 옵션")
        );
    }

    private int bmScore(JaemuPipelineResponse.MarketAnalysis market, double price, double unitCogs, String modelType) {
        int score = 68;
        if (market.cagr() >= .15) score += 8;
        if (unitCogs / price <= (modelType.equals("SERVICE") ? .25 : .4)) score += 9;
        if (market.tam() >= 50_000_000_000L) score += 6;
        return Math.min(95, score);
    }

    private String bmDecision(JaemuPipelineResponse.MarketAnalysis market, double price, double unitCogs) {
        if (unitCogs / price > .65) return "BM_ISSUE";
        if (market.tam() < 30_000_000_000L) return "MARKET_ISSUE";
        return "PASS_WITH_VALIDATION";
    }

    private List<JaemuPipelineResponse.FinancialInputSource> financialSources(JaemuPipelineRequest request, String modelType, boolean liveSearchUsed) {
        String marketSource = liveSearchUsed ? "LIVE 시장분석 + BM 분석 추정" : "시장/BM 벤치마크 추정";
        String cagrSource = liveSearchUsed ? "LIVE 시장분석 추정" : "시장분석 벤치마크";
        return List.of(
            source("targetPrice", "단위당 권장가격", request.targetPrice() == null ? marketSource : "사용자 고급 오버라이드", "경쟁가격·시장 포지셔닝·BM 수익모델을 기준으로 재무 단가에 연결"),
            source("cagr", "최근 시장성장률", request.cagr() == null ? cagrSource : "사용자 고급 오버라이드", "3개년 판매량/유저 성장 보정에 사용"),
            source("unitCogs", modelType.equals("SERVICE") ? "유저당 월간 서버/API 원가" : "단위당 제조원가", request.unitCogs() == null ? "BM 비용구조 추정" : "사용자 고급 오버라이드", modelType.equals("SERVICE") ? "MAU x 월 원가 x 12" : "연간 판매수량 x 단위 원가"),
            source("annualLaborCost", "연간 고정 인건비", request.annualLaborCost() == null ? "사업계획서 부실: 산업 벤치마크 추정" : "아이디어/사업계획서 입력", "파라미터로 보정 가능"),
            source("annualOfficeCost", "연간 임차/관리비", request.annualOfficeCost() == null ? "사업계획서 부실: 산업 벤치마크 추정" : "아이디어/사업계획서 입력", "파라미터로 보정 가능"),
            source("annualServerCost", "연간 고정 인프라비", request.annualInfraCost() == null ? "사업계획서 부실: 산업 벤치마크 추정" : "아이디어/사업계획서 입력", "파라미터로 보정 가능"),
            source("initialInvestment", "초기투자금", request.initialDevelopmentCost() == null ? "초기개발/설비/특허 항목 추정" : "아이디어/사업계획서 입력", "개발비 + 설비/인프라 + 특허/인허가"),
            source("cac", "고객획득비용", request.newCustomers() == null ? "가격 기반 추정" : "마케팅비+영업비÷신규고객수", "파라미터로 보정 가능"),
            source("targetSalesQ", "3개년 목표판매량", request.targetSalesQ() == null ? "시장/BM 기반 침투율 추정" : "아이디어/사업계획서 입력", "서비스형은 유저수 중심으로 해석"),
            source("targetUsers", "3개년 고객수/MAU", request.targetUsers() == null ? "판매량에서 유도" : "아이디어/사업계획서 입력", "서비스 종류에 따라 MAU 또는 누적 고객수로 사용")
        );
    }

    private JaemuPipelineResponse.FinancialInputSource source(String field, String label, String source, String note) {
        return new JaemuPipelineResponse.FinancialInputSource(field, label, source, note);
    }

    private double demandFactor(double price) {
        if (price <= OPTIMAL_PRICE) return Math.max(.1, Math.pow(price / OPTIMAL_PRICE, .8));
        double over = (price - OPTIMAL_PRICE) / (MAX_TOLERANCE_PRICE - OPTIMAL_PRICE);
        return Math.max(0, Math.pow(Math.max(0, 1 - over), 2.5));
    }

    private double npv(double investment, List<Long> cashFlows, double rate) {
        double value = -investment;
        for (int index = 0; index < cashFlows.size(); index++) {
            value += cashFlows.get(index) / Math.pow(1 + rate, index + 1);
        }
        return value;
    }

    private List<String> actions(JaemuAnalysisRequest input, double probability) {
        List<String> result = new ArrayList<>();
        if (input.monthlyChurnRate() > 10) result.add("월 이탈률이 높습니다. 재구매, 보증, CRM 프로그램으로 유지율을 개선하세요.");
        if (input.cac() > 5_000) result.add("CAC가 높습니다. 추천, 콘텐츠, 제휴 채널을 검토해 획득비를 낮추는 것이 좋습니다.");
        if (input.unitCogs() > input.targetPrice() * .4 && !modelType(input.businessModelType(), input.category()).equals("SERVICE")) {
            result.add("원가율이 높습니다. 공급가 협상 또는 가격 구조 재설계가 필요합니다.");
        }
        if (probability < 40) result.add("NPV 양수 확률이 낮습니다. 판매량과 고정비 가정을 보수적으로 다시 확인하세요.");
        if (result.isEmpty()) result.add("현재 가정은 안정 구간입니다. 실제 판매 데이터가 쌓이면 가격 탄력성과 CAC를 갱신하세요.");
        return result;
    }
}
