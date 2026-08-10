# Contract Overview

- Status: CURRENT_CANONICAL
- Baseline date: 2026-08-04
- Scope: 현재 Public, Internal, status/error, provenance 계약의 권위

계약은 다음 경계로 분리한다.

| 경계 | 현재 권위 | 상태 |
|---|---|---|
| Browser → Spring Public `/api/v2` | 실제 Controller, Frontend client, [Public API v2 As-Is](PUBLIC_API_V2_CONTRACT.md) | 현재 Journey + 보존 MVP 구현 |
| Spring → FastAPI Internal `/internal/v1/ai/executions` | [Internal AI principles](INTERNAL_AI_API_PRINCIPLES.md), [Internal AI v1](INTERNAL_AI_API_V1_CONTRACT.md), fixtures | 13개 TaskType 구현 |
| Public/Internal status와 error | [Status and Error Contract](STATUS_AND_ERROR_CONTRACT.md)와 실제 handlers | Public 두 envelope 공존 |
| provenance | [Provenance Contract](PROVENANCE_CONTRACT.md) | 제품 의미의 참고 권위, 실제 field는 코드 우선 |
| 기존 `/api/v1` OpenAPI | `docs/api/openapi.yaml` | machine-consumed legacy 중심 계약 |

Journey 한정으로 관점별 상세 문서가 셋 있다. 모두 AS_BUILT이며 코드가 우선한다.

| 목적 | 문서 |
|---|---|
| 프론트↔백 요청·응답 (통신 규약) | [Journey API 명세서](JOURNEY_API_SPEC.md) |
| 단계별로 오가는 값의 형태 (스키마) | [Journey 데이터 계약](JOURNEY_DATA_CONTRACT.md) |
| 단계 이동 조건 (흐름) | [Journey 상태 전이 명세](JOURNEY_STATE_MACHINE_SPEC.md) |

현재 공식 Journey는 Idea → Legal → 적격 Concept 3개 표시에서 종료한다. Quick/Detailed/Selection/Persona/Interview/Marketing/Final Report는 코드와 UI를 보존한 기존 MVP 실험 기능이다.

Public `ApiResponse`와 TaskRun 전용 envelope는 현재 다르다. Internal AI envelope와 Public envelope도 서로 대체할 수 없다. 과거 Target endpoint/status/schema는 실제 Controller에 없는 경우 현재 구현으로 기록하지 않는다.
