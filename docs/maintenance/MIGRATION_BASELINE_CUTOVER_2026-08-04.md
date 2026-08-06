# Migration Baseline Cutover

- Status: CURRENT_AS_BUILT
- Cutover date: 2026-08-04
- Scope: PostgreSQL Flyway V1~V36 → 통합 Baseline V1

## 결정과 목적

현재 개발·시연 데이터는 승계하지 않으며 빈 PostgreSQL에서 다시 시작할 수 있다는 사용자 결정에 따라, 과거 V1~V36 순차 upgrade chain을 현재 최종 스키마를 직접 생성하는 Baseline으로 통합했다. Migration 변경 이력은 별도 archive가 아니라 Git history로 보존한다.

## 지원 범위

- 새 설치: `V1__baseline_schema.sql` 하나를 빈 PostgreSQL에 적용
- 지원하지 않음: 기존 V1~V36 중간 version 또는 기존 운영/개발 DB의 in-place upgrade
- 금지: 기존 DB 재사용, `baselineOnMigrate=true`, Flyway validation 우회
- 이후 변경: Baseline V1을 다시 수정하지 않고 새 V2 이상으로 추가

## 제거하고 흡수한 범위

- 제거: 과거 SQL Migration V1~V36
- 제거: Java Migration `V5__harden_document_integrity.java`, `V10__add_username_and_optional_profile.java`
- 흡수: core/auth/admin/audit/document/storage/analysis/job, preserved MVP, TaskRun, Idea/Origin, Legal Precheck/Guardrail, Concept Eligibility, Persona/Marketing/Report의 최종 DDL
- 제외: 기존 row backfill, 중간 호환 구조, retryability normalization DML, 개발/시연 사용자·Project·업무 데이터

## 새 Runtime Migration

- `backend/src/main/resources/db/migration/V1__baseline_schema.sql`
- Reference/Seed 전용 V2는 만들지 않았다. `service_settings`는 코드의 안전한 기본값을 사용하며 persona catalog는 기존 startup importer가 담당한다.
- PostgreSQL 전용 partial index와 정규식 check를 유지한다. H2 호환을 위해 PostgreSQL 계약을 약화하지 않는다.

## DB 및 Docker Volume 초기화

주의: 다음 명령은 Docker Volume과 DB 데이터를 삭제한다. 보존할 데이터가 없는지 사용자가 직접 확인한 뒤 실행한다.

기본 Compose:

```powershell
docker compose down -v
docker compose up --build
```

Infrastructure Compose를 별도로 사용하는 경우:

```powershell
docker compose -f compose.infrastructure.yaml down -v
docker compose -f compose.infrastructure.yaml up --build
```

기존 Database나 Volume을 재사용하면 안 된다.

## 최소 검증 명령

PostgreSQL Baseline/Flyway/constraint/repository 검증:

```powershell
Push-Location backend
.\gradlew.bat postgresTest
Pop-Location
```

Backend에서 Migration cutover의 영향을 받는 최소 Service 회귀 검증:

```powershell
Push-Location backend
.\gradlew.bat test --tests "com.aivle.backend.taskrun.InternalAiExecutionClientTests" --tests "com.aivle.backend.taskrun.TaskRunWorkerIntegrationTests"
Pop-Location
```

성공 기준은 빈 PostgreSQL에 V1이 한 번 적용되고 Flyway validation, Spring JPA schema validation, 주요 FK/Unique/Check/partial index와 Repository 검증이 통과하는 것이다. H2 테스트 결과는 Migration 정확성의 증거가 아니다.

H2 기반 `test` profile은 PostgreSQL 전용 V1을 실행하지 않는다. `spring.flyway.enabled=false`와 Hibernate `create-drop`으로 Service 로직을 격리하고, PostgreSQL `postgresTest`가 Flyway V1 및 `ddl-auto=validate`를 전담한다. 과거 Flyway version/history 또는 V22 upgrade row를 직접 검증하던 H2 테스트는 Baseline cutover 이후 지원 범위가 아니므로 현재 서비스 스키마/Repository 검증으로 교체하거나 제거했다.

## Rollback

이 cutover는 데이터 변환 rollback을 제공하지 않는다. 문제가 있으면 코드와 Migration 변경을 Git revert로 되돌리고 DB/Volume을 다시 삭제한 뒤 해당 revision의 migration chain으로 새 DB를 생성한다. 기존 DB를 Baseline에 억지로 연결하지 않는다.
