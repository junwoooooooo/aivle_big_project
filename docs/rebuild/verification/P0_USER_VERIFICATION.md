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

---

## 2026-08-18 Product Polish 사용자 검증

예상 소요: 기존 프로젝트 기준 10~20분. 외부 AI 실행 시간은 provider 상태에 따라 별도다.

### 1. Business Validation

1. `/app/projects/{id}/business-validation`에서 준비 mission과 접힌 `경쟁·대체재 정보`, `사업 운영 정보`를 확인한다.
2. 실행 중 6단계 rail이 실제 session state만 표시하고 동일 heartbeat가 쌓이지 않는지 확인한다.
3. 완료 후 `요약/시장 분석/사업 모델/사업안 다듬기/최종 결과` nav와 Market 내부 nav를 확인한다.
4. 가격·수요 표는 처음 8건만 보이고 `전체 N건 보기`로 펼쳐져야 한다.
5. 가격 KPI는 `관련 가격·비용 관측`, 성장 KPI는 `관측 지표 변화`로 표시되어야 한다.
6. 변경 proposal 0건이면 `변경 제안 없음`과 `현재 사업안으로 확정`만 표시되어야 한다.

### 2. Market Interview

1. `/app/projects/{id}/market-interview`에서 실패 실행을 다시 시도한다.
2. Work Center에서 인터뷰/코딩 count가 stage별 한 줄의 최신 count로 합쳐지는지 확인한다.
3. coding 실패 시 화면은 사용자 설명만 보이고, Work Center `기술 정보`에는 `CODING_EVIDENCE_VALIDATION`, rule, batch path, participant ID만 보여야 한다. 원문·prompt·provider response는 없어야 한다.
4. 새 실행 직후 event endpoint 404가 잠깐 발생해도 오류 화면으로 고정되지 않고 연결되는지 확인한다.

수집할 로그: projectId, TaskRun/attempt ID, failure code/reason, JobEvent의 validationFields. 원문 응답이나 provider raw response는 첨부하지 않는다.

### 3. Marketing Strategy + Content

1. `/app/projects/{id}/marketing`을 연다.
2. `분석 자료 → 마케팅 전략 → 생성 설정 → 결과 확인` 네 단계를 확인한다.
3. Market/BM/재무/인터뷰가 없어도 현재 사업안이 있으면 전략 생성 CTA가 활성화되어야 한다. 없는 자료는 `이번 전략에 포함되지 않음`으로 보여야 한다.
4. 전략 결과에서 target, positioning, core messages, channel audience/actions/KPI, campaign roadmap, budget, risks, evidence detail을 확인한다.
5. PDF를 내려받아 한국어 글꼴과 10개 섹션을 확인한다.
6. `이 전략으로 콘텐츠 만들기` 후 생성 설정을 제출하고 request의 `marketingStrategyReportId`가 current report인지 확인한다.

성공 기준: 전략 생성 `SUCCEEDED`, current/stale 표시 정상, 콘텐츠 생성 request에 전략 ID 포함, 기존 이미지/편집/법률 확인/수정 이력/최종 저장 기능 유지.

### 선택적 focused 재검증 명령

```powershell
.\ai\.venv\Scripts\python.exe -m pytest ai/tests/test_market_interview.py::test_coding_evidence_rule_is_reported_without_answer_text ai/tests/test_market_interview.py::test_invalid_coding_batch_retries_only_that_batch ai/tests/test_marketing_strategy.py -q
Set-Location backend
.\gradlew.bat test --tests "com.aivle.backend.pipeline.marketing.strategy.MarketingStrategyContractTests"
Set-Location ..\frontEnd
npx vitest run src/features/business-validation/pages/BusinessValidationPage.test.jsx src/features/market-interview/pages/MarketInterviewPage.test.jsx src/features/marketing-content/pages/MarketingContentPage.test.jsx src/features/marketing-content/components/MarketingStrategyPanel.test.jsx
```

다음 단계 진행 조건: 위 세 route의 화면 계약 확인 및 실제 전략/인터뷰 TaskRun의 terminal 상태와 current lineage 확인.

---

## 2026-08-18 Runtime Repair / Marketing / Final Proposal 사용자 검증

### 1. Market Interview

1. `/app/projects/7/market-interview`에서 실패 실행 `ab36d2b0-add3-43ac-b9cd-6d209c9b6365`과 같은 입력을 재시도한다.
2. quote의 typography/공백 차이만 있는 경우 완료되며 저장된 evidence quote가 실제 answer의 정확한 substring인지 확인한다.
3. 특정 respondent repair가 실패하면 Work Center 기술 정보에서 `repairAttempts`, `exclusionAttempted`, `exclusionBlockedReason`만 확인한다. 원문/prompt/provider response는 없어야 한다.
4. minimum usable 또는 Target/Comparison coverage가 깨지면 전체 실패하고 명확한 blocked reason이 남아야 한다.

### 2. Marketing

1. `/app/projects/7/marketing`에서 `마케팅 전략`과 `콘텐츠 제작`을 자유롭게 전환한다.
2. 전략을 생성해 channel action/KPI, campaign roadmap, budget, risk, evidence를 확인한다.
3. Strategy가 없어도 콘텐츠 workspace의 `전략 없이 현재 사업안으로 제작`으로 생성할 수 있어야 한다.
4. Strategy가 있으면 `최신 마케팅 전략 적용`을 선택한 요청에만 `marketingStrategyReportId`가 포함되는지 확인한다.
5. 초안 기록은 콘텐츠 workspace에서만 보이고 0건일 때 작은 empty state인지 확인한다.

### 3. Final Business Proposal

1. `/app/projects/7/final-report`에서 필수 기반과 실행된 선택 자료를 확인하고 포함할 source를 선택한다.
2. `사업기획서 만들기`를 누른 뒤 terminal 완료 시 좌측 목차, 중앙 A4 preview, 우측 문서 정보가 표시되는지 확인한다.
3. current Marketing Strategy를 선택했다면 마케팅·시장 진입 section과 source manifest에 포함되는지 확인한다.
4. PDF와 DOCX를 내려받아 표지, 의사결정 요약, 8~10개 section, 부록, 한글 font, 표 wrapping과 page footer를 확인한다.
5. `AI 사업기획서 검토`를 별도로 실행하고 `사업기획서 부록에 포함`을 켠 뒤 내려받은 같은 snapshot 문서에만 검토 의견이 붙는지 확인한다.
6. 다른 project route 이동 시 `/final-report/status`만 호출되고 전체 report JSON은 Final Report 페이지 진입 후에만 요청되는지 확인한다.

### 4. SSE

진행 중 새로고침/route 이동 후에도 TaskRun은 계속 실행되어야 한다. Backend 로그에 `No converter for ApiResponse with preset Content-Type 'text/event-stream'`가 없어야 하며 nested broken pipe는 debug disconnect로 끝나야 한다.

다음 continuation point: project 7의 Market Interview/Marketing Strategy terminal state와 생성된 PDF/DOCX 파일을 확인한다.

---

## 2026-08-18 Retry / Optional Status 사용자 검증

### 1. Market Interview retry 소진

1. `/app/projects/7/market-interview`를 연다.
2. FAILED이며 attempt가 남은 실행에는 `실패한 실행 다시 시도`만 표시되는지 확인한다.
3. attempt 3 소진 실행에는 `현재 사업안으로 새 인터뷰 시작`이 표시되는지 확인한다.
4. 새 실행 요청 body가 이전 `requestedSampleSize`를 사용하고 생성된 run의 attempt가 1인지 확인한다.
5. stale UI에서 `/retry`가 `JOB_RETRY_NOT_ALLOWED`를 반환하면 current를 한 번 재조회하고 재시도 소진 안내와 새 실행 CTA로 전환되는지 확인한다.

### 2. Final Report source와 transaction

1. `/app/projects/7/final-report`를 연다.
2. 선택 자료에 시장 인터뷰, 마케팅 전략, 마케팅 콘텐츠, 기술, 운영, 재무만 표시되고 Twin Survey가 없는지 확인한다.
3. 마케팅 콘텐츠가 COMPLETED이면 `초안 있음 · 검토 전`, 실패한 시장 인터뷰는 `최근 실행 실패 · 포함할 결과 없음`으로 표시되는지 확인한다.
4. concept/BM revision과 무관하게 최신 Technology/Operations DOCX 결과가 `현재 결과 사용 가능`인지 확인한다.
5. 최신 USER_DOCUMENT_INPUT 재무 snapshot과 adopted report가 있으면 재무 분석이 사용 가능한지 확인한다.
6. `사업기획서 만들기`를 누르고 `SELECT FOR NO KEY UPDATE in a read-only transaction` 없이 AI execution으로 진행되는지 확인한다.
7. event 저장에 문제가 생겨도 Proposal/Review 본체 TaskRun이 event 실패만으로 `AI_RESULT_INVALID`가 되지 않는지 확인한다.

### 3. Optional Journey

1. Project Overview와 전체 단계 탐색을 연다.
2. 출시 준비와 최종 보고서에 진행 중/완료/입력 필요 badge가 없는지 확인한다.
3. Technology/Operations/Finance 미실행 또는 실패가 프로젝트 진행률과 전체 상태를 바꾸지 않는지 확인한다.
4. Final Report 생성 중에도 프로젝트 전체 상태가 진행 중으로 바뀌지 않는지 확인한다.
5. Project list 진행률 denominator가 선택 기능을 제외한 `4`인지 확인한다.

### 4. UI

Final Report 페이지에 일부 영역만 덮는 회색 직사각형이 없어야 한다. 앱 기본 surface 위에서 A4 preview만 흰 배경과 shadow를 유지해야 한다.

다음 continuation point: project 7의 새 Interview run ID/attempt와 Final Proposal TaskRun terminal state를 기록한다.

---

## 2026-08-18 Runtime Repair V4 사용자 검증

1. `/app/projects/7/market-interview`: 새 실행 후 한두 respondent의 quote evidence가 invalid여도 해당 theme만 빠지고, 다른 respondent 및 원문 interview가 유지되며 TaskRun이 완료되는지 확인한다.
2. `/app/projects/7/marketing`: `최신 자료로 다시 생성` 클릭 즉시 생성 중 안내와 기존 결과가 함께 보이는지 확인한다. 입력 자료의 `재무 분석`이 한 줄인지 확인하고 `보고서 보기`에서 A4 preview 및 PDF 저장/다운로드의 한글을 확인한다.
3. `/app/projects/7/final-report`: `재무 분석`이 선택 가능하고 inspector에도 한 번만 표시되는지 확인한다. 사업기획서 생성이 input metadata 400 없이 진행하는지, 실패 시 `사업기획서 생성에 실패했습니다` 안내가 남는지 확인한다.
4. `/app/projects`: floating `상태 안내` 버튼이 제거됐는지 확인한다.

Docker image는 이번 작업에서 rebuild하지 않았다. PDF 검증 전 Backend image를 사용자가 rebuild해 `fonts-nanum` 설치를 반영한다.

---

## 2026-08-18 Product Hardening V5 사용자 검증

1. `/app/projects/7/market-interview`: 같은 프로젝트에서 40명 인터뷰를 실행한다. 마지막 batch 일부 row가 누락/invalid여도 이전 32명과 정상 row가 유지되고, 문제 respondent만 단건 복구되어야 한다. 결과 상단은 `유효 인터뷰`, 다양성 영역은 `테마 코딩 완료`로 구분돼야 하며 zero-theme respondent도 원문 탐색기에 남아야 한다.
2. `/app/projects/7/marketing`: `최신 자료로 다시 생성` 직후 `입력 자료 확인 → 전략 작성 → 결과 정리` rail과 기존 전략이 함께 보여야 한다. `보고서 보기`는 button 형태여야 하고 문서의 표지·결재란·목차·표를 확인한다. print preview에 `본문으로 바로가기`와 app chrome이 없어야 하며 별도 `PDF 다운로드` 버튼은 없어야 한다.
3. `/app/projects/7/final-report`: Marketing Strategy가 현재 hash와 같으면 `현재 결과 사용 가능`, 다르면 `업데이트 필요`로 표시돼야 한다. 새 버전 생성 후 Proposal을 즉시 볼 수 있고 AI 검토는 자동 시작되어 완료 후 기본 포함 부록으로 나타나야 한다.
4. Final Report 문서에서 작성자, 문서번호, 결재란, 내부 목차를 확인한다. 좌우 sidebar는 없어야 한다. `PDF 저장`과 `DOCX 다운로드`는 로그인 JSON 화면으로 이동하지 않고 현재 화면에서 authenticated blob으로 다운로드돼야 한다.
5. project 7 최신 Final Proposal failure reason은 이번 로컬 환경에서 DB가 꺼져 확인하지 못했다. 새 버전이 다시 실패하면 status의 `lastErrorCode / lastErrorReason`을 기록해 정확한 후속 수정 입력으로 사용한다.

---

## 2026-08-18 Stability & Main IA Restoration V6 사용자 검증

### 1. 사업 기준값

1. `/app/projects/8/concepts`에서 7개 AI 제안값이 모두 보이면 `7/7 입력 완료`인지 확인한다.
2. 값은 있지만 semantic/legal gate를 통과하지 못한 행은 `확정할 수 없음`과 실제 사유가 표시되는지 확인한다.
3. `기준값 확정`을 누른 뒤 처리 중 표시가 유지되고 SSE terminal 뒤 canonical 상태로 갱신되는지 확인한다.
4. terminal 뒤에도 차단이면 해당 행으로 scroll/focus되고 silent no-op이 아닌 명시적 사유가 보이는지 확인한다.

### 2. 사업 검증과 시장 인터뷰 IA

1. `/app/projects/8/business-validation`에서 `사업 검증 / 다듬어진 사업안`이 별도 내부 화면인지 확인한다. 시장·BM 긴 결과와 refinement가 한 문서로 연속 노출되면 안 된다.
2. `/app/projects/8/market-interview`에서 `보여줄 것 확인 / 인터뷰 실행` 두 단계와 headline-first 결과를 확인한다.
3. 한 respondent coding 복구가 실패해도 전체 TaskRun이 실패하지 않고 `유효 인터뷰 / 테마 코딩 완료 / 코딩 제외`가 서로 다른 실제 수로 표시되는지 확인한다.
4. UNCLASSIFIED respondent가 Respondent Explorer의 원문에는 남고 theme mentionCount에는 포함되지 않는지 확인한다.

### 3. Final Business Proposal

1. `/app/projects/8/final-report`에서 새 버전을 생성한다.
2. 각 section의 `근거 상세 보기`에서 사용자용 label, 확인 내용, source 위치, 기준 시점, 실제 quote/응답자, 한계가 존재하는 범위에서 표시되는지 확인한다.
3. 존재하지 않는 evidence key나 source 밖 수치가 문서에 새로 생기지 않는지 확인한다.
4. PDF 저장에서 `·`가 XML entity 오류 없이 출력되고 `&`, `<`, `>`, 따옴표가 깨지지 않는지 확인한다.
5. DOCX도 별도로 열어 한글, `·`, 표와 근거 상세가 정상인지 확인한다.

이번 로컬 환경에서는 Backend/DB가 꺼져 project 8의 최신 TaskRun 원문을 조회하지 못했다. 실패가 재현되면 `lastTaskRunId / lastErrorCode / lastErrorReason`을 다음 정확한 continuation 입력으로 기록한다.
