import asyncio
import json

from app.tasks.idea_brief.service import execute_idea_brief_derivation


async def main() -> None:
    result = await execute_idea_brief_derivation({
        "mode": "INITIAL",
        "overview": "지역 식당의 재고 폐기를 줄이는 서비스",
        "fields": [],
        "attachmentFileIds": [],
        "attachmentDocuments": [],
        "fieldMetadata": [
            {"fieldKey": "problem", "requiredForConcept": True, "regulatorySensitive": False},
            {"fieldKey": "physicalActivity", "requiredForConcept": True, "regulatorySensitive": True},
            {"fieldKey": "personalData", "requiredForConcept": True, "regulatorySensitive": True},
        ],
    })
    print(json.dumps({"status": "SUCCEEDED", "readiness": result["readiness"]}, ensure_ascii=False))


if __name__ == "__main__":
    asyncio.run(main())
