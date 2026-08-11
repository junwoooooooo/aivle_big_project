# Session 5 Docker Final Verification Runbook

이 문서는 사용자가 Provider/MOLEG/Market/Twin/Image Provider와 실제 Browser를 포함한 최종 검증을 수행하기 위한 순서다. 명령은 `target` 디렉터리에서 실행한다. 비밀값과 Twin Bank 파일은 commit하지 않는다.

## 1. 사전 준비

```powershell
Copy-Item .env.example .env
```

`.env`에 실제 환경에 맞게 최소 다음을 채운다.

- `POSTGRES_PASSWORD`, `JWT_SECRET`
- `MINIO_ROOT_PASSWORD`, `OBJECT_STORAGE_BUCKET`
- `AI_PROVIDER`, `AI_API_KEY`, `AI_MODEL`, 필요 시 `AI_BASE_URL`
- `AI_INTERNAL_SERVICE_TOKEN`
- Backend AI timeout: `AI_SERVER_READ_TIMEOUT`, `AI_SERVER_TWIN_SURVEY_READ_TIMEOUT` (기본 `14m`)
- Market/BM: `MARKET_RESEARCH_OPENAI_API_KEY`, `OPENAI_BASE_URL`, `KOSIS_API_KEY`, `DART_API_KEY`, `BM_MODEL`
- Finance: `TAVILY_API_KEY`
- Twin: `TWIN_CONCURRENCY`, `TWIN_BANK_HOST_DIR`
- Marketing Visual: `MARKETING_COPY_MODEL`, `MARKETING_IMAGE_MODEL`
- legal: `MOLEG_API_KEY`, `MOLEG_API_BASE_URL`, `LEGAL_REGISTRY_VERSION`

Twin Bank host 디렉터리에 필요한 외부 파일을 배치한다. 저장소에 추가하지 않는다.

`TWIN_BANK_HOST_DIR`은 저장소 밖의 승인된 디렉터리를 가리켜야 한다. 파일명·크기만 확인하는 절차는 [10_TWIN_BANK_ASSET_CONTRACT.md](10_TWIN_BANK_ASSET_CONTRACT.md)를 따른다.

## 2. 구성 및 기동

```powershell
docker compose config --quiet
docker compose up -d --build
docker compose ps
docker compose logs --no-color --tail=100 postgres minio minio-init ai-server backend frontend
```

기대 결과: postgres/minio/ai-server/backend/frontend healthy, minio-init completed, Twin Bank mount가 `/app/app/twin/bank:ro`로 표시된다.

## 3. 사용자 Journey

1. `http://localhost:${FRONTEND_PORT:-3000}` 접속 후 signup/login 한다.
2. Project를 생성하고 Project Shell의 journey/helper/Work Center를 확인한다.
3. Idea를 입력·확정하고 current snapshot을 확인한다.
4. CPV2를 실행하고 결과 중 하나를 선택하여 READY_FOR_MARKET 상태를 만든다.
5. Market을 실제 실행한다. A1→A2→A3→A4→estimate→verdict, evidence/grade/caveat/not-found 및 partial 의미를 확인한다.
6. BM에서 4 planned cells와 budget/duration/team size를 저장하고 실행한다. BMC 9 blocks, fit/consistency/SWR, evidence/caveat 및 financial handoff를 확인한다.
7. Twin stimulus draft를 만들고 필요 시 X/Y를 편집한 후 50 또는 100 표본 survey를 실행한다. gate, CI/MDE, profile/interview/caveat/not-measurable를 확인한다.
8. TechOps preparation을 완료하고 proposal/snapshot을 확정한다.
9. Finance preparation에서 Market/BM/TechOps provenance를 확인하고 필요한 입력과 estimate를 처리한다. snapshot 확정 후 분석/report를 실행하고 P&L/cashflow/BEP/sensitivity/stress/Monte Carlo를 확인한다.
10. Marketing Content를 생성·편집·저장·finalize하고 legal claims/disclosures/controls와 revision history를 확인한다.
11. Marketing Visual에서 source summary/초깃값을 확인하고 promotion/copy/tone/format/keyword/source image를 수정하여 실행한다. preview, revision/tone/format, download/open을 확인한다.

## 4. Work Center/SSE

- 각 장시간 작업에서 QUEUED→RUNNING→COMPLETED/FAILED/NEEDS_INPUT과 실제 timestamp event를 확인한다.
- Network panel에서 Job/Project SSE가 연결되고, terminal event 뒤 canonical REST GET이 발생하는지 확인한다.
- SSE를 잠시 끊었다 복구해 Last-Event-ID cursor 재연결과 중복 이벤트 grouping을 확인한다.
- REST polling loop와 SSE payload를 최종 결과로 쓰는 동작이 없어야 한다.

## 5. retry/history/stale

1. retryable 실패 하나를 만든 뒤 다시 시도한다.
2. DB에서 실패 TaskRun과 새 TaskRun id가 모두 남고 기존 run이 revive되지 않았는지 확인한다.
3. CPV2 selection을 바꾸어 기존 Market가 STALE이 되는지 확인한다.
4. Market/BM/TechOps source를 갱신하여 Finance가 STALE이 되는지 확인한다.
5. Marketing Content revision을 추가한 뒤 과거 Visual의 source revision lineage가 유지되는지 확인한다.
6. Visual 실패가 완료된 Content 상태를 FAILED로 바꾸지 않는지 확인한다.

## 6. DB current/history 확인

```powershell
docker compose exec -T postgres sh -lc 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -P pager=off -c "select installed_rank,version,description,success from flyway_schema_history order by installed_rank;"'
docker compose exec -T postgres sh -lc 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -P pager=off -c "select id,project_id,task_type,subject_type,state,retryable,attempt_count,last_error_code,created_at from task_runs order by created_at desc limit 50;"'
docker compose exec -T postgres sh -lc 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -P pager=off -c "select job_id,sequence,event_type,status,occurred_at from job_events order by occurred_at desc limit 100;"'
docker compose exec -T postgres sh -lc 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -P pager=off -c "select project_id,kind,source_market_seed_snapshot_id,source_market_version_id,source_bm_plan_revision,state from market_research_runs order by created_at desc limit 20;"'
docker compose exec -T postgres sh -lc 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -P pager=off -c "select project_id,source_tech_ops_snapshot_id,source_market_research_version_id,source_business_model_version_id,deleted_at from financial_input_snapshots order by created_at desc limit 20;"'
```

## 7. MinIO/Artifact 확인

- Marketing Visual preview/open/download가 Backend ownership API를 통과하는지 확인한다.
- 다른 사용자/프로젝트로 동일 Artifact id를 조회하면 거부되어야 한다.
- MinIO Console에서 bucket/object가 존재하고 DB Artifact metadata의 project/task/content lineage와 대응하는지 확인한다.
- MinIO를 일시적으로 사용할 수 없게 한 Visual 작업은 COMPLETED가 아니라 FAILED여야 한다.

## 8. 실패 시 수집

비밀값, Authorization header, raw provider body는 공유 전에 제거한다.

```powershell
New-Item -ItemType Directory -Force .\verification-output | Out-Null
docker compose ps | Out-File .\verification-output\compose-ps.txt
docker compose logs --no-color --since=30m postgres minio minio-init ai-server backend frontend | Out-File .\verification-output\services.log
docker compose config | Out-File .\verification-output\compose-config.redact-before-share.txt
docker compose exec -T postgres sh -lc 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -P pager=off -c "select id,task_type,state,retryable,last_error_code,updated_at from task_runs order by updated_at desc limit 50;"' | Out-File .\verification-output\task-runs.txt
docker compose exec -T postgres sh -lc 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -P pager=off -c "select job_id,sequence,event_type,status,technical_code,occurred_at from job_events order by occurred_at desc limit 100;"' | Out-File .\verification-output\job-events.txt
```

Codex Session 5에서는 Provider LIVE, MOLEG LIVE, KOSIS/DART full research, Twin 대규모 LIVE, Image Provider LIVE, 전체 Browser E2E 및 실제 사용자 Docker 전체 검증을 실행하지 않았다.
