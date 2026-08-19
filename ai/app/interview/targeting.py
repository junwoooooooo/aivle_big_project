"""타겟 / 비타겟 뱅크 사전 필터.

컨셉보드의 `targetUsers` 는 자유 서술이고 표집틀(`twin_frame.csv`)에는 `pid_hash·gender·band`
세 칸뿐이다. 그래서 두 단계를 거친다.

1. **조건식 변환 (LLM 1회).** 자유 서술 → 프로필 술어. LLM 은 **조건만** 만들고 판정은
   코드가 한다. 조건식은 결과 봉투에 그대로 실어 화면에 보인다 — 기계가 옮긴 것이라
   틀릴 수 있고, 틀렸는지는 사용자만 안다.
2. **뱅크 2분할 + 각각 층화 추출.** 타겟 8 : 비타겟 2. 비타겟을 남기는 이유는 대비를 보기
   위해서다 — 타겟 밖에서 의외의 반응이 나오면 그것이 타겟을 다시 그릴 근거가 된다.

**⚠ 표집 재현성이 여기서 약해진다.** `bank.stratified_sample` 이 난수를 안 쓰는 이유는
「조사 간 비교가 사람 교체가 아니라 자극 차이만 반영하게」 하려는 것이었다. 조건식을 LLM 이
만들므로 같은 사업안을 두 번 조사해도 표집틀이 갈릴 수 있다. 그래서 조건식 호출은 좁은
구조화 스키마로 묶고, 나온 조건식을 봉투에 박아 둔다 — 두 판이 갈리면 왜 갈렸는지는 보인다.

**프로필을 못 읽은 칸은 조건을 통과시키지 않는다.** 확인할 수 없는 것을 타겟으로 세면
타겟 표본이 조용히 오염된다. 뱅크 8,604장의 6필드 실측 커버리지는 100% 라 실제로 드물다.

---

## 2026-08-15 — 타겟 0명이 조용히 지나간 판

「초등 저학년 자녀를 둔 맞벌이 부모」가 `가구원수 3~3 + jobKeywords ["맞벌이","부모"]` 로
옮겨졌고, **뱅크 8,595장에 「맞벌이」는 0회 나온다.** 조건에 맞는 사람이 0명이라 40명
전원이 조건 밖에서 채워졌는데 **화면 경고는 0건**이었다 — `shortfall = size - drawn` 이
비타겟으로 채운 뒤엔 언제나 0이기 때문이다.

원인이 둘이라 둘 다 고쳤다.

- **거를 칸이 없었다.** 「자녀를 둔」을 표현할 칸이 여섯 개 중에 하나도 없어 LLM 이
  직업 키워드에 밀어 넣었다 → `hasChildren` · `householdRoles` 신설.
  ★ **둘은 한 쌍이다** — 자녀 있는 가구 5,919장 중 **1,611장이 그 집 «자녀 본인»**이라
  세대구성만 보면 22세 자녀가 부모 타겟에 들어간다(`twin/profile.PARENT_ROLES`).
- **몇 명에 맞았는지를 아무도 안 셌다.** → `criteria_text` 가 **조건 축마다 적중 수와
  교집합을 함께 적는다**. 봉투 칸을 늘리지 않는다 — `criteriaText` 는 자유 문자열이고,
  칸을 늘리면 AI·Java·프론트 3층과 골든이 같이 움직인다.
"""

import math
import re

from pydantic import Field, ValidationError

from app.interview.models import StrictModel
from app.providers import ProviderFailure, execute_structured_prompt
from app.twin.bank import stratified_sample
from app.twin.profile import HOUSEHOLD_ROLES, PARENT_ROLES, parse_target_facts

__all__ = ["TargetCriteria", "draw_split", "matches", "criteria_text",
           "condition_matches", "has_conditions"]

CRITERIA_SCHEMA = "market_interview_target_criteria_v1"
#: 타겟 : 비타겟 = 8 : 2.
TARGET_SHARE = 0.8

_HOUSEHOLD_SIZE = re.compile(r"(\d{1,2})인 가구")

#: 패널에 실제로 적혀 있는 직업 이름 전부(실측 91종 + 「무직」). **프롬프트에 그대로 싣는다.**
#:
#: ⚠ 이 목록이 없으면 모델이 패널에 없는 말을 지어낸다. 2026-08-15 실측에서
#: 「맞벌이 부모」가 `jobKeywords ["맞벌이","부모"]` 로 나왔고, 뱅크 8,595장에 「맞벌이」는
#: **0회** 나온다 — 그 조건은 무엇을 해도 0명이라 조사 전체가 헛돌았다.
JOB_VOCABULARY = (
    "전업주부 · 무직 · 학생 · 군인 · 매장 판매 및 상품 대여직 · 매장 판매직 · 영업직 · "
    "기획·영업 및 인사 사무직 · 회계·경리 및 통계 사무직 · 일반 지원 사무직 · "
    "자재·생산 및 운송 사무직 · 경영 및 회계 관련 사무직 · 금융 사무직 · 금융 및 보험 사무직 · "
    "상담·안내 및 접수 사무직 · 상담·안내·통계 및 기타 사무직 · 법률·감사 및 정부 행정 사무직 · "
    "법률 및 감사 사무직 · 조리 및 음식 서비스직 · 개인 생활 서비스직 · "
    "돌봄 및 보건 서비스직 · 운송 및 여가 서비스직 · 이미용·예식 및 의료보조 서비스직 · "
    "경찰·소방 및 보안 관련 서비스직 · 통신 및 방문·노점 판매 관련직 · "
    "청소 및 건물 관리 단순 노무직 · 청소 및 경비 관련 단순노무직 · "
    "가사·음식 및 판매 관련 단순 노무직 · 제조 관련 단순 노무직 · 운송 관련 단순 노무직 · "
    "건설 및 광업 관련 단순 노무직 · 농림어업 및 기타 서비스 단순 노무직 · "
    "농축산 숙련직 · 어업 숙련직 · 임업 숙련직 · "
    "교육 전문가 및 관련직 · 보건 전문가 및 관련직 · 사회복지·종교 전문가 및 관련직 · "
    "보건·사회복지 및 종교 관련직 · 공학 전문가 및 기술직 · 과학 전문가 및 관련직 · "
    "정보 통신 전문가 및 기술직 · 경영·금융 전문가 및 관련직 · 법률 및 행정 전문직 · "
    "문화·예술·스포츠·기타 전문가 및 관련직 · "
    "행정·경영 지원 및 마케팅 관리직 · 행정 및 경영지원 관리직 · 판매 및 고객 서비스 관리직 · "
    "전문 서비스 관리직 · 건설·전기 및 생산 관련 관리직 · 의회·정부 및 기업 고위직 · "
    "공공 및 기업 고위직 · "
    "기계 제조·관련 기계 조작 및 조립직 · 운전 및 운송 관련 기계 조작직 · 운전 및 운송 관련직 · "
    "전기·전자 관련 기계 조작 및 조립직 · 금속 및 비금속 관련 기계 조작직 · "
    "섬유 및 신발 관련 기계 조작직 · 식품가공 관련 기계 조작직 · 화학 관련 기계 조작직 · "
    "목재·인쇄 및 기타 기계 조작직 · 상하수도 및 재활용 처리 관련 기계 조작직 · "
    "전기 및 전자 관련 기능직 · 운송 및 기계 관련 기능직 · 건설 및 채굴 관련 기능직 · "
    "섬유·의복 및 가죽 관련 기능직 · 식품 가공 관련 기능직 · 금속 성형 관련 기능직 · "
    "목재·가구·악기 및 간판 관련 기능직 · 정보 통신 및 방송장비 관련 기능직 · "
    "영상 및 통신 장비 관련 기능직 · 기타 기능 관련직"
)

CRITERIA_PROMPT = f"""너는 조사 표본을 설계하는 사람이다. 상품의 「누구를 위한 것인가」 설명을 읽고,
응답자 패널에서 그 대상을 **거를 수 있는 조건**으로 옮긴다.

패널에 대해 알 수 있는 것은 아래 아홉 가지뿐이다.
**이 아홉 가지로 표현할 수 없는 조건은 만들지 마라.** "요리를 자주 하는 사람",
"환경에 관심 있는 사람" 같은 행동·태도 조건은 **낼 수 없다** — 그런 칸이 패널에 없다.
그런 조건뿐이라면 전부 비워서 「누구나」로 둔다.

**넓게 잡아라.** 조건을 좁히면 표본이 마르고, 마른 표본은 조사가 아니라 일화가 된다.
설명에 없는 조건을 상상해서 덧붙이지 마라.

- ageMin / ageMax: 만 나이. **모르면 0** 을 넣는다(0 은 「제한 없음」이다).
- genders: "남성" 또는 "여성" 만 쓴다. 상관없으면 빈 배열.
- householdSizeMin / householdSizeMax: 가구원 수. 모르면 0.
- regions: "서울", "경기" 처럼 광역 이름만. 상관없으면 빈 배열.
- incomeKeywords: **개인** 월소득 구간 표기에 들어갈 말(예: "300", "400"). 확실하지 않으면 빈 배열.
- hasChildren: 자녀와 함께 사는 가구인가. 1=자녀 있음 · 2=자녀 없음 · **0=상관없음**.
- householdRoles: 그 사람이 가구 안에서 누구인가. 아래 넷 중에서만 고른다 —
  "가구주" · "가구주의 배우자" · "가구주의 자녀" · "부모". 상관없으면 빈 배열.
- jobKeywords: 아래 «직업 목록»에 **실제로 들어 있는 말**만 쓴다. 확실하지 않으면 빈 배열.

★★ **「자녀를 둔 부모」는 두 칸을 «함께» 걸어야 한다** —
`hasChildren=1` **그리고** `householdRoles=["가구주","가구주의 배우자"]`.
`hasChildren` 만 걸면 그 집에 얹혀 사는 **자녀 본인**(예: 22세 대학생)이 「부모」로 뽑힌다.
실제로 자녀가 있는 가구 5,919가구 중 1,611가구의 응답자가 그 집 «자녀»다.

⚠ **패널에 없는 것 — 조건으로 만들 수 없다.**
- **자녀의 나이·학년.** 「초등학생 자녀」·「영유아 자녀」는 거를 수 없다. 자녀가 있다는
  것까지만 걸고 나이는 포기한다.
- **맞벌이 여부.** 카드는 한 사람 것이라 배우자가 버는지는 알 수 없다.
  「맞벌이」를 jobKeywords 에 넣지 마라 — 그런 말이 패널에 한 번도 나오지 않아 0명이 된다.
- **부모·학부모 같은 «역할» 이름.** 그것은 jobKeywords 가 아니라 위 두 칸으로 표현한다.

«직업 목록» — jobKeywords 는 여기 있는 말의 일부여야 한다:
{JOB_VOCABULARY}"""


class TargetCriteria(StrictModel):
    """프로필 술어. 축끼리는 AND, 한 축 안의 목록은 OR. **0 과 빈 배열이 「제한 없음」이다.**

    `int | None` 을 쓰지 않는 것은 OpenAI strict json_schema 에서 nullable 정수가 공급자마다
    다르게 처리되기 때문이다. 0 을 센티널로 두는 쪽이 계약이 단순하다.
    """

    ageMin: int = Field(ge=0, le=120)
    ageMax: int = Field(ge=0, le=120)
    genders: list[str] = Field(max_length=2)
    householdSizeMin: int = Field(ge=0, le=20)
    householdSizeMax: int = Field(ge=0, le=20)
    regions: list[str] = Field(max_length=20)
    incomeKeywords: list[str] = Field(max_length=10)
    jobKeywords: list[str] = Field(max_length=15)
    #: 1=자녀 있음 · 2=자녀 없음 · 0=상관없음. **`householdRoles` 와 한 쌍으로 쓴다** —
    #: 이것만 걸면 그 집 자녀 본인이 「부모」로 뽑힌다(`twin/profile.PARENT_ROLES`).
    hasChildren: int = Field(ge=0, le=2)
    #: `twin/profile.HOUSEHOLD_ROLES` 넷 중에서만. 빈 배열이 「제한 없음」이다.
    householdRoles: list[str] = Field(max_length=4)


def _household_size(facts: dict):
    match = _HOUSEHOLD_SIZE.search(facts.get("household") or "")
    return int(match.group(1)) if match else None


def _age_ok(facts: dict, criteria: TargetCriteria) -> bool:
    age = facts.get("age")
    if not isinstance(age, int):
        return False
    if criteria.ageMin and age < criteria.ageMin:
        return False
    return not (criteria.ageMax and age > criteria.ageMax)


def _size_ok(facts: dict, criteria: TargetCriteria) -> bool:
    size = _household_size(facts)
    if size is None:
        return False
    if criteria.householdSizeMin and size < criteria.householdSizeMin:
        return False
    return not (criteria.householdSizeMax and size > criteria.householdSizeMax)


def _substring(facts: dict, key: str, words: list[str]) -> bool:
    value = facts.get(key) or ""
    return any(word in value for word in words if word)


def _age_text(criteria: TargetCriteria) -> str:
    if criteria.ageMin and criteria.ageMax:
        return f"만 {criteria.ageMin}~{criteria.ageMax}세"
    if criteria.ageMin:
        return f"만 {criteria.ageMin}세 이상"
    return f"만 {criteria.ageMax}세 이하"


def _size_text(criteria: TargetCriteria) -> str:
    if criteria.householdSizeMin and criteria.householdSizeMax:
        return f"{criteria.householdSizeMin}~{criteria.householdSizeMax}인 가구"
    if criteria.householdSizeMin:
        return f"{criteria.householdSizeMin}인 이상 가구"
    return f"{criteria.householdSizeMax}인 이하 가구"


def conditions(criteria: TargetCriteria) -> list[tuple]:
    """**걸린 조건만** `(보일 이름, 판정 함수)` 로. 조건이 하나도 없으면 빈 목록이다.

    ★ **`matches` 와 `criteria_text` 와 조건별 적중 수가 이 하나를 같이 쓴다.**
    나누어 두면 갈린다 — 이 저장소에서 「베낀 조회가 갈라진」 일이 이미 세 번 있었다.
    조건을 하나 더할 때 고치는 자리도 여기 하나뿐이다.

    판정 규율은 그대로다: **못 읽은 칸은 그 축에 조건이 있을 때 통과하지 못한다.**
    """
    rules: list[tuple] = []
    if criteria.ageMin or criteria.ageMax:
        rules.append((_age_text(criteria), lambda f: _age_ok(f, criteria)))
    if criteria.genders:
        rules.append((" 또는 ".join(criteria.genders),
                      lambda f: (f.get("gender") or "") in criteria.genders))
    if criteria.householdSizeMin or criteria.householdSizeMax:
        rules.append((_size_text(criteria), lambda f: _size_ok(f, criteria)))
    if criteria.hasChildren:
        want = criteria.hasChildren == 1
        rules.append(("자녀와 함께 사는 가구" if want else "자녀와 함께 살지 않는 가구",
                      lambda f: f.get("hasChildren") is want))
    if criteria.householdRoles:
        rules.append(("가구 안 지위 " + " · ".join(criteria.householdRoles),
                      lambda f: f.get("householdRole") in criteria.householdRoles))
    if criteria.regions:
        rules.append((" · ".join(criteria.regions),
                      lambda f: _substring(f, "region", criteria.regions)))
    if criteria.incomeKeywords:
        rules.append(("소득 " + " · ".join(criteria.incomeKeywords),
                      lambda f: _substring(f, "income", criteria.incomeKeywords)))
    if criteria.jobKeywords:
        rules.append(("직업에 " + " · ".join(f"'{word}'" for word in criteria.jobKeywords),
                      lambda f: _substring(f, "job", criteria.jobKeywords)))
    return rules


def has_conditions(criteria: TargetCriteria) -> bool:
    """조건이 하나라도 걸렸나. 「누구나」로 돌린 조사에 「타겟이 없다」고 말하지 않으려고 쓴다."""
    return bool(conditions(criteria))


def matches(facts: dict, criteria: TargetCriteria) -> bool:
    """축끼리 AND. 조건이 하나도 없으면 전원이 타겟이다."""
    return all(test(facts) for _name, test in conditions(criteria))


def condition_matches(cards: dict[str, str], criteria: TargetCriteria) -> list[dict]:
    """조건 축마다 **몇 명이 맞았나**, 그리고 **전부 동시에 만족한 사람 수**.

    ⚠ **교집합 줄이 이 함수의 존재 이유다.** 축마다 5,527명·4,308명이 떠도 동시에
    만족하는 사람은 0명일 수 있고, 2026-08-15 실측 판이 정확히 그 모양이었다.
    축별 수만 보이면 사용자는 「조건은 다 맞는데 왜 0명이지」로 **다시** 속는다.

    뱅크 전수를 한 번 돌지만 표집이 어차피 도는 자리라 추가 비용은 파싱 한 번뿐이다.
    """
    rules = conditions(criteria)
    if not rules:
        return []
    tally = [0] * len(rules)
    both = 0
    for text in cards.values():
        facts = parse_target_facts(text)
        hits = [test(facts) for _name, test in rules]
        for index, hit in enumerate(hits):
            tally[index] += hit
        both += all(hits)
    rows = [{"condition": name, "matched": tally[index]}
            for index, (name, _test) in enumerate(rules)]
    rows.append({"condition": "전부 동시에 만족", "matched": both})
    return rows


def criteria_text(criteria: TargetCriteria, matched: list[dict] | None = None) -> str:
    """화면에 그대로 보일 문구. 조건이 하나도 없으면 그렇다고 말한다.

    `matched`(`condition_matches` 의 산출)를 주면 **조건마다 몇 명이 맞았는지**와
    **교집합**을 같이 적는다. 봉투에 새 칸을 열지 않는 것이 의도다 — `criteriaText` 는
    이미 자유 문자열이라 계약 세 층과 골든을 건드리지 않고 이 정보를 화면까지 보낼 수 있다.
    """
    names = [name for name, _test in conditions(criteria)]
    if not names:
        return "조건 없음 — 패널 전체가 타겟이다"
    if not matched:
        return " / ".join(names)
    counts = {row["condition"]: row["matched"] for row in matched}
    head = " / ".join(f"{name}({counts.get(name, 0):,}명)" for name in names)
    return f"{head} → 전부 동시에 만족: {counts.get('전부 동시에 만족', 0):,}명"


async def resolve_criteria(target_users: str, problem_scenario: str,
                           timeout_seconds: float) -> TargetCriteria:
    """자유 서술 → 술어. 설명이 비어 있으면 **호출하지 않는다**(전원이 타겟이다)."""
    body = "\n".join(filter(None, [target_users.strip(), problem_scenario.strip()]))
    if not body:
        return TargetCriteria(ageMin=0, ageMax=0, genders=[], householdSizeMin=0,
                              householdSizeMax=0, regions=[], incomeKeywords=[],
                              jobKeywords=[], hasChildren=0, householdRoles=[])
    raw = await execute_structured_prompt(
        CRITERIA_PROMPT, body, response_schema=TargetCriteria.model_json_schema(),
        schema_name=CRITERIA_SCHEMA, task_type="MARKET_INTERVIEW",
        timeout_seconds_override=timeout_seconds)
    try:
        return TargetCriteria.model_validate(raw)
    except ValidationError as failure:
        raise ProviderFailure("RESULT_SCHEMA_INVALID", "AI_RESULT_INVALID", 502, False) from failure


def _merge(first: dict, second: dict, requested: int) -> dict:
    """두 분할의 표집 보고를 `sampling` 4칸으로 합친다 — 계약이 정확 집합이라 늘릴 수 없다."""
    strata: dict[str, int] = dict(first["strata"])
    for cell, count in second["strata"].items():
        strata[cell] = strata.get(cell, 0) + count
    short: dict[str, dict] = {}
    for report in (first, second):
        for cell, detail in report["shortCells"].items():
            merged = short.setdefault(cell, {"quota": 0, "available": 0})
            merged["quota"] += detail["quota"]
            merged["available"] += detail["available"]
    return {"requested": requested, "drawn": first["drawn"] + second["drawn"],
            "strata": dict(sorted(strata.items())), "shortCells": short}


def draw_split(cards: dict[str, str], frame: list[dict], size: int,
               criteria: TargetCriteria) -> tuple[list[dict], set, dict, dict]:
    """`(뽑힌 행, 타겟 pid 집합, sampling, targeting)`.

    타겟 프레임이 얕으면 **죽이지 않고** 부족분을 비타겟에서 채운 뒤 `shortfall` 에 남긴다.
    조건이 좁다는 것 자체가 읽어야 할 정보이지 실패가 아니다.
    """
    matched = condition_matches(cards, criteria)
    target_pids = {pid for pid in cards if matches(parse_target_facts(cards[pid]), criteria)}
    target_frame = [row for row in frame if row["pid_hash"] in target_pids]
    other_frame = [row for row in frame if row["pid_hash"] not in target_pids]

    wanted = math.ceil(size * TARGET_SHARE)
    target_size = min(wanted, len(target_frame))
    other_size = min(size - target_size, len(other_frame))

    target_rows, target_report = stratified_sample(target_frame, target_size)
    other_rows, other_report = stratified_sample(other_frame, other_size)

    drawn = sorted(target_rows + other_rows, key=lambda row: row["pid_hash"])
    targeting = {
        "criteria": criteria.model_dump(),
        # 조건별 적중 수와 교집합이 **이 문자열 안에** 실려 화면까지 간다.
        # 봉투 칸을 늘리지 않는 것이 의도다 — 늘리면 AI·Java·프론트·골든이 같이 움직인다.
        "criteriaText": criteria_text(criteria, matched),
        "targetRequested": wanted,
        "nonTargetRequested": size - wanted,
        "targetDrawn": len(target_rows),
        "nonTargetDrawn": len(other_rows),
        "shortfall": size - len(drawn),
        "targetShortCells": target_report["shortCells"],
        "nonTargetShortCells": other_report["shortCells"],
    }
    return drawn, {row["pid_hash"] for row in target_rows}, \
        _merge(target_report, other_report, size), targeting
