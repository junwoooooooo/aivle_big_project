package com.aivle.backend.pipeline.legal.application;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/** Resolves the currently supported legal jurisdiction without silently defaulting to KR. */
@Component
public class LegalJurisdictionResolver {
    private static final List<String> FOREIGN_MARKERS = List.of(
        "미국", "일본", "중국", "캐나다", "호주", "영국", "프랑스", "독일", "싱가포르", "대만",
        "홍콩", "베트남", "태국", "인도", "인도네시아", "필리핀", "말레이시아", "유럽", "eu",
        "usa", "u.s.", "united states", "japan", "china", "global", "글로벌", "해외"
    );
    private static final List<String> KR_MARKERS = List.of(
        "대한민국", "한국", "국내", "전국", "서울", "부산", "대구", "인천", "광주", "대전", "울산",
        "세종", "경기", "강원", "충북", "충남", "전북", "전남", "경북", "경남", "제주",
        "republic of korea", "south korea"
    );

    public Jurisdiction resolve(String targetRegion) {
        if (targetRegion == null || targetRegion.isBlank()) return Jurisdiction.UNSUPPORTED;
        String normalized = Normalizer.normalize(targetRegion, Normalizer.Form.NFKC)
            .toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
        if (normalized.equals("kr") || normalized.equals("kor")) return Jurisdiction.KR;
        if (FOREIGN_MARKERS.stream().anyMatch(normalized::contains)) return Jurisdiction.UNSUPPORTED;
        return KR_MARKERS.stream().anyMatch(normalized::contains)
            ? Jurisdiction.KR : Jurisdiction.UNSUPPORTED;
    }

    public enum Jurisdiction { KR, UNSUPPORTED }
}
