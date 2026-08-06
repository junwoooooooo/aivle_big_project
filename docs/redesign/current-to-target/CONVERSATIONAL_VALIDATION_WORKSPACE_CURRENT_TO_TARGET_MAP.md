# 대화형 사업검증 워크스페이스 현행→목표 매핑

- 상태: G0 기준선 감사 완료 / 구현 전 설계 동결

## 25. Provider Output Contract Stabilization 반영 (2026-08-06)

- 기존 `OpportunityBriefDraftResult`와 `valueJson` Domain/Backend 계약은 유지한다.
- OpenAI strict response format에는 `ProviderOpportunityBriefDraftResult`를 사용하며 `valueKind=TEXT|TEXT_LIST|MISSING`, typed nullable value fields로 빈 `Any` schema를 제거했다.
- Provider 결과는 결정론 mapper로 기존 `valueJson`에 복원한 뒤 Domain DTO로 재검증한다. Initial과 Repair 모두 Provider DTO schema를 사용한다.
- OpenAI `invalid_request_error/response_format` 400은 `RESULT_SCHEMA_INVALID/PROVIDER_RESPONSE_SCHEMA_REJECTED`, non-retryable 502로 안전하게 분류한다.
- 실제 `gpt-4o-mini` Provider smoke에서 strict schema 2xx, Provider validation PASSED, Domain mapping PASSED를 확인했다.
- Migration, Frontend, Worker/Retry/SSE, G4/G5/G6 상태 계약은 변경하지 않았으며 G7은 구현하지 않았다.

## 24. Conversational Intake Runtime Hotfix R3 반영 (2026-08-05)

- `IDEA_CONVERSATION_TURN` Provider 요청은 `OpportunityBriefDraftResult.model_json_schema()`를 strict `json_schema` response format과 최종 Pydantic validation에 함께 사용한다. 다른 Journey Task는 기존 `json_object` 계약을 유지한다.
- Conversation result의 AI 허용 provenance는 `decisionStatus=PREFERRED|OPEN|ASSUMPTION`, `sourceType=SOURCE_EXTRACTED|AI_PROPOSED|MISSING`이다. AI는 `LOCKED`, `USER_CONFIRMED`, 자동 확정값을 생성할 수 없다.
- Initial 결과가 schema-invalid이면 path/type만 진단하여 한 번의 REPAIR를 수행한다. Repair도 실패하면 `RESULT_SCHEMA_INVALID`, non-retryable FAILED이며 추가 Provider 호출이나 durable retry는 없다.
- Repair 성공은 기존 Assistant Message, Brief Draft, provenance, Conversation state, TaskRun completion 경로를 그대로 사용한다. Backend에는 bounded `job.idea.result.repairing` event만 추가되며 invalid value/Provider body/Prompt/사용자 원문은 저장하지 않는다.
- Frontend 전체 구조는 변경하지 않고 공통 Timeline에 `job.idea.result.repairing` 안전 문구만 추가했다. Migration과 G4/G5/G6 계약은 변경하지 않았으며 G7은 구현하지 않았다.
- 감사 기준 SHA: `967c19a8eca17ff24f159175fb3e7ecc9fb6cf9d`
- 감사 브랜치: `main`
- 감사 일자: 2026-08-05
- 상위 기준: [SPEC v1.0](../CONVERSATIONAL_VALIDATION_WORKSPACE_SPEC_v1.0.md)
- 구현 순서: [IMPLEMENTATION PLAN v1.0](../CONVERSATIONAL_VALIDATION_WORKSPACE_IMPLEMENTATION_PLAN_v1.0.md)
- 결정 기록: [DECISION_LOG](../decisions/DECISION_LOG.md)

이 문서는 현재 코드와 고정 기획서의 차이를 기록한다. 목표 계약을 새로
해석하지 않으며, 아래 `신규` 항목은 G1 이후 additive 구현 후보이지 G0에서
구현된 기능이 아니다.

## 1. 고정 불변식

| ID | 구현 기준 |
|---|---|
| I-01 | 채팅은 입력 경험이며 다음 단계 기준은 확정 Opportunity Brief Version이다. |
| I-02 | 사용자 확정값, 문서 추출값, AI 제안값, 기본 가정, 결측값을 구분한다. |
| I-03 | 법률은 선택 후 거절 심판이 아니라 Concept 생성의 구현 제약이다. |
| I-04 | 공개 Concept 3개는 Origin과 법률 구현 가능성 검사를 통과한다. |
| I-05 | 검증 실패 Draft는 내부에만 보존하고 사용자 후보 카드로 노출하지 않는다. |
| I-06 | 실제 비동기 단계만 표시하며 가짜 진행률과 숨겨진 추론을 표시하지 않는다. |
| I-07 | 선택 후 법률 단계는 신규 PASS/FAIL이 아니라 근거·이행·발행 단계다. |
| I-08 | 법률 민감 필드 변경 때만 영향 범주를 증분 재검사한다. |
| I-09 | 후속 분석 준비 상태를 READY / NEEDS_CONFIRMATION / MISSING으로 명시한다. |
| I-10 | AI 출력 계약을 약화하거나 임의 기본값으로 실패 결과를 통과시키지 않는다. |
| I-11 | 모든 단계는 입력 Hash, Version, 상태, Job Event로 재현 가능해야 한다. |
| I-12 | 프로젝트 제목·여정 위계는 유지하고 본문·카드·보조문구만 1~2단계 축소한다. |

## 2. 사용자 단계와 내부 단계

### 사용자에게 보이는 5단계

1. 아이디어 탐색
2. Concept 생성
3. 비교·선택
4. 법률 보고서
5. 사업성 분석

### 내부 전체 8단계

1. 아이디어 대화
2. Brief 확인 및 확정
3. 규제 경계
4. Concept 탐색·Origin 검사·법률 구현 검사·재설계/대체
5. Quick Assessment와 1~2개 선택
6. 선택 Concept 상세화
7. 법률 보고서 수렴·발행
8. 분석별 Readiness 계산

현행 `ProjectLayout.jsx`는 재설계 범위 3단계(아이디어, 법률 검토, 콘셉트
생성)와 미연결 레거시 6단계(분석, 선택, Persona, 인터뷰, 마케팅, 최종
보고서)를 별도 stepper로 표시한다. 목표 5단계 내비게이션은 아직 없다.

## 3. 분류 기준

| 분류 | 의미 |
|---|---|
| 재사용 | 목표 계약의 기반으로 보존·확장 가능 |
| 대체 | 현행 책임 또는 계약을 새 계약으로 교체하되 이력·호환 경로는 보존 |
| 신규 | 현행에 대응 영속 구조/API/UI가 없어 additive 구현 필요 |
| 레거시 | G11 전까지 삭제하지 않고 호환/Readiness 연결 대상으로 보존 |

## 4. Frontend route·page·provider·API

| 현재 파일/경로 | 현재 사실 | 목표 분류 | 목표 처리 |
|---|---|---|---|
| `frontEnd/src/app/router/AppRouter.jsx` | `/app/projects/:projectId`, `/idea`, `/legal`, `/journey/concept`와 레거시 redirect가 등록됨 | 재사용 | 기존 route를 유지하고 flag 또는 versioned route를 병행 |
| `frontEnd/src/app/layouts/ProjectLayout.jsx` | 현재 3단계와 미연결 레거시 6단계가 분리됨 | 대체 | 프로젝트 제목 위계를 유지하면서 사용자 5단계로 전환 |
| `frontEnd/src/features/journey/JourneyPages.jsx` `IdeaJourneyPage` | 긴 text/file 입력, 저장, AI interpretation, 구조화 Origin 질문/확정 UI | 재사용+대체 | 업로드·오류·복원 패턴은 재사용, 본문은 G3 채팅 65% + Brief 35%로 대체 |
| 같은 파일 `LegalJourneyPage` | `PASS`/`PASS_WITH_CONDITIONS`일 때 Concept 진입, Guardrail 배열과 Evidence 표시, 2초 polling | 대체 | G4 Boundary 상태/Rule UI로 대체. G9 보고서와 책임 분리 |
| `frontEnd/src/features/journey/ConceptJourneyPages.jsx` | Concept batch polling, 적격 후보 카드, Quick/Detailed/Selection 화면 | 재사용+대체 | 실패 Draft 미노출/stale 표시는 재사용. G6 Slot workboard, G7 v2 비교·선택으로 교체 |
| `frontEnd/src/features/journey/journeyApi.js` | v2 Idea/Origin/Legal/Concept/Quick/Detailed/Selection/Persona/Report 호출 집약 | 재사용 | 신규 API client를 additive 추가하고 기존 메서드는 flag-off/legacy에서 보존 |
| `frontEnd/src/features/journey/journey.css` | 주요 제목과 본문/메타 스타일이 혼재 | 재사용 | 주요 제목은 유지, section 15~16px/card 14~15px/body 13~14px/meta 11.5~12.5px 적용 |
| `frontEnd/src/features/journey/PersonaInterviewPages.jsx` | 선택 Concept 기반 Persona/Interview | 레거시 | G10 Readiness adapter로 연결, 삭제 금지 |
| `frontEnd/src/features/journey/MarketingReportPages.jsx` | Marketing 및 최종 통합 보고서 Journey | 레거시 | G9 법률 보고서와 혼동하지 않고 별도 보존 |
| `frontEnd/src/features/legal-review/**` | 기존 프로젝트 법률 사전검토 UI/API | 레거시 | 신규 Boundary/Legal Report와 병행, 즉시 제거 금지 |
| `frontEnd/src/features/feasibility/**` | 기존 사업성/실현가능성 분석 | 레거시 | G10 Readiness 충족 시 adapter로 연결 |
| `frontEnd/src/features/personas/**` | 기존 Persona 추천/선택 | 레거시 | G10 분석 입력 계약과 연결 |
| `frontEnd/src/features/report/**` | 현재 산출물을 모으는 통합 보고서 | 레거시 | G9의 immutable legal publication과 별도 유지 |

현재 frontend는 EventSource/SSE 공통 hook이나 Job Event cursor reducer가
없고, Idea/Legal/Concept 활성 작업을 2초 polling으로 복원한다.

## 5. Backend controller·service·domain

| 현재 파일 | 현재 사실 | 목표 분류 | 목표 처리 |
|---|---|---|---|
| `backend/src/main/java/com/aivle/backend/journey/JourneyController.java` | Idea 저장/조회, interpretation, Origin, 구형 legal review API 제공 | 재사용 | 소유권·envelope 패턴 유지, Conversation/Brief API는 신규 그룹으로 추가 |
| `JourneyAiService.java` | Idea Source 생성, 문서 파싱, IDEA_INTERPRETATION, 구형 LEGAL_REVIEW, TaskRun hash/결과 검증 | 재사용+대체 | 문서 파서·TaskRun·strict validation 재사용. G3 task 계약은 신규로 분리 |
| `IdeaOriginService.java` 및 `IdeaOriginVersion.java` | draft/confirmed Origin Version, 질문, confirmed values, stale 입력 기반 제공 | 재사용 | Brief migration의 source/provenance 이관 근거로 참조하되 Brief를 동일 개념으로 간주하지 않음 |
| `LegalPrecheckController.java` | start/current/refresh/답변·수정 적용 API | 재사용+대체 | 실행/재시작 패턴 보존, G4 Boundary API와 상태로 대체 |
| `LegalPrecheckService.java` | 공식 source 결과, Evidence, Guardrail 배열, PASS 계열 gate, Origin hash stale 판정 | 재사용+대체 | Evidence 조회·stale/hash 재사용. PASS gate와 배열형 Guardrail은 Boundary Rule 계약으로 교체 |
| `ConceptJourneyController.java` | generation/current/concepts/quick/shortlist/detailed/selection API | 재사용+대체 | 조회·소유권 패턴 유지. G5~G8 versioned 계약을 additive 도입 |
| `ConceptJourneyService.java` | background eligibility batch, AI generation, batch legal validation, origin trace 비교, 적격 3개, Quick/Detailed/Selection | 재사용+대체 | 적격 수집·stale·failed draft 보존을 재사용. Slot/법률 상태/trace/선택 계약은 교체 |
| `ConceptEligibilityBatch`, `ConceptDraft`, `ConceptVersion` | batch 상태와 내부 draft, `PASS/FAIL` 법률 상태, `LEGACY/ELIGIBLE` 공개 상태 | 재사용+대체 | 기존 이력 보존. G5 Slot/Attempt/Profile 구조를 additive 연결 |
| `TaskRunService`와 taskrun domain | idempotency, claim/lease, attempts, adopted/rejected results, retry/cancel, project ownership | 재사용 | 공통 Job 기반. G1 `job_events`를 TaskRun과 nullable 연결 |
| `TaskRunV2Controller.java` | TaskRun get/retry/cancel만 제공 | 재사용 | G2에서 별도 `/api/v2/jobs/{jobId}/events` SSE/polling API 추가 |
| Persona/Marketing/FinalReport Journey services | 선택 Concept 이후 기존 MVP 실행 | 레거시 | G10 adapter와 G11 compatibility 대상, 삭제 금지 |

## 6. AI model·provider·prompt

| 현재 파일 | 현재 사실 | 목표 분류 | 목표 처리 |
|---|---|---|---|
| `ai/app/models/journey.py` | `extra="forbid"` strict 모델, Idea/Legal/Concept/Quick/Detailed/Persona/Report 결과 | 재사용 | strict base 유지, G3~G9별 schema를 additive 추가 |
| `ai/app/services/journey_provider.py` | task별 prompt 로딩, Concept single-candidate fan-out, semaphore, slot별 1회 repair, aggregate 반환 | 재사용+대체 | fan-out/격리/민감 로그 방지 재사용. G5 결과는 roles/flows/legal hypothesis 중심으로 변경 |
| `ai/app/legal/pipeline.py`, `registry.py`, `moleg.py` | 공식 법률 source pipeline과 registry | 재사용 | G4 Evidence source로 재사용 |
| `ai/app/legal/concept_validation.py` | Guardrail 기반 batch legal validation, PASS/FAIL 결과 | 대체 | G5 구현 가능성 5상태와 Boundary Rule trace 계약으로 교체 |
| `ai/prompts/idea_interpretation/**` | 단발 Idea 구조화 prompt | 대체 | G3 대화 turn/attachment/Brief synthesis prompt로 역할 분리 |
| `ai/prompts/legal_review/**`와 legal pipeline prompt | 현행 legal review/Precheck | 재사용+대체 | 공식 source 검색은 재사용, Boundary normalization과 Report prompt 분리 |
| `ai/prompts/concept_generation/**` | 후보가 sourceValue와 legalTrace까지 반환 | 대체 | AI 원문 복사 책임 제거, 시스템이 deterministic trace 조립 |
| Quick/Detailed/Persona/Marketing/Final Report prompts | 레거시 Journey 계약 | 레거시 | G7 이후 단계별 계약이 준비될 때까지 보존 |

현재 backend/AI 정렬 TaskType에는 `IDEA_INTERPRETATION`,
`IDEA_LEGAL_PRECHECK`, `CONCEPT_GENERATION`,
`CONCEPT_LEGAL_VALIDATION`, `QUICK_ASSESSMENT`, `DETAILED_ANALYSIS`와
후속 레거시 task가 있다. G3의 Conversation/Attachment/Brief task 및 G9
versioned legal report task는 아직 없다.

## 7. 현재 테이블 매핑

현재 Flyway는 `V1__baseline_schema.sql` 한 파일이며 신규 구조를 위한 후속
migration은 없다.

| 현재 테이블 | 분류 | 근거/목표 |
|---|---|---|
| `idea_sources`, `idea_versions`, `idea_interpretation_runs` | 재사용 | 원문/파일 source, 해석 이력, TaskRun 연결 보존 |
| `idea_origin_versions`, `idea_clarification_questions` | 재사용 | version/confirmed/question 이력. Brief의 직접 대체 테이블은 아님 |
| `legal_precheck_runs`, `legal_precheck_versions`, `legal_guardrail_sets` | 재사용+대체 | 공식 근거 실행 이력 보존, Boundary Version/Rule로 확장 |
| `concept_eligibility_batches`, `concept_drafts`, `concept_versions` | 재사용+대체 | batch/draft/public version 이력 보존, Slot/Attempt/Profile 신규 연결 |
| `task_runs`, `task_attempts`, `task_results` | 재사용 | Job 실행의 기준. stage event stream은 별도 신규 테이블 필요 |
| `quick_assessment_runs`, `quick_assessments`, `shortlist_decisions` | 대체 | G7 10개 평가축과 1~2개 versioned selection 계약 필요 |
| `detailed_analysis_runs`, `detailed_analyses`, `concept_selections` | 레거시+대체 | 선택 순서와 enrichment 계약이 목표와 다름 |
| `persona_*`, `marketing_*`, `final_reports`, `final_report_versions` | 레거시 | 후속 MVP 보존. G9 legal report 테이블로 재사용하지 않음 |
| `reports`, `report_versions`, `report_sources`, `report_files` | 레거시 | 통합/파일 보고서 도메인. immutable legal publication과 분리 |

## 8. 현재 API 매핑

| 현재 API | 분류 | 목표 연결 |
|---|---|---|
| `POST/GET /api/v2/projects/{projectId}/ideas` | 재사용 | Conversation attachment/source 처리의 기반 |
| `POST/GET .../idea-interpretations` | 대체 | G3 conversation turn/Brief synthesis |
| `GET/PUT/POST .../idea-origin/**` | 재사용+대체 | G3 draft edit/confirm 참고, Brief API는 신규 |
| `POST/GET .../legal-prechecks/**` | 재사용+대체 | G4 regulatory-boundaries API |
| `POST/GET .../concept-generations/**`, `GET .../concepts` | 재사용+대체 | G5/G6 exploration/slot API |
| `POST/GET .../quick-assessments`, `PUT/GET .../shortlist` | 대체 | G7 v2 assessment/selection API |
| `POST/GET .../detailed-analyses`, `PUT/GET .../concept-selection` | 레거시+대체 | G8 enrichment/version 계약 |
| `GET/POST .../task-runs/{id}` | 재사용 | 상태·retry/cancel 유지 |
| `/api/v2/jobs/{jobId}/events` | 신규 | G2 SSE, Last-Event-ID, `after` polling |
| Persona/Interview/Marketing/Final Report API | 레거시 | G10 Readiness adapter 뒤에 보존 |

## 9. 예상 additive migration

G0에서는 SQL을 생성하지 않는다. 현재 migration이 V1 하나이므로 G1의
첫 후보는 `V2__conversational_validation_domain.sql`이다. 기존 테이블
삭제·rename 없이 다음을 추가한다.

1. Conversation: `idea_conversations`, `idea_messages`, `idea_attachments`
2. Brief: `opportunity_brief_versions`, `opportunity_field_values`
3. Boundary: `regulatory_boundary_runs`, `regulatory_boundary_versions`,
   `boundary_rules`, `boundary_evidence`, `boundary_questions`
4. Async: `job_events` (`task_run_id` nullable FK 포함)
5. G5 이후: `concept_exploration_batches`, `concept_slots`,
   `concept_attempts`, `concept_validation_results`, `concept_legal_profiles`
6. G7~G8: `concept_selection_versions`, `concept_detail_versions` 및 값별
   provenance/confirmation 구조
7. G9: `legal_report_versions`, `legal_report_publications`
8. G10: `analysis_readiness_snapshots`

각 migration은 clean migrate와 기존 V1 upgrade를 모두 검증하며, 이 목록은
단계별 구현 전 실제 FK/인덱스/제약 검토를 거친다.

## 10. Feature Flag와 Cutover

- 설계 명칭: `conversationalValidationWorkspaceEnabled`
- 기본값: G3~G10 구현 및 E2E 완료 전 `false`
- G0에서는 설정 파일이나 코드를 추가하지 않는다.
- flag off: 현재 `/idea`, `/legal`, `/journey/concept`와 레거시 Journey 유지
- flag on: 신규 Conversation→Brief→Boundary→Concept 흐름 사용
- G11 전환: 신규 E2E, refresh/relogin 복원, stale, 권한, 비노출 검증 후 기본값 전환
- rollback: flag를 `false`로 되돌리고 기존 route/data를 계속 사용
- 기존 데이터는 강제 변환하지 않으며 새 snapshot부터 신규 계약으로 저장

## 11. G1~G11 의존 관계

```text
G1 Domain Foundation
 ├─ G2 Job Events/SSE
 └─ G3 Conversational Intake (G1, G2)
     └─ G4 Regulatory Boundary
         └─ G5 Concept Core
             └─ G6 Concept Workboard
                 └─ G7 Quick Assessment/Selection
                     └─ G8 Enrichment
                         └─ G9 Legal Report
                             └─ G10 Analysis Readiness
                                 └─ G11 E2E Cutover (G1~G10)
```

## 12. 가장 큰 구조적 충돌 5개

1. 현행 내비게이션은 3단계+분리된 레거시 6단계이고 목표는 사용자 5단계다.
2. 현행 Idea는 단발 입력/해석/Origin이며 Conversation, message,
   attachment, Opportunity Brief field provenance 계약이 없다.
3. 현행 Legal Precheck는 PASS 계열 Concept gate와 배열형 Guardrail이다.
   목표는 Evidence와 분리된 실행 가능한 Boundary Rule이며 선택 후 법률은
   새로운 합격/불합격 gate가 아니다.
4. 현행 TaskRun은 실행·attempt·result는 보존하지만 durable stage event,
   SSE, cursor replay가 없고 frontend는 2초 polling을 사용한다.
5. 현행 Concept는 provider 내부 fan-out 뒤 aggregate 결과를 받고 법률을
   `PASS/FAIL_LEGAL`로 저장한다. 독립 Slot 영속 상태, 역할/거래/데이터
   골격, 5개 구현 가능성 상태, per-slot Event가 없다.

추가 관찰: 현행 Concept Analysis는 `DEFAULT_FINANCE`를 사용하고 Quick 뒤
Shortlist→Detailed→단일 최종 선택 순서다. 이는 결측 재무값을 0으로 공식
완료하지 않는 I-10 및 목표의 Quick 직후 1~2개 선택 계약과 달라 G7/G10에서
레거시 adapter 경계를 명확히 해야 한다.

## 13. G1의 정확한 수정 후보

G1은 AI prompt와 frontend를 변경하지 않는다.

### Migration

- `backend/src/main/resources/db/migration/V2__conversational_validation_domain.sql`

### 신규 backend 패키지 후보

- `backend/src/main/java/com/aivle/backend/journey/conversation/**`
- `backend/src/main/java/com/aivle/backend/journey/brief/**`
- `backend/src/main/java/com/aivle/backend/journey/boundary/**`
- `backend/src/main/java/com/aivle/backend/jobevent/**`
- 공통 canonical JSON/hash/stale 유틸리티는 기존
  `backend/src/main/java/com/aivle/backend/taskrun/service/CanonicalInputHasher.java`
  재사용 여부를 먼저 검증한다.

### 기존 파일의 최소 수정 후보

- `backend/src/main/java/com/aivle/backend/taskrun/domain/TaskRun.java`
  (관계가 필요할 때만; G1에서 Event 전송은 구현하지 않음)
- `backend/src/main/java/com/aivle/backend/taskrun/repository/TaskRunRepository.java`
  (job event FK 조회가 필요할 때만)

### 테스트 후보

- `backend/src/test/java/com/aivle/backend/postgres/PostgreSqlBaselineMigrationTests.java`
  — migration 수 고정 assertion을 additive migration에 맞게 갱신
- `backend/src/test/java/com/aivle/backend/journey/conversation/**`
- `backend/src/test/java/com/aivle/backend/journey/brief/**`
- `backend/src/test/java/com/aivle/backend/journey/boundary/**`
- `backend/src/test/java/com/aivle/backend/jobevent/**`

필수 검증은 clean/upgrade migration, repository, 상태 전이, project isolation,
동일 프로젝트 version uniqueness, deterministic hash, stale cascade다.

## 14. 변경 통제

SPEC 불변식, 사용자 여정, 외부 API, DB 계약, 상태 전이를 변경해야 하면
구현을 중단하고 DECISION_LOG에 문제, 영향 불변식, 대안, migration, 테스트
영향을 기록해 승인받는다. 동일 단계의 문구·스타일 미세 조정만 ADR 없이
허용하며 I-12의 제목 위계와 가독성 하한을 지킨다.

## 15. G1 구현 반영 (2026-08-05)

기준 SHA `967c19a8eca17ff24f159175fb3e7ecc9fb6cf9d`에서 G1 도메인 기반을
구현했다. 기존 Journey 테이블을 삭제하거나 이름을 바꾸지 않았으며,
`V2__conversational_validation_domain.sql`로 다음 11개 테이블을 추가했다.

- Conversation: `idea_conversations`, `idea_messages`, `idea_attachments`
- Brief: `opportunity_brief_versions`, `opportunity_field_values`
- Boundary: `regulatory_boundary_runs`, `regulatory_boundary_versions`,
  `boundary_rules`, `boundary_evidence`, `boundary_questions`
- Async: `job_events`

기존 `projects`, `idea_sources`, `stored_files`, `task_runs`는 각각 소유권,
Idea source 연결, 첨부 원본, 비동기 실행 연결에 재사용했다. 기존
`idea_origin_versions`, `legal_precheck*`, `concept_*`, `task_attempts`,
`task_results`는 레거시 Journey 보존을 위해 변경하지 않았고 신규 버전
스냅샷 저장소로 전용하지 않았다.

구현된 계약은 다음과 같다.

- Brief와 Boundary는 프로젝트별 증가 `version_number`, current 조회,
  canonical JSON의 `sha256:` hash를 가진다.
- canonical JSON은 객체 키 순서, Unicode NFC, 숫자 표기를 정규화한다.
- Brief 변경은 Boundary/Concept/Quick/Selection을 stale로, Boundary 변경은
  Concept/Quick/Selection을 stale로 판정한다.
- 모든 create/read 서비스와 event replay repository는 프로젝트 소유권 또는
  `project_id` 범위를 적용한다.
- `job_events`는 `(job_id, sequence)`를 유일하게 유지하며 `project_id`, 선택적
  `task_run_id`, `stage`, `event_type`, `status`, `message_key`,
  `message_params_json`, `technical_code`, `occurred_at`을 저장한다.

G1에서는 AI 호출, Prompt, frontend, 신규 controller, SSE publisher/replay API를
추가하지 않았다. G2는 위 `job_events` 영속 계약을 사용해 트랜잭션 경계의
publisher, project-scoped cursor replay, SSE와 polling fallback을 구현한다.

## 16. G2 구현 반영 (2026-08-05)

승인된 ADR-CVW-0002에 따라 native `EventSource` 대신 Authorization 헤더를
지원하는 fetch `ReadableStream` SSE client를 사용했다. access token을 URL,
localStorage 또는 cookie로 이동하지 않았다.

Backend에는 `JobEventPublisher`, safe payload policy, project ownership 기반
`JobEventQueryService`, `JobEventStreamService`, `/api/v2/jobs/{jobId}/events`
SSE/polling controller를 추가했다. 프로젝트 행의 pessimistic lock 아래에서
sequence를 할당하고 DB commit 후에만 live emitter로 전송한다. 초기 replay와
live publish는 job 단위 lock에서 직렬화하며 emitter timeout/error/completion,
heartbeat 실패, terminal event, backend shutdown 시 registry에서 정리한다.
Polling은 cursor 이후 최대 100건과 `nextSequence`, durable `latestSequence`,
`hasMore`를 반환한다.

Frontend에는 다음 공통 모듈을 추가했다.

- 인증 fetch stream과 SSE frame parser
- `after` polling API
- sequence 정렬/dedup reducer
- 제한적 exponential reconnect, terminal/auth 중지, polling fallback 및
  `reconnect`/`stop`을 제공하는 `useJobEvents(jobId)`
- safe message key mapper와 접근 가능한 `JobTimeline`

기존 Idea/Legal/Concept 페이지에는 연결하지 않았다. G3는 장기 작업 생성 시
`JobEventPublisher`로 실제 단계 이벤트를 발행하고 신규 화면에서
`useJobEvents`와 `JobTimeline`을 사용한다. G1 `job_events`를 그대로 재사용하므로
추가 migration은 없다.

## 17. G3 구현 반영 (2026-08-05)

G3는 G1의 Conversation/Brief/Attachment 테이블과 G2 Job Event를 사용해 다음 current-to-target 전환을 구현했다. 신규 Migration은 없다.

| 현재/기존 자산 | G3 분류 | 실제 연결 결과 |
|---|---|---|
| `IdeaJourneyPage` 직접 입력 UI | 병행/대체 | `VITE_CONVERSATIONAL_VALIDATION_WORKSPACE_ENABLED=true`에서 60:40 Conversational Workspace를 사용하고, flag off는 기존 화면을 유지한다. |
| `idea_sources` 및 Idea Source API | 재사용/레거시 | 최신 Source를 명시적 `importCurrentIdeaSource`로 Conversation에 참조 연결한다. Brief 사용자 확정값으로 자동 승격하지 않는다. |
| `idea_origin_versions` 및 기존 Origin API | 레거시 유지 | 변경·삭제하지 않았다. 기존 Journey fallback에서 계속 사용한다. |
| `idea_conversations`, `idea_messages`, `idea_attachments` | 재사용/확장 | project-scoped create/current/detail/message/attachment API와 새로고침 복원 계약을 추가했다. Message type은 versioned JSON envelope로 저장한다. |
| `opportunity_brief_versions`, `opportunity_field_values` | 재사용/확장 | 12개 고정 field, structured provenance, direct edit/adopt/reject/confirm, immutable confirmed version을 구현했다. |
| `stored_files`, `FileStorage`, DOCX parser | 재사용 | TXT/DOCX 원본 저장, 안전한 parse, SHA-256 연결, 실패 상태를 구현했다. PDF/CSV/image는 거부한다. |
| `TaskType.IDEA_INTERPRETATION` | Adapter 재사용 | `conversationContract=opportunity-brief-v1` marker로 전용 Prompt/strict result schema를 선택하며 기존 Idea Interpretation 계약은 유지한다. |
| `task_runs`, `task_attempts`, `task_results` | 재사용 | Conversation turn AI 작업의 durable 실행/결과 기반으로 사용한다. |
| `job_events`, G2 SSE/hook/timeline | 재사용/연결 | Attachment parse, extraction, Brief draft, question 생성 실제 event를 발행하고 새 UI에서 replay/fallback을 사용한다. |
| Regulatory Boundary/Legal/Concept/Quick 이후 | 미변경 | G4 이후 범위로 남겼다. |

G3 최종 API는 `/api/v2/projects/{projectId}/idea-conversations/**`와
`/api/v2/projects/{projectId}/opportunity-brief/**`다. Confirmed Brief가 G4의 유일한 상위 정본이며, 기존 Idea Source/Origin은 G4 입력으로 자동 승격하지 않는다.

Typography는 `.idea-workspace` 내부 semantic size만 축소했다. Project title, Journey Stepper, PageHeader h1, 주요 결과 제목과 전역 shared heading token은 변경하지 않았다.

## 18. G3-H 구현 반영 (2026-08-05)

| G3 초기 계약 | G3-H 판정 | 실제 hardening |
|---|---|---|
| `idea_messages.content` 안의 `idea-message-v1` JSON | 대체+호환 보존 | V3의 `schema_version`, `message_type`, `payload_json`, unique `task_run_id`; USER TEXT와 Assistant envelope 분리 |
| 손상 JSON을 legacy TEXT로 강등 | 제거 | Backend/Frontend schema `1.0` 및 type별 exact payload 검증, 안전한 표시 오류 |
| `source_reference VARCHAR(500)` provenance JSON | 대체+호환 보존 | Message/Attachment FK, confidence, user_confirmed, confirmed_at 구조 열을 신규 정본으로 사용 |
| Attachment process-local executor | 대체 | `IDEA_ATTACHMENT_PARSE` TaskRun과 DB claim/lease/retry/recovery worker |
| Conversation process-local executor + `IDEA_INTERPRETATION` adapter | 대체 | `IDEA_CONVERSATION_TURN` TaskRun과 전용 AI routing, DB worker recovery |
| executor submit 후 서버 종료 위험 | 제거 | 주기적 QUEUED claim과 만료 RUNNING recovery; 시작 시 scheduler가 같은 경로를 사용 |
| 성공 도메인/TaskRun/Event 분리 commit | 강화 | Conversation 도메인+result 채택 transaction, completed Event는 commit 이후 발행 |

V3는 additive이며 기존 Journey/Idea Source/Origin/G2 Event 테이블을 삭제하거나
rename하지 않는다. Frontend Docker build flag를 compose build arg로 전달할 수 있게 해
사용자가 G3 Workspace를 PostgreSQL compose 환경에서 검증할 수 있다.

## 19. G4 Regulatory Boundary 구현 반영 (2026-08-05)

| 기존 자산 | G4 분류 | 실제 연결 결과 |
|---|---|---|
| `regulatory_boundary_runs/versions`, `boundary_*` | 재사용+V4 확장 | Brief hash, 세부 Run 상태, stale, 구조화 Rule/Evidence/Question을 additive 열로 보존 |
| 기존 Legal Source route/MOLEG/screening | Adapter 재사용 | 공식 Evidence 조회까지만 재사용하고 Boundary normalization은 `REGULATORY_BOUNDARY_GENERATION` strict 계약으로 분리 |
| Legal Precheck PASS/FAIL·plainSummary guardrail | 레거시 유지/미재사용 | 기존 Journey는 유지하지만 G4 Rule 또는 G5 입력으로 승격하지 않음 |
| G3-H TaskRun lease/retry/recovery | 재사용 | Boundary 전용 Worker가 동일 DB claim, 3회 제한 retry, expired lease recovery 사용 |
| G2 JobEvent/SSE/Timeline | 재사용+확장 | Boundary 실제 단계와 BLOCKED terminal 상태, 안전한 message mapper 연결 |
| G3 Confirmed Brief | 상위 정본 | 최신 CONFIRMED Version ID/hash와 구조화 provenance만 Boundary 입력으로 사용 |
| Conversational Idea Workspace | 제한 확장 | 기존 Brief panel 안에 READY/NEEDS_INPUT/BLOCKED/FAILED 요약만 추가; 제목·Stepper·기존 fallback 유지 |
| Concept Generator/Legal Report | 미변경 | G5/G9 범위로 유지 |

G4의 Rule dedupe key는 `ruleType + structureKey + canonical normalizedRequirement + canonical appliesWhen`이다.
Evidence는 Boundary Version 안에서 `lawName + article + effectiveDate + contentHash`로 중복을 막는다.
최신 Confirmed Brief ID/hash와 불일치하는 과거 Boundary는 삭제하지 않고 `STALE`로 보존하며
current API와 Concept Builder 입력에서 제외한다. G5는 `conceptBuilderAllowed=true`인 `READY`
Version의 명시적 입력 계약만 사용할 수 있다.

## 20. G5 Concept Core 구현 반영 (2026-08-05)

| 기존 자산 | G5 분류 | 실제 연결 결과 |
|---|---|---|
| AI `journey_provider.py` single-candidate fan-out | 제한 재사용/보정 | concurrency 기본 1, sibling failure 격리; 신규 `concept_core.py`가 strict Slot pipeline 제공 |
| AI generated origin/legal trace와 sourceValue | 대체 | AI 출력에서 금지하고 Confirmed Brief/G4 Boundary에서 서버가 deterministic trace 생성 |
| 기존 Concept batch/draft/version | legacy 유지 | 삭제·변환하지 않고 기존 Journey가 계속 사용 |
| `ConceptVersion.eligible()` 축약 매핑 | 신규 경로에서 미사용 | G5 전체 Skeleton은 `exploration_concepts.skeleton_json`과 assessment에 보존 |
| G3-H/G4 TaskRun worker | 재사용 | `CONCEPT_EXPLORATION` claim/lease/retry/recovery/idempotency 적용 |
| G2 JobEvent | 재사용/확장 | Batch 및 실제 Slot phase event를 safe params로 발행 |
| G4 Concept Builder input | 상위 정본 | 최신 Confirmed Brief와 READY Boundary의 ID/hash 일치 시에만 start |
| Quick Assessment/Selection/Legal Report | 미변경 | G7/G9 범위로 유지 |

V5는 Batch, Slot, Attempt, Origin validation, Legal assessment, Boundary Rule trace, 공개 Concept를 additive table로 분리한다. Batch COMPLETED와 `IMPLEMENTABLE|IMPLEMENTABLE_WITH_CONTROLS`, current ID/hash, uniqueness를 모두 만족한 정확히 3개만 `GET /api/v2/projects/{projectId}/concepts?contract=concept-core-v1`에 공개한다. 나머지 candidate는 내부 이력으로만 보존한다.

G6 입력은 Slot View와 공개 Concept View다. G5는 Workboard, 비교, Quick Assessment, 선택 UI를 구현하지 않는다.
## 21. G6 Async Concept Workboard 반영 (2026-08-05)

- `ConversationalIdeaWorkspace`의 Confirmed Brief + READY Boundary 이후에 Feature Flag 내부 `Concept 탐색 시작`과 Workboard 전환을 연결했다. 기존 Journey/flag-off 경로는 유지한다.
- `features/concept-workboard`가 G5 Batch·Slot·Public Concept safe view와 G2 authenticated SSE/polling fallback을 소비한다. Event는 refresh signal이고 Query가 상세 정본이다.
- Batch/Slot은 safe status/message만 진행 중에 노출한다. Draft·repair/redesign/rejected Candidate는 숨기고, strict 3-Concept gate 통과 후 세 상세 Card를 동시에 공개한다.
- G5 API를 additive하게 확장해 safe Batch input hashes/stale/retryable, full public Card fields/input hashes/duplicate status를 제공하고 FAILED retry endpoint를 추가했다.
- Migration은 없다. V5 Concept Core 저장 계약을 재사용한다.
- Desktop 30:70, Mobile Workboard-first/collapsible Summary, `aria-live`, alert, `aria-expanded`, keyboard/reduced-motion 및 Workboard-local typography를 적용했다.
- G7은 `concept-core-v1` 공개 3개와 validated snapshot hash만 Quick Assessment 입력으로 사용해야 하며 G6는 평가·선택 상태를 만들지 않는다.

## 22. Conversational Intake Runtime Hotfix 반영 (2026-08-05)

- Idea Intake claim은 detached `TaskRun`/`Project` Entity 전달에서 transaction 내부 scalar `TaskRunWorkerContext`/`ClaimContext` 캡처로 교체했다. Worker와 AI 실행은 ID/hash/attempt/idempotency scalar만 사용하며 필요한 Entity는 명시적 transaction에서 다시 조회한다.
- Conversation Turn 성공은 Assistant Envelope, Brief Draft/provenance, Conversation state/active job, TaskRun result를 atomic commit하고 그 뒤 terminal Job Event를 발행한다.
- Worker claim 단위 error boundary가 retryable/permanent/unknown failure를 bounded retry 또는 FAILED로 수렴시킨다. project/conversation 불일치 capture는 claim transaction 전체를 rollback한다.
- SSE registry는 completion/timeout/error/broken heartbeat/terminal에서 job별 emitter를 제거하고 client abort 후 JSON error body를 쓰지 않는다.
- Conversation terminal event는 durable sequence를 job별 dedupe한 뒤 current Conversation 재조회 신호로 사용한다. 2초 화면 polling은 추가하지 않았다.
- Message와 Job Event API `occurredAt`은 UTC ISO-8601 `...Z`로 통일하고 Frontend는 공통 local formatter를 사용한다. Migration은 없다.
- 기존 Journey, G4 Boundary, G5 Concept Core, G6 Workboard 계약은 변경하지 않았으며 G7은 구현하지 않았다.

## 23. Conversational Intake Runtime Hotfix R2 반영 (2026-08-05)

- `IDEA_CONVERSATION_TURN`은 canonical Task Type으로 유지하고 `contracts/internal-ai/idea-conversation-turn-v1.*.json`을 Backend/AI shared fixture로 사용한다.
- AI Internal Execution endpoint의 legacy 공통 `textContents` 검증에서 Conversation Task를 분리해 strict `IdeaConversationTurnInputV1`으로 검증한다. 다른 Task의 기존 input 계약은 유지한다.
- Conversation input은 version/Project/Owner/Conversation/Source Message/Brief ID, ordered USER·versioned Assistant Message, extracted attachment hash/text, current Brief provenance, supported fields/source rules를 보존한다.
- Draft 없음은 `currentBrief=null`, attachment 없음은 `attachments=[]`로 구분한다. Brief field `valueJson`은 JSON 문자열이 아닌 실제 JSON value다.
- Contract 400은 값 없는 bounded field path/type/category만 내부 진단에 사용하고 permanent FAILED로 종료한다. Job Event와 사용자 UI에는 field path나 원문을 노출하지 않는다.
- Migration, Frontend, G4/G5/G6 상태 의미는 변경하지 않았으며 G7은 구현하지 않았다.
