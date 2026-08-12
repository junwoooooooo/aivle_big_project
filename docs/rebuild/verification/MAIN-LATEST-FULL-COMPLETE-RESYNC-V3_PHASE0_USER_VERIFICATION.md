# MAIN-LATEST-FULL-COMPLETE-RESYNC-V3 PHASE 0 사용자 검증

1. `git rev-parse origin/main`이 `ad7304756ba0845d6077a720fa083ac702a33811`인지 확인한다.
2. `git rev-parse origin/full`과 작업 시작 커밋이 `c6682c4d802d38ec61f8067819db086afb70938b`인지 확인한다.
3. PHASE 0 결과 문서 외 제품 코드 변경이 PHASE 0에서 생성되지 않았는지 확인한다.
4. Capability Ledger의 `REGRESSED`, `PARTIAL`, `MISSING` 항목이 이후 PHASE 결과에서 각각 추적되는지 확인한다.
5. main V22 competitor seed가 full V22를 덮어쓰지 않고 다음 additive migration으로 처리되는지 확인한다.
