# -*- coding: utf-8 -*-
"""어댑터 공통 — 인터페이스는 하나다: `Slot → (Finding, Document)`

**API 로 받았다고 특별대우하지 않는다.**
  · 등급은 응답이 아니라 **원본 URL + 화이트리스트**로 정한다 (규칙 2·7).
    어댑터가 `score=6` 을 박으면 규칙 파일이 무력화된다.
  · `year` 는 API 필드를 그대로 쓰지 않고 **웹 경로와 같은 파서**를 태운다.
    두 경로가 다른 year 를 만들면 `match_key` 가 갈려 교차확인이 조용히 깨진다.
  · 값은 `Document.text` 에 원문 그대로 남긴다. `quote_verified` 가 API 경로에서도
    의미를 갖게 하려면 인용문이 실제 응답 안에 있어야 한다.

실패는 전부 값이다 (`rules/adapters.v1.json` 의 `failure_map`).
잘못된 통계표 ID 는 **시스템 오류가 아니라 조사 결과**다 — 예외로 터뜨리면 실행 전체가 죽는다.
"""
from __future__ import annotations

import io, json, os, time
from datetime import datetime

import requests

from schema import Document, Finding, FindingItem, Slot

UA = "research2-pipeline/1.0 (+market research; contact: local)"


def load_env_key(name: str) -> str | None:
    """.env 에서 키를 읽는다. 없으면 None — 가짜로 채우지 않는다."""
    if os.environ.get(name):
        return os.environ[name]
    here = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    # ⚠ 깊이는 **엔진이 저장소 어디에 놓이든** 저장소 루트의 `.env` 에 닿아야 한다.
    #   판 ㉝ 이식(`시장조사/research2` → `ai/app/research/research2`)으로 **두 단계 깊어졌고**
    #   그 순간 `test_step9` 가 「Missing credentials」로 죽었다 — 조용히 None 을 돌려주는
    #   함수라 원인이 키 문제로 보인다. 넉넉히 잡되 **위로만** 본다.
    #   컨테이너에는 `.env` 가 없다 — 거기서는 위 `os.environ` 경로로 들어온다.
    for rel in (".env", "../.env", "../../.env", "../../../.env",
                "../../../../.env", "../../../../../.env"):
        p = os.path.normpath(os.path.join(here, rel))
        if os.path.exists(p):
            for line in io.open(p, encoding="utf-8"):
                if line.startswith(name + "="):
                    v = line.split("=", 1)[1].strip()
                    return v or None
    return None


class AdapterResult:
    """어댑터 하나의 결과 — Finding 과 그 근거가 된 Document 를 함께 낸다."""

    def __init__(self, finding: Finding, document: Document, adapter_state: str = "ok",
                 note: str = ""):
        self.finding = finding
        self.document = document
        self.adapter_state = adapter_state
        self.note = note


def fail(slot: Slot, trace_id: str, kind: str, rules: dict, detail: str = "",
         url: str = "", channel: str = "web") -> AdapterResult:
    """실패를 값으로 만든다. 어떤 실패가 어떤 상태가 되는지는 규칙 파일이 정한다."""
    fm = rules["adapters"]["failure_map"][kind]
    finding = Finding(slot_id=slot.slot_id, trace_id=trace_id,
                      status=fm["finding_status"], findings=[],
                      note=f"{kind}: {detail}" if detail else kind)
    doc = Document(slot_id=slot.slot_id, trace_id=trace_id, url=url, text="",
                   http_status="error" if kind != "timeout" else "timeout",
                   content_status="empty", channel=channel, error=kind,
                   retrieved_at=datetime.now().isoformat(timespec="seconds"))
    return AdapterResult(finding, doc, adapter_state=fm["adapter_state"], note=detail)


def get_json(url: str, params: dict, rules: dict) -> tuple:
    """(data, err_kind, detail). 예외를 밖으로 던지지 않는다."""
    cfg = rules["adapters"]["retry"]
    last = ("", "")
    for attempt in range(cfg["max_attempts"]):
        try:
            r = requests.get(url, params=params, timeout=cfg["timeout_sec"],
                             headers={"User-Agent": UA})
        except requests.Timeout:
            last = ("timeout", f"attempt {attempt + 1}/{cfg['max_attempts']}")
        except Exception as e:
            last = ("http_error", type(e).__name__)
        else:
            if r.status_code >= 400:
                return None, "http_error", f"HTTP {r.status_code}"
            try:
                return r.json(), None, ""
            except Exception:
                return r.text, "parse_error", r.text[:200]
        time.sleep(cfg["backoff_sec"])
    return None, last[0], last[1]


def make_document(slot: Slot, trace_id: str, url: str, payload_text: str,
                  channel: str, published_at: str | None = None) -> Document:
    """API 응답을 Document 로 감싼다.

    `content_status='usable'` 로 두는 이유: API 는 JS 껍데기 문제(F9)가 없고 값이 응답 안에 있다.
    대신 인용문 대조는 웹과 **똑같이** 한다 — `text` 안에 인용문이 실제로 있어야 한다.
    """
    return Document(slot_id=slot.slot_id, trace_id=trace_id, url=url, text=payload_text,
                    published_at_raw=published_at, http_status="ok",
                    content_status="usable" if payload_text.strip() else "empty",
                    text_len=len(payload_text),
                    digit_count=sum(c.isdigit() for c in payload_text),
                    has_table=True, channel=channel, http_code=200,
                    retrieved_at=datetime.now().isoformat(timespec="seconds"))


def make_finding(slot: Slot, trace_id: str, items: list[FindingItem]) -> Finding:
    if not items:
        return Finding(slot_id=slot.slot_id, trace_id=trace_id, status="not_found",
                       findings=[], note="empty_result")
    return Finding(slot_id=slot.slot_id, trace_id=trace_id, status="found", findings=items)
