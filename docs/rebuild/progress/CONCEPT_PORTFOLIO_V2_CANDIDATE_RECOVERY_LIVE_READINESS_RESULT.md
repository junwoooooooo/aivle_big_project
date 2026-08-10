# Concept Portfolio V2 Candidate Recovery — Stage Result

## 변경 파일

- V2 fidelity, normalizer confidence, relation, provider Gateway, Engine orchestration, diagnostics
- Candidate recovery / selection / Legal precheck tests
- canonical Notebook와 실행 README
- 상세 결과 및 사용자 검증 문서

사용자 LIVE checkpoint와 recordings는 수정하지 않았다.

## 구현 계약

- Fidelity `PASS/ADAPTED/AMBIGUOUS/FAIL`
- semantic Fidelity 실제 호출
- Plan별 targeted Candidate regeneration 1회와 full revalidation
- reserve Plan marginal activation
- Candidate-stage adaptive Plan replenishment
- 최대 5 또는 `READY_LIMITED`
- 생성 순서 독립 greedy Plan selection
- Architecture `code/confidence/source`, 무근거 `OTHER`
- Plan/Candidate 공통 semantic canonicalization fallback
- Legal placeholder dependency filter
- readiness inconsistency diagnostic
- production `run_full()` 동일 recovery 경로

## 실제 수행 검사

- compileall PASS
- targeted 146 tests PASS
- Candidate recovery 18 tests PASS
- generic domain recovery 3종 PASS
- Notebook source syntax / fresh MOCK Run All PASS
- fresh MOCK Notebook Run All 및 MOCK→REPLAY `CONTRACT_PASS` PASS
- `git diff --check` PASS (줄 끝 정규화 안내 외 오류 없음)

## 의도적으로 생략

- AI Provider LIVE Candidate recovery
- MOLEG Full Legal C1와 remaining Legal
- Docker/browser/full regression/postgresTest/frontend build
- Production route/DB/frontend cutover

## 남은 위험

- LIVE semantic fidelity 및 regeneration 응답 품질은 사용자 재검증 필요
- 실제 다도메인에서 Architecture `OTHER/LOW` 비율과 classifier 비용 관찰 필요
- Legal fact field의 구체성은 C1 LIVE 표시 후 확인 필요

## 정확한 계속 지점

Notebook Kernel Restart 후 LIVE Plan Selection까지 재확인하고 Candidate 전체 생성 뒤 Candidate Recovery 표를 확인한다. final Candidate가 준비된 후 C1 Legal Fact Pattern을 검토하고 `RUN_FULL_LEGAL_C1=True`로 진행한다.
