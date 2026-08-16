# V22-B2C1 사용자 검증

1. KEEP_CURRENT와 NO_CHANGES finalization이 새 TaskRun/Seed 없이 서로 다른 outcome의 Final을 만드는지 확인한다.
2. BM-only REFINED가 source Seed를 재사용하고 applied BM revision을 Final에 기록하는지 확인한다.
3. overlay/hypothesis 변경이 tagged auxiliary BUILD_HANDOFF 하나를 만들고, 성공 시 새 current Seed와 Final을 원자적으로 생성하는지 확인한다.
4. overlay 또는 hypothesis 결과 mismatch에서 Seed와 Final이 저장되지 않는지 확인한다.
5. tagged 실패에서 Selection 상태가 FAILED로 바뀌지 않고, 명시적 retry가 최대 3회로 제한되는지 확인한다.
6. final Selection/BM revision 또는 final Seed가 바뀌면 immutable Final view가 stale로 계산되는지 확인한다.

자동 검증 명령:

```powershell
.\gradlew.bat test --tests "com.aivle.backend.pipeline.refinement.ConceptRefinementFinalizationTests" --tests "com.aivle.backend.pipeline.conceptportfolio.ConceptPortfolioBuildHandoffMaterializationTests"
```
