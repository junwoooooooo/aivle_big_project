# New Pipeline Platform

이 저장소는 프로젝트별 기획을 정리하고 외부 분석 모듈과 연결하는 신규 제품 파이프라인을 구현합니다.

1. Idea Brief 작성·확정
2. 적격 컨셉 5개 생성과 공식 근거 기반 법률 구현 가능성 사전검토
3. 컨셉 비교·선택
4. Market Handoff와 외부 시장분석 결과 반영
5. Finalized Planning 생성, BM·재무 및 Persona 외부 모듈 Handoff
6. Marketing Content 생성·수정·확정

컨셉 생성은 정확히 5개 Slot을 사용하며 후보별 최대 1회 Redesign, 최대 2회
Replacement, 전체 최대 15개 후보 검사 한도를 적용합니다. 사실이나 근거가 부족하면 성공을
가장하지 않고 입력 필요 또는 실패 상태로 종료합니다.

## Runtime

- `frontEnd`: React/Vite Project Shell, 모듈 상태, 작업 센터와 사용자 화면
- `backend`: Spring Boot, PostgreSQL/Flyway, TaskRun/JobEvent, 신규 `pipeline/**`
- `ai`: FastAPI 내부 execution API와 신규 `app/tasks/**`
- `compose.yaml`: PostgreSQL, MinIO, Backend, AI, Frontend 로컬 구성

브라우저는 Spring Backend만 호출합니다. Backend는 상태와 snapshot을 소유하고,
`InternalAiExecutionClient`를 통해 FastAPI의 `POST /internal/v1/ai/executions`를 호출합니다.
AI 서버가 직접 DB 상태를 변경하지 않습니다.

## Database

보존 데이터가 없는 rebuild 환경을 전제로 Flyway migration은
`backend/src/main/resources/db/migration/V1__new_pipeline_baseline.sql` 하나로 시작합니다.
기존 DB/volume에 대한 in-place upgrade는 지원하지 않으므로 적용 전에 DB를 초기화해야 합니다.

## Runtime contracts

- 사용자가 직접 호출하는 제품 API는 `/api/v3/projects/{projectId}/...` 아래에 있습니다.
- 작업 Event 조회는 `/api/v2/jobs/{jobId}/events`의 SSE와 `?after=<sequence>` JSON polling을 사용합니다.
- Backend Query API가 상태 정본이며 Job Event는 갱신 신호입니다.
- Provider 작업은 Idea Brief, Concept Candidate, Concept Legal Review, Concept Redesign,
  Marketing Content Generation의 다섯 계약으로 제한합니다.
- 실제 Provider 검증은 `python -m app.tools.idea_brief_provider_smoke`,
  `python -m app.tools.concept_factory_provider_smoke`,
  `python -m app.tools.marketing_content_provider_smoke`로 수행합니다.

## Documentation

- 구현 계약: `docs/rebuild/`
- API 요약: `docs/api/openapi.yaml`
- 최종 구조: `docs/rebuild/FINAL_REPOSITORY_STRUCTURE.md`
- Entity/Table 목록: `docs/rebuild/FINAL_ENTITY_TABLE_INVENTORY.md`
- DB baseline: `docs/rebuild/FINAL_DATABASE_BASELINE.md`
- 현재 실행 단위 검증: `docs/rebuild/verification/PRODUCT-CUTOVER-CLEANUP_USER_VERIFICATION.md`
