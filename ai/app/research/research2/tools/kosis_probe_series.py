# -*- coding: utf-8 -*-
"""0단계 실재 대조 — **업종을 파라미터로 받는 범용 프로브** (판 ⑤ 신설).

    python tools/kosis_probe_series.py --tag pilates-05 \
        --subject 필라테스 --subject 요가 --metric "사업체 수" \
        --region 대한민국 --region 서울 --period 2024

**LLM 0회 · 읽기 전용 · 원장 쓰기 0.** KOSIS API 는 무료다.

`tools/kosis_probe.py`(판 ④)는 두발 미용업이 **코드에 박혀** 있었다. 같은 일을 다음 업종에서
하려면 파일을 복사해 문자열을 갈아야 했고, 그러면 「탐색기 두 개가 서로 다른 눈을 갖는」
길이 열린다. 그래서 **판정 함수는 그대로 빌려 쓰고 업종만 밖에서 받는다** —
`probe_slot`·`dump_meta` 를 판 ④ 파일에서 import 한다. 고칠 곳이 하나로 남는다.

왜 이 단계가 있는가: 백로그 18 은 **자료가 있다고 가정하고** 설계를 다 세웠다가 원문을
열어 보니 산업 대분류까지만 있어 통째로 기각됐다. **표를 먼저 연다. 추측으로 채우지 않는다.**

산출: `runs/kosis-probe-<tag>/probe_raw.json` — **판정하지 않고 보이는 것을 적는다.**
"""
from __future__ import annotations

import argparse
import io
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
for p in (ROOT, HERE, os.path.join(ROOT, "adapters"), os.path.join(ROOT, "blocks")):
    sys.path.insert(0, p)

from base import load_env_key                            # noqa: E402
from runlog import load_rules                            # noqa: E402
from kosis_probe import probe_slot, dump_meta            # noqa: E402  — 같은 눈을 쓴다


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--tag", required=True, help="산출 디렉터리 꼬리표. 예: pilates-05")
    ap.add_argument("--subject", action="append", required=True,
                    help="세는 대상(업종명). 여러 번 줄 수 있다 — 표기가 흔들리는 업종은 후보를 다 준다")
    ap.add_argument("--metric", action="append", default=None,
                    help="계량. 기본 '사업체 수'")
    ap.add_argument("--region", action="append", default=None,
                    help="지역. 기본 대한민국")
    ap.add_argument("--period", default="2024")
    ap.add_argument("--max-tables", type=int, default=10,
                    help="메타를 덤프할 표 개수 상한. 상한으로 자른 것은 보고서에 남긴다")
    a = ap.parse_args()

    metrics = a.metric or ["사업체 수"]
    regions = a.region or ["대한민국"]

    key = load_env_key("KOSIS_API_KEY")
    if not key:
        # 키가 없으면 **가짜로 채우지 않는다**(Mock 없음 — 절대 규칙 5).
        print("KOSIS_API_KEY 없음 — not_configured. 여기서 멈춘다.")
        return 2
    rules = load_rules()
    out_dir = os.path.join(ROOT, "runs", f"kosis-probe-{a.tag}")
    os.makedirs(out_dir, exist_ok=True)

    report = {"_규칙": "0단계 실측. 판정하지 않고 보이는 것을 적는다. LLM 0회.",
              "tag": a.tag, "period": a.period,
              "입력": {"subject": a.subject, "metric": metrics, "region": regions},
              "슬롯탐색": [], "표덤프": []}

    seen = {}
    for subj in a.subject:
        for met in metrics:
            for reg in regions:
                label = f"{subj} · {met} · {reg}"
                print(f"[탐색] {label} …")
                r = probe_slot(label, subj, met, reg, a.period, rules, key)
                report["슬롯탐색"].append(r)
                for c in r["후보"][:4]:
                    org, tbl = c["stat_code"].split("/", 1)
                    seen[(org, tbl)] = c["name"]
                print(f"    후보 {r['후보수']}개 · resolve={r['resolve_stat_code']} · "
                      f"{str(r['resolve_why'])[:110]}")

    tables = list(seen.items())
    # 상한으로 자른 것은 **개수만이 아니라 무엇을 잘랐는지** 남긴다(백로그 26 과 같은 종류).
    if len(tables) > a.max_tables:
        report["_상한_제외"] = [f"{o}/{t} {n}" for (o, t), n in tables[a.max_tables:]]
    for (org, tbl), name in tables[:a.max_tables]:
        print(f"[메타] {org}/{tbl} {name[:50]} …")
        d = dump_meta(org, tbl, rules, key)
        d["표이름"] = name
        report["표덤프"].append(d)

    path = os.path.join(out_dir, "probe_raw.json")
    io.open(path, "w", encoding="utf-8").write(json.dumps(report, ensure_ascii=False, indent=1))
    print(f"\n원자료: {path}")
    print(f"표 {len(report['표덤프'])}개 덤프"
          + (f" (상한으로 {len(report['_상한_제외'])}개 제외 — 목록은 보고서에)"
             if report.get("_상한_제외") else ""))
    return 0


if __name__ == "__main__":
    sys.exit(main())
