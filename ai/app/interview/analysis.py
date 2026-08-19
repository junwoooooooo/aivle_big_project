"""Insight 층 — 사실의 교차. **LLM 호출 0회.**

여기 있는 것은 전부 `respondentIds` 집합 연산이다. 「가격 우려 19명 중 15명이 월소득
300만 원 미만」 같은 문장이 나오려면 **누가 그 19명인지**를 알아야 하고, 옛 구조는
언급 수를 센 뒤 그 명단을 버렸다. 명단만 남기면 나머지는 덧셈과 교집합이라 AI 를 한 번도
더 부르지 않는다.

**해석 문장은 붙이지 않는다.** 이 모듈은 세기만 하고, 그것이 무슨 뜻인지는 화면도 말하지
않는다. 합성 응답자 표본에서 교차표에 판정을 붙이는 순간 없는 근거가 생긴다.

비율을 만들지 않는 규율도 그대로다 — 여기서 나가는 수는 전부 **사람 수**다.
"""

from app.interview.models import AXES

__all__ = ["segments", "contrast", "suggestion_links", "age_band"]

#: 교차에 쓰는 프로필 축. 뱅크에서 **실측 커버리지 100%** 인 칸만 연다(`twin/profile.py`).
DIMENSIONS = (("연령", "ageBand"), ("성별", "gender"),
              ("가구", "household"), ("개인 소득", "income"))

#: 교차표를 만들 주제 수. 전부 만들면 화면이 다시 나열이 된다.
SEGMENT_THEMES_MAX = 8
SEGMENT_THEMES_PER_AXIS = 2
#: 한 축에 늘어놓을 버킷 수. 꼬리는 **버리지 않고 한 칸으로 접는다**(아래 `_breakdown`).
BUCKETS_MAX = 6
UNKNOWN_BUCKET = "확인 안 됨"
TAIL_BUCKET = "그 밖"


def age_band(age) -> str | None:
    """만 나이 → 표집에 쓴 것과 **같은** 5구간. 축이 갈리면 교차표를 표본과 못 맞춘다."""
    if not isinstance(age, int):
        return None
    if age >= 60:
        return "60+"
    if age >= 20:
        return f"{age // 10}0대"
    return None


def _bucket(profile: dict, key: str) -> str:
    if key == "ageBand":
        return age_band(profile.get("age")) or UNKNOWN_BUCKET
    value = profile.get(key)
    return value if isinstance(value, str) and value.strip() else UNKNOWN_BUCKET


def _breakdown(ids: list[str], profiles: dict[str, dict]) -> list[dict]:
    """각 축의 버킷 합은 **언급 수와 정확히 같다** — 못 읽은 프로필도 버킷 하나로 센다.

    조용히 빼면 화면의 두 수가 어긋나고, 어긋난 이유를 아무도 못 찾는다.

    ⚠ 상위 몇 개만 남기되 **꼬리를 버리지 않고 한 칸으로 접는다.** 잘라 버리면 합이 줄고,
    합이 줄면 백엔드 계약이 결과를 통째로 거부한다(2026-08-13 실측: 소득 구간이 7종이라
    39 vs 40 으로 어긋나 `RESULT_FIELD_CONSTRAINT_VIOLATION` 이 났다). 화면을 짧게 하려던
    상한이 조사 전체를 죽인 자리다.
    """
    rows = []
    for title, key in DIMENSIONS:
        tally: dict[str, int] = {}
        for rid in ids:
            label = _bucket(profiles.get(rid) or {}, key)
            tally[label] = tally.get(label, 0) + 1
        ordered = sorted(tally.items(), key=lambda item: (-item[1], item[0]))
        head, tail = ordered[:BUCKETS_MAX - 1], ordered[BUCKETS_MAX - 1:]
        buckets = [{"label": label, "count": count} for label, count in head]
        if len(tail) == 1:
            buckets.append({"label": tail[0][0], "count": tail[0][1]})
        elif tail:
            buckets.append({"label": f"{TAIL_BUCKET} {len(tail)}종",
                            "count": sum(count for _label, count in tail)})
        rows.append({"dimension": title, "buckets": buckets})
    return rows


def segments(themes: list[dict], profiles: dict[str, dict]) -> list[dict]:
    """언급이 많은 주제 몇 개를 프로필 네 축으로 쪼갠다. 축별 최대 2개, 총 8개까지."""
    picked: list[dict] = []
    for axis in AXES:
        rows = [theme for theme in themes if theme["axis"] == axis]
        picked.extend(rows[:SEGMENT_THEMES_PER_AXIS])
    picked.sort(key=lambda theme: (-theme["mentionCount"], AXES.index(theme["axis"]),
                                   theme["label"]))
    return [{"axis": theme["axis"], "label": theme["label"],
             "mentionCount": theme["mentionCount"],
             "breakdown": _breakdown(theme["respondentIds"], profiles)}
            for theme in picked[:SEGMENT_THEMES_MAX]]


def contrast(themes: list[dict], target_ids: set) -> list[dict]:
    """같은 주제를 타겟 / 비타겟으로 갈라 센다.

    **비율로 환산하지 않는다.** 분모가 다른 두 수를 나란히 놓고, 분모는 화면이 함께 적는다.
    """
    rows = []
    for theme in themes:
        inside = sum(1 for rid in theme["respondentIds"] if rid in target_ids)
        rows.append({"axis": theme["axis"], "label": theme["label"],
                     "targetCount": inside,
                     "nonTargetCount": theme["mentionCount"] - inside})
    return rows


def suggestion_links(themes: list[dict]) -> list[dict]:
    """개선 제안 ↔ 우려·장벽. 연결의 근거는 **같은 사람이 둘 다 말했다**는 것뿐이다.

    「소량 저가 플랜을 말한 12명 중 9명이 가격 장벽도 말했다」까지가 이 함수의 산출이고,
    그래서 가격을 내려야 한다는 문장은 여기서도 화면에서도 만들지 않는다.
    """
    problems = [theme for theme in themes if theme["axis"] in ("CONCERN", "BARRIER")]
    rows = []
    for theme in themes:
        if theme["axis"] != "SUGGESTION":
            continue
        members = set(theme["respondentIds"])
        links = []
        for problem in problems:
            shared = len(members.intersection(problem["respondentIds"]))
            if shared:
                links.append({"axis": problem["axis"], "label": problem["label"],
                              "overlapCount": shared})
        links.sort(key=lambda link: (-link["overlapCount"], link["axis"], link["label"]))
        rows.append({"label": theme["label"], "mentionCount": theme["mentionCount"],
                     "links": links})
    rows.sort(key=lambda row: (-row["mentionCount"], row["label"]))
    return rows
