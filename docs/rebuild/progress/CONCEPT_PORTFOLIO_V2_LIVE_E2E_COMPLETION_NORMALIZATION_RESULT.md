# Concept Portfolio V2 LIVE E2E Completion Normalization — Stage Result

## 변경 파일

- `ai/app/concept_portfolio_v2/`의 V2 Core와 Notebook diagnostics
- `ai/app/tasks/concept_legal_review/` 및 shared Provider failure diagnostics
- `ai/notebooks/concept_portfolio_v2_lab.ipynb`와 실행 안내
- V2 41~79 및 shared Legal 테스트
- `docs/rebuild/concept-portfolio-v2/CONCEPT_PORTFOLIO_V2_LIVE_E2E_COMPLETION_NORMALIZATION_RESULT.md`

사용자 소유 LIVE checkpoint와 `ai/recordings/`는 증거로 보존했다.

## 구현한 계약

- ko-KR 사용자 content와 machine code/labelKo 분리
- Idea Brief 단일 derivation 및 interpretation downstream 보존
- ExplorationBreadth-aware intent, 실제 Plan content/LOCK 검증
- actual Candidate mechanics/fidelity/distinctness
- reserve shortfall와 on-demand replan
- Legal dynamic evidence enum, nested min 1, citation repair 1회, 안전 diagnostics
- fixedJurisdiction external fact parity와 candidate-scoped `READY_LIMITED`
- versioned REPLAY, 수동 hypothesis, 실제 Delta Legal, 최종 handoff gate

## 실제 수행한 검사

- compileall PASS
- V2 40 + 신규 39 + shared Legal/Idea 대상 테스트: 103 passed
- Notebook JSON/문법/출력 초기화 PASS
- Notebook MOCK Run All PASS
- fresh fixture REPLAY full run PASS (`REPLAY_READY`, 외부 상위 작업 0)
- git diff --check PASS

## 의도적으로 생략한 검사

- AI Provider/MOLEG LIVE
- Docker/browser/provider smoke
- full regression, full postgresTest, frontend production build

## 남은 위험

- 실제 Provider가 새 dynamic evidence schema와 한국어 correction을 준수하는지는 사용자 LIVE 재검증 필요
- 기존 사용자 녹화는 버전 메타데이터가 없어 `REPLAY_PARTIAL`
- Production V1 route/DB/frontend는 동결 상태이며 통합하지 않음

## 정확한 계속 지점

canonical Notebook을 새 커널로 열고 MOCK Run All 후, LIVE 04 Schema Preflight부터 06~28 staged 순서로 실행한다. 첫 acceptance 지점은 28 Full Legal C1의 성공이다.
