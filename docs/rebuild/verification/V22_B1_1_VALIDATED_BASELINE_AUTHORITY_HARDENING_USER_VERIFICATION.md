# V22-B1.1 사용자 검증

1. Candidate와 exact source Seed의 price/channels가 다를 때 proposal material의 현재 값은 Seed 값이어야 한다.
2. `targetUsers`와 `featureSet`은 exact Seed의 `selectedConcept`에서 와야 한다.
3. pinned BM Plan revision이 일치할 때만 BM 4개 camelCase 현재 값이 생성되고, 불일치하면 `MODULE_INPUT_STALE`이며 TaskRun은 생성되지 않아야 한다.
4. 현재 Legal Report와 exact Seed `legalResult`가 달라도 Seed 법률 자료만 사용해야 하며, LEGAL reference는 `allowedLegalRefs`와 exact match해야 한다.
5. AI가 잘못된 `currentValue`를 반환해도 저장 후보에는 authoritative baseline이 들어가고, Backend secondary validation 우회는 거절되어야 한다.
6. 이 단계에서 사업안/BM/가설/법률/Market Seed 변경, Delta Legal 실행, Frontend 변경이 없어야 한다.

Focused 자동 검증은 Backend 17건과 AI 13건 모두 통과했다.
