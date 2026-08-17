# FAST IMPLEMENTATION 사용자 검증

## 1. 기준값

1. 사업안의 시장 분석 기준값 화면을 연다.
2. 7개 항목에 AI 제안값이 모두 있으면 `7/7 입력 완료`인지 확인한다.
3. 각 항목에는 `AI 제안`, `사용자 입력`, `사용자 수정` 출처만 보이고 `AI 제안 · 확인 필요`는 없어야 한다.
4. `기준값 확정`을 한 번 누른다.
5. 실제 빈 값이 있을 때만 `6/7 입력 완료`, `값이 필요한 항목`과 해당 row focus가 나타나는지 확인한다.
6. 금액이 `500,000 KRW · 50만 원`처럼 분리되는지 확인한다.

## 2. Business Validation

1. 같은 B2B 사업안으로 Business Validation을 시작한다.
2. Market Research가 KOSIS stat_code 0건만으로 즉시 실패하지 않는지 확인한다.
3. WEB 수집 가능한 슬롯은 계속 수집되는지 확인한다.
4. 직접 시장규모 근거가 없으면 TAM/SAM 숫자를 생성하지 않고 degradation이 표시되는지 확인한다.
5. 관측 경로가 전혀 없을 때만 `MARKET_ROUTE_UNRESOLVED` non-retryable 오류인지 확인한다.

## 3. 출시 준비

1. `/app/projects/{projectId}/launch-readiness`로 이동한다.
2. Journey에는 `3. 출시 준비` 한 단계만 있고 기술·운영·재무 child step이 없는지 확인한다.
3. 한 화면에서 기술 분석, 운영 분석, 재무 분석 카드가 각각 보이는지 확인한다.
4. 각 카드에서 template download → DOCX upload → 분석 → 결과 → 보고서를 독립적으로 실행한다.
5. 한 카드의 실패가 다른 카드의 upload/start를 막지 않는지 확인한다.
6. `/technology`, `/operations`는 같은 Launch 화면의 해당 카드로 열리는지 확인한다.
7. `/tech-ops`, `/finance` 내부 호환 route도 유지되는지 확인한다.
8. 화면에 별도 `출시 준비 분석` 단일 DOCX workflow가 canonical 업무로 나타나지 않아야 한다.

## 4. Market Interview

1. `/app/projects/{projectId}/market-interview`로 이동한다.
2. Before에서 Research Mission, 현재 사업안, target 표현, 6개 조사 목적과 20/40/80 카드가 보이는지 확인한다.
3. `가상 고객 인터뷰 시작`을 누르고 실제 event에 따라 8단계 rail이 이동하는지 확인한다.
4. event에 없는 count나 percentage가 나타나지 않는지 확인한다.
5. 완료 후 Result Insight Workspace에서 theme을 누른다.
6. 연결된 respondent만 필터링되고 선택한 respondent의 대표 3개 답변과 나머지 답변 펼치기가 동작하는지 확인한다.
7. 화면에 없는 구매확률·전환율·새 quote가 생성되지 않는지 확인한다.

## 5. SSE

진행 화면에서 새로고침하거나 route를 이동한 뒤 Backend 로그를 확인한다. client disconnect는 debug/cleanup으로 끝나고 TaskRun은 계속 실행되어야 하며, `No converter for ApiResponse with preset Content-Type 'text/event-stream'` 오류가 없어야 한다.
