package com.aivle.backend.integration.ai.legal;

import com.aivle.backend.analysis.legal.entity.*;
import com.aivle.backend.common.entity.RiskLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 결정론적 Mock. 입력 내용에 따라 프레시락 시나리오를 재현한다:
 * <ul>
 *   <li>섹션에 "악취 30%"가 있으면 광고·마케팅 수정 요청(수정안 A/B)을 방출한다.</li>
 *   <li>"활성탄" 확정 정보가 없으면 산업별 규제 질문 1건을 방출한다.</li>
 *   <li>5개 범주는 "판매 개시 전" 할 일을, 나머지는 무조치 문구를 낸다.</li>
 *   <li>INCREMENTAL이면 rerunCategories만 실제 생성하고 나머지는 자리표시자만 낸다
 *       (백엔드가 승계 finding으로 덮어쓴다).</li>
 * </ul>
 */
public class MockLegalReviewAiClient implements LegalReviewAiClient {
    private static final Logger log = LoggerFactory.getLogger(MockLegalReviewAiClient.class);

    public static final String AD_TRIGGER = "악취 30%";
    public static final String FACT_KEY_TRIGGER = "활성탄";
    public static final String NO_ACTION = "현재 계획 기준으로는 별도 조치가 필요하지 않습니다.";
    /** Mock은 법적 확정을 주장하지 않는다 — 모든 판단 이유가 이 유보 문구로 끝난다. */
    public static final String UNCERTAINTY =
        "실제 적용 여부는 구체적인 운영 방식과 적용 지역에 따라 달라질 수 있습니다.";
    public static final String ACTIVATED_CARBON_QUESTION =
        "활성탄 필터가 화학제품안전법상 안전확인대상 생활화학제품에 해당하는지 관할 기관에 확인이 필요합니다.";

    private static final Map<LegalCategory, String> TODO_ACTIONS = new LinkedHashMap<>();
    static {
        TODO_ACTIONS.put(LegalCategory.BUSINESS_REGISTRATION, "통신판매업 신고 (판매 개시 전)");
        TODO_ACTIONS.put(LegalCategory.LICENSE_AND_PERMIT, "KC 안전확인 대상 여부 확인 (판매 개시 전)");
        TODO_ACTIONS.put(LegalCategory.PRIVACY_AND_DATA, "개인정보 처리방침 수립 (판매 개시 전)");
        TODO_ACTIONS.put(LegalCategory.CONSUMER_PROTECTION, "청약철회·환불 규정 정비 (판매 개시 전)");
        TODO_ACTIONS.put(LegalCategory.TAX_AND_FINANCIAL, "사업자등록·부가세 신고 체계 확인 (판매 개시 전)");
    }

    /**
     * 범주별 Mock 근거·논리. 실 파이프라인이 내는 것과 같은 모양이어야 화면 검증이 의미를 갖는다.
     * 이전 Mock은 전 범주에 같은 문구("확정된 사업계획의 정보만으로…")를 넣어 화면이 전부
     * 똑같이 보였다. 문구는 예시이며 실제 법령 해석이 아니다.
     */
    private record Story(
        String topic, String pathReason, String lawName, String article, String articleTitle,
        String plainSummary, String whyRelevant,
        String sanctionArticle, String consequence
    ) {}

    private static final Map<LegalCategory, Story> STORIES = new EnumMap<>(LegalCategory.class);
    static {
        STORIES.put(LegalCategory.BUSINESS_REGISTRATION, new Story(
            "전자상거래·통신판매", "자사몰·오픈마켓을 통한 비대면 판매 계획이 확인됩니다.",
            "전자상거래 등에서의 소비자보호에 관한 법률", "제12조", "통신판매업자의 신고 등",
            "온라인으로 물건을 팔려면 상호·주소·연락처 등을 관할 시·군·구청에 통신판매업으로 신고해야 합니다.",
            "자사몰과 오픈마켓에서 직접 판매하므로 통신판매업자 신고 의무가 그대로 적용됩니다.",
            "제42조", "신고 없이 판매를 개시하면 신고의무 위반으로 과태료·벌칙 대상이 될 수 있습니다."));
        STORIES.put(LegalCategory.LICENSE_AND_PERMIT, new Story(
            "생활용품 안전·표시", "일반 소비자가 사용하는 실물 생활용품을 제조·판매하는 계획입니다.",
            "전기용품 및 생활용품 안전관리법", "제15조", "안전기준준수대상생활용품의 안전기준 준수",
            "안전기준이 정해진 생활용품은 그 기준에 맞게 만들어야 하고, 기준에 맞지 않으면 팔 수 없습니다.",
            "제품의 최종 소재와 용도에 따라 안전기준준수대상에 들어가는지가 갈립니다.",
            null, "안전기준 대상인데 확인 없이 판매하면 수거·판매중지 명령과 벌칙 대상이 될 수 있습니다."));
        STORIES.put(LegalCategory.PRIVACY_AND_DATA, new Story(
            "개인정보 처리", "구독 회원의 주문·배송 정보를 수집해 재구매 추천에 활용하는 계획입니다.",
            "개인정보 보호법", "제30조", "개인정보 처리방침의 수립 및 공개",
            "고객 정보를 수집·이용하려면 무엇을 어떤 목적으로 얼마나 보관하는지 적은 처리방침을 만들어 누구나 볼 수 있게 공개해야 합니다.",
            "구매 이력을 저장해 필터 교체 주기를 추천하므로 개인정보 처리자에 해당합니다.",
            "제75조", "처리방침을 공개하지 않으면 과태료 대상이 될 수 있습니다."));
        STORIES.put(LegalCategory.CONSUMER_PROTECTION, new Story(
            "전자상거래 소비자 보호", "온라인 구독 판매로 청약철회·환불 처리가 발생합니다.",
            "전자상거래 등에서의 소비자보호에 관한 법률", "제17조", "청약철회등",
            "온라인으로 산 물건은 받은 날부터 7일 안에 이유 없이 반품할 수 있고, 판매자는 이를 막을 수 없습니다.",
            "정기 구독으로 소비자에게 계속 배송하므로 회차별 청약철회 처리 절차가 필요합니다.",
            "제21조", "철회를 부당하게 제한하면 금지행위에 해당해 시정조치·과태료 대상이 될 수 있습니다."));
        STORIES.put(LegalCategory.TAX_AND_FINANCIAL, new Story(
            "사업자등록·부가세", "구독료를 정기 수취하는 계속·반복적 매출 구조입니다.",
            "부가가치세법", "제8조", "사업자등록",
            "사업을 시작하면 20일 안에 세무서에 사업자등록을 해야 하고, 등록 전 매출도 신고 대상입니다.",
            "월 구독료를 계속 받는 구조이므로 등록과 부가세 신고 체계가 먼저 필요합니다.",
            null, "등록이 늦으면 미등록 가산세가 붙고 매입세액 공제를 받지 못할 수 있습니다."));
        STORIES.put(LegalCategory.ADVERTISING_AND_MARKETING, new Story(
            "표시·광고 실증", "검증 가능한 수치를 핵심 광고 카피로 쓰는 계획이 확인됩니다.",
            "표시·광고의 공정화에 관한 법률", "제5조", "표시·광고 내용의 실증 등",
            "광고에 수치나 성능을 주장하려면 그것을 뒷받침하는 시험·조사 자료를 미리 갖고 있어야 하고, 요청받으면 제출해야 합니다.",
            "실증 자료 확보 전에 구체적 개선율을 광고 카피로 쓰는 계획이 그대로 남아 있습니다.",
            "제17조", "실증 자료 없이 수치를 광고하면 부당한 표시·광고로 시정조치·과징금·벌칙 대상이 될 수 있습니다."));
        STORIES.put(LegalCategory.TERMS_AND_CONTRACT, new Story(
            "약관 규제", "구독 이용약관으로 다수 고객과 같은 조건으로 계약합니다.",
            "약관의 규제에 관한 법률", "제3조", "약관의 작성 및 설명의무 등",
            "약관은 고객이 알아보기 쉽게 쓰고, 중요한 내용은 따로 설명해야 합니다. 설명하지 않은 조항은 계약 내용으로 주장할 수 없습니다.",
            "구독 해지·환불 조건을 약관으로 정하는 계획이나 명시·설명 절차가 아직 확인되지 않습니다.",
            null, "설명하지 않은 조항은 무효가 되어 분쟁 시 사업자에게 불리하게 작용할 수 있습니다."));
        STORIES.put(LegalCategory.INTELLECTUAL_PROPERTY, new Story(
            "상표·디자인", "제품명과 용기 형상을 브랜드로 쓰는 계획입니다.",
            "상표법", "제108조", "침해로 보는 행위",
            "남의 등록상표와 같거나 비슷한 표시를 같은 종류의 상품에 쓰면 상표권 침해가 됩니다.",
            "제품명 사용 계획은 있으나 선행 상표 조사 여부가 계획서에 없습니다.",
            null, "선행 상표와 충돌하면 사용 중지와 손해배상 청구를 받을 수 있습니다."));
        STORIES.put(LegalCategory.LABOR_AND_EMPLOYMENT, new Story(
            "근로 조건", "대표 외 인력을 두는 조직 계획이 있습니다.",
            "근로기준법", "제17조", "근로조건의 명시",
            "직원을 채용하면 임금·근로시간·휴일 등을 적은 근로계약서를 써서 직원에게 주어야 합니다.",
            "인력 구성 계획은 있으나 고용 형태와 인원이 특정되지 않아 적용 범위 확인이 필요합니다.",
            null, "계약서를 주지 않으면 사업주에게 과태료가 부과될 수 있습니다."));
        STORIES.put(LegalCategory.INDUSTRY_SPECIFIC, new Story(
            "생활화학제품 규제", "흡착·탈취 기능을 내세운 소재를 제품에 넣는 계획입니다.",
            "생활화학제품 및 살생물제의 안전관리에 관한 법률", "제10조", "안전확인대상생활화학제품의 신고",
            "정부가 지정한 생활화학제품은 시험을 거쳐 안전기준에 맞는지 확인받고 신고한 뒤에 팔 수 있습니다.",
            "필터 소재와 표시 문구에 따라 안전확인대상에 들어갈 수 있어 해당 여부 확인이 필요합니다.",
            null, "대상인데 신고 없이 판매하면 판매금지·회수 명령과 벌칙 대상이 될 수 있습니다."));
    }

    private final List<LegalReviewAiRequest> invocations = new CopyOnWriteArrayList<>();

    /** 테스트가 mode/rerunCategories/confirmedFacts 수신을 단언할 수 있게 호출 이력을 노출한다. */
    public List<LegalReviewAiRequest> invocations() {
        return List.copyOf(invocations);
    }

    /** 테스트 간 호출 이력 초기화. */
    public void reset() {
        invocations.clear();
    }

    @Override
    public LegalReviewAiResponse review(LegalReviewAiRequest request) {
        invocations.add(request);
        boolean incremental = request.mode() == ReviewMode.INCREMENTAL;
        Set<LegalCategory> rerun = incremental
            ? EnumSet.copyOf(request.rerunCategories())
            : EnumSet.allOf(LegalCategory.class);

        Optional<LegalReviewAiRequest.Section> adSection = request.sections().stream()
            .filter(section -> section.content() != null && section.content().contains(AD_TRIGGER))
            .findFirst();
        boolean factPresent = request.confirmedFacts().stream()
            .anyMatch(fact -> fact.key() != null && fact.key().contains(FACT_KEY_TRIGGER));

        List<LegalReviewAiResponse.Finding> findings = new ArrayList<>();
        for (LegalCategory category : LegalCategory.values()) {
            if (!rerun.contains(category)) {
                findings.add(placeholder(category));
                continue;
            }
            findings.add(generated(category, adSection.orElse(null), request));
        }

        List<LegalReviewAiResponse.Question> questions = new ArrayList<>();
        if (rerun.contains(LegalCategory.INDUSTRY_SPECIFIC) && !factPresent) {
            questions.add(new LegalReviewAiResponse.Question(
                ACTIVATED_CARBON_QUESTION,
                "안전확인대상 여부에 따라 표시·신고 의무가 달라질 수 있습니다.",
                List.of(LegalCategory.INDUSTRY_SPECIFIC)));
        }

        List<LegalReviewAiResponse.RevisionRequestPayload> revisionRequests = new ArrayList<>();
        if (rerun.contains(LegalCategory.ADVERTISING_AND_MARKETING) && adSection.isPresent()) {
            var section = adSection.get();
            String quote = sentenceAround(section.content(), AD_TRIGGER);
            revisionRequests.add(new LegalReviewAiResponse.RevisionRequestPayload(
                LegalCategory.ADVERTISING_AND_MARKETING,
                section.code(),
                quote,
                "실증 자료 없는 구체적 수치 광고는 표시광고법상 부당 광고로 판단될 수 있습니다.",
                List.of(
                    new LegalReviewAiResponse.SuggestionPayload("A",
                        "공인기관 실증 시험을 완료한 뒤 시험 결과 범위 내 문구로 광고를 개시한다"),
                    new LegalReviewAiResponse.SuggestionPayload("B",
                        "광고 카피에서 구체적 수치를 제외하고 '탈취 성능 강화 설계'로 표기한다"))));
        }

        List<String> generatedCategories = rerun.stream().map(Enum::name).sorted().toList();
        log.info("mock legal review invoked: mode={} rerunCategories={} generatedCategories={} "
                + "(비생성 범주는 자리표시자 — 재실행하지 않음)",
            request.mode(), request.rerunCategories(), generatedCategories);

        return new LegalReviewAiResponse(
            "mock", "mock-legal-review-v1", "mock-legal-" + request.structuredPlanId(),
            adSection.isPresent() ? RiskLevel.HIGH : RiskLevel.MEDIUM,
            "확정된 계획에서 우선 확인할 법률·규제 영역을 식별했습니다. 이는 자문 또는 적법성 판정이 아닙니다.",
            findings,
            questions,
            revisionRequests
        );
    }

    private LegalReviewAiResponse.Finding generated(
        LegalCategory category, LegalReviewAiRequest.Section adSection, LegalReviewAiRequest request
    ) {
        boolean dirtyAd = adSection != null;
        String firstSectionCode = request.sections().isEmpty()
            ? "BUSINESS_OVERVIEW" : request.sections().get(0).code();
        Story story = STORIES.get(category);
        if (category == LegalCategory.ADVERTISING_AND_MARKETING) {
            // 근거 섹션 = 광고 문구가 실제로 있는 섹션 — 증분 재검토의 역인덱스가 이 매핑에 의존한다
            List<String> sectionCodes = dirtyAd ? List.of(adSection.code()) : List.of(firstSectionCode);
            String quote = dirtyAd ? sentenceAround(adSection.content(), AD_TRIGGER) : null;
            return new LegalReviewAiResponse.Finding(
                category,
                dirtyAd ? LegalApplicability.APPLICABLE : LegalApplicability.POSSIBLY_APPLICABLE,
                dirtyAd ? RiskLevel.HIGH : RiskLevel.LOW,
                "광고·표시 규제",
                dirtyAd
                    ? "실증되지 않은 성능 수치를 광고 카피로 사용하는 계획이 확인됩니다."
                    : "현재 계획의 광고 문구에서 위험 요소가 확인되지 않습니다.",
                "광고 표현 방식과 실증 자료 확보 여부에 따라 위법성이 달라질 수 있습니다.",
                NO_ACTION,
                dirtyAd ? evidence(story) : List.of(),
                dirtyAd ? reasoning(story, quote, NO_ACTION, null) : null,
                sectionCodes,
                dirtyAd,
                new BigDecimal("0.7500"));
        }
        String action = TODO_ACTIONS.getOrDefault(category, NO_ACTION);
        boolean hasTodo = TODO_ACTIONS.containsKey(category);
        String timing = hasTodo ? "판매 개시 전" : null;
        return new LegalReviewAiResponse.Finding(
            category,
            hasTodo ? LegalApplicability.POSSIBLY_APPLICABLE : LegalApplicability.INSUFFICIENT_INFORMATION,
            hasTodo ? RiskLevel.MEDIUM : RiskLevel.UNKNOWN,
            displayName(category),
            story == null
                ? "확정된 사업계획의 정보만으로 사전 확인이 필요한 영역입니다."
                : story.whyRelevant(),
            story == null
                ? "구체적인 운영 방식과 적용 지역에 따라 의무가 달라질 수 있습니다."
                : story.pathReason() + " " + story.consequence() + " " + UNCERTAINTY,
            action,
            evidence(story),
            reasoning(story, null, hasTodo ? stripTiming(action) : null, timing),
            List.of(firstSectionCode),
            hasTodo,
            new BigDecimal("0.7500"));
    }

    private List<LegalReviewAiResponse.Evidence> evidence(Story story) {
        if (story == null) {
            return List.of();
        }
        // 조문 원문 발췌는 싣지 않는다 — Mock이 축자 검증되지 않은 법령 문구를 만들어
        // 내면 화면 캡처만으로 실제 조문처럼 유통된다. 전문은 법제처 링크로 보낸다.
        return List.of(new LegalReviewAiResponse.Evidence(
            story.lawName(), story.article(), story.articleTitle(),
            LegalReviewAiResponse.EvidenceRole.REQUIREMENT,
            story.plainSummary(), story.whyRelevant(), null, null,
            "https://www.law.go.kr/법령/" + story.lawName().replace(" ", "")));
    }

    private LegalReviewAiResponse.Reasoning reasoning(
        Story story, String planQuote, String action, String timing
    ) {
        if (story == null) {
            return null;
        }
        return new LegalReviewAiResponse.Reasoning(
            new LegalReviewAiResponse.Reasoning.PlanBasis(
                List.of(), planQuote == null ? List.of() : List.of(planQuote)),
            new LegalReviewAiResponse.Reasoning.RegulatoryPath(
                story.topic(), "적용 가능", story.pathReason()),
            List.of(new LegalReviewAiResponse.Reasoning.Obligation(
                story.article(), story.lawName(), story.plainSummary())),
            new LegalReviewAiResponse.Reasoning.Consequence(
                story.sanctionArticle() == null ? List.of() : List.of(story.sanctionArticle()),
                story.consequence()),
            new LegalReviewAiResponse.Reasoning.Conclusion(action, timing));
    }

    /** 할 일 문구에서 "(판매 개시 전)" 꼬리를 떼어 사슬의 결론 문장으로 쓴다. */
    private String stripTiming(String action) {
        int paren = action.lastIndexOf(" (");
        return paren < 0 ? action : action.substring(0, paren);
    }

    /** INCREMENTAL에서 재실행하지 않는 범주 — 백엔드가 승계 finding으로 덮어쓴다. */
    private LegalReviewAiResponse.Finding placeholder(LegalCategory category) {
        return new LegalReviewAiResponse.Finding(
            category,
            LegalApplicability.INSUFFICIENT_INFORMATION,
            RiskLevel.UNKNOWN,
            displayName(category),
            "승계 예정 범주입니다(재실행하지 않음).",
            "이전 검토 결과가 승계되며 상황에 따라 달라질 수 있습니다.",
            NO_ACTION,
            List.of(),
            null,
            List.of(),
            false,
            null);
    }

    private String sentenceAround(String content, String trigger) {
        int index = content.indexOf(trigger);
        if (index < 0) {
            return trigger;
        }
        int start = Math.max(content.lastIndexOf('.', index), content.lastIndexOf('\n', index)) + 1;
        int end = content.indexOf('.', index);
        if (end < 0) {
            end = content.length();
        } else {
            end = end + 1;
        }
        return content.substring(start, end).strip();
    }

    private String displayName(LegalCategory category) {
        return category.name().replace('_', ' ');
    }
}
