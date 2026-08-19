# -*- coding: utf-8 -*-
"""**게이트 정밀도 지표② — 지명 쓰레기 표본**을 현행 게이트에 태운다. **LLM 0회 · 읽기 전용.**

    python tools/garbage_check.py --concept data/concept_hmr-product.json

`data/garbage_sample.json` 14건은 게임회사 증권신고서·자립준비청년 보고서·청년 사회통계에서
온 것으로 **우리 보고서의 «절 머리»에 서서는 안 된다.** 여기서 **절 머리에 서는 것**이
몇 건인지가 지표②이고, **오늘 1건이다. 늘리지 않는다.**

⚠ **판 ㊹ 3단계에서 잣대를 다시 세웠다.** 게재가 「버림」에서 「표시」로 바뀌어, 지명 쓰레기의
정답이 이제 `OFF_TOPIC` 이 아니라 **`밖`**(서랍에 접힘)이다. 옛 잣대를 그대로 뒀으면
**정의상 14/14 통과**가 되어 이 검사가 도장으로 전락했을 것이다.

⚠ 판 ㊶ 재개 전까지 이 표본에는 `section`·`quote`·`table_context` 가 **없었다.** 절을 보는
분기를 한 번도 안 태웠으므로 「1건 유지」는 검사가 아니라 도장이었다. 지금은 원장에서
복원돼 있다(낱건 `복원_출처` 참조).
"""
from __future__ import annotations

import argparse, io, json, os, sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
for p in (ROOT, HERE):
    sys.path.insert(0, p)

import publish_gate as PG
import read_sections as RS


def _발행사(g: dict, V: dict, R: dict) -> dict:
    """낱건의 `복원_출처`(실행/문서)로 **본문을 찾아** 발행사 판정을 낸다 (판 ㊷ R3).

    표본에 판정값을 적어 두지 않는다 — 적으면 그건 잣대에 답을 써 넣는 것이다.
    본문에서 **매번 다시 계산**한다.
    """
    out, 본문 = {}, {}
    for run in sorted({str(it.get("복원_출처") or "").split("/")[0]
                       for it in g["items"] if it.get("복원_출처")}):
        p = os.path.join(ROOT, "runs-generated", run, "publish.json")
        if not os.path.exists(p):
            continue
        src = json.load(io.open(p, encoding="utf-8"))
        for doc in RS._corpus(src["source_run"]):
            for tid in [doc["trace_id"]] + doc["별칭"]:
                본문.setdefault(f"{run}/{tid}", doc["text"][:src["cap"]])
    for k, t in 본문.items():
        out[k] = bool({w for w in V["실명"] if w in t})
    return out


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--concept", default=os.path.join(ROOT, "data", "concept_hmr-product.json"))
    ap.add_argument("--sample", default=os.path.join(ROOT, "data", "garbage_sample.json"))
    a = ap.parse_args()

    R = PG._rules()
    V = PG._vocab(json.load(io.open(a.concept, encoding="utf-8")), R)
    g = json.load(io.open(a.sample, encoding="utf-8"))
    발행사 = _발행사(g, V, R)

    통과, 바뀜 = 0, 0
    print(f"{'주어':<40}{'절':<14}{'오늘':<16}{'지금':<16}")
    for it in g["items"]:
        c, why, _ = PG.분류(it, V, R, it.get("url") or "",
                          발행사.get(str(it.get("복원_출처") or "")))
        옛 = it.get("오늘_게재")
        mark = "" if c == 옛 else "  ← 바뀜"
        if c != 옛:
            바뀜 += 1
        # ★ 판 ㊹ 3단계 — **잣대를 다시 세운다.** 게재가 「버림」에서 「표시」로 바뀌면서
        #   지명 쓰레기는 이제 `OFF_TOPIC` 이 아니라 **`밖`** 을 받는 것이 정답이다.
        #   옛 잣대(「OFF_TOPIC 이 아닌 것을 센다」)를 그대로 두면 **14/14 가 정의상 통과**해
        #   「안 버린다」의 반대편에 선 유일한 방어 장치가 소리 없이 죽는다.
        #   지금 묻는 것은 **「절 머리에 서는가」** 다 — 서랍에 접혀 들어가는 것은 정상이다.
        if c not in (PG.BURIED, PG.DRAWER):
            통과 += 1
        print(f"{str(it['subject'])[:38]:<40}{str(it.get('section') or '-'):<14}{옛:<16}{c:<16}{mark}")
        if c not in (PG.BURIED, PG.DRAWER) or mark:
            print(f"{'':<40}사유: {why}")

    print(f"\n지표② 통과 {통과}건 / 14  (기대 ≤1)")
    print(f"기록된 기대값과 다른 것 {바뀜}건")
    return 0 if 통과 <= 1 else 1


if __name__ == "__main__":
    sys.exit(main())
