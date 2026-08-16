# MARKETING-RUNTIME-HARDEN RESULT

## 실행 기준

- Branch: `rebuild/new-pipeline-v1`
- Start HEAD: `a98a19c2dc79be4177300ec1c7086d6a41536dca`
- 시작 worktree: clean
- FinalizedPlanningSnapshot 외 BM·재무, Persona, legacy Marketing/Feasibility/Legal Review 의존성은 추가하지 않았다.

## 구현한 계약

- AI `MarketingSourceSnapshot`의 `Any`를 제거하고 모든 문자열·배열을 bounded typed field로 변경했다.
- Pydantic provider schema에 대해 closed object, typed property, bounded string/array, unconstrained object 부재를 호출 전에 검사한다.
- FinalizedPlanningSnapshot JSON을 임의 전달하지 않고 Backend `MarketingSourceSnapshot` DTO mapper로 허용 필드만 정규화한다.
- Marketing source hash를 정규화 DTO에서 계산하고 현재 FinalizedPlanningSnapshot과의 hash 불일치를 STALE로 판정한다.
- 생성/조회 응답에 `contentId`, `status`, `activeJobId`, `sourceSnapshotId`, `updatedAt`을 노출한다. 호환용 `taskRunId`와 `planningSnapshotId`는 유지했다.
- Frontend 전용 `setInterval` polling을 제거하고 선택된 `activeJobId`에 공통 `useJobEvents`를 연결한다.
- 페이지 새로고침 시 목록 조회 → RUNNING/QUEUED content Detail 조회 → activeJobId 복원 → replay 연결 → Terminal 후 Detail 재조회 흐름을 구현했다.
- 정적 진행 배열을 제거하고 `job.marketing.*` 실제 Event만 Timeline에 표시한다.
- schema invalid, timeout, rate limit, configuration, prohibited claim, stale source를 안전한 사용자 문구/code로 구분한다.
- Provider 결과 또는 사용자 편집 결과에 prohibited claim이 포함되면 사용/최종화를 차단한다.
- Worker failure는 TaskRun과 MarketingContent를 같은 failure transaction 경계로 종료하며 terminal Job Event를 보낸다.

## Asset 범위

현재 구현 결과는 다음으로 제한된다.

- Copy: title, body, CTA, hashtags
- 브라우저 HTML/CSS Preview
- Image Brief 텍스트
- 텍스트 복사 및 `.txt` 다운로드

실제 PNG/JPEG/Banner binary artifact는 생성하지 않는다. 현재 계약에서는 `artifactRefs`를 항상 빈 배열로 강제한다. Image Generation Adapter, Object Storage 저장, binary Artifact Reference와 이미지 다운로드는 제품 문서의 필수 계약이 아니므로 이번 Unit에 추가하지 않았다.

## 변경 파일

### AI

- `ai/app/tasks/marketing_content/models.py`
- `ai/app/tasks/marketing_content/service.py`
- `ai/app/tasks/marketing_content/prompts/generation.py`
- `ai/app/tools/marketing_content_provider_smoke.py`
- `ai/tests/test_marketing_content_contract.py`

### Backend

- `backend/src/main/java/com/aivle/backend/common/exception/ErrorCode.java`
- `backend/src/main/java/com/aivle/backend/pipeline/marketing/api/MarketingApiModels.java`
- `backend/src/main/java/com/aivle/backend/pipeline/marketing/application/MarketingContentService.java`
- `backend/src/main/java/com/aivle/backend/pipeline/marketing/application/MarketingResultContract.java`
- `backend/src/main/java/com/aivle/backend/pipeline/marketing/application/MarketingSourceSnapshot.java`
- `backend/src/main/java/com/aivle/backend/pipeline/marketing/application/MarketingSourceSnapshotFactory.java`
- `backend/src/main/java/com/aivle/backend/pipeline/marketing/worker/MarketingContentWorker.java`
- `backend/src/test/java/com/aivle/backend/pipeline/marketing/MarketingContentContractsTests.java`
- `backend/src/test/java/com/aivle/backend/pipeline/marketing/worker/MarketingContentWorkerTests.java`

### Frontend 및 계약

- `frontEnd/src/features/marketing-content/hooks/useMarketingGeneration.js`
- `frontEnd/src/features/marketing-content/hooks/useMarketingContent.js`
- `frontEnd/src/features/marketing-content/hooks/useMarketingGeneration.test.jsx`
- `frontEnd/src/features/marketing-content/hooks/useMarketingContent.test.jsx`
- `frontEnd/src/features/marketing-content/model/marketingContentModel.js`
- `frontEnd/src/features/marketing-content/pages/MarketingContentPage.jsx`
- `frontEnd/src/shared/async-events/jobEventMessages.js`
- `docs/rebuild/contracts/marketing-content-result-v1.schema.json`

## 실제 실행한 검사

- Python `compileall` for Marketing task/smoke — 성공
- `pytest tests/test_marketing_content_contract.py -q` — 5 passed
- Backend `MarketingContentContractsTests`, `MarketingContentWorkerTests` — 성공
- Backend targeted test 실행 중 `compileJava` — 성공
- Frontend Marketing hook/editor/model targeted Vitest — 5 passed
- 변경 Frontend 파일 targeted ESLint — 성공
- `git diff --check` — 성공

시스템 Python에는 pytest가 없어 최초 실행은 실패했고 repository의 `ai/.venv` Python으로 동일 targeted test를 실행해 성공했다. Gradle 최초 sandbox 실행은 wrapper 다운로드 네트워크 제한으로 실패했다. 구현 중 중복 enum 항목과 `MissingNode` API compile 오류를 각각 수정했으며, 최종 승인 환경의 targeted compile/test는 성공했다.

## 의도적으로 생략한 검사

- 실제 Provider smoke (`python -m app.tools.marketing_content_provider_smoke`) — 사용자 Gate
- 전체 Backend/AI/Frontend test
- `postgresTest`, Docker build, Browser E2E
- Frontend production build

## 남은 위험

- 실제 Provider가 현재 strict schema를 지원하는지는 사용자 smoke 전까지 확인되지 않았다.
- 실제 DB/worker 환경에서 process restart 중 RUNNING/lease recovery와 SSE 장기 연결은 사용자 runtime 검증이 남아 있다.
- binary 이미지 생성은 현재 제품 계약에 없으며 제공되지 않는다.
- retryable provider failure 후 UI의 재생성은 새 durable TaskRun을 만든다. 기존 TaskRun 자체 retry UX는 공통 Job Center 범위이며 이번 Unit에서 확장하지 않았다.

## 정확한 계속 지점

사용자 검증 문서대로 Provider smoke와 Docker runtime 검증을 수행한다. strict schema rejection 또는 refresh recovery 실패가 있으면 safe error code, contentId, taskRunId/jobId, 마지막 Event sequence를 기준으로 이 Unit을 재개한다. 다음 Unit으로 자동 진행하지 않는다.
