# New Pipeline Platform

이 저장소는 프로젝트 단위의 신규 6단계 파이프라인을 구현합니다.

1. Idea Brief
2. Concept Factory와 법률 근거 검토
3. Concept Selection
4. 외부 시장분석 Module Handoff
5. Planning 반영·확정
6. Marketing Content 생성·수정·확정

## Runtime

- `frontEnd`: React/Vite 신규 Project Shell과 feature module
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

## Documentation

- 구현 계약: `docs/rebuild/`
- API 요약: `docs/api/openapi.yaml`
- 최종 구조: `docs/rebuild/FINAL_REPOSITORY_STRUCTURE.md`
- Entity/Table 목록: `docs/rebuild/FINAL_ENTITY_TABLE_INVENTORY.md`
- DB baseline: `docs/rebuild/FINAL_DATABASE_BASELINE.md`
- R7A 검증: `docs/rebuild/verification/R7A_USER_VERIFICATION.md`

R7A에서는 전체 테스트, Docker rebuild, provider smoke, frontend production build를 실행하지 않습니다.
정확한 사용자 검증 명령은 R7A 검증 문서를 따릅니다.
