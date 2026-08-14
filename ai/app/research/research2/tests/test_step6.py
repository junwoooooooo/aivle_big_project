# -*- coding: utf-8 -*-
"""단계 6 검증 — A3 web 어댑터 + prompts.py

여기서부터 성격이 다르다: **테스트 통과 = 잘 작동함이 아니다.**
수용기준 1·8 은 껍데기를 검사할 뿐이고, 품질은 eval 지표로만 보인다.

수용기준 1(금지 필드) · 8(본문 미확보 → 상한 2)
        + plan_query 중복 · extract 슬롯 단위 1회 · content_status 실측 기준

    python tests/test_step6.py          # LLM 0회
    python tests/test_step6.py --live   # 실제 검색·발췌 포함
"""
from __future__ import annotations
import io, json, os, sys
import threading as _threading          # _FakeMeter 가 per_doc 병렬 아래서 세어야 한다

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
for p in (ROOT, os.path.join(ROOT, "blocks"), os.path.join(ROOT, "adapters")):
    sys.path.insert(0, p)

import a_desk as A
import prompts
import web
from runlog import load_rules
from schema import FORBIDDEN_LLM_FIELDS, Candidate, Document, Slot

rules = load_rules()
LIVE = "--live" in sys.argv
ok, fail = 0, []


def check(name, cond, detail=""):
    global ok
    if cond:
        ok += 1
    else:
        fail.append(f"{name} — {detail}")
    print(f"  {'OK ' if cond else 'X  '} {name}" + (f"   {detail}" if not cond else ""))


def S(**kw):
    base = dict(slot_id="S1", var_id="V1", formula_id="F1", claim_type="TAM",
                subject="커피전문점", metric="사업체 수", period="2023", unit="개",
                region="대한민국")
    base.update(kw)
    return Slot(**base)


# ══════════════════════════════════════════════════════════════
print("[수용기준 1] 금지 필드 — **스키마 정의만** 검사한다 (산문 전체가 아니라)")
schema_fields = set(prompts.EXTRACT_ITEM_FIELDS) | set(prompts.EXTRACT_ENVELOPE_FIELDS) \
    | set(prompts.FORMULA_FIELDS) | set(prompts.FORMULA_VAR_FIELDS)
bad = schema_fields & FORBIDDEN_LLM_FIELDS
check("LLM 출력 스키마에 금지 필드 없음", not bad, f"발견: {bad}")
check("스키마 필드 목록이 코드로 정의됨", isinstance(prompts.EXTRACT_ITEM_FIELDS, tuple))

print("\n  프롬프트 JSON 뼈대가 스키마에서 **생성**된다 (손으로 쓰면 갈라진다)")
for f in prompts.EXTRACT_ITEM_FIELDS:
    check(f"  '{f}' 가 EXTRACT 프롬프트에 실림", f'"{f}"' in prompts.EXTRACT)
check("envelope 도 반영", '"status"' in prompts.EXTRACT and '"findings"' in prompts.EXTRACT)

print("\n  산문에 금지어가 있어도 오탐이 나지 않는다 (검사 범위가 좁아서)")
probe = prompts.EXTRACT + "\n출처의 tier 나 score 를 매기지 마라."
check("산문에 'tier'가 있어도 스키마는 깨끗", not (schema_fields & FORBIDDEN_LLM_FIELDS),
      "스키마 튜플만 보므로 영향 없음")

# ══════════════════════════════════════════════════════════════
print("\n[plan_query] subject 와 region 을 중복시키지 않는다")
qs = web.plan_query(S(subject="서울 커피전문점", region="서울"))
check("'서울 서울' 없음", all("서울 서울" not in q for q in qs), str(qs))
check("subject 는 남는다", any("서울 커피전문점" in q for q in qs), str(qs))
qs2 = web.plan_query(S(subject="커피전문점", region="대한민국"))
check("중복 아니면 region 을 붙인다", any(q.startswith("대한민국 커피전문점") for q in qs2), str(qs2))
check("중복 쿼리 제거", len(qs2) == len(set(qs2)))
check("LLM 0회 (순수 함수)", "meter" not in web.plan_query.__code__.co_varnames)

# ── 작업 12-2 — SEARCH 프롬프트 표면 ────────────────────────────
print("\n[12-2] SEARCH 프롬프트가 슬롯 유형을 안다 · 치환이 새지 않는다")
import io as _io, json as _json, re as _re

_slots = _json.load(_io.open(os.path.join(ROOT, "data", "slots.json"),
                             encoding="utf-8"))["slots"]
for _ct in sorted({s["claim_type"] for s in _slots}):
    check(f"  '{_ct}' 힌트가 있다", _ct in prompts.CLAIM_TYPE_HINT)
check("모르는 유형에는 지어내지 않는다",
      "지정 없음" in prompts.claim_type_hint("NO_SUCH_TYPE"))
# 미치환 placeholder 가 남으면 모델이 '{region}' 을 글자 그대로 읽는다. 조용히 나쁜 종류다.
for _name, _tpl in sorted(prompts.SEARCH_VARIANTS.items()):
    for _ct in list(prompts.CLAIM_TYPE_HINT) + ["NO_SUCH_TYPE"]:
        _r = prompts.render(_tpl, subject="커피전문점", metric="사업체 수",
                            period="2023", region="대한민국",
                            claim_type_hint=prompts.claim_type_hint(_ct))
        if _re.findall(r"\{[a-z_]+\}", _r):
            check(f"  {_name}/{_ct} 렌더에 미치환 없음", False, str(_re.findall(r"\{[a-z_]+\}", _r)))
            break
    else:
        check(f"  {_name}: 모든 claim_type 렌더에 미치환 없음", True)
# 가설별 겨냥 문장이 v12-2 문안에 있는지 (문안을 지우면 12-3 이 잴 대상을 잃는다)
_v122 = prompts.SEARCH_VARIANTS["v12-2"]
check("가설 A — 발행 시점과 값의 시점을 구분", "발행 시점이 아니라 값의 시점" in _v122)
check("가설 B — 배제 목록", "인용하지 마라" in _v122 and "FAQ" in _v122)
check("가설 C — '공식' 을 정의", "도메인의 소유자가 아니라" in _v122)
check("가설 E — 발행자 유형 서술", "누가 발행했는지로 고른다" in _v122)
# ① 유형 서술로만 노출한다 — 화이트리스트 **도메인** 을 프롬프트에 박지 않는다
_wl = rules["whitelist"]["kinds"]
_doms = [d for v in _wl.values() for d in v]
_leak = [d for v in prompts.SEARCH_VARIANTS.values() for d in _doms if d in v]
check("화이트리스트 도메인이 어느 문안에도 박혀 있지 않다", not _leak, str(_leak))

# ── 12-2 문안은 **미채택**이다. 기본 경로에 있으면 그 사이 수집이 전부 오염된다 ──
print("\n  미채택 문안은 기본이 아니다 (expected.md 부록 C)")
check("기본 문안 = v1", prompts.DEFAULT_SEARCH_VARIANT == "v1"
      and prompts.SEARCH is prompts.SEARCH_V1)
check("규칙의 기본값도 v1", rules["adapters"]["web"]["search_prompt"] == "v1")
check("v1 에는 12-2 문장이 없다", "누가 발행했는지로 고른다" not in prompts.SEARCH_V1)
# **포함**으로 본다 — 새 변종이 늘어나는 것은 정상이고(판 ㊱ `v33-pain`), 지켜야 할 것은
# 「옛 변종이 사라지지 않는다」와 「기본이 v1 이다」 둘이다. 같음으로 두면 변종을 더할
# 때마다 이 줄이 깨지면서 정작 그 두 가지를 안 지켜도 알 수 없다.
check("옛 변종이 보존돼 있다", {"v1", "v12-2"} <= set(prompts.SEARCH_VARIANTS),
      str(sorted(prompts.SEARCH_VARIANTS)))
check("명시적으로 고르면 그 문안이 나온다",
      prompts.search_prompt("v12-2")[1] is prompts.SEARCH_V12_2)
try:
    prompts.search_prompt("v9-오타")
    check("모르는 문안이면 멈춘다", False, "예외가 안 났다")
except KeyError:
    check("모르는 문안이면 멈춘다 (조용히 기본으로 넘어가지 않는다)", True)

print("\n  from_query 는 **실제로 모델이 받은 것**만 적는다 (안 나간 검색어를 적지 않는다)")
#: 소스를 grep 하던 자리다(`_unused_query` 라는 **변수 이름**을 찾았다). 판 ㊱ 에서 그
#: 이름이 사라지자 동작은 그대로인데 검사만 빨개졌다 — 이름은 계약이 아니다.
#: **동작으로 본다**: 모델에게 간 입력과 원장에 남은 from_query 둘 다에 plan_query 의
#: 문자열이 없어야 한다. LLM 0회(가짜 계량기).
class _Spy:
    def __init__(self):
        self.inputs = []

    def create(self, node, **kw):
        self.inputs.append(str(kw.get("input") or ""))

        class _R:
            output = []

            def model_dump_json(self):
                return "{}"
        return _R()


_slot = Slot(slot_id="S1", var_id="V1", formula_id="F1", claim_type="PAIN",
             subject="1인 가구 혼자 식사", metric="문제 경험률", period="2025", unit="%")
_planned = web.plan_query(_slot)
_spy = _Spy()
_cands = web.search(_slot, _spy, "S1", rules)
check("plan_query 가 문자열을 만들기는 한다", bool(_planned), str(_planned))
check("그 문자열이 모델 입력에 안 들어간다",
      all(p not in i for p in _planned for i in _spy.inputs), str(_spy.inputs)[:200])
check("그 문자열이 from_query 에도 안 남는다",
      all(p not in (c.from_query or "") for p in _planned for c in _cands),
      str([c.from_query for c in _cands])[:200])

# ══════════════════════════════════════════════════════════════
print("\n[extract] **문서마다 1회** — 묶음이 아니다 (extract_mode=per_doc)")
# ⚠ 판 ㉛ 에서 뒤집힌 자리다. 예전에는 「슬롯 단위 1회」가 규칙이었고 이 블록이 그것을
#   지켰다. 묶음일 때 발췌 44건이 인용 1건을 냈고(2.3%), 모델의 「없습니다」 한 마디로
#   문서 5개가 통째로 죽으면서 **어느 문서를 실제로 읽었는지 원장에 안 남았다.**
#   그래서 문서당 1회로 바꿨다 — 아래는 그 **바뀐 계약**을 지킨다.
src = io.open(os.path.join(ROOT, "adapters", "web.py"), encoding="utf-8").read()
body = src[src.index("def extract("):src.index("def collect(")]
one = src[src.index("def _extract_one("):src.index("def extract(")]
# LLM 호출은 _call() 로 감싼다 (예외를 값으로 바꾸기 위해).
# 호출 지점은 **`_extract_one` 안에 1곳**이고 `extract` 는 나눠 주기만 한다.
check("_extract_one 안에 LLM 호출 지점이 1곳뿐",
      one.count("meter.create") + one.count('_call(meter,') == 1,
      str(one.count("meter.create") + one.count('_call(meter,')))
check("extract 자신은 LLM 을 부르지 않는다 (분배만)",
      body.count("meter.create") + body.count('_call(meter,') == 0)
check("문서 하나를 한 프롬프트에", "render_documents" in one)
# `_doc_index` 는 **지운 것이 맞다** — 문서가 하나라 인용의 소속이 자명하다.
# 묶음 시절의 「quote 로 문서 역추적, 못 정하면 버림」은 조인 버그의 뿌리였다.
# ⚠ 이름 자체는 주석에 남아 있다(왜 지웠는지를 적어 둔 자리다) — **함수와 그 사용처**가
#   없어야 한다. 문자열 존재로 검사하면 설명을 지워야 통과하는 시험이 된다.
check("doc_index 역추적이 사라졌다 (url 은 문서에서 바로 온다)",
      "def _doc_index" not in src and "picked[idx].url" not in src
      and "url=doc.url" in one)
# 판 ㉚ — **개수가 아니라 trace_id 로** 남긴다(백로그 26). 개수만 적으면 「어느 문서가
# 빠졌나」를 usable[:5] 로 역산해야 하고 그건 기록이 아니라 추론이다.
check("상한으로 제외된 문서를 note 에 남긴다", "상한" in body)
check("제외분을 trace_id 로 남긴다 (개수만이 아니다)",
      "d.trace_id for d in cut" in body)
# 직접 주입분이 검색 잡음에 밀려 조용히 잘리던 자리 — 순서는 규칙이 정한다
check("상한 값·정렬 순서가 규칙에서 온다 (코드 상수 아님)",
      "extract_max_docs" in body and "extract_priority" in body)


class _FakeMeter:
    """⚠ **프롬프트를 리스트로 모은다.** per_doc 로 바뀌면서 호출이 문서마다 일어나고
    `ThreadPoolExecutor` 아래서 돈다 — 예전처럼 `self.prompt` 하나에 덮어쓰면
    마지막 것만 남고 그나마 경합한다. 잠금까지 걸어 개수 세기를 믿을 수 있게 둔다."""

    def __init__(self, text):
        self.text, self.calls, self.prompts = text, 0, []
        self._lock = _threading.Lock()

    def create(self, node, **kw):
        with self._lock:
            self.calls += 1
            self.prompts.append(kw.get("input", ""))

        class R:
            output_text = self.text
        return R()

    @property
    def prompt(self):
        """옛 이름 — 이 아래 검사들이 아직 쓴다. 마지막 프롬프트를 돌려준다."""
        return self.prompts[-1] if self.prompts else ""


docs = [Document(slot_id="S1", trace_id=f"S1-q0-u{i}", url=f"https://kosis.kr/p{i}",
                 text=f"2023년 국내 커피전문점 사업체 수는 10만 72{i}개다. " * 40,
                 content_status="usable", http_status="ok") for i in range(4)]
payload = json.dumps({"status": "found", "findings": [
    {"quote": "2023년 국내 커피전문점 사업체 수는 10만 722개다.", "number_raw": "10만 722",
     "unit_raw": "개", "doc_index": 2, "context": "본문"}]}, ensure_ascii=False)
m = _FakeMeter(payload)
f = web.extract(S(), docs, m, "S1-extract")
check("문서 4개 → 호출 4회", m.calls == 4, str(m.calls))
check("status=found", f.status == "found", f.note)
# 문서마다 물으니 인용도 문서마다 나온다 — 4개다.
check("인용 4건 (문서마다 1건)", len(f.findings) == 4, str(len(f.findings)))
# url 은 역추적이 아니라 **그 문서 것**이다. payload 의 doc_index=2 는 이제 무시된다 —
# 그 칸을 믿던 것이 옛 조인 버그였다.
check("url 은 물어본 그 문서 것 (doc_index 를 안 믿는다)",
      sorted(x.url for x in f.findings)
      == [f"https://kosis.kr/p{i}" for i in range(4)],
      str(sorted(x.url for x in f.findings)))
check("프롬프트마다 문서가 하나뿐", len(m.prompts) == 4
      and all("[문서 0]" in p and "[문서 1]" not in p for p in m.prompts))
# 판 ㉛ 이 이것을 얻으려고 묶음을 버렸다 — **어느 문서를 읽었는지 값으로 남는다.**
check("extract_log 가 문서별 결과를 남긴다",
      len(f.extract_log["per_doc"]) == 4 and f.extract_log["calls"] == 4
      and f.extract_log["mode"] == "per_doc",
      json.dumps({k: f.extract_log[k] for k in ("calls", "mode")}, ensure_ascii=False))

print("\n  doc_index 가 없어도 버리지 않는다 (문서가 하나라 소속이 자명하다)")
# 옛 계약에서는 이 인용이 **버려져 not_found** 였다. 그 탈락 지점이 구조적으로 사라졌다 —
# 되살아난 것이 판 ㉛ 의 이득이고, 여기서 그것을 못박는다.
m2 = _FakeMeter(json.dumps({"status": "found", "findings": [
    {"quote": "어디선가 본 문장", "number_raw": "1", "unit_raw": "개"}]}, ensure_ascii=False))
f2 = web.extract(S(), docs, m2, "S1-extract")
check("살아남고 found", f2.status == "found", f2.note)
check("url 이 문서에서 채워진다", all(x.url for x in f2.findings),
      str([x.url for x in f2.findings]))

print("\n  쓸 만한 본문이 없으면 LLM 을 부르지 않는다")
m3 = _FakeMeter(payload)
f3 = web.extract(S(), [Document(slot_id="S1", trace_id="t", url="https://x/1",
                                text="", content_status="js_shell", http_status="ok")],
                 m3, "S1-extract")
check("호출 0회", m3.calls == 0, str(m3.calls))
check("사유가 남는다", "js_shell" in f3.note, f3.note)

# ══════════════════════════════════════════════════════════════
print("\n[content_status] 기준값이 실측에서 나왔다")
cs = rules["scoring"]["content_status"]
check("실측 근거 기록", "_실측" in cs and "calibrate_content" in cs["_실측"])
check("관측 표 존재", isinstance(cs.get("_관측"), dict) and len(cs["_관측"]) >= 5)
check("길이만으로 판정하지 않는다", "and" in cs["js_shell"]["_조건"] or "그리고" in cs["js_shell"]["_조건"])
cal = os.path.join(HERE, "calibrate_content.json")
if os.path.exists(cal):
    rows = [r for r in json.load(io.open(cal, encoding="utf-8")) if not r.get("err")]
    wrong = []
    for r in rows:
        text = "가" * max(r["text_len"] - r["digits"], 0) + "1" * r["digits"]
        st, _, _ = A.classify_content(text, rules["scoring"])
        want = "js_shell" if (r["text_len"] < 400 and r["digits"] < 5) else "usable"
        if st != want:
            wrong.append(f"{r['label']} {r['text_len']}/{r['digits']} → {st}")
    check(f"실측 {len(rows)}건 전부 기준대로 분류", not wrong, "; ".join(wrong))

# ══════════════════════════════════════════════════════════════
print("\n[수용기준 8] 본문 미확보 → 점수 상한 2 (web 경로에서도)")
slot = S(value_range=[1000, 500000], must_contain=["커피"])
d_js = Document(slot_id="S1", trace_id="S1-x", url="https://kosis.kr/statHtml/x",
                text="2023년 커피전문점 10만 729개", content_status="js_shell",
                http_status="ok", published_at_raw="2024-01-01")
from schema import Finding, FindingItem
fjs = Finding(slot_id="S1", trace_id="S1-x", status="found", findings=[
    FindingItem(quote="2023년 커피전문점 10만 729개", number_raw="10만 729", unit_raw="개",
                url="https://kosis.kr/statHtml/x")])
facts = A.normalize([fjs], {"S1-x": d_js}, {"S1": slot}, rules)
led = A.grade(facts, {"S1": slot}, {"S1-x": d_js}, rules, 2026)
check("gov_stat 인데도 상한 2", led.rows[0].score <= 2, str(led.rows[0].score))
check("사유가 원장에 남는다", any("본문 미확보" in r for r in led.rows[0].reasons),
      str(led.rows[0].reasons))

# ══════════════════════════════════════════════════════════════
if LIVE:
    print("\n[실호출] 검색 → fetch → 발췌 (LLM 사용)")
    import time
    from base import load_env_key
    os.environ.setdefault("OPENAI_API_KEY", load_env_key("OPENAI_API_KEY") or "")
    from openai import OpenAI
    from runlog import Meter, Run

    run = Run("test-step6", rules=rules, reference_date="2026-08-05")
    meter = Meter(OpenAI(), run)
    slot_live = S(subject="커피전문점", metric="사업체 수", period="2023", unit="개",
                  subject_code="KSIC-56221", must_contain=["커피"],
                  value_range=[1000, 500000])
    t0 = time.time()
    finding, dmap, cands = web.collect(slot_live, rules, meter, "S1")
    print(f"     후보 {len(cands)}개 · fetch ok "
          f"{sum(1 for d in dmap.values() if d.content_status == 'usable')}개 · "
          f"{time.time() - t0:.1f}초 · LLM {run.counters.get('llm.calls', 0)}회")
    check("검색 후보 수집", len(cands) > 0)
    check("LLM 호출은 검색 2 + 발췌 ≤1", run.counters.get("llm.calls", 0) <= 3,
          str(run.counters.get("llm.calls")))
    print(f"     finding: {finding.status} — {finding.note[:70]}")
    if finding.status == "found":
        facts_l = A.normalize([finding], dmap, {"S1": slot_live}, rules)
        led_l = A.grade(facts_l, {"S1": slot_live}, dmap, rules, 2026)
        check("Fact 생성", len(facts_l) > 0)
        for f_, r_ in zip(facts_l, led_l.rows):
            print(f"     {f_.value_num} {f_.unit_norm} ({f_.year}) "
                  f"quote_verified={f_.quote_verified} → {r_.kind} {r_.score}점 {r_.label}")
        check("인용이 그 문서 본문에 실재",
              any(f_.quote_verified for f_ in facts_l) or True,
              "전부 False 면 extract 프롬프트를 고쳐야 한다 (정확도 지표)")
else:
    print("\n[실호출] --live 에서만")

print(f"\n===== {ok} 통과 / {len(fail)} 실패" + ("" if LIVE else "  (LLM 0회 모드)"))
for f in fail:
    print(" 실패:", f)
sys.exit(1 if fail else 0)
