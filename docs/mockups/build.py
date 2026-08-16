# -*- coding: utf-8 -*-
"""목업 데이터 주입기.

    python docs/mockups/build.py

`market-bm.html` 의 `<script id="fixture-data">` 자리에 데이터를 박아 넣는다.
목업을 손으로 고친 뒤 다시 돌려도 된다 — 자리 표시자(`/*__DATA__*/`)가 없으면
기존 데이터 블록을 통째로 갈아 끼운다.

데이터 출처:
  시장조사  ai/tests/fixtures/market_research/full.json   ← 골든 픽스처 그대로
  BM 판정   ai/tests/fixtures/market_research/bm.json     ← 골든 픽스처 그대로
  BM 9칸    docs/mockups/bm_canvas_projected.json         ← **배선 복구 후 예상치**

⚠ 마지막 하나만 실측이 아니다. 지금 봉투는 계획 5칸이 비어 있고(배선 끊김),
  9칸이 다 찬 화면을 확정하려면 고친 뒤의 모양이 필요해서다. 값 자체는 컨셉 파일에
  이미 있는 서술을 옮긴 것이고, 근거 id·경계는 실측 그대로다.
"""
from __future__ import annotations

import io
import json
import os
import re

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
FIXTURES = os.path.join(ROOT, "ai", "tests", "fixtures", "market_research")
PAGE = os.path.join(HERE, "market-bm.html")


def load(path: str) -> dict:
    """`_` 로 시작하는 키는 픽스처의 주석이다 — 화면에 넘기지 않는다."""
    with io.open(path, encoding="utf-8") as fp:
        return {k: v for k, v in json.load(fp).items() if not k.startswith("_")}


def main() -> int:
    full = load(os.path.join(FIXTURES, "full.json"))
    bm = load(os.path.join(FIXTURES, "bm.json"))

    projected = load(os.path.join(HERE, "bm_canvas_projected.json"))
    bm["canvas"] = {"cells": projected["cells"]}

    # 칸이 채워지면서 드러난 것 — 판정 요약도 그에 맞춘다.
    # 「제안가가 관측 밴드 밖」은 칸을 채우기 전에는 보이지 않던 모순이다.
    bm["bm"]["weaknesses"] = [
        "1인 운영 구간 직접 관측 부재",
        "채널 3종이 계획 수준 — CAC·전환율 관측 0건",
        "컨셉 제안가 39,000원이 관측 밴드 15,000~30,000원 밖",
    ]
    bm["bm"]["risks"] = [
        "인접 업종 근거를 해당 업종 관측으로 오독할 위험",
        "예약 플랫폼 제휴가 성립하지 않으면 주 채널과 핵심 파트너가 동시에 무너진다",
    ]
    bm["bm"]["consistencySummary"] = (
        "수익 방식과 가격 밴드가 어긋난다 — 제안가가 관측 밴드 상단을 넘는다."
    )

    blob = json.dumps({"full": full, "bm": bm}, ensure_ascii=False,
                      separators=(",", ":"))

    with io.open(PAGE, encoding="utf-8") as fp:
        page = fp.read()

    marker = re.compile(
        r'(<script id="fixture-data" type="application/json">).*?(</script>)',
        re.S)
    if not marker.search(page):
        raise SystemExit("market-bm.html 에서 fixture-data 블록을 못 찾았다")
    page = marker.sub(lambda m: m.group(1) + blob + m.group(2), page, count=1)

    with io.open(PAGE, "w", encoding="utf-8", newline="\n") as fp:
        fp.write(page)

    cells = projected["cells"]
    filled = sum(1 for c in cells if c["content"])
    print(f"주입 완료 — 근거 {len(full['evidence'])}건 · BM {len(cells)}칸 중 {filled}칸 서술")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
