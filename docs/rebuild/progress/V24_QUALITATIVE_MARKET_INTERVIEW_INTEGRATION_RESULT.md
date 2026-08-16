# V24 정성적 시장 인터뷰 통합 결과

## 현재 판정

- IMPLEMENTED
- TEST EXECUTION DEFERRED — FINAL INTEGRATION GATE
- VISUAL: USER REVIEW PENDING — FINAL INTEGRATION GATE

## Authority

- 시작 SHA: `1ee384936babed447a05bd5bde046f0c4ad94050`
- donor 참고 SHA: `4ee74359a1b231359dc3131fb8eecb126462d2bf`
- Full의 TaskRun, JobEvent, canonical input hash, Market Seed/Selection lineage와 shared provider가 runtime authority다.
- donor의 정성 persona, 비선도 질문, objection/theme/follow-up intent만 다시 작성했다. donor의 20/40/80 표본, Twin Bank, provider, persistence, shell은 이식하지 않았다.

## 구현 계약

### Backend

- `V36__market_interview.sql`에 durable `market_interview_runs`를 추가했다.
- `MARKET_INTERVIEW` TaskType, 전용 worker, Work Center route/label, current/start/retry API를 추가했다.
- Run은 exact current V2 Market Seed, Selection ID/revision, BM Plan revision과 canonical input hash에 binding된다.
- Seed, Selection revision 또는 BM revision 변경 시 이전 Run은 삭제하지 않고 `STALE` history로 보존한다.
- late AI result는 TaskRun identity/hash를 검증해 보존하되 current result로 승격하지 않는다.
- start는 `Idempotency-Key`가 필수인 TaskRun 경계를 사용한다. retry는 FAILED, same source, 최대 3회에서만 새 Run으로 가능하다.
- Interview는 Selection/Hypothesis/BM/Market Research/Refinement를 변경하지 않는다.

### AI

- `market-interview-input-v1`과 `market-interview-result-v1`을 추가했다.
- shared `execute_structured_prompt`만 사용하며 direct provider client는 없다.
- 결과는 `synthetic=true`, participants/interviews/themes/objections/unmetNeeds/purchaseTriggers/followUpQuestions/limitations를 강제한다.
- 실제 개인 PII, evidence ID를 붙인 가상 발언, 백분율·구매율·대표성·실제 고객 일반화 표현을 fail-closed한다.
- 원시 Market Research 문서는 입력하지 않고 current Seed의 구조화 concept/hypothesis와 현재 BM plan만 전달한다.

### Frontend

- canonical route `/app/projects/:projectId/market-interview`를 추가했다.
- `/app/projects/:projectId/virtual-interview`는 canonical route로 redirect한다.
- 페이지 상단에 실제 고객 조사가 아니라는 고지를 항상 표시한다.
- NOT_STARTED, RUNNING, SUCCEEDED, FAILED, STALE 상태와 명시적 start/retry/restart CTA를 구현했다.
- 결과는 가상 참여자, 주요 반응, 우려, 구매/사용 계기, 미충족 요구, 실제 고객에게 확인할 질문, 한계로 구조화해 표시한다.
- mutation POST가 애매하게 실패하면 자동 재전송하지 않고 current GET을 한 번만 수행한다.
- Journey에서 시장 인터뷰와 트윈 패널 조사를 별도 단계/route로 분리했다. Twin Survey engine과 정량 계약은 변경하지 않았다.

## 변경 파일 영역

- Backend: `pipeline/marketinterview`, TaskType/worker client/job projection/module status, V36 migration
- AI: `app/tasks/market_interview`, internal execution dispatch/alignment test
- Frontend: `features/market-interview`, routing, Journey/module labels, Work Center messages
- Tests: Backend service/contract, AI contract/service, Frontend page/route/navigation regression

## 작성한 테스트

- Backend: source binding, explicit start, idempotent replay/conflict, success, stale/late result, retry/source change, history preservation, result contract
- AI: schema, synthetic flag, qualitative structure, statistical claim rejection, evidence/synthetic separation, partial/invalid fail-closed
- Frontend: CTA/start/running/success/disclaimer/stale/retry/ambiguity recovery/redirect/Twin separation

## 실제 수행한 확인

- START gate fetch/branch/SHA/status 확인
- 지정 symbol 중심 정적 discovery
- `git status --short`
- `git diff --stat`
- `git diff --check` (최종 종료 시 수행)

## 의도적으로 생략

- Gradle test: 0
- pytest: 0
- Vitest: 0
- production build: 0
- 실제 provider: 0
- Docker: 0
- browser/visual: 0

## 남은 위험과 정확한 계속 지점

- 작성한 Backend/AI/Frontend test와 migration 실행 검증은 Final Integration Gate에서 수행한다.
- 실제 provider 결과 품질과 desktop/mobile 시각 검증도 같은 gate에서 확인한다.
- V23 Market Research provider quality smoke는 deferred 상태를 유지한다.
- 계속 지점: V25 — Twin Survey Alignment. V24 기능 범위는 더 확장하지 않는다.
