# G2 결과 — Durable Job Events, SSE, Polling Fallback

- 작업일: 2026-08-05
- 기준 SHA: `967c19a8eca17ff24f159175fb3e7ecc9fb6cf9d`
- 작업 브랜치: `feature/conversational-validation-workspace`
- 승인 결정: `ADR-CVW-0002` fetch `ReadableStream` 인증 SSE
- commit/push: 수행하지 않음

## 1. 변경 파일

### Backend

- `backend/src/main/java/com/aivle/backend/jobevent/JobEvent.java`
- `backend/src/main/java/com/aivle/backend/jobevent/JobEventRepository.java`
- `backend/src/main/java/com/aivle/backend/jobevent/JobEventPublisher.java`
- `backend/src/main/java/com/aivle/backend/jobevent/JobEventPayloadPolicy.java`
- `backend/src/main/java/com/aivle/backend/jobevent/JobEventQueryService.java`
- `backend/src/main/java/com/aivle/backend/jobevent/JobEventStreamService.java`
- `backend/src/main/java/com/aivle/backend/jobevent/JobEventController.java`
- `backend/src/main/java/com/aivle/backend/jobevent/JobEventSseController.java`
- `backend/src/main/java/com/aivle/backend/jobevent/JobEventView.java`
- `backend/src/main/java/com/aivle/backend/config/WebConfig.java`

### Frontend

- `frontEnd/src/shared/api/apiClient.js`
- `frontEnd/src/shared/async-events/**`

### 테스트

- `backend/src/test/java/com/aivle/backend/jobevent/**`
- `backend/src/test/java/com/aivle/backend/CorsConfigurationTests.java`
- `backend/src/test/java/com/aivle/backend/postgres/PostgreSqlJobEventConcurrencyTests.java`
- `frontEnd/src/shared/api/apiClient.test.js`
- `frontEnd/src/shared/async-events/*.test.js(x)`

### 문서

- `docs/redesign/decisions/DECISION_LOG.md`
- `docs/redesign/current-to-target/CONVERSATIONAL_VALIDATION_WORKSPACE_CURRENT_TO_TARGET_MAP.md`
- `docs/redesign/progress/G2_RESULT.md`

## 2. Backend 계약

### 발행과 순서

- `JobEventPublisher`는 `projectId`, `jobId`, 선택적 `taskRunId`, 실제 `stage`,
  `eventType`, `status`, `messageKey`, safe params, 선택적 `technicalCode`를 받는다.
- project row pessimistic lock 안에서 해당 job의 다음 sequence를 할당한다.
- 동일 job을 다른 project에 연결하거나 다른 project의 TaskRun을 연결할 수 없다.
- live publish는 영속 transaction commit 후에만 수행한다.

### 안전한 Event payload

- 사용자 문구는 `messageKey`와 최대 크기/깊이가 제한된 구조화 params로 저장한다.
- Authorization, API key/token, prompt, raw/provider body, 전체 사용자 입력을
  나타내는 key는 publisher 단계에서 거부한다.
- 운영 오류는 별도 `technicalCode` 필드에 저장하며 `JobTimeline` 사용자 문구로
  렌더링하지 않는다.
- 실제 근거 없는 percent 필드는 생성하거나 표시하지 않는다.

### Replay와 transport

- SSE: `GET /api/v2/jobs/{jobId}/events`
  - `Accept: text/event-stream`
  - `Authorization: Bearer ...`
  - `Last-Event-ID: {lastSequence}`
  - SSE `id`는 cursor로 사용하는 event sequence다.
- Polling fallback: `GET /api/v2/jobs/{jobId}/events?after={sequence}`
- 다른 소유자의 job은 `PROJECT_ACCESS_DENIED`, 인증이 없으면 401이다.
- initial replay 등록과 commit 후 live publish를 job 단위로 직렬화한다.
- 15초 기본 heartbeat를 보내며 completion/timeout/error/send failure/shutdown 때
  emitter를 정리한다.
- `COMPLETED`, `FAILED`, `NEEDS_INPUT` terminal event는 전송한 뒤 stream을 완료한다.
- CORS 보호 헤더에 `Last-Event-ID`를 추가했다.

Polling 응답은 cursor 이후 최대 100건만 반환하며 page의 마지막 cursor와 DB의
최신 cursor를 분리한다.

```json
{
  "success": true,
  "data": {
    "events": [{ "sequence": 101, "status": "RUNNING" }],
    "nextSequence": 101,
    "latestSequence": 105,
    "hasMore": true
  },
  "meta": {
    "requestId": "req-...",
    "timestamp": "2026-08-05T01:00:00Z"
  }
}
```

## 3. Frontend 계약

- 공통 API client의 `stream()`은 access token을 Authorization 헤더에만 넣고
  URL에는 넣지 않는다. 401이면 기존 단일 refresh 절차를 재사용한다.
- SSE parser는 chunk 경계, CRLF/LF, multi-line data, comment heartbeat,
  `id`와 `event` field를 처리한다.
- reducer는 sequence 기준으로 정렬하고 replay/live 중복을 제거한다.
- `useJobEvents(jobId)`는 현재 cursor로 SSE를 재연결한다. 기본 최대 3회 연결
  실패까지 1초, 2초 순으로 exponential backoff하며 지연 상한은 8초다.
- 401/403, Abort, terminal event에서는 재연결하지 않는다. 연속 실패 한도 이후에만
  동일 cursor의 polling으로 전환하며 SSE로 자동 복귀하지 않는다.
- polling도 terminal event에서 멈춘다. hook은 `events`, `lastSequence`,
  `connectionState`, `transport`, `error`, `terminal`, `reconnect`, `stop`을 반환한다.
- jobId 변경, 수동 stop, unmount 시 fetch/read/timer를 AbortController로 정리한다.
- `JobTimeline`은 safe message mapper 결과와 실제 상태만 순서대로 표시하며
  `aria-live="polite"`를 제공한다.
- 기존 Idea/Legal/Concept 화면은 교체하거나 연결하지 않았다.

## 4. Event 예제

```json
{
  "eventId": "125",
  "jobId": "idea-turn-01J...",
  "projectId": 42,
  "taskRunId": "01J...",
  "stage": "IDEA_INTAKE",
  "eventType": "FILE_EXTRACTION_STARTED",
  "status": "RUNNING",
  "messageKey": "job.idea.file.extraction.started",
  "messageParams": { "fileCount": 1, "format": "DOCX" },
  "technicalCode": null,
  "sequence": 3,
  "occurredAt": "2026-08-04T23:10:00Z"
}
```

SSE frame은 다음과 같다.

```text
id: 3
event: job-event
data: {event payload JSON}
```

## 5. Migration

추가 Migration은 없다. G1의
`V2__conversational_validation_domain.sql`과 `job_events` 테이블을 그대로
사용한다. 기존 테이블의 삭제, rename, backfill도 없다.

## 6. 테스트 결과

- Backend targeted JobEvent/SSE/CORS: 22건 통과, 실패 0, 오류 0.
- Backend 전체 회귀: 288건 통과, 실패 0, 오류 0, skipped 0.
- PostgreSQL migration/concurrency: 19건 통과, 실패 0, 오류 0.
  실제 PostgreSQL에서 12개 동시 publisher의 sequence가 중복·누락 없이 `1..12`임을 확인했다.
- Frontend async-events/API targeted: 27건 통과, 실패 0.
- Frontend lint: 통과.
- Frontend production build: 통과. Vite의 기존 500 kB 초과 chunk 경고는 유지된다.
- Frontend 공식 `test:baseline`: 통과. 264건 통과, 명시적 기존 allowlist 실패 18건,
  unexpected failure 0건.
- `git diff --check`: 통과.

## 7. G3 연결 지점

1. G3 Conversation/Attachment worker가 각 실제 단계 transaction에서
   `JobEventPublisher.publish(...)`를 호출한다.
2. 장기 작업 시작 응답은 `jobId`를 반환한다.
3. 신규 G3 화면은 `useJobEvents(jobId)` 결과를 `JobTimeline`에 전달한다.
4. 새로고침 시 cursor 0에서 durable replay하고, 활성 session에서는 마지막
   sequence 이후만 reconnect한다.
5. G3 message key를 mapper에 추가하되 prompt, raw provider body, 전체 사용자
   메시지를 params로 전달하지 않는다.

## 8. 미해결 위험

- emitter registry는 process-local이다. backend 재시작 시 live connection은
  끊기지만 DB event와 Last-Event-ID로 복원된다.
- SSE 최초 replay는 cursor 이후 durable event 전체를 한 연결에서 재생한다. 매우 긴 job의
  replay batch 분할은 G2 외 후속 운영 최적화 대상이다. Polling에는 100건 상한이 적용된다.
- reverse proxy의 SSE buffering/idle timeout은 실제 Docker smoke에서 검증해야 한다.
- G2는 공통 인프라만 제공하며 실제 G3 worker event 발행은 아직 연결하지 않았다.
