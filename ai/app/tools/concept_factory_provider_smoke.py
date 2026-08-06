import asyncio
import json

from app.tasks.concept_candidate import execute_concept_candidate
from app.tasks.concept_legal_review import execute_concept_legal_review
from app.tasks.concept_redesign import execute_concept_redesign


async def main() -> None:
    candidate = await execute_concept_candidate({
        "ideaBriefSnapshotId": "provider-smoke",
        "variationFocus": "LOW_RISK_FAST_EXECUTION",
        "fields": [{"fieldKey": "problem", "value": "소상공인의 예약 노쇼"}],
    })
    legal = await execute_concept_legal_review({
        "candidate": candidate,
        "sharedContext": {
            "industry": "예약 중개", "region": "대한민국", "platformRole": "중개자",
            "transactionStructure": "사업자와 고객의 예약 중개", "payment": "예약금 결제",
            "personalData": "예약 연락처", "physicalActivities": [],
            "qualificationsAndPermits": [], "labelingAndAdvertising": ["가격 표시"],
            "officialEvidence": [{"referenceIndex": 0, "title": "공식 근거",
                "officialSourceUri": "https://www.law.go.kr/", "reviewedAt": "2026-08-06"}],
        },
    })
    redesigned = await execute_concept_redesign({
        "candidate": candidate, "safeConstraints": legal["requiredControls"] or ["명확한 고지"],
        "prohibitedVariants": legal["prohibitedVariants"],
    })
    print(json.dumps({"candidate": candidate, "legal": legal, "redesigned": redesigned}, ensure_ascii=False))


if __name__ == "__main__":
    asyncio.run(main())
