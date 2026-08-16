# V22-B2A 사용자 검증

1. AWAITING_DECISION CurrentView의 각 proposal에 `proposalKey`, 응답에 `proposalSetHash`가 있어야 한다.
2. valid key를 선택하면 `DECISION_RECORDED`, 빈 선택과 `keepCurrent=true`이면 `KEEP_CURRENT`가 되어야 한다.
3. unknown/tampered key, 과거 set hash, 중복 key, 같은 field의 복수 선택은 decision을 저장하지 않아야 한다.
4. decision JSON에는 exact selected proposal, deterministic declined keys와 `hypotheses`/`bmPlan`/`overlay` plan이 있어야 한다.
5. 같은 idempotency key와 같은 선택은 replay되고, 같은 key의 다른 선택은 conflict여야 한다.
6. source가 달라졌으면 round만 `STALE`이고 decision은 없어야 한다.
7. decision 전후 Selection hypothesis revision, BM revision, source Seed stale 상태가 같고 TaskRun/Delta Legal이 생성되지 않아야 한다.

V31 실제 PostgreSQL 적용은 환경 검증 PENDING이다.
