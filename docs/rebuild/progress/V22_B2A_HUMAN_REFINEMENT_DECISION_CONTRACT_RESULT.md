# V22-B2A Human Refinement Decision Contract 결과

- START SHA: `5e4036cc4f9fc1bb72eb2a8a4b3718f755cf3909`
- DONOR SHA: `4ee74359a1b231359dc3131fb8eecb126462d2bf`
- 상태: **READY FOR V22-B2B**

## 구현 계약

- stored proposal의 source-bound authoritative content를 canonical JSON hashing하여 `proposalKey`를 계산하고 CurrentView에 투영한다.
- 전체 proposal key를 canonical order로 묶은 `proposalSetHash`를 decision concurrency token으로 제공한다.
- `POST /api/v3/projects/{projectId}/business-validation/refinement/decision`은 expected round/set hash와 proposal key만 신뢰한다.
- source/Selection revision/exact Seed/BM Plan revision을 재검증한 뒤 immutable decision snapshot을 기록한다.
- 선택 proposal은 Full enum 기반 `HYPOTHESIS`, 명시적 snake_case `BM_PLAN`, `targetUsers`/`featureSet` `OVERLAY`로 분류한다.
- 결정 상태는 `DECISION_RECORDED` 또는 terminal `KEEP_CURRENT`이며 동일 idempotency key+hash만 replay한다.
- decision transaction은 product state와 TaskRun을 변경하지 않는다.

## 변경 파일

- Backend: `ConceptRefinementRound`, `ConceptRefinementService`, `ConceptRefinementController`
- 신규 Backend: `ConceptRefinementDecisionContract`, `ConceptRefinementDecisionService`
- Migration: `V31__concept_refinement_human_decision.sql`
- Test: `ConceptRefinementDecisionTests`, 기존 service test constructor 호환 수정
- Docs: 이 결과 문서와 사용자 검증 문서

## 실제 검증

- Command: `.\gradlew.bat test --tests "com.aivle.backend.pipeline.refinement.ConceptRefinementDecisionTests"`
- 실행 2회: 첫 실행은 test-only Mockito strict stub 1건 실패, fixture 수정 후 최종 **8 tests PASS / failure·error·skipped 0**
- compileJava 및 compileTestJava 성공
- AI/Frontend 변경과 테스트 0회, 외부 AI 0회
- 전체 Backend suite, Docker/PostgreSQL migration, browser는 의도적으로 생략했다.

## 남은 위험과 계속 지점

- V31의 실제 PostgreSQL 적용은 환경 검증 PENDING이다.
- B2A는 application plan만 고정하고 적용하지 않는다. 다음 계속 지점은 V22-B2B의 transactional apply 및 실제 product-state mutation 경계다.

