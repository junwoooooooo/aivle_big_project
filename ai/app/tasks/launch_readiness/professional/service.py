from __future__ import annotations

import json
import os
import re
from typing import Any

import httpx

from app.providers import execute_structured_prompt
from app.tasks.launch_readiness.professional.models import (
    AnalysisReview,
    ProfessionalAnalysis,
    ProfessionalAnalysisRequest,
)

TAVILY_ENDPOINT = "https://api.tavily.com/search"


def _plain(value: Any, limit: int = 500) -> str:
    return re.sub(r"\s+", " ", str(value or "")).strip()[:limit]


def _search_queries(module_type: str, values: dict[str, str]) -> list[str]:
    context = " ".join(_plain(value, 300).lower() for value in values.values() if value)
    allowed = [
        "aws", "azure", "gcp", "kubernetes", "docker", "spring", "react", "postgresql",
        "mysql", "redis", "api", "saas", "개인정보", "결제", "인증", "백업", "모니터링",
        "고객지원", "sla", "물류", "공급망", "파일럿", "장애대응",
    ]
    keywords = " ".join(token for token in allowed if token in context) or ({
        "TECHNOLOGY": "소프트웨어 서비스", "OPERATIONS": "서비스 운영",
    }.get(module_type, "제품 서비스 출시 준비"))
    if module_type == "TECHNOLOGY":
        return [f"{keywords} 공식 기술 아키텍처 보안 가이드", f"{keywords} 성능 테스트 장애 대응 공식 문서"]
    if module_type == "OPERATIONS":
        return [f"{keywords} 운영 KPI SLA 파일럿 가이드", f"{keywords} 고객 지원 장애 대응 운영 벤치마크"]
    return [f"{keywords} 출시 체크리스트 롤백 모니터링 가이드",
            f"{keywords} go live 승인 기준 고객 지원 공식 가이드"]


async def _external_evidence(module_type: str, values: dict[str, str]) -> list[dict[str, str]]:
    key = os.getenv("TAVILY_API_KEY", "").strip()
    if not key:
        return []
    rows: list[dict[str, str]] = []
    async with httpx.AsyncClient(timeout=10) as client:
        for query in _search_queries(module_type, values):
            try:
                response = await client.post(TAVILY_ENDPOINT, json={
                    "api_key": key, "query": query, "search_depth": "advanced", "max_results": 3,
                    "include_answer": False, "include_raw_content": False,
                })
                response.raise_for_status()
                results = response.json().get("results", [])
            except (httpx.HTTPError, ValueError):
                continue
            for item in results:
                url = _plain(item.get("url"), 600)
                title = _plain(item.get("title"), 180)
                if url.startswith(("https://", "http://")) and title:
                    rows.append({"title": title, "url": url, "snippet": _plain(item.get("content"), 420), "query": _plain(query, 300)})
    unique: dict[str, dict[str, str]] = {}
    for row in rows:
        unique.setdefault(row["url"], row)
    return list(unique.values())[:6]


def _analysis_system(module_type: str) -> str:
    subject = {"TECHNOLOGY": "기술", "OPERATIONS": "운영"}.get(module_type, "출시 준비")
    dimensions = (
        "아키텍처 적합성, 구현 완성도, 보안·데이터, 성능·확장, 테스트·출시"
        if module_type == "TECHNOLOGY"
        else "프로세스, 인력·책임, 공급·파트너, 고객지원·품질, 파일럿·확장"
        if module_type == "OPERATIONS"
        else "출시 범위·승인 기준, 고객 이용 준비, 법무·정책 확인, 모니터링·장애 대응, 커뮤니케이션·미해결 위험"
    )
    return f"""당신은 기업 출시심사위원회의 {subject} 전문 분석가입니다.
사용자가 작성한 전문입력을 사실 판단의 정본으로 사용하십시오. 외부 검색 결과는 검증 보조자료로만 사용하십시오.
입력에 없는 사실이나 수치를 만들어내지 마십시오. 미확정 내용은 OPEN 게이트로 남기십시오.
{dimensions} 관점에서 서로 중복되지 않는 평가를 작성하십시오.
각 평가의 finding에는 반드시 (1) 사용자가 입력한 구체적 사실 또는 빈칸, (2) 그 사실이 출시 준비도에 미치는 영향, (3) 점수·상태를 부여한 이유를 한 문단으로 담으십시오.
각 위험은 구체적인 입력 내용과 연결하고, 각 조치는 담당자와 완료 증빙을 명시하십시오.
긴 일반론을 피하고 한국어 JSON만 반환하십시오."""


async def _generate(request: ProfessionalAnalysisRequest, evidence: list[dict[str, str]], feedback: list[str]) -> ProfessionalAnalysis:
    raw = await execute_structured_prompt(
        _analysis_system(request.moduleType),
        json.dumps({"moduleType": request.moduleType, "professionalInput": request.input,
                    "externalEvidence": evidence, "reviewFeedback": feedback}, ensure_ascii=False),
        response_schema=ProfessionalAnalysis.model_json_schema(),
        schema_name="professional_readiness_analysis_v1",
        task_type="PROFESSIONAL_READINESS_ANALYSIS",
        timeout_seconds_override=180,
    )
    return ProfessionalAnalysis.model_validate(raw)


async def _review(request: ProfessionalAnalysisRequest, analysis: ProfessionalAnalysis) -> AnalysisReview:
    system = """당신은 독립된 품질검증자입니다. 사용자 전문입력과 분석 결과만 비교하십시오.
통과 조건은 (1) 입력에 없는 확정 사실·숫자가 없음, (2) 위험·조치·게이트가 구체적임,
(3) 요청된 분석 영역과 다른 독립 업무의 결과를 대신 만들지 않음, (4) 점수가 발견된 위험과 모순되지 않음입니다.
하나라도 어기면 passed=false로 하고 재분석에 바로 쓸 수 있는 한국어 피드백을 반환하십시오."""
    raw = await execute_structured_prompt(
        system,
        json.dumps({"moduleType": request.moduleType, "professionalInput": request.input,
                    "analysis": analysis.model_dump(mode="json")}, ensure_ascii=False),
        response_schema=AnalysisReview.model_json_schema(),
        schema_name="professional_readiness_review_v1",
        task_type="PROFESSIONAL_READINESS_REVIEW",
        timeout_seconds_override=120,
    )
    return AnalysisReview.model_validate(raw)


async def analyze_professional_readiness(payload: dict) -> dict:
    request = ProfessionalAnalysisRequest.model_validate(payload)
    normalized = {key: _plain(value, 4000) for key, value in request.input.items()}
    request = request.model_copy(update={"input": normalized})
    evidence = await _external_evidence(request.moduleType, normalized)
    feedback: list[str] = []
    analysis: ProfessionalAnalysis | None = None
    review: AnalysisReview | None = None
    attempts = 0
    while attempts < 2:
        attempts += 1
        analysis = await _generate(request, evidence, feedback)
        review = await _review(request, analysis)
        if review.passed:
            break
        feedback = [*review.feedback, *review.unsupportedClaims]
    assert analysis is not None and review is not None
    result = analysis.model_dump(mode="json")
    result["externalEvidence"] = evidence
    result["quality"] = {"passed": review.passed, "reviewScore": review.score, "attempts": attempts,
                         "feedback": review.feedback, "unsupportedClaims": review.unsupportedClaims}
    return result
