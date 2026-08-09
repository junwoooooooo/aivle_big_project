# Concept Portfolio V2 Lab 단계 결과

## 상태

IMPLEMENTATION COMPLETE / MOCK·STATIC·TARGETED VERIFICATION COMPLETE / LIVE RUNTIME ACCEPTANCE PENDING USER TEST

## 파일 변경

- 새 격리 Core: `ai/app/concept_portfolio_v2/`
- fixture: `ai/fixtures/concept_portfolio_v2/`
- Notebook/README: `ai/notebooks/concept_portfolio_v2_lab.ipynb`, `ai/notebooks/CONCEPT_PORTFOLIO_V2_LAB_README.md`
- test: `ai/tests/concept_portfolio_v2/test_engine.py`
- 조사/상세 결과: `docs/rebuild/concept-portfolio-v2/`
- 개발 dependency: `ai/requirements-dev.txt`

## 구현 계약

- 현행 Idea Brief/Safety/Candidate/Legal/Hypothesis/Market Seed/Marketing Source adapter
- dynamic plan pool, max-5/non-exact-five, mechanics distinctness
- Candidate schema/lock/anchor/plan-fidelity 검증
- official Legal route, same-lineage redesign, bounded replan, Legal-LOCK needs-input
- terminal state machine, safe trace, provider usage, record/replay
- 두 downstream snapshot payload 및 compatibility 검증

## 실제 실행

- compileall: PASS
- targeted pytest: 23 passed
- Notebook JSON/metadata/code syntax: PASS
- 직접 MOCK full run: READY_FULL / 5 / handoff PASS / READY
- git diff --check: PASS

## 의도적 생략

- nbconvert fresh-kernel: 현재 환경에 실행 도구 미설치
- LIVE AI/MOLEG, Docker, Backend/Frontend/full regression: 범위 밖 또는 사용자 key/비용 필요

## 남은 위험

- LIVE response-schema와 Legal 외부 의존성 미검증
- Python downstream mirror와 Java factory의 production integration 전 교차 test 필요
- fresh-kernel Notebook Run All 사용자 검증 필요

## 정확한 continuation point

`docs/rebuild/verification/CONCEPT_PORTFOLIO_V2_LAB_USER_VERIFICATION.md` 순서로 MOCK Run All 후 LIVE 1회 acceptance를 수행한다. production cutover는 별도 단계다.

상세: `docs/rebuild/concept-portfolio-v2/CONCEPT_PORTFOLIO_V2_LAB_RESULT.md`
