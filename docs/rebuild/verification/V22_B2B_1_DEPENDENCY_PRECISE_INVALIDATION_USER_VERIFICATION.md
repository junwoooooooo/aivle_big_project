# V22-B2B.1 사용자 검증

1. BM-only 적용 후 BM revision은 1 증가하고 source Market Seed와 Legal Report는 current여야 한다.
2. overlay-only 적용 후 BM revision은 그대로이고 exact source Market Seed만 stale이어야 한다.
3. BM+overlay 적용 후 BM patch가 반영되고 exact source Market Seed만 stale이어야 한다.
4. BM-only 후 Selection/BM revision이 round의 applied revision과 같으면 refinement round는 current여야 한다.
5. 기존 Business Validation session은 pinned BM revision 차이 때문에 stale로 판정되어야 한다.

Focused 자동 검증 12건이 통과했다.
