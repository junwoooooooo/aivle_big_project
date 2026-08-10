from __future__ import annotations

import asyncio
from datetime import datetime, timedelta, timezone

import pytest
from fastapi.testclient import TestClient
from pydantic import ValidationError

from app.api.executions import TASK_TYPES
from app.canonical_json import canonical_input_hash
from app.concept_portfolio_v2.models import LegalRoute, PortfolioPlan, PortfolioStatus, RunStage
from app.providers import ProviderFailure
from app.tasks.concept_portfolio_v2.models import (
    MAX_INTERNAL_EXECUTION_BYTES,
    PRODUCTION_RESULT_SAFETY_BYTES,
    ConceptPortfolioProductionInput,
    ProductionRequiredInput,
)
from app.tasks.concept_portfolio_v2.observer import ProductionObservedConceptPortfolioEngine
from app.tasks.concept_portfolio_v2.service import (
    ConceptPortfolioProductionContractError,
    ConceptPortfolioProductionFacade,
)
from main import app


PRODUCTION_INPUT = ConceptPortfolioProductionInput.model_validate({
    "seed": {
        "ideaBriefSnapshotId": "brief-p1",
        "ideaOverview": "소형 매장의 예약 확인 업무를 자동화한다.",
        "problem": "반복 예약 확인 때문에 운영 시간이 낭비된다.",
        "targetUsers": "예약 서비스를 운영하는 국내 소형 매장",
        "fields": [
            {"fieldKey": "ideaOverview", "value": "소형 매장의 예약 확인 업무를 자동화한다.",
             "source": "USER_CONFIRMED", "decisionState": "LOCKED"},
            {"fieldKey": "problem", "value": "반복 예약 확인 때문에 운영 시간이 낭비된다.",
             "source": "USER_CONFIRMED", "decisionState": "LOCKED"},
            {"fieldKey": "targetUsers", "value": "예약 서비스를 운영하는 국내 소형 매장",
             "source": "USER_CONFIRMED", "decisionState": "LOCKED"},
        ],
        "interpretation": {"summary": "예약 확인 운영 자동화"},
        "fixtureName": "p1-production",
    },
    "maxConcepts": 5,
})


@pytest.fixture(scope="module")
def core_bundle():
    engine = ProductionObservedConceptPortfolioEngine()
    result = asyncio.run(engine.run_full(
        PRODUCTION_INPUT.seed,
        max_concepts=5,
        auto_confirm_hypotheses=False,
    ))
    assert result.concepts
    assert result.legalSummaries
    return engine, result


@pytest.fixture(scope="module")
def core_result(core_bundle):
    return core_bundle[1]


class FakeEngine:
    def __init__(self, result, candidates=None, plans=None, design=None):
        self.result = result
        self.candidates = candidates or {}
        self.plans = plans or {}
        self.design = design
        self.calls = []

    async def run_full(self, seed, *, max_concepts, auto_confirm_hypotheses):
        self.calls.append({
            "seed": seed,
            "max_concepts": max_concepts,
            "auto_confirm_hypotheses": auto_confirm_hypotheses,
        })
        return self.result

    def continuation_candidate(self, candidate_id):
        return self.candidates.get(candidate_id)

    def continuation_plan(self, plan_id):
        return self.plans.get(plan_id)

    def continuation_design(self):
        return self.design


def production_result_with_count(base, count):
    template = base.concepts[0]
    legal_template = base.legalSummaries[0]
    concepts = []
    legal = []
    for index in range(count):
        candidate_id = f"accepted-{index + 1}"
        concepts.append(template.model_copy(update={
            "candidateId": candidate_id,
            "lineageId": f"lineage-{index + 1}",
            "parentCandidateId": None,
        }, deep=True))
        legal.append(legal_template.model_copy(update={
            "candidateId": candidate_id,
            "route": LegalRoute.ACCEPT,
            "safeSummary": "법률·규제 검토를 완료했습니다.",
        }, deep=True))
    status = PortfolioStatus.READY_FULL if count == 5 else PortfolioStatus.READY_LIMITED
    summary = base.runSummary.model_copy(update={
        "finalPortfolio": count,
        "portfolioStatus": status,
        "selectedConcept": concepts[0].candidate.conceptName if concepts else None,
    }) if base.runSummary else None
    return base.model_copy(update={
        "runStatus": status,
        "runtimeStage": RunStage.READY,
        "requestedMaxConcepts": 5,
        "producedConceptCount": count,
        "concepts": concepts,
        "legalSummaries": legal,
        "legalResolutions": [],
        "requiredInputs": [],
        "unresolvedCandidates": [],
        "preLegalExclusions": [],
        "selectedConceptId": concepts[0].candidateId if concepts else None,
        "runSummary": summary,
    }, deep=True)


def test_facade_uses_canonical_run_full_and_never_auto_confirms(core_result):
    raw = production_result_with_count(core_result, 2)
    engine = FakeEngine(raw)
    result = asyncio.run(ConceptPortfolioProductionFacade(engine=engine).run(
        PRODUCTION_INPUT.model_dump(mode="json")
    ))

    assert engine.calls == [{
        "seed": PRODUCTION_INPUT.seed,
        "max_concepts": 5,
        "auto_confirm_hypotheses": False,
    }]
    assert result.engineStatus == "READY_LIMITED"
    assert result.producedConceptCount == 2
    assert result.engineDefaultConceptId == "accepted-1"
    assert result.userSelectedConceptId is None
    assert "trace" not in result.model_dump(mode="json")


@pytest.mark.parametrize("count", [1, 2, 3, 4, 5])
def test_facade_accepts_one_to_five_concepts(core_result, count):
    raw = production_result_with_count(core_result, count)
    result = asyncio.run(ConceptPortfolioProductionFacade(engine=FakeEngine(raw)).run(PRODUCTION_INPUT))
    assert result.producedConceptCount == count
    assert result.engineStatus == ("READY_FULL" if count == 5 else "READY_LIMITED")


def test_continuation_is_exported_only_for_unresolved_candidate(core_bundle):
    observed_engine, core_result = core_bundle
    raw = production_result_with_count(core_result, 2)
    unresolved = core_result.concepts[0].model_copy(update={
        "candidateId": "needs-input-1",
        "lineageId": "lineage-needs-input",
        "parentCandidateId": "parent-candidate",
        "recoverySource": "LEGAL_REDESIGN",
    }, deep=True)
    needs_review = core_result.legalSummaries[0].model_copy(update={
        "candidateId": unresolved.candidateId,
        "route": LegalRoute.NEEDS_INPUT,
        "safeSummary": "실제 개인정보 이용 범위를 확인해야 합니다.",
        "evidenceDiagnostics": {
            "affectedFields": ["personalDataUsage"],
            "factQuestion": "어떤 개인정보를 저장할 예정인가요?",
        },
    }, deep=True)
    required = {
        "candidateId": unresolved.candidateId,
        "scope": "CANDIDATE",
        "unknownFacts": ["저장할 개인정보 항목"],
        "reason": "실제 개인정보 이용 범위가 필요합니다.",
        "question": "어떤 개인정보를 저장할 예정인가요?",
        "safeSummary": "추가 확인이 필요합니다.",
    }
    raw = raw.model_copy(update={
        "legalSummaries": [*raw.legalSummaries, needs_review],
        "requiredInputs": [required],
        "unresolvedCandidates": [required],
    }, deep=True)
    candidates = {item.candidateId: item for item in raw.concepts}
    candidates[unresolved.candidateId] = unresolved
    plan = observed_engine.continuation_plan(unresolved.planId)
    design = observed_engine.continuation_design()

    result = asyncio.run(ConceptPortfolioProductionFacade(
        engine=FakeEngine(raw, candidates, {plan.planId: plan}, design)
    ).run(PRODUCTION_INPUT))

    assert result.requiredInputs[0].model_dump(
        mode="json", exclude_none=True, exclude={"affectedFields"}
    ) == required
    assert len(result.continuationArtifacts) == 1
    assert result.continuationContext is not None
    assert result.continuationContext.designSnapshot == design
    assert result.continuationContext.plans == [plan]
    assert isinstance(result.continuationContext.plans[0], PortfolioPlan)
    artifact = result.continuationArtifacts[0]
    assert artifact.candidateId == unresolved.candidateId
    assert artifact.lineageId == "lineage-needs-input"
    assert artifact.parentCandidateId == "parent-candidate"
    assert artifact.recoverySource == "LEGAL_REDESIGN"
    assert artifact.affectedFields == ["personalDataUsage"]
    assert artifact.requiredInput.affectedFields == ["personalDataUsage"]
    assert artifact.planId == plan.planId
    assert artifact.candidateSnapshot.candidateId == unresolved.candidateId
    assert artifact.acceptedPortfolioConceptIds == ["accepted-1", "accepted-2"]
    assert all(item.candidateId != "accepted-1" for item in result.continuationArtifacts)


def test_observer_fans_out_core_trace_after_core_records_it():
    events = []
    engine = ProductionObservedConceptPortfolioEngine(event_sink=events.append)
    engine._reset()

    assert len(engine.trace) == 1
    assert len(events) == 1
    assert events[0].sequence == 1
    assert events[0].stage == "CREATED"
    assert events[0].action == "CREATED"
    assert events[0].occurredAt == engine.trace[0].timestamp


def test_observer_sink_failure_does_not_break_core_trace(caplog):
    def broken_sink(_event):
        raise RuntimeError("sink unavailable")

    engine = ProductionObservedConceptPortfolioEngine(event_sink=broken_sink)
    engine._reset()

    assert len(engine.trace) == 1
    assert "CPV2 production trace sink failed" in caplog.text


def test_observer_captures_design_and_actual_plan_and_resets_all_context():
    engine = ProductionObservedConceptPortfolioEngine()
    result = asyncio.run(engine.run_full(
        PRODUCTION_INPUT.seed, max_concepts=1, auto_confirm_hypotheses=False
    ))
    candidate = result.concepts[0]
    design = engine.continuation_design()
    plan = engine.continuation_plan(candidate.planId)

    assert design is not None
    assert design.explorationBreadth.value in {"EXPLORE", "REFINE", "AS_IS"}
    assert plan is not None
    assert isinstance(plan, PortfolioPlan)
    assert plan.planId == candidate.planId
    assert plan.oneLineConcept
    assert plan.solutionThesis
    assert engine.continuation_candidate(candidate.candidateId) == candidate

    engine._reset()
    assert engine.continuation_design() is None
    assert engine.continuation_plan(candidate.planId) is None
    assert engine.continuation_candidate(candidate.candidateId) is None


@pytest.mark.parametrize("invalid", [
    {**PRODUCTION_INPUT.model_dump(mode="json"), "maxConcepts": 0},
    {**PRODUCTION_INPUT.model_dump(mode="json"), "maxConcepts": 6},
    {**PRODUCTION_INPUT.model_dump(mode="json"), "autoConfirmHypotheses": True},
])
def test_production_input_rejects_invalid_max_and_unknown_fields(invalid):
    with pytest.raises(ValidationError):
        ConceptPortfolioProductionInput.model_validate(invalid)


def test_five_concept_result_stays_below_internal_response_limit(core_result):
    raw = production_result_with_count(core_result, 5)
    result = asyncio.run(ConceptPortfolioProductionFacade(engine=FakeEngine(raw)).run(PRODUCTION_INPUT))
    payload = result.model_dump(mode="json")

    assert result.serialized_size_bytes() < PRODUCTION_RESULT_SAFETY_BYTES
    assert PRODUCTION_RESULT_SAFETY_BYTES < MAX_INTERNAL_EXECUTION_BYTES
    assert "trace" not in payload
    assert "failureDiagnostics" not in payload
    assert "providerUsage" not in payload


def test_production_summary_has_no_engine_selection_leakage(core_result):
    raw = production_result_with_count(core_result, 2)
    result = asyncio.run(ConceptPortfolioProductionFacade(engine=FakeEngine(raw)).run(PRODUCTION_INPUT))
    payload = result.model_dump(mode="json")

    assert result.userSelectedConceptId is None
    assert result.engineDefaultConceptId == "accepted-1"
    assert result.runSummary is not None
    assert {"selectedConcept", "selectedBusinessPlan", "currentSelection"}.isdisjoint(
        _nested_keys(payload)
    )


def test_five_needs_input_contract_is_shared_deduplicated_and_bounded(core_bundle):
    observed_engine, core_result = core_bundle
    unresolved = []
    legal = []
    required = []
    candidates = {}
    plans = {}
    for index, template in enumerate(core_result.concepts[:5], 1):
        candidate = template.model_copy(update={
            "candidateId": f"needs-input-{index}",
            "lineageId": f"lineage-needs-input-{index}",
        }, deep=True)
        review = core_result.legalSummaries[0].model_copy(update={
            "candidateId": candidate.candidateId,
            "route": LegalRoute.NEEDS_INPUT,
            "safeSummary": "실제 사업 사실 확인이 필요합니다.",
            "unknownFacts": [f"사업 사실 {index}"],
            "evidenceDiagnostics": {"affectedFields": [f"actualField{index}"]},
        }, deep=True)
        request = {
            "candidateId": candidate.candidateId,
            "scope": "CANDIDATE",
            "unknownFacts": [f"사업 사실 {index}"],
            "reason": "법률 검토를 계속하려면 실제 사업 사실이 필요합니다.",
            "question": f"실제 사업 사실 {index}을 확인해 주세요.",
            "possibleUserAction": "확인된 사업 사실을 입력하세요.",
            "safeSummary": "추가 확인이 필요합니다.",
        }
        unresolved.append(candidate)
        legal.append(review)
        required.append(request)
        candidates[candidate.candidateId] = candidate
        plan = observed_engine.continuation_plan(candidate.planId)
        assert plan is not None
        plans[plan.planId] = plan
    summary = core_result.runSummary.model_copy(update={
        "finalPortfolio": 0,
        "portfolioStatus": PortfolioStatus.FAILED,
        "selectedConcept": None,
    })
    raw = core_result.model_copy(update={
        "runStatus": PortfolioStatus.FAILED,
        "runtimeStage": RunStage.FAILED,
        "producedConceptCount": 0,
        "concepts": [],
        "legalSummaries": legal,
        "legalResolutions": [],
        "requiredInputs": required,
        "unresolvedCandidates": required,
        "selectedConceptId": None,
        "runSummary": summary,
    }, deep=True)
    result = asyncio.run(ConceptPortfolioProductionFacade(engine=FakeEngine(
        raw, candidates, plans, observed_engine.continuation_design()
    )).run(PRODUCTION_INPUT))
    payload = result.model_dump(mode="json")

    assert len(result.continuationArtifacts) == 5
    assert result.continuationContext is not None
    assert len(result.continuationContext.plans) == len({item.planId for item in unresolved})
    assert all(item.planId in plans for item in result.continuationArtifacts)
    assert all("canonicalSeedSnapshot" not in item for item in payload["continuationArtifacts"])
    assert all("designSnapshot" not in item for item in payload["continuationArtifacts"])
    serialized = result.model_dump_json()
    assert serialized.count('"canonicalSeedSnapshot"') == 1
    assert serialized.count('"designSnapshot"') == 1
    assert result.serialized_size_bytes() < PRODUCTION_RESULT_SAFETY_BYTES


@pytest.mark.parametrize("invalid", [
    {
        "candidateId": "candidate-1", "scope": "CANDIDATE", "unknownFacts": [],
        "rawProviderDiagnostic": {"secret": "forbidden"},
    },
    {
        "candidateId": "candidate-1", "scope": "CANDIDATE",
        "unknownFacts": ["x" * 4001],
    },
])
def test_production_required_input_rejects_unknown_and_bound_violations(invalid):
    with pytest.raises(ValidationError):
        ProductionRequiredInput.model_validate(invalid)


def test_incomplete_continuation_context_raises_production_contract_error(core_result):
    raw = production_result_with_count(core_result, 1)
    candidate = core_result.concepts[0].model_copy(update={
        "candidateId": "needs-input-incomplete",
    }, deep=True)
    review = core_result.legalSummaries[0].model_copy(update={
        "candidateId": candidate.candidateId,
        "route": LegalRoute.NEEDS_INPUT,
        "evidenceDiagnostics": {"affectedFields": ["actualSeller"]},
    }, deep=True)
    required = {
        "candidateId": candidate.candidateId,
        "scope": "CANDIDATE",
        "unknownFacts": ["실제 판매 주체"],
        "reason": "실제 판매 주체 확인이 필요합니다.",
        "safeSummary": "추가 확인이 필요합니다.",
    }
    raw = raw.model_copy(update={
        "legalSummaries": [*raw.legalSummaries, review],
        "requiredInputs": [required],
    }, deep=True)

    with pytest.raises(ConceptPortfolioProductionContractError):
        asyncio.run(ConceptPortfolioProductionFacade(
            engine=FakeEngine(raw, {candidate.candidateId: candidate})
        ).run(PRODUCTION_INPUT))


def test_dispatcher_accepts_cpv2_and_preserves_existing_task_types(monkeypatch):
    import app.tasks.concept_portfolio_v2 as task_module

    called = []

    async def fake_execute(value, **_kwargs):
        called.append(value)
        return {"contract": "concept-portfolio-v2-production-result-v1", "schemaVersion": "1.0"}

    monkeypatch.setattr(task_module, "execute_concept_portfolio_v2", fake_execute)
    monkeypatch.setenv("AI_INTERNAL_SERVICE_TOKEN", "p1-token")
    task_input = PRODUCTION_INPUT.model_dump(mode="json")
    request = _execution_request(task_input)

    with TestClient(app) as client:
        response = client.post(
            "/internal/v1/ai/executions",
            json=request,
            headers={"Authorization": "Bearer p1-token", "X-Correlation-Id": "correlation-p1"},
        )

    assert response.status_code == 200
    assert called == [task_input]
    assert response.json()["provenance"][0]["sourceKeys"] == ["concept-portfolio-v2-input"]
    assert "CONCEPT_PORTFOLIO_V2_RUN" in TASK_TYPES
    assert {
        "IDEA_BRIEF_DERIVATION", "CONCEPT_CANDIDATE", "CONCEPT_DISTINCTNESS_JUDGE",
        "CONCEPT_LEGAL_REVIEW", "CONCEPT_REDESIGN", "CONCEPT_HYPOTHESIS_ALTERNATIVE",
        "CONCEPT_DELTA_LEGAL_REVIEW", "TECH_OPS_PROPOSAL", "FINANCE_ESTIMATE",
        "MARKETING_CONTENT_GENERATION",
    } < TASK_TYPES


def test_dispatcher_reuses_safe_validation_error_for_invalid_input(monkeypatch):
    monkeypatch.setenv("AI_INTERNAL_SERVICE_TOKEN", "p1-token")
    invalid = {**PRODUCTION_INPUT.model_dump(mode="json"), "maxConcepts": 6}
    request = _execution_request(invalid)

    with TestClient(app) as client:
        response = client.post(
            "/internal/v1/ai/executions",
            json=request,
            headers={"Authorization": "Bearer p1-token", "X-Correlation-Id": "correlation-p1"},
        )

    assert response.status_code == 400
    error = response.json()["error"]
    assert error["code"] == "INVALID_REQUEST"
    assert any(item["path"] == "input.maxConcepts" for item in error["details"][0]["fields"])


def test_dispatcher_normalizes_production_contract_error(monkeypatch):
    import app.tasks.concept_portfolio_v2 as task_module

    async def invalid_result(_value, **_kwargs):
        raise ConceptPortfolioProductionContractError("CONTINUATION_CONTEXT_INCOMPLETE")

    monkeypatch.setattr(task_module, "execute_concept_portfolio_v2", invalid_result)
    monkeypatch.setenv("AI_INTERNAL_SERVICE_TOKEN", "p1-token")
    request = _execution_request(PRODUCTION_INPUT.model_dump(mode="json"))

    with TestClient(app) as client:
        response = client.post(
            "/internal/v1/ai/executions",
            json=request,
            headers={"Authorization": "Bearer p1-token", "X-Correlation-Id": "correlation-p1"},
        )

    assert response.status_code == 502
    error = response.json()["error"]
    assert error["code"] == "RESULT_SCHEMA_INVALID"
    assert error["details"] == [{"reason": "AI_RESULT_INVALID"}]
    assert error["retryable"] is False


def test_dispatcher_keeps_provider_failure_handling(monkeypatch):
    import app.tasks.concept_portfolio_v2 as task_module

    async def provider_failure(_value, **_kwargs):
        raise ProviderFailure(
            "DEPENDENCY_UNAVAILABLE", "MODEL_DEPENDENCY_UNAVAILABLE", 503, True
        )

    monkeypatch.setattr(task_module, "execute_concept_portfolio_v2", provider_failure)
    monkeypatch.setenv("AI_INTERNAL_SERVICE_TOKEN", "p1-token")
    request = _execution_request(PRODUCTION_INPUT.model_dump(mode="json"))

    with TestClient(app) as client:
        response = client.post(
            "/internal/v1/ai/executions",
            json=request,
            headers={"Authorization": "Bearer p1-token", "X-Correlation-Id": "correlation-p1"},
        )

    assert response.status_code == 503
    error = response.json()["error"]
    assert error["code"] == "DEPENDENCY_UNAVAILABLE"
    assert error["details"] == [{"reason": "MODEL_DEPENDENCY_UNAVAILABLE"}]
    assert error["retryable"] is True


def _execution_request(task_input):
    task_type = "CONCEPT_PORTFOLIO_V2_RUN"
    return {
        "contractVersion": "1.0",
        "taskType": task_type,
        "taskSchemaVersion": "1.0",
        "taskRunId": "task-p1",
        "taskAttemptId": "attempt-p1",
        "correlationId": "correlation-p1",
        "deadlineAt": (datetime.now(timezone.utc) + timedelta(minutes=5)).isoformat().replace("+00:00", "Z"),
        "canonicalInputHash": canonical_input_hash(
            contract_version="1.0",
            task_type=task_type,
            task_schema_version="1.0",
            locale="ko-KR",
            input_value=task_input,
        ),
        "locale": "ko-KR",
        "input": task_input,
    }


def _nested_keys(value):
    if isinstance(value, dict):
        return set(value).union(*( _nested_keys(item) for item in value.values()))
    if isinstance(value, list):
        return set().union(*(_nested_keys(item) for item in value)) if value else set()
    return set()
