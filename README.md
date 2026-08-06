# Venture Verify

- Status: CURRENT_AS_BUILT
- Baseline date: 2026-08-04
- Scope: 저장소 진입점과 현재 구현 기준선

Venture Verify는 아이디어를 구조화하고 한국 법률 사전검토와 두 단계 무결성 검증을 거쳐 적격 Concept을 만드는 프로젝트 단위 서비스다.

현재 공식 Journey는 다음 단계에서 종료한다.

`Idea 입력 → AI 해석 → Idea Origin 보완·확정 → Legal Precheck → Legal Guardrail → Concept 생성 → Origin Integrity → Concept Legal Validation → 적격 Concept 3개 표시`

Concept 분석·선택·Persona·Interview·Marketing·Report 코드는 삭제하지 않고 보존한다. 이들은 현재 Router에서 일부 접근 가능한 기존 MVP 실험 기능이며, 위 공식 Journey와 자동 연결된 단계로 간주하지 않는다.

## Runtime

- React/Vite Frontend
- Spring Boot Backend
- FastAPI AI Server
- PostgreSQL
- MinIO/S3-compatible Object Storage
- `TaskRun` / `TaskAttempt` / `TaskResult`

Browser는 Spring의 현재 Controller를 호출하고 Spring은 업무 상태·DB·Object Storage·TaskRun과 결과 채택을 소유한다. FastAPI의 내부 실행 경계는 `/internal/v1/ai/executions`이며 canonical request는 `contentType=TEXT`, `locale=ko-KR`, `language=ko-KR`, `taskSchemaVersion=1.0`을 사용한다.

AI 실행 방식은 현재 하나로 통일되어 있지 않다.

- Legal: Persistent Worker TaskRun
- Concept: In-memory Executor 안에서 TaskRun 실행
- 일부 Journey: Service 내부 동기 claim/execute

## Database and CI

Flyway Runtime Migration은 PostgreSQL 최종 스키마를 직접 생성하는 `V1__baseline_schema.sql` 하나다. 과거 V1~V36과 Java Migration V5/V10의 최종 효과는 이 SQL에 흡수되었으며 기존 DB upgrade는 지원하지 않는다. 적용 전 기존 PostgreSQL과 Docker volume을 반드시 초기화해야 한다. 과거 이력은 Git history에 남는다.

Repository-local GitHub Actions `CI` workflow가 Frontend lint/baseline/build, AI fixture/pytest, Backend test/postgresTest를 실행한다. 실제 Provider·법제처 호출과 전체 Docker E2E는 기본 CI 범위 밖이다. Frontend의 허용 테스트 부채는 `test-debt-baseline.json` 정책을 따른다.

## Documentation

- 문서 권위와 탐색: [docs/README.md](docs/README.md)
- 실제 구현 기준선: [docs/CURRENT_BASELINE.md](docs/CURRENT_BASELINE.md)
- 현재 Journey 설계: [AI_JOURNEY_REDESIGN_SPEC_v0.4](docs/redesign/AI_JOURNEY_REDESIGN_SPEC_v0.4.md)
- Internal AI 계약: [INTERNAL_AI_API_V1_CONTRACT](docs/contracts/INTERNAL_AI_API_V1_CONTRACT.md)
- Public API v2 As-Is: [PUBLIC_API_V2_CONTRACT](docs/contracts/PUBLIC_API_V2_CONTRACT.md)
- 저장소 감사: [REPOSITORY_BASELINE_AUDIT](docs/maintenance/REPOSITORY_BASELINE_AUDIT_2026-08-04.md)
- Migration Baseline cutover: [MIGRATION_BASELINE_CUTOVER](docs/maintenance/MIGRATION_BASELINE_CUTOVER_2026-08-04.md)
- 저장소 구조 안내: [REPOSITORY_STRUCTURE_GUIDE](docs/REPOSITORY_STRUCTURE_GUIDE.md)
- 보존 Legacy Registry: [RETAINED_LEGACY_REGISTRY](docs/maintenance/RETAINED_LEGACY_REGISTRY.md)

현재 Public API의 실행 권위는 실제 Spring Controller와 Frontend Client이며 `PUBLIC_API_V2_CONTRACT.md`가 현재 endpoint/status/envelope를 기록한다. `docs/api/openapi.yaml`은 기존 `/api/v1` 중심 machine-consumed 계약이며 현재 Journey `/api/v2` 전체 권위가 아니다. Public `/api/v2`와 Internal `/internal/v1/ai/executions`는 별도 경계다.

환경변수는 저장소의 example 파일을 바탕으로 별도 주입하고 실제 비밀값을 문서나 커밋에 기록하지 않는다.
