# V22-B2C2 사용자 검증

1. 완료된 사업 검증에서만 `다듬기 제안 받기`가 표시되고 자동 시작되지 않는지 확인한다.
2. 제안 카드의 현재 값·제안 값·근거를 읽고 원하는 항목만 선택할 수 있는지 확인한다.
3. decision/apply/legal/finalization 상태별 문구와 CTA가 정확하며 LEGAL_BLOCKED에 확정 버튼이 없는지 확인한다.
4. refinement 적용으로 사업 검증이 stale이 되어도 다듬기 영역과 이전 Market/BM 결과가 유지되는지 확인한다.
5. 최종 결과가 `final.value`의 컨셉·7개 가설·BM 계획·실제 변경으로 표시되는지 확인한다.
6. 모바일 폭에서 before/after와 최종 결과가 한 열로 쌓이고 가로 overflow가 없는지 확인한다.

자동 검증:

```powershell
npm test -- --run src/features/business-validation/pages/BusinessValidationPage.test.jsx
npx eslint src/features/business-validation/api/businessValidationApi.js src/features/business-validation/model/refinementView.js src/features/business-validation/components/ConceptRefinementPanel.jsx src/features/business-validation/components/RefinementProposalCard.jsx src/features/business-validation/components/RefinedConceptSummary.jsx src/features/business-validation/pages/BusinessValidationPage.jsx src/features/business-validation/pages/BusinessValidationPage.test.jsx
```

시각 검증: **USER REVIEW PENDING**.
