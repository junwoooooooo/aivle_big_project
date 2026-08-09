"""Source authority와 business opportunity anchor를 분리한 정책."""

from __future__ import annotations

import re

from .models import CanonicalSeed, OpportunityAnchor


STOPWORDS = {"위한", "하고", "싶은", "사용자", "서비스", "문제", "통해", "관련", "발생한다"}
ENTERPRISE_DRIFT = {"기업", "법인", "구내식당", "급식", "공장", "학교급식"}


def _tokens(value: str) -> set[str]:
    return {token for token in re.findall(r"[0-9a-z가-힣]+", value.casefold())
            if len(token) >= 2 and token not in STOPWORDS}


def build_opportunity_anchor(seed: CanonicalSeed) -> OpportunityAnchor:
    specializations = ["핵심 대상군의 구체 하위 세그먼트", "사용 상황이나 빈도에 따른 세분화"]
    forbidden = ["핵심 문제를 다른 문제로 교체", "핵심 대상군을 무관한 고객군으로 교체"]
    if "가구" in seed.targetUsers:
        specializations.extend(["직장인 1인 가구", "대학생 1인 가구", "집밥 빈도가 높은 2인 가구"])
        forbidden.append("기업 급식만을 고객으로 전환")
    return OpportunityAnchor(problemCore=seed.problem, targetUserCore=seed.targetUsers,
                             intentCore=seed.ideaOverview,
                             allowedSpecializations=specializations[:10], forbiddenDrifts=forbidden[:10])


def assess_anchor(anchor: OpportunityAnchor, problem: str, target_users: str) -> tuple[str, str]:
    seed_target, candidate_target = _tokens(anchor.targetUserCore), _tokens(target_users)
    seed_problem, candidate_problem = _tokens(anchor.problemCore), _tokens(problem)
    enterprise_drift = bool(ENTERPRISE_DRIFT & candidate_target) and not bool(ENTERPRISE_DRIFT & seed_target)
    if enterprise_drift:
        return "FAIL", "핵심 대상군이 무관한 기업·급식 고객군으로 이동했습니다."
    target_overlap = bool(seed_target & candidate_target)
    problem_overlap = bool(seed_problem & candidate_problem)
    if target_overlap and problem_overlap:
        return "PASS", "핵심 opportunity를 유지한 허용 specialization입니다."
    if not target_overlap and not problem_overlap:
        return "FAIL", "problem과 target opportunity가 모두 원 anchor에서 이탈했습니다."
    return "AMBIGUOUS", "한 anchor만 명확히 확인되어 semantic 판정이 필요합니다."
