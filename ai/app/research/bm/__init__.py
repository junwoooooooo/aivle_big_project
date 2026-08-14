# -*- coding: utf-8 -*-
"""BM 분석 — 담당자 노트북(`bm_pipeline_v1_final_actual_input.ipynb`)의 이관본.

**정본은 노트북이다.** 이 패키지는 그 셀들을 서버에서 부를 수 있는 모듈로 옮긴 것이고,
계약(스키마·프롬프트·판정 규칙)은 **한 글자도 바꾸지 않는다**. 어긋나면 여기를 고친다.

    셀 6      → contracts.py   스키마
    셀 8      → normalize.py   resolve_bm_input
    셀 10 앞  → prompt.py      BM_ANALYSIS_PROMPT · 허용 출처 라벨
    셀 10 뒤  → analyze.py     run_bm_analysis + 검증기 2종
    셀 12     → finalize.py    finalize_bm_analysis
    셀 14     → flow.py        run_bm_pipeline_flow
    셀 16     → normalize.py   create_bm_analysis_input
    셀 26     → handoff.py     재무 handoff

**옮기지 않은 셀** — 노트북에서만 뜻이 있는 것들:

    셀 2      `%pip install`
    셀 4      `getpass` 대화형 키 입력 → 서버는 환경변수만 읽는다(`analyze.get_client`)
    셀 18·20·22  전역변수 탐색·`display(JSON(...))` → 오케스트레이터가 대신한다
    셀 24     시각화 → **프론트가 대신한다.** 그 셀은 `frontEnd/src/features/market/BmCanvas.jsx`
              의 **명세서로만** 읽는다 (칸 배치·칸별 해설·통과 조건)

⚠ research2 **바깥**에 둔다. research2 는 판 ㉝ 이식 그대로 동결이고, 이쪽은 그 산출물
   (`MarketJoinData`)을 **받는** 층이다. 두 `MarketJoinData` 가 갈라지지 않는지는
   `ai/tests/test_bm_contract_parity.py` 가 단언한다.
"""
