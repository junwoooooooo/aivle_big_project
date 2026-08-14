import json
import os
from typing import Any

from app.providers import execute_structured_prompt
from app.tasks.tech_ops_advisor.models import AdvisoryExpansion, AdvisoryInput, AdvisoryResult
from app.tasks.tech_ops_external_evidence import collect_external_evidence
from app.tasks.tech_ops_input_scaler import scale_tech_ops_input


SYSTEM_PROMPT = """당신은 한국 스타트업의 기술·운영 상용화 검증 자문가입니다.
주어진 시장분석·BM 원본 사실을 제품 구현, 운영, 파트너, 파일럿, 확장 조건의 관점에서 검토합니다.
추측으로 금액·CAC·COGS·법률 결론을 만들지 마십시오. 법률 결과가 없으면 OPEN 법률 검토 게이트로만 표현하십시오.
각 조언은 반드시 제공된 FACT-* 또는 EXT-* 근거 ID를 인용하고, 파일럿에서 검증하는 방법을 제시해야 합니다."""

OUTPUT_CONTRACT = """
한국어 JSON 객체 하나만 반환하십시오. 최상위 키는 decision, summary, advice, gates, operatingCosts,
readiness, pilotPlan, disclaimer만 사용하십시오.
decision은 GO, CONDITIONAL_GO, REVISE, NO_GO 중 하나입니다.
advice 각 항목은 area, priority, advice, validationMethod, basisIds를 가져야 하며 area는
MARKET_BM, PRODUCT_TECH, OPERATIONS, RISK_GATE, PARTNER_SUPPLY, PILOT, SCALE 중 하나입니다.
gates 각 항목은 title, owner, status, exitCriteria, basisIds를 가집니다.
operatingCosts 각 항목은 category, driver, trigger, measurementUnit, behavior, pilotMeasurement, note, basisIds를 가집니다.
readiness 각 항목은 topic, priority, assessment, watchouts, controls, validationMethod, basisIds를 가집니다.
pilotPlan은 objective, scope, metrics, stopConditions, scaleConditions를 가집니다.

중요 규칙:
1) basisIds에는 아래 FACT-* 또는 EXT-* ID를 최소 하나 넣습니다. 근거가 없는 조언을 만들지 마십시오.
2) summary와 조언은 실제 제품명, 시장/BM 수치·Proxy·가격·수요·경쟁·채널 중 제공된 사실을 구체적으로 인용합니다.
   "시장 조사 강화", "기술 검토 필요", "운영 체계 구축" 같은 일반론만으로 한 항목을 작성하지 마십시오.
   각 항목에는 해당 서비스의 실제 고객·가격·채널·경쟁·수요·제품/BM 가정 중 하나와 그로 인한 구체적 운영 조건을 함께 씁니다.
3) MARKET_BM, PRODUCT_TECH, OPERATIONS, RISK_GATE, PARTNER_SUPPLY, PILOT, SCALE 영역을 모두 포함해 7개 조언을 만드십시오.
4) 게이트는 6개 이상, 비용 계측은 결제/통합·인증/접근통제·운영/CS·인프라/알림·수령/품질 중 해당 영역을 포함해 5개 이상 만드십시오.
   비용은 금액 예측이 아니라 비용 유발 요인, 발생 조건, 계측 단위, 파일럿 계측 방법이어야 합니다.
   measurementUnit에 '원'만 쓰지 말고 주문 건, 활성 사용자 월, 인증 시도 건, CS 티켓, 운영자 처리 분 등 실제 계측 단위를 씁니다.
5) readiness는 DATA_AI, CUSTOMER_TRUST, OBSERVABILITY_SLA, SCALABILITY를 각각 정확히 하나씩 만드십시오.
   assessment는 이 사업의 현재 상태를 최소 3문장으로 설명하고, watchouts와 controls에는 각각 최소 3개의
   구체적인 문장을 넣으십시오. 예를 들어 예약 통합 서비스라면 예약 동기화, 취소/노쇼, 일정 충돌, 채널/API
   연동 실패처럼 해당 사업에 실제로 맞는 운영 조건을 사용하십시오. 일반적인 '데이터 품질 문제'만 쓰지 마십시오.
   validationMethod에는 어떤 이벤트·로그·설문·운영 기록을 어떤 단위로 볼지 작성하십시오.
7) gates의 exitCriteria는 최소 2문장으로 작성하고, 단순히 '검토 완료'가 아니라 확인할 흐름, 증빙, 통과
   조건을 명시하십시오. operatingCosts의 pilotMeasurement는 최소 2문장으로 작성하십시오.
6) 법률 판단은 하지 마십시오. legalHandoff가 없으면 법률 검토를 OPEN 게이트로 두십시오.
"""


def _as_list(value: Any) -> list:
    if value is None:
        return []
    return value if isinstance(value, list) else [value]


def _choice(value: Any, allowed: set[str], default: str) -> str:
    text = str(value or "").strip().upper().replace("-", "_").replace(" ", "_")
    return text if text in allowed else default


def _basis_ids(value: Any, fallback: str) -> list[str]:
    values = [str(item).strip() for item in _as_list(value) if str(item).strip()]
    return values or [fallback]


def _normalize_model_output(raw: dict, fallback_basis: str) -> dict:
    """Accept harmless key aliases while preserving the strict public contract."""
    data = raw.get("evaluation", raw.get("result", raw.get("data", raw)))
    if not isinstance(data, dict):
        return raw
    output = dict(data)
    output["decision"] = _choice(output.get("decision"), {"GO", "CONDITIONAL_GO", "REVISE", "NO_GO"}, "REVISE")
    for item in _as_list(output.get("advice")):
        if isinstance(item, dict):
            item["area"] = _choice(item.get("area"), {"MARKET_BM", "PRODUCT_TECH", "OPERATIONS", "RISK_GATE", "PARTNER_SUPPLY", "PILOT", "SCALE"}, "OPERATIONS")
            item["priority"] = _choice(item.get("priority"), {"CRITICAL", "HIGH", "MEDIUM", "LOW"}, "MEDIUM")
            item["basisIds"] = _basis_ids(item.get("basisIds") or item.get("basis_ids"), fallback_basis)
    output["advice"] = _as_list(output.get("advice"))
    for item in _as_list(output.get("gates")):
        if isinstance(item, dict):
            item["status"] = _choice(item.get("status"), {"OPEN", "READY", "BLOCKED"}, "OPEN")
            item["basisIds"] = _basis_ids(item.get("basisIds") or item.get("basis_ids"), fallback_basis)
    output["gates"] = _as_list(output.get("gates"))
    for item in _as_list(output.get("operatingCosts")):
        if isinstance(item, dict):
            item["behavior"] = _choice(item.get("behavior"), {"FIXED", "VARIABLE", "STEP", "UNKNOWN"}, "UNKNOWN")
            item["basisIds"] = _basis_ids(item.get("basisIds") or item.get("basis_ids"), fallback_basis)
    output["operatingCosts"] = _as_list(output.get("operatingCosts"))
    for item in _as_list(output.get("readiness")):
        if isinstance(item, dict):
            item["topic"] = _choice(item.get("topic"), {"DATA_AI", "CUSTOMER_TRUST", "OBSERVABILITY_SLA", "SCALABILITY"}, "DATA_AI")
            item["priority"] = _choice(item.get("priority"), {"CRITICAL", "HIGH", "MEDIUM", "LOW"}, "MEDIUM")
            item["watchouts"] = _as_list(item.get("watchouts"))
            item["controls"] = _as_list(item.get("controls"))
            item["basisIds"] = _basis_ids(item.get("basisIds") or item.get("basis_ids"), fallback_basis)
    output["readiness"] = _as_list(output.get("readiness"))
    if isinstance(output.get("pilotPlan"), dict):
        for key in ("scope", "metrics", "stopConditions", "scaleConditions"):
            output["pilotPlan"][key] = _as_list(output["pilotPlan"].get(key))
    return output


def _parse_advisory(raw: dict, fallback_basis: str) -> AdvisoryResult:
    try:
        return AdvisoryResult.model_validate(raw)
    except Exception:
        return AdvisoryResult.model_validate(_normalize_model_output(raw, fallback_basis))


def _inferred_cost_behavior(item):
    """Do not expose an undecided machine enum when the driver makes it clear."""
    if item.behavior != "UNKNOWN":
        return item
    text = f"{item.category} {item.driver} {item.trigger} {item.measurementUnit}".lower()
    if any(token in text for token in ("payment", "order", "booking", "reservation", "transaction", "api", "notification", "ticket", "server", "cloud", "infrastructure", "quality", "feedback", "결제", "주문", "예약", "거래", "알림", "티켓")):
        behavior = "VARIABLE"
    elif any(token in text for token in ("staff", "headcount", "hire", "labor", "인력", "채용", "급여", "인건비")):
        behavior = "STEP"
    elif any(token in text for token in ("rent", "subscription", "license", "lease", "임차", "구독", "라이선스", "고정")):
        behavior = "FIXED"
    else:
        return item
    return item.model_copy(update={"behavior": behavior})


def _normalize_cost_behaviors(report: AdvisoryResult) -> AdvisoryResult:
    return report.model_copy(update={"operatingCosts": [_inferred_cost_behavior(item) for item in report.operatingCosts]})


def _is_substantive(value: AdvisoryResult) -> bool:
    generic = ("시장 조사 강화", "기술 검토 필요", "운영 체계 구축", "마케팅 전략 필요", "공급망 강화")
    return (
        len(value.summary.strip()) >= 80
        and len(value.advice) >= 7
        and {item.area for item in value.advice} >= {"MARKET_BM", "PRODUCT_TECH", "OPERATIONS", "RISK_GATE", "PARTNER_SUPPLY", "PILOT", "SCALE"}
        and len(value.gates) >= 6
        and len(value.operatingCosts) >= 5
        and {item.topic for item in value.readiness} == {"DATA_AI", "CUSTOMER_TRUST", "OBSERVABILITY_SLA", "SCALABILITY"}
        and all(item.basisIds for item in value.advice)
        and all(len(item.advice.strip()) >= 70 and item.advice not in generic for item in value.advice)
        and all(item.behavior != "UNKNOWN" and item.measurementUnit.strip() not in {"원", "KRW"} for item in value.operatingCosts)
        and all(len(item.pilotMeasurement.strip()) >= 80 for item in value.operatingCosts)
        and all(len(item.exitCriteria.strip()) >= 150 for item in value.gates)
        and all(item.title.strip() not in {"법률 검토", "시장 검증", "기술 검토", "운영 검증", "파일럿 검증"} for item in value.gates)
        and all(len(item.watchouts) >= 2 and len(item.controls) >= 2 for item in value.readiness)
    )


async def _request_advice(advisor_input: dict) -> dict:
    return await execute_structured_prompt(
        SYSTEM_PROMPT + "\n" + OUTPUT_CONTRACT + "\n"
        "Evidence IDs may be FACT-*, EXT-* or WEB-*. WEB-* is an external URL "
        "for validation context, not a confirmed numeric or legal conclusion. "
        "Never use a runId, generatedAt, stage status, seconds, llmCalls, or other execution metadata "
        "as the sole basis for advice, a gate, or a cost design. A gate must name the product's actual "
        "customer behavior, delivery flow, partner/API, price, or operating exception from a non-metadata fact.",
        json.dumps(advisor_input, ensure_ascii=False),
        # The deployment's AI_MODEL is the known working default.  A stronger
        # model can be opted in per environment without breaking deployments
        # whose OpenAI-compatible provider does not expose that model name.
        model_override=os.getenv("TECH_OPS_ADVISOR_MODEL", "").strip() or None,
        response_schema=None,
        schema_name=None,
        task_type="TECH_OPS_ADVISORY",
        timeout_seconds_override=180,
    )


async def _request_operations_expansion(advisor_input: dict, report: AdvisoryResult) -> dict:
    """D9-equivalent focused pass for cost, readiness and release gates."""
    contract = """
Return Korean JSON only with exactly operatingCosts, readiness, gates.
This is an expansion pass, not a summary. Preserve only supplied FACT-* / EXT-* IDs.
Create at least five operatingCosts. Each pilotMeasurement must be two practical sentences and use an observable unit
(booking, cancellation, API call, active user-month, CS ticket, operator-minute, integration incident), never only KRW.
Create exactly four readiness entries: DATA_AI, CUSTOMER_TRUST, OBSERVABILITY_SLA, SCALABILITY.
For every readiness entry write an assessment of at least three specific sentences, at least three watchouts, at least
three controls, and a validation method naming logs, events, interviews, or operating records.
Create at least six OPEN/READY/BLOCKED gates. Each exitCriteria must state the workflow to test, evidence to retain,
and the condition to pass. Do not use generic phrases such as 'build a system' or 'conduct research'.
Create the following six gate types in this exact order; adapt their wording to the supplied business facts rather than
copying an unrelated example:
1) serviceable-customer scope recalculation: direct target population and the behavior filters needed before using TAM/SAM;
2) core problem and customer-behavior validation: interview/usability/pilot proof for the claimed pain and adoption behavior;
3) competitive alternatives and differentiation: compare at least three realistic alternatives using official flows or evidence;
4) critical delivery-operation design: document every status, owner, exception and partner handoff in the core delivery flow
   (for a reservation service this means reservation/calendar sync, cancellation/no-show, reminders and operator exceptions);
5) legal/privacy/contract review: an OPEN expert review gate only, with the data and operating flow to submit;
6) price and repeat-use validation: test willingness to pay, actual frequency, retention/cancellation and reasons.
Each gate title must express its specific decision, not a generic title such as 'market validation gate' or 'technical review gate'.
For a reservation/calendar product, discuss the actual integration, sync, cancellation/no-show, access control,
operator exception, notification, and partner conditions only when the FACTS support them.
"""
    contract += "\nWEB-* IDs are also allowed when they are supplied as external validation context."
    expansion_input = {
        "productSummary": advisor_input["productSummary"],
        "layer1Facts": advisor_input["layer1Facts"],
        "layer2ExternalEvidence": advisor_input["layer2ExternalEvidence"],
        "currentAdvice": report.model_dump(mode="json", exclude={"layer1Facts", "layer2Evidence"}),
    }
    return await execute_structured_prompt(
        contract,
        json.dumps(expansion_input, ensure_ascii=False),
        model_override=os.getenv("TECH_OPS_ADVISOR_MODEL", "").strip() or None,
        response_schema=None,
        schema_name=None,
        task_type="TECH_OPS_OPERATIONS_EXPANSION",
        timeout_seconds_override=180,
    )


def _parse_expansion(raw: dict, fallback_basis: str) -> AdvisoryExpansion:
    data = raw.get("result", raw.get("data", raw)) if isinstance(raw, dict) else raw
    if not isinstance(data, dict):
        raise ValueError("operations expansion is not an object")
    for key in ("operatingCosts", "readiness", "gates"):
        for item in _as_list(data.get(key)):
            if isinstance(item, dict):
                item["basisIds"] = _basis_ids(item.get("basisIds") or item.get("basis_ids"), fallback_basis)
    return AdvisoryExpansion.model_validate(data)


def _is_substantive_expansion(value: AdvisoryExpansion) -> bool:
    generic_titles = {"법률 검토", "시장 검증", "기술 검토", "운영 검증", "파일럿 검증"}
    return (
        len(value.operatingCosts) >= 5
        and all(item.behavior != "UNKNOWN" and len(item.pilotMeasurement.strip()) >= 80 for item in value.operatingCosts)
        and len(value.readiness) == 4
        and all(len(item.assessment.strip()) >= 160 and len(item.watchouts) >= 3 and len(item.controls) >= 3 for item in value.readiness)
        and len(value.gates) >= 6
        and all(item.title.strip() not in generic_titles and len(item.exitCriteria.strip()) >= 150 for item in value.gates)
    )


async def generate_tech_ops_advisory(payload: dict) -> dict:
    value = AdvisoryInput.model_validate(payload)
    scaled = await scale_tech_ops_input({
        "marketResult": value.market,
        "bmResult": value.bm,
        "conceptHandoff": value.concept,
    })
    facts = scaled["layer1Facts"]
    upstream_evidence = scaled["layer2Evidence"]
    web_evidence, external_research = await collect_external_evidence(
        scaled["productSummary"], scaled["advisorFacts"],
    )
    # Web sources are supplementary Layer 2 material.  They are shown with
    # their URL and query snippet and can never overwrite Market/BM facts.
    evidence = [*web_evidence, *upstream_evidence]
    fallback_basis = facts[0]["factId"] if facts else "FACT-000"
    # The context is intentionally a fact ledger, not a lossy keyword summary.
    advisor_input = {
        "productSummary": scaled["productSummary"],
        "legalHandoffProvided": value.legalHandoff is not None,
        "layer1Facts": scaled["advisorFacts"],
        "layer2ExternalEvidence": evidence[:16],
        "marketSignals": scaled["marketSignals"],
        "bmAssumptions": scaled["bmAssumptions"],
        "externalResearch": external_research,
    }
    raw = await _request_advice(advisor_input)
    report: AdvisoryResult
    try:
        report = _parse_advisory(raw, fallback_basis)
        if not _is_substantive(report):
            raise ValueError("advisor output did not satisfy the evidence coverage contract")
    except Exception:
        repair_input = dict(advisor_input)
        repair_input["repairInstruction"] = (
            "Return a replacement that is grounded in business facts, not run metadata. Do not use generic gate titles such as "
            "legal review, market validation, technical review, operations validation, or pilot validation. Every exit criterion "
            "must be at least two concrete sentences: name the product workflow, the record or evidence to retain, and the measurable "
            "pass condition. Every cost measurement must name an observable event, operational owner/time, and a reason code or status. "
            "Use the supplied customer, price, channel, competitor, API, booking/cancellation/no-show, partner, or market facts."
        )
        report = _parse_advisory(await _request_advice(repair_input), fallback_basis)
        # A valid response must never become a 500 merely because its wording
        # did not meet the stricter advisory-quality target.  Return it with a
        # transparent notice; the next run can still improve it from the same
        # immutable Market/BM fact ledger.
        if not _is_substantive(report):
            report = report.model_copy(update={
                "disclaimer": (report.disclaimer + " 일부 조언은 근거 보강 재생성이 필요합니다.").strip(),
            })
    report = _normalize_cost_behaviors(report)
    try:
        expanded = _parse_expansion(await _request_operations_expansion(advisor_input, report), fallback_basis)
        if _is_substantive_expansion(expanded):
            report = report.model_copy(update={
                "operatingCosts": expanded.operatingCosts,
                "readiness": expanded.readiness,
                "gates": expanded.gates,
            })
    except Exception:
        # The primary advisory remains useful if the optional D9 expansion
        # cannot be parsed or the provider is temporarily unavailable.
        pass
    report = _normalize_cost_behaviors(report)
    # Layer 1/2 are generated deterministically, then returned beside Layer 3.
    return report.model_copy(update={
        "productName": scaled["productSummary"],
        "layer1Facts": facts,
        "layer2Evidence": evidence,
    }).model_dump(mode="json")
