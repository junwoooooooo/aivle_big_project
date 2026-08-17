"""Donor-equivalent target normalization and deterministic 8:2 panel sampling."""

import math
import re
from dataclasses import dataclass

from app.providers import ProviderFailure
from app.tasks.market_interview.models import TargetCriteria
from app.twin.bank import stratified_sample
from app.twin.profile import parse_profile, parse_target_facts

TARGET_SHARE = 0.8
_HOUSEHOLD_SIZE = re.compile(r"(\d{1,2})인 가구")
_COUNTRY_SCOPE = {"대한민국", "한국", "전국", "국내", "대한민국 전역"}
_REGION_ALIASES = {
    "서울특별시": "서울", "부산광역시": "부산", "대구광역시": "대구",
    "인천광역시": "인천", "광주광역시": "광주", "대전광역시": "대전",
    "울산광역시": "울산", "세종특별자치시": "세종", "경기도": "경기",
    "강원도": "강원", "강원특별자치도": "강원", "충청북도": "충북",
    "충청남도": "충남", "전라북도": "전북", "전북특별자치도": "전북",
    "전라남도": "전남", "경상북도": "경북", "경상남도": "경남",
    "제주특별자치도": "제주",
}
_KNOWN_REGIONS = {
    "서울", "부산", "대구", "인천", "광주", "대전", "울산", "세종",
    "경기", "강원", "충북", "충남", "전북", "전남", "경북", "경남", "제주",
}
_EMPLOYED_TERMS = {"직장인", "회사원", "근로자", "취업자", "임금근로자"}
_NON_EMPLOYED = {"무직", "전업주부", "학생"}

TARGETING_POLICY = """
대한민국·한국·전국은 특정 시도 조건이 아니라 한국 shipped bank 전체 geographic scope이므로
regions를 비운다. 특정 시도가 명시된 경우에만 광역 region을 쓴다. 직장인·회사원·근로자는
jobKeywords에 '직장인' 하나로 표현한다. 운동 부족·활동량·관심·가격 민감도 같은 패널에서
직접 확인할 수 없는 성향은 hard condition에 넣지 않는다.
자녀를 둔 부모는 hasChildren=1과 householdRoles=['가구주','가구주의 배우자']를 함께 쓴다.
자녀 나이·학년과 맞벌이 여부는 이 bank에서 관측할 수 없으므로 조건으로 만들지 않는다.
jobKeywords에는 아래 실제 shipped bank 직업 어휘 또는 넓은 의미의 '직장인'만 쓴다."""


@dataclass(frozen=True)
class NormalizationReport:
    raw_matched: int
    normalized_matched: int
    relaxation_level: int
    reasons: tuple[str, ...]


def _household_size(facts: dict) -> int | None:
    match = _HOUSEHOLD_SIZE.search(str(facts.get("household") or ""))
    return int(match.group(1)) if match else None


def _employed(job: str | None) -> bool:
    value = (job or "").strip()
    return bool(value) and value not in _NON_EMPLOYED


def _job_matches(facts: dict, words: list[str]) -> bool:
    job = str(facts.get("job") or "")
    return any((_employed(job) if word == "직장인" else word in job)
               for word in words if word)


def conditions(criteria: TargetCriteria) -> list[tuple[str, object]]:
    rules: list[tuple[str, object]] = []
    if criteria.ageMin or criteria.ageMax:
        def age_ok(facts):
            age = facts.get("age")
            return (isinstance(age, int)
                    and (not criteria.ageMin or age >= criteria.ageMin)
                    and (not criteria.ageMax or age <= criteria.ageMax))
        rules.append((f"나이 {criteria.ageMin or '제한없음'}~{criteria.ageMax or '제한없음'}", age_ok))
    if criteria.genders:
        rules.append(("성별 " + "·".join(criteria.genders),
                      lambda facts: facts.get("gender") in criteria.genders))
    if criteria.householdSizeMin or criteria.householdSizeMax:
        def size_ok(facts):
            size = _household_size(facts)
            return (size is not None
                    and (not criteria.householdSizeMin or size >= criteria.householdSizeMin)
                    and (not criteria.householdSizeMax or size <= criteria.householdSizeMax))
        rules.append((f"가구원 {criteria.householdSizeMin or '제한없음'}~"
                      f"{criteria.householdSizeMax or '제한없음'}", size_ok))
    if criteria.hasChildren:
        want = criteria.hasChildren == 1
        rules.append(("자녀 동거 " + ("있음" if want else "없음"),
                      lambda facts: facts.get("hasChildren") is want))
    if criteria.householdRoles:
        rules.append(("가구 지위 " + "·".join(criteria.householdRoles),
                      lambda facts: facts.get("householdRole") in criteria.householdRoles))
    if criteria.regions:
        rules.append(("지역 " + "·".join(criteria.regions),
                      lambda facts: any(word in str(facts.get("region") or "")
                                        for word in criteria.regions if word)))
    if criteria.incomeKeywords:
        rules.append(("소득 " + "·".join(criteria.incomeKeywords),
                      lambda facts: any(word in str(facts.get("income") or "")
                                        for word in criteria.incomeKeywords if word)))
    if criteria.jobKeywords:
        rules.append(("직업 " + "·".join(criteria.jobKeywords),
                      lambda facts: _job_matches(facts, criteria.jobKeywords)))
    return rules


def has_conditions(criteria: TargetCriteria) -> bool:
    return bool(conditions(criteria))


def matches(facts: dict, criteria: TargetCriteria) -> bool:
    return all(test(facts) for _name, test in conditions(criteria))


def condition_matches(facts_by_pid: dict[str, dict], criteria: TargetCriteria) -> list[dict]:
    rules = conditions(criteria)
    if not rules:
        return []
    rows = [{"condition": name,
             "matched": sum(bool(test(facts)) for facts in facts_by_pid.values())}
            for name, test in rules]
    rows.append({"condition": "전부 동시에 만족",
                 "matched": sum(matches(facts, criteria) for facts in facts_by_pid.values())})
    return rows


def _normalize_regions(regions: list[str], target_text: str) -> tuple[list[str], int, list[str]]:
    normalized: list[str] = []
    level = 0
    reasons: list[str] = []
    for raw in regions:
        value = _REGION_ALIASES.get(raw.strip(), raw.strip())
        if value in _COUNTRY_SCOPE:
            level = max(level, 2)
            reasons.append("country-scope geography")
            continue
        if value in _KNOWN_REGIONS:
            if value not in normalized:
                normalized.append(value)
            continue
        if value and value in target_text:
            normalized.append(value)
        elif value:
            level = max(level, 2)
            reasons.append("ambiguous generated geography")
    return normalized, level, reasons


def normalize_criteria(criteria: TargetCriteria, target_text: str,
                       facts_by_pid: dict[str, dict]) -> tuple[TargetCriteria, NormalizationReport]:
    raw_matched = sum(matches(facts, criteria) for facts in facts_by_pid.values())
    values = criteria.model_dump(mode="python")
    reasons: list[str] = []
    level = 0

    regions, region_level, region_reasons = _normalize_regions(values["regions"], target_text)
    values["regions"] = regions
    level = max(level, region_level)
    reasons.extend(region_reasons)

    jobs = {str(facts.get("job") or "") for facts in facts_by_pid.values() if facts.get("job")}
    requested_jobs = [word.strip() for word in values["jobKeywords"] if word.strip()]
    broad_employment = any(word in _EMPLOYED_TERMS for word in requested_jobs) or "직장인" in target_text
    normalized_jobs: list[str] = []
    if broad_employment:
        normalized_jobs.append("직장인")
        if requested_jobs != ["직장인"]:
            level = max(level, 1)
            reasons.append("broad occupation taxonomy")
    for word in requested_jobs:
        if word in _EMPLOYED_TERMS:
            continue
        if any(word in job for job in jobs):
            if word not in normalized_jobs:
                normalized_jobs.append(word)
        else:
            level = max(level, 3)
            reasons.append("unobservable soft/job preference")
    values["jobKeywords"] = normalized_jobs[:15]

    normalized = TargetCriteria.model_validate(values)
    normalized_matched = sum(matches(facts, normalized) for facts in facts_by_pid.values())
    return normalized, NormalizationReport(raw_matched, normalized_matched, level,
                                            tuple(dict.fromkeys(reasons)))


def criteria_text(criteria: TargetCriteria, facts_by_pid: dict[str, dict],
                  report: NormalizationReport) -> str:
    rows = condition_matches(facts_by_pid, criteria)
    if rows:
        counts = {row["condition"]: row["matched"] for row in rows}
        head = " / ".join(f"{name}({counts.get(name, 0):,}명)"
                          for name, _test in conditions(criteria))
        description = f"{head} → 전부 동시에 만족: {report.normalized_matched:,}명"
    else:
        description = "개인 프로필에서 직접 확인 가능한 타겟 조건 없음"
    reasons = ", ".join(report.reasons) if report.reasons else "none"
    return (f"{description}. raw={report.raw_matched:,}명, normalized={report.normalized_matched:,}명, "
            f"relaxationLevel={report.relaxation_level}({reasons}). 실제 판정과 표집은 코드가 수행했습니다.")


def draw_panel(cards: dict[str, str], frame: list[dict], criteria: TargetCriteria, size: int,
               target_text: str = "", customer_unit: str = "PERSON") -> tuple[list[dict], dict]:
    facts = {pid: parse_target_facts(text) for pid, text in cards.items()}
    normalized, report = normalize_criteria(criteria, target_text, facts)
    target_pids = {pid for pid, value in facts.items() if matches(value, normalized)}
    target_frame = [row for row in frame if row.get("pid_hash") in target_pids]
    comparison_frame = [row for row in frame if row.get("pid_hash") not in target_pids]

    directly_representable = customer_unit == "PERSON"
    if directly_representable and has_conditions(normalized) and not target_frame:
        raise ProviderFailure("INVALID_REQUEST", "MARKET_INTERVIEW_TARGET_UNAVAILABLE", 422, False,
                              safe_diagnostics={"requested": size, "rawTargetMatches": report.raw_matched,
                                                "targetMatches": 0,
                                                "relaxationLevel": report.relaxation_level})
    if not directly_representable or not has_conditions(normalized):
        picked, _sampling = stratified_sample(frame, size)
        target_pids = set()
        representation_status = "EXPLORATORY_ONLY"
        member_group = "EXPLORATORY"
        warning = ("현재 개인 profile bank로 조직 구매 담당자를 직접 표현할 수 없어 일반 관점의 "
                   "탐색 표본으로 구성했습니다." if customer_unit == "ORGANIZATION" else
                   "패널에서 직접 확인 가능한 HARD 조건이 없어 일반 관점의 탐색 표본으로 구성했습니다.")
    else:
        wanted = math.ceil(size * TARGET_SHARE)
        target_size = min(wanted, len(target_frame))
        comparison_size = size - target_size
        if len(comparison_frame) < comparison_size:
            extra = comparison_size - len(comparison_frame)
            target_size = min(size, target_size + extra)
            comparison_size = size - target_size
        target_rows, _target_report = stratified_sample(target_frame, target_size)
        comparison_rows, _comparison_report = stratified_sample(comparison_frame, comparison_size)
        picked = sorted(target_rows + comparison_rows, key=lambda row: row["pid_hash"])
        target_pids = {row["pid_hash"] for row in target_rows}
        representation_status = "PARTIAL_PROXY" if report.relaxation_level > 0 else "REPRESENTABLE_TARGET"
        member_group = "PROXY" if representation_status == "PARTIAL_PROXY" else "TARGET"
        warning = ("직접 타겟이 아니라 관찰 가능한 대리 조건으로 표집했습니다. "
                   if representation_status == "PARTIAL_PROXY" else "")
        if target_size != wanted:
            warning += f"TARGET 목표 {wanted}명 중 {target_size}명을 확보하고 나머지를 COMPARISON으로 보완했습니다."
        warning = warning or None
    if len(picked) != size:
        raise ProviderFailure("INVALID_REQUEST", "MARKET_INTERVIEW_TARGET_INSUFFICIENT", 422, False,
                              safe_diagnostics={"requested": size, "drawn": len(picked),
                                                "targetMatches": len(target_frame)})

    rows = []
    for index, row in enumerate(picked, 1):
        pid = row["pid_hash"]
        rows.append({"participantId": f"R{index:03d}",
                     "group": member_group if pid in target_pids or member_group == "EXPLORATORY" else "COMPARISON",
                     "cardText": cards[pid], "profile": parse_profile(cards[pid])})
    return rows, {"matched": len(target_frame), "rawMatched": report.raw_matched,
                  "total": len(frame), "relaxationLevel": report.relaxation_level,
                  "criteria": normalized, "criteriaText": criteria_text(normalized, facts, report),
                  "warning": warning, "representationStatus": representation_status,
                  "customerUnit": customer_unit}


def public_profile(profile: dict) -> str:
    labels = []
    if profile.get("age") is not None:
        labels.append(f"만 {profile['age']}세")
    for key in ("gender", "household", "region", "income", "job"):
        if profile.get(key):
            labels.append(str(profile[key]))
    return " · ".join(labels) or "파생 프로필 정보 없음"


def profile_taxonomy(cards: dict[str, str]) -> str:
    """Expose only the bank's normalized matching vocabulary to the criteria model."""
    facts = [parse_target_facts(text) for text in cards.values()]
    regions = sorted({str(row.get("region")) for row in facts if row.get("region")})
    jobs = sorted({str(row.get("job")) for row in facts if row.get("job")})
    return ("실제 shipped profile bank 광역지역 어휘: " + " · ".join(regions)
            + "\n실제 shipped profile bank 직업 어휘: " + " · ".join(jobs))
