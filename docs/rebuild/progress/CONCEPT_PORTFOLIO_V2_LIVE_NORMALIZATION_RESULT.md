# Concept Portfolio V2 LIVE Normalization 결과

## 상태

구현 완료. MOCK/static/targeted verification 완료. 사용자 LIVE acceptance 대기.

## 변경 파일

- V2 계약/orchestration: `ai/app/concept_portfolio_v2/models.py`, `engine.py`, `providers.py`, `adapters.py`
- 신규 정책 모듈: `planning.py`, `schema_preflight.py`, `candidate_governance.py`, `anchor_policy.py`, `distinctness.py`, `plan_fidelity.py`, `snapshot_hash.py`
- 진단/공유 transport: `diagnostics/notebook_view.py`, `ai/app/providers/structured.py`
- Notebook/안내: `ai/notebooks/concept_portfolio_v2_lab.ipynb`, `CONCEPT_PORTFOLIO_V2_LAB_README.md`
- 회귀: `ai/tests/concept_portfolio_v2/test_engine.py`, `ai/fixtures/concept_portfolio_v2/*.json`
- 문서: 본 progress, LIVE normalization 상세 결과, user verification 문서
- 사용자 실행 흔적인 `ai/notebooks/.ipynb_checkpoints/concept_portfolio_v2_lab-checkpoint.ipynb`는 수정하지 않았다.

## 구현 계약

- Provider-owned Plan Draft와 system-owned canonical Plan 분리
- strict structured-output schema preflight 및 0-call failure
- user LOCK/governance/provenance 정규화
- source lock/opportunity anchor 분리와 specialization 허용
- lock-count diversity hard cap 제거
- 3-level semantic distinctness 및 non-exact Plan fidelity
- lineage별 redesign budget과 replan full validation
- downstream legal/hypothesis/delta 구조·계약 검증
- Java-compatible snapshot hash
- external call/logical operation 분리, permanent schema failure no-retry, safe diagnostics/redaction
- staged LIVE Notebook

## 실제 실행한 검사

- `python -m compileall -q app/concept_portfolio_v2 app/providers/structured.py`: PASS
- V2 schema preflight: 4 schema PASS, Provider Calls 0
- `python -m pytest tests/concept_portfolio_v2/test_engine.py -q --tb=short`: 40 passed
- Notebook nbformat/75 cells/36 code-cell ID·syntax·output clear: PASS
- fresh-kernel MOCK `jupyter nbconvert --execute`: PASS
- `git diff --check`: PASS (Windows line-ending 안내만 출력)

## 의도적으로 생략한 검사

- 실제 LIVE Provider/MOLEG
- 전체 AI/backend/frontend/postgresTest/Testcontainers
- Docker/browser/provider smoke
- frontend production build

## 남은 위험

- Provider별 strict schema 실제 acceptance는 사용자 LIVE Plan-only smoke가 필요하다.
- 실제 Provider가 생성하는 한국어 표현의 Level 3 판정 품질은 LIVE 결과 관찰이 필요하다.
- MOLEG/official evidence 가용성과 비용·rate limit은 로컬 MOCK으로 증명하지 않았다.
- Python 3.14.x smoke는 production-equivalent Python 3.12와 차이가 있을 수 있다.

## 정확한 continuation point

`docs/rebuild/verification/CONCEPT_PORTFOLIO_V2_LIVE_NORMALIZATION_USER_VERIFICATION.md`의 Step 0부터 순서대로 사용자 LIVE acceptance를 수행한다. 다음 제품 단계로 자동 진행하지 않는다.
