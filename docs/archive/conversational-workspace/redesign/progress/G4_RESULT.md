# G4 Regulatory Boundary 구현 결과

- 기준 브랜치: `feature/conversational-validation-workspace`
- 기준 SHA: `7a77520e3c6c83a018ca3da5715d8d21fe8f5caf`
- 구현 일자: 2026-08-05
- 범위: Confirmed Opportunity Brief 기반 Regulatory Boundary 생성·조회·복원
- 제외 범위: Concept Generation, Legal Report, Quick Assessment, 기존 Journey 제거, 인증 변경, 수동 브라우저 검증

## 1. 최종 API

모든 API는 Bearer 인증과 프로젝트 소유권을 검증한다. 다른 프로젝트의 Brief, Run, Version은 조회하거나 실행할 수 없다.

- `POST /api/v2/projects/{projectId}/regulatory-boundaries`
  - 입력: `{ "confirmedBriefVersionId": number }`
  - 응답: `{ "runId": number, "jobId": number, "status": string }`
  - 최신 `CONFIRMED` Brief Version과 canonical hash가 일치할 때만 TaskRun을 `QUEUED`로 생성한다.
  - 같은 프로젝트·Brief Version·Brief hash는 동일 Run/TaskRun을 재사용한다.
  - 선행 조건이 없거나 Draft/Stale Brief이면 `NEEDS_INPUT`과 `missingPrerequisites`, `userMessage`, `nextAction`을 반환하고 작업을 만들지 않는다.
- `GET /api/v2/projects/{projectId}/regulatory-boundaries/current`
  - 최신 Confirmed Brief ID/hash와 일치하는 현재 Version만 반환한다.
  - 과거 Version은 삭제하지 않으며 불일치하면 `STALE`로 보존하고 current 결과에서 제외한다.
- `GET /api/v2/projects/{projectId}/regulatory-boundaries/{boundaryVersionId}`
  - Version, Evidence, Rule, Question, Conflict 및 안전한 상태를 반환한다.
- `GET /api/v2/projects/{projectId}/regulatory-boundaries/runs/{runId}`
  - durable Run/TaskRun 진행 상태와 `jobId`를 반환한다.

Raw provider response, 전체 Prompt, Authorization, 전체 Brief 원문과 법령 원문 전체는 API 또는 Job Event에 노출하지 않는다.

## 2. Boundary 상태

Run과 Version이 사용하는 상태는 다음과 같다.

- 진행: `QUEUED`, `CLASSIFYING`, `ROUTING`, `FETCHING_EVIDENCE`, `SCREENING`, `NORMALIZING_RULES`, `CHECKING_CONFLICTS`
- terminal: `READY`, `NEEDS_INPUT`, `BLOCKED`, `FAILED`, `STALE`

`READY`만 Concept Builder 입력으로 사용할 수 있다. `NEEDS_INPUT`, `BLOCKED`, `FAILED`, `STALE`에서는 Concept Generation을 시작할 수 없다.

## 3. Rule 계약

허용 Rule Type:

- `PROHIBITED_ROLE`
- `PROHIBITED_ACTIVITY`
- `ALLOWED_PATTERN`
- `REQUIRED_CONTROL`
- `REQUIRED_PARTNER`
- `REQUIRED_DISCLOSURE`
- `UNRESOLVED_FACT`

필수 저장 필드는 `ruleId`, `ruleType`, `structureKey`, `title`, `description`, `normalizedRequirement`, `evidenceIds`, `severity`, `sourceStatus`, `appliesWhen`, `userFacingReason`, `createdAt`이다. 선택 필드는 `alternatives`, `requiredQualifications`, `requiredPartnerRole`, `requiredDisclosure`, `affectedBriefFields`, `professionalReviewRecommended`이다.

Backend와 AI 양쪽 strict contract가 알 수 없는 field/type, 임의 citation, Evidence ID 불일치, 법률명·조문 제목 또는 `plainSummary`의 `normalizedRequirement` 복사, 정상 Rule의 Evidence 누락을 거부한다. `UNRESOLVED_FACT` 또는 `NEEDS_INPUT` 경로 외에는 `COMPLETE` 공식 Evidence가 하나 이상 있어야 한다.

Rule deduplication key는 다음 canonical tuple이다.

`ruleType + structureKey + canonical(normalizedRequirement) + canonical(appliesWhen)`

동일 key의 Rule은 하나로 합치고 Evidence ID는 정렬된 Set으로 병합한다. Legal Category 차이만으로 Rule을 복제하지 않는다.

## 4. Evidence 계약

Evidence는 `evidenceId`, `sourceType`, `lawName`, `article`, `title`, `effectiveDate`, `officialUrl`, `excerpt`, `plainSummary`, `whyRelevant`, `sourceStatus`, `retrievedAt`, `contentHash`를 저장한다.

- Source 상태: `COMPLETE`, `PARTIAL`, `WARNING`, `UNAVAILABLE`
- 공식 URL은 법제처 공식 source 계약을 검증한다.
- 중복 key: Boundary Version 안의 `lawName + article + effectiveDate + contentHash`
- 여러 Route/Category가 같은 조문을 가리켜도 Evidence는 한 번만 저장한다.
- `PARTIAL`, `WARNING`, `UNAVAILABLE`은 성공으로 숨기지 않고 source warning 또는 추가 사실 요구로 전달한다.

## 5. Locked Conflict와 사용자 선택지

AI가 반환한 conflict는 실제 Confirmed Brief의 `LOCKED` field만 참조할 수 있으며 서버가 채택 전에 다시 검증한다. 정보 부족은 `NEEDS_INPUT`, 공식 Evidence 기반 Rule과 LOCKED 값의 직접 충돌은 `BLOCKED`로 유지한다.

`BLOCKED` 결과는 `conflictId`, `affectedFieldKey`, `lockedValue`, `conflictingRuleIds`, `reason`, `userActionOptions`를 포함한다. 선택지는 LOCKED 값을 PREFERRED/OPEN으로 변경, 자격 확보 전제, 허가된 파트너 수행, 활동 제외, 새 아이디어 입력 등의 구조화된 제안이다. 서버는 Brief를 자동 수정하지 않는다.

Question은 `questionId`, `fieldKey`, `question`, `reason`, `answerType`, `options`, `required`, `relatedRuleIds`, `relatedEvidenceIds`를 저장한다. `answerType`은 `TEXT`, `SINGLE_SELECT`, `MULTI_SELECT`, `BOOLEAN`만 허용하며 한 Version에 중요한 질문 최대 4개를 노출한다. 답변은 G3 Conversation/새 Brief Version 경로로 처리한다.

## 6. Migration 결정

`V4__regulatory_boundary_contract.sql` additive Migration을 추가했다. G1 V2 구조의 단일 문자열 필드는 다음 불변식을 장기적으로 안전하게 표현하거나 검증할 수 없어서 Migration을 생략하지 않았다.

- pipeline/terminal/STALE Run 상태
- Version의 기준 Brief hash와 stale 시각
- Evidence content hash, source 상태, 관련성 및 조회 시각
- 실행 가능한 Rule의 structure key, normalized requirement, appliesWhen 및 대안·통제·파트너·고지 구조
- Question의 answer type/options/Rule·Evidence 참조

기존 열은 삭제하거나 rename하지 않았고 기존 Legal Precheck 데이터를 Boundary Rule로 추측 변환하지 않았다. G1 legacy row는 확정 근거로 쓰이지 않도록 명시적 warning 상태로 보존한다. clean 및 V1/V2/V3→V4 경로를 PostgreSQL에서 검증했다. 결정 근거는 `ADR-CVW-0004`에 기록했다.

## 7. Durable Worker와 TaskRun

- Task Type: `REGULATORY_BOUNDARY_GENERATION`
- API transaction: Confirmed Brief ID/hash 검증 후 `RegulatoryBoundaryRun`과 TaskRun `QUEUED` 저장
- Claim: G3-H의 DB pessimistic claim, lease owner/token, lease expiration, attempt count 재사용
- 실행: classify → route → official Evidence fetch/screen → Rule normalize → conflict check
- retry: retryable 오류만 기존 bounded backoff와 최대 3 attempt를 사용하며 permanent schema/contract 오류는 즉시 `FAILED`
- recovery: 주기적으로 QUEUED와 lease가 만료된 RUNNING Task를 다시 claim하고 `job.boundary.recovered` 발행
- idempotency: 동일 Confirmed Brief Version/hash에 한 Run, TaskRun, Boundary Version만 채택
- commit 순서: Version/Evidence/Rule/Question/Conflict와 TaskResult를 한 domain transaction에서 저장한 뒤 terminal Job Event를 발행한다. domain commit 실패 전에 completed event가 남지 않는다.

Job Event key:

- `job.boundary.queued`
- `job.boundary.classification.started`
- `job.boundary.routing.completed`
- `job.boundary.evidence.fetch.started`
- `job.boundary.evidence.fetch.completed`
- `job.boundary.screening.started`
- `job.boundary.rules.normalizing`
- `job.boundary.conflict.checking`
- `job.boundary.needs_input`
- `job.boundary.blocked`
- `job.boundary.completed`
- `job.boundary.failed`
- `job.boundary.recovered`

## 8. Concept Builder 입력 계약

Application service가 `READY` Version에만 명시적 Concept Builder 입력을 생성한다.

```json
{
  "projectId": 1,
  "opportunityBriefVersionId": 15,
  "opportunityBriefHash": "sha256:...",
  "regulatoryBoundaryVersionId": 8,
  "regulatoryBoundaryHash": "sha256:...",
  "status": "READY",
  "rules": [],
  "unresolvedFacts": [],
  "userActionOptions": [],
  "sourceWarnings": []
}
```

G4는 Concept를 생성하지 않는다.

## 9. Frontend 연결

Feature Flag가 켜진 기존 Conversational Idea Workspace 안에만 Boundary 실행/복원과 요약을 추가했다.

- 기존 G2 `useJobEvents(jobId)`와 `JobTimeline`으로 durable replay를 표시한다.
- `READY`: 허용 패턴, 금지 역할·활동, 필수 통제·파트너·고지, source warning
- `NEEDS_INPUT`: 질문, 이유, 관련 Brief field
- `BLOCKED`: 충돌 LOCKED 조건, 관련 Rule, 수정 선택지
- `FAILED`: 안전한 사용자 오류와 재시도 동작
- `technicalCode`, raw body, provider payload는 표시하지 않는다.
- `.idea-workspace` 범위의 기존 축소 typography를 유지하며 Project title, Journey Stepper, PageHeader h1과 전역 heading token은 변경하지 않았다.
- Feature Flag OFF에서는 기존 Idea Journey를 그대로 유지한다.

## 10. 자동 검증 결과

Inner loop에서는 실패 테스트를 단독 재현하고 해당 Targeted 범위가 Green이 된 뒤 다음 gate로 이동했다.

- Backend G4/G2 targeted (`*RegulatoryBoundary*`, `*JobEvent*`, `*InternalAiExecutionClient*`): 40 passed.
- Backend 전체 회귀: 310 passed, failures/errors/skipped 0. TaskRun과 공통 AI execution dispatch를 변경했으므로 단계 승인 전에 1회 실행했다.
- AI Boundary/Legal targeted: 35 passed, dependency deprecation warnings 10.
- AI 전체: 101 passed, dependency deprecation warnings 10. 공통 internal task dispatch에 Task Type을 추가했으므로 1회 실행했다.
- Frontend Boundary/Feature Flag/shared async-events targeted: 14 passed.
- Frontend lint: passed.
- Frontend baseline: 280 passed, 기존 allowlist failure 18, unexpected failure 0. 기존 Journey Page와 shared async-events를 변경했으므로 1회 실행했다.
- Frontend production build: passed. 기존 500 kB 초과 chunk warning은 유지된다.
- PostgreSQL targeted: Baseline Migration 4 passed, Regulatory Boundary 3 passed.
- PostgreSQL 전체 Testcontainers: 27 passed, failures/errors/skipped 0. V4 Migration, Repository, transaction, version uniqueness, idempotency 및 Worker recovery 변경 때문에 완료 시점에 1회 실행했다. 최초 전체 실행에서 기존 `PostgreSqlDocumentConcurrencyTests` 한 건이 일시적 pessimistic lock failure를 보였으나 단독 재실행에서 통과했고, 후속 전체 27건도 통과했다.
- `git diff --check`: passed.

실행하지 않은 검증:

- 수동 Docker/브라우저 검증은 정책에 따라 Codex가 수행하거나 완료했다고 주장하지 않는다.
- 별도 전체 E2E suite는 저장소에 정의된 G4 gate가 아니며, Backend/AI/PostgreSQL/Frontend의 공유 기반 전체 회귀를 각각 한 번 실행했으므로 추가 반복하지 않았다.

## 11. 사용자 Docker 검증

사용자가 G6/G11 시점에 실행할 명령, READY/NEEDS_INPUT/BLOCKED 입력, UI·DB·Job Event·로그 확인 항목과 실패 자료 수집 절차를 [G4 Docker 검증](../verification/G4_DOCKER_VERIFICATION.md)에 기록했다.

Codex는 수동 브라우저 검증을 수행했다고 주장하지 않는다.

## 12. G5 연결 지점

G5는 최신 Confirmed Brief ID/hash와 일치하고 stale이 아닌 `READY` Boundary Version의 Concept Builder 입력만 소비해야 한다. Rule은 법률 제목이나 plain summary가 아니라 `ruleType`, `structureKey`, `normalizedRequirement`, `appliesWhen`, Evidence ID를 가진 실행 계약이다. `NEEDS_INPUT`, `BLOCKED`, `FAILED`, `STALE`에서는 G5 작업을 생성하지 않는다.

## 13. 미해결 위험

- 실제 MOLEG 응답 품질과 provider가 생성한 운영 규칙의 법률적 적정성은 배포 환경의 공식 source/API key로 사용자 Docker 검증이 필요하다.
- Worker는 단일 AI execution 호출 안에서 route/fetch/screen/normalize를 수행하므로 세부 Job Event는 Worker의 논리 단계 경계를 나타낸다. 외부 pipeline의 세부 callback 단위 관찰성은 후속 운영 계측 과제다.
- reverse proxy의 장시간 SSE timeout, 다중 backend instance의 lease 경합, provider timeout/backoff 값은 실제 배포 환경에서 검증이 필요하다.
- 기존 production DB에 G1 실험 Boundary row가 있다면 V4는 이를 warning legacy row로 보존한다. 해당 row는 READY 근거로 승격되지 않지만 운영 데이터 분포 확인이 필요하다.
- Frontend production bundle의 기존 500 kB 초과 warning은 G4에서 해결하지 않았다.
