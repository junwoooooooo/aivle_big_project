# V2-9 — Active Surface Cleanup and Acceptance 결과

## 결과 요약

V2-9 구현을 완료했다. 활성 제품 경로에서 구형 Journey, 구조화 기획, planning proposal, `FinalizedPlanningSnapshot`, BM·재무·Persona 통합 화면을 제거했다. 5단계는 독립 `BUSINESS_MODEL` 모듈과 `/business-model` 경로로 전환했으며, 현재 `MarketAnalysisSeedSnapshot`만 불변 입력으로 사용한다.

Persona 계약은 향후 승인된 어댑터를 위해 `PERSONA_RESPONSE` enum으로 보존했다. 다만 현재 제품 핵심 내비게이션에는 노출하지 않고, 승인된 입력 어댑터가 없는 상태에서 handoff 요청은 명시적으로 거절한다. 외부 BM 알고리즘은 구현하지 않았다.

## 구현된 계약

- 초기 Idea Brief 필수 항목은 `ideaOverview`, `problem`, `targetUsers` 세 항목만 유지한다.
- 구형 planning 제안·결정·확정 workflow의 서비스, 엔티티, 저장소, 테스트, 프런트 컴포넌트, 기준선 테이블을 제거했다.
- `SelectedConceptSnapshot` 중간 계약을 제거하고 Market Seed를 현재 외부 분석 입력 기준으로 유지했다.
- 외부 모듈 enum을 `MARKET_ANALYSIS`, `BUSINESS_MODEL`, `TECH_OPS`, `FINANCIAL_ANALYSIS`, `PERSONA_RESPONSE`로 정리했다.
- BM handoff는 `market-analysis-seed-snapshot-v1`, `MARKET_ANALYSIS_SEED`, schema `2.0`을 사용한다.
- BM 상태는 Market Seed가 없으면 `NOT_READY`, Seed가 있고 외부 실행 연결이 없으면 `NOT_CONNECTED`, 입력이 바뀌면 `STALE`이다.
- 프로젝트 모듈 상태 응답은 8개 제품 단계만 반환한다.
- Job Center는 현재 `TaskType`과 정규 제품 경로만 사용하며, 해결된 `NEEDS_INPUT` 작업을 최근 작업으로 분리한다.
- 활성 라우터에서 구형 로그인·프로젝트·계획·검토·보고서 별칭을 제거했다.
- 공개 랜딩의 Journey/5단계 표기를 현재 파이프라인/8단계 표기로 교체했다.

## 변경 파일

### 백엔드

- 수정:
  - `backend/src/main/java/com/aivle/backend/pipeline/integration/application/ModuleIntegrationService.java`
  - `backend/src/main/java/com/aivle/backend/pipeline/integration/domain/ModuleType.java`
  - `backend/src/main/java/com/aivle/backend/pipeline/module/PipelineModuleType.java`
  - `backend/src/main/java/com/aivle/backend/pipeline/module/ProjectModuleStatusService.java`
  - `backend/src/main/resources/db/migration/V1__new_pipeline_baseline.sql`
- 제거:
  - `backend/src/main/java/com/aivle/backend/pipeline/planning/`의 전체 제품 코드
  - planning proposal 도메인·저장소
  - `SelectedConceptSnapshot` 도메인·저장소
  - 위 계약에 종속된 테스트
- 테스트 수정·추가:
  - `backend/src/test/java/com/aivle/backend/pipeline/module/ActiveSurfaceCleanupTests.java`
  - `backend/src/test/java/com/aivle/backend/pipeline/module/ProjectModuleStatusServiceTests.java`
  - `backend/src/test/java/com/aivle/backend/pipeline/module/NewPipelineFoundationMigrationTests.java`
  - `backend/src/test/java/com/aivle/backend/pipeline/techops/TechOpsHandoffTests.java`
  - `backend/src/test/java/com/aivle/backend/pipeline/finance/FinancialHandoffTests.java`
  - `backend/src/test/java/com/aivle/backend/postgres/PostgreSqlBaselineMigrationTests.java`

### 프런트엔드

- 신규 `frontEnd/src/features/business-model/` 기능 모듈
- 수정:
  - `frontEnd/src/app/routing/AppRouter.jsx`
  - `frontEnd/src/app/routing/projectRoutes.js`
  - `frontEnd/src/app/module-status/projectModuleModel.js`
  - 관련 라우팅·모듈 모델 테스트
  - 랜딩 페이지의 현재 파이프라인 문구와 8단계 workflow
  - market integration의 planning change 전용 스타일
  - 구형 layout 전용 스타일
- 제거:
  - `frontEnd/src/app/layouts/ProjectLayout.jsx`
  - `frontEnd/src/features/business-persona-integration/`의 전체 제품 코드
  - `frontEnd/src/features/planning-revision/`의 전체 제품 코드
  - market integration의 `PlanningChangeCard`와 구형 결과 모델

## 실제 실행한 검사

- Backend `compileJava`, `compileTestJava`: 성공
- Backend 직접 관련 7개 테스트 클래스: 성공
  - `ActiveSurfaceCleanupTests`
  - `ProjectModuleStatusServiceTests`
  - `ProjectJobQueryServiceTests`
  - `IdeaBriefFieldCatalogTests`
  - `MarketingSourceV2ContractTests`
  - `TechOpsHandoffTests`
  - `FinancialHandoffTests`
- Frontend 직접 관련 Vitest: 4개 파일, 31개 테스트 성공
- Frontend ESLint: 성공
- 활성 backend/frontend 소스의 구형 planning·FinalizedPlanning·구형 BM/Persona enum 및 경로 문자열 검색: 제품 코드 잔존 없음
- `git diff --check`: 성공

## 의도적으로 생략한 검사

`LOCAL_FAST_EXECUTION_PROFILE.md`에 따라 다음은 실행하지 않았다.

- 전체 백엔드 회귀 테스트
- 전체 `postgresTest` 및 Testcontainers
- Docker·브라우저 자동 smoke
- 외부 provider smoke
- 프런트엔드 production build

## 남은 위험

- 기존에 이미 V1 기준선을 적용한 로컬 DB에는 삭제된 테이블과 enum check 변경이 자동 반영되지 않는다. rebuild DB 볼륨을 재생성해야 한다.
- 외부 BM 알고리즘과 Persona 입력 어댑터는 연결되지 않았다. BM run의 정상 기본 상태는 `NOT_CONNECTED`이다.
- 실제 브라우저 레이아웃, 새로고침 복원, 네트워크 응답은 사용자가 아래 수용 절차로 확인해야 한다.
- 구형 링크는 호환 redirect 없이 Not Found 또는 프로젝트 정규 fallback으로 처리된다. 외부 북마크가 있다면 새 정규 URL로 갱신해야 한다.

## 정확한 계속 지점

V2-9 코드 작업은 완료됐다. 다음 단계로 자동 진행하지 않는다. 사용자는 `V2-9-ACTIVE-SURFACE-CLEANUP-AND-ACCEPTANCE_USER_VERIFICATION.md`의 브라우저 수용 검증을 수행하고, 실패 항목이 있으면 해당 항목 번호와 화면·응답을 전달한다. 이후 작업은 별도 지시에서 시작한다.
