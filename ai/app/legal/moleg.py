import asyncio
import hashlib
import json
import os
import time
from dataclasses import dataclass
from datetime import datetime, timezone
from typing import Any
from urllib.parse import quote

import httpx


@dataclass(frozen=True)
class LawMetadata:
    name: str
    mst: str
    law_id: str | None
    effective_date: str | None
    law_url: str
    promulgation_date: str | None = None


class MolegFailure(RuntimeError):
    def __init__(self, reason: str, retryable: bool):
        super().__init__(reason)
        self.reason = reason
        self.retryable = retryable


_CACHE: dict[str, tuple[float, Any]] = {}


class MolegClient:
    def __init__(self, registry_version: str = "legal-registry-v1"):
        self.key = os.getenv("MOLEG_API_KEY", "").strip()
        self.base_url = os.getenv("MOLEG_API_BASE_URL", "https://www.law.go.kr/DRF").strip().rstrip("/")
        self.registry_version = registry_version
        try:
            self.timeout = float(os.getenv("LEGAL_PROVIDER_TIMEOUT_SECONDS", "30"))
            self.cache_seconds = int(os.getenv("LEGAL_SOURCE_CACHE_SECONDS", "3600"))
        except ValueError as failure:
            raise MolegFailure("LEGAL_CONFIGURATION_INVALID", False) from failure
        if not self.key or not self.base_url.startswith(("http://", "https://")) or self.timeout <= 0:
            raise MolegFailure("LEGAL_CONFIGURATION_INVALID", False)

    async def _get(self, endpoint: str, params: dict[str, Any]) -> dict[str, Any]:
        safe_params = {**params, "OC": self.key, "type": "JSON"}
        normalized = json.dumps({"endpoint": endpoint, "params": params,
            "registryVersion": self.registry_version,
            "retrievedDate": datetime.now(timezone.utc).date().isoformat()},
            ensure_ascii=False, sort_keys=True, separators=(",", ":"))
        cache_key = hashlib.sha256(normalized.encode("utf-8")).hexdigest()
        cached = _CACHE.get(cache_key)
        if cached and cached[0] > time.monotonic():
            return cached[1]
        last_failure: Exception | None = None
        for attempt in range(2):
            try:
                async with httpx.AsyncClient(timeout=self.timeout) as client:
                    response = await client.get(f"{self.base_url}/{endpoint}", params=safe_params,
                        headers={"User-Agent": "aivle-legal-source/1.0", "Accept": "application/json"})
                if response.status_code in (401, 403):
                    raise MolegFailure("MOLEG_AUTHENTICATION_FAILED", False)
                if response.status_code == 429:
                    raise MolegFailure("MOLEG_RATE_LIMITED", True)
                if response.status_code >= 500:
                    raise MolegFailure("MOLEG_DEPENDENCY_UNAVAILABLE", True)
                if response.status_code >= 400:
                    raise MolegFailure("MOLEG_REQUEST_REJECTED", False)
                value = response.json()
                if not isinstance(value, dict):
                    raise ValueError("response is not an object")
                _CACHE[cache_key] = (time.monotonic() + self.cache_seconds, value)
                return value
            except MolegFailure as failure:
                if not failure.retryable or attempt == 1:
                    raise
                last_failure = failure
            except (httpx.TimeoutException, httpx.NetworkError) as failure:
                last_failure = failure
                if attempt == 1:
                    raise MolegFailure("MOLEG_DEPENDENCY_UNAVAILABLE", True) from failure
            except (ValueError, TypeError) as failure:
                raise MolegFailure("MOLEG_RESPONSE_INVALID", False) from failure
            await asyncio.sleep(0.2)
        raise MolegFailure("MOLEG_DEPENDENCY_UNAVAILABLE", True) from last_failure

    async def search_exact(self, law_name: str) -> LawMetadata | None:
        data = await self._get("lawSearch.do", {"target": "law", "query": law_name, "display": 50})
        items = data.get("LawSearch", {}).get("law") or []
        if isinstance(items, dict):
            items = [items]
        normalized = law_name.replace(" ", "")
        for item in items:
            if str(item.get("법령명한글", "")).replace(" ", "") == normalized and item.get("현행연혁코드") == "현행":
                mst = str(item.get("법령일련번호") or "")
                if not mst:
                    continue
                return LawMetadata(law_name, mst, item.get("법령ID"), item.get("시행일자"),
                    f"https://www.law.go.kr/법령/{quote(law_name)}", item.get("공포일자"))
        return None

    async def articles(self, metadata: LawMetadata) -> list[dict[str, str]]:
        data = await self._get("lawService.do", {"target": "law", "MST": metadata.mst})
        units = data.get("법령", {}).get("조문", {}).get("조문단위") or []
        if isinstance(units, dict):
            units = [units]
        result = []
        for unit in units:
            if unit.get("조문여부") != "조문":
                continue
            body = [unit.get("조문내용", "")]
            paragraphs = unit.get("항") or []
            if isinstance(paragraphs, dict):
                paragraphs = [paragraphs]
            for paragraph in paragraphs:
                body.append(paragraph.get("항내용", ""))
                clauses = paragraph.get("호") or []
                if isinstance(clauses, dict):
                    clauses = [clauses]
                body.extend(clause.get("호내용", "") for clause in clauses)
            text = "\n".join(value.strip() for value in body if isinstance(value, str) and value.strip())
            article = str(unit.get("조문번호") or "")
            branch = str(unit.get("조문가지번호") or "")
            if not article or not text:
                continue
            result.append({"article": f"제{article}조" + (f"의{branch}" if branch else ""),
                "title": str(unit.get("조문제목") or ""), "text": text})
        return result
