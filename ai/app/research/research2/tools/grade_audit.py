# -*- coding: utf-8 -*-
"""등급 축 정적 감사 — **공존 봉쇄** (판 ㉙ S3). LLM 0회 · 원장 쓰기 0회.

    python tools/grade_audit.py                 # 정적 감사만
    python tools/grade_audit.py --run <id>      # + 그 원장의 두 축 모순 검사

왜 있는가: 판 ㉙ 은 `label`(옛 축)을 **살려 둔 채** `채택`·`등급`(새 축)을 옆에 세운다.
공존은 편하지만 위험하다 — **두 축이 서로 다른 말을 하는 자리**가 생기면 그것이 곧
「낮은 등급의 높은 표기」로 새는 구멍이다. 그래서 공존 기간 내내 두 가지를 강제한다:

  ① **정적** — `"확인됨"` 리터럴을 비교하는 **모든** 소스 위치가 `fill.v2.소비자_배선` 에
     등록돼 있어야 한다. 새 소비자가 등록 없이 생기면 **실패**. 등록표는 곧
     **마이그레이션 단위**이자 「아직 옛 축을 읽는 곳이 어디인가」의 정본이다.
  ② **동적** — 한 원장 안에서 두 축이 **모순값**이면 실패.
     (`채택=True` 인데 `label ∈ {미검증, off_slot}` / `등급=확정` 인데 `채택=False` 등)

⚠ **공존은 한시적이다.** 전환이 끝나면 겸용 라벨(`확인됨`)의 **등급 의미를 폐기**한다.
   남은 곳은 **다음 판 이내** 완료 조건이며, 그 수를 이 도구가 세어 준다.
"""
from __future__ import annotations

import argparse
import ast
import io
import re
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)

#: 감사에서 제외하는 디렉터리. **소스 층만** 본다 — 산출물·기록·데이터는 감사 대상이 아니다.
SKIP_DIRS = {"__pycache__", "runs", "outputs", ".git", "data", "prompts", "tools", "tests"}

#: 옛 축 라벨 리터럴. 이 문자열을 **비교에 쓰는 것**이 「옛 축을 읽는다」는 뜻이다.
OLD_AXIS_LITERAL = "확인됨"


def _pins() -> dict:
    with io.open(os.path.join(ROOT, "rules", "rule_pins.json"), encoding="utf-8") as f:
        return json.load(f)["pins"]


def _fill() -> dict:
    with io.open(os.path.join(ROOT, "rules", _pins()["fill"]), encoding="utf-8") as f:
        return json.load(f)


def scan_sites() -> list[dict]:
    """소스 전수에서 옛 축 리터럴을 쓰는 위치를 `모듈.함수` 로 센다."""
    sites = []
    for root, dirs, files in os.walk(ROOT):
        dirs[:] = [d for d in dirs if d not in SKIP_DIRS]
        for fn in sorted(files):
            if not fn.endswith(".py"):
                continue
            path = os.path.join(root, fn)
            rel = os.path.relpath(path, ROOT).replace("\\", "/")
            src = io.open(path, encoding="utf-8").read()
            try:
                tree = ast.parse(src)
            except SyntaxError:
                continue
            mod = os.path.splitext(fn)[0]
            found: list[dict] = []

            class V(ast.NodeVisitor):
                def __init__(self):
                    self.cur: list[str] = []

                def _scoped(self, n):
                    self.cur.append(n.name)
                    self.generic_visit(n)
                    self.cur.pop()

                visit_FunctionDef = _scoped
                visit_AsyncFunctionDef = _scoped
                visit_ClassDef = _scoped

                def visit_Constant(self, n):
                    if isinstance(n.value, str) and n.value == OLD_AXIS_LITERAL:
                        fq = f"{mod}.{'.'.join(self.cur)}" if self.cur else f"{mod}.<module>"
                        found.append({"id": fq, "file": rel, "line": n.lineno})

            V().visit(tree)
            sites += found
    return sites


#: 축 판정의 **구현체**. 리터럴을 여기에 한 번 두는 것이 이 마이그레이션의 목적이므로
#  감사 대상에서 뺀다 — 빼지 않으면 「단일 원천으로 모으기」가 곧 감사 실패가 된다.
IMPL_MODULE = "fillaxis"

#: `_fx.filled(row, "<소비자 id>")` 호출. **전환된 소비자는 리터럴 대신 이 자리를 갖는다.**
CALL = re.compile(r"""filled\(\s*[^,]+,\s*["']([^"']+)["']\s*\)""")


def scan_calls() -> dict:
    """토글을 **경유하는** 소비자. 전환 완료의 증거다."""
    out: dict = {}
    for root, dirs, files in os.walk(ROOT):
        dirs[:] = [d for d in dirs if d not in SKIP_DIRS]
        for fn in sorted(files):
            if not fn.endswith(".py") or fn == IMPL_MODULE + ".py":
                continue
            path = os.path.join(root, fn)
            rel = os.path.relpath(path, ROOT).replace("\\", "/")
            for i, line in enumerate(io.open(path, encoding="utf-8"), 1):
                for m in CALL.finditer(line):
                    out.setdefault(m.group(1), []).append(f"{rel}:{i}")
    return out


def audit_static(fill: dict) -> tuple[list[str], dict, dict]:
    """등록 안 된 소비자를 찾는다. **미등록 = 실패** (조건 ⓐ).

    등록된 소비자는 **둘 중 정확히 하나**의 모습이어야 한다:
      · 리터럴 보유 — 아직 옛 축을 직접 읽는다(미전환)
      · 토글 경유 — `_fx.filled(row, "id")` 로 축을 물어본다(전환 완료)
    둘 다 아니면 **표가 코드보다 오래된 것**이고, 그 상태로는 「남은 전환 대상」 수가 거짓이 된다.
    """
    배선 = (fill.get("소비자_배선") or {}).get("배선") or {}
    fails, seen, calls = [], {}, scan_calls()
    for s in scan_sites():
        if s["id"].startswith(IMPL_MODULE + "."):
            continue                      # 구현체 자신은 감사 대상이 아니다
        seen.setdefault(s["id"], []).append(f"{s['file']}:{s['line']}")
        if s["id"] not in 배선:
            fails.append(f"미등록 소비자 {s['id']} ({s['file']}:{s['line']}) — "
                         f"fill.v2.소비자_배선 에 축을 적어야 한다")
    for cid in calls:
        if cid not in 배선:
            fails.append(f"미등록 소비자 {cid} ({calls[cid][0]}) — 토글을 부르는데 표에 없다")
    for k, ax in 배선.items():
        if k not in seen and k not in calls:
            fails.append(f"유령 등록 {k} — 리터럴도 토글 호출도 없다. 표가 코드보다 오래됐다")
        if k in calls and k in seen:
            fails.append(f"이중 경로 {k} — 토글을 부르면서 리터럴도 남았다. "
                         f"두 축이 한 자리에서 갈린다")
    return fails, seen, calls


#: 두 축이 동시에 참일 수 없는 조합. **모순은 실패**다.
CONTRADICTIONS = [
    ("채택=True 인데 label 이 격리다",
     lambda r: r.get("채택") and r.get("label") in ("미검증", "off_slot")),
    ("등급=확정 인데 채택 불가다",
     lambda r: r.get("등급") == "확정" and not r.get("채택")
     and "인용 대조 실패" in (r.get("채택_불가_사유") or [])),
    ("채택=True 인데 채택_불가_사유가 남아 있다",
     lambda r: r.get("채택") and (r.get("채택_불가_사유") or [])),
    ("채택=False 인데 사유가 비었다 — 왜 못 채우는지가 값으로 없다",
     lambda r: (not r.get("채택")) and not (r.get("채택_불가_사유") or [])),
]


def audit_run(run_id: str) -> tuple[list[str], dict]:
    rows = []
    p = os.path.join(ROOT, "runs", run_id, "run.jsonl")
    for ln in io.open(p, encoding="utf-8"):
        o = json.loads(ln)
        if o.get("node") == "a4_ledger":
            pl = o["payload"]
            rows += pl.get("rows") or ([pl] if "label" in pl else [])
    fails = []
    for name, bad in CONTRADICTIONS:
        hit = [r["fact_id"] for r in rows if bad(r)]
        if hit:
            fails.append(f"두 축 모순 — {name}: {len(hit)}건 {hit[:6]}")
    dist = {"rows": len(rows),
            "채택": sum(1 for r in rows if r.get("채택")),
            "등급": {g: sum(1 for r in rows if r.get("등급") == g)
                    for g in ("확정", "실무 신뢰", "추정")}}
    return fails, dist


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--run", default="")
    a = ap.parse_args()

    fill = _fill()
    fails, seen, calls = audit_static(fill)
    배선 = (fill.get("소비자_배선") or {}).get("배선") or {}
    판정 = {k: v for k, v in 배선.items() if v != "비판정"}
    옛 = [k for k, v in 판정.items() if v == "옛"]

    print(f"[정적] 판정 소비자 {len(판정)} · 토글 경유 {len(calls)} · "
          f"리터럴 잔존 {len(seen)} · **남은 전환 대상 {len(옛)}**")
    for k in sorted(배선):
        how = "토글" if k in calls else ("리터럴" if k in seen else "?")
        where = ",".join((calls.get(k) or seen.get(k) or ["-"])[:2])
        print(f"    {배선[k]:<6} {how:<5} {k:<32} {where}")

    if a.run:
        rf, dist = audit_run(a.run)
        fails += rf
        print(f"\n[동적] {a.run} — rows {dist['rows']} · 채택 {dist['채택']} · 등급 {dist['등급']}")

    if fails:
        print("\n실패:")
        for f in fails:
            print("  X  " + f)
        return 1
    print("\n통과 — 미등록 0 · 두 축 모순 0")
    return 0


if __name__ == "__main__":
    sys.exit(main())
