# V22-A Business Validation Controlled Transplant Foundation 결과

- 기능 상태: **COMPLETE (focused automated verification)**
- 사용자 검토: **USER REVIEW PENDING**
- TARGET: `C:\Users\seewo\Desktop\big_proj_01\aivle_full_transplant\target`
- DONOR: `C:\Users\seewo\Desktop\big_proj_01\aivle_full_transplant\donor-business-validation-refinement`
- TARGET/full SHA: `fba7d3aa6a27afc81d3e6611d0895ff110e34e67`
- DONOR SHA: `4ee74359a1b231359dc3131fb8eecb126462d2bf`
- 시작 시 TARGET/DONOR 작업 트리: clean

## 구현 계약

- canonical UI route는 `/app/projects/:projectId/business-validation`이다. `/market`, `/business-model`은 같은 route로 redirect한다.
- 사용자 시작 명령은 하나이며 Frontend는 Market 완료 뒤 BM을 호출하지 않는다.
- 기존 `MARKET_RESEARCH_FULL` TaskRun/MarketResearchVersion과 `MARKET_RESEARCH_BM` TaskRun/Business Model version을 그대로 사용한다.
- `BusinessValidationSession`은 두 실행의 lineage와 상태만 저장하고 결과 JSON을 복제하지 않는다.
- scheduled durable reconciliation이 exact Market TaskRun 성공을 확인한 뒤 exact MarketResearchVersion을 source로 BM을 시작한다.
- Market 실패 시 BM은 시작하지 않는다. BM 실패 시 Market version/result를 보존하고 BM만 재시도한다.
- current/stale은 session 최신성만 사용하지 않고 current selection revision, Market Seed, 기존 Full/BM stale 계약을 함께 확인한다.
- GET current는 상태나 실행을 생성하지 않는다.

## 상태 흐름

| 상황 | 상태/행동 |
|---|---|
| start | session 생성 + 기존 Market FULL enqueue |
| Market success | Market version 보존 + coordinator가 BM enqueue |
| Market failure | `MARKET_FAILED`, BM 미실행 |
| BM running | Market result 노출 유지 |
| BM success | `COMPLETED`, 두 결과 한 화면 |
| BM failure | `BM_FAILED`, Market result 유지 |
| retry BM | 같은 Market version으로 새 BM TaskRun만 생성 |
| source change | 결과 보존 + `STALE` + 전체 재실행 안내 |

## 변경 파일

- `backend/.../pipeline/businessvalidation/*`: session, repository, coordinator, scheduler, façade API.
- `backend/.../pipeline/market/MarketResearchService.java`: exact Market version BM start와 exact TaskRun current 조회 seam.
- `V28__business_validation_sessions.sql`: 결과 비저장형 orchestration lineage table.
- `frontEnd/src/features/business-validation/*`: 준비, macro progress, Market/BM 통합 결과 화면과 API.
- `MarketResearchPage.jsx`, `BmCanvasPage.jsx`: 기존 Full 결과 renderer를 export해 재사용.
- routing/module/journey files: canonical route와 compatibility redirect, 단일 사업 검증 substep.
- focused backend/frontend tests: 전이, 실패 보존, BM-only retry, stale, route/view states.

## Migration

- 추가: `V28__business_validation_sessions.sql`.
- 이유: 페이지 이탈/서버 재시작 뒤에도 Market→BM continuation을 복구할 durable command lineage가 기존 Full에 없었다.
- 결과 payload는 저장하지 않는다.
- 최고 기존 번호 V27 다음 번호를 사용했다. donor migration 번호는 복사하지 않았다.
- SQL/mapping/번호는 정적 검토했다. Docker/PostgreSQL 실제 upgrade는 지시대로 생략했다.

## 검증

- Backend focused test: 최종 PASS, 6 tests. 명령 호출 5회(초기 sandbox download 실패 2회, compile 수정 1회, mock fixture 수정 1회, 최종 PASS 1회).
- Frontend focused test: 최종 PASS, 5 files / 31 tests. 2회(기존 CTA 기대 문구 정정 후 PASS).
- Changed frontend ESLint: 최종 PASS. 2회(effect scheduling rule 정정 후 PASS).
- Frontend production build: PASS, 1회.
- Backend compile/classes: focused test 과정에서 PASS.
- `git diff --check`: PASS.

## 의도적으로 생략

- 전체 frontend/backend suite, Docker E2E, PostgreSQL migration integration, browser visual, 실제 AI 호출, 장시간 Market 실행.

## 변경하지 않은 범위

- Concept Refinement, Market Interview, donor `research2`, donor structured provider.
- Launch Readiness, Finance, Final Report, ProjectLayout 구조, Work Center/JobEvent/TaskRun 계약.
- Market/BM AI algorithm과 결과 authority.

## 남은 위험과 다음 지점

- 실제 PostgreSQL empty/upgrade migration과 긴 실제 Market→BM 실행은 USER REVIEW/HOLD다.
- 실제 긴 결과의 밀도와 모바일 layout은 USER REVIEW PENDING이다.
- V22-A 경계는 닫혔으며 다음 단계 판정은 **READY FOR V22-B**다. V22-B에서 Refinement/Interview를 자동 포함하지 말고 별도 명시 범위로 시작해야 한다.
