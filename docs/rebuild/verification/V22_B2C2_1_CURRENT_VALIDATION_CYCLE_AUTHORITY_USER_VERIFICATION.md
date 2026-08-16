# V22-B2C2.1 사용자 검증

1. 같은 session의 완료 Final은 기존대로 다듬어진 컨셉으로 표시되는지 확인한다.
2. 새 Business Validation 완료 후 과거 Final이 있어도 `다듬기 제안 받기`가 표시되는지 확인한다.
3. 새 validation 진행 중 과거 Final이 진행 화면을 덮지 않는지 확인한다.
4. 새 cycle의 PROPOSING/AWAITING_DECISION이 과거 FINALIZED보다 우선하는지 확인한다.
5. self-induced stale에서 같은 session refinement가 유지되는지 확인한다.
6. 과거 stale Final은 현재 cycle 경고에 섞이지 않고, 같은 cycle stale Final만 경고하는지 확인한다.

자동 검증 명령:

```powershell
.\gradlew.bat test --tests "com.aivle.backend.pipeline.businessvalidation.BusinessValidationCoordinatorTests" --tests "com.aivle.backend.pipeline.refinement.ConceptRefinementServiceTests" --tests "com.aivle.backend.pipeline.refinement.ConceptRefinementFinalizationTests"
npm test -- --run src/features/business-validation/pages/BusinessValidationPage.test.jsx
npx eslint src/features/business-validation/model/refinementView.js src/features/business-validation/pages/BusinessValidationPage.jsx src/features/business-validation/pages/BusinessValidationPage.test.jsx
```

시각 검증: **USER REVIEW PENDING**.
