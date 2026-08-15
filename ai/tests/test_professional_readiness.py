import asyncio
from unittest.mock import AsyncMock

from app.tasks.launch_readiness.professional import service
from app.tasks.launch_readiness.professional.models import AnalysisReview, ProfessionalAnalysis


def _analysis(score: int = 72) -> ProfessionalAnalysis:
    return ProfessionalAnalysis.model_validate({
        "decision": "CONDITIONAL", "score": score, "summary": "전문 입력에 근거한 조건부 출시 판단입니다. 확인된 핵심 위험을 담당자별 실행 과제로 보완하고, 완료 증빙과 테스트 결과를 독립 검토한 뒤 출시 여부를 다시 결정해야 합니다.",
        "dimensions": [{"name": f"영역 {index}", "score": score, "status": "CAUTION", "finding": "입력 자료에서 확인된 기준과 미확정 조건을 함께 검토해야 합니다."} for index in range(1, 5)],
        "risks": [{"title": f"위험 {index}", "severity": "HIGH", "likelihood": "MEDIUM", "impact": "출시 일정과 서비스 품질에 영향을 줄 수 있습니다.", "mitigation": "담당자가 검증 기록을 남기고 통과 기준을 확인해야 합니다."} for index in range(1, 4)],
        "gates": [{"title": f"게이트 {index}", "status": "OPEN", "criterion": "입력한 기준을 실제 테스트로 확인하고 결과 기록을 승인해야 합니다.", "evidenceNeeded": "테스트 로그와 담당자 승인 기록"} for index in range(1, 5)],
        "actions": [{"priority": "P1", "title": f"실행 과제 {index}", "owner": "담당자", "completionEvidence": "검증 결과 문서와 승인 이력"} for index in range(1, 4)],
    })


def test_restarts_analysis_once_when_independent_review_fails(monkeypatch):
    monkeypatch.setattr(service, "_external_evidence", AsyncMock(return_value=[]))
    generate = AsyncMock(side_effect=[_analysis(60), _analysis(78)])
    review = AsyncMock(side_effect=[
        AnalysisReview(passed=False, score=55, feedback=["위험과 점수의 연결을 보강하세요."], unsupportedClaims=[]),
        AnalysisReview(passed=True, score=90, feedback=["검증 통과"], unsupportedClaims=[]),
    ])
    monkeypatch.setattr(service, "_generate", generate)
    monkeypatch.setattr(service, "_review", review)

    result = asyncio.run(service.analyze_professional_readiness({"moduleType": "TECHNOLOGY", "input": {"systemArchitecture": "웹 서비스 구조"}}))

    assert result["score"] == 78
    assert result["quality"]["passed"] is True
    assert result["quality"]["attempts"] == 2
    assert generate.await_count == 2
