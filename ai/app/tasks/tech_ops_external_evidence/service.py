"""Fail-open, source-first external research for Tech/Ops advice.

The Market/BM handoff remains the authoritative source for business claims.
This module only adds links that can be inspected by a reviewer.  It never
turns a search snippet into a market size, price, legal conclusion, or cost.

KOSIS and DART keys are deliberately not used for a blind numeric lookup:
those APIs require a verified table/corporation selector.  Their presence is
recorded so a later selector can use them, while Tavily retrieves a small set
of current, reviewable web sources for the supplied business context.
"""

from __future__ import annotations

import asyncio
import io
import os
import re
from typing import Any
from urllib.parse import urlparse
import zipfile

import httpx


TAVILY_ENDPOINT = "https://api.tavily.com/search"
MAX_QUERIES = 4
MAX_RESULTS_PER_QUERY = 2
# Source enrichment is optional; it must finish well before an advisory run.
REQUEST_TIMEOUT_SECONDS = 8.0
KOSIS_SEARCH_ENDPOINT = "https://kosis.kr/openapi/statisticsSearch.do"
DART_CORP_CODE_ENDPOINT = "https://opendart.fss.or.kr/api/corpCode.xml"


def _plain(value: Any) -> str:
    return re.sub(r"\s+", " ", str(value or "")).strip()


def _context(product_summary: str, facts: list[dict[str, Any]]) -> str:
    values = [product_summary]
    for fact in facts[:80]:
        path = _plain(fact.get("path"))
        value = _plain(fact.get("value"))
        if path and value:
            values.append(f"{path} {value}")
    return " ".join(values).lower()


def _queries(product_summary: str, facts: list[dict[str, Any]]) -> list[str]:
    """Choose a compact domain-specific search plan without an LLM call."""
    context = _context(product_summary, facts)
    product = _plain(product_summary)[:80] or "신규 서비스"
    queries: list[str]

    if any(word in context for word in ("예약", "미용", "노쇼", "calendar", "booking", "salon")):
        queries = [
            "미용실 예약 노쇼 취소 현황 통계",
            "네이버 예약 API 예약 연동 공식 문서",
            "예약 서비스 개인정보 처리방침 취소 환불 운영 가이드",
            "미용실 예약 통합 서비스 예약 동기화 고객지원 운영",
        ]
    elif any(word in context for word in ("공동주문", "배달", "음식", "픽업", "주문", "delivery")):
        queries = [
            "공동주문 배달 주문 취소 환불 운영 가이드",
            "음식 주문 플랫폼 개인정보 처리방침 결제 운영",
            "배달 음식점 주문 연동 API 공식 문서",
            f"{product} 공동수령 운영 파일럿",
        ]
    elif any(word in context for word in ("saas", "앱", "플랫폼", "api", "software")):
        queries = [
            f"{product} API 연동 장애 대응 운영 가이드",
            f"{product} 서비스 개인정보 접근권한 운영",
            f"{product} 고객지원 SLA 운영 지표",
            f"{product} 파일럿 운영 검증 방법",
        ]
    else:
        queries = [
            f"{product} 운영 리스크 파일럿 검증",
            f"{product} 고객지원 품질 운영 지표",
            f"{product} 공급 파트너 운영 조건",
            f"{product} 서비스 개인정보 접근권한 운영",
        ]
    return list(dict.fromkeys(queries))[:MAX_QUERIES]


def _trusted(url: str) -> bool:
    host = urlparse(url).netloc.lower()
    # Search results stay reviewable, but these domains should rank first.
    return any(domain in host for domain in (
        ".go.kr", ".ac.kr", "kosis.kr", "fss.or.kr", "law.go.kr",
        "naver.com", "developers.google.com", "docs.", "developer.",
    ))


def _kosis_search_term(product_summary: str, facts: list[dict[str, Any]]) -> str:
    context = _context(product_summary, facts)
    for term in ("미용실", "예약", "음식점", "배달", "공동주문", "1인 가구", "소프트웨어", "플랫폼"):
        if term in context:
            return term
    return _plain(product_summary)[:30]


def _dart_candidate(context: str) -> str | None:
    """Only query a corporation when the Market/BM facts explicitly name it."""
    candidates = ("네이버", "카카오", "쿠팡", "토스", "우아한형제들", "배달의민족")
    return next((name for name in candidates if name in context), None)


async def _collect_kosis_source(client: httpx.AsyncClient, key: str, term: str) -> list[dict[str, Any]]:
    if not key or not term:
        return []
    try:
        response = await client.get(KOSIS_SEARCH_ENDPOINT, params={
            "method": "getList", "apiKey": key, "searchNm": term,
            "format": "json", "jsonVD": "Y",
        })
        response.raise_for_status()
        rows = response.json()
    except (httpx.HTTPError, ValueError):
        return []
    if not isinstance(rows, list):
        return []
    evidence: list[dict[str, Any]] = []
    for row in rows[:5]:
        org_id, table_id = _plain(row.get("ORG_ID")), _plain(row.get("TBL_ID"))
        title = _plain(row.get("TBL_NM"))
        if not (org_id and table_id and title):
            continue
        # KOSIS search can return lexical false positives.  A table is useful
        # only when its title actually contains the selected business stem.
        stem = term.replace("실", "").replace("업", "").strip()
        if stem and stem.lower() not in title.lower():
            continue
        evidence.append({
            "title": f"KOSIS 통계표: {title}",
            "url": f"https://kosis.kr/statHtml/statHtml.do?orgId={org_id}&tblId={table_id}",
            "source": f"KOSIS OpenAPI statisticsSearch · 검색어: {term} · 표 ID: {org_id}/{table_id}",
            "provider": "KOSIS",
        })
        if len(evidence) >= 2:
            break
    return evidence


async def _collect_dart_source(client: httpx.AsyncClient, key: str, candidate: str | None) -> list[dict[str, Any]]:
    if not key or not candidate:
        return []
    try:
        archive = await client.get(DART_CORP_CODE_ENDPOINT, params={"crtfc_key": key})
        archive.raise_for_status()
        with zipfile.ZipFile(io.BytesIO(archive.content)) as zipped:
            xml = zipped.read(zipped.namelist()[0]).decode("utf-8", "ignore")
        match = re.search(
            rf"<list>\s*<corp_code>([^<]+)</corp_code>.*?<corp_name>{re.escape(candidate)}</corp_name>",
            xml, re.S,
        )
        if not match:
            return []
        corp_code = match.group(1).strip()
    except (httpx.HTTPError, ValueError, KeyError, zipfile.BadZipFile):
        return []
    return [{
        "title": f"DART 기업 공시 대상 확인: {candidate}",
        "url": f"https://dart.fss.or.kr/dsab001/main.do?autoSearch=true&textCrpNm={candidate}",
        "source": f"DART OpenAPI company profile · Market/BM 사실에 명시된 기업명 '{candidate}' 확인 · 기업코드: {corp_code}",
        "provider": "DART",
    }]


async def _search_one(client: httpx.AsyncClient, api_key: str, query: str) -> list[dict[str, Any]]:
    try:
        response = await client.post(TAVILY_ENDPOINT, json={
            "api_key": api_key,
            "query": query,
            "search_depth": "basic",
            "max_results": MAX_RESULTS_PER_QUERY,
            "include_answer": False,
            "include_raw_content": False,
        })
        response.raise_for_status()
        results = response.json().get("results", [])
    except (httpx.HTTPError, ValueError):
        return []

    rows: list[dict[str, Any]] = []
    for item in results:
        url = _plain(item.get("url"))
        title = _plain(item.get("title"))
        snippet = _plain(item.get("content"))
        if not url.startswith(("https://", "http://")) or not title:
            continue
        rows.append({"title": title[:180], "url": url, "snippet": snippet[:420], "query": query})
    return rows


async def collect_external_evidence(product_summary: str, facts: list[dict[str, Any]]) -> tuple[list[dict[str, Any]], dict[str, Any]]:
    """Return reviewable web sources and transparent provider diagnostics.

    It is safe for this to return no evidence: advisor quality then falls back
    to immutable Market/BM facts rather than fabricating a source.
    """
    tavily_key = os.getenv("TAVILY_API_KEY", "").strip()
    diagnostics = {
        "tavilyConfigured": bool(tavily_key),
        "kosisConfigured": bool(os.getenv("KOSIS_API_KEY", "").strip()),
        "dartConfigured": bool(os.getenv("DART_API_KEY", "").strip()),
        "dartCorpLookupEnabled": os.getenv("TECH_OPS_ENABLE_DART_CORP_LOOKUP", "false").lower() == "true",
        "queryMode": "contextual_web_search",
        "note": "KOSIS/DART numeric calls require a confirmed table ID or corporation code; no blind numeric lookup was performed.",
    }
    async with httpx.AsyncClient(timeout=REQUEST_TIMEOUT_SECONDS, follow_redirects=True) as client:
        tasks = []
        if tavily_key:
            tasks.extend(_search_one(client, tavily_key, query) for query in _queries(product_summary, facts))
        kosis_key = os.getenv("KOSIS_API_KEY", "").strip()
        dart_key = os.getenv("DART_API_KEY", "").strip()
        context = _context(product_summary, facts)
        if kosis_key:
            tasks.append(_collect_kosis_source(client, kosis_key, _kosis_search_term(product_summary, facts)))
        # corpCode.xml is a large registry download.  Enable it explicitly in
        # deployments that want company-validation enrichment; the normal
        # path already uses KOSIS and web sources without this latency cost.
        if dart_key and os.getenv("TECH_OPS_ENABLE_DART_CORP_LOOKUP", "false").lower() == "true":
            tasks.append(_collect_dart_source(client, dart_key, _dart_candidate(context)))
        # Each provider call has a short independent timeout.  A failure is
        # omitted; immutable Market/BM facts remain available for analysis.
        batches = await asyncio.gather(*tasks) if tasks else []

    seen: set[str] = set()
    flattened = [row for batch in batches for row in batch]
    # Prioritize official/primary results, then retain diversity of domains.
    flattened.sort(key=lambda row: (not _trusted(row["url"]), row["url"]))
    evidence: list[dict[str, Any]] = []
    for row in flattened:
        url = row["url"]
        title_and_snippet = f"{row.get('title', '')} {row.get('snippet', '')}"
        # A Korean business query returning an unrelated English 'restore'
        # page is a common product-name collision.  Do not surface it as
        # evidence merely because its URL was returned by search.
        if any("가" <= char <= "힣" for char in row.get("query", "")) and not any(
            "가" <= char <= "힣" for char in title_and_snippet
        ):
            continue
        if url in seen:
            continue
        seen.add(url)
        provider = row.get("provider", "TAVILY_WEB_SEARCH")
        evidence.append({
            "evidenceId": f"WEB-{len(evidence) + 1:03d}",
            "title": row["title"],
            "url": url,
            "source": row.get("source") or f"{provider} · {row['query']} · {row['snippet']}",
            "evidenceLevel": 2,
            "status": "EXTERNAL_WEB_SOURCE",
        })
        if len(evidence) >= 8:
            break
    diagnostics["sourcesCollected"] = len(evidence)
    return evidence, diagnostics
