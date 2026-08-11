# Session 3 사용자 검증 절차

Codex가 수행하지 않은 LIVE/전체 환경 검증만 정리한다. 실패를 성공으로 간주하지 말고 TaskRun, JobEvent, canonical REST 결과를 함께 기록한다.

## 1. 시작 조건

1. branch가 `integration/full-transplant-v1`이고 Session 3 commit이 존재하는지 확인한다.
2. PostgreSQL/MinIO/Backend/AI/Frontend를 사용자 Docker 환경에서 시작한다.
3. `TAVILY_API_KEY`와 AI Provider key는 LIVE 검증을 선택한 경우에만 설정한다.
4. current selected Concept → Market FULL → BM → TechOps Snapshot이 모두 완료된 프로젝트를 사용한다.

## 2. Preparation/upstream

1. Finance 화면에서 Market version, BM version, TechOps Snapshot ID가 실제 current와 일치하는지 확인한다.
2. 전체 근거 상세에서 Market evidence/scorecard와 BM financial handoff/caveat가 보이는지 확인한다.
3. TechOps 상속값은 read-only인지, Market/BM 가정은 확인 가능한 assumption인지, 사용자 입력은 별도 source인지 확인한다.
4. Market/BM/TechOps 중 하나를 current 변경했을 때 기존 Finance가 stale로 표시되는지 확인한다.

## 3. AI Estimate/Tavily

1. `TAVILY_API_KEY` 미설정 상태에서 추천을 실행해 fail-open으로 TaskRun이 정상 완료되는지 확인한다.
2. LIVE key 설정 시 external context가 verified user fact로 표시되지 않는지 확인한다.
3. 추천 받기, 채택, 수정 후 채택, 거절, 다른 추천 요청을 각각 확인한다.
4. JobEvent/SSE 갱신 후 canonical preparation을 다시 읽으며 polling 요청이 발생하지 않는지 확인한다.
5. Provider 실패 시 safe error/retryable 의미가 Work Center와 화면에 일치하는지 확인한다.

## 4. Snapshot/reopen/history

1. 필수 입력을 모두 저장하고 snapshot을 확정한다.
2. snapshot ID/hash와 exact upstream IDs를 기록한다.
3. 입력 수정을 눌러 reopen한 뒤 이전 snapshot row가 삭제되지 않고 `deleted_at` history로 남는지 확인한다.
4. 수정·재확정 후 새 active snapshot이 하나만 존재하고 ID/hash가 변경되는지 확인한다.

## 5. deterministic/Monte Carlo

1. 일회성·구독·혼합 각 1건에서 3개년 P&L, 현금흐름, BEP, payback, 필요 운전자금을 확인한다.
2. P10 ≤ P50 ≤ P90, 손실/회수 확률 0~100, simulation count 2000, seed 20260810을 확인한다.
3. 같은 snapshot으로 재실행했을 때 숫자와 Monte Carlo 결과가 동일한지 확인한다.

## 6. AI Report와 fallback

1. 정상 Provider에서 `FINANCE_ANALYSIS_REPORT` TaskRun, REPORTING/COMPLETED JobEvent, Work Center 항목을 확인한다.
2. 화면의 숫자가 deterministic 결과와 동일하고 findings/cautions/actions만 AI 서술인지 확인한다.
3. Provider를 의도적으로 사용할 수 없게 한 상태에서는 숫자 결과가 유지되고 `SYSTEM_CALCULATION_FALLBACK`, `FAILED`, safe reason이 표시되는지 확인한다.
4. raw provider 응답이나 내부 stack trace가 화면/API에 노출되지 않는지 확인한다.

## 7. UI parity

- upstream source/evidence/caveat
- 모든 입력 section과 assistance action/status
- snapshot/handoff/stale/failure/empty state
- 누적 매출·영업이익·운전자금·BEP KPI
- 3개년 P&L 전체 행
- 월별 매출/영업이익 및 누적현금 차트·상세표
- stress scenario와 scenario chart
- Monte Carlo 7개 정보(P10/P50/P90/loss/payback/count/seed)
- findings/cautions/actions/disclaimer/report source

누락이 하나라도 있으면 READY로 승인하지 않는다.

## 8. 범위 밖

실 Market 조사, 실 Twin Survey 대규모 실행, MOLEG LIVE, Browser 전체 E2E는 별도 사용자 검증으로 수행한다. Persona, Journey 재설계, CPV2 변경은 Session 3 검증에 포함하지 않는다.
