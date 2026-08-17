import asyncio

from app.providers.schema_compatibility import strict_schema_failures
from app.tasks.launch_readiness.professional import service
from app.tasks.launch_readiness.professional.models import (
    AnalysisReview, ProfessionalAnalysis, ProfessionalAnalysisRequest,
)


def _analysis():
    return ProfessionalAnalysis.model_validate({
        "decision": "CONDITIONAL", "score": 72,
        "summary": "사용자 입력에 구체적인 계획이 있으나 출시 전에 보안 점검과 복구 훈련을 완료하고 그 증빙을 남겨야 합니다.",
        "dimensions": [{"name": f"평가 영역 {index}", "score": 72, "status": "CAUTION",
                        "finding": "사용자가 작성한 계획을 근거로 보완 항목과 출시 영향을 확인했습니다."} for index in range(1, 5)],
        "risks": [{"title": f"핵심 위험 {index}", "severity": "MEDIUM", "likelihood": "MEDIUM",
                   "impact": "출시 일정과 서비스 안정성에 영향을 줄 수 있습니다.",
                   "mitigation": "담당자를 지정하고 검증 결과를 출시 전에 기록합니다."} for index in range(1, 4)],
        "gates": [{"title": f"출시 기준 {index}", "status": "OPEN",
                   "criterion": "출시 전 점검을 완료하고 담당자 승인을 기록해야 합니다.",
                   "evidenceNeeded": "점검 결과 문서와 승인 기록"} for index in range(1, 5)],
        "actions": [{"priority": "P1", "title": f"실행 과제 {index}", "owner": "기술 책임자",
                     "completionEvidence": "완료된 점검표와 승인 내역"} for index in range(1, 4)],
    })


def test_professional_schema_is_strict_and_preserves_full_product_contract():
    schema = ProfessionalAnalysis.model_json_schema()
    assert strict_schema_failures(schema) == []
    assert set(schema["required"]) == set(schema["properties"])
    assert set(schema["properties"]) == {"decision", "score", "summary", "dimensions", "risks", "gates", "actions"}


def test_failed_independent_review_causes_one_bounded_regeneration(monkeypatch):
    generated = []
    reviews = iter([
        AnalysisReview(passed=False, score=60, feedback=["근거를 더 구체화하세요."], unsupportedClaims=[]),
        AnalysisReview(passed=True, score=92, feedback=["통과"], unsupportedClaims=[]),
    ])

    async def evidence(*_args): return [{"title": "공식 가이드", "url": "https://example.com/guide", "snippet": "근거", "query": "질의"}]
    async def generate(_request, _evidence, feedback): generated.append(list(feedback)); return _analysis()
    async def review(*_args): return next(reviews)
    monkeypatch.setattr(service, "_external_evidence", evidence)
    monkeypatch.setattr(service, "_generate", generate)
    monkeypatch.setattr(service, "_review", review)

    result = asyncio.run(service.analyze_professional_readiness({"moduleType": "TECHNOLOGY", "input": {"systemArchitecture": "3계층 구조"}}))
    assert len(generated) == 2
    assert generated[1] == ["근거를 더 구체화하세요."]
    assert result["quality"]["attempts"] == 2
    assert result["quality"]["passed"] is True
    assert result["externalEvidence"][0]["url"] == "https://example.com/guide"


def test_professional_input_is_the_only_required_product_authority():
    request = ProfessionalAnalysisRequest.model_validate({
        "moduleType": "OPERATIONS",
        "input": {"supportProcess": "평일 09~18시 담당자 2명"},
    })

    assert set(request.model_dump()) == {"moduleType", "input"}
    system = service._analysis_system("OPERATIONS")
    assert "전문입력을 사실 판단의 정본" in system
    assert "입력에 없는 사실이나 수치를 만들어내지" in system


def test_launch_module_is_a_single_release_readiness_authority():
    request = ProfessionalAnalysisRequest.model_validate({
        "moduleType": "LAUNCH", "input": {"releaseScope": "지자체 대상 제한 출시",
        "incidentAndRollback": "오류율 기준을 넘으면 이전 버전으로 복귀"},
    })
    assert request.moduleType == "LAUNCH"
    system = service._analysis_system("LAUNCH")
    assert "출시 범위·승인 기준" in system
    assert "모니터링·장애 대응" in system
