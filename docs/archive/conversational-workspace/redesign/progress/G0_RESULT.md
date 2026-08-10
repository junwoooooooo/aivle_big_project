# G0 결과 — Baseline Audit & Design Freeze

- 기준 SHA: `967c19a8eca17ff24f159175fb3e7ecc9fb6cf9d`
- 브랜치: `main`
- 범위: 문서 감사와 설계 동결만 수행
- 기능 코드·Migration·환경 설정 변경: 없음
- commit/push: 수행하지 않음

## 생성·정리 문서

- `docs/archive/conversational-workspace/redesign/CONVERSATIONAL_VALIDATION_WORKSPACE_SPEC_v1.0.md`
- `docs/archive/conversational-workspace/redesign/CONVERSATIONAL_VALIDATION_WORKSPACE_IMPLEMENTATION_PLAN_v1.0.md`
- `docs/archive/conversational-workspace/redesign/current-to-target/CONVERSATIONAL_VALIDATION_WORKSPACE_CURRENT_TO_TARGET_MAP.md`
- `docs/archive/conversational-workspace/redesign/CONVERSATIONAL_VALIDATION_WORKSPACE_CURRENT_TO_TARGET_MAP.md`
- `docs/archive/conversational-workspace/redesign/decisions/DECISION_LOG.md`
- `docs/archive/conversational-workspace/redesign/DECISION_LOG.md`
- `docs/archive/conversational-workspace/redesign/progress/G0_RESULT.md`

SPEC와 IMPLEMENTATION_PLAN은 G0 시작 시 작업 트리에 이미 존재한 기준
문서이며 내용을 변경하지 않았다. 이번 작업은 현행→목표 매핑, ADR 정본,
호환 경로, 이 결과 문서를 추가했다.

## 재사용 가능 영역

- `task_runs`, `task_attempts`, `task_results`의 idempotency, claim/lease,
  retry/cancel, result adoption/rejection, project ownership
- Idea Source의 text/file 저장, 문서 parsing, Idea Origin version/question/
  confirmed value 이력
- Legal Precheck의 공식 source pipeline, Evidence, registry version, hash 기반
  stale 판정
- Concept Eligibility의 background batch, 실패 Draft 내부 보존, 적격 3개
  수집, Origin integrity 검사
- AI Pydantic strict result(`extra=forbid`), Concept fan-out semaphore와 slot별
  제한된 repair, 민감 원문 비노출 로그 테스트
- 기존 route 호환 redirect와 Quick/Detailed/Selection/Persona/Marketing/
  Final Report Journey

## 가장 큰 구조적 충돌 5개

1. 사용자 내비게이션이 목표 5단계가 아니라 현재 3단계와 미연결 레거시
   6단계로 분리되어 있다.
2. Idea는 단발 입력→해석→Origin이며 Conversation/Message/Attachment와
   Opportunity Brief field provenance 계약이 없다.
3. Legal Precheck는 PASS 계열 gate와 배열형 Guardrail을 사용하며 목표
   Boundary Rule/상태 및 선택 후 설명·이행 중심 법률 보고서와 다르다.
4. TaskRun은 영속 실행 이력은 있으나 Job Event, SSE, Last-Event-ID/cursor
   replay가 없고 frontend는 2초 polling을 사용한다.
5. Concept fan-out은 provider 내부 aggregate이며 DB의 legal 상태는
   `PASS/FAIL_LEGAL`이다. 독립 Slot 상태, 구현 골격, 법률 구현 가능성 5상태,
   per-slot event가 없다.

## 계약과 Migration

- 고정 계약: SPEC I-01~I-12, 사용자 5단계, 내부 8단계, G1~G11 순서
- 현재 migration: `V1__baseline_schema.sql` 하나
- G0 migration: 없음
- 다음 additive migration 후보:
  `backend/src/main/resources/db/migration/V2__conversational_validation_domain.sql`
- 예상 G1 테이블: `idea_conversations`, `idea_messages`,
  `idea_attachments`, `opportunity_brief_versions`,
  `opportunity_field_values`, `regulatory_boundary_runs`,
  `regulatory_boundary_versions`, `boundary_rules`, `boundary_evidence`,
  `boundary_questions`, `job_events`

## 다음 G1의 정확한 수정 후보

- Migration: 위 V2 파일
- 신규 backend 패키지:
  - `backend/src/main/java/com/aivle/backend/journey/conversation/**`
  - `backend/src/main/java/com/aivle/backend/journey/brief/**`
  - `backend/src/main/java/com/aivle/backend/journey/boundary/**`
  - `backend/src/main/java/com/aivle/backend/jobevent/**`
- 재사용 검토:
  `backend/src/main/java/com/aivle/backend/taskrun/service/CanonicalInputHasher.java`
- migration 검증 갱신:
  `backend/src/test/java/com/aivle/backend/postgres/PostgreSqlBaselineMigrationTests.java`
- 신규 repository/state/version/hash/stale/project-isolation 테스트 패키지

G1에서는 AI prompt와 frontend를 변경하지 않고, read/create도 테스트에
필요한 최소 repository/service 범위만 허용한다.

## 검증 결과

- 문서 경로와 상대 링크: 모두 존재함을 확인
- `git diff --check`: 통과(출력 없음)
- 신규 문서 `git diff --no-index --check`: 통과(출력 없음)
- 기능 테스트: 기능 코드 변경이 없어 실행하지 않음

## 미해결 위험

- 루트 경로와 `current-to-target/`, `decisions/` 경로가 작업지시 안에서
  병기되어 정본+호환 문서로 보존했다. 이후 수정은 하위 정본에서만 한다.
- `PostgreSqlBaselineMigrationTests`가 migration 실행 수를 1로 고정하므로
  G1 additive migration 시 테스트 갱신이 필요하다.
- 현행 Concept Analysis의 `DEFAULT_FINANCE`와 단일 최종 선택은 목표 결측/
  1~2개 선택 계약과 다르며 G7/G10 전에 신규 계약으로 오인하면 안 된다.
- Feature Flag는 설계 명칭만 확정했으며 코드·env에는 아직 존재하지 않는다.
