import json
import os

from pydantic import ValidationError

from app.providers import ProviderFailure, execute_structured_prompt
from app.tasks.tech_ops_advisor.models import AdvisoryResult
from app.tasks.tech_ops_external_evidence import collect_external_evidence
from app.tasks.tech_ops_input_scaler import scale_tech_ops_input

SYSTEM = """당신은 한국 스타트업의 기술·운영 상용화 검증 자문가입니다.
제공된 current Concept, Market FULL, BM, 확정 TechOps 사용자 입력과 근거만 사용하십시오.
관측 사실, 사용자 확정값, 상위 분석 가정, 외부 참고 근거를 구분하십시오. 근거 없는 수치·법률 결론을 만들지 마십시오.
legalHandoff가 없으면 법률 판단 대신 반드시 OPEN 법률 검토 gate 하나를 만드십시오.
각 advice/gate/cost/readiness는 제공된 FACT-*/EXT-*/WEB-* basisIds를 최소 하나 인용해야 합니다.
조언은 MARKET_BM, PRODUCT_TECH, OPERATIONS, RISK_GATE, PARTNER_SUPPLY, PILOT, SCALE를 정확히 하나씩 작성하십시오.
readiness는 DATA_AI, CUSTOMER_TRUST, OBSERVABILITY_SLA, SCALABILITY를 정확히 하나씩 작성하십시오.
gate는 6개 이상, operatingCosts는 금액 예측이 아닌 비용 유발 조건과 파일럿 계측 단위로 5개 이상 작성하십시오.
pilotPlan에는 목적, 범위, 지표, 중단 조건, 확장 조건을 모두 구체적으로 작성하십시오.
disclaimer에는 자문 결과가 가정 기반이며 법률·재무 보증이 아니라는 점을 표시하십시오.
한국어 strict JSON 객체 하나만 반환하십시오."""


async def _request(context: dict, repair: bool) -> dict:
    instruction = "\n이전 결과가 계약을 위반했습니다. 전체 결과를 한 번만 새로 작성하십시오." if repair else ""
    return await execute_structured_prompt(SYSTEM + instruction, json.dumps(context, ensure_ascii=False),
        model_override=os.getenv("TECH_OPS_ADVISOR_MODEL", "").strip() or None,
        response_schema=None, schema_name=None,
        task_type="TECH_OPS_ADVISORY", timeout_seconds_override=180)


def _legal_gate_valid(result: AdvisoryResult, legal_present: bool) -> bool:
    if legal_present:
        return True
    return any(item.status == "OPEN" and any(word in f"{item.title} {item.exitCriteria}" for word in ("법률", "규제", "개인정보", "계약"))
               for item in result.gates)


def _validate(raw: dict, scaled: dict, legal_present: bool) -> AdvisoryResult:
    augmented = dict(raw)
    augmented["layer1Facts"] = scaled["layer1Facts"]
    augmented["layer2Evidence"] = scaled["layer2Evidence"]
    result = AdvisoryResult.model_validate(augmented)
    if not _legal_gate_valid(result, legal_present):
        raise ValueError("missing open legal gate")
    return result


async def execute_tech_ops_advisory(task_input: dict, event_sink=None) -> dict:
    emit = event_sink or (lambda _event: None)
    try:
        emit({"stage": "INPUT_SCALING", "action": "STARTED", "status": "RUNNING",
              "safeSummary": "current Market·BM과 확정 TechOps 입력을 근거 ledger로 정리합니다."})
        scaled = await scale_tech_ops_input(task_input)
    except ValidationError as failure:
        raise ProviderFailure("INVALID_REQUEST", "FIELD_CONSTRAINT_VIOLATION", 400, False) from failure
    emit({"stage": "EVIDENCE_ENRICHMENT", "action": "STARTED", "status": "RUNNING",
          "safeSummary": "선택적 외부 근거를 확인합니다. 실패해도 canonical 근거는 유지됩니다."})
    web, diagnostics = await collect_external_evidence(scaled["productSummary"], scaled["advisorFacts"])
    scaled["layer2Evidence"] = [*scaled["layer2Evidence"], *web][:24]
    context = {"productSummary": scaled["productSummary"], "legalHandoffProvided": task_input.get("legalHandoff") is not None,
        "layer1Facts": scaled["advisorFacts"], "layer2Evidence": scaled["layer2Evidence"],
        "userEvidence": scaled["userEvidence"], "externalResearch": diagnostics}
    emit({"stage": "ADVISORY_GENERATION", "action": "STARTED", "status": "RUNNING",
          "safeSummary": "7개 상용화 조언과 파일럿·비용·준비도·게이트를 작성합니다."})
    raw = await _request(context, False)
    try:
        emit({"stage": "RESULT_VALIDATION", "action": "STARTED", "status": "RUNNING",
              "safeSummary": "결과 계약과 모든 basis ID 연결을 검증합니다."})
        result = _validate(raw, scaled, context["legalHandoffProvided"])
    except (ValidationError, ValueError):
        emit({"stage": "BOUNDED_REPAIR", "action": "STARTED", "status": "RUNNING",
              "safeSummary": "계약 위반 결과를 한 번만 전체 재작성합니다."})
        repair_context = dict(context); repair_context["invalidResult"] = raw
        repaired = await _request(repair_context, True)
        try:
            result = _validate(repaired, scaled, context["legalHandoffProvided"])
        except (ValidationError, ValueError) as failure:
            raise ProviderFailure("RESULT_SCHEMA_INVALID", "AI_RESULT_INVALID", 502, False,
                schema_name="tech_ops_advisory_v1") from failure
    emit({"stage": "RESULT_VALIDATION", "action": "COMPLETED", "status": "RUNNING",
          "safeSummary": "상용화 자문 결과와 근거 연결 검증을 완료했습니다."})
    return result.model_dump(mode="json")
