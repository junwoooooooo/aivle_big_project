# -*- coding: utf-8 -*-
"""**사람이 읽는 시장조사 보고서를 쓴다** — 목표 보고서와 대조하기 위한 A단계. (판 ㊹)

    python tools/write_report.py p43-smoke-01 --mode A1
    python tools/write_report.py p43-smoke-01 --mode A2
    python tools/write_report.py p43-smoke-01 --mode A2 --write-model gpt-4o
    python tools/write_report.py p43-smoke-01 --mode A2 --dry        # 안 부르고 규모만 잰다

## 왜 이것이 먼저인가

목표 보고서(`docs/market-research-redesign/TARGET_REPORT.md`)는 **이 서비스가 버린 문서
108건을 통째로 주고 「보고서 만들어 줘」**라고 했더니 나왔다. 슬롯도 화이트리스트도
점수도 게재 판정도 없었다. **그래서 좋았다.**

그리고 **우리는 그 재료를 이미 갖고 있다.** 그러니 관문을 다 뜯은 뒤에 대조하는 것은
먼 길이다 — **먼저 써 보고 대조하면, 무엇을 뜯어야 하는지 대조가 알려 준다.**

## 두 갈래가 곧 진단이다

    A1   sections.json 의 **인용 대조 통과 512건**
         → 좋으면: 우리 추출은 충분하다. 병은 «게재 폐기 + 판정 주체»뿐이다
    A2   a3_bodies.json 의 **원문 132건**을 목표 보고서와 같은 방식으로 통째로
         → A2 만 좋으면: 추출(680건)에서 **이미** 잃고 있다

⚠ **두 갈래는 같은 모델·같은 프롬프트·같은 온도로 돈다.** 안 그러면 차이가 재료 차이인지
   설정 차이인지 못 가른다. 다른 것은 **입력 재료 하나뿐**이어야 한다.

## ⚠ 모델 교락 — 이 도구가 못 지우는 것

목표 보고서는 **Claude 가 썼고** 이 도구는 엔진 기본값(`gpt-4o-mini`)으로 쓴다.
그래서 우리 것이 지면 **「구조가 져서」인지 「모델이 져서」인지 이 도구만으로는 못 가른다.**
→ `--write-model` 로 쓰는 단계만 바꿔 한 판 더 돌리면 그 둘이 갈린다. 결과에 **쓴 모델을
   반드시 적는다**(머리말에 박는다) — 안 적으면 다음 판이 모델을 잊고 구조를 탓한다.

## 이 도구가 건드리지 않는 것

화면·봉투·계약·`publish_gate`·`promote_cards` **하나도 안 건드린다.** 순수 실험이다.
산출은 마크다운 한 장이고 제품 경로에 grep 0건이다.
"""
from __future__ import annotations

import argparse, concurrent.futures as cf, io, json, os, sys, time

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
for p in (ROOT, os.path.join(ROOT, "adapters")):
    sys.path.insert(0, p)

import prompts
import runpath
from base import load_env_key
from runlog import Meter, Run, call_options, load_rules

#: 판 ㊾ 에서 `gpt-4o-mini` → `gpt-5.6-luna`.
MODEL = "gpt-5.6-luna"
WORKERS = 6
#: 문서 하나에서 읽을 최대 글자. `read_sections` 와 **같은 값**이어야 A1·A2 가
#: 「같은 문서를 같은 만큼 읽었다」가 된다. 다르면 A2 가 더 읽고 이긴 것이 된다.
DOC_CAP = 60000
#: ⚠ 상한을 넉넉히 준다. 처음 1,400/3,000 으로 재 보니 **8회에 4,476 토큰**밖에 안 썼다 —
#: 상한에 안 닿았으니 짧은 이유는 상한이 아니라 «간추리는 버릇»이었다. 프롬프트로 막고
#: 상한은 그 뒤를 받쳐 준다. 잘림은 원장에 남는다.
MAX_OUT_NOTE = 2500
MAX_OUT_SECTION = 6000

#: 절 코드 → (제목, 그 절이 답해야 하는 물음). `render_sections.SECTIONS` 와 같은 순서다.
SECTIONS = [
    ("MARKET_SIZE", "1 · 시장 크기 — 얼마나 큰가", "시장·카테고리의 규모와 성장률"),
    ("PRICE", "2 · 가격 — 내 값은 어디에 서는가", "우리 가격이 놓일 지형"),
    ("COMPETITOR", "3 · 경쟁 지형 — 그 자리에 누가 있나", "누가 얼마나 팔고 있나"),
    ("CHANNEL", "4 · 채널 — 어디서 팔리나", "어느 경로로 얼마나 팔리고 조건은 무엇인가"),
    ("DEMAND", "5 · 수요 — 우리 고객이 실재하는가", "사람들이 얼마나·왜 쓰나, 그리고 왜 안 쓰나"),
    ("UNIT_ECONOMICS", "6 · 원가와 수익성 — 이 사업이 남기는가", "한 개 팔면 얼마가 남나"),
    ("REGULATION", "7 · 규제 — 팔기 전에 확인할 것", "무엇을 지켜야 하나"),
]

# ══════════════════════════════════════════════════════════════
# 프롬프트 — **버리라고 하지 않는다.** 이 파일의 전부가 여기다.
#
# 옛 모듈의 병은 관문이 「관계없다」고 판정해 버린 것이었다. 그래서 여기서는
# 「관계없으면 버려라」 대신 **「어떻게 읽어야 하는지를 옆에 적어라」**를 시킨다.
# ══════════════════════════════════════════════════════════════
_규율 = """규율 — 이것을 어기면 보고서가 아니라 소설이 된다.
- **주어진 재료에 없는 숫자를 쓰지 않는다.** 어림·환산·추정을 새로 만들지 않는다.
- 다만 **재료의 두 수를 견주는 것은 해도 된다.** 단, **양변을 반드시 함께 적는다** —
  「우리 8,900원은 편의점 도시락 6,513원의 **1.37배**」처럼. 양변 없이 배율·차이만
  적으면 그 수는 출처가 없는 수가 된다. (판 ㊻ 결정)
- ⚠ **컨셉에 적힌 수는 «재료»가 아니다.** 우리 판매가·침투율·목표 같은 것은 사업가가
  스스로 쓴 가정이지 조사가 알아낸 것이 아니다. **표에 올리지 않고 출처를 붙이지 않는다.**
  비교의 기준으로 문장 안에서만 언급한다 — 「우리 8,900원은 편의점 3,900~6,500원보다 높다」처럼.
  (실측 결함: 8,900원이 KRX·DART 출처를 달고 표에 앉은 적이 있다. 그것은 거짓 출처다.)
- 값마다 **출처(문서 제목이나 URL)와 연도**를 붙인다. 연도가 재료에 없으면 「연도 없음」이라고 쓴다.
- **관계가 멀어 보이는 값도 버리지 않는다.** 대신 어떻게 읽어야 하는지를 옆에 적는다 —
  「우리보다 넓은 범주라 상한으로만 본다」 · 「세계 시장이다」 · 「대체 수단의 값이다」 ·
  「경쟁사 한 곳의 전사 실적이라 이 시장의 크기가 아니다」 처럼.
- 표의 행이 여럿이면 **행을 찢지 말고 표로 함께** 낸다. 합이 100%가 아니면 그렇다고 적는다.
- 없으면 **없다고 쓴다.** 그럴듯한 문장으로 빈자리를 메우지 않는다.
- 「의향」은 실제 구매가 아니다. 그렇게 적는다.
- 마지막에 **이 절이 사업가에게 무엇을 뜻하는지 한두 문장의 판단**을 쓴다.
  판단에 쓰는 수는 위에 적은 수여야 한다."""

PROMPT_NOTE = """다음은 시장조사로 수집한 문서 하나의 본문이다.

사업 컨셉: {concept}

이 문서에서 **아래 7개 절 각각에 쓸 만한 것을 남김없이** 뽑아 적어라.
숫자·비율·금액·연도·기관명·회사명·제도명을 **원문 표기 그대로** 옮기고,
그 숫자가 나온 **문장을 그대로 인용**해 함께 적는다.

절:
{sections}

- 해당 절에 쓸 것이 없으면 그 절은 **빈 문자열**로 둔다. 억지로 채우지 않는다.
- **컨셉과 멀어 보여도 적는다.** 무엇에 쓸지는 나중에 정한다. 여기서 버리지 않는다.
- 표가 있으면 행을 **표째로** 옮긴다.
- 요약하지 말고 **수와 인용을 남긴다.**
- ⚠ **위 컨셉에 적힌 수를 문서에서 나온 것처럼 옮기지 않는다.** 컨셉은 「무엇에 쓸모가
  있는지」를 알려 주는 배경일 뿐이고, 적을 것은 **이 문서 본문에 실제로 있는 수**뿐이다.
- **길이를 아끼지 않는다.** 이 문서에 쓸 만한 수가 30개면 30개를 다 적는다.

문서 출처: {url}
조회일: {fetched}

본문:
{body}

JSON 하나로만 답한다. 키는 절 코드, 값은 문자열이다:
{skeleton}"""

PROMPT_SECTION = """너는 시장조사 보고서의 한 절을 쓴다.

사업 컨셉: {concept}

이 절: **{title}**
이 절이 답해야 하는 물음: {ask}

{규율}

아래는 이 절을 위해 수집·정리된 재료다. **여기 있는 것으로만** 쓴다.

{material}

마크다운으로 이 절 하나만 쓴다. 제목 줄(`## {title}`)로 시작한다.
표로 낼 수 있는 것은 표로 낸다.

**짧게 쓴다.** 사업가는 이 절을 1분 안에 읽는다.

- **표는 하나. 최대 6행.** 이 절의 물음에 정면으로 답하는 것부터 앞에서 고른다
  (재료는 이미 그 순서로 정렬돼 있다).
- 표 앞의 글은 **두세 문장**을 넘기지 않는다. 표를 말로 다시 읽어 주지 않는다.
- ⚠ **이 절의 물음과 먼 것은 표에 올리지 않는다.** 「시장 크기」 절에 온라인 전체
  거래액·배달비지수·지역별 인구 비중을 올리면, 값이 참이어도 그 절은 답을 잃는다.
- 표에 못 실은 것은 **버린 것이 아니다.** 표 아래에 한 줄로 이렇게 적는다 —
  「이 절의 근거 {건수}건 중 {실은}건을 실었고, 나머지는 「근거로 검산하기」에 있습니다.」
  {건수}·{실은}은 실제 수로 채운다.
재료가 얇으면 얇다고 쓰고 **분량을 지어내지 않는다**."""

PROMPT_TAIL = """너는 시장조사 보고서의 마지막 두 절을 쓴다.

사업 컨셉: {concept}

아래는 방금 쓴 1~7절 본문이다.

{body}

두 절을 마크다운으로 쓴다.

## 8 · 못 구한 것 — 다음에 채울 자리
본문에서 **끝내 답하지 못한 물음**을 줄로 적는다. 각 줄에 **어디서 구할 수 있는지**까지 적는다.
(예: 「입점 수수료 — 유통사 MD 에게 직접 문의」·「실제 구매 의사 — 시장 인터뷰」)
지어내지 말고 본문이 실제로 못 답한 것만 적는다.

## 9 · 이 조사가 말하는 것
이 사업안을 **지지하는 근거**와 **흔드는 근거**를 각각 줄로 적는다.
각 줄은 **본문에 실제로 적힌 수를 인용**해야 한다. 수 없는 총평은 쓰지 않는다.
마지막에 사업가가 **다음에 무엇을 해야 하는지** 한 문단으로 적는다."""


def _skeleton() -> str:
    return json.dumps({c: "…" for c, _, _ in SECTIONS}, ensure_ascii=False, indent=1)


def _sections_ask() -> str:
    return "\n".join(f"- {c} — {t.split(' · ',1)[-1]} ({ask})" for c, t, ask in SECTIONS)


def _읽는다(raw: str) -> dict:
    """JSON 하나를 건진다. 못 읽으면 **빈 dict 가 아니라 예외** — 조용히 0건이 되면 안 된다."""
    s = (raw or "").strip()
    if s.startswith("```"):
        s = s.split("\n", 1)[-1].rsplit("```", 1)[0]
    i, j = s.find("{"), s.rfind("}")
    if i < 0 or j < 0:
        raise ValueError(f"JSON 을 못 찾았다: {s[:200]}")
    return json.loads(s[i:j + 1])


# ══════════════════════════════════════════════════════════════
# 재료 만들기 — A1 은 사실에서, A2 는 원문에서
# ══════════════════════════════════════════════════════════════
def _run_dir(run_id: str) -> str:
    for base in runpath.SEARCH_ORDER:
        p = os.path.join(base, run_id)
        if os.path.isdir(p):
            return p
    raise SystemExit(f"원장을 못 찾았다: {run_id}")


def 재료_A1(d: os.PathLike | str) -> tuple[dict[str, str], dict]:
    """인용 대조를 **통과한 것만** 절별로 묶는다.

    ⚠ `채택 == True` 는 게재 판정이 아니라 **인용 대조**다. 이 도구는 게재를 안 본다 —
      게재가 병인지 아닌지가 바로 이 실험이 묻는 것이라, 여기서 미리 걸면 답이 정해진다.
    """
    sec = json.load(io.open(os.path.join(d, "sections.json"), encoding="utf-8"))
    묶음: dict[str, list[str]] = {c: [] for c, _, _ in SECTIONS}
    쓴, 버린 = 0, 0
    for doc in sec["문서별"]:
        url, 조회 = doc.get("url") or "", doc.get("조회일") or ""
        for it in doc.get("items") or []:
            if not it.get("채택"):
                버린 += 1
                continue
            c = it.get("section")
            if c not in 묶음:
                버린 += 1
                continue
            값 = f"{it.get('number_raw') or ''}{it.get('unit_raw') or ''}".strip()
            줄 = {"값": 값 or "(수 없음)", "무엇의_수": it.get("subject") or "",
                  "연도": it.get("year") or "연도 없음", "인용": it.get("quote") or "",
                  "표_맥락": it.get("table_context") or "", "출처": url, "조회일": 조회}
            묶음[c].append(json.dumps(줄, ensure_ascii=False))
            쓴 += 1
    재 = {c: "\n".join(v) for c, v in 묶음.items()}
    return 재, {"모드": "A1", "쓴_사실": 쓴, "안_쓴_사실": 버린,
                "절별": {c: len(v) for c, v in 묶음.items()},
                "보낸_글자": sum(len(v) for v in 재.values())}


#: 절 하나에 실어 보낼 최대 재료 건수. (판 ㊻ 실측)
#:
#: 왜 상한이 필요한가 — `재료_A1` 은 **게재 판정을 안 본다**(그 함수 머리말이 그렇게
#: 적어 뒀고, 그것이 A1/A2 실험에서는 옳았다). 그런데 제품 경로가 그대로 쓰자
#: 게이트가 이미 「서랍」·「주제 밖」으로 갈라 놓은 것까지 전부 §1 재료가 됐고,
#: **§1 본문이 42행 + 50행 + 40행 표 세 개**가 됐다 — 온라인 식품 30조·배달비지수·
#: 1인가구 지역별 비중이 「시장 크기」 절에 앉았다. 목표 보고서의 §1 표는 **8행**이다.
#:
#: ⚠ **버리는 것이 아니다.** 접힌 것은 화면의 「근거로 검산하기」와 원장에 그대로 있고,
#:   프롬프트가 **몇 건을 안 실었는지 글로 밝히게** 한다. 버리는 자리는 게재뿐이라는
#:   규율 그대로다 — 여기가 그 게재 자리다.
절_재료_상한 = 40


def 재료_카드(카드: list[dict]) -> tuple[dict[str, str], dict]:
    """**게이트가 이미 고른 것**으로 절별 재료를 만든다. LLM 0회.

    `promote_cards.build` → `pick_lead.apply` 를 지난 카드를 그대로 받는다. 그래서
    ① 주제 밖은 이미 없고 ② 서랍(`_갈래 == "밖"`)은 빼고 ③ 「먼저 볼 것」이 앞에 선다.
    **이 함수는 순서를 다시 정하지 않는다** — 고르는 자리를 두 곳으로 늘리지 않는다.
    """
    묶음: dict[str, list[dict]] = {c: [] for c, _, _ in SECTIONS}
    for c in 카드:
        if c.get("_갈래") == "밖":
            continue
        if c.get("_절") in 묶음:
            묶음[c["_절"]].append(c)

    재, 접힘 = {}, {}
    for c, 목록 in 묶음.items():
        접힘[c] = max(0, len(목록) - 절_재료_상한)
        재[c] = "\n".join(json.dumps({
            "값": card.get("_원문값") or "(수 없음)",
            "무엇의_수": card.get("계량") or "",
            "연도": card.get("기간") or "연도 없음",
            "인용": card.get("인용") or "",
            "등급": card.get("등급"),
            "출처": card.get("출처_url") or "",
            "조회일": card.get("조회일") or "",
        }, ensure_ascii=False) for card in 목록[:절_재료_상한])
    return 재, {"모드": "카드", "쓴_사실": sum(len(v[:절_재료_상한]) for v in 묶음.values()),
                "안_쓴_사실": sum(접힘.values()),
                "절별": {c: len(v) for c, v in 묶음.items()}, "접힘": 접힘,
                "보낸_글자": sum(len(v) for v in 재.values())}


def 재료_A2(d: str, concept: str, meter, run, model: str, limit: int | None,
            dry: bool) -> tuple[dict[str, str], dict]:
    """원문을 문서마다 읽어 **절별 자유 노트**로 만든다 (map). 이것이 A2 의 전부다.

    ⚠ **이것도 결국 한 번 읽는 단계 아닌가** — 맞다. 다만 `read_sections` 와 다른 점이
      셋이고, 그 셋이 이 실험의 처치다:
        ① 값 스키마(`number_raw`/`unit_raw`)가 없다 — **표도 문장도 그대로** 남는다
        ② 절 배정·주제 판정으로 **버리지 않는다** — 컨셉과 멀어도 적으라고 시킨다
        ③ 인용 대조를 **안 건다** — 그래서 A2 결과는 «아직 검증 안 된 것»이다. 그 값을
           대조에서 사람이 감안해야 한다. 목표 보고서도 같은 상태다(그 문서가 스스로 적어 뒀다)
    """
    bodies = json.load(io.open(os.path.join(d, "a3_bodies.json"), encoding="utf-8"))
    sec = json.load(io.open(os.path.join(d, "sections.json"), encoding="utf-8"))
    docs = [x for x in sec["문서별"] if bodies.get(x.get("trace_id"))]
    if limit:
        docs = docs[:limit]

    보낸 = sum(min(len(bodies[x["trace_id"]]), DOC_CAP) for x in docs)
    if dry:
        return ({c: "" for c, _, _ in SECTIONS},
                {"모드": "A2", "문서": len(docs), "보낼_글자": 보낸, "부를_횟수": len(docs)})

    뼈대, 절ask = _skeleton(), _sections_ask()

    def 하나(x):
        body = bodies[x["trace_id"]][:DOC_CAP]
        p = prompts.render(PROMPT_NOTE, concept=concept, sections=절ask,
                           url=x.get("url") or "", fetched=x.get("조회일") or "",
                           body=body, skeleton=뼈대)
        try:
            r = meter.create("a6_note", model=model, input=p,
                             **_호출옵션(model, MAX_OUT_NOTE))
            return x, _읽는다(getattr(r, "output_text", "") or "")
        except Exception as e:
            # **실패는 값이다** — 조용히 0건이 되면 「문서에 없었다」로 오독된다.
            run.log("a6_note.fail", {"trace_id": x.get("trace_id"),
                                     "error": f"{type(e).__name__}: {e}"}, status="error")
            return x, None

    묶음: dict[str, list[str]] = {c: [] for c, _, _ in SECTIONS}
    실패 = 0
    with cf.ThreadPoolExecutor(WORKERS) as ex:
        for x, got in ex.map(하나, docs):
            if not got:
                실패 += 1
                continue
            for c, _, _ in SECTIONS:
                t = (got.get(c) or "").strip()
                if t:
                    묶음[c].append(f"[출처] {x.get('url') or ''} (조회 {x.get('조회일') or ''})\n{t}")
    재 = {c: "\n\n".join(v) for c, v in 묶음.items()}
    return 재, {"모드": "A2", "문서": len(docs), "노트_실패": 실패, "보낸_글자": 보낸,
                "절별_노트": {c: len(v) for c, v in 묶음.items()},
                "재료_글자": sum(len(v) for v in 재.values())}


_re = __import__("re")
#: **배율말까지 함께** 집는다. `10만원` 에서 `10` 만 집으면 재료 아무 데나 있는 `10` 과 맞아
#: **검사가 거짓 초록을 낸다** — 판 ㊹ A단계에서 실제로 그랬다(「컨셉 누출 0」인데 표에 10만원).
_수 = _re.compile(r"\d[\d,\.]*\s*[조억만천백십]*")


def _nums(s: str) -> set[str]:
    """수를 뽑아 **배율까지 편 값**으로 만든다. 표기 차이로 유령을 만들지 않기 위해서다.

    `10만원` 과 `100000` 이 같은 값이 되고, `8천억` 과 `8억` 은 **다른 값**이 된다.
    배율 해석은 엔진의 `_수값` 하나가 한다 — **두 곳이 각자 풀지 않는다.**
    """
    import synthesize as SY                                        # noqa: PLC0415
    out: set[str] = set()
    for m in _수.findall(s or ""):
        t = m.strip()
        if not t:
            continue
        v = SY._수값(t)
        if v is not None and v >= 0:
            # 정수로 접는다 — `6.8조` 와 `6800000000000` 이 갈리지 않게
            out.add(f"{v:.0f}" if v == int(v) else f"{v:.6g}")
        else:                       # 못 읽으면 **버리지 않고** 글자 그대로 남긴다
            bare = t.replace(",", "").rstrip(".")
            if bare and bare != "0":
                out.add(bare)
    return out


def 유령수(md: str, 재: dict[str, str], concept: str) -> list[str]:
    """보고서에 있는데 **재료에도 컨셉에도 없는 수**를 센다.

    ⚠ **지우지 않는다. 센다.** 이 저장소의 규율은 「버리는 자리는 질문과 게재뿐」이고,
      여기는 그 자리가 아니다. 대신 **머리말에 박아** 대조하는 사람이 감안하게 한다.

    실측(판 ㊹ A2 탐색, 문서 3건): 「편의점 3,900~6,500원」이 나왔는데 그 수는 재료 3건에도
    컨셉에도 **없었다.** 모델이 지어낸 것이다. 이 검사가 없으면 그 줄이 조사 결과로 읽힌다.
    """
    허용 = set()
    for v in 재.values():
        허용 |= _nums(v)
    허용 |= _nums(concept)
    # 연·월과 순번은 유령 판정에서 뺀다 — 문장 구조에서 자연히 나온다
    쓴 = {n for n in _nums(md) if not (len(n) == 4 and n.startswith("20")) and len(n) > 1}
    return sorted(쓴 - 허용, key=lambda x: -len(x))[:60]


def 컨셉수(md: str, 재: dict[str, str], concept: str) -> list[str]:
    """보고서에 있는데 **재료에는 없고 컨셉에만 있는 수**를 센다.

    ⚠ 유령 검사만으로는 이것을 못 잡는다 — 컨셉에 있으니 「허용」에 들어간다. 그런데
      이 수들이야말로 이 저장소의 오래된 결함이다: **사용자가 쓴 가정을 조사 결과로 도장
      찍는 것.** 실측(판 ㊹ A1): 6절 「원가와 수익성」 표에 판매가 8,900 · CAC 10만 ·
      LTV 60만이 앉았는데 셋 다 컨셉이 적은 목표값이지 조사가 알아낸 값이 아니다.
      **그 절은 재료가 20건이었고, 표에 오른 것은 그 20건이 아니었다.**
    """
    재수 = set()
    for v in 재.values():
        재수 |= _nums(v)
    컨 = _nums(concept) - 재수
    쓴 = {n for n in _nums(md) if len(n) > 1}
    return sorted(쓴 & 컨, key=lambda x: -len(x))[:40]


# ══════════════════════════════════════════════════════════════
#: 판 ㊾ 에서 `runlog.call_options` 로 **합쳤다.** 같은 규칙이 두 곳에 있으면 갈린다 —
#: 이 저장소에서 이미 세 번 일어난 일이고, `service/summary.py` 가 그 네 번째였다.
_호출옵션 = call_options


def _본문(조각: str) -> str:
    """절 마크다운에서 **제목 줄(`## …`)만 뗀다.**

    ⚠ **자르는 셈은 이 함수 하나다.** 봉투(`serialize.report`)는 `###` 이하만 싣고 CLI 는
      제목째로 싣는데, 두 곳이 각자 자르면 언젠가 갈린다 — 이 저장소가 여섯 번 겪은 모양이다.
    """
    줄 = (조각 or "").lstrip().split("\n", 1)
    if 줄 and 줄[0].startswith("## "):
        return (줄[1] if len(줄) > 1 else "").strip()
    return (조각 or "").strip()


#: 꼬리 한 덩어리가 담는 두 절. **차례가 곧 8·9절이다.**
TAIL_SECTIONS = ("GAPS", "SYNTHESIS")


def 꼬리_절(md: str) -> list[dict]:
    """꼬리(8·9절) 한 덩어리를 **절 둘로 가른다.** 가르는 셈도 여기 한 곳이다.

    ⚠ 제목의 번호로 먼저 가르고, 번호가 없으면 **차례로** 붙인다 — 모델이 제목을 다르게
      써도 9절이 8절 자리에 앉지 않게. 조각이 하나뿐이면 그 하나만 낸다(없는 절을 만들지 않는다).
    """
    조각: list[list[str]] = []
    for line in (md or "").splitlines():
        if line.startswith("## "):
            조각.append([line])
        elif 조각:
            조각[-1].append(line)
        elif line.strip():
            조각.append([line])
    out = []
    for i, chunk in enumerate(조각[:len(TAIL_SECTIONS)]):
        머리 = chunk[0].lstrip("# ").strip()
        code = ("SYNTHESIS" if 머리.startswith("9") else
                "GAPS" if 머리.startswith("8") else TAIL_SECTIONS[min(i, 1)])
        본문 = _본문("\n".join(chunk))
        if 본문:
            out.append({"section": code, "본문": 본문})
    return out


def 절별_보고서(재: dict[str, str], concept: str, meter, run, model: str,
            꼬리: bool = True) -> dict:
    """**절마다 한 번 부른다.** 절 코드 → 그 절의 마크다운. 여기가 «쓰는» 자리 한 곳이다.

    CLI(`main`)와 제품 경로(`pipeline._sections`)가 **이 함수를 같이 쓴다.**
    돌려주는 것은 한글 키 문서고, 계약 번역은 `serialize.report()` 가 한다.

    `꼬리` 는 8·9절(못 구한 것 · 이 조사가 말하는 것)이다. **LLM 1회를 더 쓴다.**
    """
    조각: list[tuple[str, str]] = []
    for c, title, ask in SECTIONS:
        mat = 재.get(c) or ""
        if not mat.strip():
            # **재료가 없으면 LLM 을 안 부른다.** 부르면 없는 것을 만들어 낸다.
            # 그리고 그 사실을 **글로 남긴다** — 빈 절은 「안 썼다」로도 「없다」로도 읽힌다.
            조각.append((c, f"## {title}\n\n이 절에 쓸 재료가 **한 건도 없었습니다.**\n"))
            continue
        p = prompts.render(PROMPT_SECTION, concept=concept, title=title, ask=ask,
                           규율=_규율, material=mat)
        r = meter.create("a6_section", model=model, input=p,
                         **_호출옵션(model, MAX_OUT_SECTION))
        조각.append((c, (getattr(r, "output_text", "") or "").strip()))

    절 = [{"section": c, "본문": _본문(t)} for c, t in 조각]
    본문 = "\n\n".join(t for _, t in 조각)
    if 꼬리:
        r = meter.create("a6_tail", model=model,
                         input=prompts.render(PROMPT_TAIL, concept=concept, body=본문),
                         **_호출옵션(model, MAX_OUT_SECTION))
        꼬리글 = (getattr(r, "output_text", "") or "").strip()
        본문 = 본문 + "\n\n" + 꼬리글
        절 += 꼬리_절(꼬리글)
    return {"쓴_모델": model, "본문": 본문,
            "유령": 유령수(본문, 재, concept), "컨셉_누출": 컨셉수(본문, 재, concept),
            "절": 절}


def concept_text(concept: dict) -> str:
    """컨셉을 프롬프트에 싣는 **한 가지 방식.** CLI 와 제품이 같은 글자를 보낸다."""
    return json.dumps(concept.get("_hypotheses_v2") or concept, ensure_ascii=False)[:4000]


def 머리말(계: dict, mode: str, 유령: list[str], 누출: list[str],
        쓰기모델: str, 노트모델: str, 대조용: bool = False) -> str:
    """보고서 머리말 — **이 글을 어떻게 읽어야 하는가.**

    ⚠ **경계 표시다. 줄을 빼지 마라.** 재료가 무엇인지 · 누가 썼는지 · 인용 대조를 거쳤는지 ·
      유령 수와 컨셉 누출이 몇 개인지가 없으면, 모델이 쓴 문장이 **조사 결과로** 읽힌다.

    `대조용` 은 CLI 전용 액자다(목표 보고서와 나란히 놓는다는 실험 설명). 제품 봉투에는
    그 액자를 싣지 않는다 — 사용자는 실험을 읽는 것이 아니다.
    """
    if mode == "카드":
        # **접힌 건수를 같이 말한다.** 「N건으로 썼다」만 적으면 표본이 전량인 척한다.
        접 = int(계.get("안_쓴_사실") or 0)
        재료 = (f"게재를 통과한 근거 {계.get('쓴_사실')}건"
              + (f" (절마다 {절_재료_상한}건까지 — {접}건은 접혀 있고 원장에 그대로 있어요)"
                 if 접 else ""))
    elif mode == "A1":
        재료 = "인용 대조를 통과한 사실 " + str(계.get("쓴_사실")) + "건"
    else:
        재료 = "수집 원문 " + str(계.get("문서")) + "건을 통째로"
    줄 = []
    if 대조용:
        줄 += [f"# 우리 엔진이 쓴 시장조사 보고서 — {mode}", "",
              "> ⚠ **이 문서는 대조용이다.** 목표 보고서(`TARGET_REPORT.md`)와 나란히 놓고",
              "> 「사업가가 어느 쪽을 들고 다음 단계로 가겠는가」를 묻기 위해 만들었다.",
              ">"]
    줄 += [f"> - 재료: **{재료}**",
          f"> - 쓴 모델: **{쓰기모델}**"
          f"{'' if 쓰기모델 == 노트모델 else f' (노트 단계는 {노트모델})'} · 온도 0",
          f"> - LLM **{계.get('llm_calls', 0)}회** · 입력 {계.get('tokens_in', 0):,} ·"
          f" 출력 {계.get('tokens_out', 0):,} 토큰 · {계.get('초', 0)}초"]
    if 대조용:
        줄 += ["> - ⚠ 목표 보고서는 **Claude 가** 썼다. 모델이 다르므로 **이 대조만으로는",
              ">   「구조가 졌다」와 「모델이 졌다」를 못 가른다.**"]
    줄.append("> - ⚠ A2 의 값은 **인용 대조를 거치지 않았다.** 목표 보고서와 같은 상태다."
              if mode == "A2" else
              "> - 값은 전부 인용 대조를 통과했다 — 목표 보고서에 없는 보증이다.")
    # ⚠ 「가짜」라고 도장 찍지 않는다 — 판 ㊻ 부터 **재료의 두 수를 견준 값**(배율·차이)이
    #   허용이라, 이 계수기가 그것까지 함께 센다. 「전부 지어냈다」로 읽히면 사업가는
    #   맞는 문장까지 버린다. 경계는 그대로 두되 **무엇인지 정확히** 말한다.
    줄.append(f"> - **재료에 없는 수 {len(유령)}개** — 재료에도 컨셉에도 없는데 본문에 나온 수다."
              + (" 지어낸 것일 수도, 재료의 두 수를 견준 값일 수도 있다 — 옮겨 적기 전에"
                 " 그 문장이 양변을 같이 적었는지 본다." if 유령 else " (없다)"))
    if 유령:
        줄.append(">   `" + " · ".join(유령[:12]) + "`")
    줄.append(f"> - **컨셉 누출 {len(누출)}개** — 재료에 없고 **사업가가 쓴 가정에만** 있던 수가"
              f" 본문에 나왔다.{' 조사 결과가 아니다.' if 누출 else ' (없다)'}")
    if 누출:
        줄.append(">   `" + " · ".join(누출[:12]) + "`")
    return "\n".join(줄)


def 재료_경로(source_run: str) -> str:
    return os.path.join(runpath.write_dir(source_run), "report-material.json")


def _재료_저장(source_run: str, 재: dict, 계: dict, concept: dict) -> None:
    """**실패해도 판을 죽이지 않는다.** 이것은 편의지 산출물이 아니다."""
    try:
        io.open(재료_경로(source_run), "w", encoding="utf-8").write(
            json.dumps({"재": 재, "계": 계, "concept": concept}, ensure_ascii=False))
    except Exception as error:                      # noqa: BLE001
        print(f"  재료 저장 실패(무시) — {type(error).__name__}: {str(error)[:80]}")


def build(source_run: str, concept: dict, *, run_id: str,
          model: str = MODEL, 카드: list[dict] | None = None) -> tuple[dict | None, int, str]:
    """**제품 경로가 부르는 자리** — 게재를 지난 근거 카드로 보고서를 쓴다.

    ⚠ `카드` 를 **반드시 넘긴다**(판 ㊻). 안 넘기면 `재료_A1` 로 물러서는데, 그것은
      **게재 판정을 안 보므로** 서랍·주제 밖까지 재료가 되어 §1 이 132행 표가 된다.
      물러섬을 남겨 둔 것은 A1/A2 실험 CLI 가 그 경로를 쓰기 때문이다.

    1~7절 + 꼬리(8·9절) + 머리말까지 **보고서 전체**를 돌려준다. 화면이 목표 HTML 을
    통째로 그리기 때문이다.

    `(문서, LLM 호출 수, 실패 사유)` 를 돌려주고 **예외를 던지지 않는다.** 실패해도
    호출 수는 돌려줘야 원장이 거짓말을 안 한다 — 이미 쓴 돈은 안 쓴 것이 되지 않는다.

    ⚠ **꼬리말(`꼬리말`)은 `None` 이다.** 이 도구는 「주요 출처」 같은 발문을 쓰지 않는다 —
      없는 것을 여기서 지어내면 그것은 조사가 아니라 장식이다. 출처는 절마다 표 안에 있다.
    """
    os.environ.setdefault("OPENAI_API_KEY", load_env_key("OPENAI_API_KEY") or "")
    from openai import OpenAI                                       # noqa: PLC0415
    run = Run(run_id, rules=load_rules())
    meter = Meter(OpenAI(), run)
    t0 = time.time()
    try:
        재, 계 = (재료_카드(카드) if 카드 else 재료_A1(_run_dir(source_run)))
        # ★ **재료를 원장에 남긴다** (판 ㊻ · LLM 0회 · 0원).
        #   이것이 없어서 프롬프트를 한 줄 고칠 때마다 수집·게재·고르기를 통째로 다시 사고
        #   **판당 ₩600** 이 나갔다. 남겨 두면 `tools/report_bench.py` 가 절 하나만
        #   다시 써서 **₩40** 이고, `--dry` 는 아예 공짜다.
        _재료_저장(source_run, 재, 계, concept)
        doc = 절별_보고서(재, concept_text(concept), meter, run, model)
    except Exception as error:                      # noqa: BLE001 — 실패는 값이다
        return None, int(run.counters.get("llm.calls", 0)), f"{type(error).__name__}: {error}"
    계 |= {"llm_calls": int(run.counters.get("llm.calls", 0)),
          "tokens_in": int(run.counters.get("llm.tokens_in", 0)),
          "tokens_out": int(run.counters.get("llm.tokens_out", 0)),
          "초": round(time.time() - t0, 1)}
    return ({"쓴_모델": doc["쓴_모델"], "유령_수": len(doc["유령"]),
             "컨셉_누출_수": len(doc["컨셉_누출"]), "절": doc["절"],
             "머리말": 머리말(계, 계.get("모드") or "A1", doc["유령"], doc["컨셉_누출"],
                           model, model),
             "꼬리말": None},
            계["llm_calls"], "")


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("run_id")
    ap.add_argument("--mode", choices=("A1", "A2"), required=True)
    ap.add_argument("--concept", default="data/concept_hmr-product.json")
    ap.add_argument("--model", default=MODEL, help="노트(map) 단계 모델")
    ap.add_argument("--write-model", default=None, help="쓰는 단계만 다른 모델로 (모델 교락 분리용)")
    ap.add_argument("--limit", type=int, default=0, help="A2 문서 수 제한 — 싸게 확인")
    ap.add_argument("--dry", action="store_true", help="안 부르고 규모만 잰다")
    ap.add_argument("--out", default=None)
    a = ap.parse_args()

    d = _run_dir(a.run_id)
    cp = a.concept if os.path.isabs(a.concept) else os.path.join(ROOT, a.concept)
    concept = concept_text(json.load(io.open(cp, encoding="utf-8")))

    쓰기모델 = a.write_model or a.model
    run_id = f"{a.run_id}-report{a.mode}"

    if a.dry:
        재, 계 = (재료_A1(d) if a.mode == "A1"
                 else 재료_A2(d, concept, None, None, a.model, a.limit or None, True))
        print(json.dumps(계, ensure_ascii=False, indent=1))
        return 0

    os.environ.setdefault("OPENAI_API_KEY", load_env_key("OPENAI_API_KEY") or "")
    from openai import OpenAI
    run = Run(run_id, rules=load_rules())
    meter = Meter(OpenAI(), run)

    t0 = time.time()
    if a.mode == "A1":
        재, 계 = 재료_A1(d)
    else:
        재, 계 = 재료_A2(d, concept, meter, run, a.model, a.limit or None, False)
    # **재료를 남긴다.** A2 는 노트를 만드느라 유료라, 안 남기면 검사를 하나 더할 때마다
    # 다시 사야 한다. 실측: 이 한 줄이 없어서 A2 를 두 번 태울 뻔했다.
    재경로 = os.path.join(runpath.GENERATED_RUNS_DIR, run_id + "-material.json")
    io.open(재경로, "w", encoding="utf-8").write(
        json.dumps({"재료": 재, "concept": concept}, ensure_ascii=False, indent=1))

    doc = 절별_보고서(재, concept, meter, run, 쓰기모델)
    md, 유령, 컨셉누출 = doc["본문"], doc["유령"], doc["컨셉_누출"]

    계 |= {"유령_수": len(유령), "유령_보기": 유령[:12],
          "컨셉_누출": len(컨셉누출), "컨셉_누출_보기": 컨셉누출[:12], "재료": 재경로,
          "모델_노트": a.model, "모델_쓰기": 쓰기모델,
          "llm_calls": int(run.counters.get("llm.calls", 0)),
          "tokens_in": int(run.counters.get("llm.tokens_in", 0)),
          "tokens_out": int(run.counters.get("llm.tokens_out", 0)),
          "초": round(time.time() - t0, 1)}

    # ⚠ 머리말은 **봉투와 같은 함수**가 쓴다 — 두 곳이 각자 쓰면 경계 문구가 갈린다.
    머리 = (머리말(계, a.mode, 유령, 컨셉누출, 쓰기모델, a.model, 대조용=True)
            + f"\n\n```json\n{json.dumps(계, ensure_ascii=False, indent=1)}\n```\n\n---\n\n")

    out = a.out or os.path.join(
        ROOT, "..", "..", "..", "..", "docs", "market-research-redesign",
        f"OUR_REPORT_{a.mode}.md")
    out = os.path.normpath(out)
    io.open(out, "w", encoding="utf-8").write(머리 + md + "\n")
    run.log("a6_report", {**계, "out": out})
    print(json.dumps(계, ensure_ascii=False, indent=1))
    print(f"\n→ {out}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
