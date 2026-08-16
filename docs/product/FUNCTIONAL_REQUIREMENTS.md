# Functional Requirements Baseline

- Status: CURRENT_CANONICAL
- Baseline date: 2026-08-04
- Scope: 현재 구현, 보존 MVP, 목표/미구현의 구분

## 현재 공식 Journey

| ID | Requirement | 상태 |
|---|---|---|
| FR-C01 | 인증 사용자는 owner-scoped Project에서 TEXT/FILE Idea를 저장·조회한다. | 구현됨 |
| FR-C02 | AI Interpretation은 구조화된 Origin Draft, metadata와 clarification question을 생성한다. | 구현됨 |
| FR-C03 | 사용자는 질문별 답변과 출처를 저장하고 새 Origin version으로 반영한다. | 구현됨 |
| FR-C04 | USER_CONFIRMED 값과 AI 제안/가정을 구분하고 하위 단계가 확정값을 덮어쓰지 않는다. | 구현됨 |
| FR-C05 | Legal Precheck은 법률 source/evidence, 추가 질문, revision 제안과 결과 상태를 저장한다. | 구현됨 |
| FR-C06 | Precheck 결과에서 Concept용 Legal Guardrail을 생성한다. | 구현됨 |
| FR-C07 | Concept draft는 Origin Integrity와 Concept Legal Validation을 모두 통과해야 한다. | 구현됨 |
| FR-C08 | 실패 draft는 게시하지 않고 제한된 budget에서 대체 생성한다. | 구현됨 |
| FR-C09 | 기준을 낮추지 않고 적격 Concept 3개를 표시하며 여기서 공식 Journey를 종료한다. | 구현됨 |
| FR-C10 | TaskRun/Attempt/Result identity, hash와 결과를 Spring이 검증·채택한다. | 구현됨 |

## 보존된 기존 MVP 실험 기능

| ID | Requirement | 상태 |
|---|---|---|
| FR-M01 | Quick Assessment와 Shortlist | 구현·보존, 공식 Journey 미연결 |
| FR-M02 | Detailed/Financial Analysis와 Concept Selection | 구현·보존, 공식 Journey 미연결 |
| FR-M03 | synthetic Persona Card와 독립 Interview/Synthesis | 구현·보존, 공식 Journey 미연결 |
| FR-M04 | Marketing asset 생성·선택·질적 comparison | 구현·보존, 공식 Journey 미연결 |
| FR-M05 | Final Report 생성과 사용자 decision | 구현·보존, 공식 Journey 미연결 |

## 목표 또는 미구현

- 과거 Target 계약의 workflow summary, cursor history, generic capability matrix, user-edited version endpoint, persisted HTML/PDF export endpoint는 현재 `/api/v2` Controller에 없다.
- Public API 단일 envelope 전환과 모든 AI command의 일괄 202/polling 전환은 현재 요구사항이 아니다.
- 보존 MVP를 공식 Journey에 연결하려면 별도 제품 결정, API/Route/UX 검증이 필요하다.
- `/api/v1`과 기존 OpenAPI를 제거하거나 대규모 재작성하지 않는다.
