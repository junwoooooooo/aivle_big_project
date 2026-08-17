package com.aivle.backend.pipeline.market;

import java.text.Normalizer;
import java.util.Locale;
import org.springframework.stereotype.Component;

/** 확정 사업안의 고객 단위와 거래 구조로 Research2 시장 산정 계열을 결정한다. */
@Component
public class MarketStrategySelector {
    private static final String[] TRANSACTION = {
        "마켓플레이스", "marketplace", "거래 수수료", "중개 수수료", "transaction commission",
        "판매 수수료", "상품 판매", "제품 판매", "커머스", "commerce", "거래액", "gmv"
    };
    private static final String[] ORGANIZATION = {
        "b2b", "기업용", "기업 대상", "사업자", "소상공인", "매장", "사업장", "점포",
        "법인", "조직", "팀 단위", "운영자", "원장", "merchant", "enterprise", "per store", "per seat"
    };
    private static final String[] PERSON = {
        "b2c", "직장인", "지역 주민", "주민", "개인", "사용자", "소비자", "회원", "사람",
        "consumer", "community", "매칭", "matching", "모바일 서비스", "구독형 앱"
    };

    public Selection select(String... semanticParts) {
        String text = normalize(String.join(" ", semanticParts));
        int transaction = score(text, TRANSACTION);
        int organization = score(text, ORGANIZATION);
        int person = score(text, PERSON);

        if (transaction > 0 && transaction >= organization) {
            return new Selection("C", "TRANSACTION_VALUE", "시장 거래액",
                "거래·중개·상품 판매 구조가 명시되어 거래액 × 점유율로 산정한다.");
        }
        if (organization > 0 && organization >= person) {
            return new Selection("A", "ORGANIZATION_UNIT", "대상 사업체 수",
                "구매·도입 단위가 사업체 또는 조직이므로 사업체 수 × 침투율 × 단가로 산정한다.");
        }
        if (person > 0) {
            return new Selection("B", "POPULATION_UNIT", "대상 개인 수",
                "구매·이용 단위가 개인이므로 대상 인구 × 침투율 × 단가로 산정한다.");
        }
        return new Selection("D", "PROXY_REQUIRED", "직접 관측 가능한 대리 지표",
            "고객 단위나 거래액 기준이 확정되지 않아 유사시장 대리 지표로 검증한다.");
    }

    private static int score(String text, String[] anchors) {
        int value = 0;
        for (String anchor : anchors) if (text.contains(anchor)) value++;
        return value;
    }

    private static String normalize(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFKC)
            .toLowerCase(Locale.ROOT);
    }

    public record Selection(String series, String strategy, String denominator, String reason) {}
}
