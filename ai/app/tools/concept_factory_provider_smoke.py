import asyncio
import hashlib
import json
import os

from app.tasks.concept_candidate import execute_concept_candidate
from app.tasks.concept_legal_review import execute_concept_legal_review
from app.tasks.concept_redesign import execute_concept_redesign


def legal_fact_pattern(candidate: dict) -> dict:
    semantics = {item["fieldKey"]: item for item in candidate["valueSemantics"]}

    def governed(field: str) -> dict:
        return {"value": candidate[field], **{
            key: semantics[field][key] for key in ("source", "authority", "decision")
        }}

    def sensitive(field: str, level: str) -> dict:
        return {**governed(field), "legalSensitivity": level}

    return {"schemaVersion": "2.0", "jurisdiction": "KR",
        "actorRoles": governed("actorRoles"), "platformRole": governed("platformRole"),
        "commercialRoles": {field: governed(field) for field in
            ("providerRole", "sellerRole", "intermediaryRole")},
        "transactionFlow": governed("transactionFlow"), "paymentFlow": governed("paymentFlow"),
        "personalDataUsage": governed("personalDataUsage"),
        "physicalActivities": governed("physicalActivities"),
        "partnerRoles": {"partnerModel": governed("partnerModel"),
            "partnerRequirements": governed("partnerRequirements")},
        "qualificationRequirements": governed("qualificationRequirements"),
        "advertisingClaims": governed("advertisingClaims"), "operatingModel": governed("operatingModel"),
        "hypotheses": {
            "targetRegion": sensitive("targetRegion", "LEGAL_SENSITIVE"),
            "revenueModel": sensitive("revenueModel", "LEGAL_SENSITIVE"),
            "price": sensitive("price", "LEGAL_SENSITIVE"),
            "channels": sensitive("channels", "POTENTIALLY_LEGAL_SENSITIVE"),
            "differentiators": sensitive("differentiators", "POTENTIALLY_LEGAL_SENSITIVE"),
        }}


async def main() -> None:
    if not os.getenv("MOLEG_API_KEY", "").strip():
        raise RuntimeError("MOLEG_API_KEY is required for the official-evidence provider smoke")
    candidate = await execute_concept_candidate({
        "ideaBriefSnapshotId": "provider-smoke",
        "generationStrategy": "EXPLORE", "candidateIndex": 1, "originalCandidate": False,
        "diversityFocus": "LOW_RISK_FAST_EXECUTION",
        "fields": [
            {"fieldKey": "ideaOverview", "value": "예약 확인 업무 자동화", "source": "USER_INPUT", "authority": "LOCKED"},
            {"fieldKey": "problem", "value": "예약 중개 과정의 반복 확인 업무", "source": "USER_INPUT", "authority": "LOCKED"},
            {"fieldKey": "targetUsers", "value": "예약 서비스를 운영하는 소형 매장", "source": "USER_INPUT", "authority": "LOCKED"},
            {"fieldKey": "targetRegion", "value": "대한민국", "source": "USER_INPUT", "authority": "LOCKED"},
        ],
        "acceptedConceptFingerprints": [],
    })
    fact_pattern = legal_fact_pattern(candidate)
    serialized_pattern = json.dumps(fact_pattern, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
    fact_pattern_hash = "sha256:" + hashlib.sha256(serialized_pattern.encode()).hexdigest()
    legal = await execute_concept_legal_review({
        "legalFactPattern": fact_pattern, "factPatternHash": fact_pattern_hash,
        "externalFactContext": {
            "sourceSnapshotHash": "sha256:" + "a" * 64,
            "registryVersion": os.getenv("LEGAL_REGISTRY_VERSION", "legal-registry-v1"),
            "facts": [{"factKey": "fixedJurisdiction", "value": "대한민국",
                "source": "USER_INPUT", "authority": "LOCKED"}],
        },
    })
    redesigned = None
    if legal["status"] == "REDESIGNABLE":
        redesigned = await execute_concept_redesign({
            "candidate": candidate, "safeConstraints": legal["requiredControls"],
            "prohibitedVariants": legal["prohibitedVariants"],
            "designGaps": legal["redesignRequirements"], "legalFactPattern": fact_pattern,
        })
    print(json.dumps({
        "status": legal["status"],
        "evidenceCount": len(legal["officialEvidence"]),
        "redesignCompleted": bool(redesigned and redesigned.get("conceptName")),
    }, ensure_ascii=False))


if __name__ == "__main__":
    asyncio.run(main())
