# Current Repository Baseline

- Status: CURRENT_AS_BUILT
- Baseline date: 2026-08-04
- Scope: 코드로 확인한 현재 실행 기준선
- Implementation Status: IMPLEMENTED

## Runtime and ownership

현재 저장소는 React/Vite Frontend, Spring Boot Backend, FastAPI AI Server, PostgreSQL, MinIO/S3-compatible Object Storage로 구성된다. Browser는 Spring API를 호출한다. Spring은 인증, Project 소유권, JPA/Flyway, Object Storage, TaskRun/TaskAttempt/TaskResult 및 AI 결과 채택을 소유한다. FastAPI는 `/internal/v1/ai/executions`에서 provider·법률 dependency 실행과 결과 생성을 담당한다.

## Current official Journey

`Idea 입력 → AI 해석 → Idea Origin Draft 및 보완 질문 → Idea Origin 확정 → Legal Precheck → Legal Guardrail → Concept 생성 → Origin Integrity → Concept Legal Validation → 적격 Concept 3개 표시`

현재 공식 범위는 적격 Concept 3개 표시에서 끝난다. Concept 분석·선택·Persona·Interview·Marketing·Report는 보존된 기존 MVP 실험 기능이다. 일부 Route와 API가 존재하더라도 현재 공식 Journey와 자동 연결된 단계는 아니다.

## Internal AI contract

- Contract version / task schema version: `1.0`
- Locale / text language: `ko-KR`
- Text content type: `TEXT`
- TaskType: Java와 FastAPI가 동일한 13개 값
- Spring adoption 전 공통 검증: TaskRun ID, TaskAttempt ID, taskType, taskSchemaVersion, correlationId, canonicalInputHash, resultSchemaVersion, result body
- Domain invariant: 각 Journey Service와 Worker의 기존 결과 검증을 추가로 유지

실행 방식은 혼합되어 있다.

| 영역 | 현재 실행 방식 |
|---|---|
| Legal Precheck | Persistent Worker TaskRun |
| Concept eligibility | In-memory Executor 안에서 TaskRun 실행 |
| 일부 Idea/Persona/Marketing/Report | Service 내부 동기 claim/execute |

이번 기준선은 이를 하나의 202/Polling 방식으로 통일하지 않는다.

## Persistence and migrations

- PostgreSQL과 JPA/Flyway 사용
- Object Storage는 MinIO/S3-compatible adapter 사용
- Runtime Flyway는 PostgreSQL 최종 스키마를 직접 만드는 `V1__baseline_schema.sql` 하나
- 과거 V1~V36과 Java Migration V5/V10의 최종 효과를 Baseline SQL에 흡수
- 기존 DB upgrade는 지원하지 않으며 적용 전 PostgreSQL/Docker volume 초기화 필수
- 과거 Migration 이력은 Git history에 보존
- H2는 일부 Service 로직 테스트에만 사용하고 Migration 계약은 PostgreSQL/Testcontainers로 검증

## API authority and CI

현재 Public API의 As-Is 실행 권위는 실제 Spring Controller와 Frontend Client이며 `docs/contracts/PUBLIC_API_V2_CONTRACT.md`가 endpoint/status/envelope matrix를 기록한다. Journey `ApiResponse`와 TaskRun 전용 envelope가 현재 공존한다. `docs/api/openapi.yaml`은 Backend semantic test가 읽는 기존 `/api/v1` 중심 machine-consumed 계약이며 현재 Journey `/api/v2` 전체 권위가 아니다. Public `/api/v2`와 Internal `/internal/v1/ai/executions`를 구분한다.

Repository-local `.github/workflows/ci.yml`은 Frontend lint/baseline/build, AI fixture/pytest, Backend test/postgresTest를 실행한다. 실제 AI Provider·법제처·전체 Docker E2E는 기본 CI 범위 밖이며 Frontend 허용 테스트 부채는 `test-debt-baseline.json` 정책을 따른다.

## Known retained implementation

Spring provider 직접 호출, 과거 `/api/v1`, 기존 MVP 화면과 관련 데이터 모델은 현재 참조가 남아 있어 보존한다. 제거 판단은 실제 참조가 없는 HIGH 근거 항목에만 별도 작업으로 적용한다.
