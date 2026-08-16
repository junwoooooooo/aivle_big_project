# Documentation Index

- Status: CURRENT_CANONICAL
- Baseline date: 2026-08-04
- Scope: 문서 권위, 현재 구현, 목표/참고 자료의 구분

## Authority

- `CURRENT_CANONICAL`: 현재 제품 결정의 권위
- `CURRENT_AS_BUILT`: 실제 코드로 확인한 실행 상태
- `MACHINE_CONSUMED`: build/test/script/runtime 입력
- `REFERENCE_ONLY`: 디자인·원본 참고 자료
- `HISTORICAL_EVIDENCE`: 과거 감사·검증 이력
- `LEGACY_REFERENCED`: 현재 코드나 소비자가 실제 참조

제품 방향은 canonical 문서가 설명하지만 실행 가능 여부는 실제 코드와 `CURRENT_AS_BUILT` 문서가 우선한다.

## Current canonical set

- 현재 구현 기준선: [CURRENT_BASELINE](CURRENT_BASELINE.md)
- 현재 Idea·Legal·Concept 설계: [AI Journey Redesign v0.4](redesign/AI_JOURNEY_REDESIGN_SPEC_v0.4.md)
- Internal AI 원칙: [INTERNAL_AI_API_PRINCIPLES](contracts/INTERNAL_AI_API_PRINCIPLES.md)
- Internal Spring–AI v1 계약: [INTERNAL_AI_API_V1_CONTRACT](contracts/INTERNAL_AI_API_V1_CONTRACT.md)
- Public API v2 As-Is: [PUBLIC_API_V2_CONTRACT](contracts/PUBLIC_API_V2_CONTRACT.md)
- 현재 Project/User Journey: [PROJECT_WORKFLOW](product/PROJECT_WORKFLOW.md), [USER_JOURNEY](product/USER_JOURNEY.md)
- 현재 Functional baseline: [FUNCTIONAL_REQUIREMENTS](product/FUNCTIONAL_REQUIREMENTS.md)
- 현재 Route/UX: [TARGET_ROUTE_MAP](uiux/TARGET_ROUTE_MAP.md), [WORKFLOW_UX](uiux/WORKFLOW_UX.md)
- Internal fixture/validator: [internal-ai-v1 fixtures](contracts/fixtures/internal-ai-v1/README.md)
- 상태·오류: [STATUS_AND_ERROR_CONTRACT](contracts/STATUS_AND_ERROR_CONTRACT.md)
- 시스템 경계: [SYSTEM_ARCHITECTURE](architecture/SYSTEM_ARCHITECTURE.md), [AI_SERVER_BOUNDARY](architecture/AI_SERVER_BOUNDARY.md), [SPRING_WAS_BOUNDARY](architecture/SPRING_WAS_BOUNDARY.md)
- 감사 기준: [REPOSITORY_BASELINE_AUDIT](maintenance/REPOSITORY_BASELINE_AUDIT_2026-08-04.md)
- Migration Baseline 전환: [MIGRATION_BASELINE_CUTOVER](maintenance/MIGRATION_BASELINE_CUTOVER_2026-08-04.md)
- 저장소 구조 안내: [REPOSITORY_STRUCTURE_GUIDE](REPOSITORY_STRUCTURE_GUIDE.md)
- 보존 Legacy Registry: [RETAINED_LEGACY_REGISTRY](maintenance/RETAINED_LEGACY_REGISTRY.md)

이전 redesign draft와 완료된 실행계획은 필요한 결정·결과를 v0.4와 현재 기준선에 반영한 뒤 제거했으며 Git history로 보존한다. v0.4가 현재 redesign의 유일한 문서 권위다.

## Target and reference documents

- Public API As-Is: `contracts/PUBLIC_API_V2_CONTRACT.md`
- Migration plans: `migration/` (2026-08-04 Baseline cutover 결과가 이전 계획보다 우선)
- Quality/governance history: `quality/`, `governance/`
- Design originals: `reference/design/` (`REFERENCE_ONLY`)

현재 Public API 실행 권위는 Controller와 Frontend Client이며 `PUBLIC_API_V2_CONTRACT.md`가 이를 문서화한다. `docs/api/openapi.yaml`은 Backend test가 읽는 기존 `/api/v1` 중심 machine-consumed 계약으로 유지하며 `/api/v2` 전체 권위로 사용하지 않는다.

`docs/api/openapi.yaml`, `docs/guide/`, `docs/example/`, fixture의 소비 여부와 보존 근거는 감사 보고서의 machine-consumed 조사 결과를 따른다. 파일 이름이나 작성일만으로 제거하지 않는다.

Repository-local GitHub Actions `CI`는 Frontend lint/baseline/build, AI fixture/pytest, Backend test/postgresTest를 수행한다. 실제 Provider·법제처·전체 Docker E2E는 기본 CI 범위 밖이다.
