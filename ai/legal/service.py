# -*- coding: utf-8 -*-
"""법령 조사 파이프라인 라우터.

Spring 백엔드의 LegalReviewAiClient 포트가 호출한다. 입력은 확정된 StructuredPlan
섹션이고, 출력은 LegalReviewAiResponse와 같은 모양(10개 finding + 질문)이다.

AI 서버(ai/main.py)가 이 라우터를 마운트한다. 단독 실행하지 않는다.
"""
import os
import shutil
import tempfile
import threading
import traceback
import urllib.error
import uuid
from pathlib import Path

from fastapi import APIRouter, HTTPException, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from pydantic import BaseModel, Field

from . import aggregator
from . import legal_pipeline as lp

router = APIRouter()


async def log_validation_error(request: Request, exc: RequestValidationError):
    """호출자(Spring)가 보낸 모양이 안 맞을 때 원인을 바로 알 수 있게 남긴다.

    APIRouter에는 예외 처리기를 달 수 없어 ai/main.py가 앱에 등록한다.
    """
    body = exc.body
    if isinstance(body, (bytes, bytearray)):
        body = body.decode("utf-8", "replace")
    lp.log(f"[검증실패] errors={exc.errors()}")
    lp.log(f"[검증실패] body={str(body)[:1000]}")
    return JSONResponse(status_code=422, content={"detail": "요청 형식이 올바르지 않습니다."})

# 파이프라인은 법제처 API 호출과 LLM 호출이 길다. 동시 실행은 캐시 경합만 늘리므로 1건으로 제한한다.
_SLOT = threading.Semaphore(1)


class Section(BaseModel):
    code: str | None = None
    title: str | None = None
    content: str | None = None
    evidenceJson: str | None = None


class ConfirmedFact(BaseModel):
    """질문 답변으로 확정된 정보. source_text에 직접 주입된다 (§4-3)."""
    key: str | None = None
    value: str | None = None
    source: str | None = None
    answeredAt: str | None = None


class LegalReviewRequest(BaseModel):
    projectId: int | None = None
    structuredPlanId: int | None = None
    sourceDocumentVersionId: int | None = None
    promptVersion: str | None = None
    sections: list[Section] = Field(default_factory=list)
    # 피드백 루프 확장 (전부 optional — 하위호환)
    mode: str | None = None                                # FULL | INCREMENTAL
    rerunCategories: list[str] = Field(default_factory=list)
    confirmedFacts: list[ConfirmedFact] = Field(default_factory=list)


@router.get("/legal/health")
def health():
    registry = lp.load_registry()
    category_map = aggregator.load_category_map()
    cache_files = len(list(lp.CACHE_DIR.glob("*.json"))) if lp.CACHE_DIR.exists() else 0
    try:
        backend = lp.resolve_llm_backend()
    except lp.PipelineError as error:
        backend = f"unavailable ({error})"
    return {
        "status": "ok",
        "routes": len(registry["routes"]),
        "categories": len(category_map["categories"]),
        "mapped_routes": len(category_map["routes"]),
        "cache_files": cache_files,
        "law_api_oc": lp.LAW_API_OC,
        "model": lp.MODEL,
        "llm_backend": backend,
    }


@router.post("/legal-review")
def legal_review(request: LegalReviewRequest):
    sections = [s.model_dump() for s in request.sections]
    run_id = f"legal-{uuid.uuid4().hex[:12]}"
    label = f"StructuredPlan #{request.structuredPlanId}" if request.structuredPlanId else "StructuredPlan"
    work_dir = Path(tempfile.mkdtemp(prefix=f"{run_id}-"))
    keep_work_dir = False

    rerun = request.rerunCategories if (request.mode or "").upper() == "INCREMENTAL" else None
    confirmed_facts = [f.model_dump() for f in request.confirmedFacts]
    if rerun:
        lp.log(f"[{run_id}] INCREMENTAL 요청: rerunCategories={rerun}")

    with _SLOT:
        try:
            outcome = lp.review_from_sections(
                sections, work_dir, label,
                rerun_categories=rerun, confirmed_facts=confirmed_facts)
            result = aggregator.build(
                outcome["state"], outcome["screenings"], outcome["screen_audit"], sections)
        except lp.BackendUnavailableError as error:
            keep_work_dir = True
            lp.log(f"[{run_id}] LLM·외부 호출 실패: {error}")
            raise HTTPException(status_code=503, detail=str(error))
        except lp.PipelineError as error:
            keep_work_dir = True
            lp.log(f"[{run_id}] 파이프라인 중단: {error}")
            raise HTTPException(status_code=422, detail=str(error))
        except (urllib.error.URLError, TimeoutError, ConnectionError) as error:
            keep_work_dir = True
            lp.log(f"[{run_id}] 외부 API 실패: {error}")
            raise HTTPException(status_code=503, detail="법령 조회 서비스에 연결하지 못했습니다.")
        except Exception as error:  # noqa: BLE001 - 원인은 로그로 남기고 안전한 메시지만 반환
            keep_work_dir = True
            lp.log(f"[{run_id}] 예기치 못한 실패: {error}\n{traceback.format_exc()}")
            raise HTTPException(status_code=503, detail="법령 조사를 완료하지 못했습니다.")
        finally:
            # LEGAL_KEEP_WORKDIR=1 이면 성공한 실행도 보존한다.
            # 집계 로직만 고칠 때 LLM을 다시 부르지 않고 저장된 state로 검증할 수 있다.
            if keep_work_dir or os.getenv("LEGAL_KEEP_WORKDIR") == "1":
                lp.log(f"[{run_id}] 작업 디렉터리 보존: {work_dir}")
            else:
                shutil.rmtree(work_dir, ignore_errors=True)

    if result["warnings"]:
        lp.log(f"[{run_id}] 경고: {result['warnings']}")

    backend = outcome["state"].get("llm_backend") or "unknown"
    return {
        "provider": "legal-pipeline",
        "model": f"{lp.MODEL}({backend})+law.go.kr",
        "providerRequestId": run_id,
        "overallRiskLevel": result["overallRiskLevel"],
        "summary": result["summary"],
        "findings": result["findings"],
        "questions": result["questions"],
        "revisionRequests": outcome.get("revision_requests") or [],
    }
