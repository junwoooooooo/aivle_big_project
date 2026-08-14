# -*- coding: utf-8 -*-
"""단계 17 검증 — 판 ㉟ 의 계측.

    ① 실행 능력 지문      `result.json.실행_능력` · coverage_caveat
    ② 검색 질의           `Meter.create(tag=…)` → `a3_web_query` 노드
    ②-b PDF 표식          `Document.is_pdf`
    ③ 깔때기 사유 축      `tools/funnel.py` 의 PDF 단계 · content_status/error 내역

**이 파일이 지키려는 것 한 줄: 판 ㉞ 의 사고를 이름으로 잡는다.**
컨테이너에 `pdfplumber` 가 없어 PDF 48건을 통째로 버렸는데 원장 어디에도 「해석기가
없었다」가 없었고, 그래서 유료 4판(≈252회)을 결함 위에서 쟀다. 여기 검사의 절반은
**값이 맞는가**가 아니라 **칸이 존재하는가**를 본다 — 칸이 없으면 0 이 아니라 미측정이다.
"""
from __future__ import annotations
import io, json, os, shutil, sys, tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
sys.path.insert(0, ROOT)
sys.path.insert(0, os.path.join(ROOT, "adapters"))
sys.path.insert(0, os.path.join(ROOT, "tools"))

import runlog
from runlog import Run, Meter
from schema import Document

ok, fail = 0, []


def check(name, cond, detail=""):
    global ok
    if cond:
        ok += 1
    else:
        fail.append(f"{name} — {detail}")
    print(f"  {'OK ' if cond else 'X  '} {name}" + (f"   {detail}" if not cond else ""))


def fresh_run(rid):
    # ⚠ **쓰기 자리로 지운다.** `runlog.RUNS_DIR` 은 **읽기** 씨앗이고 `Run` 은
    #   `runpath.write_dir()` 에 쓴다. 읽기 자리를 지우면 `run.jsonl` 이 append-only 라
    #   전 판의 줄이 그대로 남아 다음 실행에서 지표가 배로 보인다 — 실제로 그랬다.
    shutil.rmtree(runlog.runpath.write_dir(rid), ignore_errors=True)
    return Run(rid)


# ══════════════════════════════════════════════════════════════
print("[① 실행 능력 지문] 「해석기가 없었다」가 원장에 남는다")
# ══════════════════════════════════════════════════════════════
cap = runlog.capability_fingerprint()
check("python 칸", isinstance(cap.get("python"), str), str(cap))
for pkg in runlog.CAPABILITY_PACKAGES:
    # **값이 아니라 칸을 본다.** None 은 「없다」이고, 키 부재는 「안 쟀다」다.
    check(f"{pkg} 칸이 존재한다 (값이 None 이어도 통과)", pkg in cap, str(cap))
check("없는 패키지는 예외가 아니라 None",
      runlog.capability_fingerprint().get("pdfplumber") in (None, ) or
      isinstance(cap["pdfplumber"], str), str(cap["pdfplumber"]))

r = fresh_run("t17-cap")
res = r.finish()
check("result.json 에 실행_능력 칸", "실행_능력" in res, str(sorted(res)[:6]))
check("adapters 옆자리다", list(res).index("실행_능력") == list(res).index("adapters") + 1,
      str(list(res)[:8]))
saved = json.load(io.open(os.path.join(r.dir, "result.json"), encoding="utf-8"))
check("디스크에도 실려 있다", "실행_능력" in saved)

# coverage_caveat — 어댑터 사유와 해석기 사유가 **서로를 지우지 않는다**
r2 = fresh_run("t17-caveat")
r2.set_adapter("kosis", "not_configured", "키 없음")
cv = r2.coverage_caveat() or ""
check("어댑터 사유는 그대로 나온다", "통계 API 미사용" in cv, cv)
if cap["pdfplumber"] is None:
    check("해석기 사유가 함께 실린다", "PDF 해석기 없음" in cv, cv)
    check("둘이 한 줄에 같이 있다", "통계 API 미사용" in cv and "PDF 해석기 없음" in cv, cv)
else:
    # 이 환경엔 해석기가 있다. 그래도 **문장을 만드는 경로**는 확인한다.
    r3 = fresh_run("t17-caveat-none")
    _real = runlog.capability_fingerprint
    runlog.capability_fingerprint = lambda: {**_real(), "pdfplumber": None}
    try:
        cv3 = r3.coverage_caveat() or ""
    finally:
        runlog.capability_fingerprint = _real
    check("해석기가 없으면 한 줄이 붙는다", "PDF 해석기 없음 — PDF 출처 커버리지 0" in cv3, cv3)
    check("해석기가 있으면 안 붙는다", "PDF 해석기" not in (r2.coverage_caveat() or ""), cv)


# ══════════════════════════════════════════════════════════════
print("\n[② 검색 질의] 세기만 하던 것을 남긴다")
# ══════════════════════════════════════════════════════════════
class _Act:
    def __init__(self, **kw):
        self.__dict__.update(kw)


class _Item:
    def __init__(self, action, type="web_search_call"):
        self.type, self.action = type, action


class _Resp:
    def __init__(self, output):
        self.output = output
        self.usage = None


class _Client:
    """`responses.create` 흉내. **`tag` 가 여기 오면 안 된다** — API 로 나간다는 뜻이다."""
    def __init__(self, resp):
        self._resp, self.seen = resp, None
        self.responses = self

    def create(self, **kw):
        self.seen = kw
        return self._resp


def _meter(run, output):
    c = _Client(_Resp(output))
    return Meter(c, run), c


r = fresh_run("t17-q")
m, c = _meter(r, [_Item(_Act(queries=["1인가구 간편식 실태조사", "HMR 구매빈도"])),
                  _Item(_Act(queries=["냉동식품 소비 통계"]))])
m.create("a3_search", tag={"slot_id": "S12", "trace_id": "S12-q0"}, model="x", input="y")
check("tag 는 API 로 안 나간다", "tag" not in (c.seen or {}), str(sorted(c.seen or {})))
rows = r.read("a3_web_query")
check("a3_web_query 노드가 남는다", len(rows) == 1, str(rows))
check("질의 문자열이 값으로 남는다",
      rows and rows[0]["queries"] == ["1인가구 간편식 실태조사", "HMR 구매빈도", "냉동식품 소비 통계"],
      str(rows and rows[0].get("queries")))
check("n 은 질의 수다", rows and rows[0]["n"] == 3, str(rows and rows[0].get("n")))
check("slot_id 가 줄에 꿰인다", rows and rows[0]["slot_id"] == "S12")
check("세는 것은 그대로다 (llm.web_queries=3)", r.counters.get("llm.web_queries") == 3,
      str(r.counters.get("llm.web_queries")))

# 단수형 `query` 만 오는 판본
r = fresh_run("t17-q1")
m, _ = _meter(r, [_Item(_Act(query="단수형 질의"))])
m.create("a3_search", tag={"slot_id": "S13"}, model="x")
check("단수형 query 도 긁는다", r.read("a3_web_query")[0]["queries"] == ["단수형 질의"],
      str(r.read("a3_web_query")))

# 모르는 모양 — **계측이 수집을 죽이면 안 된다**
r = fresh_run("t17-qraw")
m, _ = _meter(r, [_Item(_Act(무엇=1))])
m.create("a3_search", tag={"slot_id": "S14"}, model="x")
row = r.read("a3_web_query")[0]
check("모르는 모양은 raw 로 접어 남긴다", row["n"] == 0 and "raw" in row, str(row))
check("그래도 호출 1건은 센다", r.counters.get("llm.web_queries") == 1,
      str(r.counters.get("llm.web_queries")))

# 검색을 아예 안 한 경우 — 「질의 0」도 관측이다
r = fresh_run("t17-q0")
m, _ = _meter(r, [])
m.create("a3_search", tag={"slot_id": "S15"}, model="x")
check("검색 0회도 줄로 남는다", len(r.read("a3_web_query")) == 1)

# tag 없는 호출(a3_extract 등)은 이 노드를 만들지 않는다
r = fresh_run("t17-notag")
m, _ = _meter(r, [_Item(_Act(queries=["x"]))])
m.create("a3_extract", model="x")
check("tag 없으면 노드를 안 만든다", r.read("a3_web_query") == [])


# ══════════════════════════════════════════════════════════════
print("\n[②-b PDF 표식] 살아난 PDF 를 셀 수 있어야 한다")
# ══════════════════════════════════════════════════════════════
check("Document 에 is_pdf 칸", "is_pdf" in Document.__dataclass_fields__)
check("기본값은 False — 옛 원장은 미측정이다", Document.__dataclass_fields__["is_pdf"].default is False)
old = Document(slot_id="S1", trace_id="t", url="u")          # 옛 payload 복원 흉내
check("is_pdf 없는 옛 문서도 복원된다", old.is_pdf is False)
check("usable 인 PDF 도 표식이 남는다",
      Document(slot_id="S1", trace_id="t", url="u", content_status="usable", is_pdf=True).is_pdf)


# ══════════════════════════════════════════════════════════════
print("\n[③ 깔때기 사유 축] pdf_unreadable × 48 이 다시는 조용히 지나가지 않는다")
# ══════════════════════════════════════════════════════════════
import funnel

DOCS = [
    # 판 ㉞ 의 그 사고 그대로 — 해석기가 없어 죽은 PDF
    {"slot_id": "S12", "trace_id": "a", "content_status": "pdf_unreadable", "is_pdf": True,
     "http_status": "ok", "error": "pdfplumber 없음: ModuleNotFoundError", "text_len": 0},
    # 같은 pdf_unreadable 인데 **사유가 다르다** — 이 구별이 이 축의 존재 이유다
    {"slot_id": "S12", "trace_id": "b", "content_status": "pdf_unreadable", "is_pdf": True,
     "http_status": "ok", "error": "텍스트층 없음(스캔본 추정)", "text_len": 0},
    {"slot_id": "S12", "trace_id": "c", "content_status": "empty", "http_status": "blocked",
     "http_code": 429, "error": "", "text_len": 0},
    {"slot_id": "S12", "trace_id": "d", "content_status": "usable", "is_pdf": True,
     "http_status": "ok", "text_len": 900},
    {"slot_id": "S1", "trace_id": "e", "content_status": "usable", "http_status": "ok",
     "text_len": 500},
]
SPECS = [{"slot_id": "S12", "claim_type": "PAIN"}, {"slot_id": "S1", "claim_type": "TAM"}]

tmp = tempfile.mkdtemp()
d = os.path.join(tmp, "t17-funnel")
os.makedirs(d)
with io.open(os.path.join(d, "run.jsonl"), "w", encoding="utf-8") as f:
    for x in DOCS:
        f.write(json.dumps({"node": "a3_document", "payload": x}, ensure_ascii=False) + "\n")
    f.write(json.dumps({"node": "a3_extract",
                        "payload": {"slot_id": "S12", "picked": ["d"], "cut": [],
                                    "per_doc": [{"trace_id": "d", "status": "not_found"}]}},
                       ensure_ascii=False) + "\n")
io.open(os.path.join(d, "result.json"), "w", encoding="utf-8").write(json.dumps(
    {"input": {"slots": SPECS}, "rules": {}, "실행_능력": {"pdfplumber": None}},
    ensure_ascii=False))
_real_read = funnel.runpath.read_dir
funnel.runpath.read_dir = lambda run: d
try:
    r = funnel.build("t17-funnel")
    pdf단계 = [s for s in r["단계"] if s["이름"].startswith("PDF 해석")][0]
    check("PDF 단계가 있다", pdf단계["들어감"] == 3, str(pdf단계))
    check("살아난 PDF 를 센다", pdf단계["나옴"] == 1, str(pdf단계))
    check("사유가 종류별로 갈린다",
          pdf단계["내역"] == {"pdfplumber 없음": 1, "텍스트층 없음(스캔본 추정)": 1},
          str(pdf단계["내역"]))
    check("is_pdf 가 있으면 하한 표시를 안 붙인다", pdf단계["이름"] == "PDF 해석", pdf단계["이름"])

    s12 = [s for s in r["슬롯"] if s["slot_id"] == "S12"][0]
    check("슬롯별 본문사유", s12["본문사유"] == {"pdf_unreadable": 2, "empty": 1}, str(s12["본문사유"]))
    check("슬롯별 fetch사유에 코드가 붙는다", s12["fetch사유"].get("blocked:429") == 1,
          str(s12["fetch사유"]))
    check("슬롯별 error사유", s12["error사유"].get("pdfplumber 없음") == 1, str(s12["error사유"]))
    check("발췌 실패는 따로 센다 — 「본문이 안 왔다」와 다른 칸이다",
          s12["발췌사유"] == {"not_found": 1}, str(s12["발췌사유"]))
    check("실행_능력을 깔때기가 물어 나른다", r["_실행_능력"] == {"pdfplumber": None},
          str(r["_실행_능력"]))

    p = funnel.build("t17-funnel", "PAIN")
    check("--claim-type 이 그 유형만 남긴다", [s["slot_id"] for s in p["슬롯"]] == ["S12"],
          str([s["slot_id"] for s in p["슬롯"]]))
    check("걸러진 슬롯의 문서는 분모에서도 빠진다", p["단계"][1]["들어감"] == 4,
          str(p["단계"][1]))

    # 옛 원장 — is_pdf 칸이 아예 없다. **0 이 아니라 하한이라고 말해야 한다.**
    for x in DOCS:
        x.pop("is_pdf", None)
    with io.open(os.path.join(d, "run.jsonl"), "w", encoding="utf-8") as f:
        for x in DOCS:
            f.write(json.dumps({"node": "a3_document", "payload": x}, ensure_ascii=False) + "\n")
    old단계 = [s for s in funnel.build("t17-funnel")["단계"]
             if s["이름"].startswith("PDF 해석")][0]
    check("옛 원장은 하한이라고 이름에 적는다", "하한" in old단계["이름"], old단계["이름"])
    check("옛 원장은 죽은 PDF 만 보인다", old단계["들어감"] == 2, str(old단계))
finally:
    funnel.runpath.read_dir = _real_read
    shutil.rmtree(tmp, ignore_errors=True)


print(f"\n===== {ok} 통과 / {len(fail)} 실패")
for f in fail:
    print(" 실패:", f)
sys.exit(1 if fail else 0)
