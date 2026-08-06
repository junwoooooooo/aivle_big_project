import asyncio
import json
import os

from app.tasks.concept_candidate import execute_concept_candidate
from app.tasks.concept_legal_review import execute_concept_legal_review
from app.tasks.concept_redesign import execute_concept_redesign


async def main() -> None:
    if not os.getenv("MOLEG_API_KEY", "").strip():
        raise RuntimeError("MOLEG_API_KEY is required for the official-evidence provider smoke")
    candidate = await execute_concept_candidate({
        "ideaBriefSnapshotId": "provider-smoke",
        "variationFocus": "LOW_RISK_FAST_EXECUTION",
        "fields": [
            {"fieldKey": "problem", "value": "예약 중개 과정의 반복 확인 업무"},
            {"fieldKey": "targetCustomers", "value": "예약 서비스 이용자"},
            {"fieldKey": "usageContext", "value": "온라인 예약"},
            {"fieldKey": "targetRegion", "value": "대한민국"},
            {"fieldKey": "personalData", "value": "예약 연락처"},
            {"fieldKey": "payment", "value": "예약금 결제"},
        ],
    })
    legal = await execute_concept_legal_review({
        "candidate": candidate,
        "sharedContext": {
            "sourceSnapshotHash": "sha256:" + "a" * 64,
            "registryVersion": os.getenv("LEGAL_REGISTRY_VERSION", "legal-registry-v1"),
            "fields": [
                {"fieldKey": "problem", "value": "예약 확인 업무", "provenance": "SOURCE_EXTRACTED"},
                {"fieldKey": "targetCustomers", "value": "예약 서비스 이용자", "provenance": "SOURCE_EXTRACTED"},
                {"fieldKey": "usageContext", "value": "온라인 예약", "provenance": "SOURCE_EXTRACTED"},
                {"fieldKey": "targetRegion", "value": "대한민국", "provenance": "SOURCE_EXTRACTED"},
                {"fieldKey": "personalData", "value": "예약 연락처", "provenance": "SOURCE_EXTRACTED"},
                {"fieldKey": "payment", "value": "예약금 결제", "provenance": "SOURCE_EXTRACTED"},
            ],
        },
    })
    redesigned = await execute_concept_redesign({
        "candidate": candidate,
        "safeConstraints": legal["requiredControls"] or ["명확한 고지"],
        "prohibitedVariants": legal["prohibitedVariants"],
    })
    print(json.dumps({
        "status": legal["status"],
        "evidenceCount": len(legal["officialEvidence"]),
        "redesignCompleted": bool(redesigned.get("conceptName")),
    }, ensure_ascii=False))


if __name__ == "__main__":
    asyncio.run(main())
