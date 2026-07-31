# -*- coding: utf-8 -*-
"""피드백 루프 확장 단위 테스트 (LLM 불필요).

실행: cd ai; python -m pytest legal/test_feedback.py -q
"""
import json

import pytest

from legal import aggregator
from legal import legal_pipeline as lp


SECTIONS = [
    {"code": "BUSINESS_OVERVIEW", "title": "사업 개요",
     "content": "프레시락 미니는 1인 가구용 신선보관 밀폐용기 사업이다."},
    {"code": "PRODUCT_SERVICE", "title": "제품 서비스",
     "content": "활성탄 필터를 적용한 밀폐용기다. 악취 30% 개선을 핵심 광고 카피로 사용한다."},
]

FACTS = [{"key": "활성탄필터.안전확인대상", "value": "비대상",
          "source": "환경산업기술원", "answeredAt": "2026-07-30T09:00:00"}]


# ---------------------------------------------------------------- 확정 정보 주입

def test_build_source_text_appends_fact_block():
    text = lp.build_source_text(SECTIONS, FACTS)
    assert "[확정 정보" in text
    assert "활성탄필터.안전확인대상: 비대상" in text
    assert "환경산업기술원" in text
    # 섹션 본문은 그대로
    assert "악취 30% 개선을 핵심 광고 카피로 사용한다." in text


def test_build_source_text_without_facts_unchanged():
    assert "[확정 정보" not in lp.build_source_text(SECTIONS)


def test_fact_citing_quote_survives_routing_validation():
    """확정 정보를 인용한 evidence_quote가 화이트리스트를 통과해야 한다."""
    source = lp.build_source_text(SECTIONS, FACTS)
    raw = {"routes": [{
        "route_id": "chemical_biocidal", "status": "해당",
        "evidence_quotes": ["활성탄필터.안전확인대상: 비대상"], "reason": "", "confidence": 0.9,
    }]}
    profile = lp.validate_routing(raw, source)
    route = profile["routes"][0]
    assert route["status"] == "해당"  # 인용 생존 → 강등 없음
    assert route["evidence_quotes"] == ["활성탄필터.안전확인대상: 비대상"]


def test_missing_information_normalization_accepts_both_shapes():
    raw = {"routes": [], "missing_information": [
        "평문 질문",
        {"question": "객체 질문", "related_route_ids": ["chemical_biocidal", 123]},
        {"question": "  "},
    ]}
    profile = lp.validate_routing(raw, "본문")
    items = profile["missing_information"]
    assert items[0] == {"question": "평문 질문", "related_route_ids": []}
    assert items[1] == {"question": "객체 질문", "related_route_ids": ["chemical_biocidal"]}
    assert len(items) == 2


# ---------------------------------------------------------------- 라우트 필터 (증분)

def test_filter_routes_keeps_only_rerun_mapped_routes():
    category_map = lp.load_category_map()
    profile = {"routes": [
        {"route_id": "advertising_claims", "status": "해당"},
        {"route_id": "online_sales", "status": "해당"},
        {"route_id": "personal_data", "status": "불명"},  # 비활성 상태는 유지
    ]}
    filtered = lp.filter_routes_for_categories(
        profile, ["ADVERTISING_AND_MARKETING"], category_map)
    ids = [r["route_id"] for r in filtered["routes"]]
    assert "advertising_claims" in ids
    assert "online_sales" not in ids  # 광고 범주에 매핑되지 않음 → 제외
    assert "personal_data" in ids
    # 원본 profile은 변경되지 않는다
    assert len(profile["routes"]) == 3


def test_filter_routes_noop_without_rerun():
    profile = {"routes": [{"route_id": "online_sales", "status": "해당"}]}
    assert lp.filter_routes_for_categories(profile, [], lp.load_category_map()) is profile


# ---------------------------------------------------------------- 수정 요청 검증

def _raw_revision(quote, suggestions=None, route_id="advertising_claims"):
    return {"revision_requests": [{
        "route_id": route_id, "quote": quote, "rationale": "실증 없는 수치 광고",
        "suggestions": suggestions if suggestions is not None else [
            {"label": "A", "new_text": "실증 시험 완료 후 광고를 개시한다."},
            {"label": "B", "new_text": "수치를 제외하고 표기한다."},
        ],
    }]}


def test_validate_revisions_accepts_exact_quote():
    requests = lp.validate_revisions(
        _raw_revision("악취 30% 개선을 핵심 광고 카피로 사용한다."),
        SECTIONS, lp.load_category_map())
    assert len(requests) == 1
    request = requests[0]
    assert request["anchorSectionCode"] == "PRODUCT_SERVICE"
    assert request["category"] == "ADVERTISING_AND_MARKETING"
    assert [s["label"] for s in request["suggestions"]] == ["A", "B"]


def test_validate_revisions_drops_quote_not_in_sections():
    requests = lp.validate_revisions(
        _raw_revision("원문에 없는 문장이다."), SECTIONS, lp.load_category_map())
    assert requests == []


def test_validate_revisions_drops_whitespace_mismatch():
    # 백엔드가 exact indexOf로 교체하므로 공백이 달라도 탈락해야 한다
    requests = lp.validate_revisions(
        _raw_revision("악취 30%  개선을 핵심 광고 카피로 사용한다."),
        SECTIONS, lp.load_category_map())
    assert requests == []


def test_validate_revisions_drops_ambiguous_quote():
    sections = SECTIONS + [{"code": "SCHEDULE_RISK", "title": "일정",
                            "content": "악취 30% 개선을 핵심 광고 카피로 사용한다."}]
    requests = lp.validate_revisions(
        _raw_revision("악취 30% 개선을 핵심 광고 카피로 사용한다."),
        sections, lp.load_category_map())
    assert requests == []


def test_validate_revisions_requires_two_suggestions():
    requests = lp.validate_revisions(
        _raw_revision("악취 30% 개선을 핵심 광고 카피로 사용한다.",
                      suggestions=[{"label": "A", "new_text": "하나뿐"}]),
        SECTIONS, lp.load_category_map())
    assert requests == []


# ---------------------------------------------------------------- 질문 범주 (집계)

def test_aggregator_questions_carry_categories():
    state = {
        "profile": {
            "routes": [],
            "missing_information": [
                {"question": "활성탄 필터가 안전확인대상인가요?",
                 "related_route_ids": ["chemical_biocidal"]},
            ],
        },
        "candidates": [],
        "fetch_log": [],
    }
    result = aggregator.build(state, {}, {"route_gaps": ["online_sales"], "action_items": []}, [])
    questions = result["questions"]
    assert len(questions) == 2
    assert questions[0]["categories"]  # chemical_biocidal 매핑 범주 존재
    assert all(isinstance(c, str) for c in questions[0]["categories"])
    assert questions[1]["categories"]  # route_gap 질문도 범주 연결
    # 10-finding 보증은 유지된다
    assert len(result["findings"]) == 10


def test_aggregator_accepts_legacy_string_questions():
    state = {"profile": {"routes": [], "missing_information": ["평문 질문"]},
             "candidates": [], "fetch_log": []}
    result = aggregator.build(state, {}, {"route_gaps": [], "action_items": []}, [])
    assert result["questions"][0]["question"] == "평문 질문"
    assert result["questions"][0]["categories"] == []


# ------------------------------------------------- 구조화 근거 + 논리 사슬 (집계)

_LAW = "전자상거래 등에서의 소비자보호에 관한 법률"


def _evidence_state():
    """online_sales 경로 + 의무 조문 1건·제재 조문 1건."""
    return {
        "profile": {
            "routes": [{
                "route_id": "online_sales", "status": "해당", "confidence": 0.9,
                "reason": "자사몰을 통한 비대면 판매가 확인됨",
                # 기획서 표에서 뽑힌 인용 — 셀 구분자가 섞여 있다
                "evidence_quotes": ["자사몰·스마트스토어 | 55% | 고객 데이터 확보"],
            }],
            "missing_information": [],
        },
        "candidates": [
            {"citation_id": "CIT-001", "route_id": "online_sales", "law_name": _LAW,
             "조문": "제12조", "제목": "통신판매업자의 신고 등", "내용": "제12조(통신판매업자의 신고 등) …",
             "mst": "282793", "현행": True, "시행일자": "20260721"},
            {"citation_id": "CIT-002", "route_id": "online_sales", "law_name": _LAW,
             "조문": "제42조", "제목": "벌칙", "내용": "제42조(벌칙) …",
             "mst": "282793", "현행": True, "시행일자": "20260721"},
        ],
        "fetch_log": [],
    }


def _evidence_screenings(plain_summary=None):
    first = {"category": "requirement", "relevance_note": "통신판매업자로서 신고 의무가 직접 적용됨"}
    if plain_summary is not None:
        first["plain_summary"] = plain_summary
    return {
        "CIT-001": first,
        "CIT-002": {"category": "risk", "relevance_note": "신고 누락에 대한 벌칙 조항",
                    "plain_summary": ""},
    }


_AUDIT = {"route_gaps": [], "action_items": [{
    "rank": 1, "action": "통신판매업 신고", "timing": "판매 개시 전",
    "reason": "신고 없이 판매를 개시하면 벌칙(제42조) 대상이 될 수 있음",
    "citation_ids": ["CIT-001", "CIT-002"],
}]}


def _finding(result, category):
    return next(f for f in result["findings"] if f["category"] == category)


def test_evidence_is_structured_per_article():
    result = aggregator.build(
        _evidence_state(), _evidence_screenings("온라인으로 물건을 팔려면 구청에 신고해야 합니다."),
        _AUDIT, SECTIONS)
    evidence = _finding(result, "BUSINESS_REGISTRATION")["evidence"]
    obligation = next(e for e in evidence if e["article"] == "제12조")
    assert obligation["lawName"] == _LAW
    assert obligation["title"] == "통신판매업자의 신고 등"
    assert obligation["role"] == "REQUIREMENT"
    # 조문별 설명이 그 조문에 붙어 있다 — 예전에는 전부 한 문장으로 뭉개졌다
    assert obligation["plainSummary"] == "온라인으로 물건을 팔려면 구청에 신고해야 합니다."
    assert obligation["whyRelevant"] == "통신판매업자로서 신고 의무가 직접 적용됨"
    assert obligation["effectiveDate"] == "2026-07-21"
    assert obligation["lawUrl"].startswith("https://www.law.go.kr/")
    assert "mst" not in obligation and "MST" not in str(obligation)


def test_evidence_survives_missing_plain_summary():
    """구 프롬프트로 저장된 실행(plain_summary 없음)도 그대로 집계된다."""
    result = aggregator.build(_evidence_state(), _evidence_screenings(None), _AUDIT, SECTIONS)
    obligation = next(e for e in _finding(result, "BUSINESS_REGISTRATION")["evidence"]
                      if e["article"] == "제12조")
    assert obligation["plainSummary"] is None
    assert obligation["whyRelevant"]  # 관련 이유는 남는다


def test_reasoning_chain_uses_topic_not_route_id():
    result = aggregator.build(_evidence_state(), _evidence_screenings("요약"), _AUDIT, SECTIONS)
    finding = _finding(result, "BUSINESS_REGISTRATION")
    chain = finding["reasoning"]
    assert chain["regulatoryPath"]["topic"] == "전자상거래·통신판매"
    assert chain["regulatoryPath"]["status"] == "해당"
    assert [o["article"] for o in chain["obligations"]] == ["제12조"]
    # 그동안 버려졌던 action_items[].reason 이 위반 결과로 살아난다
    assert "벌칙(제42조)" in chain["consequence"]["text"]
    assert chain["conclusion"] == {"action": "통신판매업 신고", "timing": "판매 개시 전"}
    # 내부 route_id 는 어디에도 노출되지 않는다
    assert "online_sales" not in json.dumps(finding, ensure_ascii=False)


def test_sanction_article_lands_in_its_mapped_category():
    """조문 범주 배정은 category_rules 가 정한다 — 벌칙 조문은 소비자 보호로 간다."""
    result = aggregator.build(_evidence_state(), _evidence_screenings("요약"), _AUDIT, SECTIONS)
    consumer = _finding(result, "CONSUMER_PROTECTION")
    sanction = next(e for e in consumer["evidence"] if e["article"] == "제42조")
    assert sanction["role"] == "SANCTION"
    assert consumer["reasoning"]["consequence"]["sanctionArticles"] == ["제42조"]
    # 신고 의무 조문은 사업자 등록 범주에만 있다
    assert all(e["article"] != "제12조" for e in consumer["evidence"])


def test_reasoning_cleans_table_cell_quotes():
    result = aggregator.build(_evidence_state(), _evidence_screenings("요약"), _AUDIT, SECTIONS)
    quotes = _finding(result, "BUSINESS_REGISTRATION")["reasoning"]["planBasis"]["quotes"]
    # 셀 구분자만 바뀌고 셀 내용은 하나도 버려지지 않는다
    assert quotes == ["자사몰·스마트스토어 · 55% · 고객 데이터 확보"]


def test_finding_text_summarizes_roles_without_run_on():
    result = aggregator.build(_evidence_state(), _evidence_screenings("요약"), _AUDIT, SECTIONS)
    finding = _finding(result, "BUSINESS_REGISTRATION")
    assert finding["finding"] == "이 범주에서 직접 의무 조문 1건이 확인됩니다."
    # 조문별 노트는 finding 문장이 아니라 evidence 로 간다
    assert "통신판매업자로서 신고 의무가" not in finding["finding"]
    assert _finding(result, "CONSUMER_PROTECTION")["finding"] == (
        "이 범주에서 위반 시 제재 조문 1건이 확인됩니다.")


def test_reasoning_absent_for_category_without_basis():
    result = aggregator.build(_evidence_state(), _evidence_screenings("요약"), _AUDIT, SECTIONS)
    finding = _finding(result, "LABOR_AND_EMPLOYMENT")
    assert finding["reasoning"] is None
    assert finding["evidence"] == []
    assert finding["rationale"] == "판단 근거가 될 계획 내용이 확인되지 않았습니다."
    assert len(result["findings"]) == 10  # 10-finding 보증 유지
