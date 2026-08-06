import asyncio
import json

from app.tasks.marketing_content import execute_marketing_content


async def main() -> None:
    result = await execute_marketing_content({
        "source": {
            "conceptName": "동네 식재료 연결", "targetSegment": "1인 가구", "problem": "소량 구매 어려움",
            "valueProposition": "필요한 만큼 당일 수령", "positioning": "지역 기반 소량 장보기",
            "keyFeatures": ["소분 주문"], "pricing": "주문 수수료", "channels": ["모바일"],
            "competitorDifferentiators": ["동네 재고"], "allowedClaims": ["당일 수령 가능 지역 운영"],
            "prohibitedClaims": ["전 지역 최저가"], "requiredDisclosures": ["지역별 제공 범위 상이"],
            "sourceSnapshotHash": "sha256:" + "0" * 64,
        },
        "request": {
            "contract": "marketing-content-request-v1", "planningSnapshotId": "smoke-planning",
            "contentType": "SOCIAL_POST", "channel": "Instagram", "purpose": "인지도 확보",
            "tone": "친근함", "length": "SHORT", "requiredPhrases": [], "excludedPhrases": [],
            "additionalInstruction": None,
        },
    })
    print(json.dumps({
        "ok": True,
        "contract": result.get("contract"),
        "contentType": result.get("contentType"),
        "hasTitle": bool(result.get("title")),
        "hasBody": bool(result.get("body")),
        "legalCompliant": result.get("legalReview", {}).get("compliant"),
        "artifactRefCount": len(result.get("artifactRefs", [])),
    }, ensure_ascii=False))


if __name__ == "__main__":
    asyncio.run(main())
