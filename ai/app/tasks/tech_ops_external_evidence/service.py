"""선택적 외부 근거 보강. 실패해도 immutable Layer 1 사실을 훼손하지 않는다."""

import asyncio
import io
import os
import re
from typing import Any
from urllib.parse import urlparse
import zipfile

import httpx

TAVILY_ENDPOINT = "https://api.tavily.com/search"
KOSIS_ENDPOINT = "https://kosis.kr/openapi/statisticsSearch.do"
DART_ENDPOINT = "https://opendart.fss.or.kr/api/corpCode.xml"


def _plain(value: Any) -> str:
    return re.sub(r"\s+", " ", str(value or "")).strip()


def _context(summary: str, facts: list[dict[str, Any]]) -> str:
    return " ".join([summary] + [f"{row.get('path')} {row.get('value')}" for row in facts[:80]]).lower()


def _queries(summary: str, facts: list[dict[str, Any]]) -> list[str]:
    context, product = _context(summary, facts), _plain(summary)[:80] or "신규 서비스"
    if any(word in context for word in ("예약", "노쇼", "booking", "calendar")):
        return ["예약 서비스 노쇼 취소 운영 통계", "예약 API 연동 공식 문서", "예약 서비스 개인정보 운영 가이드"]
    return [f"{product} 운영 리스크 파일럿 검증", f"{product} 고객지원 SLA 운영 지표",
            f"{product} 공급 파트너 운영 조건"]


async def _tavily(client: httpx.AsyncClient, key: str, query: str) -> list[dict[str, str]]:
    try:
        response = await client.post(TAVILY_ENDPOINT, json={"api_key": key, "query": query,
            "search_depth": "basic", "max_results": 2, "include_answer": False, "include_raw_content": False})
        response.raise_for_status()
        return [{"title": _plain(row.get("title"))[:180], "url": _plain(row.get("url")),
                 "source": f"TAVILY_WEB_SEARCH · {query} · {_plain(row.get('content'))[:420]}"}
                for row in response.json().get("results", []) if _plain(row.get("title"))]
    except (httpx.HTTPError, ValueError, TypeError):
        return []


async def _kosis(client: httpx.AsyncClient, key: str, term: str) -> list[dict[str, str]]:
    try:
        response = await client.get(KOSIS_ENDPOINT, params={"method": "getList", "apiKey": key,
            "searchNm": term, "format": "json", "jsonVD": "Y"})
        response.raise_for_status(); rows = response.json()
    except (httpx.HTTPError, ValueError, TypeError):
        return []
    output = []
    for row in rows[:5] if isinstance(rows, list) else []:
        org, table, title = _plain(row.get("ORG_ID")), _plain(row.get("TBL_ID")), _plain(row.get("TBL_NM"))
        if org and table and title and term in title:
            output.append({"title": f"KOSIS 통계표: {title}",
                "url": f"https://kosis.kr/statHtml/statHtml.do?orgId={org}&tblId={table}",
                "source": f"KOSIS OpenAPI · 검색어: {term} · 표: {org}/{table}"})
    return output[:2]


async def _dart(client: httpx.AsyncClient, key: str, company: str | None) -> list[dict[str, str]]:
    if not company:
        return []
    try:
        response = await client.get(DART_ENDPOINT, params={"crtfc_key": key}); response.raise_for_status()
        with zipfile.ZipFile(io.BytesIO(response.content)) as archive:
            xml = archive.read(archive.namelist()[0]).decode("utf-8", "ignore")
        match = re.search(rf"<corp_code>([^<]+)</corp_code>.*?<corp_name>{re.escape(company)}</corp_name>", xml, re.S)
        if not match:
            return []
        return [{"title": f"DART 기업 확인: {company}",
            "url": f"https://dart.fss.or.kr/dsab001/main.do?autoSearch=true&textCrpNm={company}",
            "source": f"DART OpenAPI · Market/BM 명시 기업 · 코드: {match.group(1)}"}]
    except (httpx.HTTPError, ValueError, KeyError, zipfile.BadZipFile):
        return []


async def collect_external_evidence(summary: str, facts: list[dict[str, Any]]) -> tuple[list[dict[str, Any]], dict[str, Any]]:
    tavily, kosis, dart = (os.getenv("TAVILY_API_KEY", "").strip(),
        os.getenv("KOSIS_API_KEY", "").strip(), os.getenv("DART_API_KEY", "").strip())
    enabled_dart = os.getenv("TECH_OPS_ENABLE_DART_CORP_LOOKUP", "false").lower() == "true"
    context = _context(summary, facts)
    company = next((name for name in ("네이버", "카카오", "쿠팡", "토스") if name in context), None)
    term = next((name for name in ("예약", "음식점", "배달", "소프트웨어", "플랫폼") if name in context), summary[:20])
    async with httpx.AsyncClient(timeout=8, follow_redirects=True) as client:
        tasks = [_tavily(client, tavily, query) for query in _queries(summary, facts)] if tavily else []
        if kosis: tasks.append(_kosis(client, kosis, term))
        if dart and enabled_dart: tasks.append(_dart(client, dart, company))
        batches = await asyncio.gather(*tasks) if tasks else []
    rows, seen = [], set()
    for row in [item for batch in batches for item in batch]:
        host = urlparse(row["url"]).netloc.lower()
        if not host or row["url"] in seen: continue
        seen.add(row["url"]); rows.append({"evidenceId": f"WEB-{len(rows)+1:03d}", **row,
            "evidenceLevel": 2, "status": "EXTERNAL_WEB_SOURCE"})
        if len(rows) >= 8: break
    return rows, {"tavilyConfigured": bool(tavily), "kosisConfigured": bool(kosis),
        "dartConfigured": bool(dart), "dartCorpLookupEnabled": enabled_dart,
        "sourcesCollected": len(rows), "failOpen": True}
