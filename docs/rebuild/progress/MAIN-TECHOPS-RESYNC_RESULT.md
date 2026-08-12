# MAIN-TECHOPS-RESYNC 결과

## 1. 기준선과 작업 범위

- 작업 브랜치: `full`
- 시작 HEAD 및 `origin/full`: `bbd83c791d8f91ff61de9ee25b2cbd18ab983f99`
- donor `origin/main`: `2be5999b677f5ba26ade1c1996975631970c2771`
- 시작 worktree: clean
- 구현 범위: 최신 main의 TechOps Commercialization Advisory 제품 의도를 full의 CPV2, current/stale, TaskRun/TaskAttempt, JobEvent/SSE, ownership 구조에 적응 이식
- 비범위: merge/cherry-pick/commit/push, 실제 `.env`, API key, Twin Bank, Docker 및 실제 provider 호출

## 2. 구현 계약

### 2.1 Canonical source

Advisory 실행은 같은 프로젝트의 다음 exact lineage를 매번 해석한다.

1. current Concept Portfolio Selection
2. 선택 Concept와 동일 run
3. non-stale Market Analysis Seed
4. CURRENT legal handoff가 있으면 exact selection/concept로 연결
5. Seed 및 selection에 연결된 current Market FULL
6. Market FULL version을 source로 하는 current BM
7. 같은 Seed의 finalized TechOps Input Snapshot

실행 입력에는 source ID와 canonical JSON snapshot을 함께 고정한다. 완료 시 source를 다시 해석해 달라졌으면 TaskRun을 `STALE`로 종료하고 결과를 materialize하지 않는다.

### 2.2 AI advisory

- `tech_ops_input_scaler`: Concept/legal, Market, BM, TechOps 사실을 분리한 결정론 ledger 및 균형 잡힌 advisor 입력 생성
- `tech_ops_external_evidence`: Tavily/KOSIS/DART 선택 조회, 짧은 timeout과 fail-open 적용, DART 법인 조회는 opt-in
- `tech_ops_advisor`: decision, 7개 advice, 6개 이상 gates, 5개 이상 operatingCosts, 4개 readiness, pilotPlan, Layer 1/2를 strict typed contract로 생성
- 근거 ID는 실제 Layer 1 fact 또는 Layer 2 evidence ID만 허용
- 1회 bounded repair 후에도 invalid이면 안전 실패
- TechOps 호출에만 provider timeout override 적용
- fake/demo/sample 성공 결과를 만들지 않음

### 2.3 Backend runtime

- `TECH_OPS_ADVISORY` TaskType 추가
- start/current REST와 internal AI execution dispatcher 연결
- Worker claim, lease/deadline, 진행 JobEvent, source 재검증, strict result 검증, TaskRun terminal 처리
- 새 V22 migration으로 canonical advisory report 저장소 추가
- Project ownership, immutable input hash/idempotency, stale/history 의미 유지
- Module Status는 snapshot 이후 Market/BM exact source가 준비되면 Advisory 상태를 표시
- Finance source/readiness에는 TechOps prerequisite를 추가하지 않음

### 2.4 Frontend

- 기존 Phase A 준비·Snapshot UX 유지
- Phase B에 실행/재실행, 상태·실패·stale, SSE 진행, decision/summary를 추가
- 7개 advice, pilot, operating cost, readiness, gates, Layer 1 facts, Layer 2 evidence 링크, 사용자 evidence, disclaimer 표시
- 결과/작업 상태는 Backend DB 및 TaskRun이 canonical이며 localStorage를 사용하지 않음
- 최종 표시 순서: 아이디어, 사업안, 시장 분석, 사업 모델 분석, 기술·운영 분석, 재무 분석, 트윈 패널 조사, 마케팅 콘텐츠 제작
- 내부 route/task/module ID는 유지

### 2.5 공통 비동기 및 배포 보완

- `useJobEvents`: HTTP 404 또는 `JOB_NOT_FOUND` 시 무한 재연결 중단
- 완료 history 선택 시 불필요한 SSE 연결을 열지 않음
- Work Center 실행 중 경과시간을 1초 단위로 갱신
- Nginx: `/assets/` immutable cache, SPA HTML no-cache 분리

## 3. main 역방향 parity

| main 제품 기능 | full 구현 | 판정 |
| --- | --- | --- |
| Advisory decision/summary | strict AI 및 Backend result contract | ADAPTED |
| 7개 advice | exact area 검증 및 TechOps 결과 UI | ADAPTED |
| gates | 최소 개수·basis 검증 및 UI | ADAPTED |
| operatingCosts | 최소 개수·basis 검증 및 UI | ADAPTED |
| readiness 4개 영역 | exact topic 검증 및 UI | ADAPTED |
| pilotPlan | typed contract 및 UI | ADAPTED |
| layer1Facts | canonical source ledger 및 UI | FULL_STRONGER |
| layer2Evidence | 선택 외부 조회, 링크 표시, fail-open | ADAPTED |
| input scaler | deterministic balanced scaler | ADAPTED |
| provider timeout/error | TechOps-only override와 safe failure | FULL_STRONGER |
| rerun/progress | TaskRun·JobEvent·SSE | FULL_STRONGER |
| 결과 persistence | V22 canonical DB report | FULL_STRONGER |
| localStorage persistence | 이식하지 않음 | MAIN_TEMP 제외 |
| direct long advisory HTTP | internal execution/Worker로 변환 | MAIN_TEMP 제외 |
| latest BM/placeholder concept | exact current lineage로 대체 | MAIN_TEMP 제외 |
| fake/sample fallback | 이식하지 않음 | MAIN_TEMP 제외 |
| TechOps→Finance prerequisite | 이식하지 않음 | 금지된 결합 제외 |

- TechOps main observable feature MISSING: **0**
- Navigation MISSING: **0**
- Generic async fix MISSING: **0**
- 제외한 main temporary/demo 항목: **6개 범주**

## 4. 변경 파일 범주

- AI: `ai/app/tasks/tech_ops_advisor`, `tech_ops_input_scaler`, `tech_ops_external_evidence`, executions dispatcher, structured provider
- Backend: TechOps advisory source/service/worker/result contract/entity/repository/API, TaskType/JobEvent/Module Status 연결
- Migration: `V22__tech_ops_advisory_reports.sql`
- Frontend: TechOps API/hook/page/style, module navigation, Finance/Twin/Marketing 번호·CTA, Work Center 및 공통 SSE
- Infra/config: `.env.example`, `compose.yaml`, `frontEnd/nginx.conf`
- Tests: AI advisory, Backend TechOps/result/module/status, Frontend TechOps/navigation/Finance/SSE

## 5. 실제 검증 결과

- Backend 영향 회귀: 40개 테스트 클래스, 147 passed, 0 failed, 0 errors, 0 skipped
- Backend `compileJava`, `compileTestJava`: PASS
- AI advisory/proposal: 2개 파일, 5 passed, 0 failed, 0 skipped
- AI `compileall app tests`: PASS
- Frontend 영향 회귀: 40개 파일, 199 passed, 0 failed, 0 skipped
- 변경 Frontend 파일 ESLint: PASS
- Frontend production build: PASS, 259 modules transformed
- `git diff --check`: 최종 gate에서 확인

Frontend build에는 500 kB 초과 chunk 경고가 있었고 실패는 아니었다. Backend 종료 과정에는 테스트 scheduler 종료 대기 경고가 있었으며 테스트 결과의 실패/오류에는 포함되지 않았다.

## 6. 의도적으로 실행하지 않은 검증

- Docker Compose live 실행
- 실제 PostgreSQL Flyway V22 적용
- OpenAI provider live 호출
- Tavily/KOSIS/DART live 호출
- MinIO live 검증
- 브라우저 실제 사용자 journey

위 항목은 미검증이며 PASS로 판정하지 않는다.

## 7. 보호 영역

- CPV2 core 알고리즘: 변경 없음
- Market/BM 알고리즘: 변경 없음
- Twin 알고리즘/runtime: 변경 없음
- Finance 결정론 계산 및 Monte Carlo: 변경 없음
- Marketing CPV2 source/runtime: 변경 없음
- 기존 migration V1~V21: 변경 없음
- 실제 `.env`, API key, Twin Bank: 열람·변경 없음
- TaskRun/SSE core는 새 TaskType 및 필요한 additive 연결만 변경

## 8. 남은 위험과 이어갈 지점

- 실제 PostgreSQL에 V22를 적용해 FK/index와 기존 데이터 호환성을 확인해야 한다.
- 실제 provider 키가 있는 환경에서 external evidence fail-open 및 TechOps-only timeout을 확인해야 한다.
- 실제 브라우저에서 Phase A→Snapshot→Phase B→SSE→결과→재실행 journey를 확인해야 한다.
- 다음 작업 시작점은 `docs/rebuild/verification/MAIN-TECHOPS-RESYNC_USER_VERIFICATION.md`의 live 검증 절차다.

## 9. 완료 판정

최신 main TechOps observable product feature를 full canonical runtime에 적응 이식했으며 제품 기능 MISSING은 0이다. 임시 bridge와 금지된 TechOps→Finance 결합은 이식하지 않았다.

**MAIN-TECHOPS-RESYNC COMPLETE**
