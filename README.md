# AIVLE 사업 검증 플랫폼

사업 아이디어를 다음 제품 여정으로 검증하고 실행 결과의 근거 계보를 보존하는 통합 플랫폼입니다.

1. 아이디어
2. 사업안(Concept Portfolio V2)
3. 시장 분석
4. 사업 모델 분석
5. 기술·운영 분석
6. 재무 분석
7. 트윈 패널 조사
8. 마케팅 콘텐츠 제작

`Marketing Visual`은 통합 Marketing Content 화면과 별도로 기존 런타임 호환성을 유지합니다.

## 실행 권한 구조

- Backend는 프로젝트 소유권, current/history/stale, source lineage 및 제품 상태의 canonical authority입니다.
- AI는 Backend가 확정한 immutable 입력을 실행하는 엔진입니다. AI 로컬 디스크는 canonical 저장소가 아닙니다.
- TaskRun/TaskAttempt는 실행·재시도·lease·recovery authority입니다.
- JobEvent/SSE는 진행률과 갱신 신호이며 제품 상태 자체의 authority가 아닙니다.
- Object Storage는 이미지, 증거 및 Market Research2 raw ledger 같은 durable artifact authority입니다.

브라우저는 Spring Backend만 호출합니다. Backend worker가 내부 토큰과 실행 계보를 포함해 FastAPI의
`POST /internal/v1/ai/executions`를 호출합니다.

## Market recollect

Market FULL 실행이 만든 Research2 원장(`run.jsonl`, `a3_bodies.json`, `result.json`)은 체크섬과
lineage가 포함된 제한적 ZIP bundle로 Object Storage에 저장됩니다. 다음 recollect TaskRun은 소유권,
current Concept/Market version, manifest 및 파일 체크섬을 검증한 뒤 임시 workspace의
`runs-generated/<sourceRun>`에 원자를 복원하고 동일 Research2 recollect 엔진을 실행합니다.
원장이 없거나 손상되면 신규 FULL 실행으로 조용히 대체하지 않고 실패합니다.

## 환경변수 정책

실제 비밀값은 `.env`에 두고 저장소에는 커밋하지 않습니다. 계약 기준은 `.env.example`과
`scripts/audit_env_contract.py`입니다.

- `AI_API_KEY`: 플랫폼 structured provider
- `MARKET_RESEARCH_OPENAI_API_KEY`: Compose에서 Market Research2에 우선 전달할 전용 키
- `OPENAI_API_KEY`: Research2 직접 실행 호환 키. Compose에서는 전용 키가 비어 있을 때 fallback
- `AI_INTERNAL_SERVICE_TOKEN` / `AI_SERVER_INTERNAL_API_KEY`: AI와 Backend가 공유하는 내부 실행 토큰
- `TWIN_BANK_HOST_DIR`: 운영 Twin Bank read-only mount. E2E는 실제 조사 자산 대신 synthetic fixture 사용

`OPENAI_BASE_URL`을 지정하는 경우 단순 Chat Completions 호환만으로는 충분하지 않습니다. 현재
Research2가 사용하는 OpenAI Responses API와 `web_search` tool 계약을 지원해야 합니다.
일반 AI용 `AI_API_KEY`만 설정하고 Market 전용/fallback 키를 비워 두면 Market은 구성 완료로 보지 않습니다.

비밀값을 출력하지 않는 로컬 사전 점검:

```bash
python scripts/check_local_env.py --compose
```

Twin Bank의 Compose host mount 이름은 `TWIN_BANK_HOST_DIR` 하나뿐이다.
`TWIN_BANK_PATH`는 alias가 아니며 precheck가 이름 변경 안내와 함께 실패시킨다.

Backend의 AI HTTP client는 작업 규모별로 분리됩니다. 기본 짧은 작업은 30초, Marketing Content와
TechOps Advisory는 7분, Concept Portfolio는 15분, Twin Survey는 14분이며, fresh Market collection은 20분 worker
deadline과 22분 transport/lease envelope를 사용합니다.

Docker 내부 Object Storage endpoint는 `http://minio:9000`, host browser용 presigned endpoint는
기본 `http://localhost:9000`입니다. `OBJECT_STORAGE_PUBLIC_ENDPOINT`에는 `minio`, `backend`,
`ai-server` 같은 Docker 전용 hostname을 사용할 수 없습니다.

새 환경변수를 추가한 경우 다음 검사를 통과해야 합니다.

```bash
python scripts/audit_env_contract.py
```

## 로컬 구성

`compose.yaml`은 PostgreSQL, MinIO, Backend, AI, Frontend를 구성합니다. 실제 secret `.env`를
출력하거나 저장소에 포함하지 마십시오. E2E 정적 구성은 다음처럼 별도 예제를 사용합니다.

```bash
docker compose --env-file .env.e2e.example -f compose.yaml -f compose.e2e.yaml config
```

Docker 이미지에는 Research2 generated runs, outputs, 실제 Twin Bank 및 `.env*`가 포함되지 않도록
`ai/.dockerignore`로 차단합니다. canonical seed fixture는 런타임 이미지가 아니라 테스트/개발 권한에
따라 명시적으로 취급합니다.

## 문서

- 구현·계약: `docs/rebuild/`
- API: `docs/api/openapi.yaml`
- 검증 결과: `docs/rebuild/verification/`
- 진행 보고: `docs/rebuild/progress/`
