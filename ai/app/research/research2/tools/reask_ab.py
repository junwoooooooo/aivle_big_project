# -*- coding: utf-8 -*-
"""재질문(`reask_sections`)이 **실제로 무엇을 더했나** — 산출물로 잰다. LLM 0회 · 0원.

왜 필요한가
-----------
재질문은 벽시계와 지출의 **70%** 를 먹는다(실측 112문서 × 4절 = 448 호출). 그런데 그
값어치를 **끝까지 간 산출물로 잰 적이 한 번도 없다.**

`sections.json` 에 이미 `합침: {read, reask}` 칸이 있지만 **그것만 보면 속는다.**
`merge()` 는 중복 사실을 **일부러 안 지운다** — 같은 값이 양쪽에서 나오면 교차 근거이고
접는 일은 게재 단계 몫이기 때문이다(`reask_sections.py:246`). 그래서 그 숫자가 커진 것이
「새 사실이 늘었다」인지 「같은 사실을 두 번 셌다」인지 **수로는 못 가른다.**

무엇을 하나
-----------
합쳐진 원장에는 **도장이 찍혀 있다** — 재질문이 만든 행에만 `물은_절` 이 있다
(`reask_sections.py:111`). 읽기가 만든 행에는 없다. 그래서 도로 가를 수 있다.

    <원장>-ab-read   읽기만          (`물은_절` 이 있는 행을 뺀다)
    <원장>-ab-both   읽기 + 재질문   (원본 그대로)

두 벌을 각각 **RESCORE** 로 돌린다. RESCORE 는 절 캐시를 읽고(`pipeline.py:610`) 요약과
9절을 건너뛰므로(`:746` · `:725`) **LLM 을 0회 부른다.** 나오는 것은 인용 수가 아니라
**성적표 · 절별 근거 · 판단 · 처방** — 사업가가 실제로 받는 물건이다.

⚠ **원본 원장은 건드리지 않는다.** 복사본 둘을 새로 만든다(원장 1벌 ≈ 8MB).
⚠ RESCORE 라서 **요약과 9절은 양쪽 다 안 나온다.** 그 둘은 비교 대상이 아니다.
⚠ 이 도구가 답하는 것은 **「지금 모델에서 재질문이 값어치가 있나」** 하나다.
   「luna 읽기가 mini 읽기보다 나은가」는 답하지 않는다 — 이 판이 예산·모델·PDF 를
   한꺼번에 바꿔서 그건 이 원장으로 못 가른다.

쓰는 법
-------
    docker compose exec ai-server python app/research/research2/tools/reask_ab.py <원장id>
"""
from __future__ import annotations

import argparse, asyncio, io, json, os, shutil, sys

HERE = os.path.dirname(os.path.abspath(__file__))
RESEARCH2 = os.path.dirname(HERE)
AI_ROOT = os.path.dirname(os.path.dirname(os.path.dirname(RESEARCH2)))
for _dir in (RESEARCH2, AI_ROOT):
    if _dir not in sys.path:
        sys.path.insert(0, _dir)

import runpath                                                     # noqa: E402


def _load_sections(source_run: str) -> dict:
    path = os.path.join(runpath.read_dir(source_run), "sections.json")
    if not os.path.isfile(path):
        raise SystemExit(
            f"절 원장이 없다: {path}\n"
            "  → 아직 절 체인이 안 돈 원장이다. 실행이 끝난 뒤에 다시 부른다.")
    return json.load(io.open(path, encoding="utf-8"))


def _rebuild(base: dict, rows: list) -> dict:
    """행 목록으로 집계 칸을 **다시 센다.** `merge()` 와 같은 셈을 쓴다.

    ⚠ 같은 셈을 두 벌로 적으면 갈린다. 여기 바꿀 일이 생기면 `reask_sections.merge()` 를
      **같이** 본다 — 그쪽이 정본이다.
    """
    사실 = [it for r in rows for it in (r.get("items") or [])]
    ok = [it for it in 사실 if it.get("채택")]
    per: dict = {}
    for it in ok:
        per[it["section"]] = per.get(it["section"], 0) + 1
    out = {**base,
           "문서": len({r.get("trace_id") for r in rows}),
           "보낸_글자": sum(int(r.get("보낸_글자") or 0) for r in rows),
           "인용_총": len(사실), "인용_채택": len(ok), "절별": per, "문서별": rows}
    out.pop("합침", None)
    return out


def _split(sections: dict) -> tuple[dict, dict, int, int]:
    """`물은_절` 도장으로 읽기/재질문을 가른다."""
    행 = list(sections.get("문서별") or [])
    읽기 = [r for r in 행 if not r.get("물은_절")]
    재질문 = [r for r in 행 if r.get("물은_절")]
    return _rebuild(sections, 읽기), sections, len(읽기), len(재질문)


def _clone(source_run: str, new_id: str, sections: dict) -> str:
    """원장을 복사하고 절 원장만 갈아 끼운다. **원본은 안 건드린다.**"""
    src = runpath.read_dir(source_run)
    dst = os.path.join(runpath.GENERATED_RUNS_DIR, new_id)
    if os.path.isdir(dst):
        shutil.rmtree(dst)
    # `a3_bodies.json`(7MB)은 재채점이 안 읽는다 — 빼면 복사가 빨라지지만, 빼도 되는지
    # 확신할 수 없어 통째로 복사한다. 8MB 두 벌이면 손해가 아니다.
    shutil.copytree(src, dst)
    io.open(os.path.join(dst, "sections.json"), "w", encoding="utf-8").write(
        json.dumps(sections, ensure_ascii=False, indent=1))
    return new_id


def _concept_id_of(source_run: str) -> str:
    result = json.load(io.open(
        os.path.join(runpath.read_dir(source_run), "result.json"), encoding="utf-8"))
    cid = ((result.get("input") or {}).get("concept") or {}).get("concept_id")
    if not cid:
        raise SystemExit(f"원장 {source_run} 에 concept_id 가 없어 재채점을 못 건다")
    return str(cid)


def _rescore(run_id: str, concept_id: str) -> dict:
    from app.research.pipeline import run_market_research            # noqa: PLC0415
    return asyncio.run(run_market_research(
        {"mode": "RESCORE", "conceptId": concept_id, "sourceRun": run_id},
        run_id=f"{run_id}-score", timeout_seconds=900))


# ══════════════════════════════════════════════════════════════
# 보고
# ══════════════════════════════════════════════════════════════
def _절별(env: dict) -> dict:
    """**화면이 실제로 그리는 것**으로 센다 — `evidence[].section`.

    ⚠ `scorecard` 로 세지 않는다. 성적표는 절 셋(채널·원가·규제)만 건수를 싣고
      나머지는 슬롯 판정이라 **모집단이 다르다**(`serialize.py:292` 가 그 모순을 적어 뒀다).
      절이 안 붙은 카드는 `assign_sections` 가 붙여 주므로 여기가 유일하게 9절 전부를
      같은 잣대로 센다.
    """
    표: dict = {}
    for it in env.get("evidence") or []:
        key = it.get("section") or "(절 없음)"
        표[key] = 표.get(key, 0) + 1
    return 표


def _등급(env: dict) -> dict:
    """`scorecard` 는 **list** 다 — `{"subject","state","detail"}` 행이 10개."""
    return {row.get("subject"): row.get("state")
            for row in env.get("scorecard") or [] if row.get("subject")}


def _델타(a: int, b: int) -> str:
    d = b - a
    return f"  {d:+d}" if d else "     ·"


def _report(읽기: dict, 합침: dict, 행수: tuple[int, int]) -> None:
    p = print
    p("")
    p("═" * 68)
    p("  재질문이 무엇을 더했나 — 산출물 대조 (LLM 0회)")
    p("═" * 68)
    p(f"  행: 읽기 {행수[0]}건 · 재질문 {행수[1]}건")
    p("")

    ca, cb = _절별(읽기), _절별(합침)
    p("  ── 절별 근거 (화면에 실제로 실리는 것) ────────────────")
    p(f"  {'절':<18}{'읽기만':>8}{'+재질문':>9}{'차이':>8}")
    for key in sorted(set(ca) | set(cb)):
        a, b = int(ca.get(key) or 0), int(cb.get(key) or 0)
        p(f"  {key:<18}{a:>8}{b:>9}{_델타(a, b):>8}")
    p(f"  {'합계':<18}{sum(ca.values()):>8}{sum(cb.values()):>9}"
      f"{_델타(sum(ca.values()), sum(cb.values())):>8}")

    ga, gb = _등급(읽기), _등급(합침)
    바뀐 = [k for k in sorted(set(ga) | set(gb)) if ga.get(k) != gb.get(k)]
    p("")
    p("  ── 성적표 (10과목) ────────────────────────────────────")
    if 바뀐:
        for k in 바뀐:
            p(f"  {k:<18}{str(ga.get(k)):>8} → {gb.get(k)}")
    else:
        p("  ★ 상태가 «하나도» 안 바뀌었다 — 재질문이 성적을 못 올렸다는 뜻이다")

    p("")
    p("  ── 사업가가 받는 물건 ─────────────────────────────────")
    for 이름, 꺼내기 in (
            ("근거 총건수", lambda e: len(e.get("evidence") or [])),
            # `judgment` 는 **가격 판단 하나**다(dict). 절 수가 아니라 «갈래» 수를 센다 —
            # dict 를 그냥 `len` 하면 계약 칸 수(3)를 세어 언제나 3이 나온다.
            ("가격 판단 갈래", lambda e: len((e.get("judgment") or {}).get("lines") or [])),
            ("처방(못 구한 것)", lambda e: len(e.get("prescriptions") or []))):
        a, b = 꺼내기(읽기), 꺼내기(합침)
        p(f"  {이름:<18}{a:>8}{b:>9}{_델타(a, b):>8}")

    p("")
    p("  ── 판정 ───────────────────────────────────────────────")
    총a, 총b = sum(ca.values()), sum(cb.values())
    비율 = (총b - 총a) / 총a * 100 if 총a else 0.0
    빈칸_전 = [k for k in cb if not ca.get(k)]
    p(f"  절 머리 증가율 {비율:+.0f}%")
    if 빈칸_전:
        p(f"  ★ 재질문이 «비어 있던» 절을 채웠다: {', '.join(sorted(빈칸_전))}")
        p("     → 끄면 이 절들이 다시 빈다. 유지한다")
    elif 비율 < 20 and not 바뀐:
        p("  ★ 재질문을 꺼도 된다 — 새로 채운 절이 없고 등급도 안 바뀐다")
        p("     → 벽시계 약 70% · 지출 약 70% 를 그냥 돌려받는다")
    else:
        p("  중간이다 — 절별 표에서 «어느 절이» 늘었는지 보고 그 절만 남긴다")
        p("     (`reask_sections.DEFAULT_SECTIONS` 를 줄인다)")
    p("═" * 68)
    p("")


def main() -> int:
    ap = argparse.ArgumentParser(description="재질문의 값어치를 산출물로 잰다 (LLM 0회)")
    ap.add_argument("source_run", help="합쳐진 절 원장이 있는 실행 id")
    ap.add_argument("--keep", action="store_true",
                    help="복사본 원장 둘을 지우지 않고 남긴다 (기본: 남긴다)")
    a = ap.parse_args()

    sections = _load_sections(a.source_run)
    읽기_sec, 합침_sec, n읽기, n재질문 = _split(sections)
    if not n재질문:
        print("재질문 행이 0건이다 — 이 원장은 재질문이 안 돌았거나 다 실패했다.")
        print("  `degradations` 에서 REASK_SKIPPED / REASK_FAILED 를 확인한다.")
        return 1

    cid = _concept_id_of(a.source_run)
    print(f"컨셉 {cid} · 원장 {a.source_run}")
    print(f"복사본 만드는 중 (각 ≈8MB) …")
    id읽기 = _clone(a.source_run, f"{a.source_run}-ab-read", 읽기_sec)
    id합침 = _clone(a.source_run, f"{a.source_run}-ab-both", 합침_sec)

    print(f"재채점 1/2 — 읽기만 ({id읽기})")
    env읽기 = _rescore(id읽기, cid)
    print(f"재채점 2/2 — 읽기+재질문 ({id합침})")
    env합침 = _rescore(id합침, cid)

    for 이름, env in (("읽기만", env읽기), ("합침", env합침)):
        부른 = sum(int(s.get("llmCalls") or 0) for s in env.get("stages") or [])
        if 부른:
            print(f"⚠ {이름} 재채점이 LLM 을 {부른}회 불렀다 — 0회여야 한다. 원인을 본다")

    _report(env읽기, env합침, (n읽기, n재질문))

    out = os.path.join(runpath.GENERATED_RUNS_DIR, f"{a.source_run}-ab.json")
    io.open(out, "w", encoding="utf-8").write(json.dumps(
        {"source_run": a.source_run, "행": {"읽기": n읽기, "재질문": n재질문},
         "읽기만": env읽기, "합침": env합침}, ensure_ascii=False, indent=1))
    print(f"봉투 두 벌 저장: {out}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
