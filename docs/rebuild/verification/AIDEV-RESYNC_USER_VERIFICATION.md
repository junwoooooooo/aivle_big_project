# AIDEV 재동기화 사용자 검증

## 검증 목표

Finance가 TechOps와 독립적으로 current Market/BM 이후 시작되고, Marketing이 CPV2 Source와 선택적 참조 이미지를 사용해 카피·이미지를 하나의 콘텐츠 흐름으로 생성하는지 실제 통합 환경에서 확인한다.

## 준비와 안전 조건

1. 변경 전 DB를 백업한다.
2. 운영 복제본 또는 격리된 검증 환경을 사용한다.
3. Backend, AI, Frontend, PostgreSQL, MinIO를 같은 검증 환경에 연결한다.
4. Backend와 AI의 내부 서비스 토큰이 동일하게 설정되었는지 확인한다. 토큰 값을 보고서나 로그에 복사하지 않는다.
5. OpenAI/Tavily 호출 비용과 데이터 정책을 확인한 뒤에만 LIVE provider 검증을 수행한다.

## 1. Flyway migration

Backend 시작 전 다음 중복을 읽기 전용 쿼리로 확인한다.

```sql
SELECT project_id, source_market_research_version_id, source_business_model_version_id, COUNT(*)
FROM financial_input_preparations
WHERE deleted_at IS NULL
  AND source_market_research_version_id IS NOT NULL
  AND source_business_model_version_id IS NOT NULL
GROUP BY 1, 2, 3
HAVING COUNT(*) > 1;

SELECT project_id, source_market_research_version_id, source_business_model_version_id, COUNT(*)
FROM financial_input_snapshots
WHERE deleted_at IS NULL
  AND source_market_research_version_id IS NOT NULL
  AND source_business_model_version_id IS NOT NULL
GROUP BY 1, 2, 3
HAVING COUNT(*) > 1;
```

결과가 있으면 V20 적용 전에 중단하고 각 row의 실제 current/history 의미를 검토한다. 자동 삭제로 해결하면 안 된다.

Flyway 적용 후 다음을 확인한다.

- V20, V21이 성공 상태다.
- 기존 migration checksum 오류가 없다.
- Finance의 TechOps/market seed source 컬럼이 nullable이다.
- Finance active unique index가 프로젝트 + Market version + BM version을 사용한다.
- Marketing Source에 `source_type`, `portfolio_selection_id`, `portfolio_concept_id`와 4개 FK, 2개 CHECK가 존재한다.
- 기존 Finance·Marketing row가 삭제되지 않았다.

## 2. Finance 시작 권한

### Market 또는 BM 누락

1. Market FULL이 없는 프로젝트에서 Finance를 연다.
2. 시장 분석 완료 안내와 BM 경로 CTA가 표시되는지 확인한다.
3. TechOps 완료를 요구하는 문구가 없어야 한다.
4. BM이 없는 프로젝트에서도 같은 방식으로 BM 완료를 요구해야 한다.

### TechOps 없이 Finance 시작

1. current Market FULL과 해당 Market을 source로 한 current BM을 준비한다.
2. TechOps preparation/snapshot은 만들지 않는다.
3. Finance를 열고 초기화를 수행한다.
4. 응답의 Market version ID와 BM version ID가 현재 version과 정확히 같아야 한다.
5. `sourceTechOpsSnapshotId`는 null이어도 정상이다.
6. 같은 입력으로 다시 초기화했을 때 기존 preparation을 재사용해야 한다.

### stale 및 lineage 차단

1. Market을 새 version으로 바꿔 기존 BM이 이전 Market을 가리키게 만든다.
2. Finance가 기존 BM을 current로 사용하지 않고 lineage 불일치를 안내하는지 확인한다.
3. Market 또는 BM을 stale로 만든 경우에도 Finance 초기화·확정·현재 조회가 차단되어야 한다.

## 3. Finance 기본값과 AI 보조

1. 같은 가격·수익 모델에 대해 Concept, Market, BM에 서로 다른 가정을 준비한다.
2. 새 preparation에서 BM 가정이 최종 자동 기본값이고 provenance가 BM 경로를 가리키는지 확인한다.
3. 사용자 값을 저장한 뒤 페이지를 다시 열어 자동 기본값이 사용자 값을 덮어쓰지 않는지 확인한다.
4. AI 추천을 채택, 수정 후 채택, 거절, 다른 추천 요청 순서로 확인한다.
5. 다른 추천의 proposal version이 2 이상이고 기존 제안 감사 정보가 보존되는지 확인한다.
6. 3개년 목표가 정확히 1·2·3년차인지, 월 이탈률이 percent, 신규 고객 수가 count인지 확인한다.
7. Tavily를 비활성화한 환경에서도 Finance 입력과 AI 기본 흐름이 안전한 오류 또는 fail-open으로 동작하는지 확인한다.
8. Snapshot 확정 후 값을 수정할 수 없고, 재오픈 시 기존 Snapshot이 history로 남는지 확인한다.
9. 재무 분석을 실행해 기존 결정론 계산과 `AnalysisReport`가 표시되는지 확인한다.

## 4. CPV2 Marketing Source

1. current CPV2 selection과 같은 selection/concept를 가리키는 non-stale Market Analysis Seed를 준비한다.
2. Marketing Source 확정 응답의 selection/concept/seed ID가 정확한지 확인한다.
3. Source 내용에 고객, 문제, 가치, 포지셔닝, 기능, 가격, 채널, 차별점, SOM, 법무 통제, 공식 근거가 유지되는지 확인한다.
4. CPV2 seed를 stale로 만들거나 제거하고 legacy selection을 남긴다.
5. Marketing Source가 legacy로 fallback하지 않고 명시적으로 차단되는지 확인한다.
6. CPV2 selection 자체가 없는 legacy 프로젝트에서만 기존 source 확정이 가능한지 확인한다.
7. 다른 프로젝트 seed/concept를 연결하려 하면 차단되는지 확인한다.

## 5. 참조 이미지 업로드

1. Marketing 화면에서 20MB 이하 PNG 또는 JPEG를 선택한다.
2. Network에서 먼저 다음 업로드가 실행되는지 확인한다.

```text
POST /api/v3/projects/{projectId}/evidence-artifacts
```

3. 이어지는 Marketing create 요청에 반환된 `referenceArtifactId`가 포함되는지 확인한다.
4. 0바이트, PDF, GIF, 20MB 초과 파일이 Frontend와 Backend에서 차단되는지 확인한다.
5. 다른 사용자의 프로젝트 artifact ID를 요청에 넣으면 차단되는지 확인한다.
6. 이미지를 첨부하지 않아도 Marketing 생성 요청이 정상 시작되는지 확인한다.

## 6. 카피·이미지 생성과 ObjectStorage

LIVE provider와 MinIO 사용이 승인된 환경에서만 수행한다.

1. 참조 이미지 없이 콘텐츠를 생성한다.
2. 금지 주장이 없는 카피, image brief, 생성 JPEG 한 개가 결과에 포함되는지 확인한다.
3. 결과 ref가 `ai-artifacts/{uuid}.jpg` 형식인지 확인한다.
4. MinIO object metadata가 `image/jpeg`, 0보다 크고 20MB 이하인지 확인한다.
5. 참조 이미지를 포함해 다시 생성하고 제품 형태·비율·색상·패키지 특성이 유지되는지 사람이 검토한다.
6. 생성 이미지 자체에 글자, 로고, watermark가 포함되지 않는지 확인한다.
7. Source에 금지 문구를 넣고 생성해 이미지 호출 전에 작업이 안전 차단되는지 확인한다.
8. 존재하지 않는 object ref, PNG ref, 20MB 초과 ref를 completion에 주입한 격리 테스트에서 revision/asset이 커밋되지 않는지 확인한다.
9. 저장 실패 또는 트랜잭션 rollback 후 orphan object가 남지 않는지 확인한다.
10. 상세 API의 artifact URL을 브라우저에서 열 수 있고 만료된 URL은 상세 재조회로 갱신되는지 확인한다.

## 7. Marketing 통합 화면

1. 콘텐츠가 0개인 프로젝트에서 페이지가 오류 없이 빈 상태를 표시하는지 확인한다.
2. 생성 중 진행 상태가 TaskRun/JobEvent를 따라 갱신되는지 확인한다.
3. 생성 후 같은 Canvas에 이미지, 제목, 본문, CTA, hashtag가 표시되는지 확인한다.
4. 생성 이미지가 아직 없어도 카피 preview가 깨지지 않는지 확인한다.
5. 편집 저장 시 새 revision이 생성되고 image asset은 생성 revision과 결속되어 유지되는지 확인한다.
6. 법무 금지 표현은 저장·최종 확정을 차단하고 필수 고지 경고를 표시해야 한다.
7. 복사, 다운로드, 새 초안 생성, 최종 저장이 동작하는지 확인한다.
8. 활성 페이지에 standalone `MarketingVisualSection` 또는 별도 Visual 생성 CTA가 없어야 한다.
9. 기존 Marketing Visual API·TaskRun·history·artifact 데이터는 삭제되지 않고 호환 조회가 가능한지 확인한다.

## 8. 운영 관찰

- AI 이미지 작업의 worker timeout이 5분, lease가 7분 범위로 동작하는지 확인한다.
- provider 지연 중 중복 TaskRun이 생성되지 않는지 확인한다.
- 같은 idempotency key 재요청이 기존 작업을 재사용하거나 계약대로 충돌하는지 확인한다.
- 작업 실패 이벤트에 토큰, prompt 원문, presigned URL 전체가 노출되지 않는지 확인한다.
- stale Marketing Source 또는 Finance source가 current로 표시되지 않는지 확인한다.

## 자동 검증 명령

Backend:

```powershell
cd backend
.\gradlew.bat test --tests "com.aivle.backend.pipeline.finance.*" --tests "com.aivle.backend.pipeline.techops.*" --tests "com.aivle.backend.pipeline.marketing.MarketingContentArtifactTests" --tests "com.aivle.backend.pipeline.marketing.MarketingContentContractsTests" --tests "com.aivle.backend.pipeline.marketing.MarketingSourceSnapshotServiceTests" --tests "com.aivle.backend.pipeline.marketing.MarketingSourceV2ContractTests" --tests "com.aivle.backend.pipeline.marketing.worker.MarketingContentWorkerTests" --tests "com.aivle.backend.pipeline.module.ProjectModuleStatusServiceTests"
```

AI:

```powershell
cd ai
python -m pytest tests/test_finance_estimate.py tests/test_finance_tavily.py tests/test_finance_analysis_report.py tests/test_marketing_content_contract.py tests/test_marketing_visual.py -q
```

Frontend:

```powershell
cd frontEnd
npm.cmd run test:run -- src/features/finance/hooks/useFinance.test.jsx src/features/finance/model/financeModel.test.js src/features/finance/pages/AnalysisReport.test.jsx src/features/finance/pages/FinancePage.test.jsx src/features/marketing-content/components/MarketingCanvas.test.jsx src/features/marketing-content/components/MarketingCopyEditor.test.jsx src/features/marketing-content/components/MarketingVisualSection.test.jsx src/features/marketing-content/hooks/useMarketingContent.test.jsx src/features/marketing-content/hooks/useMarketingGeneration.test.jsx src/features/marketing-content/hooks/useMarketingVisual.test.jsx src/features/marketing-content/model/marketingContentModel.test.js src/features/marketing-content/model/marketingVisualModel.test.js src/features/marketing-content/pages/MarketingContentPage.test.jsx
npm.cmd run build
```

## 수용 기준

- Finance는 current Market/BM과 정확한 lineage만 요구하며 TechOps 없이 시작한다.
- 사용자·채택·확정 재무 값은 자동 기본값이나 AI가 덮어쓰지 않는다.
- CPV2가 있으면 CPV2 Source만 사용하고 잘못된 seed를 legacy로 우회하지 않는다.
- 참조 이미지는 동일 프로젝트 evidence artifact만 사용한다.
- 법무 검사 후 생성된 JPEG가 Backend ObjectStorage에 저장되고 revision/asset에 결속된다.
- 활성 Marketing 화면은 이미지와 카피를 통합 표시하며 legacy Visual 런타임은 삭제되지 않는다.
- 실제로 실행한 항목만 PASS로 기록하고, 실행하지 않은 LIVE 항목은 미검증으로 남긴다.
