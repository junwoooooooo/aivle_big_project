# AIDEV 재동기화 결과

## 결과 요약

`full`의 운영·통합 구조를 기준으로 유지하면서 `aidev`의 Finance와 Marketing Content 제품 의도를 선택 이식했다. 브랜치 병합, 전체 cherry-pick, donor 디렉터리 복사는 수행하지 않았다.

- Finance 시작 권한을 `current Market FULL + current Business Model + 두 결과의 정확한 lineage 일치`로 변경했다.
- TechOps는 독립 제품 분기로 보존하고 Finance 필수 선행 조건에서만 제거했다.
- Finance 기본값은 컨셉 확정 가정 → 시장 분석 가정 → BM 가정 순으로 적용하며 사용자 입력·채택값·확정 Snapshot을 덮어쓰지 않는다.
- CPV2 Marketing Source를 우선 사용하고, 현재 CPV2가 존재할 때 누락·stale·타 프로젝트 seed를 legacy로 우회하지 않는다.
- 선택적 참조 이미지는 기존 Project Evidence Artifact 업로드 경로를 사용한다.
- Marketing Content 작업은 카피 법무 검사 후 이미지 생성 또는 편집을 수행하고, 생성 JPEG를 Backend 내부 API를 통해 ObjectStorage에 저장한다.
- 활성 Marketing 화면은 이미지와 카피를 하나의 Canvas에 표시한다. 기존 standalone Marketing Visual 런타임과 저장 데이터는 호환·복구 경로로 보존했다.

## 시작 게이트

- 브랜치: `full`
- 시작 HEAD: `b1a38368fada08d649d52a28fc0c3d020d43874f`
- donor `origin/aidev`: `d1b690acf7e62bbf4a8e07a810857be49d826313`
- 시작 시 `git status --short`: clean
- 기존 migration 최종 번호: `V19`
- 새 migration 번호: `V20`, `V21`
- `.env`, API key, Twin Bank는 읽지 않았다.
- 실제 provider, Docker, 브라우저 LIVE E2E는 실행하지 않았다.

## Finance 계약

### 권한과 lineage

- `FinancialService`는 TechOps Snapshot을 조회하거나 필수로 요구하지 않는다.
- Market FULL과 BM의 current 응답이 존재하고 stale이 아니어야 한다.
- 두 version은 같은 프로젝트에 속해야 하며, BM source Market version ID가 현재 Market FULL version ID와 일치해야 한다.
- preparation과 snapshot의 current/stale 판정, 중복 초기화 재사용, source hash가 같은 Market/BM authority를 사용한다.
- 기존 TechOps-linked row는 Market/BM version 조합으로 재조회하여 삭제 없이 재사용할 수 있다.
- 컨셉 가정은 현재 Market version의 정확한 source seed에서만 가져온다.

### 입력 준비와 AI 보조

- 자동 기본값 source는 `CONCEPT_HYPOTHESIS`, `MARKET_ANALYSIS_ASSUMPTION`, `BUSINESS_MODEL_ASSUMPTION`으로 구분한다.
- `USER_INPUT`, `ACCEPTED`, `USER_EDITED_ACCEPTED`, finalized/immutable 값은 자동 적용 대상이 아니다.
- BM adapter는 현재 결과 계약의 `bm.financialHandoff.revenueModel`, `priceBase`를 읽는다.
- AI 계약은 1·2·3년차를 정확히 요구하고, 잘못된 3개년 응답에 한해 보정 호출을 최대 1회 수행한다.
- 월 이탈률은 percent 객체, 신규 고객 수는 정수 count 객체로 제한한다.
- 단위원가, 결제 수수료, 파트너 지급액, 배송비, 고객 증가 인프라비에 가격 기준 상한과 단위 설명을 적용한다.
- Tavily가 없거나 실패해도 입력 흐름을 계속하는 fail-open을 유지한다.
- `proposalVersion >= 2` 대안 요청과 기존 TaskRun/idempotency/retry 구조를 유지한다.

### UI와 분석 엔진

- Finance 화면의 필수 선행 문구와 source 표기에서 TechOps를 제거했다.
- Market/BM 및 컨셉 가정의 provenance를 표시한다.
- 기존 AI 추천 채택·수정 채택·거절·대안 요청, Snapshot 확정, 재오픈, 분석 실행 흐름을 유지했다.
- 기존 `AnalysisReport`를 그대로 사용한다.
- `FinancialCalculationService`, `FinancialMonteCarloService`, full snapshot/report hardening은 수정하지 않았다.

## Marketing 계약

### CPV2 Marketing Source

- current `ConceptPortfolioSelection`이 있으면 CPV2를 우선한다.
- 해당 selection의 non-stale seed만 허용하며 project ID, source type, portfolio concept ID를 대조한다.
- CPV2가 존재할 때 seed가 누락되거나 잘못되면 명시적으로 차단하며 legacy fallback을 수행하지 않는다.
- CPV2 selection 자체가 없을 때만 기존 `ConceptSelection` 경로를 사용한다.
- DB에는 legacy와 CPV2 authority가 동시에 채워지지 않도록 상호 배타 CHECK를 추가했다.
- Snapshot의 기존 고객·가치·가격·채널·차별점·SOM·법무·공식 근거 정보는 유지했다.

### 참조 이미지와 생성 아티팩트

- Frontend는 PNG/JPEG, 20MB 이하 파일만 기존 evidence-artifacts API로 업로드한다.
- `referenceArtifactId`를 create 요청에서 누락하지 않는다.
- Backend는 소유자와 프로젝트가 일치하는 evidence artifact인지 확인하고 PNG/JPEG 및 크기를 다시 검증한다.
- AI는 참조가 없으면 생성, 있으면 제품 형태·비율·색상·패키지 특성을 유지하는 편집을 수행한다.
- 카피의 금지 주장 검사를 이미지 생성보다 먼저 수행한다.
- 생성물은 JPEG, 20MB 이하, UUID 기반 `ai-artifacts/{uuid}.jpg`만 허용한다.
- 내부 토큰으로 Backend 업로드 API를 호출하고, Backend가 ObjectStorage 저장 결과의 크기와 SHA-256을 검증한다.
- completion은 ObjectStorage 존재 여부, JPEG content type, 크기를 검증한 뒤 생성 revision과 `MarketingAsset`을 결속한다.
- 트랜잭션 rollback 시 생성 아티팩트 삭제를 시도하며, 브라우저 응답에는 presigned URL을 제공한다.

### 활성 UI와 legacy 보존

- 활성 `MarketingContentPage`에서 `MarketingVisualSection`을 렌더링하지 않는다.
- 참조 이미지 입력, 생성 진행, 이미지+카피 통합 Canvas, 편집, 법무 경고, revision, 복사·다운로드·재생성·최종 저장 흐름을 유지한다.
- 콘텐츠 0개, 참조 이미지 없음, 생성 이미지 없음 상태가 모두 안전하게 렌더링된다.
- standalone Marketing Visual의 Backend, AI, TaskRun, DB, artifact, Frontend 구성요소는 삭제하지 않았다.

## Donor 매핑

| aidev 파일·기능 | full 목적지 | 판정 | 이유 |
|---|---|---|---|
| `FinancialService`의 BM 시작 의도 | full `FinancialService`, `ProjectModuleStatusService` | ADAPTED | donor의 단순 latest BM 대신 full current/stale 및 exact lineage를 사용했다. |
| Finance domain nullable source | full Finance preparation/snapshot domain, V20 | PORTED/ADAPTED | 기존 row 호환을 위해 legacy 컬럼은 보존하고 nullable로만 전환했다. |
| `FinancialPreparationFactory` 기본값 | full factory adapter | ADAPTED | full Market/BM 결과 envelope와 정확한 source label에 맞췄다. |
| Finance estimate schema·가드레일 | full Finance AI model/service | ADAPTED | full TaskRun과 completion 검증을 유지하고 bounded repair만 추가했다. |
| aidev Finance 화면 | full `FinancePage` | ADAPTED | full Shell, 단계 번호, Snapshot, `AnalysisReport`를 유지했다. |
| donor Finance 계산·분석 코드 | full 계산·Monte Carlo·분석 | SKIPPED | full 구현이 authority이며 더 강한 결정론·fallback을 보유한다. |
| CPV2 Marketing Source | full source service/factory/domain, V21 | ADAPTED | full seed repository와 project-scoped lineage에 맞췄다. |
| reference product image | evidence artifact API + Marketing create request | ADAPTED | 별도 저장소를 만들지 않고 기존 소유권·검증 파이프라인을 재사용했다. |
| `marketing_image.py` | full Marketing AI task | PORTED/ADAPTED | 내부 token, Backend ObjectStorage authority, 안전한 오류 코드에 맞췄다. |
| internal artifact controller/service | full 내부 API와 storage service | PORTED/ADAPTED | full security 설정에 필요한 두 route만 최소 추가했다. |
| aidev Marketing Content UI | full Marketing Content feature | ADAPTED | full revision/legal/history 기능 위에 이미지 통합 경험을 결합했다. |
| donor의 Marketing Visual 삭제 | full legacy visual runtime | SKIPPED | rollback·호환을 위해 비활성 보존이 요구된다. |
| donor security 전체 파일 | full `SecurityConfiguration` | SKIPPED | 구형 Finance permit 등은 이식하지 않고 내부 artifact route만 추가했다. |
| donor의 구형 Market/BM/TechOps/TaskRun | full 기존 모듈 | SKIPPED | 보호 영역이며 full authority다. |

## Migration

- `V20__finance_market_bm_authority.sql`
  - Finance preparation/snapshot의 TechOps 및 market seed source 컬럼을 nullable로 전환한다.
  - active source uniqueness를 프로젝트 + Market version + BM version으로 변경한다.
- `V21__bind_marketing_source_to_concept_portfolio_v2.sql`
  - `source_type`, `portfolio_selection_id`, `portfolio_concept_id`를 추가한다.
  - legacy 및 CPV2 project-scoped FK와 상호 배타 CHECK를 추가한다.
- 기존 migration 파일은 수정·rename·version 변경하지 않았다.
- 데이터 truncate/delete는 수행하지 않았다.

## 변경 파일

### AI

- `ai/app/tasks/finance_estimate/models.py`
- `ai/app/tasks/finance_estimate/service.py`
- `ai/app/tasks/marketing_content/models.py`
- `ai/app/tasks/marketing_content/prompts/generation.py`
- `ai/app/tasks/marketing_content/service.py`
- `ai/app/tasks/marketing_content/marketing_image.py`
- `ai/tests/test_finance_estimate.py`
- `ai/tests/test_marketing_content_contract.py`

### Backend

- `backend/src/main/java/com/aivle/backend/auth/SecurityConfiguration.java`
- `backend/src/main/java/com/aivle/backend/pipeline/artifact/application/ProjectEvidenceArtifactService.java`
- `backend/src/main/java/com/aivle/backend/pipeline/finance/application/FinancialEstimateCompletionService.java`
- `backend/src/main/java/com/aivle/backend/pipeline/finance/application/FinancialPreparationFactory.java`
- `backend/src/main/java/com/aivle/backend/pipeline/finance/application/FinancialService.java`
- `backend/src/main/java/com/aivle/backend/pipeline/finance/domain/FinancialInputPreparation.java`
- `backend/src/main/java/com/aivle/backend/pipeline/finance/domain/FinancialInputSnapshot.java`
- `backend/src/main/java/com/aivle/backend/pipeline/finance/repository/FinancialInputPreparationRepository.java`
- `backend/src/main/java/com/aivle/backend/pipeline/finance/repository/FinancialInputSnapshotRepository.java`
- `backend/src/main/java/com/aivle/backend/pipeline/marketing/api/InternalMarketingArtifactController.java`
- `backend/src/main/java/com/aivle/backend/pipeline/marketing/api/MarketingApiModels.java`
- `backend/src/main/java/com/aivle/backend/pipeline/marketing/application/MarketingArtifactStorageService.java`
- `backend/src/main/java/com/aivle/backend/pipeline/marketing/application/MarketingContentCompletionService.java`
- `backend/src/main/java/com/aivle/backend/pipeline/marketing/application/MarketingContentService.java`
- `backend/src/main/java/com/aivle/backend/pipeline/marketing/application/MarketingResultContract.java`
- `backend/src/main/java/com/aivle/backend/pipeline/marketing/application/MarketingSourceSnapshotFactory.java`
- `backend/src/main/java/com/aivle/backend/pipeline/marketing/application/MarketingSourceSnapshotService.java`
- `backend/src/main/java/com/aivle/backend/pipeline/marketing/domain/MarketingSourceSnapshot.java`
- `backend/src/main/java/com/aivle/backend/pipeline/marketing/repository/MarketingAssetRepository.java`
- `backend/src/main/java/com/aivle/backend/pipeline/marketing/worker/MarketingContentWorker.java`
- `backend/src/main/java/com/aivle/backend/pipeline/module/ProjectModuleStatusService.java`
- `backend/src/main/resources/db/migration/V20__finance_market_bm_authority.sql`
- `backend/src/main/resources/db/migration/V21__bind_marketing_source_to_concept_portfolio_v2.sql`
- `backend/src/test/java/com/aivle/backend/pipeline/finance/FinancialPreparationContractsTests.java`
- `backend/src/test/java/com/aivle/backend/pipeline/finance/FinancialServiceAsyncTests.java`
- `backend/src/test/java/com/aivle/backend/pipeline/marketing/MarketingContentArtifactTests.java`
- `backend/src/test/java/com/aivle/backend/pipeline/marketing/MarketingContentContractsTests.java`
- `backend/src/test/java/com/aivle/backend/pipeline/marketing/MarketingSourceSnapshotServiceTests.java`
- `backend/src/test/java/com/aivle/backend/pipeline/marketing/MarketingSourceV2ContractTests.java`
- `backend/src/test/java/com/aivle/backend/pipeline/module/ProjectModuleStatusServiceTests.java`

### Frontend와 계약 문서

- `frontEnd/src/features/finance/pages/FinancePage.jsx`
- `frontEnd/src/features/finance/pages/FinancePage.test.jsx`
- `frontEnd/src/features/marketing-content/api/marketingContentApi.js`
- `frontEnd/src/features/marketing-content/components/MarketingCanvas.jsx`
- `frontEnd/src/features/marketing-content/components/MarketingCanvas.test.jsx`
- `frontEnd/src/features/marketing-content/components/MarketingSetupPanel.jsx`
- `frontEnd/src/features/marketing-content/hooks/useMarketingContent.js`
- `frontEnd/src/features/marketing-content/model/marketingContentModel.js`
- `frontEnd/src/features/marketing-content/model/marketingContentModel.test.js`
- `frontEnd/src/features/marketing-content/pages/MarketingContentPage.jsx`
- `frontEnd/src/features/marketing-content/pages/MarketingContentPage.test.jsx`
- `frontEnd/src/features/marketing-content/styles/marketing-content.css`
- `docs/rebuild/contracts/marketing-content-request-v1.schema.json`
- `docs/rebuild/contracts/marketing-content-result-v1.schema.json`
- `docs/rebuild/progress/AIDEV-RESYNC_RESULT.md`
- `docs/rebuild/verification/AIDEV-RESYNC_USER_VERIFICATION.md`

## 자동 검증

- Backend: Finance 전체, TechOps 전체, Marketing 관련, module status 총 20개 클래스·76개 테스트 성공
- Backend: `compileJava`, `compileTestJava` 성공
- AI: Finance estimate/Tavily/report 및 Marketing Content/legacy Visual 19개 테스트 성공
- Frontend: Finance 및 Marketing Content/legacy Visual 13개 파일·28개 테스트 성공
- Frontend 변경 파일 대상 ESLint: 성공
- Frontend 전체 ESLint: 이번 변경과 무관한 기존 파일 7곳의 12개 오류와 2개 경고로 실패했으며 보호 영역을 수정하지 않았다.
- Marketing 요청·결과 JSON Schema 2개 파싱 성공
- `git diff --check`: 성공
- Frontend production build: 정확히 1회 실행, 성공했다. Vite가 259개 모듈을 변환했으며 500kB 초과 JS chunk 경고가 1건 있었다(`709.12 kB`, gzip `202.33 kB`). 경고는 기존 단일 bundle 크기 문제이며 build 실패로 간주하지 않았다.

## 보호 영역 감사

- CPV2 엔진·선택·법무 핵심: diff 없음
- Market 계산·Product FULL A1~A4: diff 없음
- BM 계산 알고리즘: diff 없음
- Twin 알고리즘·Bank: diff 없음
- TechOps 제품 로직: diff 없음
- `FinancialCalculationService`, `FinancialMonteCarloService`: diff 없음
- TaskRun 상태 코어·retry-current: diff 없음
- SSE·Work Center·JobEvent 코어: diff 없음
- ObjectStorage 코어: diff 없음
- 실제 `.env`: diff 없음
- auth core는 내부 AI artifact 두 route를 기존 3개 security chain에 추가한 최소 변경만 존재하며, 각 controller가 기존 내부 토큰을 다시 검사한다.

## 실행하지 않은 LIVE 검증과 남은 위험

- Docker compose 및 실제 PostgreSQL Flyway 적용은 실행하지 않았다.
- 실제 OpenAI 카피·이미지 생성, Tavily 호출은 실행하지 않았다.
- 실제 MinIO 업로드·다운로드·presigned URL은 실행하지 않았다.
- 브라우저 전체 journey와 provider latency, production 크기 이미지는 검증하지 않았다.
- 따라서 위 항목을 PASS로 간주하지 않는다.
- V20의 새 unique index를 적용하기 전에 운영 DB에 동일한 활성 Market/BM 조합의 legacy Finance row가 여러 개 있는지 확인해야 한다. migration은 데이터를 임의 삭제하지 않으므로 중복이 있으면 명시적으로 실패한다.
- 의존성 복구를 위해 `frontEnd`에서 `npm install`을 실행했으며 tracked package manifest/lockfile 변경은 없다. audit는 기존 3개 high severity 항목을 보고했으며 자동 수정은 수행하지 않았다.

## 커밋과 계속 지점

저장소 지침에 따라 commit·push를 수행하지 않았다. 권장 커밋 경계는 다음과 같다.

1. `resync: align finance authority with market and bm`
2. `resync: adopt aidev finance assistance and product ui`
3. `resync: bind marketing source to concept portfolio`
4. `resync: integrate marketing image generation into content`
5. `resync: finalize aidev product surfaces`

사용자는 `AIDEV-RESYNC_USER_VERIFICATION.md`에 따라 실제 PostgreSQL·MinIO·provider·브라우저 수용 검증을 수행한다. 실패 시 해당 항목 번호, HTTP 응답, TaskRun ID와 화면 증상을 전달하면 그 지점부터 이어서 진단한다.
