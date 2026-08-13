# MAIN-FULL-RESYNC-V4-RECOVERY-CONTINUE 사용자 검증

## PostgreSQL/Flyway

Docker 또는 완전한 PostgreSQL 17 server가 있는 환경에서 clean database를 준비한 뒤 다음을 확인한다.

1. Flyway V1→V23 전체 적용
2. Spring `ddl-auto=validate` 기동
3. `research_competitor_seeds` table, project/user FK, `version`, `display_order` CHECK, partial unique index
4. `backend/gradlew.bat postgresTest`

## Runtime smoke

1. backend, AI, frontend, PostgreSQL, MinIO를 기동한다.
2. 한 프로젝트의 current CPV2 selection으로 Market FULL을 새로 실행한다.
3. 생성 원장이 `runs-generated`에서 정상 materialize되는지 확인한다.
4. Market FULL에서 BM을 생성하고 exact concept lineage를 확인한다.
5. confirmed TechOps input을 포함해 advisory를 실행하고 Layer 1 fact source/status와 advice basisId를 확인한다.
6. 작업 SSE가 완료되고 404/JOB_NOT_FOUND에서 reconnect가 중단되는지 확인한다.

## 남은 Product recollect

현재 TaskRun이 끝나면 임시 workspace의 raw ledger가 사라지므로 다음 실행 recollect는 제품 경로에서 지원되지 않는다. 이 항목이 Artifact/TaskRun에 안전하게 저장·복원되기 전에는 recollect parity를 완료로 판정하지 않는다.

