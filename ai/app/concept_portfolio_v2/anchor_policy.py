"""도메인 독립 OpportunityKernel 생성과 관계 판정."""

from __future__ import annotations

import re

from .models import CanonicalSeed, ExplorationBreadth, OpportunityKernel


GENERIC_STOPWORDS = {"위한", "통해", "관련", "사용자", "서비스", "문제", "제공", "기반"}


def _tokens(value: str) -> set[str]:
    return {token for token in re.findall(r"[0-9a-z가-힣]+", value.casefold())
            if len(token) >= 2 and token not in GENERIC_STOPWORDS}


def _components(value: str) -> list[str]:
    parts = [item.strip() for item in re.split(r"[,·/]|(?:\s+및\s+)|(?:\s+그리고\s+)", value) if item.strip()]
    return (parts or [value.strip()])[:12]


def build_opportunity_kernel(seed: CanonicalSeed) -> OpportunityKernel:
    interpretation = seed.interpretation
    problem = str(interpretation.get("interpretedProblem") or seed.problem)
    target = str(interpretation.get("interpretedTargetUsers") or seed.targetUsers)
    usage = str(interpretation.get("usageContext") or "입력된 문제 상황")
    definition = str(interpretation.get("conciseIdeaDefinition") or seed.ideaOverview)
    return OpportunityKernel(
        problemCore=problem,
        targetCore=target,
        useContexts=[usage],
        intentComponents=_components(definition),
        mustPreserve=[problem, target, definition],
        maySpecialize=["핵심 대상의 의미 있는 하위 세그먼트", "핵심 사용 맥락의 구체화", "가치 제안 또는 offer의 구체화"],
        forbiddenDriftSummary="핵심 문제와 대상이 모두 무관한 기회로 교체되면 범위를 벗어납니다.",
    )


def assess_anchor(kernel: OpportunityKernel, problem: str, target_users: str,
                  solution_content: str = "",
                  breadth: ExplorationBreadth = ExplorationBreadth.EXPLORE) -> tuple[str, str]:
    """명확한 관계만 결정론적으로 판정하고 나머지는 semantic 경계로 남긴다."""
    if not problem.strip() or not target_users.strip():
        return "OUT_OF_SCOPE", "핵심 problem 또는 target이 비어 있습니다."
    source_problem, actual_problem = _tokens(kernel.problemCore), _tokens(problem)
    source_target, actual_target = _tokens(kernel.targetCore), _tokens(target_users)
    problem_overlap = source_problem & actual_problem
    target_overlap = source_target & actual_target
    opportunity_match = bool(problem_overlap) and bool(target_overlap)
    specialization = bool(problem_overlap) and bool(source_target & actual_target)
    if not opportunity_match and not specialization:
        return "AMBIGUOUS", "표면 token만으로 Opportunity 관계를 확정할 수 없어 semantic 판정이 필요합니다."

    if solution_content:
        intent = _tokens(" ".join(kernel.intentComponents))
        actual_intent = _tokens(solution_content)
        intent_overlap = intent & actual_intent
        intent_ratio = len(intent_overlap) / max(1, len(intent))
        if breadth == ExplorationBreadth.AS_IS and intent and intent_ratio < 0.50:
            return "OUT_OF_SCOPE", "AS_IS에서 원 Concept intent를 확인할 수 없습니다."
        if breadth == ExplorationBreadth.REFINE and intent and intent_ratio < 0.30:
            return "AMBIGUOUS", "REFINE intent 보존 여부에 semantic 판정이 필요합니다."
    return "PASS", "OpportunityKernel의 problem·target·intent와 호환됩니다."


# 이전 import 지점을 위한 이름 호환이며 반환 계약은 OpportunityKernel이다.
build_opportunity_anchor = build_opportunity_kernel
