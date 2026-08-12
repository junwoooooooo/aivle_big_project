# -*- coding: utf-8 -*-
"""발췌에 **무엇을 보낼지** 고른다. LLM 0회 · 네트워크 0회 · 순수 함수.

예전에는 문서 **앞에서 N자**를 잘랐다(`extract_doc_chars`). `paid31a-hmr` 실측에서
본문 도달률이 **16.2%** 였다 — 230만 자 중 37만 자. 모자란 게 아니라 **앞 20,000자에
값이 없어서**다. 요금표·통계표·시장 보고서의 숫자는 보통 중반 이후에 나온다.
상한을 올리면 토큰만 배로 들고 큰 문서에서는 비율이 그대로다.

**닻은 `tools/extract_triage.py` 와 같은 곳을 본다** — 계량 낱말·주제 낱말의 위치.
같은 물음을 두 곳이 각자 풀면 「탐색기엔 보이는데 수집기엔 안 보인다」가 생긴다.

⚠ 그러나 **판정 조건까지 공유하지는 않는다.** triage 의 잣대는 「조건을 만족하는 값이
  실재하는가」를 **엄격히** 가리는 것이라(주제 + 단위 + `value_range` 안 숫자를 전부 요구)
  그대로 쓰면 표현이 다른 문서를 통째로 버린다. 창 고르기는 **거르는 게 아니라 고르는 것**
  이므로 그 신호들을 **탈락 조건이 아니라 순위**로 쓴다. 거르는 일은 여전히 A4 가 한다.

⚠ 심사 완화가 아니다 — 문턱·점수·가드 무변경. **무엇을 심사대에 올리는가**만 바뀐다
  (판 ㉚ `extract_priority` 와 같은 결).
"""
from __future__ import annotations

import re

#: 원문에서 구간을 건너뛰었다는 표시. 모델이 「여기서 이어지지 않는다」를 알아야
#: 앞뒤를 한 문장으로 잇지 않는다.
ELISION = "\n…(중략)…\n"

_NUM = re.compile(r"\d[\d,]*(?:\.\d+)?")
#: 낱말 쪼개기 — `extract_triage` 와 **같은 규율**(2자 미만은 닻이 아니다).
#: 「시장」·「용품」 같은 흔한 2자 낱말로 주제를 가르지 못한다는 것은 triage 가 배운 것이고,
#: 여기서는 그것이 **탈락이 아니라 낮은 점수**로 나타난다.
_SPLIT = re.compile(r"[\s·/,()\[\]]+")


def anchors_of(slot) -> list[str]:
    """어디를 볼 것인가. **계량 낱말 + 주제 낱말**, 긴 것부터."""
    words = set()
    for raw in (getattr(slot, "metric", "") or "", getattr(slot, "subject", "") or ""):
        for w in _SPLIT.split(raw):
            if len(w) >= 2:
                words.add(w)
    return sorted(words, key=len, reverse=True)


def _score(window: str, slot, anchors: list[str]) -> int:
    """이 구간이 얼마나 값이 있어 보이는가. **탈락시키지 않는다 — 순위만 매긴다.**"""
    unit = getattr(slot, "unit", "") or ""
    vr = getattr(slot, "value_range", None) or []
    n = sum(1 for a in anchors if a in window)          # 닻이 많이 겹칠수록 좋다
    if unit and unit in window:
        n += 1
    for m in _NUM.finditer(window):
        try:
            v = float(m.group(0).replace(",", ""))
        except ValueError:
            continue
        if not vr:
            n += 1
            break
        if vr[0] <= v <= vr[1]:
            n += 2                                      # 범위 안 숫자가 가장 센 신호다
            break
    return n


def _merge(spans: list[tuple[int, int]]) -> list[tuple[int, int]]:
    out: list[tuple[int, int]] = []
    for lo, hi in sorted(spans):
        if out and lo <= out[-1][1]:
            out[-1] = (out[-1][0], max(out[-1][1], hi))
        else:
            out.append((lo, hi))
    return out


def _used(spans: list[tuple[int, int]]) -> int:
    """이 구간들을 보내면 몇 글자인가. **중략 표시도 글자다.**"""
    return sum(hi - lo for lo, hi in spans) + len(ELISION) * max(0, len(spans) - 1)


def _grow(spans: list[tuple[int, int]], total: int, max_chars: int) -> list[tuple[int, int]]:
    """**예산을 다 쓸 때까지 창을 바깥으로 넓힌다.**

    ⚠ 이것이 없으면 창 방식이 앞자르기보다 **글자를 적게** 보낸다 — 실측(win-on):
    창 15개 문서에서 평균 1.3개 창만 담아 상한 20,000자 중 3~6천 자만 썼고, 전체로는
    앞자르기보다 20% 적었다. 그러면 대조가 「좋은 자리 vs 나쁜 자리」가 아니라
    **「적은 입력 vs 많은 입력」**이 되고, 실제로 결과가 입력량을 따라 단조롭게 나빠졌다.
    창의 취지는 **같은 예산으로 다른 자리**다.
    """
    spans = _merge(spans)
    for _ in range(60):
        room = max_chars - _used(spans)
        if room <= 0:
            break
        if spans[0][0] == 0 and spans[-1][1] == total:
            break                                       # 더 넓힐 데가 없다
        step = max(100, room // (2 * len(spans)))
        cand = _merge([(max(0, lo - step), min(total, hi + step)) for lo, hi in spans])
        if _used(cand) > max_chars or cand == spans:
            break
        spans = cand
    # 남은 자리는 **뒤로** 채운다 — 상한을 정확히 쓰고 끝낸다.
    room = max_chars - _used(spans)
    if room > 0:
        lo, hi = spans[-1]
        spans = _merge(spans[:-1] + [(lo, min(total, hi + room))])
        room = max_chars - _used(spans)
        if room > 0:                                    # 뒤가 막혔으면 앞으로
            lo, hi = spans[0]
            spans = _merge([(max(0, lo - room), hi)] + spans[1:])
    return spans


def select(text: str, slot, rules: dict, max_chars: int) -> tuple[str, dict]:
    """(보낼 본문, 기록). **버린 양을 값으로 남긴다** — 「우리가 버렸다」와
    「자료가 없다」가 구별되지 않으면 §7 이 거짓이 된다(백로그 26 과 같은 계보)."""
    text = text or ""
    total = len(text)
    cfg = ((rules.get("adapters") or {}).get("web") or {}).get("extract_window") or {}
    base = {"chars_total": total, "chars_sent": min(total, max_chars),
            "chars_dropped": max(0, total - max_chars)}

    if total <= max_chars:
        return text, {**base, "mode": "whole", "chars_sent": total, "chars_dropped": 0}
    if not cfg.get("enabled"):
        return text[:max_chars], {**base, "mode": "head"}

    radius = int(cfg.get("radius") or 1200)
    anchors = anchors_of(slot)
    spans = []
    for a in anchors:
        for m in re.finditer(re.escape(a), text):
            spans.append((max(0, m.start() - radius), min(total, m.start() + radius)))
    spans = _merge(spans)
    if not spans:
        # **조용히 빈손을 만들지 않는다.** 닻이 없다고 아무것도 안 보내면 「자료 부재」가
        # 우리 탓으로 생긴다 — 예전 방식 그대로 앞에서 자른다.
        return text[:max_chars], {**base, "mode": "head_fallback", "anchors": 0}

    # 점수 높은 구간부터 예산까지 담고, **원문 순서로 되돌려** 잇는다.
    # 동점은 **앞에 있는 것 먼저** — 실행마다 갈리지 않게(정렬 열쇠에 위치를 넣는다).
    ranked = sorted(spans, key=lambda s: (-_score(text[s[0]:s[1]], slot, anchors), s[0]))
    picked: list[tuple[int, int]] = []
    used = 0
    for lo, hi in ranked:
        cost = hi - lo + (len(ELISION) if picked else 0)
        if used + cost > max_chars:
            continue                                    # 다음 구간은 더 작을 수 있다
        picked.append((lo, hi))
        used += cost
    if picked:
        picked = _grow(picked, total, max_chars)        # **예산을 다 쓴다**
    if not picked:
        # 창 하나가 예산보다 크다 — 가장 점수 높은 구간의 앞부분을 보낸다.
        lo, hi = ranked[0]
        return text[lo:lo + max_chars], {**base, "mode": "window", "windows": 1,
                                         "anchors": len(spans), "chars_sent": max_chars,
                                         "chars_dropped": total - max_chars}
    picked.sort()
    sent = ELISION.join(text[lo:hi] for lo, hi in picked)
    return sent, {**base, "mode": "window", "windows": len(picked),
                  "anchors": len(spans), "chars_sent": len(sent),
                  "chars_dropped": total - sum(hi - lo for lo, hi in picked)}
