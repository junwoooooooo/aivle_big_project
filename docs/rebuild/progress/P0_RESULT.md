# FAST IMPLEMENTATION 결과 — 사업검증·기준값·출시준비·가상인터뷰

## 기준

- start SHA: `729bdb60ee83dc74d57b4323e350f1429b6e42f3`
- branch: `full`
- start HEAD와 `origin/full` 일치 확인
- 기존 변경사항 없음. reset, clean, stash, checkout, revert, commit, push를 실행하지 않았다.

## 구현한 계약

### Business Validation Market

- Research2 dryrun의 `stat_code 해결=0`을 전체 Market의 즉시 HARD FAIL로 사용하지 않는다.
- 슬롯을 `KOSIS_VERIFIED`, `WEB_DIRECT`, `KOSIS_UNRESOLVED_WEB_FALLBACK`, `BLOCKED_NO_ROUTE`와 직접 adapter route로 분류한다.
- 기존 route metric 규칙에 WEB fallback이 있는 KOSIS 슬롯은 collect 단계까지 진행한다.
- 일부 슬롯에 대체 경로가 없으면 해당 직접 시장규모 근거를 생성하지 않고 `MARKET_ROUTE_PARTIAL` degradation을 남긴다.
- 모든 슬롯에 관측 경로가 없을 때만 `MARKET_ROUTE_UNRESOLVED`, HTTP 422, retryable=false로 실패한다.

### 기준값

- `finalValue ?? proposedValue`가 실제로 존재하면 AI 제안도 입력 완료로 계산한다.
- 7개 값이 있으면 `7/7 입력 완료`, 실제 빈 값 하나면 `6/7 입력 완료`로 표시한다.
- `AI 제안 · 확인 필요` 진행 blocker를 제거하고 `AI 제안`, `사용자 입력`, `사용자 수정` 출처 badge만 남겼다.
- `기준값 확정` 한 번으로 현재 화면의 7개 값을 기존 global confirm 계약에 전달한다.
- 실제 빈 값과 semantic/legal 오류만 구체 메시지와 row focus로 막는다.
- 금액 표시는 `500,000 KRW · 50만 원` 형식을 유지한다.

### 출시 준비

- Project Journey에는 `3. 출시 준비` 한 단계만 노출하고 TechOps/Finance child step을 제거했다.
- `/launch-readiness`에 기술 분석, 운영 분석, 재무 분석의 독립 DOCX workflow를 구성했다.
- 각 분석은 template → upload → 독립 실행/event → 결과 → 보고서 흐름이며 서로 prerequisite가 아니다.
- 새 `LAUNCH_READINESS/LAUNCH` 단일 분석은 canonical 사용자 surface에서 제거했다. Backend 참조는 compile 안전을 위해 삭제하지 않았다.
- `/technology`, `/operations` 호환 경로는 같은 Launch 화면의 해당 카드로 연결하며 `/tech-ops`, `/finance` 내부 route는 유지한다.
- 기술·운영·재무 및 통합 보고서 route를 다시 연결했다.

### Market Interview UX

- Before에 Research Mission hero, 사업안과 target representability, 6개 조사 목적, 20/40/80 선택 카드, 5단계 실행 흐름을 표시한다.
- During에 실제 event stage 8단계 rail, 현재 단계 문구와 실제 event count만 표시한다.
- After에 Result Insight Workspace, deterministic insight, Theme Explorer, Respondent master-detail, theme → respondent → original answer traceability를 표시한다.
- UI에서 새 LLM 호출, 없는 요약·확률·count·quote를 만들지 않는다.

### SSE

- 현재 source of truth에 이미 client abort/committed async response 전용 void handler와 emitter cleanup이 구현되어 있음을 확인했다.
- Broken pipe는 TaskRun 실패로 전환하지 않으며 JSON ApiResponse를 `text/event-stream`에 쓰지 않는다.
- 이번 diff에서는 중복 수정하지 않았다.

## 변경 파일

- AI: `ai/app/research/pipeline.py`, `product_pipeline.py`, `runner.py`, focused test.
- Backend: `InternalAiExecutionClient.java`.
- Frontend: Journey/module/router, 기준값 model/workspace/style/tests, Launch page/report/model/tests, Market Interview page/result/style/tests.
- Stage artifacts: 이 결과 문서와 `docs/rebuild/verification/P0_USER_VERIFICATION.md`.

정확한 목록은 `git status --short`를 기준으로 한다.

## 실제로 실행한 확인

- AI changed files `python -m py_compile ...`: PASS.
- AI pytest: 로컬 Python에 pytest/httpx가 없어 실행 불가. 설치나 Docker rebuild는 FAST 지시상 수행하지 않았다.
- Frontend 기준값 + Interview focused: 4 files, 66 tests PASS.
- Frontend Launch + Journey focused: 7 files, 66 tests PASS.
- 변경 Frontend 파일 ESLint: PASS.
- Backend focused test: 로컬 Gradle distribution은 실행됐으나 Spring Boot plugin artifact가 캐시에 없어 dependency resolution에서 중단. 다운로드 재시도는 하지 않았다.
- `git diff --check`: PASS. LF→CRLF 안내만 존재.

## 의도적으로 생략

전체 AI/Backend/Frontend test, baseline, Docker rebuild, provider call, runtime E2E, browser automation, 새 프로젝트 생성, production build.

## 남은 위험과 continuation point

1. 사용자가 현재 환경에서 같은 Business Validation 입력을 재실행해 KOSIS unresolved 슬롯이 WEB fallback으로 넘어가고 Market 결과가 degradation과 함께 완료되는지 확인한다.
2. Backend dependency cache가 준비된 환경에서 SSE focused tests만 재실행한다.
3. 인증 후 Launch 세 카드와 Interview Before/During/After를 실제 화면에서 확인한다.
