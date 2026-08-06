# 대화형 AI 사업검증 워크스페이스 구현 마스터플랜 v1.0

- 상태: **DESIGN FREEZE 구현 계획**
- 기준 저장소: `chamgo260210/bp_new_2`
- 기준 커밋: `967c19a8eca17ff24f159175fb3e7ecc9fb6cf9d`
- 구현 방식: 로컬 Codex 5.6sol, 추론 중간, 단계별 단일 작업
- 원칙: 한 단계씩 구현·검증·커밋하고 다음 단계로 이동한다.

## 1. 작업 운영 규칙

1. 각 단계 시작 전에 `git status`, 현재 브랜치, 기준 커밋, 관련 계약·테스트를 확인한다.
2. 한 작업에서는 명시된 범위만 수정한다. 다음 단계 기능을 미리 구현하지 않는다.
3. 외부 API, DB, 상태, 이벤트, AI Schema를 바꾸면 문서·Fixture·테스트를 동시에 갱신한다.
4. 기존 Journey를 삭제하지 않는다. 신규 경로가 E2E Green이 될 때까지 병행하거나 Feature Flag를 사용한다.
5. Contract를 느슨하게 하거나 누락값을 임의 기본값으로 채워 테스트를 통과시키지 않는다.
6. 작업 완료 보고에는 변경 파일, 계약, Migration, 테스트 명령·결과, 미해결 위험을 포함한다.
7. 사용자 승인 없이 commit/push하지 않는다. 사용자가 단계별 commit을 승인한 경우에만 지정 범위를 commit한다.

## 2. 브랜치·커밋 전략

권장 통합 브랜치:

```text
feature/conversational-validation-workspace
```

단계별 커밋 예시:

```text
docs: freeze conversational validation workspace design
feat: add conversation brief and job event foundations
feat: stream durable job events to the workspace
feat: add conversational idea intake and brief confirmation
feat: normalize regulatory evidence into boundary rules
feat: isolate concept slots and validate implementable candidates
feat: add asynchronous concept exploration workboard
feat: align quick assessment and concept selection contracts
feat: enrich selected concepts and detect legal-sensitive changes
feat: publish versioned legal compliance reports
feat: expose analysis input readiness and confirmation states
test: complete conversational workspace end-to-end cutover
```

## 3. 단계 의존 관계

```text
G0 Baseline/Docs
  └─ G1 Domain Foundation
       ├─ G2 Job Events/SSE
       └─ G3 Conversational Intake
            └─ G4 Regulatory Boundary
                 └─ G5 Concept Core
                      └─ G6 Concept Workboard
                           └─ G7 Quick Assessment/Selection
                                └─ G8 Enrichment
                                     └─ G9 Legal Report
                                          └─ G10 Analysis Readiness
                                               └─ G11 E2E Cutover
```

---

# G0. 기준선 감사와 설계 동결

## 목적

현행 코드와 새 기획 사이의 재사용·대체·보존 범위를 문서로 확정한다. 기능 코드는 변경하지 않는다.

## 작업

- 현재 `main`과 기준 커밋을 확인한다.
- Idea Source, Idea Origin, Legal Precheck, Concept Eligibility, TaskRun, Frontend Journey route를 실제 파일 기준으로 매핑한다.
- `docs/redesign/CONVERSATIONAL_VALIDATION_WORKSPACE_SPEC_v1.0.md`를 생성한다.
- `docs/redesign/CONVERSATIONAL_VALIDATION_WORKSPACE_IMPLEMENTATION_PLAN_v1.0.md`를 생성한다.
- `docs/redesign/DECISION_LOG.md`에 Design Freeze ADR을 추가한다.
- 기존 공식 Journey와 레거시 MVP의 보존 범위를 명시한다.
- Feature Flag 명칭과 기본값만 설계한다. 기능은 구현하지 않는다.

## 산출물

- 현행→목표 매핑표
- 재사용/대체/신규/레거시 분류표
- DB Migration 예상 목록
- API Version 전략
- 위험 목록

## 검증

```powershell
git diff --check
git status --short
```

## 완료 조건

- 기능 파일 변경 없음
- 문서가 고정 기획서의 불변식과 모순 없음
- 다음 단계가 수정할 정확한 패키지와 파일 후보가 식별됨

---

# G1. Conversation·Brief·Boundary·Event 도메인 기반

## 목적

대화, 구조화 Brief, 규제 경계, Job Event의 영속 모델과 상태 계약을 추가한다. 아직 AI 호출·SSE·새 UI를 연결하지 않는다.

## Backend

- additive DB migration 추가
- 권장 엔티티/테이블:
  - `idea_conversations`
  - `idea_messages`
  - `idea_attachments`
  - `opportunity_brief_versions`
  - `opportunity_field_values`
  - `regulatory_boundary_runs`
  - `regulatory_boundary_versions`
  - `boundary_rules`
  - `boundary_evidence`
  - `boundary_questions`
  - `job_events`
- 상태 Enum과 전이 규칙 구현
- Field Decision Status: `LOCKED`, `PREFERRED`, `OPEN`, `ASSUMPTION`
- Source Type: `USER_CONFIRMED`, `SOURCE_EXTRACTED`, `AI_PROPOSED`, `DEFAULT_ASSUMPTION`, `MISSING`
- Snapshot Hash와 Stale 판정 유틸리티 작성

## API

Read/Create 중심의 최소 API 계약만 추가한다. 장기 작업 시작은 후속 단계에서 연결한다.

## 테스트

- Migration test
- Entity/repository test
- 상태 전이 unit test
- 동일 프로젝트 Version 번호와 현재 Version 조회
- 프로젝트 권한 격리
- Snapshot Hash 결정론

## 변경 금지

- 기존 Idea/Legal/Concept API 제거
- Frontend Journey 교체
- OpenAI Prompt 변경

## 완료 조건

- 깨끗한 DB와 기존 DB 마이그레이션 통과
- 신규 도메인 단위 테스트 Green
- 기존 전체 Backend 테스트 Green

---

# G2. Durable Job Event와 SSE

## 목적

장기 작업 이벤트를 저장하고 새로고침 후 재생할 수 있는 공통 인프라를 만든다.

## Backend

- `JobEventPublisher`, `JobEventRepository`, `JobEventQueryService`
- 기존 `TaskRun`과 연결 가능한 `job_id`, `task_run_id`, `sequence`
- Event message는 `messageKey + messageParams` 중심
- 기술 오류는 `technicalCode`, 사용자 문구와 분리
- SSE endpoint:

```text
GET /api/v2/jobs/{jobId}/events
```

- `Last-Event-ID`와 `after` cursor 지원
- heartbeat, connection cleanup, project ownership 검증
- Polling fallback endpoint 제공

## Frontend

- 공통 `useJobEvents(jobId)` hook
- Authorization header를 사용하는 fetch + ReadableStream SSE 연결, 재접속, cursor, deduplication
- SSE 실패 시 Polling fallback
- 브라우저 새로고침 후 이벤트 복원

## 테스트

- sequence ordering
- Last-Event-ID replay
- 중복 이벤트 제거
- 다른 프로젝트 이벤트 접근 금지
- SSE disconnect/reconnect
- Backend shutdown 시 emitter cleanup
- Frontend reducer와 fallback

## 완료 조건

- Mock 장기 Job으로 UI에 실제 단계 메시지가 순서대로 표시
- 가짜 퍼센트 없음
- 기술 로그와 사용자 메시지 분리

---

# G3. 대화형 Idea Intake와 Opportunity Brief

## 목적

기존 긴 입력 중심 화면을 하이브리드 채팅 + 구조화 정보판으로 교체한다. Feature Flag 아래 신규 경로로 구현한다.

## AI Contract

신규 Task Type 후보:

```text
IDEA_CONVERSATION_TURN
IDEA_ATTACHMENT_EXTRACTION
OPPORTUNITY_BRIEF_SYNTHESIS
```

응답에는 다음을 포함한다.

- 사용자에게 보낼 메시지
- 한 번에 2~4개 후속 질문
- 추출 또는 변경된 Brief field patch
- 각 field의 source type, decision status suggestion, confidence
- readiness와 missing/contradiction 목록

AI는 Brief Version을 직접 확정하지 않는다.

## Backend

- Conversation 생성·message 저장
- 사용자 메시지 저장 후 비동기 Turn Job 시작
- Attachment 메타데이터·파싱 Job·추출 결과 연결
- AI field patch 검증 및 Draft Brief Version 반영
- 사용자의 직접 필드 수정
- `confirm` 시 필수 항목·모순·OPEN 공간 검사
- Confirmed Brief Version과 canonical hash 저장

## Frontend

- 상단 프로젝트/Stepper 유지
- Desktop: Chat 65% + Brief 35%
- Mobile: Chat + Brief Drawer
- 메시지, 질문, 선택지, 파일 카드, Job Event 카드
- Brief field별 상태·출처·편집
- `[대화로 수정] [직접 편집] [확정하고 진행]`
- 본문·카드 폰트는 기존보다 1~2단계 축소하되 주요 제목은 유지

## 테스트

- 짧은 Idea → 후속 질문 → Brief ready
- 문서 첨부 → 추출 → 질문 매핑
- 답변 충돌 표시
- 모든 필드를 LOCKED했을 때 Concept 다양성 경고
- confirm 전 다음 단계 차단
- refresh 후 conversation/brief 복원
- 파일 형식·크기·권한 검증

## 완료 조건

- 사용자 승인 전에는 CONFIRMED Brief가 생성되지 않음
- 다음 단계 입력은 대화 전체가 아니라 Confirmed Brief Snapshot

---

# G4. Regulatory Boundary

## 목적

기존 Legal Evidence를 Concept 생성에 사용 가능한 Boundary Rule로 정규화한다.

## 핵심 계약

```text
PROHIBITED_ROLE
PROHIBITED_ACTIVITY
ALLOWED_PATTERN
REQUIRED_CONTROL
REQUIRED_PARTNER
REQUIRED_DISCLOSURE
UNRESOLVED_FACT
```

각 Rule은:

- ruleId
- ruleType
- statement
- rationale
- affectedBriefFields
- evidenceIds
- severity
- userActionOptions

을 가진다.

## Pipeline

1. Brief 활동·역할 분류
2. 법률 Route 선택
3. 공식 법령·조문 조회
4. 관련성 Screening
5. Evidence 저장
6. Evidence → Boundary Rule 정규화
7. LOCKED field 충돌 검사
8. READY / NEEDS_INPUT / BLOCKED 결정

## UI

- 실제 단계 이벤트 표시
- 허용 방향, 피해야 할 방향, 필수 조건, 미해결 사실
- BLOCKED 시 수정 가능한 선택지
- 사용자 답변 후 새 Brief/Boundary Version

## 테스트

- 같은 Evidence가 중복 category로 들어와도 Rule dedupe
- 조문 제목이 그대로 hard constraint가 되지 않음
- BLOCKED와 NEEDS_INPUT 구분
- 공식 Evidence 없는 Rule 금지 또는 명시적 source warning
- Boundary 변경 시 Concept stale

## 완료 조건

- Concept 입력에 법률 조문 문장이 아니라 실행 가능한 Rule이 전달됨

---

# G5. Concept Core 안정화

## 목적

현재 single-candidate fan-out을 유지하되 실패 격리, 결정론 Trace, 구현 가능성 상태를 완성한다.

## AI Model

Concept Generator에서 제거 또는 축소:

- sourceValue 복사 책임
- 정식 legalTrace
- 공식 법률 원문

Concept Generator가 반환:

- Concept implementation skeleton
- roles, transactionFlow, dataFlow, physicalActivities
- partnerRequirements
- legalImplementationHypothesis

시스템이 생성:

- Origin Trace source and key
- Boundary Rule trace
- Legal Evidence linkage

## Orchestrator

- `asyncio.gather(..., return_exceptions=True)` 또는 동등한 Slot 격리
- Slot 결과: VALID / SCHEMA_INVALID / TRANSIENT_PROVIDER_FAILURE / PERMANENT_PROVIDER_FAILURE
- 정상 Slot 보존
- Slot당 initial + repair 또는 retry 중 하나
- Provider 동시성 1 기준선 → 2 → 3 검증
- 법률 상태:
  - IMPLEMENTABLE
  - IMPLEMENTABLE_WITH_CONTROLS
  - REDESIGN_REQUIRED
  - INSUFFICIENT_INFORMATION
  - HARD_BLOCK
- REDESIGN_REQUIRED는 1회 재설계 후 재검사
- 적격 부족 시 대체 Slot 생성

## Backend

- 기존 Concept Eligibility Batch와 신규 Boundary Version 연결
- Concept Draft에 roles/flows/profile 저장
- READY 3개만 publish
- Reserve 후보 정책은 내부 옵션

## 테스트

- 한 Slot timeout이 다른 Slot repair를 막지 않음
- 정상 Slot 재호출 없음
- source trace는 코드가 조립
- REDESIGN_REQUIRED → 수정 → 통과
- HARD_BLOCK → 폐기·대체
- 3개 미확보 + 사용자 정보 필요 → NEEDS_INPUT
- Provider 총 호출 상한

## 완료 조건

- 실제 Docker/OpenAI 샘플에서 검사 수가 0에 머무르지 않고 Slot별 상태가 독립 진행

---

# G6. Concept Workboard UI

## 목적

3개 Slot의 비동기 작업을 보드로 표시하고 READY 후보만 상세 공개한다.

## UI

- Brief/Boundary 요약 30%, Concept board 70%
- Slot 카드:
  - focus
  - current stage
  - short user message
  - expandable timeline
- 상세 후보는 READY 전 숨김
- REDESIGNING 메시지
- FAILED/NEEDS_INPUT의 복구 Action
- READY 카드에 구현 구조와 법률 조건 표시
- Stale banner와 재생성

## Typography

- 프로젝트/여정 제목 유지
- section 15~16px
- card 14~15px
- body 13~14px
- meta 11.5~12.5px

## 테스트

- 이벤트 순서가 비순차여도 Slot별 타임라인 정렬
- refresh 복원
- 실패 Draft 미노출
- 3개 READY일 때만 비교 단계 활성화
- keyboard/ARIA live

---

# G7. Quick Assessment와 Concept 선택

## 목적

Legacy 변환 없이 신규 Concept 계약을 직접 평가한다.

## 평가 계약

- problemFit
- customerValue
- differentiation
- executionFeasibility
- revenuePotential
- testability
- legalFeasibility
- complianceComplexity
- partnerDependency
- assumptionUncertainty

## 작업

- 현재 Quick Assessment input mapping 제거 또는 v2 추가
- `risks=[]`, `problem=originTrace JSON` 같은 Legacy 변환 금지
- 3개 후보 비교, 강점·약점·근거·검증 과제
- 사용자 1~2개 선택, 선택 이유 저장
- Selection Version과 Snapshot Hash

## 테스트

- 정확히 현재 READY 후보만 평가
- 자동 선택 금지
- stale 후보 선택 금지
- 선택 이유·Version 저장

---

# G8. 선택 Concept 상세화

## 목적

선택 후보만 기능·역할·거래·데이터·운영·재무 가설 수준으로 확장한다.

## 필드

- featureArchitecture
- userJourney
- actorResponsibilities
- transactionFlow
- paymentRefundFlow
- dataLifecycle
- physicalOperations
- channels
- pricingHypotheses
- revenueFlow
- costDrivers
- metrics
- experimentPlan

각 값은 provenance와 confirmation state를 갖는다.

## 법률 민감 변경 감지

- role
- payment actor
- delivery/collection actor
- data purpose/retention
- region
- regulated product/service
- advertising claims

변경 시 영향 범주만 `LEGAL_RECHECK_REQUIRED`.

## 테스트

- AI 제안이 자동 확정되지 않음
- 민감 변경 diff 계산
- 비민감 문구 변경은 법률 재검사 불필요

---

# G9. 법률·규제 상세 보고서

## 목적

기존 Concept 법률 프로필과 Evidence로 설명·이행 중심의 Versioned 보고서를 발행한다.

## 이전 junwoo UX에서 이관

- 10개 범주 또는 적용 범주별 종합 판정
- 쉬운 설명
- 5단 판단 사슬
- 판매/사업 개시 전 할 일
- 조건부 의무
- 추가 질문과 confirmed facts
- 수정 요청과 선택 가능한 수정안
- 증분 재검토 diff
- CONVERGED와 정식 발행 Snapshot
- 전문가 확인과 책임 한계

## 변경점

- 신규 PASS/FAIL Gate로 사용하지 않음
- Concept 생성 때 검증한 Legal Feasibility Profile을 기준으로 작성
- 민감 변경이 있을 때만 범주 재검사
- 보고서 발행 이후 Snapshot 불변

## 테스트

- report source concept/hash 일치
- 5단 chain과 evidence URL
- 수정안 적용 → 새 Concept Detail Version
- incremental diff
- publication snapshot 보존

---

# G10. Analysis Readiness

## 목적

시장·BM·기술운영·재무·Persona·사용자 검증의 입력 준비 상태를 계산한다.

## 상태

```text
READY
NEEDS_CONFIRMATION
MISSING
BLOCKED
```

## 작업

- 분석별 필수 Field Catalog
- 현재 값, provenance, confirmation, missing reason
- Readiness Snapshot 저장
- 사용자 확인 UI
- 재무는 결측값 0 처리 금지
- 준비 상태에 맞춰 기존 MVP 분석 route 연결

## 테스트

- 값 하나 변경 시 관련 분석만 readiness 변경
- AI 제안은 NEEDS_CONFIRMATION
- 필수 결측은 MISSING
- upstream stale은 BLOCKED

---

# G11. E2E 통합·공식 Journey 전환

## 목적

신규 구조를 공식 Journey로 전환하고 레거시를 안전하게 보존한다.

## E2E

1. 짧은 Idea 대화 → Brief → Boundary → 3 Concept → 선택 → Report → Readiness
2. 파일 첨부 기반 Intake
3. Boundary BLOCKED 후 수정
4. Slot 혼합 실패와 독립 복구
5. Concept redesign과 대체
6. SSE reconnect
7. Stale cascade
8. 법률 민감 변경 증분 검사
9. 재무 필수 가정 결측
10. 접근권한과 Secret 비노출

## 전환

- Feature Flag 기본값 전환
- 기존 route redirect 또는 compatibility wrapper
- README, LOCAL_RUN, CURRENT_BASELINE, architecture docs 갱신
- CI에 신규 테스트 추가
- 운영 Rollback 절차 작성

## 최종 품질 게이트

```powershell
# frontend
npm.cmd ci
npm.cmd run lint
npm.cmd run test:baseline
npm.cmd run build

# ai
python -m pytest tests
python docs/contracts/fixtures/internal-ai-v1/validate_fixtures.py

# backend
.\gradlew.bat clean test postgresTest --no-daemon --console=plain

# optional infrastructure
.\gradlew.bat minioTest --no-daemon --console=plain

# repository
git diff --check
git status --short
```

## 완료 조건

- 사용자 공개 후보는 구현 가능 후보 3개뿐
- 선택 후 법률 보고서가 거절 Gate가 아님
- 후속 분석 준비 상태가 정직하게 표시
- 새로고침·재로그인 후 비동기 상태 복원
- 기존 공식 Journey 회귀 없음
