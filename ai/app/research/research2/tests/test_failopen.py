# -*- coding: utf-8 -*-
"""판 ⑦ H3 — **fail-open 마감** 검사. LLM 0회.

강제 실패 시나리오: **게이트를 통과할 수 없는 가짜 컨셉·가짜 초안**을 하네스에 넣고
  ① 사람을 부르지 않는가 (종료 0 · 「사람 판단이 필요하다」 문구 없음)
  ② 실패가 **구조화되어** 남는가 (`harness_failure.json` 필수 필드)
  ③ 스냅샷을 **쓰지 않는가** (게이트 못 넘은 슬롯으로 수집하지 않는다)
  ④ canvas 가 **실패 표시를 달고 출력**되는가 (9칸 · audit 6/6 · 머리말)
를 본다.

⚠ **조용히 넘어가면 실패다.** fail-open 은 「멈추지 않는다」와 「값으로 남긴다」를 **둘 다**
요구한다 — 하나만 하면 그건 조용한 실패이고, 이 프로젝트가 가장 오래 싸운 것이다.
"""
from __future__ import annotations

import io
import json
import os
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
sys.path.insert(0, ROOT)
sys.path.insert(0, os.path.join(ROOT, "service"))

import canvas as C                                          # noqa: E402

ok, fail = 0, []


def check(name, cond, detail=""):
    global ok
    if cond:
        ok += 1
        print("  OK ", name)
    else:
        fail.append(f"{name} {detail}")
        print("  X  ", name, detail)


TAG = "zz-failopen-test"
sys.path.insert(0, ROOT)
import runpath                                                       # noqa: E402
# 하네스 산출은 `runs-generated/` 로 옮겼다(씨앗 `runs/` 는 컨테이너에서 `:ro`).
# ⚠ 여기를 안 옮기면 **옛 자리에 남은 산물**을 보고 통과처럼 읽는다 — 실제로 그랬다.
OUT = runpath.harness_write_dir(TAG)
CONCEPT = os.path.join(ROOT, "data", "concept_zz-failopen-test.json")

#: 게이트를 **구조적으로** 통과할 수 없는 초안 — 통제 어휘 밖 계량 하나면 충분하다.
#  값을 조금 틀리는 것이 아니라 «절대 통과 못 하는» 것을 넣어야 재시도 소진을 재현한다.
BROKEN = {"formulas": [{"formula_id": "F_TAM", "vars": [{
    "var_role": "사업체수", "subject": "있을 수 없는 업종", "metric": "존재하지 않는 계량",
    "period": "2024", "unit": "개", "region": "대한민국", "subject_code": None,
    "stat_code": None, "corp_name": None, "claim_type": "TAM",
    "canvas_cell": "고객 세그먼트", "observable": True, "must_contain": ["x"],
    "must_not_contain": [], "value_range": [1, 10], "추출_힌트": [],
    "proxy_선언": {"대상": "", "사유": ""}}]}]}

FAKE_CONCEPT = {
    "_설명": "판 ⑦ H3 강제 실패 테스트용. **실제 컨셉이 아니다** — 지우면 테스트가 만든다.",
    "_계열": {"계열": "A", "왜": "테스트"},
    "concept_id": "CPT-ZZ-FAILOPEN", "name": "강제 실패 테스트 컨셉",
    "problem": "테스트", "target": "테스트", "solution": "테스트",
    "region": "대한민국", "hypotheses": [], "price_hypothesis_krw": None,
    "constraint": {"budget_krw": 1, "months": 1, "team": 1},
    "_경쟁_씨앗": {"seeds": []},
}

print("[1] 강제 실패 — 하네스가 사람을 부르지 않고 기록을 남긴다")
os.makedirs(OUT, exist_ok=True)
io.open(CONCEPT, "w", encoding="utf-8").write(json.dumps(FAKE_CONCEPT, ensure_ascii=False))
replay = os.path.join(OUT, "broken_raw.json")
io.open(replay, "w", encoding="utf-8").write(
    json.dumps({"model": "test", "usage": {}, "text": "", "data": BROKEN, "repair": ""},
               ensure_ascii=False))
snap = os.path.join(ROOT, "data", f"slots_{TAG}.json")
if os.path.exists(snap):
    os.remove(snap)

env = dict(os.environ, PYTHONIOENCODING="utf-8")
r = subprocess.run([sys.executable, os.path.join(ROOT, "harness", "slot_harness.py"),
                    "--concept", CONCEPT, "--tag", TAG, "--replay", replay],
                   capture_output=True, text=True, encoding="utf-8", env=env, cwd=ROOT)
out = (r.stdout or "") + (r.stderr or "")

check("종료 코드 0 — 파이프라인을 멈추지 않는다", r.returncode == 0, f"got {r.returncode}")
check("「사람 판단이 필요하다」 문구가 사라졌다", "사람 판단이 필요하다" not in out)
check("실패를 **말은 한다** (조용하지 않다)", "fail-open" in out and "게이트 미통과" in out)
check("스냅샷을 쓰지 않았다 — 나쁜 슬롯으로 수집하지 않는다", not os.path.exists(snap))

print("\n[2] 실패 기록이 구조화돼 있다")
fp = os.path.join(OUT, "harness_failure.json")
check("harness_failure.json 이 생겼다", os.path.exists(fp))
rec = json.load(io.open(fp, encoding="utf-8")) if os.path.exists(fp) else {}
need = ["tag", "concept", "시도_횟수", "상한", "실패_검사", "마지막_사유",
        "영향_칸", "canvas_표시"]
check(f"필수 필드 {len(need)}개가 다 있다 (규칙 파일이 정한 목록)",
      all(k in rec for k in need), [k for k in need if k not in rec])
check("무엇이 몇 회 실패했는지 — 검사명과 건수", bool(rec.get("실패_검사")))
check("마지막 게이트 실패 사유가 값으로 남는다", bool(rec.get("마지막_사유")))
check("canvas 어느 칸에 반영되는지 적혀 있다", bool(rec.get("영향_칸")))
check("상태 어휘 4종과 섞지 않는다 — 「미생성」",
      (rec.get("canvas_표시") or {}).get("상태") == "미생성")

print("\n[3] canvas 는 **반드시 출력된다** (원장이 없어도)")
doc = C.build_from_failure(rec, FAKE_CONCEPT)
a = C.audit(doc)
check("9칸이 다 있다", len(doc["칸"]) == 9)
check("조립 검사 6/6 통과 — 실패 산출물도 계약을 지킨다", a["passed"],
      [c["name"] for c in a["checks"] if not c["passed"]])
check("머리말 첫 줄이 실패를 밝힌다 — 성공 문서와 겉모습이 같으면 안 된다",
      "하네스 실패" in doc["결론_머리말"][0])
check("측정 칸 4개가 전부 「미생성」",
      all(doc["칸"][k]["상태"] == "미생성"
          for k in ("고객 세그먼트", "가치 제안", "채널", "수익원")))
check("§7 이 빈 껍데기가 아니다 — 사유가 들어 있다",
      bool((doc["못_찾은_것"] or {}).get("_사유")))
check("§4 틀릴 수 있는 지점 보존", bool(doc.get("틀릴_수_있는_지점")))
check("계획 칸은 컨셉에서 그대로 온다", doc["칸"]["비용 구조"]["상태"] == "계획")
check("실패 기록이 문서 안에 통째로 실린다 — 되짚을 수 있게",
      doc.get("_하네스_실패", {}).get("tag") == TAG)

print("\n[4] 정상 경로는 무변경 (fail-open 이 통과까지 바꾸지 않는다)")
gate = json.load(io.open(os.path.join(ROOT, "runs", "harness", "beauty-p2check",
                                      "gate.json"), encoding="utf-8"))
check("통과한 판은 스냅샷이 있다", gate["passed"] and os.path.exists(
    os.path.join(ROOT, "data", "slots_beauty-p2check.json")))

# 뒷정리 — 테스트가 만든 것만 지운다(§1: 삭제는 명시한 id 로만, 패턴 금지)
for p in (CONCEPT, replay):
    if os.path.exists(p):
        os.remove(p)

print(f"\n===== {ok} 통과 / {len(fail)} 실패")
for f in fail:
    print(" 실패:", f)
sys.exit(1 if fail else 0)
