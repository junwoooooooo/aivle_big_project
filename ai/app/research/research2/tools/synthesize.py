# -*- coding: utf-8 -*-
"""**9절 합성** — 「이 사업안을 지지하는 것 / 흔드는 것」. (판 ㊷ 4단계)

    python tools/synthesize.py runs-generated/p42-gate/publish.json \
           --concept data/concept_hmr-product.json --id p42-synth --dry-run
    …            (--dry-run 을 빼면 **유료**. LLM 1회)

**갈래와 묶음은 기계가 정하고 LLM 은 문장화만 한다.** 무엇을 말할지는 `rules/synthesize.v1.json`
의 셈 조건이 고르고, 모델은 고른 사실들을 한국어 한 문장으로 옮기기만 한다.

검사 하나가 유령 수를 구조적으로 막는다 — **문장 안의 모든 수가 그 묶음의 사실 안에 있어야
한다.** 없으면 문장째로 버린다. **버릴 뿐 고쳐 쓰지 않는다**(규칙 5 — 실패는 값이다).
"""
from __future__ import annotations

import argparse, io, json, os, re, sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
for p in (ROOT, HERE, os.path.join(ROOT, "adapters")):
    sys.path.insert(0, p)

import prompts
import publish_gate as PG          # ⚠ 절 배정 규칙의 정본은 PG.절() 하나다
from base import load_env_key      # ⚠ `adapters/base.py` 다 — 위 경로 추가가 있어야 뜬다
from runlog import Meter, Run, call_options, load_rules

#: 판 ㊾ 에서 `gpt-4o-mini` → `gpt-5.6-luna`. 발췌(`read_sections`)가 판 ㊺ 에 먼저
#: 옮겨간 것과 같은 이유이고, 인자 차이는 `runlog.call_options` 가 흡수한다.
MODEL = "gpt-5.6-luna"
MAX_OUT = 4096                 # 판 ㊶ 교훈 — 상한을 안 주면 잘리고 통째로 파싱 실패가 된다
_NUM = re.compile(r"[0-9][0-9,]*(?:\.[0-9]+)?")
JSON_OBJ = re.compile(r"\{.*\}", re.S)

PROMPT = """너는 시장조사 보고서의 마지막 절을 쓴다. **판단은 이미 끝나 있다.**
아래 묶음마다 갈래(지지/흔듦)와 근거 사실이 **정해져 있고, 너는 그것을 바꿀 수 없다.**

네가 할 일은 하나다 — 묶음마다 **「이 수들이 이 사업안에 무엇을 뜻하는가」를 한국어 한 문장**으로.

⚠ **값을 나열하지 마라.** 「증가율은 17.3%이며 성장률은 5.5%이다」는 표를 읽은 것이지 문장이
아니다. 사업가는 표를 이미 보고 있다. 그 아래에 **「그래서 이 사업안에 무엇인가」**를 쓴다.
갈래가 `지지` 면 그 수들이 **왜 이 사업안을 밀어주는지**, `흔듦` 이면 **무엇을 위태롭게
하는지**가 문장에 드러나야 한다.

지켜야 할 것:
1. **그 묶음의 근거 사실만** 쓴다. 다른 묶음의 수도, 네가 아는 수도 쓰지 않는다
2. 문장에 **수를 최소 하나** 넣고, 그 수는 근거의 `값`과 **글자 그대로 같아야** 한다
   (`804만 5천` 을 `804.5만` 으로 바꾸지 마라. 단위도 그대로)
3. **근거 2건 이상을 문장 안에서 쓴다**
4. **수 없는 평가어를 쓰지 마라.** 「긍정적·부정적·적정·우수·잠재력·매력적·유망·성공적·
   기대된다·전망된다」는 **기계가 걸러 문장째로 버린다.** 수가 스스로 말하게 써라
5. 한 문장. 40~90자. **과장하지 마라** — 이것은 사업가가 돈을 걸 판단이다
6. **확신을 지어내지 마라.** 수가 말하는 데까지만 쓴다. 「따라서 성공한다」는 이 수들이
   말하지 않는다

[사업안]
{concept}

[묶음]
{groups}

**JSON 만** 출력한다:
{"문장": [{"키": "묶음의 키", "문장": "…"}]}
"""


_OBJ = re.compile(r"\{[^{}]*\}")


def _읽는다(raw: str) -> tuple:
    """(키→문장, 잘렸나). **잘린 응답이 통째로 0건이 되게 두지 않는다** (판 ㊶ 교훈).

    통짜 파싱이 실패하면 `{…}` 낱건으로 건진다 — 마지막 하나만 잘렸는데 앞의 일곱을
    버리는 것은 손해이고, 무엇보다 **왜 비었는지를 못 가르게 만든다.**
    """
    m = JSON_OBJ.search(raw or "")
    if m:
        try:
            got = json.loads(m.group(0)).get("문장") or []
            return {x.get("키"): str(x.get("문장") or "") for x in got if isinstance(x, dict)}, False
        except json.JSONDecodeError:
            pass
    out = {}
    for o in _OBJ.findall(raw or ""):
        try:
            x = json.loads(o)
        except json.JSONDecodeError:
            continue
        if isinstance(x, dict) and x.get("키"):
            out[x["키"]] = str(x.get("문장") or "")
    return out, True


def _실린(d: dict) -> list:
    out = []
    for r in d["문서별"]:
        for it in r.get("items", []):
            if not PG.머리인가(it):      # 9절도 절 머리만 — 판 ㊹ 3단계
                continue
            sec = PG.절(it)
            out.append({**it, "_절": sec, "_url": r.get("url") or ""})
    return out


def _nums(s: str) -> set:
    return {m.replace(",", "") for m in _NUM.findall(str(s or ""))}


#: **큰 배율.** 한국어 수는 이 셋으로 «자리»가 끊긴다 — 각 자리 안에 다시 천·백·십이 온다.
_단위값 = (("조", 10 ** 12), ("억", 10 ** 8), ("만", 10 ** 4))
#: 한 자리 «안»의 작은 배율. `8천억` 의 `천` 이 여기다.
_잔단위 = (("천", 1000), ("백", 100), ("십", 10))


def _소단위(s: str) -> float | None:
    """한 자리 안의 수 — `8천` → 8000 · `7421` → 7421 · `백` → 100 · `` → None.

    ⚠ **수 없이 배율말만 오는 것이 정상이다** (`백만원` 의 `백`, `십억` 의 `십`).
      그때 계수는 1 이다 — 0 으로 두면 값이 통째로 사라진다.
    """
    남, 총, 봄 = s, 0.0, False
    for 말, 배 in _잔단위:
        if 말 not in 남:
            continue
        앞, 남 = 남.split(말, 1)
        m = _NUM.search(앞)
        총 += (float(m.group(0)) if m else 1.0) * 배
        봄 = True
    m = _NUM.search(남)
    if m:
        총 += float(m.group(0))
        봄 = True
    return 총 if 봄 else None


def _수값(s) -> float:
    """`804만 5천` · `804만5,000` · `2조 7,421억` 을 **같은 수로** 만든다.

    글자 비교로는 이 셋이 서로 다른 값처럼 보인다. 판 ㊵ 의 「804만 5천 ×4 중복」이
    이 자리의 병이었고, 실측에서 `804만 5천` 과 `804만5,000` 이 **둘 다 살아남았다.**
    못 읽으면 `-1` — **추측해서 같다고 하지 않는다.**

    ## ⚠ 판 ㊹ 2단계 — **배율말이 겹치면 앞의 것을 통째로 잃고 있었다**

    옛 구현은 조·억·만·천을 **한 줄에 세워** 각 자리에서 «첫 숫자 하나»만 집었다.
    그래서 `8천억` 의 `8천` 에서 **`8`만** 집어 8억이 됐다. 실측(고치기 전):

    | 표기 | 옛 값 | 참값 | 틀린 배 |
    |---|---|---|---|
    | `6조 8천억` | 6,000,800,000,000 | **6.8조** | 8천억을 8억으로 |
    | `8천억` | 800,000,000 | **8천억** | **1,000배** |
    | `3천만` | 30,000 | **3천만** | **1,000배** |
    | `1억 2천만` | 100,020,000 | **1.2억** | 2천만을 2만으로 |

    `6조 8천억` 은 이 판의 **왕관 사실**이고, 그것이 봉투에 `6,000,800,000,000` 으로
    앉아 있었다. **자리를 끊고(조·억·만) 자리 «안»을 따로 읽는다**(`_소단위`)로 고친다.
    덤으로 `1,140,941백만원` 같은 공시 표기도 바르게 읽힌다(`백`을 자리 안에서 처리).
    """
    t = str(s or "").replace(",", "").replace(" ", "")
    if not t:
        return -1.0
    총, 남 = 0.0, t
    for 말, 배 in _단위값:
        if 말 not in 남:
            continue
        앞, 남 = 남.split(말, 1)
        v = _소단위(앞)
        if v is None:
            return -1.0
        총 += v * 배
    v = _소단위(남)
    if v is not None:
        총 += v
    return 총 if 총 else -1.0


def _채널_대조(ev: list, 컨셉: dict) -> str:
    """**컨셉이 든 주 채널이 실측 비중 표에 있나.** 없으면 그것 자체가 흔드는 사실이다.

    ⚠ 이 계산을 안 넘기면 모델이 엉뚱한 말을 한다 — 실측: 「대형마트 31.05%**에 불과**하고」.
    31.05% 는 그 표의 **최대**인데 최소처럼 읽었다. **대조는 기계가 하고 모델은 옮기기만 한다.**
    """
    가정 = str(((컨셉.get("_hypotheses_v2") or {}).get("7_채널") or {}).get("주_채널_가정") or "")
    if not 가정 or not ev:
        return ""
    # **비중은 같은 표 안에서만 견준다.** 실측: 「편의점 사업의 매출 구성비 45.0%」는 채널
    # 점유율이 아니라 **편의점이 파는 상품의 구성비**인데, 섞이자 그것이 최대가 되어
    # 「최대는 편의점인데 컨셉의 주 채널이 아니다」라는 자기모순 문장이 나왔다.
    # ⚠ **연도로도 가른다.** 실측: 9절 문장이 「편의점 5.99%(2025)」와 「대형마트 32.37%
    # (2024)」를 한 문장에서 견줬다. 같은 해 값은 31.05% 다.
    표 = {}
    for it in ev:
        표.setdefault((str(it.get("table_context") or ""), str(it.get("year") or "")), []).append(it)
    ev = max(표.values(), key=len)
    잡힌 = []
    for it in ev:
        이름 = re.sub(r"(에서의|에서|의)$", "", re.split(r"[ —-]", str(it.get("subject") or ""))[0])
        if 이름 and 이름 in 가정:
            잡힌.append((이름, it))
    최대 = max(ev, key=lambda x: _수값(x.get("number_raw")))
    최대이름 = re.sub(r"(에서의|에서|의)$", "", re.split(r"[ —-]", str(최대.get("subject") or ""))[0])
    # **주어를 못 박는다.** 이것은 시장 전체가 아니라 **한 회사의 매출처 구성비**다.
    # 실측: 모델이 「**업계** 주요 매출원에서 입지가 약하다」라고 썼다 — 판 ㊵ 최악의
    # 어긋남(「음·식료품 38조를 냉동 간편식 시장이라 불렀다」)과 같은 종류다.
    회사 = str(ev[0].get("게재_발행사") or "").split("·")[0] or "이 회사"
    해 = str(ev[0].get("year") or "")
    머리 = f"⚠ 이것은 시장 전체가 아니라 **{회사} 한 회사의 {해} 매출처 구성비**다. "
    if 잡힌:
        이름, it = min(잡힌, key=lambda x: _수값(x[1].get("number_raw")))
        return (머리 + f"컨셉이 든 주 채널 중 이 표에 잡힌 것은 **{이름} 하나뿐이고 "
                f"{it['number_raw']}{it.get('unit_raw')}** 다. 같은 표의 최대는 "
                f"{최대이름} {최대['number_raw']}{최대.get('unit_raw')} 이고 **컨셉의 주 채널이 아니다**")
    return (머리 + f"컨셉이 든 주 채널이 이 표에 **하나도 없다.** 같은 표의 최대는 "
            f"{최대이름} {최대['number_raw']}{최대.get('unit_raw')} 다")


def _고른다(spec: dict, 실린: list, 컨셉: dict, 판단: dict) -> list:
    """묶음 하나의 근거를 **셈으로** 고른다. 사람이 지목한 목록이 아니다."""
    if spec.get("판단에서"):
        J = (판단 or {}).get(spec["판단에서"]) or {}
        out = []
        for g in J.get("갈래", []):
            for s in g.get("근거", []):
                out.append({**s, "_절": "PRICE"})
        return out

    g = spec.get("고르기") or {}
    hit = []
    for it in 실린:
        if g.get("절") and it["_절"] not in g["절"]:
            continue
        # ⚠ **게재 갈래를 본다.** 엔진이 이미 「이 수를 어떻게 읽어야 하나」를 판정해
        #    뒀는데(우리 시장 / 상위 범주 / 대체 수단 / 경쟁사) 9절이 그것을 무시했다.
        #    실측(2026-08-15): 「시장이 자란다」의 근거가 **상위 범주**인 온라인쇼핑 전체
        #    20.1% 와 **가전·전자·통신기기 41.6%** 였다. 같은 시스템이 그 수에
        #    「⚠ 상한으로만 읽어야 한다」는 경계를 붙여 놓고, 9절에서는 그것으로 성장을
        #    단언했다 — **자기 판정을 자기가 어긴 것**이다.
        #    이 잣대는 업종을 타지 않는다: 어느 사업이든 상위 범주가 자라는 것이
        #    내 세그먼트가 자란다는 뜻은 아니다.
        if g.get("갈래") and (it.get("게재") or "") not in g["갈래"]:
            continue
        if g.get("단위") and str(it.get("unit_raw") or "").strip() not in g["단위"]:
            continue
        t = " ".join(str(it.get(k) or "") for k in ("subject", "table_context"))
        if g.get("어휘") and not any(w in t for w in g["어휘"]):
            continue
        if g.get("부호") == "양수":
            m = _NUM.search(str(it.get("number_raw") or ""))
            if not m or float(m.group(0).replace(",", "")) <= 0:
                continue
        hit.append(it)

    # **같은 값 중복은 접는다** — 판 ㊵ 의 「804만 5천 ×4」가 이 자리의 병이었다.
    # ⚠ 글자로 접으면 못 잡는다: 실측으로 `804만 5천` 과 `804만5,000` 이 **둘 다 살아남았다.**
    # 그래서 **수로 환산해서** 접는다.
    본, 봄 = [], set()
    for it in hit:
        k = (_수값(it.get("number_raw")), str(it.get("unit_raw")))
        if k in 봄:
            continue
        봄.add(k)
        본.append(it)
    return 본


def 묶는다(d: dict, c: dict, 판단: dict | None = None) -> list:
    """**기계가 갈래와 근거를 정한다.** LLM 0회. 판 ㊸ 1단계에서 `main()` 밖으로 꺼냈다.

    이 함수와 `build()` 를 가른 이유 — 여기까지가 **공짜**다. 제품 경로에서 키가 없거나
    예산이 막히면 여기서 멈춰도 「무엇을 말하려 했는지」는 남는다.
    """
    S = json.load(io.open(os.path.join(ROOT, "rules", "synthesize.v1.json"), encoding="utf-8"))
    판단 = 판단 or {}
    실린 = _실린(d)

    묶음 = []
    for spec in S["묶음"]:
        ev = _고른다(spec, 실린, c, 판단)
        if len(ev) < 2:
            print(f"  (빠짐) {spec['키']:<20} 근거 {len(ev)}건 — **2건 미만이라 침묵한다**")
            continue
        m = {**spec, "근거": ev[:4]}
        if spec.get("컨셉_채널_대조"):
            m["무엇"] = _채널_대조(ev, c) or m["무엇"]
        if spec.get("판단에서"):
            # **2절이 이미 계산한 결론을 그대로 넘긴다.** 안 넘기면 9절이 같은 근거를 쥐고
            # 다른 말을 한다 — 실측: 2절은 「배달과 8% 차이로 근소」인데 9절은 「가격 부담을
            # 줄 수 있다」만 말했다(배달값 둘을 근거로 들고도 안 썼다). 사업가가 두 절을
            # 같이 읽으면 **어느 쪽이 이 보고서의 답인지 모른다.**
            # ⚠ 프롬프트에 「2절과 맞춰라」를 넣는 것은 부탁이지 규칙이 아니다. 기계가
            #    계산한 결론을 **재료로** 넘기는 것이 규칙이다.
            결 = ((판단 or {}).get(spec["판단에서"]) or {}).get("결론")
            if 결:
                m["무엇"] = f"{m['무엇']} — 2절이 이미 낸 결론: {결}"
        묶음.append(m)
    return 묶음


def build(d: dict, c: dict, 판단: dict | None = None, *, run_id: str = "p43-synth") -> dict:
    """9절 「지지 / 흔듦」. **LLM 1회 — 유료다.** 돌려주는 모양은 `synthesis.json` 과 같다.

    기계가 갈래·근거를 정하고(`묶는다`) 모델은 **문장만 쓴다.** 쓴 문장은 여기서 검사해
    묶음에 없는 수나 금지 평가어가 있으면 **문장째 버린다.**
    """
    S = json.load(io.open(os.path.join(ROOT, "rules", "synthesize.v1.json"), encoding="utf-8"))
    묶음 = 묶는다(d, c, 판단)
    if not 묶음:
        return {"문장": []}

    h2 = c.get("_hypotheses_v2") or {}
    개념 = (f"{c.get('name')} — {c.get('solution')}\n"
           f"가격 제안값: {(h2.get('6_수익_가격') or {}).get('제안값_krw_월')}원\n"
           # 「주 채널이 안 보인다」 묶음은 **컨셉이 무엇을 주 채널로 들었는지**를 알아야
           # 문장이 된다. 근거 사실만으로는 「대형마트가 31.05%」에서 끝난다.
           f"주 채널 가정: {(h2.get('7_채널') or {}).get('주_채널_가정')}")
    본문 = json.dumps([{"키": m["키"], "갈래": m["갈래"], "무엇": m["무엇"],
                      "근거": [{"값": f"{s['number_raw']}{s.get('unit_raw') or ''}",
                              "무엇의_수": s["subject"]} for s in m["근거"]]}
                     for m in 묶음], ensure_ascii=False, indent=1)

    os.environ.setdefault("OPENAI_API_KEY", load_env_key("OPENAI_API_KEY") or "")
    from openai import OpenAI
    run = Run(run_id, rules=load_rules())
    meter = Meter(OpenAI(), run)
    # **측정 조건을 고정한다.** 온도를 안 주면 판마다 다른 줄이 금지어에 걸려 **내용 손실이
    # 비결정적**이 된다(실측: `긍정적` → `잠재력`). 답에 맞춰 깎는 것이 아니라 조건을
    # 고정하는 것이고, `max_output_tokens` 를 명시하는 것과 같은 종류다.
    # ⚠ 여러 판을 굴려 살아남은 것만 고르는 것은 **부정**이다. 편차를 줄이는 게 아니라 감춘다.
    #
    # ⚠ 추론 모델(판 ㊾ 부터)은 온도를 못 받는다. 대신 `call_options` 가 **출력 상한을
    #   4배로 연다** — 생각한 토큰이 상한을 먹어 본문이 빈 채로 «성공»하는 것을 막는다.
    r = meter.create("a5_synth", model=MODEL, **call_options(MODEL, MAX_OUT),
                     input=prompts.render(PROMPT, concept=개념, groups=본문))
    raw = getattr(r, "output_text", "") or ""
    got, 잘림 = _읽는다(raw)
    if 잘림:
        # **잘린 것이 통째로 0건이 되게 두지 않는다** (판 ㊶ 교훈). 온 데까지는 쓴다.
        print(f"\n⚠ 응답이 잘렸다 — 낱건으로 건졌다. 건진 문장 {len(got)} / 묶음 {len(묶음)}")
    if not got:
        # **못 읽은 것을 「할 말이 없었다」로 만들지 않는다.** 빈 목록으로 조용히 돌려주면
        # 화면이 「9절이 비었다」로 그리고, 그것은 거짓이다 — 말할 것은 있었고 못 읽었을 뿐이다.
        print("\n**하나도 못 읽었다.** 원문 앞머리:", raw[:300])
        raise RuntimeError("9절 합성 응답을 하나도 읽지 못했다")

    # ── 검사: 문장의 모든 수가 그 묶음 안에 있어야 한다 ──────────
    정가 = str(((c.get("_hypotheses_v2") or {}).get("6_수익_가격") or {}).get("제안값_krw_월") or "")
    결과 = []
    for mm in 묶음:
        문장 = got.get(mm["키"], "")
        허용 = set()
        for s in mm["근거"]:
            허용 |= _nums(s["number_raw"]) | _nums(s["subject"])
        허용 |= _nums(정가)
        쓴 = _nums(문장)
        유령 = sorted(쓴 - 허용)
        맞은 = sorted(쓴 & {n for s in mm["근거"] for n in _nums(s["number_raw"])})
        # **평가어는 기계가 막는다.** 프롬프트로 부탁해서는 안 지켜졌다(실측).
        평가어 = sorted(w for w in S["검사"]["금지어"] if w in 문장)
        ok = bool(문장) and not 유령 and bool(맞은) and not 평가어
        결과.append({**mm, "문장": 문장 if ok else None, "유령수": 유령, "맞은수": 맞은,
                     "평가어": 평가어,
                     "버린_이유": ("" if ok else
                                 "빈 문장" if not 문장 else
                                 f"묶음에 없는 수 「{'·'.join(유령)}」" if 유령 else
                                 f"수 없는 평가어 「{'·'.join(평가어)}」" if 평가어 else
                                 "근거의 수를 하나도 안 썼다")})

    print("\n── 검사 ──")
    for x in 결과:
        if x["문장"]:
            print(f"  [{x['갈래']}] {x['문장']}")
        else:
            print(f"  [{x['갈래']}] **버림** ({x['키']}) — {x['버린_이유']}")
    산 = [x for x in 결과 if x["문장"]]
    print(f"\n살아남은 문장 {len(산)} / {len(결과)}  "
          f"(지지 {sum(1 for x in 산 if x['갈래'] == '지지')} · "
          f"흔드는 것 {sum(1 for x in 산 if x['갈래'] == '흔듦')})")
    print(f"비용: {run.counters.get('llm.tokens_in', 0)} in / "
          f"{run.counters.get('llm.tokens_out', 0)} out")

    return {"문장": [{k: v for k, v in x.items() if k != "고르기"} for x in 결과]}


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("publish")
    ap.add_argument("--concept", required=True)
    ap.add_argument("--id", default="p42-synth")
    ap.add_argument("--dry-run", dest="dry", action="store_true")
    a = ap.parse_args()

    d = json.load(io.open(a.publish, encoding="utf-8"))
    c = json.load(io.open(a.concept, encoding="utf-8"))
    jp = os.path.join(os.path.dirname(a.publish), "judgments.json")
    판단 = json.load(io.open(jp, encoding="utf-8")) if os.path.exists(jp) else {}

    묶음 = 묶는다(d, c, 판단)
    지 = [m for m in 묶음 if m["갈래"] == "지지"]
    흔 = [m for m in 묶음 if m["갈래"] == "흔듦"]
    print(f"\n기계가 나눈 갈래 — 지지 {len(지)} · 흔드는 것 {len(흔)}")
    for m in 묶음:
        print(f"  [{m['갈래']}] {m['키']:<20}{m['무엇']}")
        for s in m["근거"]:
            print(f"       · {s['number_raw']}{s.get('unit_raw')}  «{s['subject']}»")

    if a.dry:
        print("\n--dry-run — 여기서 멈춘다 (LLM 0회 · 0원)")
        return 0

    try:
        doc = build(d, c, 판단, run_id=a.id)
    except RuntimeError as e:
        print(f"\n{e}")
        return 1

    out = os.path.join(os.path.dirname(a.publish), "synthesis.json")
    io.open(out, "w", encoding="utf-8").write(
        json.dumps(doc, ensure_ascii=False, indent=1, default=str))
    print(f"기록: {out}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
