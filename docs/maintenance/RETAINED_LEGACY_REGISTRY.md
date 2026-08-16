# Retained Legacy Registry

- Status: CURRENT_AS_BUILT
- Purpose: 의도적으로 보존한 호환·실험·machine-consumed 항목과 제거 전제 관리
- Rule: 이 Registry 등재는 삭제 승인이 아니다. 아래 조건을 모두 증명한 별도 작업에서만 제거한다.

| 영역 | 경로 | 현재 소비자/근거 | 상태 | Owner | 제거 조건 | 금지사항 |
|---|---|---|---|---|---|---|
| Spring public compatibility API | `backend/src/main/java/**`의 `/api/v1` Controller | Frontend compatibility redirect, E2E scripts, legacy consumers 가능성 | LEGACY_REFERENCED | UNASSIGNED | 실제 route/script/test/외부 소비 0, v2 대체와 retirement 공지 완료 | 이번 기준선에서 path/status/envelope 또는 Controller 삭제 금지 |
| Legacy OpenAPI | `docs/api/openapi.yaml` | `Phase2SemanticContractTests`가 runtime read | MACHINE_CONSUMED | UNASSIGNED | semantic test와 외부 소비를 새 v2 OpenAPI로 전환 | 전면 재작성·삭제 금지; 현재 v2 단일 권위로 오인 금지 |
| Analysis domain | `backend/src/main/java/com/aivle/backend/analysis/**` | 기존 feasibility/financial Controller, Repository와 보존 화면 | LEGACY_REFERENCED | UNASSIGNED | 보존 MVP 대체, API/route/import/test 소비 0, 데이터 전환 완료 | Entity/Repository 의미나 기존 결과 삭제 금지 |
| AnalysisJob/provider adapters | `backend/src/main/java/com/aivle/backend/job/**`, 관련 direct provider adapter | `/api/v1` task, document/marketing worker와 E2E scripts | LEGACY_REFERENCED | UNASSIGNED | 모든 소비를 TaskRun으로 전환하고 recovery/artifact 회귀 통과 | TaskRun 존재만으로 제거 금지 |
| Preserved MVP Journey | `backend/.../journey`의 analysis/selection/persona/interview/marketing/report Service·Entity | 보존 MVP `/api/v2` API와 Frontend route | PRESERVED_MVP | UNASSIGNED | 제품의 공식 대체 흐름 확정, route/API/data/test 전환 완료 | 공식 Journey와 자동 연결된 단계로 문서화하거나 삭제 금지 |
| Old page tree | `frontEnd/src/page/**` | 현재 정적 import는 확인되지 않았으나 외부/동적 소비 미확정 | UNKNOWN_EXTERNAL_CONSUMER | UNASSIGNED | hidden 포함 전체 참조 0 재확인, build/test 성공, 외부 소비 없음 확인 | 현재 조사만으로 삭제 금지 |
| Preserved MVP routes | `frontEnd/src/app/router/AppRouter.jsx`의 concept analysis/selection/persona/interview/marketing/final-report | Router와 `features/journey` page가 직접 소비 | PRESERVED_MVP | UNASSIGNED | 공식 대체 route·UX 완료, compatibility redirect와 링크 소비 0 | Route/Page 삭제 금지 |
| Legacy demo env | `.env.demo.example` | `scripts/demo-start.ps1`이 `.env.demo`를 읽음 | LEGACY_DEMO | UNASSIGNED | 공식 대체 demo 합의, script/doc 소비 0 | 공식 Journey 실행 env로 사용하거나 Secret 저장 금지 |
| Legacy demo launcher | `scripts/demo-start.ps1` | Backend/Frontend direct local/H2 `/api/v1` demo | LEGACY_DEMO | UNASSIGNED | 소비 팀 확인, 대체 실행기와 종료 절차 제공 | FastAPI/PostgreSQL/MinIO 공식 E2E로 표현 금지 |
| Legacy AI task endpoint | `ai/app/api/tasks.py`, `ai/app/services/task_service.py` | Spring legacy task/E2E compatibility | LEGACY_REFERENCED | UNASSIGNED | Spring 호출과 E2E 소비 0, Internal executions 대체 검증 | `/internal/v1/ai/executions`와 같은 권위로 혼합 금지 |
| Legacy AI marketing endpoint | `ai/app/api/marketing.py`, marketing services/models | `/api/v1/marketing/banners/generate` readiness와 legacy marketing | LEGACY_REFERENCED | UNASSIGNED | Backend/Frontend/test 외부 소비 0, 대체 artifact flow 완료 | 보존 marketing 기능과 함께 임의 삭제 금지 |
| Guide source | `docs/guide/*.docx` | npm predev/prebuild/pretest copy/hash와 Frontend Docker build 입력 | MACHINE_CONSUMED | UNASSIGNED | copy script와 UI 다운로드 consumer를 대체한 뒤 build/test 통과 | 참고 문서로 보고 삭제 금지 |
| Example source | `docs/example/*.docx` | npm predev/prebuild/pretest copy/hash와 Frontend Docker build 입력 | MACHINE_CONSUMED | UNASSIGNED | copy script와 UI 다운로드 consumer를 대체한 뒤 build/test 통과 | 참고 문서로 보고 삭제 금지 |
| Design originals | `docs/reference/design/**` | 사람의 UI 디자인 참고 원본 | REFERENCE_ONLY | UNASSIGNED | 제품·디자인 Owner 승인과 대체 reference 확정 | 코드 미참조만으로 삭제 금지 |

## 후속 API 과제

- `/api/v2` OpenAPI 작성 또는 기존 OpenAPI와의 통합
- Journey `ApiResponse`와 TaskRun envelope 호환 전략
- `/api/v1`을 포함한 API version retirement 기준과 외부 소비 확인
