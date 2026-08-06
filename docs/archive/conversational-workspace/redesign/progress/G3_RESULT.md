# G3 Result — Conversational Idea Intake and Opportunity Brief

## 1. 기준과 범위

- 기준 SHA: `967c19a8eca17ff24f159175fb3e7ecc9fb6cf9d`
- 작업 브랜치: `feature/conversational-validation-workspace`
- G1의 `V2__conversational_validation_domain.sql`과 G2 Job Event 인프라를 재사용했다.
- G3 최초 구현 뒤 G3-H에서 additive V3 Migration을 추가했다. 인증 구조, G4 Regulatory Boundary, G5 Concept Generator는 변경하지 않았다.
- 기존 Idea/Origin/Journey 구현은 삭제하지 않았고 Feature Flag가 꺼지면 기존 `IdeaJourneyPage`를 그대로 렌더링한다.

## 2. 최종 API

모든 API는 Bearer 인증과 프로젝트 소유권 검증을 적용한다.

| Method | Path | 결과 |
|---|---|---|
| `POST` | `/api/v2/projects/{projectId}/idea-conversations` | Conversation 생성. `{ "importCurrentIdeaSource": true }`이면 기존 최신 Idea Source를 참조만 한다. |
| `GET` | `/api/v2/projects/{projectId}/idea-conversations/current` | 현재 Conversation, Message, Attachment, Brief, 복원할 `activeJobId` 조회 |
| `GET` | `/api/v2/projects/{projectId}/idea-conversations/{conversationId}` | 프로젝트 범위 Conversation 상세 조회 |
| `POST` | `/api/v2/projects/{projectId}/idea-conversations/{conversationId}/messages` | 사용자 Message/구조화 답변 저장 후 `202 Accepted`와 `jobId` 반환 |
| `POST` | `/api/v2/projects/{projectId}/idea-conversations/{conversationId}/attachments` | TXT/DOCX 저장 후 `202 Accepted`와 Attachment `jobId` 반환 |
| `GET` | `/api/v2/projects/{projectId}/opportunity-brief/current?conversationId={id}` | 현재 Brief Version 조회 |
| `PUT` | `/api/v2/projects/{projectId}/opportunity-brief/fields/{fieldKey}` | 직접 편집값을 `USER_CONFIRMED`로 저장하고 새 Draft Version 생성 |
| `POST` | `/api/v2/projects/{projectId}/opportunity-brief/fields/{fieldKey}/adopt` | AI/자료 제안을 사용자 확인값으로 채택하고 새 Version 생성 |
| `POST` | `/api/v2/projects/{projectId}/opportunity-brief/fields/{fieldKey}/reject` | 제안을 `MISSING`으로 되돌리고 새 Version 생성 |
| `POST` | `/api/v2/projects/{projectId}/opportunity-brief/confirm` | 서버가 missing field와 최신 unresolved contradiction을 검증한 뒤 Confirmed Version 생성 |

Confirm 실패는 `422 OPPORTUNITY_BRIEF_INCOMPLETE`와 정확한 `fieldErrors`를 반환한다.

## 3. Conversation, Message, Attachment 계약

- Conversation 상태: `ACTIVE`, `CLOSED`.
- 영속 Message role은 G1 제약을 유지해 `USER`, `ASSISTANT`이다. `SYSTEM_STATUS` 표시는 G2 durable Job Event에서 파생한다.
- Message type: `TEXT`, `QUESTION_SET`, `BRIEF_REVIEW`, `ATTACHMENT_SUMMARY`, `JOB_STATUS`, `ERROR`.
- G3-H부터 USER TEXT는 Message 본문, Assistant 구조화 메시지는 V3의 schema/type/payload 열에 저장한다. 손상 Assistant envelope는 plain TEXT로 강등하지 않는다.
- 질문 답변은 원문에 이어 붙이지 않고 User Message envelope의 구조화 `answers`와 Brief field의 `sourceMessageId` provenance로 연결한다.
- Message와 Attachment는 G1 repository 정렬 계약으로 sequence/생성 순서를 보존한다.
- 사용자 원문은 Message 본문과 AI Task 입력에만 저장할 수 있으며 로그나 Job Event로 복사하지 않는다.

Attachment:

- 지원: UTF-8 `.txt` (`text/plain`), `.docx` (`application/vnd.openxmlformats-officedocument.wordprocessingml.document`).
- 거부: PDF, CSV, 이미지와 확장자/MIME 불일치.
- 최대 업로드: 5 MiB. DOCX는 기존 Apache POI parser의 signature, archive, 암호화, 압축 한도 검사를 추가 적용한다.
- 원본은 기존 `FileStorage`/`StoredFile`에 저장한다. Attachment는 원본과 추출 텍스트의 SHA-256 hash를 연결하고 AI 입력 시 동일 원본을 안전하게 재파싱한다.
- 파싱 상태: `UPLOADED -> PROCESSING -> EXTRACTED` 또는 `UPLOADED|PROCESSING -> FAILED`.
- 파싱 시작 transaction을 실제 parse와 분리해 `parsing.started` Event가 긴 파싱 완료 전에 commit된다.

## 4. AI Task 계약

- 외부 `IDEA_INTERPRETATION` 계약은 유지한다.
- Conversation은 전용 `IDEA_CONVERSATION_TURN` Task Type과 `idea_conversation_turn` Prompt, `OpportunityBriefDraftResult` strict Pydantic schema를 사용한다.
- 입력: 순서가 보존된 대화, 안전하게 추출한 첨부 텍스트, 현재 Brief, 지원 field 목록, 사용자 확정 상태, source 규칙, 선택적 기존 Idea Source.
- 출력 필드: `extractedFields`, `fieldSuggestions`, `assumptions`, `openFields`, `contradictions`, `clarificationQuestions`, `readiness`, `userFacingSummary`.
- AI field source는 `SOURCE_EXTRACTED`, `AI_PROPOSED`, `MISSING`만 허용한다. `USER_CONFIRMED`, `DEFAULT_ASSUMPTION`, AI `LOCKED` 또는 자동 확정 메타데이터는 전체 결과를 거부한다.
- `NEEDS_INPUT`은 2~4개의 질문을 요구한다. 질문 type은 `FREE_TEXT`, `SINGLE_SELECT`, `MULTI_SELECT`, `UNDECIDED`이다.
- Backend도 top-level/field/question exact-field set, enum, confidence 범위, 질문 수를 다시 검증한다. JSON 문법만 맞는 결과는 채택하지 않는다.
- AI 결과는 자동 Confirm하지 않는다.

## 5. Opportunity Brief Field와 provenance

고정 field는 다음 12개다.

`problem`, `targetCustomer`, `beneficiaries`, `usageContext`, `desiredOutcome`, `targetRegion`, `fixedConstraints`, `preferredConstraints`, `openDecisions`, `assumptions`, `prohibitedApproaches`, `regulatorySensitiveActivities`.

각 field는 G1의 `decisionStatus`, `sourceType`, `valueJson`과 V3 구조 provenance 열을 사용한다. 아래 JSON은 API 표현이며 DB 정본은 개별 FK/scalar 열이다.

```json
{
  "sourceMessageId": 31,
  "sourceAttachmentId": null,
  "confidence": 0.82,
  "userConfirmed": false,
  "confirmedAt": null
}
```

- Decision Status: `LOCKED`, `PREFERRED`, `OPEN`, `ASSUMPTION`.
- Source Type: `USER_CONFIRMED`, `SOURCE_EXTRACTED`, `AI_PROPOSED`, `DEFAULT_ASSUMPTION`, `MISSING`.
- G3 생성 경로는 `DEFAULT_ASSUMPTION`을 만들지 않는다.
- “아직 결정하지 않음”은 `OPEN` + `MISSING`으로 저장하며 사용자 답변 provenance를 남긴다. 필수값 충족으로 오인하지 않는다.
- Confirmed Version은 수정하지 않는다. 확정 후 편집은 based-on 관계를 가진 새 Draft Version을 만든다.
- Snapshot은 field key 순서와 provenance를 포함한 canonical JSON으로 구성하고 G1 `SnapshotHasher`로 결정적 SHA-256을 계산한다.

## 6. Confirm Gate

서버가 다음 조건을 검증한다.

- `problem` 존재
- `targetCustomer` 또는 `beneficiaries` 존재
- `desiredOutcome` 존재
- `targetRegion` 존재
- `fixedConstraints`와 `openDecisions`가 별도 field로 존재
- `regulatorySensitiveActivities` 식별 완료(빈 배열도 명시적 식별 결과로 허용)
- 최신 Assistant Message의 unresolved contradiction 없음

전체 확인 동작은 현재 non-missing field에 `userConfirmed=true`, `confirmedAt`을 기록한 새 Version을 만든 뒤 Confirm한다.

## 7. Job Event

Attachment와 대화 turn 모두 연계 `TaskRun.id`를 `jobId`로 사용한다.

- `job.idea.attachment.received`
- `job.idea.attachment.parsing.started`
- `job.idea.attachment.parsing.failed`
- `job.idea.information.extraction.started`
- `job.idea.information.extraction.completed`
- `job.idea.brief.draft.queued`
- `job.idea.brief.draft.started`
- `job.idea.brief.draft.completed`
- `job.idea.brief.draft.failed`
- `job.idea.questions.completed`

Event에는 ID, stage, status, 안전한 message key/params, 허용된 technical code만 기록한다. Prompt, provider body, Authorization, 사용자 전체 원문, 첨부 원문은 넣지 않는다. 가짜 percent는 없다.

## 8. 새로고침 복원과 기존 Journey 호환

- current Conversation API가 Message, Attachment 상태, 현재 Brief Version, 실행 중 Conversation Task 또는 Attachment의 `activeJobId`를 반환한다.
- Frontend는 `useJobEvents(activeJobId)`로 durable replay/reconnect/polling fallback을 연결하고 terminal Event에서 current 상태를 다시 조회한다.
- 기존 최신 Idea Source는 명시적 `importCurrentIdeaSource` Adapter로 Conversation에 참조 연결한다. 기존 값은 자동으로 `USER_CONFIRMED` Brief가 되지 않는다.
- Frontend flag는 `VITE_CONVERSATIONAL_VALIDATION_WORKSPACE_ENABLED=true`일 때만 신규 Workspace를 렌더링한다. 기본값/미설정은 기존 Journey fallback이다.

## 9. Frontend와 Typography

- Desktop은 대화:Brief 약 60:40 grid다.
- Mobile은 대화가 기본이며 Brief는 toggle panel로 열고, 모든 column에 `min-width: 0`과 wrapping을 적용해 가로 scroll을 방지한다.
- 대화, 질문, 첨부, 실제 Job Timeline, 직접 편집, status 변경, AI 제안 채택/거절, missing/contradiction gate를 제공한다.
- 신규 `.idea-workspace` 범위에서 카드/field label은 1단계, 본문/metadata/timeline 보조 설명은 1~2단계 축소했다.
- 기존 Project title, Journey Stepper, PageHeader h1, 전역 shared heading token은 변경하지 않았다.
- `technicalCode`, stack trace, provider payload는 UI에 표시하지 않는다.

## 10. 테스트 결과

- Backend G3/G2 targeted (`*Conversation*`, `*Opportunity*`, `*Attachment*`, `*IdeaIntake*`, `*JobEvent*`): 통과.
- Backend 전체: 298 passed, failures 0, errors 0, skipped 0.
- AI provider/model 전체: 90 passed, warnings 10(dependency deprecation).
- Frontend G3 targeted: 4 passed. G3 JobTimeline mapper 포함 targeted는 5 passed.
- Frontend lint: 통과.
- Frontend baseline: 268 passed, 기존 allowlist failure 18, unexpected failure 0.
- Frontend production build: 통과. 기존 500 kB 초과 chunk 경고가 유지된다.
- PostgreSQL test: G3는 Migration과 G2 sequence locking을 변경하지 않아 재실행하지 않았다. 직전 G2 결과는 19 passed이다.
- `git diff --check`: 최종 검증 항목에 기록한다.

## 11. 미해결 위험

- Attachment 추출 본문은 별도 신규 열 없이 원본 `StoredFile` + `extractedTextHash`로 연결하고 AI 실행 시 재파싱한다. 큰 문서에서 중복 parse 비용이 발생할 수 있다.
- G3-H에서 process-local executor를 DB claim/lease/recovery worker로 교체했다. 다중 backend instance의 처리량·운영 알림과 장시간 provider 호출 heartbeat tuning은 배포 환경 검증 위험으로 남는다.
- 운영 reverse proxy의 업로드/SSE timeout과 DOCX 실제 대용량 fixture는 배포 환경 smoke test가 필요하다.
- 신규 Workspace는 flag 기본 off다. G11 cutover 전 실제 AI provider를 사용한 E2E와 접근성/모바일 실기기 검증이 필요하다.

## 12. G4 연결 지점

G4는 `CONFIRMED` Opportunity Brief Version의 `id`, `version`, canonical `hash`, 12개 field와 provenance를 입력 정본으로 사용한다. `regulatorySensitiveActivities`, `targetRegion`, `fixedConstraints`, `prohibitedApproaches`, `openDecisions`가 Boundary 초기 입력이며, 상위 Brief Version 변경 시 G1 stale 판정 계약을 적용한다. G4 실행 Job은 G2 `JobEventPublisher`와 현재 Workspace의 Job Timeline 연결 방식을 그대로 사용한다.

## 13. G3-H Message Contract and Durable Worker Hardening

### Envelope 저장 계약

- V3는 Assistant Message에 `schema_version=1.0`, 허용 `message_type`, type별
  strict `payload_json`, 선택적 unique `task_run_id`를 저장한다.
- 외부 envelope는 `{schemaVersion, messageType, payload}` 정확히 세 필드다.
- USER TEXT는 `content`에 저장하고 envelope를 갖지 않는다.
- Backend와 Frontend 모두 unknown field/type/version 및 손상 JSON을 거부한다.
  손상 Assistant payload를 일반 TEXT로 조용히 강등하지 않는다.

### Provenance 저장 계약과 V3 결정

선택 B를 채택했다. `source_reference VARCHAR(500)`는 의미·길이·FK·검색·검증에
부적합하므로 V3에서 `source_message_id`, `source_attachment_id`, `confidence`,
`user_confirmed`, `confirmed_at`을 구조 열로 추가했다. FK와 application-level
project 일치 검증을 함께 적용한다. 기존 `source_reference`는 삭제하지 않지만 신규
정본으로 사용하지 않는다.

AI 결과는 `AI_PROPOSED`, `SOURCE_EXTRACTED`, `MISSING`만 허용한다.
`USER_CONFIRMED`, `userConfirmed=true`, `confirmedAt`, `LOCKED`, 자동
`DEFAULT_ASSUMPTION`은 채택 전에 거부한다.

### Durable Worker, lease/retry/recovery

- Task type: `IDEA_ATTACHMENT_PARSE`, `IDEA_CONVERSATION_TURN`.
- API transaction에서 TaskRun QUEUED와 도메인 link를 저장한다.
- scheduler는 DB의 `next_attempt_at` 이후 task를 pessimistic lock으로 claim하고 기존
  `task_attempts.claimed_by`, claim token, `lease_expires_at`, attempt count/result를 재사용한다.
- retryable 실패는 1/2/4초(상한 30초) bounded backoff로 재큐잉하고 max attempt 이후
  FAILED다. permanent schema/contract 오류는 재시도하지 않는다.
- 정기 recovery가 만료된 RUNNING lease를 QUEUED로 되돌리고 `job.recovered`를 발행한다.
- process-local `ideaIntakeExecutor` bean과 commit 후 submit 경로는 제거했다.

### Idempotency와 Event 순서

- Attachment key는 attachment ID+content checksum, Conversation key는 source Message ID로 결정적이다.
- Attachment가 이미 EXTRACTED이고 content checksum이 같으면 재parse하지 않고 기존 hash를 채택한다.
- Assistant Message와 Brief Version은 `task_run_id` unique link로 중복 생성을 막는다.
- Conversation Brief/Assistant/TaskResult 채택은 단일 transaction이다.
- Attachment completed Event는 extraction domain commit과 TaskRun SUCCEEDED 이후,
  Conversation terminal Event는 completion transaction commit 이후 발행한다.
- Event params에는 Prompt, provider body, Authorization, 파일/사용자 전체 원문을 넣지 않는다.

### Migration과 검증

- Migration: `V3__idea_workspace_durability_hardening.sql`.
- clean, V1→V3, V2→V3 PostgreSQL migration 경로를 테스트한다.
- 사용자 Docker 절차: [G3 Docker 검증](../verification/G3_DOCKER_VERIFICATION.md).
- Codex는 수동 브라우저 검증을 수행하거나 완료했다고 주장하지 않는다.

최종 자동 검증 결과:

- Backend G3-H targeted: 50 passed.
- Backend 전체: 299 passed, failure/error/skipped 0.
- PostgreSQL Testcontainers: 24 passed. Docker Desktop 최소 API에 맞춰 테스트
  process에서 `DOCKER_API_VERSION=1.40`, `-Dapi.version=1.40`을 사용했으며
  저장소 runtime 설정은 이 테스트 때문에 완화하지 않았다.
- AI 전체: 90 passed, dependency deprecation warning 10.
- Frontend Envelope/Workspace targeted: 9 passed.
- Frontend lint: 통과.
- Frontend baseline: 273 passed, 기존 allowlist failure 18, unexpected failure 0.
- Frontend production build: 통과. 기존 500 kB 초과 chunk warning은 유지된다.

### G4가 사용할 계약

G4는 구조화 provenance가 포함된 confirmed Brief Version만 입력 정본으로 사용한다.
장기 실행은 TaskRun QUEUED 저장 → DB worker claim/lease → bounded retry/recovery →
도메인+result commit → commit 이후 safe Job Event 순서를 그대로 사용한다.
