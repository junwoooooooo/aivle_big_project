# -*- coding: utf-8 -*-
"""기존 수집 본문의 bounded section recall과 deterministic evidence promotion.

새 검색·fetch·PDF refetch를 하지 않는다. provider는 passage 후보만 반환하며, 원문 substring
대조와 source/grade/card 변환은 모두 이 모듈의 결정론적 코드가 담당한다.
"""
from __future__ import annotations

import concurrent.futures
import hashlib
import io
import json
import os
import re
import time
import unicodedata
from urllib.parse import urlsplit

import runpath

import web
from a_desk import kind_of, parse_number


JSON_OBJECT = re.compile(r"\{.*\}", re.S)
GRADE_RANK = {"근거 없음": 0, "추정": 1, "실무 신뢰": 2, "확정": 3}


def _norm(value: str) -> str:
    text = unicodedata.normalize("NFKC", str(value or ""))
    text = text.translate(str.maketrans({"“": '"', "”": '"', "‘": "'", "’": "'",
                                        "–": "-", "—": "-", "…": "..."}))
    return " ".join(text.split()).strip()


def quote_is_exact(body: str, quote: str, minimum: int = 10) -> bool:
    normalized = _norm(quote)
    return len(normalized) >= minimum and normalized in _norm(body)


def _blocked_domain(url: str, whitelist: dict) -> bool:
    host = urlsplit(url).netloc.lower().split(":")[0]
    for item in whitelist.get("rejected") or []:
        domain = item.get("domain") if isinstance(item, dict) else item
        domain = str(domain or "").lower()
        if domain and (host == domain or host.endswith("." + domain)):
            return True
    return False


def _grade(kind: str, fill: dict) -> str:
    for grade in ("확정", "실무 신뢰", "추정"):
        if kind in (fill.get("등급표", {}).get(grade) or []):
            return grade
    return str(fill.get("등급표", {}).get("_기본") or "추정")


def _source(doc, rules: dict) -> dict | None:
    url = str(getattr(doc, "url", "") or "").strip()
    retrieved = str(getattr(doc, "retrieved_at", "") or "").strip()
    if not url or not retrieved or _blocked_domain(url, rules["whitelist"]):
        return None
    kind, kind_by = kind_of(url, rules["whitelist"])
    blocked_kinds = set(((rules.get("fill") or {}).get("채택_불가_부류") or {})
                        .get("kinds", {}))
    if not kind or kind in blocked_kinds:
        return None
    return {"url": url, "retrieved_at": retrieved, "kind": kind,
            "kind_by": kind_by, "grade": _grade(kind, rules["fill"])}


def select_documents(docs: dict, ledger, coverage: list, rules: dict) -> list:
    """원장 신호만으로 base document를 안정적으로 고른다."""
    weak_slots = {getattr(item, "slot_id", "") for item in coverage
                  if getattr(item, "thin", False) or getattr(item, "status", "") != "충족"}
    weak_slots.update(getattr(row, "slot_id", "") for row in getattr(ledger, "rows", [])
                      if getattr(row, "label", "") in ("off_slot", "미검증"))
    selected = []
    for doc in docs.values():
        body = str(getattr(doc, "text", "") or "")
        if getattr(doc, "content_status", "") != "usable" or not body.strip():
            continue
        source = _source(doc, rules)
        if source is None:
            continue
        selected.append({"document": doc, "source": source,
                         "weak": getattr(doc, "slot_id", "") in weak_slots,
                         "body_len": len(body)})
    selected.sort(key=lambda item: (
        not item["weak"], -GRADE_RANK.get(item["source"]["grade"], 0),
        -item["body_len"], str(getattr(item["document"], "trace_id", ""))))
    maximum = int(rules["section_recall"].get("max_documents") or 8)
    return selected[:maximum]


def _prompt(document, sections: list[str], reask: bool, cfg: dict) -> str:
    body = str(getattr(document, "text", "") or "")[:int(cfg.get("max_document_chars") or 30000)]
    return f"""아래 문서에서 지정한 section에 직접 답하는 원문 passage만 JSON으로 추출하라.

section: {json.dumps(sections, ensure_ascii=False)}
재질문: {str(bool(reask)).lower()}

규칙:
- 문장을 고쳐 쓰거나 요약하지 않는다.
- 떨어진 두 구간을 ... 로 합치지 않는다.
- 표나 개조식 한 줄도 원문 그대로 passage가 될 수 있다.
- 숫자가 없어도 공법, 규격, 인증, 의무, 계약 조건, 유통 조건이면 가능하다.
- 관련 내용이 없으면 그 section은 빈 배열이다. 모든 section을 억지로 채우지 않는다.
- quote는 필수이고 number_raw, unit_raw, year는 원문에 있을 때만 선택적으로 쓴다.

응답 형식: {{"MARKET_SIZE":[{{"quote":"...","number_raw":"...","unit_raw":"...","year":"..."}}]}}

[문서]
{body}"""


def _default_provider(meter, document, sections: list[str], reask: bool, cfg: dict):
    response = meter.create(
        "section_recall", model=web.EXTRACT_MODEL,
        input=_prompt(document, sections, reask, cfg),
        max_output_tokens=int(cfg.get("max_output_tokens") or 4000))
    return response.output_text or ""


def _parse(raw, known: set[str]) -> dict | None:
    if isinstance(raw, dict):
        data = raw
    else:
        match = JSON_OBJECT.search(str(raw or ""))
        if not match:
            return None
        try:
            data = json.loads(match.group(0))
        except (TypeError, ValueError):
            return None
    if not isinstance(data, dict):
        return None
    out = {}
    for section in known:
        value = data.get(section, [])
        if not isinstance(value, list):
            return None
        out[section] = [item for item in value if isinstance(item, dict)]
    return out


def _promote(document, source: dict, parsed: dict, rules: dict,
             document_order: int) -> tuple[list[dict], int]:
    cfg = rules["section_recall"]
    minimum = int(cfg.get("min_quote_chars") or 10)
    maximum = int(cfg.get("max_quote_chars") or 500)
    mappings = cfg.get("sections") or {}
    cards, rejected = [], 0
    for section in mappings:
        for position, item in enumerate(parsed.get(section) or []):
            raw_quote = str(item.get("quote") or "").strip()
            if not quote_is_exact(getattr(document, "text", "") or "", raw_quote, minimum):
                rejected += 1
                continue
            quote = raw_quote[:maximum].strip()
            number_raw = str(item.get("number_raw") or "").strip()
            unit_raw = str(item.get("unit_raw") or "").strip()
            value, unit = None, unit_raw or None
            if number_raw and _norm(number_raw) in _norm(quote):
                parsed_value, parsed_unit, _approx = parse_number(
                    number_raw, unit_raw, rules["units"])
                if parsed_value is not None:
                    value, unit = parsed_value, parsed_unit or unit
            year = str(item.get("year") or "").strip()
            period = year if year and _norm(year) in _norm(quote) else None
            source_identity = str(getattr(document, "trace_id", "") or source["url"])
            digest = hashlib.sha256(
                json.dumps([source_identity, section, _norm(quote)], ensure_ascii=False,
                           separators=(",", ":")).encode("utf-8")).hexdigest()[:24]
            channel_eligible = section == "CHANNEL" and any(
                _norm(anchor) in _norm(quote)
                for anchor in (cfg.get("channel_anchors") or []) if _norm(anchor))
            card_prefix = "C-SEC-CH-" if channel_eligible else "C-SEC-"
            cards.append({
                "카드_id": card_prefix + digest,
                "종류": "관측",
                "칸": mappings[section],
                "계량": mappings[section],
                "주제": mappings[section],
                "기간": period,
                "값": value,
                "단위": unit,
                "등급": source["grade"],
                "등급_근거": f"기존 source kind {source['kind']}의 등급을 그대로 사용",
                "채택": True,
                "출처_url": source["url"],
                "kind": source["kind"],
                "조회일": source["retrieved_at"],
                "인용": quote,
                "_section": section,
                "_source_identity": source_identity,
                "_document_order": document_order,
                "_passage_order": position,
            })
    return cards, rejected


def _dedup_and_cap(cards: list[dict], cfg: dict) -> tuple[list[dict], list[dict], int]:
    unique = {}
    for card in cards:
        key = (card["_source_identity"], card["_section"], _norm(card["인용"]))
        unique.setdefault(key, card)
    grouped = {section: [] for section in (cfg.get("sections") or {})}
    for card in unique.values():
        grouped[card["_section"]].append(card)
    kept, sampled = [], []
    cap = int(cfg.get("max_promoted_per_section") or 4)
    for section in (cfg.get("sections") or {}):
        rows = sorted(grouped[section], key=lambda card: (
            card.get("값") is None, -GRADE_RANK.get(card.get("등급"), 0),
            card["_document_order"], card["_passage_order"], card["카드_id"]))
        kept.extend(rows[:cap])
        if len(rows) > cap:
            sampled.append({"stage": "collect", "code": "SECTION_EVIDENCE_SAMPLED",
                            "detail": f"{section}: 전체 {len(rows)}건 중 {cap}건 공개"})
    for card in kept:
        for key in tuple(card):
            if key.startswith("_"):
                card.pop(key)
    return kept, sampled, len(unique)


def execute(*, docs: dict, ledger, coverage: list, rules: dict, meter=None,
            call_budget: int, deadline_monotonic: float | None = None,
            provider=None, clock=time.monotonic) -> dict:
    cfg = rules["section_recall"]
    degradations, all_cards = [], []
    attempts, quote_rejected = 0, 0
    base_attempts = successful_responses = provider_failures = timeouts = bad_json = 0
    candidate_passages = quote_verified = 0
    if not cfg.get("enabled"):
        return {"cards": [], "attempts": 0, "degradations": []}
    hard_cap = int(cfg.get("max_attempts") or 10)
    allowance = min(hard_cap, max(0, int(call_budget)))
    if allowance <= 0:
        return {"cards": [], "attempts": 0, "degradations": [{
            "stage": "collect", "code": "SECTION_RECALL_BUDGET_SKIPPED",
            "detail": "summary reserve 뒤 section provider 예산이 없다"}]}

    selected = select_documents(docs, ledger, coverage, rules)
    started = clock()
    known = set(cfg.get("sections") or {})
    counts = {section: 0 for section in known}
    reasked_sources = set()
    caller = provider or (lambda doc, sections, reask:
                          _default_provider(meter, doc, sections, reask, cfg))

    def can_start() -> bool:
        call_timeout = float(cfg.get("per_call_timeout_sec") or 30)
        if (attempts >= allowance
                or clock() - started + call_timeout > float(cfg.get("max_wall_sec") or 120)):
            return False
        guard = float(cfg.get("deadline_guard_sec") or 30)
        return deadline_monotonic is None or deadline_monotonic - clock() >= guard + call_timeout

    def run_batch(batch, sections: list[str], reask: bool) -> bool:
        nonlocal attempts, base_attempts, quote_rejected, quote_verified
        nonlocal successful_responses, provider_failures, timeouts, bad_json
        nonlocal candidate_passages
        if not batch or not can_start():
            return False
        batch = batch[:max(0, allowance - attempts)]
        attempts += len(batch)  # 실패도 attempt budget을 소비한다.
        if not reask:
            base_attempts += len(batch)
        workers = min(4, max(1, int(cfg.get("workers") or 1)), len(batch))
        call_timeout = float(cfg.get("per_call_timeout_sec") or 30)
        wall_remaining = max(0.0, float(cfg.get("max_wall_sec") or 120)
                             - (clock() - started))
        task_remaining = float("inf") if deadline_monotonic is None else max(
            0.0, deadline_monotonic - clock()
            - float(cfg.get("deadline_guard_sec") or 30))
        batch_timeout = min(call_timeout, wall_remaining, task_remaining)
        if batch_timeout <= 0:
            return False
        executor = concurrent.futures.ThreadPoolExecutor(max_workers=workers)
        future_rows = [(row, executor.submit(caller, row["document"], sections, reask))
                       for row in batch]
        try:
            done, pending = concurrent.futures.wait(
                [future for _row, future in future_rows], timeout=batch_timeout)
            for row, future in future_rows:
                if future not in done:
                    continue
                try:
                    raw = future.result()
                except Exception as error:  # noqa: BLE001 - 기존 collect를 살리는 soft path
                    provider_failures += 1
                    degradations.append({"stage": "collect", "code": "SECTION_RECALL_FAILED",
                                         "detail": f"section provider 실패: {type(error).__name__}"})
                    continue
                parsed = _parse(raw, known)
                if parsed is None:
                    bad_json += 1
                    degradations.append({"stage": "collect", "code": "SECTION_RECALL_BAD_JSON",
                                         "detail": "section provider JSON 계약 불일치"})
                    continue
                successful_responses += 1
                candidate_passages += sum(len(parsed.get(section) or []) for section in known)
                promoted, rejected = _promote(
                    row["document"], row["source"], parsed, rules, selected.index(row))
                quote_rejected += rejected
                quote_verified += len(promoted)
                all_cards.extend(promoted)
                for section in known:
                    counts[section] += sum(1 for card in promoted
                                           if card.get("_section") == section)
            if pending:
                timeouts += len(pending)
                degradations.append({"stage": "collect", "code": "SECTION_RECALL_TIMEOUT",
                                     "detail": f"section provider batch timeout: {len(pending)}건"})
                for future in pending:
                    future.cancel()
            return bool(pending)
        finally:
            executor.shutdown(wait=False, cancel_futures=True)

    batch_size = min(4, max(1, int(cfg.get("workers") or 1)))
    timed_out = False
    for offset in range(0, len(selected), batch_size):
        if not can_start():
            break
        timed_out = run_batch(
            selected[offset:offset + batch_size], list(cfg["sections"]), False)
        if timed_out:
            break

    missing = [section for section in (cfg.get("reask_priority") or [])
               if counts.get(section, 0) < int(cfg.get("thin_below") or 1)]
    for row in selected:
        if (timed_out or not missing
                or len(reasked_sources) >= int(cfg.get("max_reasks") or 2)
                or not can_start()):
            break
        source_id = str(getattr(row["document"], "trace_id", ""))
        if source_id in reasked_sources:
            continue
        reasked_sources.add(source_id)
        timed_out = run_batch([row], missing, True)
        missing = [section for section in missing
                   if counts.get(section, 0) < int(cfg.get("thin_below") or 1)]

    if quote_rejected:
        degradations.append({"stage": "collect", "code": "SECTION_QUOTE_REJECTED",
                             "detail": f"원문 substring 불일치 passage {quote_rejected}건 거부"})
    if clock() - started >= float(cfg.get("max_wall_sec") or 120):
        degradations.append({"stage": "collect", "code": "SECTION_RECALL_TIMEOUT",
                             "detail": "section recall wall-clock 상한 도달"})
    elif deadline_monotonic is not None and deadline_monotonic - clock() < float(
            cfg.get("deadline_guard_sec") or 30):
        degradations.append({"stage": "collect", "code": "SECTION_RECALL_TIMEOUT",
                             "detail": "Task deadline guard로 새 section call 중단"})

    cards, sampled, promoted_before_cap = _dedup_and_cap(all_cards, cfg)
    degradations.extend(sampled)
    section_counts = {section: sum(
        1 for card in cards
        if card.get("계량") == (cfg.get("sections") or {}).get(section))
        for section in (cfg.get("sections") or {})}
    return {"cards": cards, "attempts": attempts, "degradations": degradations,
            "selected_documents": len(selected), "reasks": len(reasked_sources),
            "base_attempts": base_attempts,
            "successful_responses": successful_responses,
            "provider_failures": provider_failures,
            "timeouts": timeouts,
            "bad_json": bad_json,
            "candidate_passages": candidate_passages,
            "quote_verified": quote_verified,
            "quote_rejected": quote_rejected,
            "promoted_before_cap": promoted_before_cap,
            "promoted_after_cap": len(cards),
            "section_counts": section_counts,
            "wall_seconds": round(clock() - started, 3)}


def load(run_id: str) -> dict:
    try:
        path = os.path.join(runpath.read_dir(run_id), "section_recall.json")
        with io.open(path, encoding="utf-8") as handle:
            value = json.load(handle)
        return value if isinstance(value, dict) else {}
    except (OSError, ValueError):
        return {}


def load_cards(run_id: str) -> list[dict]:
    return [card for card in (load(run_id).get("cards") or []) if isinstance(card, dict)]
