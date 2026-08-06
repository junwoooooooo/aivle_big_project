# AI Journey Redesign Specification v0.4

- Status: CURRENT_CANONICAL
- Baseline date: 2026-08-04
- Scope: Idea → Legal → 적격 Concept 3개
- Supersedes: 이전 redesign draft와 완료된 실행계획(Git history 보존)
- Implementation Status: IMPLEMENTED_WITH_REMAINING_VERIFICATION

## 1. 문서 관계

v0.4는 이전 draft의 제품 결정, Idea 입력 모델, 출처 구분, 잠금과 stale 원칙 및 현재 구현 상태를 통합한 유일한 redesign 권위다. 이전 문서와 완료된 실행계획은 Git history로 보존한다. 구현 완료 판단은 이 문서만이 아니라 실제 Controller, Service, Entity, Migration, Internal client와 FastAPI dispatcher를 함께 근거로 한다.

## 2. 현재 공식 범위

`Idea 입력 → AI 해석 → Idea Origin Draft 및 보완 질문 → Idea Origin 확정 → Legal Precheck → Legal Guardrail → Concept 생성 → Origin Integrity → Concept Legal Validation → 적격 Concept 3개 표시`

적격 Concept 3개 표시가 현재 Journey의 종료점이다. Concept 분석·선택·Persona·Interview·Marketing·Report 코드는 보존된 기존 MVP 실험 기능으로 유지하며 현재 Journey에 자동 연결하지 않는다.

## 3. 유지되는 핵심 결정

| 결정 | 현재 규칙 |
|---|---|
| Idea Origin 중심 | Concept은 확정된 Origin snapshot에서 파생한다. |
| 법률 검증 2회 | Idea Legal Precheck와 Concept Legal Validation을 구분한다. |
| 사용자 값 우선 | USER_CONFIRMED 값은 하위 단계가 덮어쓰지 않는다. |
| 실패 후보 비노출 | Origin 또는 Legal 검증 실패 후보는 표시하지 않고 대체 생성한다. |
| 적격 후보 수 | 기본 3개이며 기준을 낮춰 채우지 않는다. |
| Concept 동결 | 두 검증을 통과한 Concept만 ELIGIBLE로 게시한다. |
| Spring 소유 | 업무 상태, version, TaskRun, 결과 검증·채택은 Spring이 소유한다. |
| Migration | PostgreSQL 최종 스키마는 통합 `V1__baseline_schema.sql`로 생성한다. 기존 DB upgrade는 지원하지 않는다. |

값의 출처 모델도 v0.3을 유지한다.

- `USER_CONFIRMED`: 사용자가 확인하거나 결정한 값
- `CONCEPT_GENERATED`: 허용 범위 안에서 Concept별로 생성한 값
- `UPSTREAM_OUTPUT`: 검증된 앞 단계 결과
- `STAGE_LOCAL`: 현재 단계의 검색·추정·계산·비교용 값

필요 시점 분류(`REQUIRED_FOR_IDEA_ORIGIN`, `REQUIRED_FOR_LEGAL_PRECHECK`, `REQUIRED_FOR_CONCEPT_BUILD` 및 후속 단계용 분류) 역시 유지한다. 다만 후속 단계용 값은 현재 공식 Journey 실행 범위가 아니다.

### 3.1 현재 Idea Origin 입력 모델

현재 Origin 필수 구조는 실제 `IdeaOriginService`와 AI schema를 따른다.

| 필드 | 의미 | 현재 처리 |
|---|---|---|
| `productServiceDescription` | 제품·서비스 설명 | Origin 필수, 사용자 보완 가능 |
| `problem` | 해결할 문제 | Origin Integrity 보존 대상 |
| `target` | 목표 고객 | Origin Integrity 보존 대상 |
| `solution` | 해결 방식 | Origin 필수 |
| `coreValue` | 핵심 가치 | Origin Integrity 보존 대상 |
| `primaryCategory` | 주 카테고리 | Origin/Legal routing 입력 |
| `targetRegion` | 초기 대상 지역 | Origin/Legal routing 입력 |
| `fixedValues` | 변경 불가 사용자 값 | Origin Integrity 보존 대상 |

AI metadata의 `sourceType`은 `USER_CONFIRMED` 또는 `AI_PROPOSED`, 상태는 `MISSING`/`AI_PROPOSED`/`USER_CONFIRMED`다. 질문 requirement는 `REQUIRED_FOR_IDEA_ORIGIN` 또는 `REQUIRED_FOR_LEGAL_PRECHECK`다. AI 제안은 사용자의 답변과 확인 출처가 저장되기 전 `USER_CONFIRMED`로 승격하지 않는다.

가격·수익모델·채널처럼 사용자가 이미 확정한 선택값은 confirmed values로 잠그고 Concept trace에서 보존한다. 시장·BM·기술운영·재무의 추가 입력은 보존 MVP 또는 후속 제품 범위이며 현재 Idea 첫 화면에서 모두 요구하지 않는다.

Origin이 변경되면 이전 Legal Precheck/Guardrail/Concept는 current 입력으로 재사용하지 않는다. 과거 결과는 삭제하지 않고 history/evidence로 보존한다.

## 4. 현재 구현 상태

### 4.1 Idea

- 자유 입력을 AI가 해석한다.
- Origin Draft, field metadata와 clarification question을 생성한다.
- 사용자가 보완하고 확정한 snapshot과 confirmed values를 다음 단계 입력으로 사용한다.

### 4.2 Legal Precheck and Guardrail

- `IDEA_LEGAL_PRECHECK` TaskRun이 법률 source pipeline을 실행한다.
- versioned registry와 법제처 source 결과를 바탕으로 route, finding, evidence와 추가 질문을 만든다.
- Precheck 결과에서 Concept 생성에 강제할 Legal Guardrail을 만든다.
- Legal은 persistent TaskRun worker 경로를 사용한다.

### 4.3 Concept eligibility

- 확정 Origin과 Guardrail을 입력으로 Concept 초안을 생성한다.
- Origin Integrity가 확정 값과 핵심 구조의 보존 여부를 PASS/FAIL로 검사한다.
- Concept Legal Validation은 `GUARDRAIL_BATCH`로 후보 묶음을 검사한다.
- Batch 응답의 candidateKey는 입력 집합과 정확히 같아야 한다. 누락·중복·알 수 없는 key와 extra field를 거부한다.
- 실패 후보를 게시하지 않고 제한된 round/candidate budget 안에서 대체 생성한다.
- 두 검증을 통과한 3개만 ELIGIBLE Concept으로 게시한다.
- Concept eligibility orchestration은 in-memory Executor 안에서 TaskRun을 실행한다.

## 5. Internal AI v1 canonical contract

| 항목 | canonical 값 |
|---|---|
| contractVersion | `1.0` |
| taskSchemaVersion | `1.0` |
| locale | `ko-KR` |
| TextContent.contentType | `TEXT` |
| TextContent.language | `ko-KR` |

FastAPI는 누락/blank, `PLAIN_TEXT`, 다른 locale/language를 `INVALID_REQUEST` 계열로 거부한다. Java와 FastAPI는 다음 13개 TaskType을 동일하게 유지한다.

`IDEA_INTERPRETATION`, `LEGAL_REVIEW`, `IDEA_LEGAL_PRECHECK`, `CONCEPT_LEGAL_VALIDATION`, `CONCEPT_GENERATION`, `QUICK_ASSESSMENT`, `DETAILED_ANALYSIS`, `PERSONA_CARD_GENERATION`, `PERSONA_INTERVIEW`, `INTERVIEW_SYNTHESIS`, `MARKETING_GENERATION`, `MARKETING_COMPARISON`, `FINAL_REPORT_GENERATION`

Spring은 adopt 전에 공통으로 TaskRun ID, TaskAttempt ID, taskType, taskSchemaVersion, correlationId, canonicalInputHash, resultSchemaVersion과 result body를 검증한다. 각 Service의 domain invariant 검증은 별도로 유지한다.

## 6. Error and retry semantics

- deadline: `DEADLINE_EXCEEDED / REQUEST_DEADLINE_EXCEEDED / retryable=true`
- token missing: `UNAUTHORIZED_INTERNAL_CALL / SERVICE_TOKEN_MISSING / false`
- token invalid: `UNAUTHORIZED_INTERNAL_CALL / SERVICE_TOKEN_INVALID / false`
- provider/model/legal dependency와 result 오류는 Internal contract의 stable code/reason registry를 사용한다.

retryable은 새 Attempt를 정책상 허용할 수 있다는 의미이며 현재 Attempt를 재개한다는 의미가 아니다.

## 7. Runtime and retained MVP

Runtime은 React Frontend, Spring Backend, FastAPI AI Server, PostgreSQL, MinIO/Object Storage와 TaskRun/TaskAttempt/TaskResult로 구성된다. AI 실행 방식은 Legal persistent worker, Concept in-memory executor, 일부 Journey service 내부 동기 claim/execute가 공존한다. 이번 버전은 이를 하나로 통일하지 않는다.

후속 MVP 화면은 보존한다. 현재 Route에서 접근 가능하다는 사실과 공식 Journey 연결은 구분한다. 공식 연결은 별도 제품 결정과 Endpoint/Route 작업이 있기 전까지 선언하지 않는다.

## 8. 기준선 완료 상태와 남은 별도 기능

- 사용자가 Internal fixture validator와 관련 AI/Backend 테스트를 실행해 변경을 확인한다.
- Public API Controller/Frontend client As-Is matrix는 `PUBLIC_API_V2_CONTRACT.md`에 반영했다.
- 현재 Journey와 보존 MVP Route는 `ProjectLayout` 및 UIUX 문서에서 분리했다.
- env/compose 외부 입력 이름은 정리 작업 D에서 표준화했고 repository-local CI가 Frontend, AI, Backend의 최소 gate를 수행한다. 실제 Provider·법제처·전체 Docker E2E는 기본 CI 범위 밖이다.
- OpenAPI 전면 통합, 보존 MVP의 공식 Journey 연결, Public envelope 통일은 별도 기능 작업이다.

## 9. 이번 버전에서 하지 않은 것

Internal AI 계약 정렬 당시에는 Flyway Migration을 변경하지 않았다. 이후 승인된 Migration Baseline cutover에서 과거 V1~V36과 Java V5/V10의 최종 효과를 통합 V1 SQL로 흡수했으며 DB entity/repository와 업무 의미는 변경하지 않았다. Public API, Frontend API client, Route/화면, env/compose, prompt/model, `/api/v1`과 기존 MVP 기능은 변경하지 않았다.
