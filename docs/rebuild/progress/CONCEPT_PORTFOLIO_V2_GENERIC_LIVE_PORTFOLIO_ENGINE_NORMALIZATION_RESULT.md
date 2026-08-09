# Concept Portfolio V2 Generic LIVE Portfolio Engine Normalization — Stage Result

## 변경 파일

- V2 models, anchor, mechanics/normalizer, distinctness, fidelity, planning, engine, providers, diagnostics
- production task `ai/app/tasks/concept_portfolio_v2/`
- generic domain fixture/test
- canonical Notebook/README
- 상세 결과 및 사용자 검증 문서

사용자 LIVE checkpoint와 recordings는 수정하지 않았다.

## 구현 계약

- Generic OpportunityKernel
- ConceptThesis / BusinessArchitecture / CanonicalConceptDescriptor
- DUPLICATE / VARIANT / DISTINCT / OUT_OF_SCOPE
- soft Concept Family selection
- system-owned 동일 Plan/Candidate normalizer
- PASS / ADAPTED / FAIL fidelity
- initial + 최대 2 adaptive replenishment
- production-importable same-Engine entrypoint
- 기존 Legal/Hypothesis/Handoff 회귀 보존

## 실제 수행 검사

- compileall PASS
- targeted 128 tests PASS
- generic domains 7종 PASS
- Notebook nbformat/syntax 및 MOCK Run All PASS
- fresh full REPLAY PASS
- food-specific Core static scan PASS
- git diff --check PASS (줄 끝 정규화 안내 외 오류 없음)

## 의도적으로 생략

- AI/MOLEG LIVE
- Docker/browser/provider smoke
- full regression/postgresTest/frontend build

## 남은 위험

- 실제 LIVE Provider의 새 explicit thesis 품질과 replenishment 응답은 사용자 재검증 필요
- deterministic generic normalizer의 OTHER/AMBIGUOUS 비율은 실제 다도메인 LIVE 결과로 관찰 필요
- production route cutover는 수행하지 않음

## 정확한 계속 지점

Notebook 새 커널에서 MOCK Run All 후 LIVE Idea Brief → OpportunityKernel → Plan Pool → Family/Variant relation 순으로 실행한다. Candidate C1 fidelity가 PASS 또는 ADAPTED인지 확인한 뒤 Legal C1로 진행한다.
