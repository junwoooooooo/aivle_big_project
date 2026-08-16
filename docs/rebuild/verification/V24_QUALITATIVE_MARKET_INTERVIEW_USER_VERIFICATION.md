# V24 시장 인터뷰 사용자 검증

## 상태

- TEST EXECUTION DEFERRED — FINAL INTEGRATION GATE
- VISUAL: USER REVIEW PENDING — FINAL INTEGRATION GATE

## Final Integration Gate에서 확인할 항목

1. 현재 확정된 사업안이 있는 프로젝트에서 `/app/projects/{projectId}/market-interview`를 연다.
2. 페이지 진입만으로 작업이 시작되지 않고 `[시장 인터뷰 시작]` 버튼이 보이는지 확인한다.
3. 상단에 “AI 가상 고객 인터뷰이며 실제 고객 조사 결과가 아니다”라는 고지가 항상 보이는지 확인한다.
4. 시작 후 Work Center와 페이지가 “가상 고객 관점 검토 중”으로 표시되고 실제 고객에게 연락한다는 표현이 없는지 확인한다.
5. 성공 결과가 raw JSON이 아니라 가상 참여자, 주요 반응, 우려, 구매/사용 계기, 미충족 요구, 실제 고객에게 확인할 질문으로 보이는지 확인한다.
6. 백분율, 대표성, 구매율 또는 실제 고객 발언처럼 오해할 표현이 없는지 확인한다.
7. 사업안을 변경한 뒤 이전 결과가 `이전 버전 기준`으로 표시되고 `[현재 사업안으로 다시 인터뷰]`를 누르기 전에는 자동 재실행되지 않는지 확인한다.
8. 실패 상태에서만 `[다시 시도]`가 보이고 raw error code가 노출되지 않는지 확인한다.
9. `/app/projects/{projectId}/virtual-interview`가 새 시장 인터뷰 route로 redirect되는지 확인한다.
10. Journey와 Work Center에서 `시장 인터뷰`와 `트윈 패널 조사`가 별도 항목이며 기존 Twin Survey 표본/결과 UX가 유지되는지 확인한다.
11. desktop/mobile에서 participant card와 action group에 가로 overflow가 없는지 확인한다.

## 누적 실행 backlog

- V23 Market Research actual provider quality smoke
- V24 Backend focused tests
- V24 AI focused tests
- V24 Frontend focused tests
- bounded Market Interview real-provider smoke
- Business Validation → Refinement → Market Interview source lineage integration
- desktop/mobile visual verification
