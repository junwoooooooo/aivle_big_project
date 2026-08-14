"""카드 본문 → 화면에 앉힐 프로필 6필드.

**왜 카드 원문을 그대로 내보내지 않는가.** 카드는 재배포 금지 마이크로데이터이고 본문에
학력·혼인·미디어이용·심리척도까지 들어 있다. 화면이 필요로 하는 것은 «누가 말했는지»를
알아볼 정도이지 그 사람을 특정할 정보가 아니다. 그래서 여기서 **6필드만 뽑고 나머지는
계약 밖에 둔다**. `pid_hash` 도 싣지 않는다 — 화면이 쓰지 않는데 재식별 표면만 넓힌다.

파서 규율: **필드마다 실패하면 `None`, 예외는 던지지 않는다.** 카드 문장은 응답자마다
조금씩 다르고, 한 명이 안 읽힌다고 조사 전체가 죽는 것은 균형이 맞지 않는다.
못 읽은 필드는 화면에서 그냥 빠진다.

전체 8,604장 실측 커버리지(2026-08-10): 나이·성별·가구 8,604 / 지역 8,604 /
직업 8,604(«따로 하는 일은 없습니다» 816 포함) / 소득 8,604(«없습니다» 1,738 포함).
"""

import re

__all__ = ["parse_profile", "FIELDS"]

FIELDS = ("age", "gender", "household", "region", "income", "job")

# 「저는 만 48세 남성입니다.」
_AGE_GENDER = re.compile(r"저는 만 (\d{1,3})세 (남성|여성)입니다")
# 「서울 시 지역에 살고 있습니다.」 / 「전남 군 지역에 살고 있습니다.」
_REGION = re.compile(r"([가-힣]{2,4})\s*[시군구]\s*지역에 살고 있습니다")
# 「2세대가구(부부+자녀) 형태의 3인 가구이고,」
_HOUSEHOLD = re.compile(r"형태의 (\d{1,2})인 가구")
# 「개인 월소득은 300~400만 원 미만 수준입니다.」 — 가구 월소득이 아니라 **개인** 쪽이다.
_INCOME = re.compile(r"개인 월소득은 ([^.]+?) 수준입니다")
_INCOME_NONE = "개인 소득은 없습니다"
# 「일은 일반 지원 사무직 쪽 일을 임금 근로자로 하고 있습니다.」
_JOB = re.compile(r"일은 (.+?) 쪽 일을")
# 「현재 전업주부입니다.」 / 「현재 학생입니다.」 / 「현재 군인입니다.」
_JOB_CURRENT = re.compile(r"현재 ([^.]+?)입니다")
_JOB_NONE = "현재 따로 하는 일은 없습니다"

JOB_MAX = 24
INCOME_MAX = 24


def _first(pattern: re.Pattern, text: str, group: int = 1) -> str | None:
    match = pattern.search(text)
    return match.group(group).strip() if match else None


def parse_profile(card_text: str | None) -> dict:
    """6필드. 못 읽은 자리는 `None` 이고, 그 자리는 화면에서 빠진다."""
    text = card_text or ""

    age = None
    gender = None
    match = _AGE_GENDER.search(text)
    if match:
        age = int(match.group(1))
        gender = match.group(2)

    household = _first(_HOUSEHOLD, text)
    if household:
        household = f"{household}인 가구"

    region = _first(_REGION, text)

    if _INCOME_NONE in text:
        income = "개인 소득 없음"
    else:
        raw = _first(_INCOME, text)
        # 「300~400만 원 미만」 → 「월소득 300~400만 원」. «미만»은 구간 표기의 잔재라 뗀다.
        income = f"월소득 {raw.replace(' 미만', '')}"[:INCOME_MAX] if raw else None

    if _JOB_NONE in text:
        job = "무직"
    else:
        job = _first(_JOB, text) or _first(_JOB_CURRENT, text)
        job = job[:JOB_MAX] if job else None

    return {"age": age, "gender": gender, "household": household,
            "region": region, "income": income, "job": job}


def is_empty(profile: dict) -> bool:
    """전부 못 읽었다. 빈 카드를 화면에 앉히지 않으려고 쓴다."""
    return all(profile.get(field) is None for field in FIELDS)
