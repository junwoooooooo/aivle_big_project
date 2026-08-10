# Concept Portfolio V2 Generic Role Semantic Multi-Domain Recovery 진행 결과

## 상태

- 구현 완료
- 정적/targeted/multi-domain 계약 검증 완료
- 사용자 LIVE 재검증 대기

## 변경 파일

- `ai/app/concept_portfolio_v2/models.py`
- `ai/app/concept_portfolio_v2/legal_fact_completeness.py`
- `ai/app/concept_portfolio_v2/providers.py`
- `ai/app/concept_portfolio_v2/engine.py`
- `ai/app/concept_portfolio_v2/schema_preflight.py`
- `ai/app/concept_portfolio_v2/diagnostics/notebook_view.py`
- `ai/notebooks/concept_portfolio_v2_lab.ipynb`
- `ai/tests/concept_portfolio_v2/test_generic_role_semantic_recovery_round2.py`
- `docs/rebuild/concept-portfolio-v2/CONCEPT_PORTFOLIO_V2_GENERIC_ROLE_SEMANTIC_MULTI_DOMAIN_RECOVERY_RESULT.md`
- 본 progress 및 matching verification 문서

## 구현 계약

- deterministic 역할 판정 후 ambiguous role 1회 batch semantic fallback
- Fact Completion child의 1회 batch semantic 재검사
- architecture-role cross-consistency diagnostic
- `preLegalExclusions`와 사용자 `requiredInputs` 분리
- `NO_LEGAL_READY_CANDIDATES` 전용 실패 taxonomy
- Notebook 실행 레벨별 외부 호출 gate와 분리 metric
- empty Hypothesis의 명시적 NOT_READY
- 디지털 문구의 physical activity false-positive 억제
- 동일 production entrypoint/Core path 유지

## 실제 수행 검사

- compileall: PASS
- strict schema preflight 7종: PASS
- targeted tests: 244 PASS
- canonical Notebook 47 code cells syntax: PASS
- fresh non-LIVE production entrypoint smoke: `READY_FULL 2`
- `git diff --check`: PASS (whitespace error 없음)

## 의도적으로 생략한 검사

- AI Provider LIVE, MOLEG LIVE
- Backend route cutover
- full repository regression/postgresTest/Docker/browser/frontend build
- commit/push

## 남은 위험

- 실제 Provider가 새 role semantic strict schema와 의미 판정 지침을 따르는지는 사용자 LIVE 재실행으로 확인해야 한다.
- 사용자 LIVE 4개 도메인의 Legal-ready 및 Full Legal 결과는 아직 미확인이다.
- architecture-role consistency는 diagnostic이므로 conflict가 발견되어도 자동 교정하지 않는다.

## 정확한 계속 지점

- canonical Notebook에서 `LIVE_TEST_LEVEL='ONE_CLICK'`로 네 시나리오를 fresh kernel에서 순차 실행한다.
- 첫 확인점은 `BUSINESS_ROLE_SEMANTIC_BATCH`, `legalReady`, `preLegalExclusions`, `failureDiagnostics`다.
- 구현 결과 상세는 `docs/rebuild/concept-portfolio-v2/CONCEPT_PORTFOLIO_V2_GENERIC_ROLE_SEMANTIC_MULTI_DOMAIN_RECOVERY_RESULT.md`를 따른다.
