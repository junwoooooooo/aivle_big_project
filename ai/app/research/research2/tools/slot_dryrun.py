# -*- coding: utf-8 -*-
"""드라이런 — **수집 직전까지만 간다.** 슬롯 로드 → 라우팅 → `stat_code` 실재 대조 (판 ⑤ 신설).

    python tools/slot_dryrun.py --tag pilates-05 --from-harness runs/harness/pilates-member
    python tools/slot_dryrun.py --tag beauty-chk --slots data/slots_beauty-noshow.json

**A3 수집 호출 0 · LLM 0회 · 원장 쓰기 0.** KOSIS 메타 조회(무료)만 쓴다.

왜 별도 도구인가: `run.py` 에 `--dry-run` 이 **없다**. 판 ④ 의 「드라이런」은
`--from a4 --source-run <기존원장>`(LLM 0)이었는데, 그 수는 **원본 원장이 이미 있을 때만**
쓸 수 있다. 새 컨셉에는 원장이 없다 — 그래서 수집 전에 볼 수 있는 것만 보는 잎 도구를 짠다.
`run.py` 는 손대지 않는다.

`--from-harness` 는 게이트 **미통과 스냅샷에도** 쓸 수 있다. 하네스는 fail-closed 라
미통과면 `data/slots_*.json` 을 쓰지 않는데, 그렇다고 라우팅을 못 보면 「왜 막혔나」의
절반을 못 본다. 그래서 저장된 LLM 응답을 `slot_harness.wire()` 로 **메모리에서만** 슬롯으로
되살린다 — 스냅샷 파일은 만들지 않는다(동결 스냅샷 오염 금지, 백로그 21).

유리벽: `blocks/` import 0. `schema.Slot` 과 `adapters/kosis` 는 **엔진과 같은 눈으로 봐야
하므로** 그대로 쓴다 — 여기서 따로 구현하면 「탐색기엔 보이는데 수집기엔 안 보인다」가 생긴다.
"""
from __future__ import annotations

import argparse
import dataclasses
import importlib.util
import io
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
for p in (ROOT, os.path.join(ROOT, "adapters"), os.path.join(ROOT, "harness")):
    sys.path.insert(0, p)

import kosis                                             # noqa: E402
import runpath                                           # noqa: E402
import gate as G                                         # noqa: E402
from base import load_env_key                            # noqa: E402
from runlog import load_rules                            # noqa: E402
from schema import Slot                                  # noqa: E402


def _load(p):
    return json.load(io.open(p, encoding="utf-8"))


def _harness_module():
    """`slot_harness.py` 를 파일 경로로 읽는다 — 패키지가 아니라 스크립트라서."""
    spec = importlib.util.spec_from_file_location(
        "slot_harness", os.path.join(ROOT, "harness", "slot_harness.py"))
    m = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(m)
    return m


def load_slots(a) -> tuple[list, dict]:
    """(슬롯 목록, 출처 표시). **어디서 온 슬롯인지를 산출물에 남긴다** — 통과한 스냅샷과
    미통과 초안을 나중에 같은 것으로 읽으면 안 된다."""
    if a.slots:
        d = _load(a.slots if os.path.isabs(a.slots) else os.path.join(ROOT, a.slots))
        return d.get("slots") or [], {"종류": "스냅샷", "경로": a.slots,
                                      "게이트": "통과(스냅샷이 존재한다는 것이 곧 통과다)"}
    d = a.from_harness if os.path.isabs(a.from_harness) else os.path.join(ROOT, a.from_harness)
    raw_path = os.path.join(d, f"llm_raw_{a.attempt}.json")
    if not os.path.exists(raw_path):
        raise DryrunError(f"없다: {raw_path}")
    sh = _harness_module()
    vocab = _load(os.path.join(ROOT, "harness", "vocab.json"))
    slots, _formulas, _notes = sh.wire(_load(raw_path)["data"], vocab)
    gj = os.path.join(d, "gate.json")
    passed = _load(gj).get("passed") if os.path.exists(gj) else None
    return slots, {"종류": "하네스 초안(메모리 복원)", "경로": raw_path,
                   "게이트": ("통과" if passed else "**미통과**"),
                   "_주의": "스냅샷이 아니다. 이 슬롯으로 수집하면 안 된다 — 라우팅을 보기 위한 복원이다"}


def to_slot(d: dict) -> Slot:
    return Slot(slot_id=d.get("slot_id") or "", var_id=d.get("var_id") or "",
                formula_id=d.get("formula_id") or "", claim_type=d.get("claim_type") or "",
                subject=d.get("subject") or "", metric=d.get("metric") or "",
                period=str(d.get("period") or ""), unit=d.get("unit") or "",
                region=d.get("region") or "대한민국",
                subject_code=d.get("subject_code"), stat_code=d.get("stat_code"),
                corp_name=d.get("corp_name"),
                must_contain=d.get("must_contain") or [],
                must_not_contain=d.get("must_not_contain") or [],
                value_range=d.get("value_range"))


def check_extract_hints(d: dict, need_types: set, lo: int) -> list:
    """발췌 검증 배선 (P2, 판 ⑥-0) — **업종 상수가 아니라 슬롯이 실어 온 힌트를 읽는다.**

    예전엔 이 자리에서 볼 것이 통제 어휘의 업종 계량 이름뿐이었다. 그래서 미용실 밖
    업종은 검사할 것이 없었다. 지금은 `_추출_힌트`(컨셉 유래)를 보고, **없으면 없다고 적는다.**
    """
    if d.get("claim_type") not in need_types:
        return []
    hints = [h for h in (d.get("_추출_힌트") or []) if str(h).strip()]
    out = []
    if len(hints) < lo:
        out.append(f"_추출_힌트 {len(hints)}개 (<{lo}) — 발췌가 이 업종의 표현을 모른다")
        return out
    mc = [w for w in (d.get("must_contain") or []) if str(w).strip()]
    # 힌트와 must_contain 이 완전히 어긋나면 **문서를 거르는 말과 값을 찾는 말이 따로 논다.**
    if mc and not any(any(h in w or w in h for h in hints) for w in mc):
        out.append(f"must_contain {mc} 와 _추출_힌트 {hints} 가 겹치지 않는다 — "
                   "거르는 말과 찾는 말이 따로 논다")
    return out


def check_guards(d: dict) -> list:
    """수집 전에 **눈으로 보이는** 가드 결함만 적는다. 판정하지 않는다 — 값이 없으니까.

    「크기 필터로 종류 오류를 거르지 마라」(§4)에 걸리므로 **경고만** 낸다.
    """
    out = []
    vr = d.get("value_range")
    if not vr:
        out.append("value_range 없음 — 자릿수 오류를 못 거른다")
    elif len(vr) == 2:
        lo, hi = vr
        if hi <= lo:
            out.append(f"value_range 상한≤하한 {vr} — 모든 값을 off_slot 으로 격리한다")
        elif lo > 0 and hi / max(lo, 1e-9) < 10:
            out.append(f"value_range 폭이 10배 미만 {vr} — 정답을 좁힐 위험(백로그 14)")
    if not d.get("must_contain"):
        out.append("must_contain 없음 — 슬롯 매칭 겹이 없다")
    for w in (d.get("must_contain") or []):
        if " " in w:
            out.append(f"must_contain '{w}' 에 공백 — 원문 표기와 어긋나면 통과 불가능한 벽이 된다(백로그 35)")
    return out


class DryrunError(RuntimeError):
    """부르는 쪽이 잘못 줬다. 예전에는 `SystemExit` 였고, 그러면 **함수로 부를 수 없다** —
    `BaseException` 이라 서버에서 부르면 워커가 통째로 넘어간다."""


@dataclasses.dataclass
class DryrunOptions:
    """⚠ 필드 이름은 CLI 인자의 `dest` 와 같아야 한다 — `main()` 이 `vars()` 를 붓는다."""

    tag: str
    slots: str = ""
    from_harness: str = ""
    attempt: int = 3
    no_net: bool = False


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--tag", required=True)
    ap.add_argument("--slots", default="", help="스냅샷 경로")
    ap.add_argument("--from-harness", dest="from_harness", default="",
                    help="하네스 산출 디렉터리 (게이트 미통과여도 된다)")
    ap.add_argument("--attempt", type=int, default=3, help="--from-harness 일 때 몇 번째 시도")
    ap.add_argument("--no-net", action="store_true",
                    help="stat_code 실재 대조를 건너뛴다 (라우팅만)")
    a = ap.parse_args()
    try:
        dryrun(DryrunOptions(**vars(a)))
    except DryrunError as bad:
        raise SystemExit(str(bad))          # CLI 에서는 종전과 같이 멈춘다
    return 0


def dryrun(a: DryrunOptions) -> dict:
    """슬롯이 서는지 **무료로** 본다. 수집 호출 0 · LLM 0회 · 원장 쓰기 0.

    ⚠ **판정하지 않는다.** 보이는 것을 적어 돌려주고, 유료 수집을 태울지는 부르는 쪽이
      정한다 — 이 모듈의 `_규칙` 이 그렇게 적혀 있다.
    """
    if not a.slots and not a.from_harness:
        raise DryrunError("--slots 또는 --from-harness 중 하나가 필요하다")

    slots, origin = load_slots(a)
    vocab = _load(os.path.join(ROOT, "harness", "vocab.json"))
    hint_req = ((vocab.get("요구") or {}).get("추출_힌트") or {})
    hint_types = set(hint_req.get("필수_claim_type") or [])
    hint_min = int(hint_req.get("최소_개수") or 0)
    adapters = load_rules()["adapters"]
    rules = load_rules()
    key = None if a.no_net else load_env_key("KOSIS_API_KEY")
    if not a.no_net and not key:
        # 절대 규칙 5 — 실패는 값이다. 조용히 라우팅만 보고 끝내지 않는다.
        print("KOSIS_API_KEY 없음 → stat_code 대조는 not_configured 로 기록한다")

    report = {"_규칙": "드라이런. 수집 호출 0 · LLM 0회 · 원장 쓰기 0. 판정하지 않고 보이는 것을 적는다.",
              "tag": a.tag, "출처": origin, "슬롯수": len(slots), "슬롯": []}
    routes = {}
    for d in slots:
        route, why = G.route_of(d, adapters)
        routes[route] = routes.get(route, 0) + 1
        row = {"slot_id": d.get("slot_id"), "claim_type": d.get("claim_type"),
               "subject": d.get("subject"), "metric": d.get("metric"),
               "region": d.get("region"), "period": d.get("period"),
               "route": route, "route_why": why,
               "stat_code_기입": d.get("stat_code"), "corp_name": d.get("corp_name"),
               "_추출_힌트": d.get("_추출_힌트") or [],
               "가드_경고": check_guards(d) + check_extract_hints(d, hint_types, hint_min)}
        if route == "kosis":
            if not key:
                row["stat_code_대조"] = "not_configured"
            else:
                code, why2, _ = kosis.resolve_stat_code(to_slot(d), rules, key)
                row["stat_code_대조"] = code
                row["stat_code_사유"] = str(why2)[:300]
        report["슬롯"].append(row)
        print(f"  {row['slot_id']:>4} {route:<5} {str(d.get('metric'))[:12]:<14} "
              f"{str(d.get('subject'))[:22]:<24} " +
              (f"stat={row.get('stat_code_대조')}" if route == "kosis" else "") +
              (f"  ⚠{len(row['가드_경고'])}" if row["가드_경고"] else ""))

    report["경로_분포"] = routes
    report["가드_경고_슬롯수"] = sum(1 for r in report["슬롯"] if r["가드_경고"])
    report["힌트_요구_슬롯"] = {"대상": sum(1 for r in report["슬롯"]
                                        if r["claim_type"] in hint_types),
                              "힌트_있음": sum(1 for r in report["슬롯"]
                                            if r["claim_type"] in hint_types and r["_추출_힌트"])}
    if not a.no_net and key:
        ks = [r for r in report["슬롯"] if r["route"] == "kosis"]
        report["stat_code_해결"] = {"대상": len(ks),
                                    "해결": sum(1 for r in ks if r.get("stat_code_대조"))}
    # 씨앗 `runs/` 는 컨테이너에서 `:ro` 라 여기가 그 자리면 드라이런이 그 자리에서 죽는다.
    out_dir = runpath.write_dir(f"dryrun-{a.tag}")
    os.makedirs(out_dir, exist_ok=True)
    path = os.path.join(out_dir, "dryrun.json")
    io.open(path, "w", encoding="utf-8").write(json.dumps(report, ensure_ascii=False, indent=1))
    print(f"\n경로 분포: {routes} · 가드 경고 슬롯 {report['가드_경고_슬롯수']}/{len(slots)}")
    if report.get("stat_code_해결"):
        print(f"stat_code 해결: {report['stat_code_해결']['해결']}/{report['stat_code_해결']['대상']}")
    print(f"산출: {path}")
    report["_산출"] = path
    return report


if __name__ == "__main__":
    sys.exit(main())
