"""사용자 승인형 Finance structured-provider smoke.

비밀키, prompt, provider raw body를 출력하지 않는다. 이 도구는 실제 provider
호출 비용이 발생하므로 자동 테스트나 기본 startup에서 실행되지 않는다.
"""

from __future__ import annotations

import asyncio

from pydantic import ValidationError

from app.providers import ProviderFailure
from app.tasks.finance_analysis_report.models import FinanceAnalysisReportResult
from app.tasks.finance_analysis_report.service import execute_finance_analysis_report


SCHEMA_NAME = "finance_analysis_report_v1"


async def _run() -> int:
    task_input = {
        "snapshotId": "provider-smoke-snapshot",
        "snapshotHash": "sha256:" + "0" * 64,
        "sourceMarketResearchVersionId": 1,
        "sourceBusinessModelVersionId": 1,
        "sourceTechOpsSnapshotId": None,
        "deterministicResult": {
            "calculation": {"years": [
                {"year": 1, "revenue": 1000000, "operatingProfit": -100000},
                {"year": 2, "revenue": 1500000, "operatingProfit": 100000},
                {"year": 3, "revenue": 2200000, "operatingProfit": 300000},
            ]},
            "monteCarlo": {"p10": -200000, "p50": 100000, "p90": 400000},
        },
    }
    try:
        result = await execute_finance_analysis_report(task_input)
        FinanceAnalysisReportResult.model_validate(result)
    except ProviderFailure as failure:
        print(f"schemaName={SCHEMA_NAME}")
        print(f"httpCategory={failure.code}")
        print("resultValidation=NOT_REACHED")
        return 1
    except ValidationError:
        print(f"schemaName={SCHEMA_NAME}")
        print("httpCategory=SUCCESS")
        print("resultValidation=FAILED")
        return 1
    print(f"schemaName={SCHEMA_NAME}")
    print("httpCategory=SUCCESS")
    print("resultValidation=PASSED")
    return 0


if __name__ == "__main__":
    raise SystemExit(asyncio.run(_run()))
