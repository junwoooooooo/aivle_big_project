# V2-10A — Seed Commitment and Strategy 결과

## 결과

구현 완료. 자유문장에 명시된 선택 Seed 구체값은 AI Interpretation의 reviewable commitment 후보로 분리되며, 사용자 확인 전에는 LOCKED가 아니다. 사용자가 확인하거나 수정 후 확인한 값만 `USER_CONFIRMED + LOCKED`로 승격되고, dedicated `USER_INPUT + LOCKED` 값은 그대로 우선한다. Concept 전략은 Backend deterministic policy가 최소 Seed, 부분 확정, 구체 원안과 복수 확정을 구분한다.

## 변경 파일

- AI: `ai/app/tasks/idea_brief/{models,service,mapper}.py`, `ai/tests/test_idea_brief_schema.py`
- Backend Idea: `IdeaBriefApiModels`, `IdeaBriefController`, `IdeaBriefService`, `IdeaBriefDerivationCommitService`, `IdeaBriefField` 및 표적 테스트
- Backend Concept: `ConceptGenerationStrategyPolicy`, `ConceptFactoryExecutionService` 및 policy 테스트
- Frontend Idea Review: API, hook, model, review component/page/style 및 표적 테스트
- 계약: Master Plan, Product Spec, 본 RESULT와 USER_VERIFICATION

## 구현 계약

- 추출 대상은 optional Seed 10개로 strict 제한했다.
- 후보 metadata는 `AI_DERIVED / USER_TEXT / REVIEWABLE`로 고정하고 원문 evidence quote를 보존한다.
- AI와 Backend가 모두 dedicated LOCKED 충돌 후보를 제거하며 field overwrite를 금지한다.
- Review Action은 `CONFIRM`, `EDIT_AND_CONFIRM`, `RETURN_TO_OPEN`이다.
- 확정 후보는 `USER_CONFIRMED + LOCKED`, OPEN 복귀 후보는 값 없이 `MISSING + OPEN`이다.
- 최소 Seed는 항상 EXPLORE이며, 일부 LOCKED commitment는 REFINE, 구체 문제·사용자·mechanism과 복수의 상업/채널/운영 commitment는 AS_IS다.
- AS_IS Candidate 1 원안 보존 validator와 Concept Factory 5-slot 경계는 유지했다.

## 실제 실행한 검사

- AI Idea Brief targeted pytest: `8 passed`.
- Frontend Idea Review/model targeted Vitest: `2 files, 5 tests passed`.
- Backend 첫 실행은 sandbox의 Gradle 다운로드 차단 후 승인된 재실행으로 compileJava/compileTestJava까지 성공했다. 초기 fixture에 신규 strict field가 없어 3개 테스트가 실패했고 fixture를 보정했다.
- 최종 Backend 직접 관련 재검증: `IdeaBriefDerivationCommitServiceTests`, `ConceptGenerationStrategyPolicyTests` 성공. 최초 묶음에서 compile과 나머지 13개 표적 테스트도 통과했다.
- Frontend targeted ESLint 성공.
- `git diff --check` 성공(LF→CRLF 안내만 존재).

## 의도적으로 생략한 검사

- 전체 Backend/AI/Frontend suite, 전체 postgresTest, Docker/browser/provider smoke, production build.

## 남은 위험

- 실제 Provider의 한국어 원문 commitment 추출 품질은 V2-10G provider smoke 전까지 미승인이다.
- 기존 baseline DB를 적용한 볼륨은 별도 reset이 필요하다.

## 정확한 계속 지점

V2-10B는 기존 deterministic fingerprint를 유지한 채 structured business fingerprint와 ambiguous pair 전용 semantic judge를 Legal 호출 앞에 추가하는 지점에서 시작한다.
