# V22-B2B 사용자 검증

1. DECISION_RECORDED round에 올바른 expected round/decision hash로 apply하면 hypothesis 포함 시 `APPLYING_HYPOTHESES`, local-only면 `APPLIED_PENDING_FINALIZATION`이어야 한다.
2. tagged CONFIRM 완료 전에는 hypothesis/BM revision과 Seed/Legal current 상태가 변하지 않아야 한다.
3. CONFIRM 실패는 `APPLY_FAILED`이며 Selection 기존 status와 모든 product state를 보존해야 한다.
4. CONFIRM 성공은 선택 hypothesis만 적용하고 BM patch의 미선택 plan/constraints를 보존해야 한다.
5. Delta 승인/차단/transport 실패는 각각 `APPLIED_PENDING_FINALIZATION`/`LEGAL_BLOCKED`/`LEGAL_REVIEW_FAILED`여야 한다.
6. legal transport 실패는 명시적 retry endpoint에서만 tagged Delta TaskRun 하나를 다시 생성해야 한다.
7. self-induced Selection/BM revision과 stale source Seed는 post-apply current로 인정하고, 이후 외부 revision 변화는 round를 STALE로 만들어야 한다.
8. 이 단계에서 신규 Market Seed, final narrative, BUILD_HANDOFF, Frontend 변경이 없어야 한다.

V32 실제 PostgreSQL 적용은 환경 검증 PENDING이다.
