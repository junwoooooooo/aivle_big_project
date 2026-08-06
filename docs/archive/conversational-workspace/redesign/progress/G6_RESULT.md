# G6 Async Concept Workboard 구현 결과

- 시작 HEAD: `060020daa7276486ba378705a625598a3ca969d6`
- 브랜치: `feature/conversational-validation-workspace`
- 구현 일자: 2026-08-05
- 범위: G5 Batch/Slot/Public Concept를 기존 Conversational Idea Workspace와 G2 Job Event에 연결하는 Workboard
- 제외: Concept 생성 알고리즘·Prompt·법률 판정, Quick Assessment, 선택, 상세화, Legal Report, 수동 브라우저 검증

## 1. 구현 계약

- Batch가 없을 때는 기존 대화·Confirmed Brief·READY Boundary를 유지하고 `Concept 탐색 시작` Action만 제공한다.
- Batch 시작 후 Desktop은 왼쪽 Summary와 오른쪽 Workboard의 `30:70` 구조다. Mobile은 Workboard를 먼저, 접이식 Summary를 다음에 세로 배치한다.
- Slot은 DB/Event 도착 순서와 무관하게 `slotIndex`로 정렬한다. Focus, G5 status/currentPhase, attemptCount, safe message, legalState, updatedAt만 진행 중에 표시한다.
- 진행 중·실패·Repair/Redesign 전 Candidate 상세는 렌더링하지 않는다.
- 공개 Gate는 Batch `COMPLETED`, 정확히 3개의 `ELIGIBLE` Slot, Public Concept 정확히 3개, 동일 Brief/Boundary ID·Hash, `stale=false`, `duplicateStatus=UNIQUE`, legalState가 `IMPLEMENTABLE` 또는 `IMPLEMENTABLE_WITH_CONTROLS`인 경우에만 열린다. 하나라도 어긋나면 세 Card 모두 숨긴다.
- Timeline은 G2 `useJobEvents`의 sequence-deduplicated durable event를 사용한다. Slot Timeline은 safe param의 `slotIndex`로 분리하며 technicalCode/raw payload를 표시하지 않는다.
- Event는 재조회 신호이고 Batch·Slot·Public Concept Query가 화면 정본이다. 초기 진입, 생성 직후, 중요/terminal event, 명시적 새로고침에서 재조회하며 2초 주기 화면 Polling은 추가하지 않았다. SSE 실패 시 G2 polling fallback을 그대로 사용한다.
- 새로고침은 current → batch/slots → 완료 시 public concepts 순서로 복원한다. Stale Batch도 조회할 수 있도록 Brief/Boundary 현재 READY 여부와 무관하게 current를 조회한다.
- NEEDS_INPUT은 Brief 수정, FAILED는 retryable일 때만 재실행, STALE은 현재 Brief 확인 Action을 제공한다.

## 2. 상태·접근성·Typography

- Batch: `QUEUED`, `GENERATING`, `VALIDATING`, `REPLACING`, `COMPLETED`, `NEEDS_INPUT`, `FAILED`, `STALE`을 안전한 한국어 문구로 매핑했다.
- Slot: G5 상태를 변경하지 않고 `SCHEMA_INVALID`, provider failure, origin/boundary validation, redesign/replacement 등을 안전 문구로 매핑했다.
- 상태 변화는 `aria-live="polite"`, terminal error는 `role="alert"`, Timeline 버튼은 `aria-expanded`/`aria-controls`, Slot은 index와 상태가 포함된 accessible label을 사용한다. 모든 Card/Slot은 키보드 접근 가능하고 색상 외 문구를 함께 표시한다.
- `prefers-reduced-motion`을 적용했다. 전역 heading token은 변경하지 않았고 Workboard 범위에서 section 16px, card 15px, body 14px, meta 12px, timeline 12~13px을 사용한다.
- Project Title, Journey Stepper, Page Header 크기는 변경하지 않았다.

## 3. API와 Backend 보완

- 기존 G5 API를 유지했다.
  - `POST /api/v2/projects/{projectId}/concept-explorations`
  - `GET /api/v2/projects/{projectId}/concept-explorations/current`
  - `GET /api/v2/projects/{projectId}/concept-explorations/{batchId}`
  - `GET /api/v2/projects/{projectId}/concept-explorations/{batchId}/slots`
  - `GET /api/v2/projects/{projectId}/concepts?contract=concept-core-v1`
- safe Batch View에 Brief/Boundary version ID·hash, stale, retryable, needsInput을 additive하게 포함했다.
- Public Concept View에 G6 Card 필드, requiredDisclosures, 입력 version/hash, stale, duplicateStatus를 additive하게 포함했다.
- `POST /api/v2/projects/{projectId}/concept-explorations/{batchId}/retry`를 추가했다. 소유권, FAILED Batch, TaskRun retryable을 검사하고 같은 TaskRun을 idempotency key로 재큐잉한다.
- Migration은 없다. V5가 필요한 관계와 JSONB를 이미 보존하며 G6는 safe view/UI 연결만 추가했다. 기존 Journey 및 legacy Concept API는 유지한다.

## 4. Frontend 구조

- `features/concept-workboard/ConceptWorkboard.jsx`
- `ConceptSlotCard.jsx`, `PublicConceptCard.jsx`
- `useConceptWorkboard.js`, `conceptWorkboardApi.js`, `conceptWorkboardModel.js`
- `conceptWorkboard.css`
- 기존 `ConversationalIdeaWorkspace`는 Feature Flag 내부에서 시작/복원/되돌아가기만 연결한다. Flag OFF 경로와 기존 `ConceptJourneyPage`는 변경하지 않았다.

## 5. 검증 결과

- Frontend G6 targeted: 5 files, 20 tests passed, failure/error/skip 0.
  - `npm.cmd run test:run -- src/features/concept-workboard/conceptWorkboardModel.test.js src/features/concept-workboard/ConceptWorkboard.test.jsx src/features/concept-workboard/useConceptWorkboard.test.jsx src/features/conversational-idea/ConversationalIdeaWorkspace.test.jsx src/pages/IdeaJourneyFeatureFlag.test.jsx src/shared/async-events/JobTimeline.test.jsx`
  - Timeline 문구가 Slot과 전체 Timeline에 함께 표시되는 정상 UI를 단일 요소로 가정해 최초 1건 실패했다. 제품 코드는 변경하지 않고 테스트를 두 위치 계약으로 수정했으며 단독 재현 1 passed/7 skipped 후 전체 targeted 20/20을 확인했다.
- Frontend lint: `npm.cmd run lint`, 성공.
- Frontend production build: `npm.cmd run build`, 성공(197 modules). 기존 500 kB chunk 경고는 남아 있다.
- Frontend baseline: `npm.cmd run test:baseline`, 294 passed, 18 explicitly allowed failures, 0 unexpected failures.
- Backend targeted: `ConceptExplorationTests` 6 tests, failures/errors/skipped 0, Gradle `BUILD SUCCESSFUL`.
  - 최초 결합 실행에서 신규 retry test가 재큐잉 Task를 남겨 후속 worker test 2건과 경합했다. 단독 원인 확인 후 test cleanup에서 Task를 cancel했고 관련 3 tests를 통과한 뒤 최종 class 6/6을 확인했다. Product runtime 우회는 추가하지 않았다.
- Backend compile: `compileJava`, 성공.
- 전체 PostgreSQL 통합: `postgresTest`, 11 suites/31 tests, failures 0, errors 0, skipped 0. `DOCKER_API_VERSION=1.40`, `JAVA_TOOL_OPTIONS=-Dapi.version=1.40`으로 1회 실행했다.
- Backend 전체 회귀는 실행하지 않았다. G6는 TaskRun 공통 기반·인증·공통 Repository·기존 Journey 계약을 변경하지 않았고 Concept safe view/retry를 targeted 및 PostgreSQL 통합 Gate로 검증했다.
- AI 테스트는 실행하지 않았다. AI 코드·Prompt·Provider·Dispatch를 G6에서 변경하지 않았다.
- `git diff --check`: 통과. whitespace error 없음(LF→CRLF 안내는 기존 작업 파일의 Git 경고이며 diff check 실패가 아니다).
- 수동 Docker/OpenAI/브라우저 검증은 수행했다고 주장하지 않는다.

## 6. 변경 파일

- Backend: `ConceptExplorationApplicationService.java`, `ConceptExplorationController.java`, `ConceptExplorationTests.java`
- Frontend: `ConversationalIdeaWorkspace.jsx`, `conversationalIdea.css`, `jobEventMessages.js`, `features/concept-workboard/*`
- 문서: 이 결과, Current-to-Target Map, `verification/G6_DOCKER_BROWSER_VERIFICATION.md`

## 7. G7 연결 지점과 위험

- G7은 공개 Gate를 통과한 `concept-core-v1` 3개만 Quick Assessment 입력으로 사용할 수 있다. G6는 선택 상태나 점수를 만들지 않는다.
- Public Concept Gate는 frontend와 backend public eligibility 양쪽에서 검사하지만, 세 Concept의 동시 응답에 대한 별도 DB snapshot transaction은 추가하지 않았다. Stale/hash mismatch는 frontend에서 전체 비공개 처리한다.
- Workboard는 기존 큰 frontend bundle에 포함되어 build chunk 경고가 지속된다. 라우트 단위 분리는 후속 성능 작업이며 G6 계약에는 영향이 없다.
- 실제 반응형·키보드·screen reader·OpenAI provider UX는 사용자가 검증 문서로 G6 통합 확인을 수행해야 한다.
- commit과 push는 수행하지 않았다.
