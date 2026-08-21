# V29 Concept Portfolio Production Contract Recovery — Result

## IMPLEMENTED

- Concept Candidate의 7개 hypothesis shape를 단일 계약으로 만들었다. 5개 legal-sensitive 값은 string이고, 두 SOM 값은 기존 structured object다.
- `CHANNELS`/`DIFFERENTIATORS`의 flat nonblank string list만 항목 의미를 유지한 `", "` 구분 한 줄 string으로 정규화한다. 기존 정상 string은 그대로 보존하며 empty/nested/object/임의 array는 거절한다.
- AI hypothesis loading, refinement 통과값, backend confirm, materialization persistence 경계에 계약 방어선을 추가했다.
- Delta Legal 변경 Candidate는 `model_copy(update=...)`가 아니라 전체 payload를 `ConceptCandidateResult.model_validate(...)`로 다시 검증한다.
- `DELTA_LEGAL_FAILED` retry는 현재 revision의 최신 7개 hypothesis만 repair하고 repository에 저장한다. 과거 decision row, revision, immutable Concept snapshot은 바꾸지 않는다.
- Legal input Pydantic 오류는 값 없이 `path`, `expectedType`, `category`만 `ProviderFailure.validation_fields`로 전달한다.
- Frontend는 confirm/retry 공통 selection action task ID를 추적하고 `DELTA_LEGAL_PENDING` SSE, terminal refresh/ID clear, duplicate retry 방지, retry 실패 refresh를 수행한다.
- Confirmed Idea 편집은 baseline/dirty를 추적하고 입력·첨부의 저장되지 않은 변경을 경고한다. CTA는 실제 derive/review/confirm 동작을 설명한다.
- authoritative Concept module `STALE` 동안 이전 run의 concepts/selection/report/seed를 current 결과로 노출하지 않고, 최신 confirmed Idea snapshot으로 새 run을 시작한다.
- canonical hash algorithm은 바꾸지 않았다. `CanonicalInputHasher`에서 Spring application `ObjectMapper` 주입을 제거하고 dedicated parser/serializer를 사용한다.

## ROOT CAUSE REMOVED

1. Refinement list proposal이 backend raw edit로 전달되고 `CHANNELS.finalValueJson` array로 저장됐다.
2. Delta Legal이 검증 없는 Pydantic `model_copy`로 `channels: str` Candidate에 list를 삽입했다.
3. Legal fact pattern의 `GovernedText.value: str` 검증이 실패했지만 validation path가 버려졌다.
4. Retry frontend가 새 task ID를 버리고 pending Delta 상태를 SSE 대상으로 삼지 않았다.
5. Confirmed Idea 편집이 local screen state만 바꿔 unsaved 변경과 downstream stale authority가 UI에서 보호되지 않았다.
6. Canonical hasher가 전역 Spring mapper 설정에 결합돼 production runtime configuration 차이가 hash 결과에 영향을 줄 수 있었다.

현재 local Spring context의 application `JsonMapper`는 sanitized shared vector에서 dedicated mapper와 같은 hash를 냈다. 따라서 production의 `3ed...`를 만든 구체 mapper feature/module state는 repository만으로 재현되지 않았다. 확인된 코드 수준 원인은 canonical hasher가 그 전역 mutable/configurable bean에 의존한 구조이며, 새 구현은 그 의존성을 제거한다.

## FILES CHANGED

- AI runtime: `hypothesis_value_contract.py`, `models.py`, `engine.py`, `drift.py`, Concept Legal Review `service.py`.
- AI tests: hypothesis contract, live Delta normalization, legal diagnostics, shared canonical vector tests.
- Backend runtime: `HypothesisValueContract.java`, hypothesis decision repair, refinement apply, selection service, selection materialization, dedicated `CanonicalInputHasher`.
- Backend tests/fixtures: selection retry, refinement apply, hypothesis contract, canonical unit/Spring integration/shared fixture, constructor call-site updates.
- Frontend runtime: Concept hook/presentation/workspace and Idea hook/form/page.
- Frontend tests: Delta task tracking/failure/duplicate/STALE and Idea dirty/navigation/refresh flows.
- Stage artifacts: this result and `V29_CONCEPT_PORTFOLIO_PRODUCTION_CONTRACT_RECOVERY_USER_VERIFICATION.md`.

## CONTRACTS IMPLEMENTED

- Legal-sensitive hypotheses: `TARGET_REGION`, `REVENUE_MODEL`, `PRICE`, `CHANNELS`, `DIFFERENTIATORS` = nonblank JSON string.
- SOM hypotheses retain exact object fields and range/type validation.
- Legacy list repair applies only to current latest `CHANNELS`/`DIFFERENTIATORS` hypothesis representation.
- Delta Candidate must pass `ConceptCandidateResult.model_validate` before legal adapter invocation.
- Stale Concept UI is gated by authoritative module status; a replacement run reads current `confirmedSnapshotId`.
- Persisted input snapshot hash, TaskRun hash, worker/internal request hash, and AI shared vector use one canonical contract.

## CHECKS ACTUALLY RUN

- AI full pytest: `1089 passed, 0 failed, 8 skipped`.
- Backend `gradlew test`: `742 passed, 0 failed, 2 skipped` (`744` total).
- Backend `gradlew postgresTest`: `15 passed, 0 failed, 0 skipped`.
- Frontend Vitest: `776 passed, 0 failed, 7 skipped` (`783` total).
- Frontend ESLint: PASS, 0 errors.
- Frontend production build: PASS; Vite emitted only the existing chunk-size warning.
- `git diff --check`: PASS after implementation and repeated after stage documentation.

The first resource-contended parallel frontend full run had one timeout in `AuthProjectFlow`; its isolated rerun passed `16/16`, and the final standalone full run passed `776/776` executed tests. The first AI full run found three new-contract regressions; they were fixed before the final `1089/1089` executed pass.

## CHECKS INTENTIONALLY OMITTED

- Docker/provider/browser smoke and AWS deployment were not run. The user prohibited autonomous deployment and requested code/test completion first.
- No production database mutation or Project 3 hardcoded repair was performed.

## REMAINING RISKS

- The exact production Spring `ObjectMapper` feature/module state that produced `3ed...` is not reproducible in the local Spring profile; production must verify the dedicated mapper now yields the AI/standalone hash for the real persisted snapshot.
- UI navigation protection covers browser unload and in-app anchor navigation. Programmatic navigation introduced later must preserve the same dirty guard.
- Frontend build retains a non-blocking >500 kB chunk warning unrelated to this repair.

## CONTINUATION

Code and requested regression gates are complete. Continue only with user-approved image build/deploy, then follow the V29 production verification document. Frontend, backend, and AI images all require rebuild because each runtime changed.
