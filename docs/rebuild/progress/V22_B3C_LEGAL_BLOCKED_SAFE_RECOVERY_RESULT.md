# V22-B3C 결과

## 구현 계약

- 가설 적용 직전 `concept-refinement-application-before-v1` snapshot과 canonical hash를 최초 1회 저장한다.
- LEGAL_BLOCKED 복구는 post-apply lineage가 current일 때만 실행한다.
- 선택 가설은 snapshot의 전체 mutable metadata로 새 proposal version을 생성하고 Selection revision을 증가시킨다.
- 선택 BM 필드는 decision snapshot의 `currentValue`만 partial patch하며, 비선택 필드와 constraints를 보존한다.
- CURRENT 법률 보고서를 deterministic하게 다시 만들고 Round를 `RECOVERED`로 기록한다.
- RECOVERED는 명시적 finalize 또는 허용된 다음 Round만 지원하며 자동 실행은 없다.
- 복구된 finalization은 새 Market Seed를 요구하고, 차단된 현재 Round 변경은 final selectedChanges에서 제외한다.

## 변경 범위

- Backend domain/service: `ConceptPortfolioSelection`, `ConceptRefinementRound`, `ConceptRefinementApplicationBeforeContract`, `ConceptRefinementApplicationService`, `ConceptRefinementLegalRecoveryService`, `ConceptRefinementDecisionContract`, `ConceptRefinementService`, `ConceptRefinementFinalizationService`, `ConceptRefinementMaterialFactory`, `ConceptRefinementController`
- DB: `V35__concept_refinement_legal_blocked_recovery.sql`
- Backend test: `ConceptRefinementApplicationTests`, `ConceptRefinementLegalRecoveryTests`, `ConceptRefinementNextRoundTests`, `ConceptRefinementFinalizationTests`, constructor 정렬을 위한 `ConceptRefinementServiceTests`
- Frontend: `businessValidationApi.js`, `ConceptRefinementPanel.jsx`, `BusinessValidationPage.jsx`, `BusinessValidationPage.test.jsx`
- AI 소스 변경 없음

## 실행 검증

- Backend focused Gradle: 4개 Refinement class PASS
- Frontend focused Vitest: 1 file, 31 tests PASS
- 변경 Frontend 파일 selective ESLint: PASS
- 전체 suite, production build, AI test, Docker, browser: 실행하지 않음

## 남은 검증 및 이어갈 지점

- V35 실제 PostgreSQL migration과 시각 UX는 사용자 검증 대기다.
- 다음 단계는 post-refinement audit이며 자동으로 진행하지 않는다.
