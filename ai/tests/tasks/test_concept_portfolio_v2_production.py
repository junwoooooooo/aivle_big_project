from __future__ import annotations

import asyncio
from datetime import datetime, timedelta, timezone

import pytest
from fastapi.testclient import TestClient
from pydantic import ValidationError

from app.api.executions import TASK_TYPES
from app.canonical_json import canonical_input_hash
from app.concept_portfolio_v2 import ConceptPortfolioEngine
from app.concept_portfolio_v2.models import LegalRoute, PortfolioStatus, RunStage
from app.tasks.concept_portfolio_v2.models import (
    MAX_INTERNAL_EXECUTION_BYTES,
    PRODUCTION_RESULT_SAFETY_BYTES,
    ConceptPortfolioProductionInput,
)
from app.tasks.concept_portfolio_v2.observer import ProductionObservedConceptPortfolioEngine
from app.tasks.concept_portfolio_v2.service import ConceptPortfolioProductionFacade
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
def core_result():
    result = asyncio.run(ConceptPortfolioEngine().run_full(
        PRODUCTION_INPUT.seed,
        max_concepts=5,
        auto_confirm_hypotheses=False,
    ))
    assert result.concepts
    assert result.legalSummaries
    return result


class FakeEngine:
    def __init__(self, result, candidates=None):
        self.result = result
        self.candidates = candidates or {}
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


def test_continuation_is_exported_only_for_unresolved_candidate(core_result):
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

    result = asyncio.run(ConceptPortfolioProductionFacade(
        engine=FakeEngine(raw, candidates)
    ).run(PRODUCTION_INPUT))

    assert result.requiredInputs == [required]
    assert len(result.continuationArtifacts) == 1
    artifact = result.continuationArtifacts[0]
    assert artifact.candidateId == unresolved.candidateId
    assert artifact.lineageId == "lineage-needs-input"
    assert artifact.parentCandidateId == "parent-candidate"
    assert artifact.recoverySource == "LEGAL_REDESIGN"
    assert artifact.affectedFields == ["personalDataUsage"]
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


def test_dispatcher_accepts_cpv2_and_preserves_existing_task_types(monkeypatch):
    import app.tasks.concept_portfolio_v2 as task_module

    called = []

    async def fake_execute(value):
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

