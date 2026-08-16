# V22-B2C1.1 사용자 검증

1. Final A 이후 Round B가 생겨도 Final A 조회가 exact Round A를 사용하는지 확인한다.
2. Final/Round binding이 어긋나면 `stale=true`인지 확인한다.
3. 유효한 FINALIZING 또는 FINALIZATION_FAILED가 `stale=false`, `value=null`인지 확인한다.
4. Round와 Final이 없으면 `NOT_STARTED`, `stale=false`인지 확인한다.
5. `GET /api/v3/projects/{projectId}/business-validation/refinement/final`이 현재 FinalView를 반환하는지 확인한다.

자동 검증 명령:

```powershell
.\gradlew.bat test --tests "com.aivle.backend.pipeline.refinement.ConceptRefinementFinalizationTests"
```
