# -*- coding: utf-8 -*-
"""BM 내보내기 검증 — 서비스 층 2.5호. **LLM 0회 · 네트워크 0회.**

여기서 지키는 것은 파일이 아니라 **보존**이다:
  · 꼬리표(선언 · 조립 · 공백 선언 · single_path · 축_부재)가 **파일에서도 전부 살아 있다**
  · 채널 한계 문구는 **뒷문장까지** 파일에 있다
  · 머리에 run_id · 생성 시각 · 「단일 원장 기준」 · 템플릿 버전
  · **fail-closed** — 없는 원장으로 부르면 exit 비0 이고 **파일이 안 생긴다**
  · export 는 **해석하지 않는다** — bm_layer 본문이 그대로 들어 있다

    python tests/test_bm_export.py
"""
from __future__ import annotations
import io, json, os, re, shutil, subprocess, sys, tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
sys.path.insert(0, os.path.join(ROOT, "service"))

import bm_export as X
import bm_layer as L

ok, fail = 0, []


def check(name, cond, detail=""):
    global ok
    if cond:
        ok += 1
    else:
        fail.append(f"{name} — {detail}")
    print(f"  {'OK ' if cond else 'X  '} {name}" + (f"   {detail}" if not cond else ""))


SRC = io.open(os.path.join(ROOT, "service", "bm_export.py"), encoding="utf-8").read()
CODE = "\n".join(l.split("#")[0] for l in SRC.split("\n") if not l.strip().startswith("#"))

# ══════════════════════════════════════════════════════════════
print("[유리벽] 엔진 import 0 · LLM 0 · 해석 없음")
for bad in ("import a_desk", "import b_estimate", "import c_chain", "from blocks",
            "openai", "OpenAI", "responses.create"):
    check(f"'{bad}' 없음", bad not in CODE)
check("bm_layer 를 읽기만 한다 (build/render 호출)",
      "bm_layer.build" in CODE and "bm_layer.render" in CODE)
check("원장 경로에 직접 쓰지 않는다", "runs" not in CODE.replace("runs/<run_id>", ""))

# ══════════════════════════════════════════════════════════════
print("\n[격리] 테스트는 납품 디렉터리(outputs/)에 쓰지 않는다")
TMP = tempfile.mkdtemp(prefix="bm_export_test_")
check("임시 폴더가 outputs/ 밖이다",
      os.path.abspath(TMP) != os.path.abspath(X.OUT_DIR)
      and not os.path.abspath(TMP).startswith(os.path.abspath(X.OUT_DIR)))
before_outputs = sorted(os.listdir(X.OUT_DIR)) if os.path.isdir(X.OUT_DIR) else []
TESTSRC = io.open(os.path.abspath(__file__), encoding="utf-8").read()
check("이 테스트에 out_dir 없는 export 호출이 없다",
      "X.export(rid," in TESTSRC and "out_dir=TMP" in TESTSRC)

print("\n[두 건 내보내기]")
paths = {}
for rid in ("audit-final", "report3-04"):
    p = X.export(rid, now="2026-01-01T00:00:00", out_dir=TMP)
    paths[rid] = p
    check(f"{rid} 파일 생성", os.path.exists(p), p)
    check(f"  {rid} 는 임시 폴더에 생겼다", os.path.dirname(p) == TMP, p)

for rid, p in paths.items():
    md = io.open(p, encoding="utf-8").read()
    print(f"\n  [{rid}] 머리 · 꼬리표 보존")
    check("  머리에 run_id", f"`{rid}`" in md)
    check("  머리에 생성 시각", "생성 시각: 2026-01-01T00:00:00" in md)
    check("  머리에 「단일 원장 기준」", "단일 원장 기준" in md)
    check("  머리에 템플릿 버전", "템플릿 v1" in md)
    check("  조립 꼬리표 (「생성」이 아니다)",
          "조립(템플릿 v1)" in md and "LLM 생성이 아니라" in md)
    check("  채널 한계 앞문장", "채널 축을 다루지 않는다" in md)
    check("  **채널 한계 뒷문장**", "채널 없이 BM 이 성립한다는 뜻이 아니다" in md)
    check("  축_부재 표기 보존", "축_부재" in md)
    check("  공백 선언 보존", "공백 선언" in md or "공백" in md)

    # 선언 꼬리표는 **등장할 때마다** 동반돼야 한다 (bm_layer 와 같은 검사를 파일에도)
    for m in re.finditer(r"조회_경로_결함", md):
        seg = md[m.start():m.start() + 400]
        check("  '조회_경로_결함' 등장 시 꼬리표 동반",
              "선언(원장 관측 아님)" in seg and "만료" in seg, seg[:70])

md34 = io.open(paths["report3-04"], encoding="utf-8").read()
check("report3-04: single_path 꼬리표 보존", "single_path" in md34)
check("report3-04: 값이 실려 있다 (SAM 중앙)", "729,504,000" in md34)
md_af = io.open(paths["audit-final"], encoding="utf-8").read()
check("audit-final: 경쟁 확인됨 5점 보존", "확인됨" in md_af and "official_page" in md_af)

print("\n  export 는 해석하지 않는다 — bm_layer 본문이 그대로 들어 있다")
body = L.render(L.build("audit-final"))
check("bm_layer.render 결과가 파일에 그대로 포함", body in md_af)

# ══════════════════════════════════════════════════════════════
print("\n[fail-closed] 없는 원장 → exit 비0 · 파일 미생성")
bad_id = "존재하지않는런ID"
before = os.path.exists(os.path.join(X.OUT_DIR, f"bm_report_{bad_id}.md"))
r = subprocess.run([sys.executable, "-m", "service.bm_export", bad_id],
                   cwd=ROOT, capture_output=True, text=True, encoding="utf-8",
                   env={**os.environ, "PYTHONIOENCODING": "utf-8"})
check("exit code 가 0 이 아니다", r.returncode != 0, str(r.returncode))
check("  사유가 stderr 로 나간다", "원장을 찾을 수 없다" in (r.stderr or ""), (r.stderr or "")[:60])
check("  **파일이 생기지 않는다**",
      not os.path.exists(os.path.join(X.OUT_DIR, f"bm_report_{bad_id}.md")) and not before)

print("\n[서버 계약] 성공 시 stdout 마지막 줄이 파일 경로 · exit 0")
r2 = subprocess.run([sys.executable, "-m", "service.bm_export", "audit-final",
                     "--out-dir", TMP],
                    cwd=ROOT, capture_output=True, text=True, encoding="utf-8",
                    env={**os.environ, "PYTHONIOENCODING": "utf-8"})
last = (r2.stdout or "").strip().split("\n")[-1] if r2.stdout else ""
check("exit 0", r2.returncode == 0, str(r2.returncode))
check("  마지막 줄이 실재 파일 경로", bool(last) and os.path.exists(last), last[:70])
# 계약 정본은 저장소 루트의 `문서/` 에 있다. 엔진이 저장소 안 어디에 놓이든 찾게 한다 —
# 판 ㉝ 이식으로 ROOT 가 두 단계 깊어지자 고정 `../../` 가 어긋났다.
def _contract_doc() -> str:
    d = ROOT
    for _ in range(7):
        p = os.path.join(d, "문서", "시장조사_BM_호출계약.md")
        if os.path.exists(p):
            return p
        d = os.path.dirname(d)
    return os.path.join(ROOT, "..", "..", "문서", "시장조사_BM_호출계약.md")


check("  계약 문서가 있다",
      os.path.exists(_contract_doc()), _contract_doc())

# ══════════════════════════════════════════════════════════════
print("\n[절단 금지] 원장 not_found 원소 수 == 문서 불릿 수")
for rid, p in paths.items():
    res = json.load(io.open(os.path.join(ROOT, "runs", rid, "result.json"), encoding="utf-8"))
    nf = (res.get("report") or {}).get("not_found") or {}
    md = io.open(p, encoding="utf-8").read()
    sec = md[md.find("## 4. 공백"):md.find("## 5.")]
    for k in ("independent_topdown_blocked", "empty_slots"):
        v = nf.get(k)
        if not v:
            continue
        items = v if isinstance(v, list) else [v]
        head = sec.find(f"§7 {k}")
        tail = sec[head:]
        nxt = tail.find("\n- ")
        blk = tail[:nxt] if nxt > 0 else tail
        bullets = [l for l in blk.split("\n") if l.startswith("  - ")]
        check(f"  [{rid}] {k}: 원소 {len(items)} == 불릿 {len(bullets)}",
              len(items) == len(bullets), f"{len(items)} vs {len(bullets)}")
        check(f"    마지막 원소까지 실려 있다",
              str(items[-1])[:40] in blk, str(items[-1])[:40])

md34 = io.open(paths["report3-04"], encoding="utf-8").read()
check("GMV≠매출 경고가 살아남는다", "거래액(GMV)이지 매출이 아니다" in md34)
check("  '0단계 실측:' 뒤가 비어 있지 않다",
      "0단계 실측:\n  - " in md34 or "0단계 실측:" in md34 and "상위권이 비상장" in md34)

print("\n[문형] 비율(1/5)로 읽히지 않는다")
for rid, p in paths.items():
    md = io.open(p, encoding="utf-8").read()
    check(f"  [{rid}] '충족 축 N개(...)' 문형", re.search(r"충족 축 \d+개\(", md) is not None)
    check(f"    옛 '/ 전체' 분수 문형이 없다", "/ 전체" not in md)
    check(f"    축_부재가 있으면 분모 경고가 붙는다",
          ("축_부재 1 포함" in md and "분모가 될 수 없다" in md))

print("  충족 2개 케이스 — 우연히 맞는 지대 제거")
import bm_layer as _L
_card = {"axes": [{"name": "A", "state": "충족", "why": ""},
                  {"name": "B", "state": "충족", "why": ""},
                  {"name": "C", "state": "미충족", "why": "x"},
                  {"name": "D", "state": "축_부재", "why": "y"}]}
_n = _L.build_narrative(_card, {"values": [], "gaps": []},
                        {"rows": [], "gap": None}, [], "dummy")["text"]
check("  충족 2개도 오독 없이 나온다", "충족 축 2개(A, B)" in _n and "전체 4축" in _n, _n)
check("    분수로 읽힐 자리가 없다", "/ 전체" not in _n and "2/4" not in _n)

print("\n[납품 디렉터리 무오염] outputs/ 가 테스트 전후로 동일하다")
after_outputs = sorted(os.listdir(X.OUT_DIR)) if os.path.isdir(X.OUT_DIR) else []
check("outputs/ 목록 불변", before_outputs == after_outputs,
      f"{before_outputs} → {after_outputs}")
shutil.rmtree(TMP, ignore_errors=True)

print(f"\n===== {ok} 통과 / {len(fail)} 실패")
for f in fail:
    print("   -", f)
sys.exit(1 if fail else 0)
