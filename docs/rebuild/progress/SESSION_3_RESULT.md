# Session 3 Finance 이식 및 TechOps 재검증 결과

- 기준 Target: `integration/full-transplant-v1` / 시작 commit `2b78dc7`
- Finance donor: `donor-mini` / `c7f7946`
- 판정: **SESSION 3 TRANSPLANT READY**
- LIVE Provider, Tavily LIVE, MOLEG LIVE, 실 Market/Twin, Browser E2E, 사용자 Docker 전체 검증은 수행하지 않았다.

## 1. donor/Target 비교와 이식 원칙

| 분류 | 내용 |
|---|---|
| KEEP_TARGET | CPV2, Project Shell, ownership, Finance preparation/snapshot authority, `FINANCE_ESTIMATE`, TaskRun/Attempt/Result, JobEvent/SSE, Work Center, current/stale/retry, MinIO/Artifact |
| PORT_DONOR | `RevenueModel`, `FinancialCalculationService`, `FinancialInputScaler`, `FinancialMonteCarloService`, 3개년 P&L·현금흐름·stress·Monte Carlo·보고서 UI |
| MERGE_SEAM | Market/BM/TechOps exact source binding, Finance snapshot mapper, final AI report TaskRun, module status, `/api/v3` route |
| NOT_OFFICIAL | donor `/api/finance/analysis`, `/api/v1/modules/financial/preview`, demo/sample fallback |

donor 계산식과 결과 항목은 제거하거나 재설계하지 않았다. 공식 Finance는 current Market Research, 그 Market version을 source로 갖는 current BM, current TechOps Snapshot이 동시에 준비된 경우에만 초기화된다.

## 2. TechOps 재검증

- Session 1의 34개 관련 source 동일 판정을 재확인했다.
- `donor-mini/backend/.../pipeline/techops` 제품 source 14개 SHA-256 비교 결과: `DIFFERENT=0`, `DONOR_ONLY=0`.
- 시작 commit 대비 TechOps product diff: 0. TechOps 전체 덮어쓰기는 하지 않았다.

## 3. 이식 기능

- 일회성·구독·혼합 수익모델, 12/24/36개월 계산, 고정비·변동비·영업이익, contribution margin, BEP 수량/매출/월, payback, 운전자금, sensitivity를 보존했다.
- KRW/천원/백만원 경계 스케일링은 입력 경계에서 한 번만 수행하고 DB·계산 정본은 KRW를 유지한다.
- Monte Carlo는 simulation count, volume/price/cost volatility, seed를 결과에 결속하며 동일 입력/seed 재현성을 유지한다.
- 3개년 P&L, 월별 매출·영업이익·누적현금흐름, stress scenario, P10/P50/P90, 손실/회수 확률, seed를 공식 UI에 보존했다.

## 4. Preparation과 immutable upstream

`FinancialInputPreparation`과 `FinancialInputSnapshot`은 다음 ID를 함께 저장한다.

- `sourceTechOpsSnapshotId`
- `sourceMarketSeedSnapshotId` (기존 Target 연결 유지)
- `sourceMarketResearchVersionId`
- `sourceBusinessModelVersionId`

Market/BM/TechOps lineage를 한 source hash에 결속한다. BM version의 source Market version이 current Market version과 다르거나 어느 upstream이 stale이면 공식 preparation을 생성·조회하지 않는다. sample/demo를 공식 upstream으로 대체하지 않는다.

## 5. provenance

- TechOps 확정값: read-only, source Snapshot ID와 `requiredFacts` 경로 유지
- Market 가격: `MARKET_ANALYSIS_ASSUMPTION`, `market.price.base`, 사용자 확인 필요
- BM 수익모델: `BUSINESS_MODEL_ASSUMPTION`, `bm.financialHandoff.revenueModel`
- 사용자 저장: `USER_INPUT`/`LOCKED`
- AI 채택: `AI_ESTIMATE`/`ACCEPTED`, proposal version 기록
- AI 수정 채택/거절: `USER_EDITED_ACCEPTED` 또는 `REJECTED`; 제안 원본은 감사 근거로 유지

Market evidence/scorecard와 BM 전체 결과/financial handoff/caveat는 upstream reference에 보존하고 UI의 전체 상세에서 확인할 수 있다.

## 6. AI Estimate와 Tavily

- 기존 `FINANCE_ESTIMATE`를 중복 생성하지 않고 input/context contract를 Market/BM/TechOps, 가격, 이탈률, 신규 고객 수까지 확장했다.
- TaskRun → Worker → Internal AI Execution → canonical materialization → JobEvent/SSE 흐름을 유지했다.
- `TAVILY_API_KEY`가 없거나 HTTP/JSON 오류가 발생하면 빈 external context로 fail-open한다.
- Tavily 결과는 verified fact가 아니라 선택적 external context로만 prompt에 전달한다.

## 7. deterministic 분석과 AI report

확정 snapshot에서 deterministic Java 계산을 먼저 즉시 수행한다. 숫자 결과는 AI가 변경하지 않는다. 최종 서술만 별도 `FINANCE_ANALYSIS_REPORT` TaskRun으로 실행한다.

- 성공: canonical 계산 결과의 `report`만 `AI_GENERATED_REPORT/SUCCEEDED`로 교체
- Provider/contract 실패: 계산 결과를 성공적으로 보존하고 `SYSTEM_CALCULATION_FALLBACK/FAILED/safeFailureReason`으로 명시
- raw provider output은 API/UI에 노출하지 않는다.
- Work Center task label, Finance module status, JobEvent stage와 Project/Job SSE를 연결했다. Frontend polling은 없다.

## 8. current/history/stale/reopen

- reopen은 active snapshot을 soft-delete하고 기존 row를 history로 보존한다.
- V19 partial unique index는 preparation별 active snapshot 하나만 허용하고 replacement snapshot 생성을 허용한다.
- 상위 Market/BM/TechOps 변경 시 preparation/snapshot/analysis를 stale로 표시하며 기존 결과를 in-place overwrite하지 않는다.

## 9. Migration

| Migration | 판정 |
|---|---|
| V17 | 생성하지 않음. `TaskRun.lastErrorCode`, `TaskAttempt` failure, `JobEvent.technicalCode`가 safe failure authority를 제공하고 donor가 `last_error_reason` column에 의존하지 않음 |
| V18 | Finance preparation/snapshot에 exact Market/BM version FK와 active composite-source unique index 추가 |
| V19 | snapshot preparation unique를 `deleted_at IS NULL` partial unique index로 전환 |

V17 번호는 재사용하지 않았고 V20 이상은 생성하지 않았다. V1~V16 내용 diff는 0이다.

## 10. API와 dev surface

공식 API는 `/api/v3/projects/{projectId}/finance/...`만 사용한다. preparation 조회/저장, AI assistance, snapshot finalize/current/reopen, analysis run/current를 제공한다. donor sandbox preview와 demo 가짜 데이터는 공식 route에 노출하지 않았고 donor 자체는 변경하지 않았다.

## 11. 검증 결과

- Backend `compileJava`: PASS
- Backend Finance targeted tests: 28 PASS (계산, scaling, Monte Carlo, preparation/provenance, estimate, report/fallback, reopen/history 계약)
- AI Finance targeted: 4 PASS; `compileall`: PASS
- Frontend Finance targeted: 14 PASS
- Frontend production build: PASS, 요청대로 1회 수행 (261 modules)
- PostgreSQL 17.10 Testcontainers: PASS; 존재 migration 18개 clean apply/validate, current V19, `ddl-auto=validate` PASS
- Session 2 대표 회귀: Backend Market/BM/Twin 5개 계약군 PASS, AI 38 PASS, Frontend 93 PASS
- 전체 Backend `test`는 5분 제한까지 완료되지 않아 완료 Gate로 사용하지 않았다. Session 요구는 전체 재실행이 아니라 대표 regression gate이며, 이후 daemon 정리 후 targeted/대표 test와 PostgreSQL은 모두 PASS했다.
- `git diff --check`: 최종 Gate에서 확인

## 12. 금지 영역 diff 감사

- CPV2 Core: 0
- Persona: 0
- Marketing/AIdev: 0
- TechOps product: 0
- Market/BM/Twin algorithm: 0
- V1~V16 migration: 0
- donor worktree: 0 변경

공식 Finance `PRESERVE_REQUIRED=YES` 항목의 `NOT_PORTED`는 없다. sandbox/demo는 공식 parity와 분리해 `02_DONOR_UI_INFORMATION_INVENTORY.md`에 기록했다.

## 13. 사용자 수행 검증

Provider LIVE, Tavily LIVE, 실제 Docker 전체 stack, 브라우저 E2E는 `docs/integration/SESSION_3_USER_VERIFICATION.md` 절차로 사용자가 확인한다.
