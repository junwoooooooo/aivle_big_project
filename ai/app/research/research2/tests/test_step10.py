# -*- coding: utf-8 -*-
"""판 ②-b-B 배선 검증 — PDF 적재 · 주입분 진단 · 파생 실행의 이어매기.
**LLM 0회 · 네트워크 0회.**

여기서 재는 것 넷:
  ① PDF 를 **매직바이트로** 알아본다 (관공서 `Download.do` 는 `octet-stream` 으로 준다)
  ② 본문을 못 얻은 PDF 는 `empty` 가 아니라 `pdf_unreadable` 이다 — 「빈 페이지」와 구분된다
  ③ `_seen_direct` 가 기존 `-direct-N` 을 세어 **덮어쓰기를 막는다** (파생 실행의 조용한 사고)
  ④ 주입분 `not_found` 는 §7 에서 빠지되 **진단 채널에는 남는다** (백로그 25)
  ⑤ 채널 가정 승격이 **계산을 채우되 판정을 올리지 않는다** (②-b-B 후속)

    python tests/test_step10.py
"""
from __future__ import annotations
import os, sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
for p in (ROOT, os.path.join(ROOT, "blocks"), os.path.join(ROOT, "adapters")):
    sys.path.insert(0, p)

import pdf_text
import run as RUN
from schema import Document, Finding, FindingItem, Report

ok, fail = 0, []


def check(name, cond, detail=""):
    global ok
    if cond:
        ok += 1
    else:
        fail.append(f"{name} — {detail}")
    print(f"  {'OK ' if cond else 'X  '} {name}" + (f"   {detail}" if not cond else ""))


CFG = pdf_text.load_pdf_cfg()


def _make_pdf(text: str) -> bytes:
    import fitz
    doc = fitz.open()
    page = doc.new_page()
    page.insert_text((72, 100), text)
    return doc.tobytes()


# ── ① 판별 ────────────────────────────────────────────────────
print("[1] is_pdf — 매직바이트가 Content-Type 보다 먼저다")
raw = _make_pdf("no-show 65 percent")
check("규칙이 실려 있다", bool(CFG) and CFG.get("magic") == "%PDF-", str(sorted(CFG)))
check("octet-stream 이어도 PDF 로 안다 (mss Download.do 실제 응답)",
      pdf_text.is_pdf(raw, "application/octet-stream;charset=UTF-8", CFG))
check("Content-Type 만으로도 안다", pdf_text.is_pdf(b"", "application/pdf", CFG))
check("HTML 을 PDF 로 오인하지 않는다",
      not pdf_text.is_pdf(b"<!doctype html><html>", "text/html; charset=utf-8", CFG))

# ── ② 추출과 실패값 ───────────────────────────────────────────
print("\n[2] extract — 실패는 값이다 (절대규칙 5)")
text, why = pdf_text.extract(raw, CFG)
check("본문을 얻는다", "65" in text, repr(text[:60]))
check("성공이면 사유가 비어 있다", why == "", repr(why))
broken, why2 = pdf_text.extract(b"%PDF-1.4 truncated garbage", CFG)
check("깨진 PDF 는 본문이 비고 사유가 남는다", broken == "" and why2, repr(why2))
check("상태값이 empty 와 구분된다",
      CFG.get("unreadable_status") == "pdf_unreadable" == pdf_text.UNREADABLE,
      str(CFG.get("unreadable_status")))
check("스캔본(텍스트층 없음)도 값으로 남는다",
      pdf_text.extract(_make_pdf(""), CFG)[1] != "", str(pdf_text.extract(_make_pdf(""), CFG)))
check("pdf_unreadable 은 usable 이 아니다 → 상한 2 가 그대로 걸린다",
      pdf_text.UNREADABLE != "usable")
check("schema 가 새 상태값을 받는다",
      Document(slot_id="S16", trace_id="t", url="u",
               content_status="pdf_unreadable").content_status == "pdf_unreadable")

# ── ③ 이어매기 ────────────────────────────────────────────────
print("\n[3] _seen_direct — 파생 실행에서 문서를 덮어쓰지 않는다")
restored = {f"S16-direct-{i}": Document(slot_id="S16", trace_id=f"S16-direct-{i}", url=f"u{i}")
            for i in range(8)}
restored["S16-q0-u0"] = Document(slot_id="S16", trace_id="S16-q0-u0", url="q")
restored["S1-direct-0"] = Document(slot_id="S1", trace_id="S1-direct-0", url="s1")
seen = RUN._seen_direct(restored)
check("슬롯별 기존 주입 수를 센다", seen == {"S16": 8, "S1": 1}, str(seen))
check("검색분(-q0-u0)은 세지 않는다", seen.get("S16") == 8, str(seen))
check("빈 원장이면 빈 dict (단일 사양은 예전과 이름이 같다)", RUN._seen_direct({}) == {})
check("새 문서가 기존 자리를 비껴간다",
      f"S16-direct-{seen['S16']}" not in restored, f"S16-direct-{seen['S16']}")

# ── ④ 주입분 진단 ─────────────────────────────────────────────
print("\n[4] injected_extract — 버리되 말없이 버리지 않는다 (백로그 25)")


def _fake_collect(path, slots, rules, meter, run, seen_per_slot=None):
    docs = {"S16-direct-8": Document(slot_id="S16", trace_id="S16-direct-8", url="pdf",
                                     channel="gov_doc", content_status="usable", text="65%")}
    findings = [
        Finding(slot_id="S16", trace_id="S16-extract-8", status="found",
                findings=[FindingItem(quote="65%", number_raw="65", unit_raw="%", url="pdf")],
                note="문서 1개 묶어 1회 호출"),
        # 주입 URL 이 **없는** 슬롯의 not_found — §7 을 오염시키면 안 되고 진단에도 안 남는다
        Finding(slot_id="S15", trace_id="S15-direct", status="not_found",
                note="direct_url 모드 — 이 슬롯에 지정된 URL 이 없다"),
    ]
    return findings, docs, {"direct_url": "ok"}, []


_orig = RUN._collect_direct
RUN._collect_direct = _fake_collect
try:
    f, d, st, diag = RUN._merge_direct("data/spec_mss.json", [], {}, None, None, {"S16": 8})
finally:
    RUN._collect_direct = _orig

check("주입분 found 는 원장으로 간다", [x.slot_id for x in f] == ["S16"], str(f))
check("URL 없는 슬롯의 가짜 not_found 는 버려진다 (§7 오염 0)",
      all(x.status != "not_found" for x in f))
check("진단에는 **주입이 실제로 있었던** 슬롯만 남는다",
      [x["slot_id"] for x in diag] == ["S16"], str(diag))
check("진단이 문서수·인용수·사양을 함께 적는다",
      diag[0]["docs"] == 1 and diag[0]["n_items"] == 1
      and diag[0]["spec"] == "spec_mss.json", str(diag[0]))
check("채널 태그가 사양의 것을 지킨다",
      d["S16-direct-8"].channel == "gov_doc", d["S16-direct-8"].channel)

r = Report()
check("Report 에 진단 칸이 있고 기본은 비어 있다", r.injected_extract == [])
check("§7 과 섞이지 않는다 — not_found 키에 안 들어간다",
      "injected_extract" not in r.not_found)

# ── ⑤ 채널 가정 승격 (②-b-B 후속) ────────────────────────────
print("\n[5] 채널 가정 승격 — 계산을 채우되 판정을 올리지 않는다")
sys.path.insert(0, os.path.join(ROOT, "service"))
import verdict as V                                                # noqa: E402

_cr = V._channel_rules()
_est = V._channel_assumption({"_목표_고객수": 100})

check("규칙 파일에서 온다 (코드에 상수 없음)",
      _cr["채택_밴드_usd"] == [80, 300] and _cr["환율_krw_per_usd"]["value"] == 1400,
      str(_cr["채택_밴드_usd"]))
check("경계 **두 문장**을 값과 함께 나른다", len(_est["경계"]) == 2, str(_est["경계"]))
check("  ① 해외 벤치마크임을 밝힌다",
      "국내 소상공인 CAC 관측이 아니다" in _est["경계"][0])
check("  ② 그대로 옮길 수 없음을 밝힌다",
      "그대로 옮길 수 없다" in _est["경계"][1])
check("가정 목록이 비어 있지 않다", len(_est["가정"]) == 4, str(len(_est["가정"])))
check("assumption_count 가 가정 수와 일치한다 (낮춰 적지 않는다)",
      _est["assumption_count"] == len(_est["가정"]) == 4)
check("등급은 **추정** — 확정·실무 신뢰와 섞지 않는다", _est["등급"] == "추정", _est["등급"])
check("관측이 아님을 스스로 밝힌다", "관측" in _est["_이것은_관측이_아니다"])
check("참고 출처가 «원장 밖» 임을 표시한다",
      "원장 밖" in _est["참고_출처"][0]["_지위"])
check("제외한 값의 사유를 남긴다 (밴드를 조용히 고르지 않는다)",
      len(_est["제외한_값"]) == 3, str(sorted(_est["제외한_값"])))
check("목표 고객 수가 있으면 필요 마케팅비를 계산한다",
      _est["필요_마케팅비_추정"]["값"] == [100 * 112000, 100 * 420000],
      str(_est["필요_마케팅비_추정"]["값"]))
check("목표 고객 수가 없으면 **곱하지 않고 사유를 남긴다**",
      "필요_마케팅비_추정" not in V._channel_assumption({})
      and V._channel_assumption({})["필요_마케팅비_사유"])

# 승격이 **판정을 올리지 않는다** — 이것이 이 변경의 핵심 안전장치다
_led = V.bm_scorer.load_ledger("beauty-07")
_led_ch = dict(_led, slots=_led["slots"] + [{"slot_id": "S_CH", "claim_type": "CHANNEL",
                                             "metric": "고객 획득 비용", "unit": "원"}])
_ch = V.judge_channel(_led_ch, {}, {"_목표_고객수": 100})
check("도장은 **미검증 그대로** — 가정 승격이 판정을 올리지 않는다",
      _ch["도장"] == "미검증", _ch["도장"])
check("  뒷문장(경계)은 여전히 붙는다",
      "채널 없이 BM 이 성립한다는 뜻이 아니다" in _ch["why"])
check("  관측(근거)은 여전히 0건 — 추정이 관측 자리를 차지하지 않는다",
      _ch["근거"] == [], str(_ch["근거"]))
check("  추정은 별도 칸에 실린다", _ch["추정"] is not None)

# CHANNEL 슬롯이 **없으면** 축_부재이고, 그때는 승격도 하지 않는다.
# ⚠ `beauty-07` 에는 S15(CHANNEL)가 **있다**(판 ① 신설). 그래서 슬롯을 빼고 만든다 —
#   원장을 그대로 쓰면 이 갈래가 아예 안 밟힌다(첫 판에 실제로 그렇게 틀렸다).
_led_none = dict(_led, slots=[s for s in _led["slots"]
                              if s.get("claim_type") != "CHANNEL"])
_ch0 = V.judge_channel(_led_none, {}, {"_목표_고객수": 100})
check("슬롯이 없으면 축_부재 유지 — 승격으로 축_부재를 덮지 않는다",
      _ch0["도장"] == "축_부재" and _ch0["추정"] is None,
      f"{_ch0['도장']} / {_ch0['추정']}")

print(f"\n===== {ok} 통과 / {len(fail)} 실패")
for x in fail:
    print("   -", x)
sys.exit(1 if fail else 0)
