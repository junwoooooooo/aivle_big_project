# -*- coding: utf-8 -*-
"""파이프라인 state → 백엔드 LegalReviewAiResponse 형태로 접는다.

파이프라인은 27개 규제 경로 단위로 답하지만 백엔드는 10개 LegalCategory에 대해
정확히 한 행씩을 요구한다(LegalReviewPersistenceService.validate, uk_legal_finding_review_category).
따라서 매핑은 전사(total)여야 하고, 기여 경로가 없는 범주도 반드시 한 행을 만든다.
"""
import json
import re
from pathlib import Path
from urllib.parse import quote

BASE_DIR = Path(__file__).resolve().parent
CATEGORY_MAP_PATH = BASE_DIR / "category_map.json"
CATEGORY_RULES_PATH = BASE_DIR / "category_rules.json"
LAW_REGISTRY_PATH = BASE_DIR / "law_registry.json"

# 상태 우선순위 — 같은 범주에 여러 경로가 기여하면 가장 강한 쪽을 취한다.
_APPLICABILITY = {
    "해당": (3, "APPLICABLE"),
    "적용 가능": (2, "POSSIBLY_APPLICABLE"),
    "불명": (1, "INSUFFICIENT_INFORMATION"),
    "비해당": (0, "NOT_APPLICABLE"),
}
_DEFAULT_APPLICABILITY = "INSUFFICIENT_INFORMATION"
_MAX_EVIDENCE = 8
_MAX_NOTES = 4
# 근거 정렬 우선순위 — 직접 의무가 먼저, 적용범위 판단이 마지막.
_SCREEN_ORDER = {"requirement": 0, "risk": 1, "scope": 2}
# 선별 분류 → 화면에 노출하는 근거 역할.
_ROLE = {"requirement": "REQUIREMENT", "risk": "SANCTION", "scope": "SCOPE"}
_ROLE_LABEL = {"REQUIREMENT": "직접 의무", "SANCTION": "위반 시 제재", "SCOPE": "적용 범위"}
_MAX_QUOTES = 2
_MAX_OBLIGATIONS = 4


def _normalize(text):
    return re.sub(r"\s+", "", text or "")


def _format_date(value):
    """법제처 API는 시행일자를 YYYYMMDD로 준다. 화면에 그대로 노출되므로 다듬는다."""
    text = str(value or "").strip()
    if re.fullmatch(r"\d{8}", text):
        return f"{text[:4]}-{text[4:6]}-{text[6:]}"
    return text


def load_category_map():
    return json.loads(CATEGORY_MAP_PATH.read_text(encoding="utf-8"))


def load_category_rules():
    return json.loads(CATEGORY_RULES_PATH.read_text(encoding="utf-8"))["laws"]


def load_route_topics():
    """route_id → 사람이 읽는 규제 영역명. 화면에서 내부 route_id를 지우기 위한 매핑."""
    routes = json.loads(LAW_REGISTRY_PATH.read_text(encoding="utf-8")).get("routes") or {}
    return {rid: (entry or {}).get("topic") or rid for rid, entry in routes.items()}


def _law_url(law_name):
    """국가법령정보센터 법령 페이지. 발췌만 저장하므로 전문은 이 링크로 보낸다."""
    name = (law_name or "").strip()
    if not name:
        return None
    return "https://www.law.go.kr/법령/" + quote(name.replace(" ", ""))


def _clean_quote(text):
    """기획서 표에서 뽑힌 인용은 셀 구분자가 섞여 그대로 쓰면 판독이 안 된다.

    실데이터 예: "자사몰·스마트스토어 | 55% | 고객 데이터와 높은 마진 확보"
    → 셀 구분자만 가운뎃점으로 바꾼다. 셀을 버리면 주어("자사몰·스마트스토어")가 사라져
    남은 조각("고객 데이터와 높은 마진 확보")만으로는 무슨 근거인지 알 수 없다.
    """
    raw = re.sub(r"\s+", " ", str(text or "")).strip()
    if not raw:
        return ""
    cells = [c.strip() for c in raw.split("|") if c.strip()]
    return " · ".join(cells) if len(cells) > 1 else raw


def _clean_quotes(quotes):
    seen, out = set(), []
    for quote_text in quotes:
        cleaned = _clean_quote(quote_text)
        if cleaned and cleaned not in seen:
            seen.add(cleaned)
            out.append(cleaned)
        if len(out) >= _MAX_QUOTES:
            break
    return out


def _categories_for(route_id, route_map, warnings):
    mapped = route_map.get(route_id)
    if not mapped:
        warnings.append(f"매핑에 없는 경로 {route_id} → INDUSTRY_SPECIFIC으로 흡수")
        return ["INDUSTRY_SPECIFIC"]
    return mapped


def _categories_for_citation(candidate, law_rules, route_map, warnings):
    """조문 하나가 어느 범주의 근거인지 정한다.

    1) 법령의 rules 중 조문 제목이 부분일치하는 첫 규칙
    2) 없으면 법령의 default
    3) 법령 자체가 규칙표에 없으면 route 매핑으로 폴백(기존 동작) + 경고
    """
    law = law_rules.get(candidate.get("law_name"))
    if law is None:
        warnings.append(
            f"category_rules에 없는 법령 '{candidate.get('law_name')}' → route 매핑으로 폴백")
        return _categories_for(candidate.get("route_id"), route_map, warnings)

    title = candidate.get("제목") or ""
    for rule in law.get("rules") or []:
        if any(keyword in title for keyword in rule.get("제목") or []):
            return rule.get("categories") or []
    return law.get("default") or []


def _risk_level(screen_categories, applicability):
    """실제 근거로 채택된 조문(requirement/risk/scope)만 보고 등급을 매긴다.

    requirement 하나만으로 HIGH를 주면, 절차·수수료 조항까지 최고 등급이 된다.
    제재 조항(risk)이 함께 있을 때만 HIGH로 올린다.
    """
    has_requirement = "requirement" in screen_categories
    has_risk = "risk" in screen_categories
    if has_requirement and has_risk:
        return "HIGH"
    if has_requirement or has_risk:
        return "MEDIUM"
    if "scope" in screen_categories:
        return "LOW"
    if applicability == "NOT_APPLICABLE":
        return "LOW"
    return "UNKNOWN"


def _overall(findings):
    order = ["UNKNOWN", "LOW", "MEDIUM", "HIGH", "CRITICAL"]
    worst = "UNKNOWN"
    for f in findings:
        if order.index(f["riskLevel"]) > order.index(worst):
            worst = f["riskLevel"]
    return worst


def _section_labels(codes, sections):
    if not codes or not sections:
        return []
    titles = {s.get("code"): (s.get("title") or s.get("code")) for s in sections}
    return [titles.get(code, code) for code in codes]


def _build_reasoning(bucket_routes, route_topics, quotes, evidence, matched_actions,
                     section_codes, sections):
    """왜 이 판정인가를 5단으로 명시한다.

    기획서 문장 → 걸린 규제 영역 → 발생 의무 → 위반 시 결과 → 조치.
    재료는 전부 기존 파이프라인 산출물이다. 특히 consequence.text는 그동안
    버려지던 action_items[].reason으로, 제재 수위까지 담고 있다.
    """
    if not bucket_routes and not evidence:
        return None

    # 같은 범주에 여러 경로가 기여하면 가장 강한 상태의 경로를 대표로 쓴다.
    strongest = None
    for route in bucket_routes:
        rank = _APPLICABILITY.get(route.get("status"), (1, None))[0]
        if strongest is None or rank > strongest[0]:
            strongest = (rank, route)
    lead_route = strongest[1] if strongest else None

    obligations = [
        {"article": item["article"], "lawName": item["lawName"],
         "summary": item["plainSummary"] or item["whyRelevant"] or item["title"]}
        for item in evidence if item["role"] == "REQUIREMENT"
    ][:_MAX_OBLIGATIONS]

    sanction_articles = [item["article"] for item in evidence if item["role"] == "SANCTION"]
    consequence_text = next(
        (a.get("reason") for a in matched_actions if a.get("reason")), None)

    reasoning = {
        "planBasis": {
            "sectionLabels": _section_labels(section_codes, sections),
            "quotes": quotes,
        },
        "regulatoryPath": {
            "topic": route_topics.get(lead_route.get("route_id")) if lead_route else None,
            "status": lead_route.get("status") if lead_route else None,
            "reason": lead_route.get("reason") if lead_route else None,
        },
        "obligations": obligations,
        "consequence": {
            "sanctionArticles": sanction_articles,
            "text": consequence_text,
        },
        "conclusion": {
            "action": matched_actions[0]["action"] if matched_actions else None,
            "timing": (matched_actions[0].get("timing") or None) if matched_actions else None,
        },
    }
    return reasoning


def _rationale_text(reasoning):
    """사슬을 사람이 읽는 문단으로 접는다 — 기존 '판단 이유' 표시 위치 하위호환.

    내부 route_id는 절대 넣지 않는다(예전 형식: "규제 경로: online_sales(해당)").
    """
    if not reasoning:
        return "판단 근거가 될 계획 내용이 확인되지 않았습니다."
    parts = []
    path = reasoning["regulatoryPath"]
    if path.get("topic"):
        status = f" — {path['status']}" if path.get("status") else ""
        parts.append(f"규제 영역: {path['topic']}{status}")
    if path.get("reason"):
        parts.append(path["reason"])
    quotes = reasoning["planBasis"].get("quotes") or []
    if quotes:
        parts.append("계획 근거: " + " / ".join(f"“{q}”" for q in quotes))
    if reasoning["consequence"].get("text"):
        parts.append("위반 시: " + reasoning["consequence"]["text"])
    return "\n".join(parts) or "판단 근거가 될 계획 내용이 확인되지 않았습니다."


def _source_section_codes(quotes, sections):
    """라우터가 인용한 문장이 어느 계획 섹션에서 왔는지 되짚는다."""
    if not sections:
        return []
    codes = []
    normalized = [(s.get("code"), _normalize(s.get("content"))) for s in sections]
    for quote_text in quotes:
        needle = _normalize(quote_text)
        if not needle:
            continue
        for code, haystack in normalized:
            if code and needle in haystack and code not in codes:
                codes.append(code)
    return codes


def build(state, screenings, screen_audit, sections=None):
    """state + 선별 결과 → {overallRiskLevel, summary, findings[10], questions[]}"""
    category_map = load_category_map()
    route_map = category_map["routes"]
    labels = category_map["categories"]
    route_topics = load_route_topics()
    warnings = []

    profile = state.get("profile") or {}
    routes = profile.get("routes") or []
    candidates = state.get("candidates") or []
    actions = (screen_audit or {}).get("action_items") or []

    # 범주별로 기여 경로와 인용을 모은다.
    buckets = {code: {"routes": [], "citations": []} for code in labels}
    for route in routes:
        for code in _categories_for(route.get("route_id"), route_map, warnings):
            if code in buckets:
                buckets[code]["routes"].append(route)
    # 인용은 route가 아니라 조문 단위로 배정한다. route 단위로 뿌리면
    # 하나의 route가 N개 범주에 걸릴 때 N개 범주가 완전히 같은 근거를 갖게 된다.
    law_rules = load_category_rules()
    for candidate in candidates:
        for code in _categories_for_citation(candidate, law_rules, route_map, warnings):
            if code in buckets:
                buckets[code]["citations"].append(candidate)

    findings = []
    for code, label in labels.items():
        bucket = buckets[code]
        bucket_routes = bucket["routes"]
        citations = bucket["citations"]

        rank, applicability = -1, _DEFAULT_APPLICABILITY
        for route in bucket_routes:
            candidate_rank, candidate_value = _APPLICABILITY.get(
                route.get("status"), (1, "INSUFFICIENT_INFORMATION"))
            if candidate_rank > rank:
                rank, applicability = candidate_rank, candidate_value

        screened = [(c, screenings.get(c["citation_id"], {})) for c in citations]
        relevant = [(c, s) for c, s in screened
                    if s.get("category") in ("requirement", "scope", "risk")]
        # 근거로 채택한 것만 본다 — supporting·exclude까지 세면 등급이 부풀려진다.
        screen_categories = {s.get("category") for _, s in relevant}
        risk_level = _risk_level(screen_categories, applicability)
        # 8개로 자르기 전에 의무 → 제재 → 적용범위 순으로 정렬한다.
        # 리스트 순서대로 자르면 앞 순번 정의·적용범위 조문이 벌칙·청약철회를 밀어낸다.
        relevant.sort(key=lambda pair: _SCREEN_ORDER.get(pair[1].get("category"), 9))

        # 근거 조문 — 조문 하나가 자기 설명을 들고 다닌다. 예전에는 relevance_note들을
        # 한 문장으로 이어붙여 "어느 조문의 설명인지"가 유실됐다.
        evidence, seen_articles = [], set()
        for c, s in relevant:
            key = (c.get("law_name"), c.get("조문"))
            if key in seen_articles:
                continue
            seen_articles.add(key)
            evidence.append({
                "lawName": c.get("law_name"),
                "article": c.get("조문"),
                "title": c.get("제목") or None,
                "role": _ROLE.get(s.get("category"), "SCOPE"),
                "plainSummary": (s.get("plain_summary") or "").strip() or None,
                "whyRelevant": (s.get("relevance_note") or "").strip() or None,
                "excerpt": c.get("내용") or None,
                "effectiveDate": _format_date(c.get("시행일자")) or None,
                "lawUrl": _law_url(c.get("law_name")),
            })
            if len(evidence) >= _MAX_EVIDENCE:
                break

        role_counts = {}
        for item in evidence:
            role_counts[item["role"]] = role_counts.get(item["role"], 0) + 1
        if evidence:
            finding_text = "이 범주에서 " + ", ".join(
                f"{_ROLE_LABEL[role]} 조문 {role_counts[role]}건"
                for role in ("REQUIREMENT", "SANCTION", "SCOPE") if role in role_counts
            ) + "이 확인됩니다."
        elif bucket_routes:
            finding_text = ("현재 계획 정보로는 이 범주에서 직접적인 의무 조항을 찾지 못했습니다."
                            if applicability != "NOT_APPLICABLE"
                            else "현재 계획 정보상 이 범주는 적용 대상이 아닌 것으로 보입니다.")
        else:
            finding_text = "확정된 계획에서 이 범주를 판단할 근거를 찾지 못했습니다."

        raw_quotes = [q for r in bucket_routes for q in (r.get("evidence_quotes") or [])]
        quotes = _clean_quotes(raw_quotes)

        citation_ids = {c["citation_id"] for c, _ in relevant}
        matched_actions = [a for a in actions
                           if citation_ids & set(a.get("citation_ids") or [])]
        # LLM이 매긴 rank를 실제로 쓴다 — 그동안 리스트 순서로만 잘라 무시되고 있었다.
        matched_actions.sort(key=lambda a: a.get("rank") or 99)
        if matched_actions:
            recommended = " / ".join(
                f"{a['action']} ({a.get('timing') or '시점 미정'})" for a in matched_actions[:3])
        elif applicability == "NOT_APPLICABLE":
            recommended = "현재 계획 기준으로는 별도 조치가 필요하지 않습니다. 사업 내용이 바뀌면 다시 확인하세요."
        else:
            recommended = "관할 기관 또는 자격 있는 전문가에게 적용 여부와 대응 방법을 확인하세요."

        reasoning = _build_reasoning(
            bucket_routes, route_topics, quotes, evidence, matched_actions,
            _source_section_codes(quotes, sections), sections)

        confidences = [r.get("confidence") for r in bucket_routes
                       if isinstance(r.get("confidence"), (int, float))]
        confidence = round(min(confidences), 4) if confidences else None

        findings.append({
            "category": code,
            "applicability": applicability,
            "riskLevel": risk_level,
            "title": label,
            "finding": finding_text,
            "rationale": _rationale_text(reasoning),
            "recommendedAction": recommended,
            "evidence": evidence,
            "reasoning": reasoning,
            "sourceSectionCodes": _source_section_codes(quotes, sections),
            "requiresProfessionalReview": applicability != "NOT_APPLICABLE",
            "confidence": confidence,
        })

    # 질문에는 관련 범주를 함께 싣는다 — 답변(확정 정보) 후 증분 재검토가
    # 어느 범주를 재실행할지 이 연결로 결정한다 (백엔드 IncrementalReviewPlanner).
    def _question_categories(route_ids):
        codes = []
        for rid in route_ids or []:
            for code in _categories_for(rid, route_map, warnings):
                if code in labels and code not in codes:
                    codes.append(code)
        return codes

    questions = []
    for item in profile.get("missing_information") or []:
        # 구 state 파일은 평문 문자열일 수 있다
        if isinstance(item, str):
            item = {"question": item, "related_route_ids": []}
        questions.append({
            "question": item.get("question"),
            "reason": "라우팅 단계에서 판단에 필요한 정보가 계획에 없어 확인이 필요합니다.",
            "categories": _question_categories(item.get("related_route_ids")),
        })
    for route_id in (screen_audit or {}).get("route_gaps") or []:
        questions.append({
            "question": f"{route_id} 경로에서 직접 의무 조항을 찾지 못했습니다. 해당 사업 행위를 실제로 수행하나요?",
            "reason": "레지스트리 법령에서 requirement로 분류된 조문이 없어 경로 해당 여부를 재확인해야 합니다.",
            "categories": _question_categories([route_id]),
        })

    applicable = [f for f in findings if f["applicability"] in ("APPLICABLE", "POSSIBLY_APPLICABLE")]
    fetched = len([f for f in state.get("fetch_log") or [] if f.get("status") == "ok"])
    summary = (
        f"확정 계획 기준 {len(applicable)}개 범주에서 확인이 필요합니다. "
        f"법제처 현행 법령 {fetched}건에서 조문 {len(candidates)}건을 검토했습니다."
    )

    return {
        "overallRiskLevel": _overall(findings),
        "summary": summary,
        "findings": findings,
        "questions": questions,
        "warnings": warnings,
    }
