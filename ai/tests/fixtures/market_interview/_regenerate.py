"""골든 픽스처 재생성 — **유료 호출 0회, 결정론적.**

```
cd ai
python tests/fixtures/market_interview/_regenerate.py
```

`interview.json` 을 py·java·js **세 층이 함께 읽는다**. 그래서 손으로 쓰면 안 된다 —
`segments` 의 버킷 합이나 `suggestionLinks` 의 교집합 수를 사람이 적으면 코드가 내는 값과
조용히 어긋나고, 그러면 픽스처가 계약을 지키는 대신 계약을 속인다.

여기서 하는 일은 **응답과 배정만 손으로 짓고 나머지는 진짜 코드에 맡기는 것**이다.
언급 수·인용문·세그먼트 교차·연결표·포화 지표는 전부 `app/interview` 가 계산한다.
LLM 자리(조건식·러너·코더)만 갈아끼우고 표집·검산·조립은 실제 경로를 탄다.

**재료에 반드시 들어가야 하는 것** — 안 넣으면 새 블록이 빈 배열로 굳어 테스트가
아무것도 안 잡는다:
  · 오해한 사람 1명          · 「차이 없다」고 한 사람들
  · 비타겟으로 뽑힌 사람들    · 장벽이 없어지면 사겠다고 **말한** 사람
  · 제안과 우려를 **함께** 말한 사람 (연결표의 근거)
"""

import asyncio
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[3]))

import app.interview as I                                              # noqa: E402
from app.interview.coding import (Assignment, Codebook, CodebookAlternative,   # noqa: E402
                                  CodebookTheme, verify)
from app.interview.targeting import TargetCriteria                     # noqa: E402

OUT = Path(__file__).parent / "interview.json"

BOARD = {"conceptName": "귀가 알림 밴드", "targetUsers": "초등학생 자녀를 둔 맞벌이 부모",
         "problemScenario": "하교 후 30분 동안 아이와 연락이 닿지 않는다",
         "featureSet": ["학교·학원 도착 알림", "배터리 일주일", "분실 시 마지막 위치"],
         "differentiators": "아이에게 전화기를 쥐여 주지 않아도 된다",
         "priceKrw": 39000}

CARD = ("저는 만 {age}세 {gender}입니다. {region} 시 지역에 살고 있습니다. "
        "{kind} 형태의 {size}인 가구이고, 개인 월소득은 {income} 미만 수준입니다. "
        "일은 {job} 쪽 일을 임금 근로자로 하고 있습니다.")

#: (나이, 성별, 지역, 가구원, 소득, 직업, 원형). 앞 16명이 타겟(만 30~49세)이다.
PEOPLE = [
    (34, "여성", "서울", 4, "300~400만 원", "일반 지원 사무직", "price"),
    (41, "남성", "경기", 4, "400~500만 원", "영업 관리직", "battery"),
    (37, "여성", "부산", 3, "200~300만 원", "보건 의료직", "price"),
    (45, "남성", "서울", 5, "500~600만 원", "정보 통신 전문직", "simple"),
    (33, "여성", "인천", 3, "200~300만 원", "교육 전문직", "price"),
    (48, "남성", "대구", 4, "300~400만 원", "생산 기능직", "half"),
    (39, "여성", "경기", 4, "300~400만 원", "일반 지원 사무직", "battery"),
    (44, "남성", "광주", 3, "400~500만 원", "경영 관리직", "price"),
    (31, "여성", "서울", 3, "200~300만 원", "디자인 전문직", "simple"),
    (46, "남성", "충남", 5, "300~400만 원", "운수 운전직", "price"),
    (35, "여성", "대전", 4, "300~400만 원", "회계 사무직", "misread"),
    (42, "남성", "경기", 4, "400~500만 원", "건설 기능직", "battery"),
    (38, "여성", "서울", 3, "500~600만 원", "법률 전문직", "price"),
    (49, "남성", "경북", 4, "200~300만 원", "판매 종사직", "half"),
    (32, "여성", "울산", 3, "300~400만 원", "사회 복지직", "simple"),
    (47, "남성", "전북", 4, "300~400만 원", "생산 기능직", "price"),
    (26, "여성", "서울", 1, "200~300만 원", "일반 지원 사무직", "outsider"),
    (58, "남성", "경기", 2, "300~400만 원", "경영 관리직", "outsider"),
    (63, "여성", "부산", 2, "100~200만 원", "판매 종사직", "outsider"),
    (69, "남성", "강원", 2, "100만 원", "농림 어업직", "outsider"),
]

ARCHETYPES = {
    "price": {
        "answers": {
            "firstImpression": "아이 하교 때문에 늘 마음 졸였는데, 이런 게 있으면 좋겠다 싶었어요.",
            "restatement": "아이가 학교나 학원에 도착하면 부모 휴대폰으로 알려 주는 밴드네요.",
            "like": "도착했다는 알림이 자동으로 오는 게 제일 좋아요. 제가 회사에서 전화를 "
                    "못 받을 때가 많은데, 그 시간에 확인이 되니까 그게 저한테는 중요해요.",
            "concern": "3만 9천 원이면 좀 부담스러워요. 아이 학용품이랑 학원비까지 생각하면 "
                       "다이소에서 파는 미아방지 호루라기랑 비교했을 때 차이가 커요.",
            "differentiation": "솔직히 요즘 나오는 위치 추적기랑 크게 달라 보이지는 않아요.",
            "relevance": "필요하긴 해요. 지금은 아이가 도착할 때쯤 제가 직접 전화를 걸어요.",
            "usageScene": "평일 하교 시간이요. 2시에서 4시 사이에 제일 신경이 쓰이거든요.",
            "barrier": "가격이 가장 큰 걸림돌이에요. 2만 원대였으면 바로 샀을 것 같아요.",
            "suggestion": "값을 조금만 내려 주시면 좋겠어요.",
        },
        "assign": {"comprehension": "accurate", "verdict": "similar", "resolved": True,
                   "like": ["아이 위치를 바로 안다"], "concern": ["가격이 부담된다"],
                   "differentiation": ["위치 추적기와 비슷하다"], "usage": ["하교 시간"],
                   "barrier": ["가격이 가장 큰 걸림돌"], "suggestion": ["값을 내려 달라"],
                   "alternative": "직접 전화한다"},
    },
    "battery": {
        "answers": {
            "firstImpression": "괜찮아 보이는데 실제로 잘 될지는 좀 봐야 알 것 같아요.",
            "restatement": "아이가 어디 도착하면 알림이 오고, 잃어버리면 마지막 위치를 알려 준다는 거죠.",
            "like": "전화기를 안 사 줘도 된다는 점이요. 아이한테 스마트폰을 일찍 쥐여 주는 게 "
                    "제일 걱정이었는데 그 고민을 안 해도 되니까요.",
            "concern": "배터리가 일주일이라는데 실제로는 더 짧을 것 같아요. 아이 스마트워치도 "
                       "하루 만에 꺼지더라고요. 충전을 제가 챙겨야 할 것 같아서 걸려요.",
            "differentiation": "전화기 없이 된다는 점은 확실히 다르네요.",
            "relevance": "네 필요해요. 지금은 학원 선생님께 문자로 물어봐요.",
            "usageScene": "학원 오갈 때요. 태권도랑 피아노를 혼자 다녀서요.",
            "barrier": "가격도 가격인데 배터리가 진짜 일주일 가는지를 못 믿겠어요.",
            "suggestion": "배터리가 더 오래가게 해 주세요.",
        },
        "assign": {"comprehension": "accurate", "verdict": "different", "resolved": False,
                   "like": ["전화기가 필요 없다"], "concern": ["배터리가 걱정된다"],
                   "differentiation": ["전화기 없이 되는 점이 다르다"], "usage": ["학원 오갈 때"],
                   "barrier": ["가격이 가장 큰 걸림돌"], "suggestion": ["배터리를 오래가게"],
                   "alternative": "학원 선생님께 연락한다"},
    },
    "simple": {
        "answers": {
            "firstImpression": "생각보다 단순해서 좋네요. 복잡한 건 아이가 못 써요.",
            "restatement": "버튼 없이 차고만 다니면 도착 알림이 가는 물건이요.",
            "like": "아이가 따로 조작할 게 없다는 점이 좋아요. 뭘 눌러야 하면 우리 애는 "
                    "절대 안 해요. 그냥 차고 다니기만 하면 된다는 게 저한테는 핵심이에요.",
            "concern": "아이가 답답하다고 안 차고 다닐 것 같아요. 손목시계도 이틀 만에 "
                       "빼놓더라고요.",
            "differentiation": "위치 추적기랑 비슷한 것 같기도 하고 잘 모르겠어요.",
            "relevance": "있으면 좋죠. 지금은 그냥 참고 기다려요.",
            "usageScene": "주말에 놀이터나 마트 갈 때도 쓸 것 같아요.",
            "barrier": "아이가 안 차면 소용이 없으니까 그게 제일 걱정이에요.",
            "suggestion": "통화도 됐으면 좋겠어요.",
        },
        "assign": {"comprehension": "accurate", "verdict": "unclear", "resolved": False,
                   "like": ["조작이 간단하다"], "concern": ["아이가 안 차고 다닐 것 같다"],
                   "differentiation": [], "usage": ["주말 외출"],
                   "barrier": [], "suggestion": ["통화도 되게"],
                   "alternative": "그냥 참고 기다린다"},
    },
    "half": {
        "answers": {
            "firstImpression": "요즘 이런 게 많이 나오더라고요.",
            "restatement": "아이 위치를 실시간으로 계속 보여 주는 물건 아닌가요?",
            "like": "잃어버렸을 때 마지막 위치를 알 수 있다는 게 마음이 놓이네요.",
            "concern": "값이 좀 있네요. 편의점에서 파는 미아방지 팔찌는 몇천 원이잖아요.",
            "differentiation": "다른 것들이랑 뭐가 다른지는 잘 모르겠습니다.",
            "relevance": "우리 애는 이제 중학생이라 크게 필요하진 않아요. 직접 전화해요.",
            "usageScene": "쓴다면 하교 시간에 쓸 것 같긴 합니다.",
            "barrier": "굳이 안 사도 될 것 같아서요. 애가 휴대폰이 이미 있어요.",
            "suggestion": "값을 내려 달라는 것 말고는 딱히 없네요.",
        },
        "assign": {"comprehension": "partial", "verdict": "unclear", "resolved": False,
                   "like": ["아이 위치를 바로 안다"], "concern": ["가격이 부담된다"],
                   "differentiation": [], "usage": ["하교 시간"],
                   "barrier": ["아이가 이미 휴대폰이 있다"], "suggestion": ["값을 내려 달라"],
                   "alternative": "직접 전화한다"},
    },
    "misread": {
        "answers": {
            "firstImpression": "아이 건강 상태를 재는 밴드인 줄 알았어요.",
            "restatement": "아이 심박수나 걸음 수를 재서 부모한테 보내 주는 건강 밴드요.",
            "like": "아이 건강을 매일 확인할 수 있다는 게 좋네요.",
            "concern": "그런 건 이미 스마트워치에 다 있어서 굳이 따로 살 필요가 있나 싶어요.",
            "differentiation": "스마트워치랑 다를 게 없어 보여요.",
            "relevance": "저희는 아이가 건강해서 딱히 필요하진 않아요. 그냥 참고 지내요.",
            "usageScene": "운동할 때 채워 줄 것 같아요.",
            "barrier": "이미 비슷한 걸 갖고 있어서요.",
            "suggestion": "수면 측정도 됐으면 좋겠어요.",
        },
        "assign": {"comprehension": "misunderstood", "verdict": "similar", "resolved": False,
                   "like": [], "concern": [],
                   "differentiation": ["위치 추적기와 비슷하다"], "usage": [],
                   "barrier": ["아이가 이미 휴대폰이 있다"], "suggestion": [],
                   "alternative": "그냥 참고 기다린다"},
    },
    "outsider": {
        "answers": {
            "firstImpression": "저한테는 해당이 없는 물건 같네요.",
            "restatement": "아이 도착을 부모한테 알려 주는 기계인 것 같습니다.",
            "like": "아이 키우는 집에는 도움이 되겠네요.",
            "concern": "저는 쓸 일이 없어서 잘 모르겠어요.",
            "differentiation": "비슷한 게 이미 있는 걸로 압니다.",
            "relevance": "저는 아이가 없어서 필요 없습니다.",
            "usageScene": "제가 쓸 일은 없을 것 같아요.",
            "barrier": "필요가 없다는 게 이유죠.",
            "suggestion": "딱히 없습니다.",
        },
        "assign": {"comprehension": "partial", "verdict": "similar", "resolved": False,
                   "like": [], "concern": [],
                   "differentiation": ["위치 추적기와 비슷하다"], "usage": [],
                   "barrier": [], "suggestion": [],
                   "alternative": ""},
    },
}

CODEBOOK = Codebook(
    themes=[CodebookTheme(axis="LIKE", label=label) for label in
            ("아이 위치를 바로 안다", "전화기가 필요 없다", "조작이 간단하다")]
    + [CodebookTheme(axis="CONCERN", label=label) for label in
       ("가격이 부담된다", "배터리가 걱정된다", "아이가 안 차고 다닐 것 같다")]
    + [CodebookTheme(axis="DIFFERENTIATION", label=label) for label in
       ("위치 추적기와 비슷하다", "전화기 없이 되는 점이 다르다")]
    + [CodebookTheme(axis="USAGE_SCENE", label=label) for label in
       ("하교 시간", "학원 오갈 때", "주말 외출")]
    + [CodebookTheme(axis="BARRIER", label=label) for label in
       ("가격이 가장 큰 걸림돌", "아이가 이미 휴대폰이 있다")]
    + [CodebookTheme(axis="SUGGESTION", label=label) for label in
       ("값을 내려 달라", "배터리를 오래가게", "통화도 되게")],
    alternatives=[CodebookAlternative(label=label) for label in
                  ("직접 전화한다", "학원 선생님께 연락한다", "그냥 참고 기다린다")],
    misreadPoints=["도착 알림을 실시간 위치 추적으로 읽은 답이 있다",
                   "건강·활동량 측정 기기로 읽은 답이 있다"])

#: 형식을 어겨 분모에서 빠지는 사람(뽑힌 순서 기준). 조사는 늘 몇 명을 잃는다.
LOST = frozenset({7, 14})

# ⚠ `householdRoles` 는 비워 둔다 — 위 `CARD` 템플릿에 「가구 안에서는 …입니다」 문장이
#   없어서 골든 카드는 그 칸이 언제나 «못 읽음»이고, 못 읽은 칸은 조건을 통과하지 못한다.
#   조건으로 걸면 골든의 타겟이 통째로 0명이 된다.
CRITERIA = TargetCriteria(ageMin=30, ageMax=49, genders=[], householdSizeMin=3,
                          householdSizeMax=0, regions=[], incomeKeywords=[], jobKeywords=[],
                          hasChildren=0, householdRoles=[])

_HOUSEHOLD_KIND = {1: "1인가구", 2: "1세대가구(부부)", 3: "2세대가구(부부+자녀)",
                   4: "2세대가구(부부+자녀)", 5: "3세대가구"}


def _band(age: int) -> str:
    return "60+" if age >= 60 else f"{age // 10}0대"


def _bank():
    cards, frame = {}, []
    for index, (age, gender, region, size, income, job, _kind) in enumerate(PEOPLE, 1):
        pid = f"pid{index:03d}"
        cards[pid] = CARD.format(age=age, gender=gender, region=region,
                                 kind=_HOUSEHOLD_KIND[size], size=size,
                                 income=income, job=job)
        frame.append({"pid_hash": pid, "gender": gender[0], "band": _band(age)})
    return cards, frame


def install():
    """LLM 자리만 갈아끼운다. 표집·검산·조립·교차는 실제 코드가 그대로 돈다."""
    cards, frame = _bank()
    I.load = lambda: (cards, frame)

    async def criteria(*_args):
        return CRITERIA

    I.resolve_criteria = criteria

    async def run(drawn_cards, board_text, _budget):
        # 두 명은 형식을 어겨 분모에서 빠진다. **분모가 표본 크기와 달라야** 화면·정규화기의
        # 「분모는 answered 다」 규약이 골든으로 실제로 시험된다.
        rows = []
        for index, pid in enumerate(sorted(drawn_cards), 1):
            archetype = PEOPLE[int(pid[3:]) - 1][6]
            ok = index not in LOST
            rows.append({"subject": pid, "ok": ok, "kind": None if ok else "format",
                         "answers": dict(ARCHETYPES[archetype]["answers"]) if ok else None})
        return rows, {"cells": len(rows), "rateLimited": 0, "timeouts": 0, "retries": 0,
                      "formatViolations": len(LOST), "failures": 0, "truncated": 0,
                      "waitSeconds": 0.0, "promptTokens": 18400, "completionTokens": 7300,
                      "model": "gpt-4o-mini", "concurrency": 32, "seconds": 41.2,
                      "llmCalls": len(rows)}

    I.run_interviews = run

    async def code(_board_text, answers, _timeout):
        # 응답자 번호(R1..)는 **답한 사람만** pid 오름차순으로 다시 매겨진다 —
        # 잃은 사람을 빼고 나서 원형을 맞춰야 배정이 엉뚱한 사람에게 붙지 않는다.
        order = [PEOPLE[int(pid[3:]) - 1][6]
                 for index, pid in enumerate(sorted(_drawn_pids), 1) if index not in LOST]
        rows = []
        for rid, archetype in zip(sorted(answers, key=lambda r: int(r[1:])), order):
            spec = ARCHETYPES[archetype]["assign"]
            rows.append(Assignment(
                id=rid, comprehension=spec["comprehension"],
                differentiationVerdict=spec["verdict"], barrierResolved=spec["resolved"],
                likeLabels=spec["like"], concernLabels=spec["concern"],
                differentiationLabels=spec["differentiation"],
                usageSceneLabels=spec["usage"], barrierLabels=spec["barrier"],
                suggestionLabels=spec["suggestion"],
                alternativeLabel=spec["alternative"]))
        coded = verify(CODEBOOK, rows, answers)
        return type(coded)(**{**coded.__dict__, "llmCalls": 2})

    I.code_responses = code


_drawn_pids: list = []


def main() -> int:
    install()
    original = I.draw_split

    def spy(cards, frame, size, criteria):
        drawn, targets, sampling, targeting = original(cards, frame, size, criteria)
        _drawn_pids.clear()
        _drawn_pids.extend(row["pid_hash"] for row in drawn)
        return drawn, targets, sampling, targeting

    I.draw_split = spy
    result = asyncio.run(I.execute_market_interview(
        {"conceptBoard": BOARD, "sampleSize": 20}, budget_seconds=900.0))
    OUT.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    answered = result["telemetry"]["answered"]
    print(f"wrote {OUT}  bytes={OUT.stat().st_size:,}  answered={answered}")
    print("  타겟/비타겟 :", result["targeting"]["targetDrawn"], "/",
          result["targeting"]["nonTargetDrawn"])
    print("  주제        :", len(result["themes"]),
          " 대안 합계:", result["telemetry"]["homogeneity"]["alternativeSum"], "/", answered)
    print("  포화        :", result["telemetry"]["homogeneity"]["saturatedThemes"] or "없음")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
