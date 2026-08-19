# -*- coding: utf-8 -*-
"""**판단 문장을 기계로 쓴다.** LLM 0회 · 0원. (판 ㊷ 3단계)

    python tools/judge_lines.py runs-generated/p42-gate/publish.json \
           --concept data/concept_hmr-product.json

## 왜 LLM 에 안 맡기나

판 ㊵ 에서 LLM 집필층은 근거 없이 「적정 수준입니다」라고 썼고, 표에 유령 수를 넣었고,
억원↔백만원을 바꿨고, **그런데 검사기를 만점 통과했다.** 문장이 그럴듯하면 검사기가 못 잡는다.
그래서 판단은 기계가 하고, 기계가 못 하는 것은 **안 쓴다.**

## 규칙 셋 — 어기면 문장이 아예 안 나온다

1. **절 머리에 선 사실만 인용한다** (`publish_gate.머리인가`). 새 사실을 만들지 않는다
2. **비교쌍이 둘 다 있을 때만 쓴다.** 한쪽이 없으면 그 갈래는 통째로 침묵한다
3. **모든 수는 인용 사실의 `number_raw` 이거나 그것들로부터의 산술**이다.
   파생 수는 계산식을 같이 적는다 — 사업가가 손으로 검산할 수 있어야 한다

## 무엇을 답하나

성공 판정 ① **「내 가격이 시장 어디에 서 있나 — 비교 대상·배수, 그리고 어느 쪽으로 팔라」**.
"""
from __future__ import annotations

import argparse, io, json, os, re, sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
for p in (ROOT, HERE):
    sys.path.insert(0, p)

import publish_gate as PG

_NUM = re.compile(r"-?[0-9][0-9,]*(?:\.[0-9]+)?")


def _값(it: dict):
    """`number_raw` 를 수로. 못 읽으면 None — **추측하지 않는다.**"""
    m = _NUM.search(str(it.get("number_raw") or ""))
    if not m:
        return None
    try:
        return float(m.group(0).replace(",", ""))
    except ValueError:
        return None


def _실린(d: dict) -> list:
    out = []
    for r in d["문서별"]:
        for it in r.get("items", []):
            # ⚠ **판단 문장은 «절 머리»만 인용한다** (판 ㊹ 3단계). 서랍(`밖`)은
            #    「해외다」·「상위 범주다」처럼 **읽는 법이 붙은** 값이라, 그대로 판단에
            #    쓰면 「우리 시장이 2,970억 달러」 같은 문장이 나온다.
            if not PG.머리인가(it):
                continue
            sec = PG.절(it)
            out.append({**it, "_절": sec, "_url": r.get("url") or ""})
    return out


def _윗값(it: dict):
    """**범위값의 위끝.** 없으면 그냥 값.

    ⚠ 실측(판 ㊹ 6단계): 「CU 도시락 가격 범위」의 `number_raw` 가 **`3,900`** 인데
      인용은 「최소 3,900원부터 **최대 6,500원**까지」였다. 대체재와 견줄 때 아래끝을 쓰면
      **격차가 두 배로 부푼다**(5,000원 → 실제 2,400원). 방향은 맞아도 크기가 거짓이 된다.
      → 인용 안에서 **같은 단위의 더 큰 수**를 찾아 위끝으로 쓴다. 없으면 안 만든다.
    """
    v = _값(it)
    if v is None:
        return None
    q = str(it.get("quote") or "")
    후보 = [float(m.replace(",", "")) for m in _NUM.findall(q)]
    # **자릿수가 같은 것만** 위끝 후보다 — 「6,500」은 되고 「2025(연도)」·「1g당 100」은 안 된다
    같은자리 = [x for x in 후보 if v <= x < v * 10]
    return max(같은자리) if 같은자리 else v


def _원(it: dict) -> bool:
    return str(it.get("unit_raw") or "").strip() in ("원", "")


def 가격_판단(실린: list, 정가: float, R: dict, V: dict) -> dict:
    """컨셉 가격이 어디에 서는지. **비교쌍이 없으면 그 갈래는 안 쓴다.**"""
    가격절 = [it for it in 실린 if it["_절"] == "PRICE" and _원(it) and _값(it)]

    # ① 경쟁사 개당단가 — 「같은 물건 한 개」의 값. 절 PRICE + 회사 출처 + 「단가」
    단가 = [it for it in 가격절
            if it["게재"] == "COMPETITOR_FIRM" and "단가" in str(it.get("subject") or "")]
    # ② 편의점 대체 — 컨셉이 problem 에 적은 「저가 편의점 제품」
    편의점 = [it for it in 가격절 if it["게재"] == "SUBSTITUTE"
             and "편의점" in " ".join(str(it.get(k) or "") for k in ("subject", "quote"))
             or (it["게재"] == "SUBSTITUTE" and "도시락" in str(it.get("subject") or ""))]
    # ③ 배달 대체 — 컨셉이 problem 에 적은 「배달은 최소주문금액과 배달비가 붙어」
    배달 = [it for it in 가격절 if it["게재"] == "SUBSTITUTE"
           and "배달" in " ".join(str(it.get(k) or "") for k in ("subject", "quote"))]

    갈래 = []

    if 단가:
        기준 = min(단가, key=_값)            # **가장 싼 것과 견준다** — 가장 불리한 비교다
        v = _값(기준)
        갈래.append({
            "무엇": "같은 진열대의 한 개 값",
            "문장": f"컨셉 가격 {정가:,.0f}원은 {기준['subject']} {기준['number_raw']}원의 "
                   f"{정가 / v:.2f}배다.",
            "계산": f"{정가:,.0f} ÷ {v:,.0f} = {정가 / v:.2f}",
            "근거": [기준],
        })

    if 편의점:
        # ⚠ **「가격 범위」의 하한과 견주면 격차가 두 배로 부푼다** (판 ㊹ 6단계 판정).
        #   실측: 「CU 도시락 가격 범위 3,900원보다 5,000원 비싸다」 — 그 표의 최고가는
        #   6,500원이고 진짜 격차는 2,400원이다. 방향은 맞지만 크기가 거짓이 된다.
        #   → **가장 비싼 것과 견준다.** 대체재는 「제일 좋은 대안」이 기준이어야 한다.
        기 = max(편의점, key=lambda it: _윗값(it))
        v = _윗값(기)
        아래 = _값(기)
        위 = 정가 > v
        폭 = ("" if v == 아래 else
              f" (그 표의 아래끝은 {아래:,.0f}원이라 폭이 {아래:,.0f}~{v:,.0f}원이다)")
        갈래.append({
            "무엇": "편의점으로 대체될 때",
            "문장": f"컨셉 가격 {정가:,.0f}원은 {기['subject']}의 **위끝** {v:,.0f}원보다 "
                   f"{'위다' if 위 else '아래다'} ({abs(정가 - v):,.0f}원 "
                   f"{'비싸다' if 위 else '싸다'}).{폭}",
            "계산": f"{정가:,.0f} − {v:,.0f} = {정가 - v:,.0f}",
            "근거": [기],
        })

    # 배달 한 끼는 **음식값 + 배달비**다. 둘 다 있을 때만 쓴다 (규칙 2).
    음식 = [it for it in 배달 if "배달비" not in str(it.get("subject") or "")
           and "주문액" not in str(it.get("subject") or "")]
    배달비 = [it for it in 배달 if "배달비" in str(it.get("subject") or "")]
    if 음식 and 배달비:
        a = min(음식, key=_값)
        b = min(배달비, key=_값)
        한끼 = _값(a) + _값(b)
        갈래.append({
            "무엇": "배달로 대체될 때",
            # ⚠ **연도를 문장에 박는다.** 실측: 두 항이 **둘 다 2018년** 값인데 연도가
            #   없어 「8% 차이로 근소」가 오늘 값처럼 읽혔다. 검산하는 사업가가 신뢰를 잃는다.
            "문장": f"배달 한 끼는 최소 {한끼:,.0f}원이다"
                   f"({a['subject']} {a['number_raw']}원 [{a.get('year') or '연도 없음'}] + "
                   f"{b['subject']} {b['number_raw']}원 [{b.get('year') or '연도 없음'}]). "
                   f"컨셉 가격 {정가:,.0f}원은 그보다 "
                   f"{'위다' if 정가 > 한끼 else '아래다'}.",
            "계산": f"{_값(a):,.0f} + {_값(b):,.0f} = {한끼:,.0f}",
            "근거": [a, b],
        })
    elif 배달:
        갈래.append({"무엇": "배달로 대체될 때", "문장": None,
                     "왜_못_쓰나": "배달 한 끼는 음식값과 배달비가 **둘 다** 있어야 셈이 된다. "
                                f"지금 실린 것은 {'음식값' if 음식 else '배달비'}뿐이다.",
                     "근거": []})

    # ── 결론은 **조건문 틀**이고, 틀의 어느 가지를 타는지는 위에서 **계산된 부호**가 정한다.
    # ⚠ 결론을 고정 문구로 박으면 그것이 기계 옷을 입은 「적정 수준입니다」다 — 판 ㊵ 의 병이
    #    되돌아온다. 실측으로 걸렸다: 8,900원이 배달 한 끼 8,244원보다 **위**인데도 틀이
    #    「배달 대체면 설 자리가 있다」를 그대로 찍었다.
    편 = next((g for g in 갈래 if g["무엇"] == "편의점으로 대체될 때" and g.get("문장")), None)
    배 = next((g for g in 갈래 if g["무엇"] == "배달로 대체될 때" and g.get("문장")), None)
    결론 = None
    if 편 and 배:
        편위 = 정가 > _값(편["근거"][0])
        배기준 = sum(_값(s) for s in 배["근거"])
        배위 = 정가 > 배기준
        배차 = abs(정가 - 배기준) / 배기준
        근소 = 배차 <= 0.10          # 10% 안이면 「위」라고 잘라 말하지 않는다
        if 편위 and 배위 and 근소:
            결론 = (f"**양쪽 다 위다 — 다만 배달과는 {배차 * 100:.0f}% 차이로 근소하다.** "
                   "편의점을 대체하는 물건이라면 값으로는 설 자리가 좁고, 배달을 대체하는 "
                   "물건이라면 값이 거의 같아 **값이 아닌 이유로 골라야 한다.**")
        elif 편위 and 배위:
            결론 = ("**양쪽 다 위다 — 값으로는 설 자리가 없다.** 값이 아닌 이유"
                   "(정량·조리 시간·보존)가 서지 않으면 이 가격은 지탱되지 않는다.")
        elif 편위 and not 배위:
            결론 = ("**편의점을 대체하는 물건이면 비싸고, 배달을 대체하는 물건이면 설 자리가 있다.**")
        elif not 편위:
            결론 = ("**편의점 값보다도 아래다.** 저가 경쟁에 들어가는 값이라 "
                   "프리미엄이라는 컨셉 서술과 어긋난다 — 둘 중 하나가 틀렸다.")
        결론 += " 어느 쪽으로 팔지는 **이 조사가 정하지 못한다** — 시장 인터뷰에서 물을 것."
    return {"정가": 정가, "갈래": 갈래, "결론": 결론}


def _pct(c: dict):
    """`%` 값만. 아니면 `None` — 단위를 무시하고 섞으면 셈이 거짓이 된다.

    ⚠ **승격 «카드»는 칸 이름이 다르다** — 사실은 `unit_raw`/`number_raw`, 카드는
      `단위`/`값` 이다. 실측(판 ㊹ 5단계 1차): 이 하나로 원가·채널·수요 판단이
      **전부 침묵했다.** 조용히 0건이 되는 자리라 눈에 안 띈다.
    """
    u = str(c.get("단위") if "단위" in c else c.get("unit_raw") or "").strip()
    if u != "%":
        return None
    v = c.get("값") if "값" in c else None
    return float(v) if isinstance(v, (int, float)) else _값(c)


def H_정체(c: dict) -> str:
    """`headline.정체` 를 늦게 부른다 — 서로 import 하면 순환이 된다."""
    import headline as H                                            # noqa: PLC0415
    return H.정체(c)


def _문턱() -> int:
    """얇은 마진의 선. **코드가 아니라 `rules/synthesize.v1.json`** 에 있다(절대규칙 7)."""
    try:
        R = json.load(io.open(os.path.join(ROOT, "rules", "synthesize.v1.json"),
                              encoding="utf-8"))
        return int(((R.get("대차대조") or {}).get("얇은_마진_문턱_pct")) or 10)
    except Exception:
        return 10


def 절_판단(머리: dict, 정가: float, 컨셉: dict | None = None) -> dict:
    """**절마다 「그래서 이 사업에 무엇인가」 한 문장.** LLM 0회 — 전부 기계 계산이다.

    ## 왜 기계인가

    판 ㊵ 에서 LLM 집필층은 근거 없이 「적정 수준입니다」라고 썼고, 표에 유령 수를 넣었고,
    억원↔백만원을 바꿨고, **그런데 검사기를 만점 통과했다**(`judge_lines.py` 머리말).
    그래서 이 층은 **셈과 조건문뿐**이다. 문장의 모든 수는 근거의 수이거나 그 둘의 나눗셈이고,
    나눗셈은 `계산` 칸에 그대로 보인다.

    ## ⚠ 비교쌍이 없으면 **통째로 침묵한다**

    「없는데 있는 것처럼」이 이 제품의 가장 큰 병이다. 한쪽이 없으면 문장을 짓지 않고
    **왜 못 쓰는지**를 남긴다(규칙 5 — 실패는 값이다).

    받는 것은 `headline.build()` 의 산출 — **절 머리에 선 것만** 본다.
    """
    out: dict = {}
    #: 컨셉이 든 주 채널. 4절 방향(지지/흔듦)이 **이것과 겹치는지**로 갈린다.
    주채널 = str((((컨셉 or {}).get("_hypotheses_v2") or {})
                .get("7_채널") or {}).get("주_채널_가정") or "")

    # ── 원가·수익성 — 「하나 팔면 얼마 남나」 ─────────────────
    #   ★ 목표 보고서가 6절에서 이긴 자리다. 업종 이익률 × 우리 판매가 = 한 개당 남는 돈.
    #   이 곱셈은 **업종을 안 탄다** — 미용실이면 시술가 × 업종 영업이익률이 된다.
    ue = (머리.get("UNIT_ECONOMICS") or {}).get("머리") or []
    이익률 = [c for c in ue if _pct(c) is not None
             and any(w in str(c.get("주제") or "") for w in ("영업이익률", "순이익률", "이익률"))]
    if 이익률 and 정가:
        기준 = min(이익률, key=lambda c: _pct(c))     # **가장 낮은 것** — 가장 불리한 쪽으로 본다
        r = _pct(기준)
        남 = 정가 * r / 100
        # ⚠ **원문이 「미만」이면 그 값은 상한이다.** 실측: 인용이 「영업이익률은 4% 미만으로
        #   낮으며, 감소하는 상황임」인데 문장이 「4%이다」로 나가 **356원이 상한이라는 말이
        #   빠졌다.** 사업가는 그것을 기댓값으로 읽는다.
        상한 = any(w in str(기준.get("인용") or "") for w in ("미만", "이하", "이내"))
        out["UNIT_ECONOMICS"] = {
            "문장": (f"{기준.get('주제')}이 {기준.get('_원문값')}"
                   + ("미만이다" if 상한 else "이다")
                   + f". 그 비율을 우리 판매가 {정가:,.0f}원에 그대로 대면 한 개를 팔 때 "
                     f"남는 돈은 {'많아야 ' if 상한 else '약 '}{남:,.0f}원이다."),
            "계산": f"{정가:,.0f} × {r}% = {남:,.0f}",
            # **방향은 그 판단이 이미 계산한 부호에서만 나온다.** 문턱은 `rules/` 에 있다.
            "방향": ("흔듦" if r < _문턱() else "지지"),
            "왜그쪽": (f"업계가 남기는 몫이 {r}% 로 문턱 {_문턱()}% 아래다 — "
                    f"값 인상이나 원가 절감 없이는 버티기 어렵다" if r < _문턱() else
                    f"업계가 남기는 몫이 {r}% 로 문턱 {_문턱()}% 위다"),
            "경계": ["이것은 **업계 평균**이지 우리 원가가 아니다 — 우리 원가는 이 조사가 못 구했다",
                   "판매가 전부가 우리에게 오지 않는다(채널 수수료·물류는 따로다)"],
            "근거": [기준],
        }
    elif 정가:
        out["UNIT_ECONOMICS"] = {"문장": None,
                                 "왜_못_쓰나": "업종 이익률이 절 머리에 한 건도 없다 — "
                                            "우리 원가를 모르는 상태에서 남는 돈을 셀 길이 없다"}

    # ── 시장 크기 — 「우리 자리는 그 안에서 얼마나 되나」 ────────
    ms = (머리.get("MARKET_SIZE") or {}).get("머리") or []
    우리 = [c for c in ms if c.get("_갈래") == "OURS_SEGMENT" and c.get("값")]
    상한 = [c for c in ms if c.get("_갈래") == "OURS_UMBRELLA" and c.get("값")]
    쓸상한 = None
    if 우리 and 상한:
        _a, _b = max(우리, key=lambda c: c["값"]), max(상한, key=lambda c: c["값"])
        쓸상한 = _b if (_b["값"] and _a["값"] < _b["값"]) else None
    if 우리 and 쓸상한:
        a, b = max(우리, key=lambda c: c["값"]), 쓸상한
        # ⚠ **넓은 쪽이 더 작으면 그것은 상한이 아니다.** 실측: 「컬리 거래액 3.5조」가
        #   상한으로 잡혀 「우리 시장이 상한의 192%」라는 헛소리가 나왔다.
        #   비율이 100%를 넘으면 **셈을 접는다** — 억지로 말하지 않는다.
        if True:
            out["MARKET_SIZE"] = {
                "문장": (f"우리 시장으로 잡은 가장 큰 값은 {a.get('주제')} {a.get('_원문값')}이고, "
                       f"그보다 넓은 {b.get('주제')} {b.get('_원문값')}의 "
                       f"**{a['값'] / b['값'] * 100:.1f}%**다."),
                "계산": f"{a['값']:,.0f} ÷ {b['값']:,.0f} = {a['값'] / b['값'] * 100:.1f}%",
                "경계": ["넓은 쪽은 **상한으로만** 읽는다 — 우리 시장은 이보다 작다"],
                "근거": [a, b],
            }
    elif 우리:
        a = max(우리, key=lambda c: c["값"])
        out["MARKET_SIZE"] = {
            "문장": f"우리 시장으로 잡은 가장 큰 값은 {a.get('주제')} {a.get('_원문값')}이다.",
            "방향": "지지", "왜그쪽": "들어갈 자리의 크기가 수로 확인된다",
            "계산": "", "경계": ["더 넓은 범주 값이 절 머리에 없어 **우리 자리의 비율은 못 잰다**"],
            "근거": [a]}

    # ── 채널 — 「어느 길이 큰가」 ─────────────────────────────
    ch = (머리.get("CHANNEL") or {}).get("머리") or []
    몫 = [c for c in ch if _pct(c) is not None]
    주채널겹침 = any(str(c.get("주제") or "").split()[0] in 주채널 for c in 몫 if c.get("주제"))
    if len(몫) >= 2:
        큰 = max(몫, key=lambda c: _pct(c))
        합 = sum(_pct(c) for c in 몫)
        누구 = 큰.get("_발행사") or ""
        out["CHANNEL"] = {
            "문장": (f"절 머리에 선 경로 {len(몫)}개 중 가장 큰 것은 {큰.get('주제')} "
                   f"{큰.get('_원문값')}이다."),
            # ⚠⚠ **남의 매출처 구성으로 «우리» 채널 계획을 흔들지 않는다** (판 ㊹ 최종 판정).
            #   실측 결함: 「컨셉이 든 주 채널이 안 보인다 — **큰 길은 다른 데 있다**」로 단정했다.
            #   그런데 근거는 **오뚜기 한 곳의 매출처 구성**이고, 대형마트·특약점·대리점은
            #   60년 된 식품 대기업의 유통망이지 **신생 D2C 가 고를 수 있는 길이 아니다.**
            #   같은 절이 스스로 붙인 경고(「오뚜기 한 곳의 구성비다」)와 **모순**이었고,
            #   사업가가 계획 채널을 근거보다 세게 접게 만드는 **노출 차단 사유**였다.
            #   → 「우리 계획 채널의 비중을 이 조사가 «못 구했다»」가 참인 문장이다.
            "방향": "지지" if 주채널겹침 else "못 정함",
            "왜그쪽": ("컨셉이 든 주 채널이 실제로 큰 몫을 쥐고 있다" if 주채널겹침 else
                    "이 표는 **남의 유통망**이라 우리 계획 채널(자사몰·이커머스)의 몫을 "
                    "말해 주지 않는다 — 그 값을 이 조사가 못 구했다"),
            "계산": f"머리에 선 것들의 합 = {합:.2f}%",
            "경계": ([f"이것은 시장 전체가 아니라 **{누구 or '어느 회사'} 한 곳의 매출처 구성비**다"]
                   + ([] if 99 <= 합 <= 101 else
                      [f"⚠ 머리에 선 것들의 합이 **{합:.1f}% 로 100%가 아니다** — 표의 일부만 보고 있다"])),
            "근거": 몫,
        }

    # ── 경쟁 — 「그 자리에 누가 얼마나 있나」 ─────────────────
    #   ⚠ 이 절에는 판단이 **아예 없었다**(구조적 결여). 전수 목록이 잡았다.
    cp = (머리.get("COMPETITOR") or {}).get("머리") or []
    점유 = [c for c in cp if _pct(c) is not None
           and any(w in str(c.get("주제") or "") for w in ("점유", "비중"))]
    if 점유:
        # ⚠ **해외 값을 우리 자리 판단의 머리로 삼지 않는다** (판 ㊹ 6단계 재판정).
        #   실측: 「상위 5개 기업 점유율 45%」의 인용이 Ajinomoto·Cargill·General Mills —
        #   **세계** 시장이었는데 「이 자리는 이미 임자가 있다」가 국내 이야기처럼 읽혔다.
        #   국내 값이 하나라도 있으면 그것을 쓰고, 없을 때만 해외 값을 쓰되 경계를 붙인다.
        _해외 = ("글로벌", "세계", "전세계", "Ajinomoto", "Cargill", "General Mills", "Nestle")
        국내 = [c for c in 점유
               if not any(w in f"{c.get('주제')} {c.get('인용')}" for w in _해외)]
        큰 = max(국내 or 점유, key=lambda c: _pct(c))
        out["COMPETITOR"] = {
            "문장": (f"{큰.get('주제')}이 {큰.get('_원문값')}로 절 머리에서 가장 크다. "
                   f"이 자리는 이미 임자가 있다."),
            "방향": "흔듦",
            "왜그쪽": "빈 자리가 아니다 — 이미 그만큼을 쥔 상대가 있다",
            "계산": "",
            # ⚠ **경계문을 템플릿으로 박으면 값과 어긋난다** (판 ㊹ 6단계 재판정).
            #   실측: 「상위 5개 기업 합산 45%」에 「이 값은 **어느 회사 한 곳**의 수다」가
            #   붙었다 — 한 회사도 아니고 시장 전체의 판도 값인데 **정반대**를 말했다.
            #   그리고 인용을 보면 Ajinomoto·Cargill·General Mills 로 **세계** 시장이다.
            #   → 갈래와 인용을 보고 **맞는 말만** 고른다. 모르면 안 붙인다.
            "경계": ([f"이 값은 **{H_정체(큰)}**의 수다 — 시장 전체의 판도가 아니다"]
                   if 큰.get("_갈래") == "COMPETITOR_FIRM" else
                   [f"이 값은 **{H_정체(큰)}** 기준이다"])
                   + ([ "⚠ **국내 값이 아니다** — 인용이 해외 기업을 든다. 우리 자리와 직접 견주지 않는다"]
                      if any(w in str(큰.get("인용") or "")
                             for w in ("글로벌", "세계", "Ajinomoto", "Cargill", "General Mills", "Nestle"))
                      else [])
                   + ["우리 몫을 여기서 빼서 계산하지 않는다 — **점유율은 남의 실적이지 우리 목표가 아니다**"],
            "근거": [큰]}

    # ── 수요 — 「몇 명이 그렇다고 했나」 ──────────────────────
    dm = (머리.get("DEMAND") or {}).get("머리") or []
    비율 = [c for c in dm if _pct(c) is not None]
    if 비율:
        큰 = max(비율, key=lambda c: _pct(c))
        out["DEMAND"] = {
            "문장": f"{큰.get('주제')}이 {큰.get('_원문값')}로 절 머리에서 가장 크다.",
            "방향": "지지",
            "왜그쪽": "이 사업이 풀겠다는 문제가 **수치로 실재한다**",
            "계산": "",
            "경계": ["**말한 것이지 산 것이 아니다** — 실제 구매 의사는 시장 인터뷰에서 묻는다"],
            "근거": [큰]}

    # ── 규제 — 「몇 가지를 지켜야 하나」 ──────────────────────
    rg = (머리.get("REGULATION") or {}).get("머리") or []
    if rg:
        # ⚠ **같은 주제가 여럿이면 한 번만 적는다.** 실측: 「즉석섭취식품, 즉석조리식품,
        #   신선편의식품 규」가 **세 번 이어져** 나왔다 — 근거가 셋인 것처럼 보이지만 하나다.
        이름 = []
        for c in rg:
            t = str(c.get("주제") or "").strip()[:24]
            if t and t not in 이름:
                이름.append(t)
        out["REGULATION"] = {
            "문장": (f"팔기 전에 확인해야 할 것으로 {len(이름)}가지가 절 머리에 섰다 — "
                   + " · ".join(이름[:3]) + "."),
            # ⚠ **요건의 «존재»는 이 사업안 고유의 반대 근거가 아니다** (판 ㊹ 최종 판정).
            #   모든 식품 사업의 상수라, 흔드는 칸에 넣으면 저울이 **실제보다 비관적으로 부푼다.**
            "방향": "해야 할 일",
            "왜그쪽": "팔기 전에 치러야 할 일이고, 그 비용과 기간은 이 조사가 안 쟀다",
            "계산": "",
            "경계": ["**법률 자문이 아니다.** 조문 원문은 직접 확인해야 한다"],
            "근거": rg}
    return out


def build(d: dict, c: dict) -> dict | None:
    """**가격 판단**을 낸다. 컨셉에 가격 제안값이 없으면 `None` — **지어내지 않는다.**

    판 ㊸ 1단계에서 `main()` 밖으로 꺼냈다. 돌려주는 모양은 `judgments.json` 과 같다.
    """
    R = PG._rules()
    V = PG._vocab(c, R)

    정가 = ((c.get("_hypotheses_v2") or {}).get("6_수익_가격") or {}).get("제안값_krw_월")
    if not 정가:
        return None

    실린 = _실린(d)
    res = 가격_판단(실린, float(정가), R, V)
    # ⚠ `year` 를 반드시 들고 간다. 이 판단은 **결론에 가장 가까운 자리**이고, 여기서
    #   연도가 빠지면 「배달 한 끼 8,244원」이 2018년 자장면값이라는 사실이 사라진다.
    #   그러면 「8% 차이로 근소」가 8년 묵은 수 위에 선 채 오늘 값처럼 읽힌다.
    칸 = ("number_raw", "unit_raw", "subject", "quote", "_url", "year")
    return {"가격": {**res, "갈래": [{**g, "근거": [{k: s.get(k) for k in 칸}
                                               for s in g["근거"]]} for g in res["갈래"]]}}


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("publish")
    ap.add_argument("--concept", required=True)
    ap.add_argument("--out", default="")
    a = ap.parse_args()

    d = json.load(io.open(a.publish, encoding="utf-8"))
    c = json.load(io.open(a.concept, encoding="utf-8"))

    out_doc = build(d, c)
    if out_doc is None:
        print("컨셉에 가격 제안값이 없다 — **판단을 지어내지 않는다.**")
        return 1
    res = out_doc["가격"]

    print(f"컨셉 가격 {res['정가']:,.0f}원 — 실린 사실 {len(_실린(d))}건 위에서 잰다\n")
    for g in res["갈래"]:
        print(f"■ {g['무엇']}")
        if g.get("문장"):
            print(f"   {g['문장']}")
            print(f"   계산: {g['계산']}")
            for s in g["근거"]:
                print(f"   근거: {s['number_raw']} {s.get('unit_raw')} «{s['subject']}»  {s['_url'][:64]}")
        else:
            print(f"   (안 쓴다) {g['왜_못_쓰나']}")
        print()
    if res["결론"]:
        print("⇒", res["결론"])
    else:
        print("⇒ (결론 없음) 비교쌍이 갖춰지지 않았다. **지어내지 않는다.**")

    out = a.out or os.path.join(os.path.dirname(a.publish), "judgments.json")
    io.open(out, "w", encoding="utf-8").write(
        json.dumps(out_doc, ensure_ascii=False, indent=1))
    print(f"\n기록: {out}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
