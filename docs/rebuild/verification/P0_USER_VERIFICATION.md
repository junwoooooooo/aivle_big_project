# P0 사용자 검증

서비스는 `http://localhost:13000`에서 실행 중이다. 로그인 후 확인한다.

## 1. Launch / TechOps / Finance 분리

1. project 6 또는 사용자가 소유한 프로젝트를 연다.
2. `/app/projects/{projectId}/launch-readiness`로 이동한다.
3. 출시 준비 제목, DOCX 안내, template download, DOCX upload, 분석 시작만 보이는지 확인한다.
4. `재무 분석`, Finance 입력, 별도 `기술 분석`/`운영 분석` 카드, 3-module dashboard가 없는지 확인한다.
5. project 6에는 이미 `launch-input.docx` 결과가 있으므로 완료 결과와 `보고서 보기`, `새 DOCX로 재실행`을 확인한다.
6. `/app/projects/{projectId}/launch-readiness/reports/launch`에서 출시 준비 보고서와 `PDF로 저장`을 확인한다.
7. `/app/projects/{projectId}/technology`와 `/operations`가 `/tech-ops`로 redirect되는지 확인한다.
8. `/app/projects/{projectId}/tech-ops`와 `/finance`가 각각 자신의 입력·실행·결과 surface인지 확인한다.

## 2. 기준값 6/7

1. Concept Portfolio에서 사업안을 선택하고 가설 7개를 연다.
2. PRICE가 AI proposal이지만 아직 canonical 확정 전인 상태를 만든다.
3. 상단에서 `6/7 확인 완료`와 함께 `가격·과금 방식 · AI 제안 · 확인 필요`가 표시되는지 확인한다.
4. `기준값 확정`을 누른다.
5. 값이 validation/legal gate를 통과하면 현재 제안이 그대로 확정되어 7/7이 되는지 확인한다.
6. 통과하지 못하면 PRICE row로 focus/scroll되고 `가격·과금 방식의 AI 제안을 확인해 주세요`와 semantic reason이 표시되는지 확인한다.
7. 금액이 `500,000 KRW · 50만 원`처럼 분리되어 읽히는지 확인한다.

## 3. Market Interview Before

1. `/app/projects/{projectId}/market-interview`로 이동한다.
2. 현재 인터뷰 사업안 이름과 설명이 자전거 대여·관리/AI 카메라/데이터 분석 내용인지 확인한다.
3. `이번 인터뷰에서 확인할 것`에 이해도, 매력 요소, 우려·거부 이유, 기존 대안, 사용 상황, 개선 요구가 표시되는지 확인한다.
4. 20명 빠른 탐색, 40명 패턴 비교, 80명 더 넓은 정성 탐색 설명을 확인한다.
5. organization/B2B 사업안은 개인 profile bank의 직접 타겟이 아니라 `직접 타겟 표현 불가 · 탐색 표본`으로 안내되는지 확인한다.

## 4. Market Interview During

실행 가능한 current Market Seed/BM lineage에서 시작한다.

1. `가상 고객 인터뷰 시작`을 누른다.
2. 사업안 기준 확인 → 타겟 조건 해석 → 패널 후보 탐색 → 패널 구성 → 가상 인터뷰 진행 → 응답 코딩 → 반복 패턴 정리 → 결과 구성 rail이 실제 event에 따라 전이하는지 확인한다.
3. Backend event에 있는 count만 보이는지 확인한다.
4. 새로고침한 뒤 같은 TaskRun 진행 상태가 복원되는지 확인한다.
5. 실패하면 실패 단계 설명과 retry가 보이는지 확인한다.

## 5. Market Interview After

1. 결과에 parking/주차 관리 domain이 섞이지 않고 자전거 대여·관리 사업을 설명하는지 확인한다.
2. organization target에서 개인 전체가 TARGET으로 표시되지 않고 탐색 표본으로 정직하게 표시되는지 확인한다.
3. Theme Explorer의 count가 usable count 분모와 함께 표시되며 구매율로 해석되지 않는지 확인한다.
4. theme의 `응답자 보기`를 누르면 실제 participantId가 연결된 respondent만 표시되는지 확인한다.
5. respondent를 선택해 대표 3개 답변과 `나머지 답변 6개 보기`가 동작하는지 확인한다.
6. quote가 해당 respondent의 원문에 실제 존재하는지 확인한다.
7. Target/Comparison/Proxy/탐색 표본 filter가 존재하는 group만 노출하는지 확인한다.
8. 실제 고객 확인 경고가 각 답변마다 반복되지 않고 respondent/page 수준에서 한 번만 표시되는지 확인한다.
9. 브라우저 console error가 없는지 확인한다.

## 현재 runtime 주의

- project 6의 Launch는 실제 `SUCCEEDED` 결과가 있다.
- project 6의 Concept Portfolio는 두 번 모두 `NO_LEGAL_READY_CANDIDATES`로 terminal 실패했으므로 Business Validation/Interview 연속 E2E는 아직 시작할 수 없다.
- 기존 project 5를 재검증하려면 그 프로젝트 소유자 계정으로 로그인해야 한다.
