# -*- coding: utf-8 -*-
"""설계 대조 — **설계된 슬롯 집합을 기준 스냅샷과 견준다.** LLM 0회 · 원장 쓰기 0.

    python tools/design_score.py --tag ds-pin06 \
        --slots data/slots_hmr-pin06.json --ref data/slots_hmr-pin09.json \
        --concept data/concept_hmr-solo.json

왜 이 도구인가 — **재는 자가 없었다.** 기존 도구는 이 자리를 못 본다:

  `tools/scorecard.py`      수집 **결과**를 잰다. 설계가 나빠도 검색이 운 좋으면 높다.
  `tools/slot_dryrun.py`    설계를 잰다. 하지만 **절대 검사**다 — 기준 설계가 없다.
  `tools/harness_agreement.py` 기준 대조를 한다. 하지만 골든이 하드코딩(`{"미용실": ...}`)이고
                            스냅샷 두 개를 서로 못 먹인다.

⚠ **성적표를 믿지 마라 — 이 도구가 존재하는 이유다.** 판 ㉛ 9회차에서 `pin-06` 이
  성적표 **6/6 을 내면서 오답**이었다(수요 칸을 「70대 이상 1인 가구 우울증상유병률」이
  채웠다 — 인구만 맞고 문제의 종류가 다르다). 문턱은 **개수를 세지 종류를 안 본다.**
  이 도구의 `must_contain_규율` 축은 바로 그 판본을 감점하도록 짜였다. 성적표와 답이
  갈리는 것이 이 축의 존재 이유이지 고장이 아니다.

**축 넷 — 전부 판 ㉛ 9회차 실측에서 나왔다. 지어낸 축은 없다.**

  서식지_분산       한 칸에 표적 하나면 그 서식지를 검색이 못 물어온 판은 칸이 통째로 빈다.
                    `pin-05`(14→17 분산)가 가격을 열었고 `pin-09`(→21)가 6/6 을 냈다.
                    ⚠ **분산은 subject 로 한다** — `plan_query` 가 subject·metric·period·
                    region 으로 검색어를 만들어서(`adapters/web.py`), `must_contain` 만
                    다르고 subject 가 같으면 **같은 검색어를 두 번 던지는 것**이다(`pin-05` 함정).
  must_contain_규율 `must_contain` 은 `any()` 다(`blocks/a_desk.py`) — 낱말을 늘리면
                    조여지는 게 아니라 **느슨해진다.** `pin-09` 의 비지 않은 9칸은 전부
                    낱말이 **하나**이고 그 낱말이 **자기 subject 의 부분문자열**이다.
                    실패한 것들은 이 한 줄을 정확히 어긴다 — `pin-06` 의 「문제」(subject
                    「1인 가구」에 없다) · `paid31a` 의 「성장」(metric 「거래액」과 무관).
  발행_가능성       회사를 지목한 표적은 대체로 빈손이다 — 그 값이 발행되지 않아서다
                    (실측: 「프레시지 월 구독료」 0건). 경쟁·채널 칸은 씨앗 실명을 **쓰는 것이
                    규칙**이므로(하네스 프롬프트 규칙 15) 이 축은 수요·가격 칸만 본다.
  계열_가격어휘     계열 C(제품·이커머스)에 `월 구독료` 를 쓰면 서비스 낱말로 묻는 것이다.
                    `판매가` 가 판 ㉚ 에서 **바로 이 실패 때문에** 신설돼 있다
                    (`harness/vocab.json` `metric.catalog.판매가._왜_신설했나`).

**판정하지 않는다.** `tools/slot_dryrun.py` 와 같은 규약이다 — 보이는 것을 값으로 적어
돌려주고, 유료 수집을 태울지는 부르는 쪽이 정한다. 만점이 「정답」을 뜻하지도 않는다:
기준인 `pin-09` 자체에 약한 고리가 있다(S13 의 「배달 이용경험률 85%」는 `문제 경험률`
칸에 앉은 **이용률**이다).

유리벽: `blocks/` import 0. 라우팅은 `gate.route_of` 를 그대로 쓴다 — 여기서 따로
구현하면 「재는 자엔 보이는데 수집기엔 안 보인다」가 생긴다.
"""
from __future__ import annotations

import argparse
import collections
import dataclasses
import io
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
for p in (ROOT, HERE, os.path.join(ROOT, "harness")):
    sys.path.insert(0, p)

import gate as G                                          # noqa: E402
import harness_agreement as HA                            # noqa: E402
import runpath                                            # noqa: E402
from runlog import load_rules                             # noqa: E402


def _load(p: str) -> dict:
    return json.load(io.open(p if os.path.isabs(p) else os.path.join(ROOT, p),
                             encoding="utf-8"))


def _slots(path: str) -> list:
    d = _load(path)
    return d.get("slots") or []


# ── 축 ① 서식지 분산 ────────────────────────────────────────────────
def 서식지_분산(slots: list, ref: list, req: dict) -> dict:
    """claim_type 별 **서로 다른 subject 개수**. 슬롯 개수가 아니다.

    `pin-05` 가 슬롯을 늘리고도 안 열린 칸이 있었던 이유가 이것이다 — subject 가 같으면
    검색어가 같아서 분산이 아니라 중복이었다.
    """
    대상 = list(req.get("대상_claim_type") or [])
    문턱 = int(req.get("최소_서로_다른_subject") or 0)

    def 세기(xs):
        out = collections.defaultdict(set)
        for s in xs:
            out[s.get("claim_type")].add(s.get("subject"))
        return {k: len(v) for k, v in out.items()}

    설계, 기준 = 세기(slots), 세기(ref)
    미달 = [{"claim_type": ct, "서로_다른_subject": 설계.get(ct, 0), "문턱": 문턱,
            "기준": 기준.get(ct, 0),
            "why": "표적이 한 서식지에 몰려 있다 — 그 서식지를 검색이 못 물어오면 칸이 통째로 빈다"}
           for ct in 대상 if 설계.get(ct, 0) < 문턱]
    점수 = round(sum(min(설계.get(ct, 0) / 문턱, 1.0) for ct in 대상) / len(대상), 4) \
        if (대상 and 문턱) else None
    return {"대상_claim_type": 대상, "문턱": 문턱,
            "설계": 설계, "기준": 기준, "미달": 미달, "점수": 점수,
            "_중복_주의": "subject 가 같고 must_contain 만 다른 슬롯은 여기서 1개로 센다 — "
                        "검색어가 같기 때문이다"}


# ── 축 ② must_contain 규율 ──────────────────────────────────────────
def must_contain_규율(slots: list) -> dict:
    """비지 않은 칸만 본다. **빈 칸은 위반이 아니다** — `pin-09` 의 TAM·COMP 12칸이 비어 있고
    그것이 옳다(경로 보증이 있거나 낱말로 가를 것이 없는 자리다).
    """
    위반, 대상 = [], 0
    for s in slots:
        mc = [w for w in (s.get("must_contain") or []) if str(w).strip()]
        if not mc:
            continue
        대상 += 1
        subj = str(s.get("subject") or "")
        나쁨 = []
        if len(mc) > 1:
            나쁨.append(f"낱말 {len(mc)}개 — must_contain 은 any() 라 늘리면 느슨해진다")
        for w in mc:
            if " " in w:
                나쁨.append(f"'{w}' 에 공백 — 원문 표기와 어긋나면 통과 불가능한 벽이 된다")
            elif w not in subj:
                나쁨.append(f"'{w}' 가 자기 subject 「{subj}」에 없다 — "
                            "아무 문서에나 있는 말이면 종류가 다른 값이 문턱을 넘는다")
        if 나쁨:
            위반.append({"slot_id": s.get("slot_id"), "claim_type": s.get("claim_type"),
                        "subject": subj, "metric": s.get("metric"),
                        "must_contain": mc, "why": 나쁨})
    return {"대상": 대상, "준수": 대상 - len(위반), "위반": 위반,
            "점수": round((대상 - len(위반)) / 대상, 4) if 대상 else None}


# ── 축 ③ 발행 가능성 ────────────────────────────────────────────────
def 발행_가능성(slots: list, concept: dict | None, adapters: dict, 대상_ct: list) -> dict:
    """수요·가격 칸이 **회사를 지목**하고 있는가. 그 값은 발행되지 않는다.

    경쟁(COMP·COMPARABLE)·채널 칸은 씨앗 실명을 쓰는 것이 **규칙**이라 여기서 보지 않는다.
    dart 로 라우팅되는 슬롯도 보지 않는다 — 공시는 회사 단위로 실제 발행된다.
    """
    if concept is None:
        return {"검사_안_함": "--concept 미지정 — 씨앗 이름을 모르면 회사 지목을 못 가른다",
                "점수": None}
    seeds = ((concept.get("_경쟁_씨앗") or {}).get("seeds") or [])
    이름 = sorted({str(x).strip() for s in seeds
                  for x in (s.get("이름"), s.get("운영사")) if str(x or "").strip()},
                 key=len, reverse=True)
    위반 = []
    for s in slots:
        if s.get("claim_type") not in 대상_ct:
            continue
        route, _why = G.route_of(s, adapters)
        if route == "dart":
            continue
        subj = str(s.get("subject") or "")
        맞은 = [n for n in 이름 if n in subj]
        if 맞은 or s.get("corp_name"):
            위반.append({"slot_id": s.get("slot_id"), "claim_type": s.get("claim_type"),
                        "subject": subj, "metric": s.get("metric"), "route": route,
                        "지목": 맞은 or [s.get("corp_name")],
                        "why": "회사를 지목한 수요·가격 표적은 발행되지 않는다 "
                               "(실측: 「프레시지 월 구독료」 0건). 통계·보도에 실제로 실리는 "
                               "대체재·이용 행태로 물어라"})
    대상 = sum(1 for s in slots if s.get("claim_type") in 대상_ct)
    return {"씨앗_이름": 이름, "대상": 대상, "위반": 위반,
            "점수": round((대상 - len(위반)) / 대상, 4) if 대상 else None}


# ── 축 ⑤ value_range 자릿수 ─────────────────────────────────────────
def value_range_자릿수(slots: list, guards: dict) -> dict:
    """슬롯의 `value_range` 가 그 계량의 **전형 밴드**와 겹치는가.

    **이 축은 라벨에서 나오지 않았다.** pin-01~09 아홉 판 전부 TAM·GROWTH·COMP 의 밴드를
    사람이 옳게 적어 **신호가 0이었다** — 그래서 이 도구의 첫 판이 네 축을 만들면서
    가장 크게 죽이는 원인을 **구조적으로 못 봤다.** 판 ㉜ 유료 실측에서 그 대가를 치렀다:
    하네스가 `[1e8, 2e9]` 를 적었고 참값은 38.0조라 **6슬롯·성적표 4과목·blocker 1개**가
    한 원인으로 죽었다. 검색은 값을 찾아왔고 우리가 버렸다.

    ⚠ **기준(`--ref`)과 무관한 절대 축이다.** 그래야 기준의 흠을 안 따라간다 —
      `pin-09` 자신도 이 축에서 걸린다(S1 이 `[1e9, 5e10]`, 거래액 전형은 1e11~1e14).
      그 6/6 의 ①시장크기는 자릿수 차이 2.88 로 문턱 3.0 을 **간신히** 지나 서 있었다.
    """
    bands = ((guards.get("value_range") or {}).get("계량_전형_밴드") or {})
    대상, 위반 = 0, []
    for s in slots:
        band = (bands.get(s.get("metric")) or {}).get("밴드")
        vr = s.get("value_range")
        if not band or not vr or len(vr) != 2:
            continue                       # 밴드 없는 계량은 판정하지 않는다
        대상 += 1
        lo, hi = vr
        if hi < band[0] or lo > band[1]:
            위반.append({"slot_id": s.get("slot_id"), "metric": s.get("metric"),
                        "value_range": [lo, hi], "전형_밴드": list(band),
                        "why": "슬롯 기대가 계량의 전형 크기와 겹치지 않는다 — "
                               "이대로 수집하면 **맞는 값이 격리된다**"})
    return {"대상": 대상, "위반": 위반,
            "점수": round((대상 - len(위반)) / 대상, 4) if 대상 else None}


def off_slot_집계(run_ids: list) -> dict:
    """지난 실행에서 **무엇이 실제로 슬롯을 죽였나.** 판정이 아니라 계측이다.

    **왜 리포트에 싣나** — 이 도구의 축을 라벨(`pin-01~09`)에서만 뽑았더니, 라벨이 안 흔든
    축에 대해 눈이 없었다(위 `value_range_자릿수` 참조). 그런데 **무엇이 죽였는지는 원장에
    값으로 남아 있다.** 판 ㉜ 에서 이것을 먼저 세었으면 첫날 잡았다:
    `값범위 밖(자릿수 차이 4.3 > 3.0)` 이 6건으로 1등이었다.

    **다음 판의 규율:** 축을 새로 정하기 전에 이 집계를 먼저 본다. 라벨은 사람이 이미
    옳게 한 것을 안 흔들지만, 원장은 **실제로 무엇이 깨졌는지**를 안다.
    """
    tally, 표본 = collections.Counter(), {}
    for rid in run_ids:
        d = runpath.find(rid)
        p = os.path.join(d or "", "run.jsonl")
        if not d or not os.path.exists(p):
            continue
        for line in io.open(p, encoding="utf-8"):
            try:
                o = json.loads(line)
            except Exception:
                continue
            if o.get("node") != "a4_ledger":
                continue
            why = (o.get("payload") or {}).get("off_slot_reason")
            if not why:
                continue
            # 값이 박힌 사유는 앞머리로 묶는다 — 「값범위 밖(자릿수 차이 4.3 …)」이
            # 건마다 다른 문자열이면 집계가 1건씩 흩어진다.
            key = str(why).split("(")[0].split(":")[0].strip()
            tally[key] += 1
            표본.setdefault(key, str(why)[:110])
    return {"_왜": "무엇이 실제로 슬롯을 죽였나. **축을 정하기 전에 이것부터 센다** — "
                 "라벨이 안 흔든 축은 라벨에서 못 나오지만 원장은 알고 있다.",
            "실행": run_ids, "사유별": dict(tally.most_common()), "표본": 표본}


# ── 축 ④ 계열 가격 어휘 ─────────────────────────────────────────────
def 계열_가격어휘(slots: list, concept: dict | None, vocab: dict) -> dict:
    기피 = ((vocab.get("metric") or {}).get("_계열C_기피_가격계량") or [])
    계열 = ((concept or {}).get("_계열") or {}).get("계열") or ""
    if concept is None:
        return {"검사_안_함": "--concept 미지정 — 계열을 모르면 판단할 수 없다", "점수": None}
    if 계열 != "C":
        return {"계열": 계열 or "(미선언)", "검사_안_함": "계열 C 가 아니다", "점수": None}
    price = [s for s in slots if s.get("claim_type") == "PRICE"]
    위반 = [{"slot_id": s.get("slot_id"), "subject": s.get("subject"),
            "metric": s.get("metric"),
            "why": "계열 C 는 제품을 판다 — 「월 구독료」는 서비스 낱말이라 소비재 가격이 "
                   "그 말로 발행되지 않는다. `판매가` 가 이 실패 때문에 신설돼 있다"}
           for s in price if s.get("metric") in 기피]
    return {"계열": 계열, "기피_계량": 기피, "대상": len(price), "위반": 위반,
            "점수": round((len(price) - len(위반)) / len(price), 4) if price else None}


class ScoreError(RuntimeError):
    """부르는 쪽이 잘못 줬다. `SystemExit` 가 아니다 — 서버에서 부르면 워커가 통째로 넘어간다."""


@dataclasses.dataclass
class ScoreOptions:
    """⚠ 필드 이름은 CLI 인자의 `dest` 와 같아야 한다 — `main()` 이 `vars()` 를 붓는다."""

    tag: str
    slots: str
    ref: str
    concept: str = ""
    runs: str = ""


def score(a: ScoreOptions) -> dict:
    if not a.slots or not a.ref:
        raise ScoreError("--slots 와 --ref 가 둘 다 필요하다")
    설계, 기준 = _slots(a.slots), _slots(a.ref)
    if not 설계:
        raise ScoreError(f"슬롯이 없다: {a.slots}")
    vocab = _load(os.path.join(ROOT, "harness", "vocab.json"))
    req = ((vocab.get("요구") or {}).get("서식지_분산") or {})
    concept = _load(a.concept) if a.concept else None
    adapters = load_rules()["adapters"]

    축 = {
        "서식지_분산": 서식지_분산(설계, 기준, req),
        "must_contain_규율": must_contain_규율(설계),
        "발행_가능성": 발행_가능성(설계, concept, adapters,
                              list(req.get("대상_claim_type") or [])),
        "계열_가격어휘": 계열_가격어휘(설계, concept, vocab),
        # ⚠ **기준과 무관한 절대 축이다** — 그래야 기준(pin-09)의 흠을 안 따라간다.
        "value_range_자릿수": value_range_자릿수(설계, _load("rules/guards.v1.json")),
    }
    잰_축 = [v["점수"] for v in 축.values() if v.get("점수") is not None]
    report = {
        "_규칙": "설계를 기준 스냅샷과 견준다. LLM 0회 · 원장 쓰기 0. **판정하지 않는다** — "
                "보이는 것을 값으로 적고, 유료 수집을 태울지는 부르는 쪽이 정한다.",
        "tag": a.tag, "설계": a.slots, "기준": a.ref,
        "concept": a.concept or None,
        "슬롯수": {"설계": len(설계), "기준": len(기준)},
        # 구조 서명은 `harness_agreement` 것을 그대로 쓴다 — 문구가 아니라 뼈대를 보는
        # 이유가 그 파일에 적혀 있다. ⚠ 스냅샷끼리 견줄 때 「게이트」 항목은 둘 다 통과라
        # 늘 1.0 이고 아무것도 뜻하지 않는다.
        "구조": HA.agree(HA.signature(설계, True), HA.signature(기준, True)),
        "축": 축,
        "잰_축_평균": round(sum(잰_축) / len(잰_축), 4) if 잰_축 else None,
        # **축을 정하기 전에 이것부터 본다** — 판 ㉜ 의 배움. 라벨이 안 흔든 축은
        # 라벨에서 못 나오지만, 무엇이 실제로 죽였는지는 원장에 값으로 남아 있다.
        "지난_실행_off_slot": (off_slot_집계([x.strip() for x in a.runs.split(",") if x.strip()])
                           if a.runs else
                           {"_안_잰_이유": "--runs 미지정. 지난 실행 id 를 주면 "
                                        "**무엇이 실제로 슬롯을 죽였는지** 세어 함께 낸다"}),
        "_같이_볼_것": "tools/slot_dryrun.py — value_range 폭·힌트 겹침 같은 **절대** 가드는 "
                     "거기서 본다. 이 도구는 그것을 다시 구현하지 않는다.",
        "_만점이_정답은_아니다": "기준(pin-09)에도 약한 고리가 있다 — S13 「배달 이용경험률 "
                             "85%」는 `문제 경험률` 칸에 앉은 이용률이다. 인용은 눈으로 봐라.",
    }
    out_dir = runpath.write_dir(f"design-{a.tag}")
    os.makedirs(out_dir, exist_ok=True)
    path = os.path.join(out_dir, "design.json")
    io.open(path, "w", encoding="utf-8").write(
        json.dumps(report, ensure_ascii=False, indent=1, default=dict))
    report["_산출"] = path

    print(f"\n== {a.tag} ==  설계 {len(설계)}슬롯 · 기준 {len(기준)}슬롯 · "
          f"구조 일치 {report['구조']['_평균']}")
    for name, v in 축.items():
        if v.get("검사_안_함"):
            print(f"  {name:<18} —      {v['검사_안_함']}")
            continue
        n = len(v.get("위반") or v.get("미달") or [])
        print(f"  {name:<18} {v['점수']}  위반 {n}건")
        for w in (v.get("위반") or v.get("미달") or [])[:6]:
            print(f"      {w.get('slot_id') or w.get('claim_type')}: "
                  f"{(w['why'][0] if isinstance(w['why'], list) else w['why'])[:88]}")
    print(f"  잰 축 평균 {report['잰_축_평균']}\n산출: {path}")
    return report


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--tag", required=True)
    ap.add_argument("--slots", required=True, help="잴 설계")
    ap.add_argument("--ref", required=True, help="기준 스냅샷 (예: data/slots_hmr-pin09.json)")
    ap.add_argument("--concept", default="",
                    help="발행_가능성·계열_가격어휘 축에 필요하다. 없으면 그 축은 «검사 안 함»")
    ap.add_argument("--runs", default="",
                    help="쉼표로 지난 실행 id. 그 원장들의 off_slot 사유를 세어 함께 낸다 — "
                         "**축을 새로 정하기 전에 이것부터 본다**(판 ㉜ 의 배움)")
    a = ap.parse_args()
    try:
        score(ScoreOptions(**vars(a)))
    except ScoreError as bad:
        raise SystemExit(str(bad))          # CLI 에서는 종전과 같이 멈춘다
    return 0


if __name__ == "__main__":
    sys.exit(main())
