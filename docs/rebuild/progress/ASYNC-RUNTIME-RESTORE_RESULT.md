# ASYNC-RUNTIME-RESTORE Result

## Outcome

R7 legacy removal 이후 비활성화된 Spring Scheduling을 신규 Pipeline 전용 설정으로 복구했다. Idea Brief, Concept Factory, Marketing Content Worker와 Job Event heartbeat는 공통 8-thread scheduler에서 실행되며, 기존 `QUEUED` TaskRun도 별도 startup 우회 없이 기존 Worker `claimNext` 경로로 처리된다.

SSE는 등록 직후 연결 comment를 전송하고, heartbeat/terminal/timeout/error cleanup을 유지한다. 프런트 transport는 45초 inactivity watchdog 뒤 제한된 SSE 재연결을 거쳐 polling으로 전환하며, polling은 2초부터 최대 30초까지 backoff하고 hidden page에서는 더 느리게 동작한다.

## Files changed

- `backend/src/main/java/com/aivle/backend/config/AsyncExecutionConfiguration.java`
- `backend/src/main/java/com/aivle/backend/jobevent/JobEventStreamService.java`
- `backend/src/main/java/com/aivle/backend/jobevent/JobEventSseController.java`
- `backend/src/test/java/com/aivle/backend/config/AsyncExecutionConfigurationTests.java`
- `backend/src/test/java/com/aivle/backend/jobevent/JobEventApiIntegrationTests.java`
- `backend/src/test/java/com/aivle/backend/jobevent/JobEventControllerTests.java`
- `backend/src/test/java/com/aivle/backend/jobevent/JobEventStreamServiceTests.java`
- `backend/src/test/java/com/aivle/backend/pipeline/concept/worker/ConceptFactoryWorkerTests.java`
- `frontEnd/nginx.conf`
- `frontEnd/src/shared/async-events/authenticatedSseClient.js`
- `frontEnd/src/shared/async-events/useJobEvents.js`
- `frontEnd/src/shared/async-events/useJobEvents.test.jsx`
- `docs/rebuild/progress/ASYNC-RUNTIME-RESTORE_RESULT.md`
- `docs/rebuild/verification/ASYNC-RUNTIME-RESTORE_USER_VERIFICATION.md`

## Contracts implemented

- `@EnableScheduling`이 적용된 신규 Pipeline scheduling configuration과 Spring이 자동 인식하는 `taskScheduler` Bean.
- pool size 8, `pipeline-scheduler-` prefix, cancel removal 및 최대 60초 graceful shutdown을 갖는 `ThreadPoolTaskScheduler`.
- 기존 `TimeConfiguration`의 `Clock` Bean 유지 및 legacy job package 미복원.
- Scheduling context에서 post processor, scheduler, 세 Worker, Job Event stream service 및 다중 thread pool 존재 검증.
- SSE 최초 `connected` comment, heartbeat, completion/timeout/error registry cleanup, terminal event 뒤 정상 complete, 종료 emitter 오류 비노출.
- SSE 응답의 `text/event-stream`, `Cache-Control: no-cache`, `X-Accel-Buffering: no`와 기존 인증/소유권 검증.
- Polling/SSE route 분리, owner replay, sequence 1 시작, `after` cursor, non-owner 거부 검증.
- Nginx 전용 `/api/v2/jobs/` location의 buffering/cache 비활성화, 1시간 read timeout 및 Authorization 전달 보존.
- Last-Event-ID 및 sequence dedupe 유지, 401/403 즉시 종료, inactivity watchdog, bounded SSE retry, capped exponential polling backoff, hidden-page 감속, event 수신 시 backoff reset.
- Concept Factory의 claim 이후 RuntimeException이 TaskRun `FAILED` 및 terminal Job Event로 끝나는 Worker 경계 검증.

## Checks actually run

- `backend\\gradlew.bat compileJava` — 통과.
- Scheduling Context, Idea Brief Worker, Concept Factory Worker, Job Event Poll/SSE targeted test 묶음 — 17개 중 16개 통과; 신규 initial comment에 따른 기존 Mockito 호출 횟수 기대값 1건을 수정.
- `backend\\gradlew.bat test --tests "com.aivle.backend.jobevent.JobEventStreamServiceTests"` — 수정 후 통과.
- `npm.cmd exec vitest run src/shared/async-events/useJobEvents.test.jsx` — 11개 통과.
- PowerShell Nginx SSE location/directive/order/Authorization 정적 검사 — 통과.
- `git diff --check` — 통과.

## Checks intentionally omitted

- 전체 backend test 및 전체 `postgresTest`.
- 전체 frontend baseline, production build 및 lint.
- Docker build/up, 실제 PostgreSQL 기존 row claim 확인.
- 실제 AI provider smoke 및 AI Server 호출 확인.
- browser E2E와 90초 SSE 수동 유지/차단 시험.

위 항목은 현재 실행 단위의 fast execution 규칙에 따라 사용자 검증 문서로 이관했다.

## Remaining risks

- 실제 Docker/PostgreSQL 환경에서 기존 TaskRun `2617c8d9-6148-4347-bbb3-5db458c6fe25`가 claim되는지는 아직 실행 검증하지 않았다.
- Provider latency 중 scheduler pool 8이 heartbeat와 다른 Worker를 격리하도록 구성했지만 실제 동시 provider 부하 검증은 하지 않았다.
- Nginx binary가 로컬에 없어 `nginx -t` 대신 요구 directive와 location 우선순위를 정적으로 검사했다.
- SSE 차단 시 브라우저/프록시 조합별 네트워크 동작은 수동 확인이 필요하다.

## Exact continuation point

`docs/rebuild/verification/ASYNC-RUNTIME-RESTORE_USER_VERIFICATION.md`의 명령을 순서대로 실행해 기존 TaskRun의 `attempt_count` 증가, AI Server execution POST, Job Event 증가, Idea Brief terminal 전이, 90초 SSE 유지 및 차단 시 polling fallback을 확인한다. 이 검증 전에는 다음 실행 단위로 진행하지 않는다.
