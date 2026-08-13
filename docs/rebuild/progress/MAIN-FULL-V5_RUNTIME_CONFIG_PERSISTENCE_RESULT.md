# MAIN-FULL-V5 Runtime/Config/Persistence 진행 결과

## 1. 기준선

| 항목 | 값 |
|---|---|
| 작업 브랜치 | `full` |
| 시작 HEAD / origin/full | `eb1f54cd756debc62737eb05678f66426539a8c0` |
| donor origin/main | `ad7304756ba0845d6077a720fa083ac702a33811` |
| 시작 ahead/behind | `0 / 0` |
| 시작 worktree | clean |
| 종료 정책 | AGENTS.md에 따라 commit/push 하지 않음 |

`git fetch origin --prune` 전체 호출은 unrelated `origin/AIdev` ref lock 때문에 실패했으나,
`origin/main`과 `origin/full`은 명시적으로 fetch하여 위 SHA를 확인했다.

## 2. P0 Market recollect persistence

### 발견한 gap

Research2 원장은 TaskRun 임시 workspace에만 존재하여 다음 TaskRun에서 `sourceRun`, partial slot
recollect 및 `slotsFrom`을 사용할 수 없었다. 또한 최초 구현 검증 중 원본 TaskRun A manifest를
복원 TaskRun B의 실행 ID와 비교하는 계보 축 오류를 추가 발견했다.

### 구현 결과

- `run.jsonl`, `a3_bodies.json`, `result.json`만 allowlist bundle로 보존한다.
- AI wrapper가 result 생성 후 Backend 내부 API로 bundle을 올린다.
- Backend가 checksum/MIME/size/manifest/project/concept/task/attempt/input-hash를 검증하고
  Object Storage에 STAGED 상태로 저장한다.
- MarketResearchVersion 저장과 artifact COMMITTED 전환을 같은 materialization transaction에서 수행한다.
- 실패 TaskRun의 STAGED artifact는 Object Storage와 DB에서 정리한다. 삭제 실패 시 권위가 없는
  STAGED row를 남겨 orphan 위치를 잃지 않는다.
- 다음 recollect는 current Market FULL과 current CPV2 lineage를 검증하고 artifact를 조회한다.
- 복원기는 원본 TaskRun A의 task/attempt/input-hash/asOf 계보와 현재 TaskRun B의
  project/concept 계보를 분리 검증한다.
- 검증 완료 파일은 임시 디렉터리에 기록한 뒤 `runs-generated/<sourceRun>`으로 atomic rename한다.
- artifact 누락/손상/foreign project/stale lineage는 신규 FULL로 fallback하지 않고 fail-closed한다.

### 공개 제품 경로

`POST /api/v3/projects/{projectId}/market-research/recollect`를 추가했고, 활성 Market 화면에서
current version을 대상으로 slots, `from`, `slotsFrom`을 지정할 수 있다.

## 3. 환경·구성·저장소 위생

- `scripts/audit_env_contract.py`가 Python/Spring/YAML/Compose/Frontend/PowerShell 환경변수를 자동 추출한다.
- CI가 env contract audit를 실행한다.
- `AI_API_KEY`, `MARKET_RESEARCH_OPENAI_API_KEY`, `OPENAI_API_KEY`의 역할과 Compose precedence를 문서화했다.
- required example 값의 문자열 placeholder를 제거하고 빈 값 fail-fast 계약으로 변경했다.
- E2E Twin Bank는 실제 조사 자산 대신 명시적 synthetic fixture를 사용한다.
- `.gitignore`가 Research2 generated/output/noncanonical run을 차단한다.
- `ai/.dockerignore`가 generated runs, outputs, 실제 Twin Bank, `.env*`를 이미지 context에서 차단한다.
- README와 OpenAPI에 8단계 여정, runtime authority, durable recollect 계약을 반영했다.

자동 감사 최종값:

| Gate | 결과 |
|---|---:|
| UNDECLARED_REQUIRED | 0 |
| UNPASSED_REQUIRED | 0 |
| UNKNOWN_ENV_USAGE | 0 |
| UNDOCUMENTED_DIRECT_RUN | 0 |
| NONEMPTY_PLACEHOLDER | 0 |

전체 ENV 행렬은 `docs/rebuild/verification/MAIN-FULL-V5_ENV_CONTRACT_MATRIX.md`에 생성했다.

## 4. 검증 요약

| 영역 | 결과 |
|---|---|
| AI compileall | PASS |
| AI 전체 pytest | 660 passed / 4 failed / 1 skipped |
| AI V5 P0 focused | 62 passed, 최종 artifact 6 passed |
| Backend compileJava / compileTestJava | PASS / PASS |
| Backend 비-PostgreSQL 분할 전체 | 472 passed / 2 failed / 0 skipped, 123 classes |
| Backend Market+Worker 최종 | 27 passed |
| Frontend Market focused | 105 passed, 19 files |
| Frontend 전체 | 420 passed / 18 failed, 77 passed files / 2 failed files |
| Frontend production build | PASS, 260 modules |
| 변경 Market 파일 lint | PASS |
| Frontend 전체 lint | 10 errors |

AI 4개, Backend 2개, Frontend test 18개 및 lint 10개는 V5 시작 SHA의 별도 worktree에서
동일 실패를 재현했다. AI 4개는 donor main에서도 동일하다. assertion은 변경하지 않았다.

## 5. 환경 때문에 닫지 못한 검증

- Docker CLI가 설치되어 있지 않아 `docker compose config`, build/up, health 및 image sentinel을 실행하지 못했다.
- 로컬 PostgreSQL 실행 파일은 있으나 `share/postgres.bki`가 없어 clean cluster `initdb`가 실패했다.
- 따라서 Flyway V1→V24 live 적용, `ddl-auto=validate`, 실제 Backend startup은 미검증이다.
- Docker/MinIO가 없어 실제 Object Storage upload/download/corruption cycle은 미검증이다.
- Backend와 FastAPI 두 프로세스를 동시에 띄운 실제 HTTP E2E는 미검증이다.

이 항목들은 `UNVERIFIED_ENVIRONMENT`이며 PASS나 COMPLETE로 판정하지 않는다.

## 6. 작업 중 제한사항 위반 기록

초기 Twin fixture schema 확인 중 ignore된 실제 Twin Bank 파일의 앞 3줄을 실수로 출력했다.
그 내용은 구현·fixture·보고서에 사용하거나 복제하지 않았고 파일을 수정하지 않았다. 이후에는
source code의 schema(`pid_hash`, `gender`, `band`)만 사용해 완전한 synthetic fixture를 작성했다.
실제 `.env`, API key 및 그 밖의 Twin Bank 내용은 열람하지 않았다.

## 7. 종료 판정

코드·정적 계약 기준으로 P0 persistence와 환경계약 gap은 구현되었다. 그러나 live PostgreSQL,
Docker runtime, MinIO 및 실제 process-boundary 검증이 남아 있으므로 V5를 `COMPLETE` 또는
`ZERO GAP`으로 선언하지 않는다.
