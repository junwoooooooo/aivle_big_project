"""Deterministic 8:2 profile-bank sampling for synthetic market interviews."""

import re

from app.providers import ProviderFailure
from app.tasks.market_interview.models import TargetCriteria
from app.twin.bank import stratified_sample
from app.twin.profile import parse_profile, parse_target_facts

TARGET_SHARE = 0.8
_HOUSEHOLD_SIZE = re.compile(r"(\d{1,2})인 가구")


def has_conditions(criteria: TargetCriteria) -> bool:
    return any((criteria.ageMin, criteria.ageMax, criteria.genders,
                criteria.householdSizeMin, criteria.householdSizeMax, criteria.regions,
                criteria.incomeKeywords, criteria.jobKeywords, criteria.hasChildren,
                criteria.householdRoles))


def matches(facts: dict, criteria: TargetCriteria) -> bool:
    if criteria.ageMin or criteria.ageMax:
        age = facts.get("age")
        if not isinstance(age, int) or (criteria.ageMin and age < criteria.ageMin) \
                or (criteria.ageMax and age > criteria.ageMax):
            return False
    if criteria.genders and facts.get("gender") not in criteria.genders:
        return False
    if criteria.householdSizeMin or criteria.householdSizeMax:
        match = _HOUSEHOLD_SIZE.search(str(facts.get("household") or ""))
        size = int(match.group(1)) if match else None
        if size is None or (criteria.householdSizeMin and size < criteria.householdSizeMin) \
                or (criteria.householdSizeMax and size > criteria.householdSizeMax):
            return False
    if criteria.hasChildren:
        if facts.get("hasChildren") is not (criteria.hasChildren == 1):
            return False
    if criteria.householdRoles and facts.get("householdRole") not in criteria.householdRoles:
        return False
    for key, words in (("region", criteria.regions), ("income", criteria.incomeKeywords),
                       ("job", criteria.jobKeywords)):
        if words and not any(word in str(facts.get(key) or "") for word in words if word):
            return False
    return True


def criteria_text(criteria: TargetCriteria, matched: int, total: int) -> str:
    active = []
    if criteria.ageMin or criteria.ageMax:
        active.append(f"나이 {criteria.ageMin or '제한없음'}~{criteria.ageMax or '제한없음'}")
    if criteria.genders: active.append("성별 " + "·".join(criteria.genders))
    if criteria.householdSizeMin or criteria.householdSizeMax:
        active.append(f"가구원 {criteria.householdSizeMin or '제한없음'}~{criteria.householdSizeMax or '제한없음'}")
    if criteria.regions: active.append("지역 " + "·".join(criteria.regions))
    if criteria.incomeKeywords: active.append("소득 " + "·".join(criteria.incomeKeywords))
    if criteria.jobKeywords: active.append("직업 " + "·".join(criteria.jobKeywords))
    if criteria.hasChildren: active.append("자녀 동거 " + ("있음" if criteria.hasChildren == 1 else "없음"))
    if criteria.householdRoles: active.append("가구 지위 " + "·".join(criteria.householdRoles))
    description = ", ".join(active) if active else "패널에서 확인 가능한 제한 조건 없음"
    return f"{description}. 조건 교집합 {matched}명 / 표집틀 {total}명. 조건 변환은 AI가 수행하고 실제 판정과 표집은 코드가 수행했습니다."


def draw_panel(cards: dict[str, str], frame: list[dict], criteria: TargetCriteria, size: int) -> tuple[list[dict], dict]:
    facts = {pid: parse_target_facts(text) for pid, text in cards.items()}
    target_frame = [row for row in frame if matches(facts.get(row.get("pid_hash")) or {}, criteria)]
    comparison_frame = [row for row in frame if row not in target_frame]
    target_size = int(size * TARGET_SHARE)
    comparison_size = size - target_size
    if has_conditions(criteria) and not target_frame:
        raise ProviderFailure("INVALID_REQUEST", "MARKET_INTERVIEW_TARGET_UNAVAILABLE", 422, False,
                              safe_diagnostics={"requested": size, "targetMatches": 0})
    if not has_conditions(criteria):
        picked, _ = stratified_sample(frame, size)
        groups = ["TARGET"] * size
        warning = "패널 필드로 표현 가능한 타겟 조건이 없어 전체 표집틀을 대상으로 탐색했습니다."
    else:
        if len(target_frame) < target_size or len(comparison_frame) < comparison_size:
            raise ProviderFailure("INVALID_REQUEST", "MARKET_INTERVIEW_TARGET_INSUFFICIENT", 422, False,
                                  safe_diagnostics={"targetAvailable": len(target_frame),
                                                    "comparisonAvailable": len(comparison_frame),
                                                    "targetRequired": target_size,
                                                    "comparisonRequired": comparison_size})
        target_rows, _ = stratified_sample(target_frame, target_size)
        comparison_rows, _ = stratified_sample(comparison_frame, comparison_size)
        picked = target_rows + comparison_rows
        groups = ["TARGET"] * len(target_rows) + ["COMPARISON"] * len(comparison_rows)
        warning = None
    rows = []
    for index, (row, group) in enumerate(zip(picked, groups), 1):
        pid = row["pid_hash"]
        rows.append({"participantId": f"R{index:03d}", "group": group,
                     "cardText": cards[pid], "profile": parse_profile(cards[pid])})
    return rows, {"matched": len(target_frame), "total": len(frame), "warning": warning}


def public_profile(profile: dict) -> str:
    labels = []
    if profile.get("age") is not None: labels.append(f"만 {profile['age']}세")
    for key in ("gender", "household", "region", "income", "job"):
        if profile.get(key): labels.append(str(profile[key]))
    return " · ".join(labels) or "파생 프로필 정보 없음"
