# Conversational Intake Runtime Hotfix 구현 결과

- 시작 HEAD: `d50385e8e9efbe4ec39858f62ebcbd883de43c83`
- 브랜치: `feature/conversational-validation-workspace`
- 구현 일자: 2026-08-05
- 범위: G2 SSE, G3 Conversation, G3-H Durable Worker의 런타임 통합 결함 수정
- 제외: G7, Regulatory Boundary/Concept Core 변경, 신규 Journey, AI Prompt/Provider 변경, 브라우저 수동 검증

## 1. 원인과 Transaction 경계

`IdeaIntakeDurableWorker`가 claim transaction 종료 후 detached `TaskRun`을 `IdeaIntakeAiService`에 넘겼고, `executeClaim`이 `run.getProject().getOwner()` lazy proxy를 접근해 AI 호출 전에 `LazyInitializationException`으로 종료됐다. 예외가 scheduler claim 단위에서 수렴되지 않아 TaskRun이 `RUNNING`에 남고 terminal event도 발행되지 않았다.

수정 후 `IdeaIntakeClaimService.claimNext`가 claim과 scalar capture를 하나의 transaction에서 수행한다. immutable `TaskRunWorkerContext`/`ClaimContext`는 `taskRunId`, `projectId`, `ownerId`, `conversationId`, `sourceMessageId` 또는 attachment ID/checksum, attempt/idempotency/contract scalar만 운반한다. Worker와 AI client는 detached Entity를 받지 않는다. 성공 commit 서비스는 자체 transaction 안에서 ID로 TaskRun을 다시 조회해 project/owner를 검증한다. OSIV, EAGER 전환, `open-in-view=true` 우회는 사용하지 않았다.

## 2. Worker 성공·실패 계약

- Conversation 성공: internal AI execution → strict result 검증 → AI proposal provenance 저장 → Assistant `QUESTION_SET`/`BRIEF_REVIEW` 저장 → Brief Draft 저장 → Conversation `NEEDS_INPUT`/`READY_FOR_CONFIRMATION` → active job 해제 → TaskRun `SUCCEEDED`를 한 transaction에서 채택한다.
- 성공 domain/task commit 뒤 `job.idea.questions.completed` 또는 `job.idea.brief.draft.completed` terminal event를 발행한다. Event 발행 실패가 이미 commit된 성공을 실패로 되돌리지 않는다.
- AI proposal은 `AI_PROPOSED`, `SOURCE_EXTRACTED`, `MISSING`만 허용하며 자동 `USER_CONFIRMED`, `userConfirmed=true`, `LOCKED`, 확정 `DEFAULT_ASSUMPTION`으로 승격하지 않는다.
- retryable: 명시적 AI transient failure, transient DB access, attachment parse failure. 기존 maxAttempts와 제한 backoff를 사용한다.
- permanent: strict result/schema, project/conversation contract 불일치, TaskRun permanent failure, unknown RuntimeException. Unknown 오류는 무한 retry하지 않는다.
- 최종 실패는 TaskRun/Conversation 또는 Attachment 상태와 active job을 정리한 후 안전한 `job.failed`를 발행한다. 오류 원문은 Event에 넣지 않는다.
- claim 이전/capture 실패는 claim transaction이 롤백된다. PostgreSQL에서 다른 Project Message 참조 시 `QUEUED`, attempt 0 유지가 검증됐다.

## 3. SSE Cleanup과 Frontend 갱신

- Emitter completion/timeout/error callback, heartbeat send failure, live send failure, terminal event, backend shutdown에서 해당 subscription만 registry에서 제거한다.
- Broken pipe 경로에서 `completeWithError`를 호출하지 않으며 이미 종료된 emitter에 terminal 이후 재전송하지 않는다. 한 job/emitter 실패는 다른 job emitter에 영향을 주지 않는다.
- `ClientAbortException`과 `AsyncRequestNotUsableException`은 GlobalExceptionHandler에서 void 처리해 commit된 `text/event-stream`에 `ApiResponse` JSON을 쓰지 않는다.
- fetch SSE parser는 AbortController 취소 중 발생한 read 오류를 정상 cleanup으로 처리하고 reconnect 오류로 승격하지 않는다.
- Workspace는 `job.idea.information.extraction.completed`, `job.idea.brief.draft.completed`, `job.idea.questions.completed`, `job.completed`, `job.failed`의 durable sequence를 job별 한 번만 반영해 current Conversation을 재조회한다. 반복 2초 polling은 추가하지 않았다.
- `job.claimed`/`job.started`는 사용자 Timeline에서 숨기고 실제 Idea 단계 문구만 표시한다.

## 4. 시간 계약

DB migration 없이 API DTO에서 Conversation Message `occurredAt`을 UTC `Instant` 형식 문자열(`...Z`)로 변환했다. Job Event의 기존 UTC Instant 계약과 일치한다. Frontend Message와 JobTimeline은 같은 `formatLocalTime`을 사용해 브라우저 local time으로 표시한다. 기존 DB `TIMESTAMP` 값은 변경하지 않았다.

## 5. API·Migration·호환

- 신규/변경 HTTP endpoint는 없다. 기존 Conversation, authenticated SSE, polling API를 유지한다.
- Migration은 없다. 기존 V1~V5 저장 구조를 변경하지 않았다.
- 기존 Journey, Feature Flag OFF 경로, G4/G5 결과와 API를 변경하지 않았다.
- `InternalAiExecutionClient.execute(TaskRun, ...)`는 기존 호출 호환을 유지하고 Worker용 scalar `executeWorker(...)`를 additive하게 추가했다.

## 6. 검증 결과

실행한 검증:

- Backend targeted: `IdeaIntakeAiContractTests` 3, `ConversationFoundationIntegrationTests` 4, `JobEventStreamServiceTests` 7, `GlobalExceptionHandlerSseTests` 2 — 합계 16 passed, failure/error/skip 0.
- PostgreSQL targeted: `PostgreSqlIdeaIntakeWorkerTests` 5 passed, failure/error/skip 0. 실제 claim transaction 종료 후 성공, Assistant/Brief/provenance, NEEDS_INPUT/READY, terminal event, unknown permanent failure, bounded retry, active job cleanup, project isolation rollback을 검증했다.
- Frontend targeted: 5 files, 23 passed, failure 0. terminal refetch/dedup, Assistant/QUESTION_SET/Brief 표시, Abort cleanup, Timeline filtering/time formatting을 검증했다.
- Backend compile은 targeted/full test의 `compileJava`로 통과했다.
- Backend 전체 `test`: 320 passed, failure/error/skip 0. 공통 TaskRun projection/service와 Internal AI client adapter를 변경했으므로 단계 종료 시 한 번 실행했다.
- 전체 Backend 회귀 뒤 terminal/shutdown의 `complete()`도 안전 cleanup으로 통일했으며 전체 suite를 반복하지 않고 직접 관련 SSE test 9개와 `compileJava`를 재실행해 통과했다.
- Frontend lint: 0 error, 0 warning.
- Frontend production build: 성공. 기존 500 kB 초과 chunk 경고는 유지된다.
- Frontend baseline: 298 passed, 18 explicitly allowed failures, 0 unexpected failures. shared SSE/Timeline과 기존 Journey Page 연결 변경 때문에 한 번 실행했다.
- Baseline 뒤 terminal `eventType=job.failed` fallback을 보완했으며 전체 baseline을 반복하지 않고 직접 관련 Workspace test 6개, lint, production build를 재실행해 통과했다.
- AI test: 실행하지 않았다. Prompt, schema, provider, dispatch를 변경하지 않았다.
- 전체 `postgresTest`: 실행하지 않았다. Migration/공통 worker schema 변경이 없고 Hotfix 전용 PostgreSQL class가 실제 transaction/retry/project-isolation 계약을 검증하므로 targeted class로 제한했다.
- `git diff --check`: 통과. 기존 파일의 CRLF→LF warning 외 whitespace error는 없다.

주요 실행 명령:

```powershell
.\gradlew.bat test --tests "*IdeaIntakeAiContractTests" --tests "*ConversationFoundationIntegrationTests" --tests "*JobEventStreamServiceTests" --tests "*GlobalExceptionHandlerSseTests" --no-daemon --console=plain
$env:DOCKER_API_VERSION='1.40'; $env:JAVA_TOOL_OPTIONS='-Dapi.version=1.40'; .\gradlew.bat postgresTest --tests "*PostgreSqlIdeaIntakeWorkerTests" --no-daemon --console=plain
.\gradlew.bat test --no-daemon --console=plain
npm.cmd run test:run -- src/features/conversational-idea/ConversationalIdeaWorkspace.test.jsx src/shared/async-events/JobTimeline.test.jsx src/shared/async-events/authenticatedSseClient.test.js src/shared/async-events/useJobEvents.test.jsx src/shared/async-events/formatLocalTime.test.js
npm.cmd run lint
npm.cmd run build
npm.cmd run test:baseline
git diff --check
```

초기 실패와 처리:

- 최초 compile/testClasses에서 누락 import와 기존 Mockito 호출에 대한 overload ambiguity가 발생했다. scalar 메서드를 `executeWorker`로 분리하고 import를 바로잡은 뒤 동일 compile/testClasses가 통과했다.
- 최초 targeted test 3건은 예외 subtype 계약과 변경된 사용자 문구/fixture 불일치였다. 실패 method를 단독 재현하고 strict contract fixture/기대값만 수정한 뒤 동일 targeted 범위와 최종 suite가 통과했다.
- PostgreSQL 단독 재실행 최초 시 sandbox가 Gradle distribution network 접근을 막았다. 권한 승인 후 같은 test를 재실행해 통과했으며 제품 코드 실패가 아니었다.

## 7. 변경 파일

- Backend production: `GlobalExceptionHandler.java`, `JobEventStreamService.java`, `IdeaIntakeAiService.java`, `IdeaIntakeClaimService.java`, `IdeaIntakeDurableWorker.java`, `IdeaMessageContract.java`, `IdeaTurnCompletionService.java`, `InternalAiExecutionClient.java`, `TaskRunRepository.java`, `TaskRunService.java`, `TaskRunWorkerContext.java`.
- Backend test: `GlobalExceptionHandlerSseTests.java`, `JobEventStreamServiceTests.java`, `PostgreSqlIdeaIntakeWorkerTests.java`.
- Frontend production: `ConversationalIdeaWorkspace.jsx`, `JobTimeline.jsx`, `authenticatedSseClient.js`, `formatLocalTime.js`, `jobEventMessages.js`.
- Frontend test: `ConversationalIdeaWorkspace.test.jsx`, `JobTimeline.test.jsx`, `authenticatedSseClient.test.js`, `formatLocalTime.test.js`.
- 문서: 이 결과 문서, current-to-target map, Docker 검증 절차.
- 작업 시작 전 존재한 미추적 `.pytest-tmp/`는 사용자 변경으로 간주해 수정·삭제하지 않았다.

## 8. 사용자 검증과 남은 위험

사용자 Docker 절차: [CONVERSATIONAL_INTAKE_RUNTIME_FIX.md](../verification/CONVERSATIONAL_INTAKE_RUNTIME_FIX.md)

Codex는 실제 OpenAI 호출과 브라우저 수동 검증을 수행했다고 주장하지 않는다. 남은 위험은 실제 provider 지연/5xx에서의 운영 retry 관찰, servlet container별 disconnect 예외 형태, 기존 offset 없는 DB timestamp가 서버 기본 timezone과 다른 배포 환경에서 생성된 과거 row의 의미다. 이번 Hotfix는 현재 계약대로 기존 LocalDateTime을 UTC로 해석하므로 과거 데이터의 timezone provenance 자체를 복원하지는 않는다.

G7은 구현하지 않았다. commit과 push도 수행하지 않았다.
