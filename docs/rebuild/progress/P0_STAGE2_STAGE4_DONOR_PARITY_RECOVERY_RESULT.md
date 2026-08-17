# P0 Stage 2·4 Donor Parity Recovery 결과

## Authority

- 시작 HEAD / `origin/full`: `7c2e6116ecb46d1f374a88651812fef46688abfd`
- 제품 authority `origin/merge/main-into-bv`: `598209fedfd6ee6e8f7ae98c56340f1bf1c60efe`
- 보조 비교 `origin/feat/business-validation-refinement`: `4ee74359a1b231359dc3131fb8eecb126462d2bf`
- reset, stash, clean, cherry-pick, commit, push: 수행하지 않음
- 유료 provider 호출: 0회

## Stage 2 — 사업 검증

- top-level Journey는 하나의 `2. 사업 검증`으로 유지했다.
- 내부 계약은 Market → Business Model → Concept Refinement 순서로 projection한다.
- `CONCEPT_REFINEMENT`를 프로젝트 module status에 추가하고 현재 Business Validation session과
  동일한 source session만 현재 refinement로 인정한다.
- Market과 BM이 완료된 현재 세션에는 refinement가 `READY`로 열리며, refinement를 완료하기 전에는
  Stage 2 전체를 완료로 집계하지 않는다.
- 기존 `BusinessValidationCoordinator`의 market version 고정, BM plan revision 고정, project lock,
  idempotency, Market 실패 시 BM 미실행, BM 실패 시 Market 결과 보존과 BM-only retry 계약을 유지했다.
- Business Validation 완료 후 refinement starter가 동일 session과 결정적 idempotency key로 시작하는
  기존 full 비동기 계약을 유지했다.

## MARKET_FAILED 조사 결과

- frontend start → controller → `MarketResearchService.start` → TaskRun → worker →
  `InternalAiExecutionClient` → Research2 → schema/materialization → status projection 경로를 추적했다.
- 20분 실행 budget과 22분 worker/client timeout은 유지되어 있다.
- Compose는 `MARKET_RESEARCH_OPENAI_API_KEY`를 우선하고 `OPENAI_API_KEY`로 fallback한다.
- 현재 환경에는 두 이름 모두 설정되어 있고 secret 값은 출력하지 않았다.
- `OPENAI_BASE_URL`은 OpenAI `/v1` 호환 형태이며 market model은 `gpt-5.4-nano`로 설정되어 있다.
- 공급자 호출 없는 Research2 계약 테스트 50개가 통과했다.
- 현재 로컬에는 실행 중인 backend/AI/PostgreSQL과 실패 TaskRun/log가 없어 실제
  `task_runs.state`, `last_error_code`, backend/AI root exception은 회수하지 못했다.
  따라서 timeout, auth, quota, schema 중 하나로 추정하거나 코드 성공으로 위장하지 않는다.

## Stage 4 — 가상 인터뷰

- Journey의 필수 child를 canonical `marketInterview` 한 개로 축소했다.
- `TWIN_SURVEY`와 `MARKET_INTERVIEW`를 모두 `marketInterview`에 투영한다.
- 현재 Market Interview 실행 증거가 있으면 그것을 우선하고, 없을 때만 legacy Twin Survey 실행을
  canonical slot의 compatibility fallback으로 사용한다.
- raw backend DTO에는 두 module type을 모두 남겨 API, migration, persisted record, Twin 기능을 보존했다.
- ProjectService의 완료 수, 현재 Journey, attention, sidebar/dashboard summary가 같은 canonical 선택을 쓴다.
- `MARKET_INTERVIEW=COMPLETED`, `TWIN_SURVEY=NOT_READY`는 Stage 4 완료이며, legacy Twin-only 완료도
  현재 Market Interview 실행이 없을 때 canonical 완료로 투영된다.

## Verification

- `git diff --check`: PASS
- frontend focused: PASS — 22 files, 208 tests
- backend focused: PASS — 관련 market/business-validation/refinement/module/project/taskrun 계약 테스트
- AI Market provider-free focused: PASS — 50 tests
- frontend baseline: PASS — 685 passed, 기존 명시 허용 실패 6, 신규 실패 0
- backend full test: PASS — Gradle `BUILD SUCCESSFUL`
- AI `tests` full: PASS — 812 passed, 1 skipped
- frontend lint: PASS
- frontend production build: PASS — 기존 유형의 500 kB chunk-size warning 1건
- backend build: PASS — Gradle `BUILD SUCCESSFUL`
- 저장소 루트 `ai` 전체 pytest 수집: 기존 Research2 진단 스크립트의 import-time `SystemExit` 때문에
  수집 단계에서 종료. 정식 `ai/tests` suite는 위와 같이 통과했다.
- browser: NOT RUN — `http://localhost:3000` 연결 거부, 앱 스택 미기동
- real provider: NOT RUN — 유료 provider 호출 금지

## 판정

**IMPLEMENTATION READY FOR MANUAL ACCEPTANCE**

실제 browser와 provider 실행 증거가 생기기 전에는 `MARKET_FAILED`의 운영 root cause 확정 또는
수동 acceptance 완료로 판정하지 않는다.
