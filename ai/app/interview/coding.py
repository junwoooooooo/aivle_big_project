"""2단계 주제 코딩 — 흩어진 답을 몇 개의 주제로 묶는다.

**LLM 은 이름표만 붙인다. 숫자와 인용문은 코드가 만든다.**

이 분업이 이 모듈의 전부다. LLM 에게 「몇 명이 말했나」를 세게 하면 그 수를 검산할 길이
없고, 「대표 인용문을 써라」고 하면 아무도 하지 않은 말이 결과에 실린다.

---

## 왜 2패스인가 — 관측된 40/40 (2026-08-12)

n=40 실행에서 **모든 주제가 `40명 중 40명`** 으로 나왔다. 대안 3개("가끔 요리한다" ·
"간편식·배달" · "참는다")가 **동시에 40/40** 이었다 — 한 사람이 셋을 다 한다는 뜻이라
응답 분포로는 성립할 수 없다. 같은 판에서 이해도만 22/17/1 로 멀쩡히 갈렸다.

원인 셋이 겹쳤고 셋 다 이 파일의 옛 구조에 있었다.

- **A.** 이해도에만 배타 제약이 있었다("셋 중 하나에 정확히 한 번"). 주제에는 아무 제약도
  없었다. 이해도만 멀쩡했던 것이 그 증거다.
- **B.** 옛 규칙 3 「숫자를 쓰지 말고 id 만 넣어라」를 모델이 **「전부 넣어라」로 수행**했다.
  환각을 막으려던 지시가 덤프를 불렀다.
- **C.** 검산이 「그 id 가 존재하나」만 봤다. **「그 사람이 그 말을 했나」는 아무도 안 봤다.**

그래서 방향을 뒤집었다. **「주제 → 사람」이 아니라 「사람 → 주제」다.**

1. **코드북(LLM 1회).** 전원 응답을 보고 **이름표 목록만** 만든다.
   이 스키마에는 `respondentIds` 칸이 **아예 없다** — 덤프할 자리가 존재하지 않는다.
2. **배정(LLM ⌈n/40⌉회).** 코드북을 고정한 채 응답자를 **한 명 한 줄로** 판정한다.
   `alternativeLabel` 이 단수라 「1인 1대안(합계 ≤ n)」이 구조로 강제된다.

언급 수는 코드가 이 배정표를 **뒤집어서** 만든다. LLM 은 여전히 숫자를 쓰지 않는다.

배정을 쪼개도 이름표는 안 갈린다 — 코드북이 고정이기 때문이다. 옛 1패스가 쪼개지 못했던
이유(주제 묶기는 전수를 한 번에 봐야 한다)는 1패스에만 남는다.

---

## 그런데 2패스로도 40/40 이 다시 났다 (2026-08-15, 유료 실측)

위 개편 뒤 **유료로 재 본 적이 없었다.** 원장(`20260815T053154Z`, n=40)을 처음 남기고
**응답을 그대로 둔 채 코딩만 세 번** 돌리자 결과가 이렇게 갈렸다.

| 판 | 나온 것 |
|---|---|
| 1 | CONCERN·DIFFERENTIATION·USAGE_SCENE·BARRIER·SUGGESTION **다섯 축이 0명** |
| 2 | BARRIER 31 · CONCERN 25 · USAGE_SCENE 5 |
| 3 | LIKE **40/40** · USAGE_SCENE **40/40** · SUGGESTION 39/40 |

응답 원문은 셋 다 같았고 **완전중복 0건 · 40명 전원이 공유하는 어절 0개**였다.
즉 **갈리는 것은 응답이 아니라 코딩이다.** 「전원 일치」는 그 흔들림의 한쪽 끝이었고,
반대쪽 끝은 「아무도 말하지 않았다」였다.

원인 둘을 찾아 고쳤다.

- **D. 코드북이 축을 통째로 빼먹었다.** 빈 축은 배정이 무엇을 골라도 `verify` 가 전부
  버린다(코드북에 없는 이름표라서) — 한 판에서 **208개**가 그렇게 사라졌다.
  → 규칙 3(여섯 축 전부) · 빠지면 **1회 재시도**(`_missing_axes`).
- **E. 한 호출에 40명을 넣으면 배정이 무너진다.** 앞줄을 베끼거나(3판) 통째로 포기한다(1판).
  → `ASSIGN_BATCH` 40 → **8**.

같은 원장으로 넷을 다시 돌린 결과: **0명 축 0건 · 40/40 0건**, 축마다 이름표 2~6개.
그리고 버려진 이름표를 이제 **센다** — 조용히 사라지던 것이 로그에 남는다.
"""

import asyncio
import json
import logging
from dataclasses import dataclass
from typing import Literal

from pydantic import Field, ValidationError

from app.interview.models import AXES, COMPREHENSION, DIFFERENTIATION_VERDICTS, StrictModel
from app.providers import ProviderFailure, execute_structured_prompt

logger = logging.getLogger(__name__)

CODEBOOK_SCHEMA = "market_interview_codebook_v1"
ASSIGNMENT_SCHEMA = "market_interview_assignment_v1"

#: 이름표 대조에서 무시할 것 — 공백의 개수, 앞뒤 따옴표·마침표·가운뎃점.
_TRIM = " \t\"'`“”‘’.·,、。"


def _match_key(label: str) -> str:
    """배정이 낸 이름표를 코드북과 맞춰 보기 위한 열쇠.

    **왜 글자 그대로 대조하지 않나.** 예전에는 `label in known_labels` 였고, 배정이 마침표
    하나만 더 붙여도 그 이름표가 **소리 없이** 버려졌다. 한 축의 이름표가 다 그렇게 밀리면
    **그 축이 통째로 0명**이 되어 화면에는 「아무도 그 말을 하지 않았다」로 나왔다.
    2026-08-15 실측에서 그 모양이 재현됐다 — 다섯 축이 한꺼번에 0이었다.

    ⚠ **뜻이 비슷한 것까지 붙여 주지는 않는다.** 여기서 지우는 것은 공백과 문장부호뿐이다.
    그 이상을 하면 서로 다른 주제가 한 이름표로 합쳐지고, 그것이야말로 이 조사가 막으려는
    「뭉개기」다.
    """
    return " ".join(label.strip(_TRIM).split()).strip(_TRIM)

#: 축마다 만들 이름표 수의 상한. 너무 잘게 쪼개면 「1명이 말한 주제」만 늘어선다.
LABELS_PER_AXIS_MAX = 6
THEMES_MAX = LABELS_PER_AXIS_MAX * len(AXES)          # 36
ALTERNATIVES_MAX = 8
MISREAD_MAX = 8
#: 코딩 프롬프트에 싣는 답 하나의 길이 상한. 인용문은 원문에서 다시 꺼내므로 여기서만 자른다.
FIELD_MAX_CHARS = 300
#: 배정 1회에 넣는 응답자 수. 출력도 n 에 비례하므로 한 호출이 길어지지 않게 자른다.
#:
#: ⚠ **40 이었다. 그 크기에서 배정이 재현되지 않았다.** 같은 응답 40명분을 그대로 두고
#: 배정만 세 번 돌린 실측(2026-08-15, 원장 `20260815T053154Z`):
#:
#:   1판 — CONCERN·DIFFERENTIATION·USAGE_SCENE·BARRIER·SUGGESTION **다섯 축이 통째로 0명**
#:   2판 — BARRIER 31 · CONCERN 25 · USAGE_SCENE 5
#:   3판 — LIKE 40/40 · USAGE_SCENE 40/40 · SUGGESTION 39/40  ← 「전원 일치」가 재현됐다
#:
#: 응답 원문은 셋 다 같았고 **완전중복 0건 · 40명 전원이 공유하는 어절 0개**였다. 즉 갈리는
#: 것은 응답이 아니라 배정이다. 한 호출에 40명을 넣으면 모델이 앞줄을 베끼거나(3판) 통째로
#: 포기한다(1판). 「40명이 다 같은 말을 한다」는 그 흔들림의 한쪽 끝이었다.
ASSIGN_BATCH = 8
#: 한 사람이 한 축에서 고를 수 있는 이름표 수. 여기서도 덤프를 막는다.
LABELS_PER_RESPONDENT_MAX = 3

# 코딩에 쓰는 칸. `firstImpression` 은 뺀다 — 무편집 첫반응은 묶을 대상이 아니라
# 대표 응답자 카드에 그대로 실을 것이고, 넣으면 프롬프트만 커진다.
CODED_FIELDS = ("restatement", "like", "concern", "differentiation",
                "relevance", "usageScene", "barrier", "suggestion")

#: 배정표의 축별 이름표 칸 ← 축 이름.
AXIS_SLOT = {"LIKE": "likeLabels", "CONCERN": "concernLabels",
             "DIFFERENTIATION": "differentiationLabels",
             "USAGE_SCENE": "usageSceneLabels", "BARRIER": "barrierLabels",
             "SUGGESTION": "suggestionLabels"}

CODEBOOK_PROMPT = """너는 정성 조사의 코더다. 응답자들이 상품 설명 하나를 보고 남긴 답을 읽고,
비슷한 이야기끼리 묶을 **이름표 목록**을 만든다. 한국어로만 쓴다.

이 단계에서 하는 일은 이름표를 짓는 것뿐이다. **누가 무슨 말을 했는지는 다음 단계에서
정한다** — 여기서는 응답자 id 를 쓰지 않는다.

규칙 — 어기면 그 항목은 버려진다.
1. 이름표(label)는 그 사람들이 실제로 말한 것을 짧게 요약한 명사구다.
   «가격이 비싸다», «설치가 번거로울 것 같다» 처럼 구체적으로 쓴다.
   «부정적 의견», «기타» 같은 뭉뚱그린 이름표는 쓰지 않는다.
2. 축(axis)을 지킨다 — like 에서 나온 이야기는 LIKE, concern 은 CONCERN,
   differentiation 은 DIFFERENTIATION, usageScene 은 USAGE_SCENE,
   barrier 는 BARRIER, suggestion 은 SUGGESTION 으로만 묶는다.
3. **여섯 축 전부에 이름표를 만든다.** LIKE·CONCERN·DIFFERENTIATION·USAGE_SCENE·BARRIER·
   SUGGESTION 중 하나라도 비우면 안 된다. 비운 축은 「아무도 그런 말을 하지 않았다」로
   화면에 나가는데, 응답자들은 그 칸에도 답을 썼다. 어떤 축이든 답이 있으면 이름표가 있다.
4. **한 축에 이름표를 하나만 만들지 마라.** 답이 갈리는 결을 찾아 나눈다.
   정말로 전원이 똑같은 말만 했다면 하나여도 되지만, 그것은 드문 일이다.
5. 같은 축 안에서 이름표가 서로 겹치지 않게 한다.
6. 축마다 최대 6개까지다. 많이 나온 이야기부터 놓는다.

alternatives — relevance 답에서 «지금은 이렇게 해결한다»고 말한 것의 이름표다.
아무것도 안 하고 있다는 답(«그냥 참는다», «해본 적 없다»)도 하나의 이름표로 만든다.

misreadPoints — restatement(제품을 본인 말로 설명한 것)를 상품 설명과 대조해,
**어디를 잘못 읽었는지**를 문장으로 적는다. 오해가 없으면 빈 배열이다."""

ASSIGNMENT_PROMPT = """너는 정성 조사의 코더다. 이미 만들어진 **이름표 목록(코드북)** 을 받고,
응답자를 **한 명씩** 읽어 그 사람에게 해당하는 이름표를 고른다. 한국어로만 쓴다.

규칙 — 어기면 그 항목은 버려진다.
1. **코드북에 있는 이름표만 쓴다.** 새로 짓지 않는다. 글자 그대로 옮긴다.
2. **그 사람이 실제로 쓴 문장에 근거가 있을 때만 이름표를 붙인다.**
   애매하면 붙이지 않는다. 아무 이름표도 안 붙는 사람이 있어도 된다.
   전원에게 같은 이름표를 붙이는 것은 거의 항상 틀린 답이다.
3. 한 축에 이름표는 **최대 3개**다. 그 사람이 가장 분명하게 말한 것부터 고른다.
4. 받은 응답자 **전원을 정확히 한 줄씩** 낸다. id 를 지어내지 않는다.

comprehension — restatement 를 상품 설명과 대조한다. 셋 중 하나다.
· accurate: 이 제품이 무엇을 해주는지 제대로 옮겼다
· partial: 반은 맞고 반은 비었다 — 핵심 기능 하나를 빠뜨렸거나 대상을 잘못 짚었다
· misunderstood: 다른 물건으로 이해했다

differentiationVerdict — differentiation 답을 읽고 셋 중 하나다.
· different: 기존 것과 다른 점이 있다고 말했다
· similar: 별로 다르지 않다 / 비슷하다고 말했다
· unclear: 모르겠다고 했거나 판단할 수 없는 답이다

barrierResolved — barrier 답에서 «그 걸림돌이 없어지면 사겠다»는 뜻을 **직접 말한** 경우만
true 다. **추측하지 마라.** 말하지 않았으면 false 다.

  조건을 달아 사겠다고 한 것은 **전부 true** 다. 이런 모양이면 true 다:
  · «가격만 좀 더 싸면 살 것 같아요»
  · «~하다면 고려해 볼 만해요» / «~면 사겠어요»
  · «지금은 필요 없지만, 나중에 ~하면 살 것 같아요»
  · «~만 해결되면 괜찮을 것 같은데»
  반대로 이런 모양이면 false 다:
  · «비싸서 안 살 거예요» (조건 없이 거절)
  · «저한테는 필요 없는 물건이에요» (조건 없이 무관)
  · «잘 모르겠어요»
  ⚠ 앞의 문장이 부정적이어도 **뒤에 조건이 붙어 있으면 true** 다. 「안 살 것 같지만
  ~라면 사겠다」는 true 다 — 이 조사가 가장 알고 싶어 하는 것이 그 «~라면»이다.

alternativeLabel — 그 사람의 **주된 대안 하나**다. 코드북의 alternatives 중 하나를 그대로
쓰고, 해당하는 것이 없으면 빈 문자열로 둔다. **여러 개를 쓸 수 없다.**"""


class CodebookTheme(StrictModel):
    axis: Literal["LIKE", "CONCERN", "DIFFERENTIATION", "USAGE_SCENE", "BARRIER", "SUGGESTION"]
    label: str = Field(min_length=1, max_length=80)


class CodebookAlternative(StrictModel):
    label: str = Field(min_length=1, max_length=80)


class Codebook(StrictModel):
    """1패스 산출. **`respondentIds` 칸이 없다** — 덤프할 자리를 만들지 않는 것이 설계다."""

    themes: list[CodebookTheme] = Field(max_length=THEMES_MAX)
    alternatives: list[CodebookAlternative] = Field(max_length=ALTERNATIVES_MAX)
    misreadPoints: list[str] = Field(max_length=MISREAD_MAX)


class Assignment(StrictModel):
    """응답자 한 명 = 한 줄. 기본값을 두지 않는다(strict json_schema 가 전부 required)."""

    id: str = Field(min_length=1, max_length=12)
    comprehension: Literal["accurate", "partial", "misunderstood"]
    differentiationVerdict: Literal["different", "similar", "unclear"]
    barrierResolved: bool
    likeLabels: list[str] = Field(max_length=LABELS_PER_AXIS_MAX)
    concernLabels: list[str] = Field(max_length=LABELS_PER_AXIS_MAX)
    differentiationLabels: list[str] = Field(max_length=LABELS_PER_AXIS_MAX)
    usageSceneLabels: list[str] = Field(max_length=LABELS_PER_AXIS_MAX)
    barrierLabels: list[str] = Field(max_length=LABELS_PER_AXIS_MAX)
    suggestionLabels: list[str] = Field(max_length=LABELS_PER_AXIS_MAX)
    #: 주된 대안 하나. 해당 없으면 빈 문자열. **단수인 것이 계약의 핵심이다.**
    alternativeLabel: str = Field(max_length=80)


class AssignmentBatch(StrictModel):
    assignments: list[Assignment] = Field(max_length=ASSIGN_BATCH)


@dataclass(frozen=True)
class CodedResponses:
    """검산까지 끝난 코딩 결과. 세는 것은 이 뒤(`__init__`)가 한다."""

    #: `[{axis, label, respondentIds}]` — 축 안에서 코드북 순서를 유지한다.
    themes: list[dict]
    #: `[{label, respondentIds}]`. 1인 1대안이라 언급 수 합계 ≤ 응답자 수다.
    alternatives: list[dict]
    #: `{accurate|partial|misunderstood: [id]}` — 1인 1칸.
    comprehension: dict[str, list[str]]
    #: `{different|similar|unclear: [id]}` — 1인 1칸.
    differentiation: dict[str, list[str]]
    #: 「걸림돌이 없어지면 사겠다」고 **말한** 사람.
    barrierResolvedIds: list[str]
    misreadPoints: list[str]
    #: 배정표 원본(검산 후). 원장에 그대로 싣는다.
    assignments: dict[str, dict]
    llmCalls: int


def _order(rid: str) -> int:
    """`R12` → 12. 순서를 번호로 세운다 — LLM 이 준 순서를 믿으면 인용문이 흔들린다."""
    try:
        return int(rid[1:])
    except (ValueError, IndexError):
        return 10**9


def _trimmed(answers: dict[str, dict], rid: str) -> dict:
    return {field: (answers[rid].get(field) or "")[:FIELD_MAX_CHARS] for field in CODED_FIELDS}


def _codebook_message(board_text: str, answers: dict[str, dict]) -> str:
    """id 를 **싣지 않는다.** 이 단계는 이름표만 짓는 자리라 id 가 있으면 유혹만 된다."""
    payload = {"productDescription": board_text,
               "responses": [_trimmed(answers, rid) for rid in sorted(answers, key=_order)]}
    return json.dumps(payload, ensure_ascii=False)


def _assignment_message(board_text: str, codebook: Codebook, batch: list[str],
                        answers: dict[str, dict]) -> str:
    payload = {
        "productDescription": board_text,
        "codebook": {
            "themes": [{"axis": t.axis, "label": t.label} for t in codebook.themes],
            "alternatives": [a.label for a in codebook.alternatives],
        },
        "responses": [{"id": rid, **_trimmed(answers, rid)} for rid in batch],
    }
    return json.dumps(payload, ensure_ascii=False)


async def _codebook(board_text: str, answers: dict[str, dict], timeout: float) -> Codebook:
    raw = await execute_structured_prompt(
        CODEBOOK_PROMPT, _codebook_message(board_text, answers),
        response_schema=Codebook.model_json_schema(), schema_name=CODEBOOK_SCHEMA,
        task_type="MARKET_INTERVIEW", timeout_seconds_override=timeout)
    try:
        book = Codebook.model_validate(raw)
    except ValidationError as failure:
        raise ProviderFailure("RESULT_SCHEMA_INVALID", "AI_RESULT_INVALID", 502, False) from failure
    return book


def _missing_axes(book: Codebook) -> list[str]:
    """이름표가 **하나도 없는** 축. 비어 있으면 그 축은 화면에서 통째로 사라진다.

    배정은 코드북에 없는 이름표를 쓸 수 없고(`verify` 가 버린다), 그래서 빈 축의 배정은
    한 건도 살아남지 못한다. 원인이 배정이 아니라 **여기**라는 것을 이름으로 남긴다.
    """
    return [axis for axis in AXES if not any(t.axis == axis for t in book.themes)]


async def _assign(board_text: str, codebook: Codebook, batch: list[str],
                  answers: dict[str, dict], timeout: float) -> list[Assignment]:
    raw = await execute_structured_prompt(
        ASSIGNMENT_PROMPT, _assignment_message(board_text, codebook, batch, answers),
        response_schema=AssignmentBatch.model_json_schema(), schema_name=ASSIGNMENT_SCHEMA,
        task_type="MARKET_INTERVIEW", timeout_seconds_override=timeout)
    try:
        return AssignmentBatch.model_validate(raw).assignments
    except ValidationError as failure:
        raise ProviderFailure("RESULT_SCHEMA_INVALID", "AI_RESULT_INVALID", 502, False) from failure


def verify(codebook: Codebook, rows: list[Assignment],
           answers: dict[str, dict]) -> CodedResponses:
    """배정표를 검산하고 **뒤집는다**. LLM 호출 0회 — 여기가 40/40 을 막는 마지막 문이다.

    버리는 것: 모르는 id · 같은 id 두 번째 줄 · 코드북에 없는 이름표 ·
    한 축에서 3개를 넘는 이름표 · 코드북에 없는 대안.
    """
    known_labels = {axis: [] for axis in AXES}
    # 이름표 → 코드북 정본. **글자 그대로 일치를 요구하지 않는다** — 이유는 `_match_key` 참조.
    label_index: dict[str, dict[str, str]] = {axis: {} for axis in AXES}
    for theme in codebook.themes:
        clean = theme.label.strip()
        if clean and clean not in known_labels[theme.axis]:
            known_labels[theme.axis].append(clean)
            label_index[theme.axis].setdefault(_match_key(clean), clean)
    known_alternatives = []
    alternative_index: dict[str, str] = {}
    for alternative in codebook.alternatives:
        clean = alternative.label.strip()
        if clean and clean not in known_alternatives:
            known_alternatives.append(clean)
            alternative_index.setdefault(_match_key(clean), clean)
    dropped = 0                       # 코드북에 못 붙은 이름표. **세서 드러낸다.**

    members: dict[tuple[str, str], list[str]] = {}
    alt_members: dict[str, list[str]] = {label: [] for label in known_alternatives}
    comprehension: dict[str, list[str]] = {bucket: [] for bucket in COMPREHENSION}
    differentiation: dict[str, list[str]] = {v: [] for v in DIFFERENTIATION_VERDICTS}
    resolved: list[str] = []
    table: dict[str, dict] = {}

    for row in rows:
        rid = row.id.strip()
        if rid not in answers or rid in table:
            continue                                   # 모르는 id · 중복 줄은 버린다
        comprehension[row.comprehension].append(rid)
        differentiation[row.differentiationVerdict].append(rid)
        if row.barrierResolved:
            resolved.append(rid)

        chosen: dict[str, list[str]] = {}
        for axis, slot in AXIS_SLOT.items():
            picked: list[str] = []
            for label in getattr(row, slot):
                canonical = label_index[axis].get(_match_key(label))
                if canonical is None:
                    if label.strip():
                        dropped += 1
                    continue
                if canonical not in picked:
                    picked.append(canonical)
                if len(picked) >= LABELS_PER_RESPONDENT_MAX:
                    break
            chosen[axis] = picked
            for label in picked:
                members.setdefault((axis, label), []).append(rid)

        alternative = alternative_index.get(_match_key(row.alternativeLabel), "")
        if not alternative and row.alternativeLabel.strip():
            dropped += 1                               # 지어낸 대안은 「해당 없음」으로 접는다
        if alternative:
            alt_members[alternative].append(rid)

        table[rid] = {"comprehension": row.comprehension,
                      "differentiationVerdict": row.differentiationVerdict,
                      "barrierResolved": row.barrierResolved,
                      "alternativeLabel": alternative,
                      **{AXIS_SLOT[axis]: chosen[axis] for axis in AXES}}

    themes = [{"axis": axis, "label": label,
               "respondentIds": sorted(members[(axis, label)], key=_order)}
              for axis in AXES for label in known_labels[axis]
              if members.get((axis, label))]
    alternatives = [{"label": label, "respondentIds": sorted(alt_members[label], key=_order)}
                    for label in known_alternatives if alt_members[label]]

    # 버린 것은 반드시 드러낸다. 이 수가 크면 배정이 코드북 밖으로 새고 있다는 뜻이고,
    # 그때 화면의 「n명 중 x명」은 실제보다 **작다**. 조용히 지나가면 그 사실을 알 길이 없다.
    if dropped:
        logger.warning("market interview coding dropped %d label pick(s) not in the codebook",
                       dropped)

    return CodedResponses(
        themes=themes, alternatives=alternatives,
        comprehension={k: sorted(v, key=_order) for k, v in comprehension.items()},
        differentiation={k: sorted(v, key=_order) for k, v in differentiation.items()},
        barrierResolvedIds=sorted(resolved, key=_order),
        misreadPoints=[p.strip() for p in codebook.misreadPoints if p.strip()][:MISREAD_MAX],
        assignments=table, llmCalls=0)


async def code_responses(board_text: str, answers: dict[str, dict],
                         timeout_seconds: float) -> CodedResponses:
    """코드북 1회 + 배정 ⌈n/40⌉회. 실패하면 조사 전체가 실패한다 — 코딩 없는 결과는 답 뭉치다."""
    ordered = sorted(answers, key=_order)
    batches = [ordered[start:start + ASSIGN_BATCH]
               for start in range(0, len(ordered), ASSIGN_BATCH)]

    # 코드북에 예산의 절반, 배정에 나머지. 배정은 동시에 돌리므로 각자 그 나머지를 다 쓴다.
    #
    # ⚠ **축을 빼먹은 코드북이면 한 번 다시 묻는다.** 빈 축은 배정이 무엇을 골라도 전부
    #   버려지게 만들고(코드북에 없는 이름표라서), 화면에는 「아무도 그런 말을 하지 않았다」가
    #   뜬다. 2026-08-15 실측에서 다섯 축이 한꺼번에 빈 코드북이 **두 번 중 한 번** 나왔다 —
    #   그 판의 배정 208개가 통째로 버려졌다. 재시도 1회는 그 절반을 걷어낸다.
    #   두 번째도 비면 그대로 간다. 여기서 죽이면 「답이 정말 없는 축」까지 조사를 잃는다.
    codebook = await _codebook(board_text, answers, timeout_seconds / 4)
    calls = 1
    if _missing_axes(codebook):
        logger.warning("market interview codebook retried — empty axes: %s",
                       ",".join(_missing_axes(codebook)))
        codebook = await _codebook(board_text, answers, timeout_seconds / 4)
        calls += 1
    results = await asyncio.gather(*(
        _assign(board_text, codebook, batch, answers, timeout_seconds / 2) for batch in batches))

    rows = [row for batch in results for row in batch]
    coded = verify(codebook, rows, answers)
    return CodedResponses(**{**coded.__dict__, "llmCalls": calls + len(batches)})
