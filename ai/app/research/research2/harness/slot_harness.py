# -*- coding: utf-8 -*-
"""슬롯 생성 하네스 최소 버전 (백로그 12) — 컨셉 1개 → 슬롯·식 스냅샷 1벌.

    concept.json → [LLM 1회] → 식·변수 초안 → [코드 칸 채우기] → [기계 게이트] → 스냅샷

**LLM 의 일은 「문장 짓기」가 아니라 「통제 어휘로 빈칸 채우기」다.** 자유 서술은 게이트에서
죽는다. 무엇을 어디까지 LLM 이 정하는지는 아래 분담표가 정본이다(2026-08-08 승인).

    LLM 칸 : metric · subject · period · unit · region · must_contain ·
             must_not_contain · value_range · claim_type
    코드 칸 : slot_id · var_id · formula_id 연결 · stat_code · accept

`stat_code` 는 코드가 **실재 대조**로 채우고 못 찾으면 빈칸으로 둔다 — 추측 금지.
`slot_id`·`var_id`·`formula_id` 를 코드가 잡는 이유는 B 블록 조인이
`formula_id + var_id` 이기 때문이다(blocks/b_estimate.py:74). LLM 이 이 셋을 적으면
오타 하나가 **조용한 조인 오류**가 된다.

유리벽: `blocks/` import 0 · 원장 쓰기 0. 엔진에는 파일로만 넘긴다.

실행:
    python harness/slot_harness.py --concept data/concept_beauty-noshow.json --tag beauty-noshow
    python harness/slot_harness.py ... --replay runs/harness/<tag>/llm_raw.json   # LLM 0회 재검사
"""
from __future__ import annotations

import argparse
import dataclasses
import datetime
import io
import json
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
sys.path.insert(0, HERE)
# ⚠ `runpath` 는 **ROOT** 에 있다(잎 모듈). HERE 만 넣으면 CLI 로 부를 때 죽는다 —
#   파이프라인에서 부를 때는 그쪽이 이미 ROOT 를 넣어 둬서 **드러나지 않았다**.
sys.path.insert(0, ROOT)

import runpath                                                    # noqa: E402
import gate as G                                                   # noqa: E402

# 1차 초안에서 gpt-4o-mini 는 형식 예시를 그대로 베끼거나 통제 어휘를 어겼다(2회 폐기).
# 이 일은 「빈칸 채우기」지만 빈칸이 서로 물려 있어서 작은 모델이 자리를 뒤섞는다.
MODEL = "gpt-4o"

# ══════════════════════════════════════════════════════════════
# 무인 계측기 (판 ⑪ ①) — 엔진 `runlog.Run.decide/intervene` 와 **같은 어휘**
#   하네스는 `Run` 을 쓰지 않는다(엔진 밖이다). 그래서 같은 모양의 기록을 여기서 따로
#   모아 `gate.json` 에 싣는다. **어휘가 갈리면 두 산출물을 나란히 못 읽는다** —
#   「이 컨셉은 개입 0으로 돌았다」는 두 기록을 합쳐야만 말할 수 있는 문장이다.
# ══════════════════════════════════════════════════════════════
_기록 = {"개입": [], "결정": []}
_사전등록: dict | None = None


def _stamp_prereg():
    """**유료 진입점 ②** (판 ⑪ ②) — 하네스 LLM 호출 직전. 첫 호출에서만 잰다.

    엔진(`runlog.Meter.create`)과 **같은 함수**를 쓴다. 두 진입점이 각자 계산하면
    「하네스는 사전등록됐다는데 엔진은 아니라는」 산출물이 나온다.
    """
    global _사전등록
    if _사전등록 is None:
        import time as _t
        sys.path.insert(0, ROOT)
        from runlog import prereg_stamp
        _사전등록 = {**prereg_stamp(_t.time()), "진입점": "slot_harness.call_llm"}
    return _사전등록


def _decide(what, choice, *, rule, why=""):
    """스스로 내린 결정. `rule` 이 비면 「(없음 — 코드 판단)」으로 **드러나게** 남긴다."""
    _기록["결정"].append({"무엇": what, "고른_것": choice,
                        "근거_규칙": rule or "(없음 — 코드 판단)", "왜": why,
                        "at": datetime.datetime.now().isoformat(timespec="seconds")})


def _intervene(kind, detail="", *, blocking=True):
    """사람을 불러야 했던 사건. ⚠ **fail-open 은 여기가 아니라 `_decide` 다** —
    사람을 **안 부르고** 진행한 것이므로."""
    _기록["개입"].append({"종류": kind, "상세": str(detail)[:300], "멈췄나": blocking,
                       "at": datetime.datetime.now().isoformat(timespec="seconds")})


def _무인_기록() -> dict:
    """**빈 리스트여도 반드시 싣는다.** 칸이 없으면 「개입 0」이 아니라 「미측정」이다."""
    d = _기록
    return {"_규칙": ("개입 = 사람을 불러야 했던 사건. fail-open 은 개입이 아니라 결정이다"
                    "(사람을 안 부르고 진행). 둘을 섞지 않는다."),
            "개입_횟수": len(d["개입"]),
            "개입_멈춤": sum(1 for x in d["개입"] if x.get("멈췄나")),
            "개입": list(d["개입"]),
            "결정_횟수": len(d["결정"]),
            "결정_규칙없음": sum(1 for x in d["결정"]
                            if str(x.get("근거_규칙", "")).startswith("(없음")),
            "결정": list(d["결정"]),
            # 유료 호출이 0회면 `None` — 「사전등록 안 함」이 아니라 「잴 일이 없었다」.
            # replay 는 LLM 0회이므로 여기가 None 인 것이 정상이다.
            "사전등록": _사전등록}


def targets(vocab: dict, concept: dict | None = None) -> list[tuple]:
    """만들 식의 목록. **정본은 `vocab.식_목록` 이다**(백로그 49 · 규약 ①).

    계열이 TAM 을 세우는 **구조**가 다르면 `template` 칸만 갈아끼운다. 계열 C 가
    그렇다 — 시장 거래액 × 점유율이라 `사업체수` 자리를 요구하는 T2 로는 표현이
    **불가능**하다. 판 ⑧ 은 그 자리에서 죽었다(게이트 `템플릿 필수 자리`, 재시도 3/3 소진).
    막은 것은 자료 부재가 아니라 **담을 틀의 부재**였다.

    override 가 없는 계열은 기본값을 그대로 쓴다 — 모르는 계열에 대해 **조용히 다른 것을
    고르지 않는다.** 바꾸는 것은 `template` 하나뿐이고 target·path 는 손대지 않는다.
    """
    spec = vocab.get("식_목록") or {}
    base = [tuple(row) for row in (spec.get("기본") or [])]
    series = ((concept or {}).get("_계열") or {}).get("계열") or ""
    over = (spec.get("계열_템플릿") or {}).get(series) or {}
    if not over:
        return base
    # ── 분기형 override (판 ㉕ · 백로그 71) ────────────────────────────
    # 계열 E 는 고객 단위가 「개인·거래」 **둘 다 허용**이라 계열만으로는 못 가른다.
    # 컨셉이 「구조는 C 를 따른다」고 **스스로 적어** 두었으므로 그 선언을 읽는다 —
    # **코드가 조용히 추측하지 않는다.** 못 가르면 기본값을 두고 「미선언」을 남긴다.
    분기_근거 = ""
    _br = over.get("_분기")
    if _br:
        _why = str(((concept or {}).get("_계열") or {}).get("왜") or "")
        _hit = next((k for k in (_br.get("map") or {})
                     if f"구조는 {k}" in _why or f"{k}(" in _why), None)
        if _hit:
            over = {"F_TAM": (_br["map"] or {})[_hit], "F_SAM": (_br["map"] or {})[_hit]}
            분기_근거 = f"컨셉 선언 「구조는 {_hit}」 → {over['F_TAM']}"
        else:
            over, 분기_근거 = {}, "분기 미선언 — 기본 템플릿 유지(추측하지 않는다)"
        if not over:
            return [(f, t, p, tm, w + f" · 계열 {series} {분기_근거}") for f, t, p, tm, w in base]
    out = []
    for fid, t, path, tmpl, why in base:
        new_tmpl = over.get(fid)
        if new_tmpl and 분기_근거:
            why = why + f" · {분기_근거}"
        out.append((fid, t, path, new_tmpl or tmpl,
                    why + (f" · 계열 {series} 템플릿 {tmpl}→{new_tmpl}" if new_tmpl else "")))
    return out

PROMPT = """너는 시장조사 슬롯 설계자다. **정해진 빈칸을 채우기만 한다. 자유 서술 금지.**

[컨셉]
{concept}

[업종 분류]
{industry}

[만들 식 목록 — 이 목록 그대로, 늘리거나 줄이지 마라]
{targets}

[통제 어휘 — 이 밖의 값을 쓰면 탈락한다]
metric 표 (단위는 계량마다 **고정**이다. 다른 단위를 쓰면 탈락):
{metrics}

var_role 표 (식 안에서 그 변수가 맡은 자리. 자리와 계량 종류가 맞아야 한다):
{roles}

**TAM·SAM 의 subject — 정본 표기에서 고른다** (통계표가 실제로 쓰는 이름):
{subject_canon}

템플릿이 요구하는 자리: {tmpl_req}
**정확 일치를 요구하는 템플릿** (이 자리만·전부): {tmpl_exact}
- claim_type: {claim_types}
- canvas_cell: {cells}

[규칙]
1. metric 은 위 목록에서 **그대로** 고른다. 한정어(업종·지역·조건)는 metric 이 아니라
   subject 에 넣는다. "미용실 사업체 수" 처럼 쓰면 라우팅이 깨진다 — subject="두발 미용업",
   metric="사업체 수" 로 쪼개라.
2. subject 에 지역명을 넣지 마라. 지역은 region 칸이 따로 있다.
3. stat_code 는 **모르면 null**. 지어내지 마라 — 틀린 코드는 조용히 빈손이 된다.
   ⚠ **`stat_code` 는 업종 분류 코드가 아니다.** 형식은 `orgId/tblId`(예: `101/DT_1K52F03`)
   즉 **KOSIS 통계표 번호**다. 위 [업종 분류]의 KSIC 코드(예: `91132`)를 여기에 옮겨 적지 마라 —
   형식이 달라 그 자리에서 탈락한다. 업종 코드는 `subject_code` 칸에 적는다.
4. corp_name 은 dart 경로를 쓸 때만, 한국 법인 실명으로 적는다.
5. period 는 연도 하나. 기준연도는 {as_of} 다. **사업체 수·종사자 수·매장 수·인구는
   {lagged_year}**(국가통계는 늦게 나온다), **나머지는 전부 {fresh_year}**. 다른 해를 쓰면 탈락.
5-1. ⚠ **`F_GROWTH` — 「성장률」이라는 계량을 쓰지 마라. 그것은 계산값이다.**
   대신 **F_TAM 이 쓰는 것과 같은 LEVEL 계량**(거래액·사업체 수 …)을 **연도만 다르게 두 개**
   만든다: {lagged_year} 와 {prev_year}. 두 변수는 **metric·subject·unit 이 같고 period 만
   다르다.** claim_type 은 **`GROWTH`**, canvas_cell 은 **「고객 세그먼트」**.
   **증감률 계산은 판정 층이 한다** — 우리가 관측할 것은 **두 해의 값**이지 남이 계산한 비율이 아니다.
6. value_range 는 그 값이 가질 수 있는 [최소, 최대] 다. 자릿수를 틀리게 하는 값을
   걸러내는 용도이지 정답을 좁히는 용도가 아니다. 넓게 잡아라.
   ⚠ **상한은 하한보다 반드시 커야 한다.** `[0, 0]` 처럼 같게 적으면 그 슬롯의 **모든 값이
   걸러져 통째로 빈손**이 된다. 무료 서비스는 `[0, 0]` 이 아니라 `[0, 소액 상한]` 으로 적는다.
   ⚠ **계량 표에 「전형 크기」가 적힌 계량은 그 구간과 반드시 겹치게 잡아라.** 겹치지 않으면
   맞는 값이 통째로 격리된다 — 실측: 거래액을 `[1e9, 1e10]` 으로 적어 참값 38.0조(3.8e13)가
   버려졌고 성적표 4과목이 그 하나의 하류였다. 세그먼트가 작아 보여도 **상한을 전형 크기까지
   열어라**(좁히는 것은 이 칸의 일이 아니다). 겹치지 않으면 코드가 전형 크기로 갈아끼운다.
7. must_contain 은 그 문서가 반드시 담고 있어야 할 낱말이다. **`any()` 로 평가된다** —
   그래서 낱말을 늘리면 조여지는 게 아니라 **느슨해진다.** 반대로 알기 쉬우니 주의하라.
   ⚠ **낱말은 하나만 적고, 그 낱말은 반드시 그 슬롯의 `subject` 안에 있는 말이어야 한다.**
   「문제」·「성장」처럼 아무 문서에나 있는 말을 적으면 **종류가 다른 값이 문턱을 넘는다** —
   실측: subject="1인 가구" · must_contain=["문제"] 인 수요 슬롯을 「70대 이상 1인 가구
   우울증상유병률 8.9%」가 채웠다. 인구만 맞고 문제의 종류가 다르다.
   예) subject="편의점 도시락" → ["도시락"] / subject="1인 가구 혼자 식사" → ["혼자"]
   가를 것이 없으면 **빈 배열로 둔다.** 억지로 채우지 마라.
   must_not_contain 은 확실히 다른 주제로 새는 낱말이며, **이 컨셉과 무관한 말을 적지 마라** —
   실측: HMR 컨셉 슬롯에 ["반려동물"] 이 남아 있어 찾아낸 값이 통째로 격리됐다.
7-0. **PAIN·PRICE 는 표적을 하나만 두지 마라.** 같은 값이 실리는 문서 종류(서식지)가
   표적마다 다르다 — 하나에 걸면 그 서식지를 검색이 못 물어온 판은 **칸이 통째로 빈다.**
   이 두 claim_type 은 **subject 가 서로 다른 변수를 3개 이상** 만들어라.
   ⚠ **분산은 subject 로 한다.** 검색어는 subject·metric·period·region 으로 만들어지므로,
   `must_contain` 만 다르고 subject 가 같으면 **같은 검색어를 두 번 던지는 것**이다 —
   분산이 아니라 중복이고, 실측에서 슬롯만 늘고 칸은 그대로 비었다.
   ⚠ **회사를 지목하지 마라.** 그 값은 발행되지 않는다(실측: 「프레시지 월 구독료」 0건).
   **통계·보도자료에 실제로 비율·금액으로 실리는 대상**을 골라라 — 대체재 가격(배달비·
   편의점 도시락가·외식비)과 타깃의 행동률(혼자 식사 비율·결식률)이 그런 자리다.
7-1. subject_aliases 는 **그 subject 를 가리키는 다른 표기** 0~4개다. 같은 대상을 부르는
   이름이 문서마다 다르기 때문에 둔다 — 회사는 공시에서 법인명(「NAVER」·「네이버주식회사」),
   보도에서 서비스명으로 불리고, 업종은 통계표 항목명과 일상 표기가 다르다.
   **같은 대상의 다른 이름만 적는다.** 상위 개념·경쟁사·유사 업종을 적지 마라 —
   그건 다리가 아니라 다른 대상이고, 넣으면 엉뚱한 문서가 통과한다.
   모르면 빈 배열로 둔다. 지어내지 마라.
8. canvas_cell 은 이 관측이 채울 BM 캔버스 칸이고, **claim_type 과 짝이 정해져 있다**:
   고객 세그먼트=TAM·SAM / 가치 제안=PAIN·COMP·COMPARABLE / 채널=CHANNEL /
   수익원=PRICE·ALT. 짝이 어긋나면 탈락한다.
9. observable=false 는 "공개 자료로 관측할 수 없고 가정으로 둘 변수"다(침투율·연환산 등).
   그 변수는 슬롯이 되지 않으므로 **metric 을 표 밖의 말로 적어도 된다** — 연환산은
   metric="연 결제 개월", unit="개월", observable=false 로 적는다. 규칙 1의 metric 표는
   **관측하는 변수(observable=true)에만** 적용된다.
10. **claim_type 은 식의 target 이 아니다.** 위 claim_type 목록에서만 고른다
    (예: F_GROWTH 의 target 은 TAM_GROWTH 지만 그 변수의 claim_type 은 TAM 이다).
11. subject 는 **무엇을 세는지**다. 통계 변수면 업종명("두발 미용업"), 경쟁·대체재·채널
    관측이면 **그 서비스·업종 카테고리 이름**을 쓴다. 전부 "두발 미용업" 으로 적으면
    경쟁사를 묻는 슬롯이 업종 통계를 물어온다 — 탈락 사유다.
12. 매출액·영업이익은 **DART 공시 법인(상장·공시 대상)** 에만 쓸 수 있고 corp_name 이
    반드시 있어야 한다. 비상장 경쟁사는 공시가 없다 — corp_name 없이 「가입 매장 수」·
    「누적 가입자 수」 같은 web 계량으로 관측하라.
13. 가격 계량(월 구독료·이용 요금)은 **canvas_cell="수익원" · claim_type="PRICE"** 다.
    식의 단가 변수여도 그렇다.
14. T2 식에는 **연환산**(연 결제 개월, observable=false) 자리가 반드시 있어야 한다.
    없으면 월 매출을 연 매출로 읽게 된다.
14-1. ⚠ **템플릿은 「고르는 것」이지 「더하는 것」이 아니다.** 위 [만들 식 목록]이 각 식의
    template 을 지정했다. 그 템플릿의 자리만 쓰고 **다른 템플릿의 자리를 얹지 마라.**
    특히 template=T7(시장거래액 × 추정점유율)인 식에 T2 의 자리(사업체수·세그먼트비중·
    침투율·단가·연환산)를 **함께** 넣으면 탈락한다 — 계산이 그 변수들을 **전부 곱해서**
    무의미한 수가 되기 때문이다. 자리 목록은 아래 «템플릿이 요구하는 자리»가 정본이다.
    T7 이면 변수는 **정확히 둘**이다.

15. **경쟁사 이름을 지어내지 마라.** F_COMP 의 subject 는 아래 [경쟁 씨앗]에 있는 이름을
    **그대로** 쓴다. 씨앗에 없는 회사를 넣지 말고, 「A사」·「A미용 예약 SaaS」·「○○」 같은
    자리표시자도 쓰지 마라 — 검색이 아무것도 못 찾고 그 사실이 자료 부재로 오독된다.
    corp_name 은 씨앗 줄에 «공시법인» 이라 적힌 것에만 쓴다. 나머지는 반드시 null 이다 —
    비상장사는 공시가 없어서 조회가 통째로 빈손이 된다.
16. 식별로 쓸 수 있는 계량이 못박힌 것이 있다: {fixed_metrics}
    기능 유무(노쇼 차단 방식·시술 중 응대·취소 후 회수)는 수치가 아니라 슬롯이 되지 않는다.
17. **var_role 자리마다 쓸 수 있는 계량이 1:1로 정해져 있다** — 위 var_role 표를 보라.
    특히 침투율 자리는 「도입률」만, 세그먼트 필터(1인 사업체 비중 등)는 **세그먼트비중**
    자리에 넣는다. 남의 자리에 밀어넣으면 탈락한다.
18. **corp_name 은 매출액·영업이익에만 붙인다.** web 계량(월 구독료·가입 매장 수 등)에
    corp_name 을 달면 dart 로 라우팅돼 공시에 없는 계정을 찾다가 빈손이 된다.
19. {rule19}
20. value_range 는 **상한이 하한보다 커야 한다.** 무료 서비스는 [0, 0] 이 아니라
    [0, 소액 상한] 으로 적는다 — [0,0] 은 모든 값을 격리한다.
21. **`추출_힌트`** — claim_type 이 **PAIN 인 변수에만** {hint_min}개 이상 적는다(나머지는 []).
    그 문제가 **이 업종의 자료에서 실제로 불리는 말**을 적어라. 계량 이름(「문제 경험률」)은
    업종 중립이라 그대로 두고, 업종 표현은 여기에 담는다.
    ⚠ **최소 {hint_ground}개는 위 [컨셉] 본문에 그대로 나오는 말**이어야 한다 — 컨셉에 없는
    업종 지식을 지어내면 탈락한다. 나머지는 그 말의 동의어·자료에서 쓰일 표현으로 적어도 된다.
    예) 컨셉이 노쇼를 말하면 ["노쇼", "예약 부도", "피해"] / 회원 만료 이탈을 말하면
    ["만료", "재등록", "이탈률"].

22. **`proxy_선언`** — 이 컨셉의 계열과 고객 단위는 **{series_line}** 이다.
    TAM·SAM 슬롯이 **그 고객 단위가 아닌 것**을 세면 `proxy_선언` 을 채워야 한다:
    `대상`(무엇으로 대신했는가) + `사유`(왜 그것이 대신할 수 있는가, **10자 이상**).
    - 고객 단위와 **맞는** 것을 세면 둘 다 빈 문자열로 둔다.
    - 선언 없이 다른 것을 세면 **탈락한다** — 그것이 「조용한 오염」이다(고객이 개인인데
      공급자 사업체 수를 세는 따위).
    - **선언은 면죄부가 아니라 표시다.** 적으면 그 값에 경계 문장이 따라붙는다(코드가 붙인다).
    - 예) 신시장이라 정확한 시장이 없을 때: 대상="유사 시장(미용기기 렌탈)",
      사유="네일 로봇 렌탈 시장의 공개 통계가 없어 인접 렌탈 시장으로 규모를 대신 잰다"

[경쟁 씨앗 — 사람이 준 이름. 진실이 아니라 출발점이다]
{seeds}

[분량 — 지키지 않으면 탈락한다]
- formulas 는 위 목록의 **8개 전부**. 하나라도 빠뜨리지 마라.
- F_TAM·F_SAM 은 변수 5개(사업체수·세그먼트비중·침투율·단가·연환산).
- **F_PAIN·F_PRICE 는 3~5개** — 규칙 7-0 의 서식지 분산이 여기서 나온다.
  같은 var_role 을 여러 변수가 써도 된다. **subject 가 서로 다르면 다른 변수다.**
- 나머지 식(F_GROWTH·F_COMP·F_DIFF·F_CHANNEL)은 1~3개.
- F_COMP 는 **씨앗 3개를 각각 변수 하나로** + **매출액 변수 1개**(규칙 19).
- 캔버스 칸 4개(고객 세그먼트·가치 제안·채널·수익원)가 **전부** 최소 1개 슬롯을 갖도록
  observable=true 변수를 배치하라. 한 칸이라도 비면 탈락한다.

[출력 — 이 JSON 하나만. 설명 문장·코드펜스 금지]
**아래는 형식 예시이고 F_TAM 의 변수 하나만 보인 것이다. 실제 출력은 식 8개를 전부 채운다.**
{{"formulas": [
  {{"formula_id": "F_TAM", "vars": [
     {{"var_role": "사업체수", "subject": "...", "metric": "...", "period": "2024",
      "unit": "개", "region": "대한민국", "subject_code": null, "stat_code": null,
      "corp_name": null, "claim_type": "TAM", "canvas_cell": "고객 세그먼트",
      "observable": true, "must_contain": ["..."], "must_not_contain": ["..."],
      "subject_aliases": [],
      "value_range": [1000, 500000], "추출_힌트": [],
      "proxy_선언": {{"대상": "", "사유": ""}}}}
  ]}}
]}}
"""


def _load(p):
    return json.load(io.open(p, encoding="utf-8"))


def _env_key(name: str):
    """adapters/base.py 와 같은 탐색 순서. 엔진을 import 하지 않으려고 옮겨 적었다.

    ⚠ **베낀 값은 갈라진다 — 실제로 갈라졌다.** 판 ㉝ 이식(`시장조사/research2` →
      `ai/app/research/research2`)으로 저장소 루트가 두 단계 멀어졌을 때 `base.py` 는
      깊이를 6으로 고쳤지만 **이 사본은 4로 남았다.** 그래서 컨테이너 밖에서 하네스만
      「OPENAI_API_KEY 없음」으로 죽었다 — preflight 는 `base.py` 를 써서 「ok」라 했고,
      두 답이 정반대였다. 2026-08-11 수집 배선 실측에서 잡혔다.
      **깊이를 고칠 일이 생기면 `adapters/base.py` 와 여기를 같이 고친다.**
    """
    if os.environ.get(name):
        return os.environ[name]
    for rel in (".env", "../.env", "../../.env", "../../../.env",
                "../../../../.env", "../../../../../.env"):
        p = os.path.normpath(os.path.join(ROOT, rel))
        if os.path.exists(p):
            for line in io.open(p, encoding="utf-8"):
                if line.startswith(name + "="):
                    return line.split("=", 1)[1].strip() or None
    return None


def _seed_lines(concept: dict, corpcode: dict | None) -> str:
    """씨앗을 프롬프트 줄로. **corp_name 허용 여부는 코드가 사전 대조로 정한다** —
    모델이 «이 회사는 상장사겠지» 를 판단하면 그 순간 추측이 근거로 들어온다."""
    seeds = (concept.get("_경쟁_씨앗") or {}).get("seeds") or []
    if not seeds:
        return "  (없음 — F_COMP 의 subject 는 업종 카테고리로 두고 실명은 적지 마라)"
    out = []
    for s in seeds:
        op = s.get("운영사")
        listed = bool(op and (corpcode or {}).get(op))
        tail = (f"corp_name=\"{op}\" 를 써도 된다 «공시법인»" if listed
                else "corp_name 은 null «공시 없음 — web 계량으로만»")
        out.append(f"  {s['이름']} — {s['왜']} / {tail}")
    return "\n".join(out)


def _subject_canon() -> str:
    """정본 subject 표기 목록. **`adapters.kosis.resolve.subject_별칭` 이 단일 원천이다.**

    이 표의 **키와 값**이 곧 「통계표가 실제로 쓰는 이름 ↔ 우리가 부르는 이름」이다.
    하네스가 그 밖의 표기를 지으면 수집이 못 찾는다 — **같은 해석을 두 번 하지 않는다**(판 ⑯).
    """
    al = ((_load(os.path.join(ROOT, "rules", "adapters.v1.json"))
           .get("kosis") or {}).get("resolve") or {}).get("subject_별칭") or {}
    m = {k: v for k, v in (al.get("map") or {}).items() if not k.startswith("_")}
    if not m:
        return "  (등재된 정본 표기 없음 — 업종명을 통계표 표기 그대로 쓸 것)"
    lines = [f"  「{k}」 → 통계표 표기 {v}" for k, v in m.items()]
    lines.append("  ⚠ 위 목록에 있는 대상이면 **왼쪽 표기를 그대로** 쓴다(변형 금지 — "
                 "「…시장」을 붙이거나 「반려동물」을 「반려견」으로 바꾸면 수집이 못 찾는다). "
                 "목록에 없으면 통계표가 쓰는 이름을 그대로 쓴다.")
    return "\n".join(lines)


def _rule19(concept: dict) -> str:
    """F_COMP 의 corp_name 요구는 **씨앗이 있을 때만**이다(백로그 39 수리).

    씨앗이 없으면 정당한 corp_name 이 **존재할 수 없다.** 그래도 요구하면 모델은
    없는 회사를 지어내고(실측: «공시법인»·퓨처센터·ACompany) 공시 대조에서 전부 죽는다 —
    **채울 수 없는 칸을 강제한 것**이지 모델이 나빴던 게 아니다.
    """
    if (concept.get("_경쟁_씨앗") or {}).get("seeds"):
        return ("F_COMP 에 **「매출액」 + corp_name(«공시법인» 씨앗) 변수 하나를 반드시** 넣는다."
                "\n    DART 경로를 한 번 태우기 위한 것이다. (경계 표시는 코드가 붙인다)")
    # ⚠ **옛 문구는 덫이었다** (판 ⑫ ② 실측 3/3).
    #   규칙 12 는 「매출액에는 corp_name 필수」라 하고 여기는 「corp_name 넣지 마라」라 했다.
    #   모델이 `매출액` 을 고르는 순간 **두 지시를 동시에 지킬 방법이 없다** — 실제로
    #   `S16 매출액 · corp_name 없음` 이 3/3 시도에서 나왔다. 모델이 어긴 게 아니라
    #   **우리가 빠져나갈 길 없는 지시를 줬다.** 계량 자체를 금지해야 모순이 사라진다.
    return ("**경쟁 씨앗이 없다.** F_COMP 에 corp_name 을 **넣지 마라** — 실명을 모르는 상태이고"
            "\n    지어내면 탈락한다."
            "\n    ⚠ 그러므로 **「매출액」·「영업이익」을 아예 쓰지 마라.** 그 둘은 공시 계량이라"
            "\n    corp_name 이 반드시 필요한데(규칙 12), 지금은 그것을 채울 수 없다 —"
            "\n    고르는 순간 어느 쪽이든 탈락한다."
            "\n    subject 는 **업종·서비스 카테고리 이름**으로 두고,"
            "\n    계량은 「가입 매장 수」·「누적 가입자 수」 같은 web 계량을 쓴다."
            "\n    경쟁사 실명은 **수집 결과에서 나오면 잡는다** — 여기서 만들지 않는다.")


def _series_line(concept: dict) -> str:
    """계열과 고객 단위를 프롬프트 한 줄로. **모델이 계열을 추측하지 않게** 값으로 준다."""
    import json as _j
    rule = _load(os.path.join(ROOT, "rules", "series_unit.v1.json"))
    s = ((concept.get("_계열") or {}).get("계열")) or ""
    spec = (rule.get("계열_고객_단위") or {}).get(s)
    if not spec:
        return "계열 미표기 — 고객 단위 제약 없음(그래도 고객이 아닌 것을 세면 선언하라)"
    allow = spec.get("허용") or []
    if not allow:
        return (f"계열 {s} (신시장) — **무엇을 세든 proxy_선언이 필요하다.** "
                "비교 가능한 기존 시장이 없는 계열이기 때문이다")
    return f"계열 {s} · 고객 단위 = {_j.dumps(allow, ensure_ascii=False)}"


def build_prompt(concept: dict, vocab: dict, as_of_year: int, violations: list | None = None,
                 corpcode: dict | None = None, guards: dict | None = None) -> str:
    # 규칙 6 — research_view 와 같은 필드만 넘긴다. 가설(_hypotheses_v2)·제약은 넣지 않는다.
    view = {k: concept[k] for k in ("name", "problem", "target", "solution", "region")}
    ind = (concept.get("_다듬기5") or {}).get("4_업종_분류") or {}
    cat = vocab["metric"]["catalog"]
    # 계량마다 **전형 크기**를 옆에 박는다. 검사(`check_range_band`)와 교정(`repair_design`)은
    # 이 표를 보는데 프롬프트는 「넓게 잡아라」만 말하고 있었다 — **검사하는 것과 지시하는
    # 것이 달랐다.** 실측: 자동 설계가 거래액 밴드를 [1e9, 1e10] 으로 적었고 참값은 38.0조라
    # 4칸이 전부 어긋났다. 모델은 어길 수 없는 것을 어긴 게 아니라 **모르는 것을 못 맞췄다**
    # (백로그 59 계보 — 판 ⑩ 의 허용 계량 목록과 같은 처방이다).
    if guards is None:
        guards = _load(os.path.join(ROOT, "rules", "guards.v1.json"))
    _bands = (guards.get("value_range") or {}).get("계량_전형_밴드") or {}
    body = PROMPT.format(
        concept=json.dumps(view, ensure_ascii=False, indent=1),
        industry=json.dumps({k: v for k, v in ind.items() if not k.startswith("_")},
                            ensure_ascii=False),
        # **식마다 「그 식이 채워야 할 자리」를 옆에 박는다** (판 ㉒ ①).
        # 자리 목록을 **별도 표**로만 주면 모델이 식과 자리를 못 잇는다 — 판 ⑳ 실측:
        # B·C·E 의 최소 미통과가 **전부** 「템플릿 필수 자리」였고 빠진 자리가 4~5개였다.
        # 게이트가 요구하는 것을 **지시가 같은 줄에서** 말해야 한다(백로그 59 계보).
        targets="\n".join(
            f"  {fid} · target={t} · path={p} · template={tp}"
            f" · **필수 자리: "
            f"{' · '.join((vocab['template']['required_roles'].get(tp) or [])) or '(없음)'}**"
            + (" · **이 자리만 쓴다(초과 금지)**"
               if tp in ((vocab["template"].get("허용_자리") or {}).get("map") or {}) else "")
            + f" · {why}"
            for fid, t, p, tp, why in targets(vocab, concept)),
        metrics="\n".join(
            f"  {k} — 경로 {v['route']} · 종류 {v['kind']} · 단위 {v['unit']}"
            + (f" · **전형 크기 [{(_bands[k]['밴드'])[0]:g}, {(_bands[k]['밴드'])[1]:g}]**"
               if (_bands.get(k) or {}).get("밴드") else "")
            for k, v in cat.items()),
        # 자리마다 **허용 계량 목록**을 같이 적는다. 게이트(`check_role_kind`)는 이 목록을
        # 강제하는데 프롬프트는 종류(kind)만 알려 주고 있었다 — **검사하는 것과 지시하는 것이
        # 달랐다.** 판 ⑩ 실측: 계열 C 초안이 「시장거래액」 자리에 `시장 규모`(금액이라 kind 는
        # 맞다)를 넣어 3/3 시도가 같은 자리에서 죽었다. 모델은 어길 수 없는 것을 어긴 게 아니라
        # **모르는 것을 못 맞춘 것이다.** 완화가 아니라 정합이다.
        roles="\n".join(
            f"  {k} — 종류 {v['kind']}"
            + (f" · 허용 계량: {', '.join(v['metrics'])}" if v.get("metrics")
               else " · 허용 계량 지정 없음(종류만 맞으면 된다)")
            for k, v in vocab["var_role"]["catalog"].items()),
        # **정본 표기는 별칭 표에서 온다 — 단일 원천**(판 ㉓ ①).
        # 하네스가 매번 다른 표기를 지으면(「반려동물 간식 **시장**」·「반려**견** 수제 간식」)
        # 별칭 정확 일치가 안 맞고, 그러면 **수집이 통째로 빈손**이 된다(판 ㉒ 36회 실측).
        # 손 별칭을 늘려 쫓는 것은 임시 처방이라 금지 — **표기를 고르게** 한다.
        # ⚠ 목록 밖 표기를 아예 막지는 않는다(모르는 업종이 죽는다) — **강한 권고**로 둔다.
        subject_canon=_subject_canon(),
        tmpl_req=json.dumps(vocab["template"]["required_roles"], ensure_ascii=False),
        # 게이트가 정확 일치를 요구하는 템플릿을 **프롬프트에도 그대로** 보여 준다.
        # 검사하는 것과 지시하는 것이 갈리면 모델은 «모르는 것을 못 맞춘» 상태가 된다
        # (백로그 59 — 판 ⑩ 에서 같은 병으로 3/3 시도가 죽었다).
        tmpl_exact=json.dumps((vocab["template"].get("허용_자리") or {}).get("map") or {},
                              ensure_ascii=False),
        fixed_metrics=", ".join(
            f"{k}={v}" for k, v in vocab["metric"]["_식별_계량"].items()
            if not k.startswith("_")),
        claim_types=", ".join(vocab["claim_type"]["enum"]),
        cells=", ".join(list(vocab["canvas"]["측정판정"]["cells"])),
        seeds=_seed_lines(concept, corpcode),
        rule19=_rule19(concept),
        as_of=as_of_year, lagged_year=as_of_year - 2, fresh_year=as_of_year - 1,
        prev_year=as_of_year - 3,
        hint_min=(vocab.get("요구", {}).get("추출_힌트", {}) or {}).get("최소_개수", 2),
        hint_ground=(vocab.get("요구", {}).get("추출_힌트", {}) or {}).get("컨셉_유래_최소", 1),
        series_line=_series_line(concept))
    if violations:
        body += ("\n\n[직전 시도가 기계 검증에서 걸린 항목 — 같은 실수를 되풀이하지 마라]\n"
                 + json.dumps(violations, ensure_ascii=False, indent=1))
    return body


def output_schema(vocab: dict, concept: dict | None = None) -> dict:
    """통제 어휘를 **스키마의 enum 으로** 내린다.

    산문 규칙만으로는 안 잡혔다 — 시도마다 다른 칸 하나씩이 흘렀다(metric 자리에
    var_role, 단위 혼재, 칸 오배치). 「고르기만 한다」를 말로 부탁하지 말고
    **구조로 강제한다.** 게이트는 그래도 그대로 둔다: 스키마는 형식을, 게이트는
    관계(자리·칸·조인·누출)를 본다.
    """
    cat = vocab["metric"]["catalog"]
    assume = {k: v for k, v in vocab["metric"]["_가정_계량"].items() if not k.startswith("_")}
    # 씨앗이 없으면 공시 계량을 **enum 에서 뺀다** — 표현 자체를 불가능하게 만든다.
    # 산문으로 네 번 말해도 안 지켜진 자리다(판 ⑫ ②′). 목록·on/off 는 규칙 파일에.
    ex = vocab["metric"].get("씨앗_없으면_제외") or {}
    if ex.get("enabled") and concept is not None             and not ((concept.get("_경쟁_씨앗") or {}).get("seeds")):
        cat = {k: v for k, v in cat.items() if k not in set(ex.get("metrics") or [])}
    var = {
        "type": "object", "additionalProperties": False,
        "required": ["var_role", "subject", "metric", "period", "unit", "region",
                     "subject_code", "stat_code", "corp_name", "claim_type",
                     "canvas_cell", "observable", "must_contain", "must_not_contain",
                     "subject_aliases",
                     "value_range", "추출_힌트", "proxy_선언"],
        "properties": {
            "var_role": {"type": "string", "enum": list(vocab["var_role"]["catalog"])},
            "subject": {"type": "string"},
            "metric": {"type": "string", "enum": list(cat) + list(assume)},
            "period": {"type": "string"},
            "unit": {"type": "string", "enum": vocab["unit"]["enum"]},
            "region": {"type": "string"},
            "subject_code": {"type": ["string", "null"]},
            "stat_code": {"type": ["string", "null"]},
            "corp_name": {"type": ["string", "null"]},
            "claim_type": {"type": "string", "enum": vocab["claim_type"]["enum"]},
            "canvas_cell": {"type": "string",
                            "enum": list(vocab["canvas"]["측정판정"]["cells"])},
            "observable": {"type": "boolean"},
            "must_contain": {"type": "array", "items": {"type": "string"}},
            # 표기 변종(판 ㉛). 상한은 게이트가 `vocab.subject_aliases` 로 잰다.
            "subject_aliases": {"type": "array", "items": {"type": "string"}},
            "must_not_contain": {"type": "array", "items": {"type": "string"}},
            "value_range": {"type": "array", "items": {"type": "number"}},
            # P2 배선(판 ⑥-0): 업종 표현은 통제 어휘가 아니라 **컨셉**에서 온다.
            # PAIN 에만 요구한다 — 필요 없는 곳까지 요구하면 채울 수 없는 칸이 된다(판 ⑤ P3).
            "추출_힌트": {"type": "array", "items": {"type": "string"}},
            # 고객 단위 정합(⑥-1·⑥-2): 계열 고객 단위가 **아닌 것**을 셀 때의 정당한 탈출구.
            # 「대상」= 무엇으로 대신했는가 · 「사유」= 왜 그것이 대신할 수 있는가.
            # **경계 표시는 코드가 붙인다** — 모델이 적기를 기다리면 빠지는 판이 생긴다.
            # 둘 다 빈 문자열 = 선언 없음. 선언이 없고 불일치면 게이트에서 탈락한다.
            "proxy_선언": {
                "type": "object", "additionalProperties": False,
                "required": ["대상", "사유"],
                "properties": {"대상": {"type": "string"}, "사유": {"type": "string"}}},
        },
    }
    return {
        "type": "object", "additionalProperties": False, "required": ["formulas"],
        "properties": {"formulas": {
            "type": "array",
            "items": {"type": "object", "additionalProperties": False,
                      "required": ["formula_id", "vars"],
                      "properties": {
                          "formula_id": {"type": "string",
                                         "enum": [t[0] for t in targets(vocab)]},
                          "vars": {"type": "array", "items": var}}}}},
    }


def call_llm(body: str, model: str, schema: dict | None = None) -> dict:
    from openai import OpenAI
    _stamp_prereg()                 # 돈이 나가는 순간을 기준으로 잰다
    # JSON 모드 — 파손된 JSON 은 재시도 3회를 통째로 태운다(실측: 따옴표 하나 빠져 시도 1 소실).
    # 형식은 API 가 보장하고, **내용은 게이트가 본다.** 둘을 섞지 않는다.
    fmt = ({"type": "json_schema", "name": "slot_draft", "strict": True, "schema": schema}
           if schema else {"type": "json_object"})
    r = OpenAI().responses.create(model=model, input=body, text={"format": fmt})
    txt = r.output_text or ""
    i, j = txt.find("{"), txt.rfind("}")
    if i < 0 or j < 0:
        # **사람이 와야 끝나는 자리다.** 계측하지 않으면 이 멈춤이 「개입 0」 속에 숨는다.
        _intervene("멈춤 — LLM 출력에 JSON 없음", txt[:200], blocking=True)
        # ⚠ 예전에는 `SystemExit` 였다. `BaseException` 이라 아래 재시도 루프의
        #   `except Exception` 이 **못 잡고** 프로세스가 그대로 죽었다 — 서버에서 부르면
        #   워커가 통째로 넘어간다. `HarnessError` 로 두면 루프가 잡아 무인 기록을 남긴다.
        raise HarnessError("LLM 출력에 JSON 이 없다 — 게이트 이전 단계에서 멈춘다:\n" + txt[:400])
    usage = {}
    try:
        usage = {"tokens_in": r.usage.input_tokens, "tokens_out": r.usage.output_tokens}
    except Exception:
        pass
    body, repaired = txt[i:j + 1], ""
    try:
        data = json.loads(body)
    except json.JSONDecodeError as e:
        # 후행 쉼표는 결정론적으로 고친다. 그 외 파손은 **값으로** 넘긴다 —
        # 빈 data 는 게이트에서 커버리지 미충족으로 떨어지고 재시도가 사유를 받는다.
        fixed = re.sub(r",(\s*[}\]])", r"\1", body)
        try:
            data, repaired = json.loads(fixed), "후행 쉼표 제거"
        except json.JSONDecodeError:
            data, repaired = {}, f"파싱 실패: {e}"
    return {"model": model, "usage": usage, "text": txt, "data": data, "repair": repaired}


# ══════════════════════════════════════════════════════════════
# 코드 칸 — slot_id · var_id · formula_id 연결 · accept
#   var 는 (var_role, subject, metric, period, unit, region) 이 같으면 **한 변수**다.
#   같은 변수를 여러 식이 쓰면 var_id 를 공유해야 B 가 값을 재사용한다.
# ══════════════════════════════════════════════════════════════
def wire(data: dict, vocab: dict, concept: dict | None = None) -> tuple[list, list, list]:
    # 계열별 템플릿이 여기서도 같은 눈으로 적용돼야 한다 — 프롬프트가 T7 을 시키고
    # 배선이 T2 로 되돌리면 게이트는 「모델이 어긴 것」으로 읽는다(엉뚱한 데를 고치게 된다).
    spec = {fid: (t, p, tp) for fid, t, p, tp, _ in targets(vocab, concept)}
    var_ids, formulas, slots, notes = {}, [], [], []

    for f in data.get("formulas", []):
        fid = f.get("formula_id")
        if fid not in spec:
            notes.append({"formula_id": fid, "why": "목록 밖 식 — 버림"})
            continue
        target, path, template = spec[fid]
        fvars = []
        for v in f.get("vars", []):
            key = (v.get("var_role"), v.get("subject"), v.get("metric"),
                   str(v.get("period")), v.get("unit"), v.get("region"))
            vid = var_ids.setdefault(key, f"V{len(var_ids) + 1}")
            fvars.append({"var_id": vid, "var_role": v.get("var_role") or "",
                          "subject": v.get("subject") or "", "metric": v.get("metric") or "",
                          "period": str(v.get("period") or ""), "unit": v.get("unit") or "",
                          "subject_code": v.get("subject_code") or None,
                          "stat_code": v.get("stat_code") or None,
                          "corp_name": v.get("corp_name") or None,
                          "_observable": bool(v.get("observable"))})
            if not v.get("observable"):
                notes.append({"var_id": vid, "formula_id": fid,
                              "why": "observable=false — 슬롯 없음, 가정으로 간다"})
                continue
            if any(s["var_id"] == vid for s in slots):
                continue                      # 같은 변수는 슬롯 하나
            cell, ct = v.get("canvas_cell"), v.get("claim_type")
            # 가격 계량의 칸·claim_type 은 **세상에 대한 판단이 아니라 배선**이다 —
            # 계량이 정해지면 답이 하나뿐이다. 모델은 식의 target(TAM/SAM)을 자꾸
            # 따라 적었다(실측 3판). 배선은 코드가 잡고, 고친 사실을 기록에 남긴다.
            if v.get("metric") in (vocab["metric"].get("_가격_계량") or []) \
                    and (cell, ct) != ("수익원", "PRICE"):
                notes.append({"var_id": vid, "metric": v.get("metric"),
                              "고침": f"({cell}, {ct}) → (수익원, PRICE)",
                              "why": "가격 계량의 칸·claim_type 은 코드가 정한다"})
                cell, ct = "수익원", "PRICE"

            # ── 식 → claim_type **강제**. 위 가격 계량과 **같은 결**이다 ────────────
            # 식이 정해지면 답이 하나뿐이라 세상에 대한 판단이 아니다. 그런데 여기는
            # 게이트만 있고 배선이 없어서, 모델이 식의 target(TAM)을 따라 적으면
            # **되먹임으로 되돌려 주고 모델이 새로 짜다가 다른 것을 깨뜨렸다**
            # (2026-08-11 실측: 3회 시도가 서로 다른 검사를 오가며 진동했다).
            # ⚠ 규칙은 `vocab.식_목록.claim_type_강제` 하나가 정본이다 — 게이트
            #   (`gate.check_cell_claim_type`)가 읽는 자리와 **같은 곳**을 읽는다.
            _force = (vocab.get("식_목록") or {}).get("claim_type_강제") or {}
            _series = ((concept or {}).get("_계열") or {}).get("계열")
            if _force.get("enabled") and _series not in (_force.get("제외_계열") or []):
                want = (_force.get("map") or {}).get(fid)
                if want and ct != want:
                    notes.append({"var_id": vid, "formula_id": fid,
                                  "고침": f"claim_type {ct} → {want}",
                                  "why": "식이 정해지면 claim_type 은 하나다 — 코드가 정한다"})
                    ct = want

            # ── 역방향 corp_name 제거 ──────────────────────────────────────────
            # `route_sources` 는 corp_name 이 있으면 **무조건 dart** 로 보낸다
            # (blocks/a_desk.py:311). web 계량에 붙으면 공시에 없는 계정을 찾으러 가서
            # 그대로 빈손이 된다. 어느 계량이 dart 인지는 **표가 값으로 안다**
            # (`vocab.metric.catalog[…].route`) — 판단이 아니라 조회다.
            corp = v.get("corp_name") or None
            if corp and (vocab["metric"]["catalog"].get(v.get("metric")) or {}).get("route") != "dart":
                notes.append({"var_id": vid, "metric": v.get("metric"),
                              "고침": f"corp_name {corp} → null",
                              "why": "web 계량에 corp_name 이 붙으면 dart 로 라우팅돼 빈손이 된다"})
                corp = None

            slot = {"slot_id": f"S{len(slots) + 1}", "var_id": vid, "formula_id": fid,
                    "claim_type": ct,
                    "subject": v.get("subject") or "", "metric": v.get("metric") or "",
                    "period": str(v.get("period") or ""), "unit": v.get("unit") or "",
                    "region": v.get("region") or "대한민국",
                    "subject_code": v.get("subject_code") or None,
                    "stat_code": v.get("stat_code") or None,
                    "corp_name": corp,
                    "must_contain": v.get("must_contain") or [],
                    "subject_aliases": [a for a in (v.get("subject_aliases") or [])
                                        if str(a).strip()],
                    "must_not_contain": v.get("must_not_contain") or [],
                    "value_range": v.get("value_range") or None,
                    "accept": {"min_score": 5, "min_facts": 2},
                    "_canvas_cell": cell,
                    # `_` 접두라 run.py:243 이 걸러낸다 — 엔진 Slot 에는 안 들어간다.
                    "_추출_힌트": [h for h in (v.get("추출_힌트") or []) if str(h).strip()],
                    "_proxy_선언": {
                        "대상": str((v.get("proxy_선언") or {}).get("대상") or "").strip(),
                        "사유": str((v.get("proxy_선언") or {}).get("사유") or "").strip()}}
            if cell in vocab.get("잠정", {}):
                slot["_잠정"] = vocab["잠정"][cell]["사유"]
            # 경계 표시는 코드가 붙인다. 모델이 적기를 기다리면 빠지는 판이 생기고,
            # 경계 표시는 빠지면 안 되는 종류의 문장이다.
            probe = (vocab.get("요구") or {}).get("dart_검증_슬롯") or {}
            if probe.get("필요") and v.get("metric") == probe.get("metric"):
                slot["_경계"] = probe["경계"]
            slots.append(slot)
        formulas.append({"formula_id": fid, "target": target, "path": path,
                         "template": template, "vars": fvars})
    return slots, formulas, notes


def repair_design(slots: list, vocab: dict, guards: dict | None = None) -> list[dict]:
    """**답이 하나로 정해지는 권고를 코드가 고친다.** LLM 0회 · 네트워크 0회.

    (이름이 `repair` 가 아닌 이유: 이 파일에서 `raw["repair"]` 는 **JSON 파싱 복구**를
    뜻한다 — 같은 낱말이 두 가지를 가리키면 읽는 쪽이 헷갈린다.)

    권고 검사는 `passed=True` 라 재시도를 안 건다 — 그것이 「경고만」 결정의 실제
    내용이고, 그래서 권고 8건이 떠도 설계가 한 칸도 안 바뀌었다(실측). 그중 밴드는
    **표가 답을 값으로 들고 있다**(`rules/guards.v1.json` 계량_전형_밴드) — 판단이
    아니라 조회라, `wire()` 의 가격 계량 칸 강제·claim_type 강제와 같은 결이다.

    ⚠ **입력이 「슬롯 dict 목록」인 것이 설계의 핵심이다.** 저장된 스냅샷을 그대로
      먹일 수 있어야 유료 실행 0회로 효과를 증명한다. 원안(raw draft)을 받게 만들면
      그 증명이 불가능해진다.

    ⚠ 제자리에서 고친다(`slots` 를 바꾼다). 원안은 `_value_range_원안` 에 남고,
      **밑줄 접두라 `run.py:243` 이 걸러내 엔진 `Slot` 에는 안 들어간다.**

    돌려주는 것 — 교정 내역 목록. 비면 아무것도 안 고쳤다는 뜻이다. **인쇄하지 않는다**
    (부르는 쪽이 `gate.json` 에 값으로 싣는다).
    """
    rule = vocab.get("설계_교정") or {}
    if not rule.get("enabled"):
        return []
    고침 = []
    고침 += _교정_value_range(slots, rule.get("value_range_밴드") or {}, guards)
    고침 += _교정_must_contain(slots, rule.get("must_contain_낱말") or {})
    return 고침


def _교정_value_range(slots: list, rule: dict, guards: dict | None) -> list[dict]:
    """겹치지 않는 밴드를 **전형 밴드로 대체**한다. 답은 표에 값으로 있다."""
    if not rule.get("enabled"):
        return []
    if guards is None:
        guards = _load(os.path.join(ROOT, "rules", "guards.v1.json"))
    bands = (guards.get("value_range") or {}).get("계량_전형_밴드") or {}
    if not bands:
        return []
    원안_키 = rule.get("_원안_키") or "_value_range_원안"

    고침 = []
    for s in slots:
        band = (bands.get(s.get("metric")) or {}).get("밴드")
        vr = s.get("value_range")
        if not band or not vr or len(vr) != 2:
            continue                        # 밴드 없는 계량은 건드리지 않는다
        lo, hi = vr
        if not (hi < band[0] or lo > band[1]):
            continue                        # 겹친다 — 그대로 둔다
        s[원안_키] = [lo, hi]
        s["value_range"] = [band[0], band[1]]
        고침.append({"slot_id": s.get("slot_id"), "metric": s.get("metric"),
                    "칸": "value_range",
                    "원안": [lo, hi], "교정": [band[0], band[1]],
                    "근거": "guards.value_range.계량_전형_밴드",
                    "why": f"「{s.get('metric')}」의 전형 크기는 [{band[0]:g}, {band[1]:g}] "
                           f"인데 기대가 [{lo:g}, {hi:g}] 라 겹치지 않는다 — "
                           "이대로면 맞는 값이 격리된다"})
    return 고침


def _교정_must_contain(slots: list, rule: dict) -> list[dict]:
    """낱말을 **자기 subject 안의 어절 하나**로 줄인다.

    `any()` 라 낱말이 여럿이면 느슨해지고, subject 밖의 말이면 종류가 다른 값이 문턱을
    넘는다. 그래서 답은 항상 subject 안에 있다 — 판단이 아니라 조회다.

    ⚠ **없던 낱말을 지어내지 않는다.** 하나도 안 남으면 빈 채로 둔다 — 빈 `must_contain`
      은 위반이 아니고(`gate.check_must_contain`), 채우는 것은 조회가 아니라 판단이다.
    """
    if not rule.get("enabled"):
        return []
    원안_키 = rule.get("_원안_키") or "_must_contain_원안"

    고침 = []
    for s in slots:
        mc = [w for w in (s.get("must_contain") or []) if str(w).strip()]
        if not mc:
            continue
        subj = str(s.get("subject") or "")
        # 공백이 든 낱말은 어절로 쪼갠 뒤, subject 안에 실제로 있는 것만 남긴다.
        후보 = [tok for w in mc for tok in str(w).split() if tok and tok in subj]
        # 가장 긴 것 하나. 길이가 같으면 원래 순서를 지켜 결정론을 잃지 않는다.
        새것 = [max(후보, key=len)] if 후보 else []
        if 새것 == mc:
            continue                        # 이미 규율을 지킨다 — 그대로 둔다
        s[원안_키] = list(mc)
        s["must_contain"] = 새것
        고침.append({"slot_id": s.get("slot_id"), "metric": s.get("metric"),
                    "칸": "must_contain", "subject": subj,
                    "원안": list(mc), "교정": 새것,
                    "근거": "vocab.설계_교정.must_contain_낱말",
                    "why": f"must_contain 은 any() 라 낱말이 여럿이면 느슨해지고 subject "
                           f"밖의 말이면 종류가 다른 값이 문턱을 넘는다 — "
                           f"「{subj}」 안의 어절 "
                           + (f"「{새것[0]}」 하나로 줄였다" if 새것
                              else "이 하나도 없어 비웠다(없는 낱말을 지어내지 않는다)")})
    return 고침


class HarnessError(RuntimeError):
    """하네스가 **답을 못 받았다.** 게이트 미통과(fail-open)와 다르다 —
    저쪽은 「모델이 답했는데 검사를 못 넘었다」이고 이쪽은 「답 자체를 못 받았다」다.

    예전에는 이 자리가 `SystemExit` 였다. 프로세스를 끝내는 방법이라 **함수로 부를 수가
    없었다** — 서버에서 부르면 워커가 통째로 죽는다.
    """


@dataclasses.dataclass
class HarnessOptions:
    """⚠ 필드 이름은 CLI 인자의 `dest` 와 같아야 한다 — `main()` 이 `vars()` 를 붓는다."""

    concept: str
    tag: str
    model: str = MODEL
    replay: str = ""
    as_of: int = 0
    reason: str = ""

    def __post_init__(self):
        self.as_of = self.as_of or datetime.date.today().year


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--concept", required=True)
    ap.add_argument("--tag", required=True, help="스냅샷 꼬리표. 예: beauty-noshow")
    ap.add_argument("--model", default=MODEL)
    ap.add_argument("--replay", default="", help="저장된 LLM 응답으로 재검사 (LLM 0회)")
    ap.add_argument("--as-of", type=int, default=datetime.date.today().year)
    ap.add_argument("--reason", default="", help="스냅샷을 덮어쓰는 사유. _수정이력 에 남는다")
    a = ap.parse_args()
    try:
        run_harness(HarnessOptions(**vars(a)))
    except HarnessError as stopped:
        raise SystemExit(str(stopped))          # CLI 에서는 종전과 같이 멈춘다
    return 0


def run_harness(a: HarnessOptions) -> dict:
    """슬롯·식 설계 1판. **LLM ≤3회.** 인자 파싱 밖이라 오케스트레이터가 부를 수 있다.

    돌려주는 것 — `passed`(게이트 통과 여부) · `outdir`(gate.json 이 있는 자리) ·
    `slots`/`formulas`(메모리 값) · `snapshot`(통과했을 때만 파일 경로).

    ⚠ **미통과는 예외가 아니다.** fail-open 이라 `passed=False` 로 **돌아온다** —
      부르는 쪽이 그것을 `degradation` 으로 옮긴다. 예외는 「답을 못 받았다」뿐이다.
    """
    concept = _load(os.path.join(ROOT, a.concept) if not os.path.isabs(a.concept) else a.concept)
    vocab = _load(os.path.join(HERE, "vocab.json"))
    adapters = _load(os.path.join(ROOT, "rules", "adapters.v1.json"))
    # 씨앗 `runs/` 는 컨테이너에서 `:ro` 라 여기가 그 자리면 하네스가 죽는다.
    outdir = runpath.harness_write_dir(a.tag)
    os.makedirs(outdir, exist_ok=True)

    slotcheck = _load(os.path.join(ROOT, "rules", "slotcheck.v1.json"))
    corpcode = _load(os.path.join(ROOT, "adapters", "_cache_corpcode.json"))
    # 게이트도 자기 것을 따로 읽지만 **같은 파일**이라 갈릴 수 없다. 여기서 한 번 읽어
    # 교정에 넘기는 것은 「교정과 판정이 같은 표를 본다」를 호출로 못박는 것이다.
    guards = _load(os.path.join(ROOT, "rules", "guards.v1.json"))
    hyp = concept.get("_hypotheses_v2") or {}

    def judge(raw):
        slots, formulas, notes = wire(raw["data"], vocab, concept)
        # ⚠ **판정 앞에 온다.** 게이트가 자기가 판정할 것을 고치면 판정이 사라지므로,
        #   교정은 설계 층(여기)에 두고 게이트는 교정된 결과를 그대로 잰다.
        교정 = repair_design(slots, vocab, guards)
        if 교정:
            _decide("설계 교정 — 답이 하나인 권고를 코드가 고쳤다", f"{len(교정)}칸",
                    rule="vocab.설계_교정",
                    why="고친 칸 수가 곧 **모델 초안의 오류 건수**다 (권고는 판정 앞에서 "
                        "이미 지워지므로 권고_수로는 안 보인다): "
                        + ", ".join(f"{c['slot_id']}.{c['칸']}" for c in 교정))
        rep = G.run_gate(raw["data"], slots, formulas, vocab, adapters, hyp,
                         _env_key("KOSIS_API_KEY"), slotcheck, a.as_of, corpcode,
                         concept=concept)
        rep["wire_notes"] = notes
        # ⚠ `요약` 에는 절대 넣지 않는다 — `tools/harness_variance.py` 가 「통과 아님 =
        #   미통과」로 세기 때문이다. 교정은 통과/미통과와 다른 층의 값이다.
        rep["교정"] = 교정
        rep["교정_수"] = len(교정)
        return slots, formulas, rep

    attempts, report, best = [], None, None
    if a.replay:
        raw = _load(a.replay)
        print(f"[replay] {a.replay} — LLM 0회")
        slots, formulas, report = judge(raw)
    else:
        if not _env_key("OPENAI_API_KEY"):
            # 여기서 죽으면 `gate.json` 이 아예 안 만들어져 **기록이 통째로 사라진다.**
            # 「측정 안 됨」과 「개입 0」이 또 같아지는 자리라, 최소 기록만 따로 남긴다.
            _intervene("멈춤 — OPENAI_API_KEY 없음", "not_configured", blocking=True)
            io.open(os.path.join(outdir, "무인_기록.json"), "w", encoding="utf-8").write(
                json.dumps(_무인_기록(), ensure_ascii=False, indent=2))
            raise HarnessError("OPENAI_API_KEY 없음 → not_configured. 가짜 슬롯을 만들지 않는다.")
        os.environ.setdefault("OPENAI_API_KEY", _env_key("OPENAI_API_KEY"))
        # 재시도 상한은 규칙에 있다(vocab.재시도). 통과할 때까지 돌리면 하네스가 아니라 난수다.
        limit = vocab["재시도"]["max_attempts"]
        #: 이름이 여기 있는 **권고만** 재시도를 건다. 규칙은 vocab 이 정본이고 코드는 읽기만 한다.
        _RETRY_ADVISORY = set(vocab["재시도"].get("권고_재시도") or [])
        violations = None
        for n in range(1, limit + 1):
            try:
                raw = call_llm(build_prompt(concept, vocab, a.as_of, violations, corpcode,
                                            guards),
                               a.model, output_schema(vocab, concept))
            except Exception as e:
                # **LLM 호출 자체가 실패하는 자리.** 게이트 실패(fail-open)와 다르다 —
                # 저쪽은 «모델이 답했는데 검사를 못 넘었다», 이쪽은 «답을 못 받았다»다.
                #
                # 예전에는 여기서 예외가 그대로 올라가 프로세스가 죽었고, `gate.json` 도
                # `무인_기록` 도 **아무것도 안 남았다** — 판 ⑫ 실측: 크레딧 소진(429)으로
                # `runs/harness/p12-gate/` 가 **완전히 빈 디렉터리**로 남았다.
                # 무인 서비스에서 「돈이 떨어졌다」가 산출물에 안 남는 것은 조용한 실패다.
                #
                # ⚠ **fail-open 하지 않는다.** 답을 못 받았는데 진행하면 지어낸 슬롯이 된다.
                #   사람을 불러야 하는 자리가 맞으므로 **차단 개입으로 기록하고 멈춘다**.
                _intervene(f"멈춤 — LLM 호출 실패({type(e).__name__})",
                           str(e)[:280], blocking=True)
                io.open(os.path.join(outdir, "무인_기록.json"), "w", encoding="utf-8").write(
                    json.dumps({**_무인_기록(), "_시도": n, "_상한": limit},
                               ensure_ascii=False, indent=2))
                raise HarnessError(
                    f"LLM 호출 실패 (시도 {n}/{limit}) — {type(e).__name__}: {str(e)[:200]}\n"
                    f"  기록: {outdir}/무인_기록.json (차단 개입 1건)")
            io.open(os.path.join(outdir, f"llm_raw_{n}.json"), "w", encoding="utf-8").write(
                json.dumps(raw, ensure_ascii=False, indent=2))
            slots, formulas, report = judge(raw)
            failed = sum(len(c.get("violations") or []) or (0 if c["passed"] else 1)
                         for c in report["checks"])
            # 권고는 **탈락이 아니다.** 그래서 `failed` 에 더하지 않고 «둘째 열쇠»로 둔다 —
            # 위반이 같은 판본이 둘이면 권고가 적은 쪽을 고른다. 임의의 가중치(0.25 따위)를
            # 지어내지 않으려고 튜플 정렬을 쓴다.
            권고 = report.get("권고_수", 0)
            순위 = (failed, 권고)
            attempts.append({"시도": n, "usage": raw.get("usage"), "repair": raw.get("repair"),
                             # 모델 초안의 오류 건수. 권고가 0 이어도 이 값은 0 이 아닐 수
                             # 있다 — 교정이 판정 앞에서 이미 지웠기 때문이다.
                             "교정_수": report.get("교정_수", 0),
                             "요약": report["요약"], "통과": report["passed"], "위반_수": failed,
                             "권고_수": 권고, "권고_요약": report.get("권고_요약")})
            # ⚠ **최선 판본을 붙든다.** 예전에는 루프가 끝나면 «마지막» 판본이 남았고,
            #   되먹임이 진동하면 더 나쁜 판본이 채택됐다 — 2026-08-11 실측:
            #   시도2 가 위반 1건이었는데 버려지고 위반 2건인 시도3 이 최종이 됐다.
            #   재시도는 개선을 **보장하지 않는다**(모델이 매번 새로 짠다). 그러면
            #   「세 번 돌렸다」가 「가장 좋은 것을 골랐다」를 뜻하게 두어야 한다.
            if best is None or 순위 < best[3]:
                best = (slots, formulas, report, 순위, raw, n)
            print(f"[시도 {n}/{limit}] {raw['model']} · 슬롯 {len(slots)} · 식 {len(formulas)} · "
                  + ("통과" if report["passed"] else f"미통과(위반 {failed}건)")
                  + (f" · 권고 {권고}건" if 권고 else ""))
            # ⚠ **권고 대부분은 재시도를 걸지 않는다** — 그것이 「경고만」 결정의 실제
            #   내용이고, 답을 아는 권고는 `repair_design` 이 코드로 고친다. 예외는
            #   `vocab.재시도.권고_재시도` 에 이름이 적힌 것뿐이다: **지시가 이미 있는데
            #   모델이 요동하는 자리**라 지시로도 코드로도 못 고치고 다시 뽑아야 한다.
            #   ⚠ `passed` 는 건드리지 않으므로 fail-open 갈래는 그대로다.
            재뽑기 = [c["name"] for c in report["checks"]
                    if c.get("권고") and c["name"] in _RETRY_ADVISORY]
            if report["passed"] and not 재뽑기:
                _decide("게이트 통과 — 재시도 종료", f"시도 {n}/{limit}",
                        rule="vocab.재시도.max_attempts",
                        why="전 검사 통과" + (f" (권고 {권고}건은 막지 않는다)" if 권고 else ""))
                break
            if report["passed"] and n < limit:
                _decide("게이트는 통과했으나 권고로 재시도", f"시도 {n + 1}/{limit}",
                        rule="vocab.재시도.권고_재시도",
                        why="지시가 이미 있는데 모델이 요동하는 자리다 — " + ", ".join(재뽑기))
            # **재시도는 스스로 내린 결정이다** — 사람을 부르지 않고 위반을 되먹여 다시 돈다.
            # 이 줄이 없으면 3회를 돈 실행과 1회에 끝난 실행이 기록상 구별되지 않는다.
            if n < limit and not report["passed"]:
                _decide("게이트 미통과 → 재시도", f"시도 {n + 1}/{limit}",
                        rule="vocab.재시도.max_attempts",
                        why="미통과: " + ", ".join(c["name"] for c in report["checks"]
                                                 if not c["passed"]))
            violations = [{"검사": c["name"],
                           "위반": c.get("violations")
                           or {k: v for k, v in c.items()
                               if k in ("미충족_칸", "고아_슬롯") and v}}
                          for c in report["checks"] if not c["passed"]]
            # 어차피 다시 도는 판이면 권고도 같이 실어 보낸다 — **공짜다**(호출이 안 는다).
            # ⚠ 「탈락이 아니라 권고」라고 **문안에 적는다.** 안 적으면 모델이 규칙으로 읽고
            #   컨셉이 허락하지 않는 분산을 억지로 만든다.
            violations += [{"검사": c["name"], "부류": "권고(탈락 아님 — 지킬 수 있으면 지켜라)",
                            "위반": c.get("권고")}
                           for c in report["checks"] if c.get("권고")]

    if best is not None and best[3] < (
            sum(len(c.get("violations") or []) or (0 if c["passed"] else 1)
                for c in report["checks"]), report.get("권고_수", 0)):
        slots, formulas, report, _, raw, _n = best
        _decide("최선 판본 채택", f"시도 {_n}/{limit} (위반 {best[3][0]}건 · 권고 {best[3][1]}건)",
                rule="harness:best-of-N",
                why="마지막 판본보다 위반이 적다 (같으면 권고가 적다)")
        print(f"    최선 판본은 시도 {_n} (위반 {best[3][0]}건 · 권고 {best[3][1]}건) — "
              "그것으로 마감한다")

    report["tag"] = a.tag
    report["model"] = raw.get("model")
    report["시도_기록"] = attempts
    report["vocab"] = vocab                     # 규칙은 값째로 복사한다(절대 규칙 7)

    def _flush_gate():
        """`gate.json` 을 **지금 상태로** 쓴다.

        ⚠ **한 번만 쓰면 안 된다.** 첫 판본을 여기서 쓰고 끝내면 그 아래 fail-open 이
        내리는 결정이 기록에 안 들어간다 — 실제로 그랬다(`p11-meter-check` 에서
        `결정_횟수 0` 이 나왔는데 fail-open 은 발동한 상태였다). 마지막 결정까지 담기려면
        **끝나는 모든 갈래에서** 다시 흘려야 한다.
        """
        # **빈 리스트여도 반드시 싣는다** — 칸이 없으면 「개입 0」이 아니라 「미측정」이다(판 ⑪ ①).
        report["무인_기록"] = _무인_기록()
        io.open(os.path.join(outdir, "gate.json"), "w", encoding="utf-8").write(
            json.dumps(report, ensure_ascii=False, indent=2))

    _flush_gate()

    print(f"슬롯 {len(slots)}개 · 식 {len(formulas)}개")
    for k, v in report["요약"].items():
        print(f"  [{v}] {k}")

    if not report["passed"]:
        # ── fail-open 마감 (판 ⑦ H3, `rules/failopen.v1.json`) ──────────────
        # **사람을 부르지 않는다.** 무인 서비스에는 부를 사람이 없고, «사람 판단이 필요하다»
        # 로 끝나는 경로는 §0「어떤 입력에도 출력은 나온다」를 깨뜨린다.
        # 단 **조용히 넘어가지도 않는다** — 실패를 구조화해 남기고 하위 층이 그것을 읽는다.
        fo = _load(os.path.join(ROOT, "rules", "failopen.v1.json"))
        bad = [c for c in report["checks"] if not c["passed"]]
        # ⚠ **fail-open 은 개입이 아니라 결정이다.** 사람을 «안 부르고» 진행한 것이므로
        #   `_intervene` 이 아니라 `_decide` 로 간다. 여기를 개입으로 세면 H3 가 작동한
        #   실행이 「개입 1회」로 보이고, 「무인으로 돌았다」가 정반대로 뒤집힌다.
        _decide("게이트 미통과 · 재시도 소진 → fail-open 진행", "사람을 부르지 않고 종료 0",
                rule=f"failopen:{fo.get('version')}",
                why="미통과 검사: " + ", ".join(c["name"] for c in bad))
        # ⚠ **위반 항목이 전부 dict 인 것은 아니다.** `check_hypothesis_leak` 은 «샌 값»을
        #   문자열로 낸다(슬롯에 매인 위반이 아니라 값 자체가 위반이라서 옳은 모양이다).
        #   그걸 모르고 `.get` 을 부르면 **fail-open 이 터진다** — 하필 「어떤 입력에도
        #   출력은 나온다」를 지키라고 있는 자리가 예외로 죽는 것이다.
        #   판 ㉜ 분산 측정에서 실제로 터졌다(AttributeError: 'str' object has no attribute 'get').
        hit = {v.get("slot_id") for c in bad for v in (c.get("violations") or [])
               if isinstance(v, dict)}
        cells = sorted({s.get("_canvas_cell") for s in slots
                        if s.get("slot_id") in hit and s.get("_canvas_cell")})
        n = len(attempts) or 1
        rec = {"tag": a.tag, "concept": concept.get("name"),
               "시도_횟수": n, "상한": vocab["재시도"]["max_attempts"],
               "실패_검사": [{"검사": c["name"], "건수": len(c.get("violations") or [])}
                          for c in bad],
               "마지막_사유": [v for c in bad for v in (c.get("violations") or [])][:5],
               # 위반 슬롯이 어느 칸을 채웠어야 했는지. 못 고르면 측정 칸 전부로 둔다 —
               # **모른다를 «영향 없음»으로 적지 않는다.**
               "영향_칸": cells or sorted(vocab["canvas"]["측정판정"]["cells"]),
               "canvas_표시": {
                   "상태": fo["canvas"]["상태"],
                   "사유": fo["canvas"]["사유_문구"].format(
                       시도=n, 상한=vocab["재시도"]["max_attempts"]),
                   "머리말": fo["canvas"]["머리말"]},
               "_스냅샷": ("쓰지 않았다 — 게이트를 못 넘은 슬롯으로 수집하면 그 빈손이 "
                        "「자료 부재」로 오독된다(rules/failopen.스냅샷)"),
               "_규칙": fo["version"]}
        io.open(os.path.join(outdir, "harness_failure.json"), "w",
                encoding="utf-8").write(json.dumps(rec, ensure_ascii=False, indent=1))
        print(f"\n게이트 미통과 — 재시도 {n}/{rec['상한']} 소진. "
              f"**실패를 기록하고 계속 진행한다**(fail-open). 스냅샷은 쓰지 않는다.\n"
              f"  기록: {outdir}/harness_failure.json\n"
              f"  영향 칸: {', '.join(rec['영향_칸'])}")
        _flush_gate()          # fail-open 결정까지 담아 다시 쓴다
        # ⚠ **미통과는 예외가 아니다.** fail-open 이라 값으로 돌아간다 —
        #   부르는 쪽이 이것을 `degradation` 으로 옮긴다. 스냅샷은 쓰지 않았다.
        return {"passed": False, "outdir": outdir, "slots": slots, "formulas": formulas,
                "report": report, "snapshot": None, "failure": rec}

    stamp = datetime.date.today().isoformat()

    def write(kind: str, payload: list):
        path = os.path.join(ROOT, "data", f"{kind}_{a.tag}.json")
        # 스냅샷을 덮어쓰는 것은 「재생성」이 아니라 **승인된 1회 수정**이다 — 앞선 판의
        # 생성 기록과 사유를 이력으로 남긴다. 남기지 않으면 어느 판으로 측정했는지가 사라진다.
        history = []
        if os.path.exists(path):
            old = _load(path)
            history = list(old.get("_수정이력") or [])
            history.append({"시점": stamp, "이전_생성": old.get("_생성"),
                            "사유": a.reason or "(사유 미기재)"})
        head = {"_설명": f"슬롯 하네스 산출 스냅샷 ({a.tag}). 같은 컨셉 재실행은 이 파일을 쓴다 — 재생성 금지.",
                "_생성": {"방식": "LLM 초안 + 기계 게이트", "model": raw.get("model"),
                         "시도": len(attempts) or 1, "일자": stamp,
                         "게이트": f"runs/harness/{a.tag}/gate.json"},
                "_수정이력": history,
                "concept": concept.get("name"), "as_of": stamp}
        io.open(path, "w", encoding="utf-8").write(
            json.dumps({**head, kind: payload}, ensure_ascii=False, indent=2))

    write("slots", slots)
    write("formulas", formulas)
    _decide("게이트 통과 → 스냅샷 기록", f"data/slots_{a.tag}.json",
            rule="failopen:스냅샷 — 게이트 통과분만 기록", why="전 검사 통과")
    _flush_gate()              # 성공 갈래도 마지막 결정까지 담아 다시 쓴다
    print(f"\n스냅샷: data/slots_{a.tag}.json · data/formulas_{a.tag}.json")
    return {"passed": True, "outdir": outdir, "slots": slots, "formulas": formulas,
            "report": report, "snapshot": {
                # `run.py --slots/--formulas` 는 ROOT 기준 상대경로를 받는다 — 절차의
                # 정본(표준검사세트 v1.1)이 이 이름을 쓰므로 자리를 옮기지 않는다.
                "slots": os.path.join("data", f"slots_{a.tag}.json"),
                "formulas": os.path.join("data", f"formulas_{a.tag}.json")}}


if __name__ == "__main__":
    sys.exit(main())
