# V23-A Market Research Donor Delta Audit 사용자 검증

이번 단계는 분석-only이므로 실행 검증 대상이 없다. 다음 V23-B 착수 전에 아래 계획 경계만 확인한다.

1. Full의 `product_pipeline`, durable ledger/recollect, progress, exact FULL→BM lineage를 donor 코드로 교체하지 않는다.
2. `pipeline.py`, `serialize.py`, `publish.v1.json` wholesale copy를 하지 않는다.
3. V23-B1은 provider 호출을 늘리지 않는 PDF·가정·판정 안전성 교정만 수행한다.
4. V23-B2는 current 90-call budget과 20-minute TaskRun deadline을 명시적으로 재설계하고, 모든 section/re-ask에 총 호출·대기·취소 상한을 둔다.
5. section/passages는 exact 원문 대조, source URL, retrievedAt을 통과한 항목만 기존 evidence shape로 승격한다.
6. donor hardcoded provider/model, unbounded retry, 9-section Markdown report, experimental data/runs는 production에 이식하지 않는다.
7. 새 top-level result field와 Frontend 변경은 V23-B1/B2 범위에서 제외한다.
8. donor known-red business-specific publish rule은 가져오지 않고 generic rule test부터 작성한다.

판정: `READY FOR V23-B1` / provider-backed recall은 `V23-B2`의 별도 budget gate 필요.
