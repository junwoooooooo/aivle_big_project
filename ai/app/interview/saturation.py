"""포화 검출 — 「전원이 같은 말을 했다」를 숨기지 않고 드러낸다. LLM 0회.

코딩을 2패스로 뒤집으면 **코딩이 만들어 낸** 40/40 은 사라진다. 그래도 **진짜로 40명이
다 가격을 말한 경우**는 남는다. 자극이 한 속성에 쏠렸거나, 합성 응답자의 분산이 소실됐거나,
표본이 너무 좁을 때 그렇다.

그때 할 일은 막는 것이 아니라 **보이게** 하는 것이다. 이 저장소가 시장조사에서 「미확보」를
숨기지 않는 것과 같은 원리다 — 값을 지어내는 것보다 값이 없다고 말하는 쪽이 언제나 낫다.

여기서 재는 것은 전부 **사람 수와 이름표 수**다. 비율은 만들지 않는다.
"""

from app.interview.models import AXES

__all__ = ["homogeneity", "saturated"]


def homogeneity(themes: list[dict], alternatives: list[dict], answered: int) -> dict:
    """축별 이름표 수 · 축별 최대 언급 수 · 포화 주제 · 대안 합계.

    이 네 줄이 「40/40 이 사라졌나」를 판정하는 진단표다. 원장(`ledger`)에 그대로 실리고
    재코딩 하니스(`tools/recode_ledger`)가 같은 함수를 쓴다 — 판정 기준이 갈리면 안 된다.
    """
    counts = {axis: 0 for axis in AXES}
    peaks = {axis: 0 for axis in AXES}
    for theme in themes:
        axis = theme["axis"]
        counts[axis] = counts.get(axis, 0) + 1
        peaks[axis] = max(peaks.get(axis, 0), theme["mentionCount"])
    return {
        "axisLabelCounts": counts,
        "maxMentionByAxis": peaks,
        "saturatedThemes": saturated(themes, answered),
        "alternativeSum": sum(row["mentionCount"] for row in alternatives),
    }


def saturated(themes: list[dict], answered: int) -> list[str]:
    """전원이 든 주제, 그리고 이름표가 하나뿐인 축.

    둘 다 「이 축은 읽지 마라」의 신호다. 앞은 답이 안 갈린 것이고, 뒤는 코더가 결을
    못 찾은 것이다 — 화면에서 구분할 필요는 없고 둘 다 경고면 된다.
    """
    if answered <= 0:
        return []
    flagged = [f"{theme['axis']}: {theme['label']}"
               for theme in themes if theme["mentionCount"] >= answered]
    seen = set(flagged)
    by_axis: dict[str, list[dict]] = {}
    for theme in themes:
        by_axis.setdefault(theme["axis"], []).append(theme)
    for axis in AXES:
        rows = by_axis.get(axis) or []
        if len(rows) == 1 and f"{axis}: {rows[0]['label']}" not in seen:
            flagged.append(f"{axis}: {rows[0]['label']}")
    return flagged
