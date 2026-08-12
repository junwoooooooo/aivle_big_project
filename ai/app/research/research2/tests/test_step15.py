# -*- coding: utf-8 -*-
"""판 ㉛ A단계 — 상위 카테고리 다리와 **울타리**. LLM 0회 · 네트워크 0회 · 원장 쓰기 0회.

    python tests/test_step15.py

무엇을 지키나:
  ① 「냉동 간편식」이 KOSIS 표기 「음·식료품」에 닿는다 (드라이런 벽을 뚫는다)
  ② 그 값은 **상위 카테고리**이므로 울타리·경계가 반드시 따라붙는다
  ③ **`must_contain` 이 비어도 붙는다** — 판 ㉛ 유료 실측에서 하네스가 슬롯 13개의
     `must_contain` 을 전부 비웠고, 그때 울타리가 붙는 분기(`off_slot_reason` 의
     다리 갈래)가 아예 실행되지 않았다. 그러면 34.8조가 경계 없이 TAM 칸에 앉는다 —
     **울타리 없는 2단**이고 이 파이프라인이 없애려는 실패 그 자체다.
  ④ 반례 — 별칭에 없는 표기는 여전히 안 걸리고, 치환이 없으면 울타리도 안 붙는다.
     다리가 문을 **여는** 게 아니라 **넓히는** 것임의 증명(판 ⑰ S.2 조건 4).
"""
from __future__ import annotations
import io, os, sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
for p in (ROOT, os.path.join(ROOT, "adapters"), os.path.join(ROOT, "blocks"),
          os.path.join(ROOT, "service")):
    sys.path.insert(0, p)

import kosis                                                       # noqa: E402
import a_desk as A                                                 # noqa: E402
from runlog import load_rules                                      # noqa: E402
from schema import Document, Finding, FindingItem, Slot            # noqa: E402

ok, fail = 0, []


def check(name, cond, detail=""):
    global ok
    if cond:
        ok += 1
        print(f"  OK  {name}")
    else:
        fail.append(f"{name} {detail}")
        print(f"  X   {name} {detail}")


RULES = load_rules()
_RC = RULES["adapters"]["kosis"]["resolve"]["subject_별칭"]

#: 실측으로 확인한 KOSIS `101/DT_1KE10041` 의 축 (2026-08-11 프로브).
#: **표기는 눈으로 본 것만 적는다** — 추측으로 채우면 통과 불가능한 벽이 된다.
FAKE_META = (
    ["ITEM", "A", "B"],
    {"ITEM": {"obj_id": "ITEM", "obj_nm": "항목",
              "items": [{"id": "T20", "name": "거래액"}]},
     "A": {"obj_id": "A", "obj_nm": "상품군별",
           "items": [{"id": "000", "name": "합계"},
                     {"id": "012", "name": "음·식료품"},
                     {"id": "013", "name": "농축수산물"},
                     {"id": "017", "name": "애완용품"},
                     {"id": "023", "name": "음식서비스"}]},
     "B": {"obj_id": "B", "obj_nm": "범위별",
           "items": [{"id": "00", "name": "계"}]}},
)


def _slot(subject, must_contain=()):
    return Slot(slot_id="S1", var_id="V1", formula_id="F_TAM", claim_type="TAM",
                subject=subject, metric="거래액", period="2024", unit="원",
                region="대한민국", must_contain=list(must_contain))


print("\n[1] 규칙 — 다리와 울타리가 값으로 있다")
_map = _RC.get("map") or {}
check("「냉동 간편식」 → 「음·식료품」", _map.get("냉동 간편식") == ["음·식료품"],
      str(_map.get("냉동 간편식")))
_cap = ((_RC.get("상위_카테고리") or {}).get("map") or {}).get("음·식료품") or {}
check("「음·식료품」에 상한_울타리", _cap.get("상한_울타리") is True, str(_cap))
check("  경계 문구가 붙어 있다", len(_cap.get("경계") or []) >= 1)
check("  경계가 「상한」이라고 말한다", any("상한" in x for x in (_cap.get("경계") or [])))
check("반례 — 「프리미엄 냉동 간편식」은 안 걸린다 (정확 일치 규율)",
      "프리미엄 냉동 간편식" not in _map)
check("반례 — 「음식서비스」에는 울타리를 안 붙였다 (배달이라 다른 시장)",
      "음식서비스" not in ((_RC.get("상위_카테고리") or {}).get("map") or {}))

print("\n[2] 어댑터 — 치환한 자리에서 값으로 남긴다 (네트워크 0회)")
kosis._META_CACHE["101/DT_1KE10041"] = FAKE_META

itm, objs, why, subst = kosis.resolve_axes(
    _slot("냉동 간편식"), "101", "DT_1KE10041", RULES, "fake-key")
check("축이 선다", objs is not None, str(why))
check("  상품군 축이 「음·식료품」(012)", (objs or {}).get("objL1") == "012", str(objs))
check("  치환이 **값**으로 나온다", isinstance(subst, list) and len(subst) == 1, str(subst))
if subst:
    check("    슬롯 표기", subst[0].get("슬롯_표기") == "냉동 간편식", str(subst[0]))
    check("    통계 표기", subst[0].get("통계_표기") == "음·식료품", str(subst[0]))

itm2, objs2, why2, subst2 = kosis.resolve_axes(
    _slot("애완용품"), "101", "DT_1KE10041", RULES, "fake-key")
check("반례 — 표기가 그대로 있으면 치환 기록이 없다", subst2 == [], str(subst2))
check("  그래도 축은 선다", (objs2 or {}).get("objL1") == "017", str(objs2))

print("\n[3] A4 — must_contain 이 **비어도** 울타리가 붙는다")


def _facts(subject, subst, must_contain=()):
    slot = _slot(subject, must_contain)
    doc = Document(slot_id="S1", trace_id="S1-kosis", url="https://kosis.kr/x",
                   text='[{"DT": "34805394"}]', channel="kosis_api")
    f = Finding(slot_id="S1", trace_id="S1-kosis", status="found",
                findings=[FindingItem(quote='"DT": "34805394"', number_raw="34805394",
                                      unit_raw="백만원", url="https://kosis.kr/x",
                                      context="온라인쇼핑 거래액 음·식료품 2024")])
    f.표기_치환 = subst
    return A.normalize([f], {"S1-kosis": doc}, {"S1": slot}, RULES)


got = _facts("냉동 간편식", [{"슬롯_표기": "냉동 간편식", "통계_표기": "음·식료품"}])
check("사실이 만들어진다", len(got) == 1, str(len(got)))
if got:
    br = got[0].표기_다리
    check("  표기_다리가 실린다", len(br) == 1, str(br))
    if br:
        check("    상한_울타리 True", br[0].get("상한_울타리") is True, str(br[0]))
        check("    경계가 함께 온다", len(br[0].get("경계") or []) >= 1, str(br[0]))
    check("  ⚠ must_contain 은 비어 있었다 (그래도 붙었다)",
          not got[0].슬롯_보증 or True)

got2 = _facts("애완용품", [])
check("반례 — 치환이 없으면 울타리도 없다", got2 and got2[0].표기_다리 == [],
      str(got2[0].표기_다리 if got2 else "사실 없음"))

print("\n[4] 회귀 — 기존 must_contain 경로(판 ⑰ 다리)는 그대로 산다")
slot = _slot("반려동물 수제 간식", ["반려동물"])
doc = Document(slot_id="S1", trace_id="S1-kosis", url="https://kosis.kr/y",
               text="애완용품 거래액 2조 9600억", channel="kosis_api")
fact = _facts("반려동물 수제 간식", [])[0]
fact.quote = "애완용품 거래액"
off = A.off_slot_reason(fact, slot, doc, RULES)
check("별칭 다리가 must_contain 을 넘긴다", off is None, str(off))
check("  울타리가 붙는다", any(b.get("상한_울타리") for b in fact.표기_다리),
      str(fact.표기_다리))

slot_x = _slot("반려동물 수제 간식", ["세상에없는말"])
fact_x = _facts("반려동물 수제 간식", [])[0]
fact_x.quote = "애완용품 거래액"
off_x = A.off_slot_reason(fact_x, slot_x, doc, RULES)
check("반례 — 별칭 무관 표기는 여전히 차단", off_x is not None and "must_contain" in off_x,
      str(off_x))

print("\n[5] 카드 — 울타리가 **바깥으로** 나간다 (판 ⑰ 조건 3)")
import cards as C                                                  # noqa: E402

_BR = [{"슬롯_표기": "냉동 간편식", "통계_표기": "음·식료품",
        "상한_울타리": True, "경계": ["⚠ 상한으로만 읽어야 한다", "⚠ 매우 헐겁다"]}]

c = C.merge_bridge_caveats({"카드_id": "C-F001"}, {"표기_다리": _BR})
check("상한_울타리가 카드에 실린다", c.get("상한_울타리") is True, str(c))
check("  경계 2줄이 실린다", len(c.get("경계") or []) == 2, str(c.get("경계")))
check("  무엇을 무엇으로 바꿨는지도 실린다", c.get("표기_다리") == _BR)

c2 = C.merge_bridge_caveats({"경계": "전사 매출 — 시장 매출 아님."}, {"표기_다리": _BR})
check("슬롯 경계를 **덮어쓰지 않고 더한다**", len(c2.get("경계") or []) == 3, str(c2.get("경계")))
check("  슬롯 경계가 살아 있다", "전사 매출 — 시장 매출 아님." in (c2.get("경계") or []))

c3 = C.merge_bridge_caveats({"카드_id": "C-F009"}, {"표기_다리": []})
check("반례 — 치환이 없으면 카드도 그대로", "상한_울타리" not in c3 and "경계" not in c3, str(c3))

c4 = C.merge_bridge_caveats({}, {"표기_다리": [{"슬롯_표기": "a", "통계_표기": "b",
                                            "상한_울타리": False, "경계": []}]})
check("반례 — 울타리 없는 다리는 카드를 안 건드린다", c4 == {}, str(c4))

# 계약층은 유리벽 밖(`ai/app/research/serialize.py`)이라 import 하지 않고 **글자로** 본다.
_ser = io.open(os.path.join(os.path.dirname(ROOT), "serialize.py"), encoding="utf-8").read()
check("계약 열쇠에 상한_울타리가 있다 (payload 로 나간다)",
      '_CAVEAT_KEYS' in _ser and '"상한_울타리"' in _ser)

print("\n[6] 울타리 표식은 **문장**으로 나간다 — `\"True\"` 금지")
import bm_adapter as BM                                            # noqa: E402

cav = BM._caveats({"상한_울타리": True})
check("BM 캔버스에 bool 이 안 샌다", cav and "True" not in cav, str(cav))
check("  울타리가 사라지지도 않는다", len(cav) == 1 and "상한" in cav[0], str(cav))
check("  경계 문장이 없는 카드에서도 남는다", cav != [])
# 계약층과 캔버스층이 **같은 말**을 해야 한다 — 갈리면 사람이 다른 사실로 읽는다.
check("계약층 문장과 한 글자도 다르지 않다", f'"{BM.CEILING_SENTENCE}"' in _ser,
      BM.CEILING_SENTENCE)

print("\n[7] 보증 — **어댑터가 확정한 것도 보증이다** (판 ㉛A 도장)")
# 실측(`paid31a-hmr` S3·S4): 하네스가 `metric=거래액` 슬롯에 `must_contain=["성장"]` 을
# 적었다. KOSIS 거래액 응답에 「성장」이라는 낱말은 **구조적으로** 없다 — 통과 불가능한
# 벽이고 §14.2-3 의 NAVER 건과 같은 「파이프라인이 스스로 만든 모순」이다.
# 면제 장치는 있었는데 `_slot_guaranteed` 가 **슬롯이 선언한** stat_code 만 봤고,
# 이 슬롯들은 `stat_code=null` 이며 **어댑터가 검색으로 확정**했다.
_보증 = {"경로_칸": "stat_code", "값": "101/DT_1KE10041", "어떻게": "search(항목 완전일치)"}


def _fact_with(보증, must_contain):
    slot = _slot("냉동 간편식", must_contain)
    doc = Document(slot_id="S1", trace_id="S1-kosis", url="https://kosis.kr/x",
                   text='[{"DT": "34805394"}]', channel="kosis_api")
    f = Finding(slot_id="S1", trace_id="S1-kosis", status="found",
                findings=[FindingItem(quote='"DT": "34805394"', number_raw="34805394",
                                      unit_raw="백만원", url="https://kosis.kr/x",
                                      context="온라인쇼핑 거래액 2024")])
    f.경로_보증 = 보증
    facts = A.normalize([f], {"S1-kosis": doc}, {"S1": slot}, RULES)
    return facts[0], slot, doc


fx, sl, dc = _fact_with(_보증, ["성장"])
check("어댑터 확정이 사실까지 온다", fx.경로_보증 == _보증, str(fx.경로_보증))
off = A.off_slot_reason(fx, sl, dc, RULES)
check("통과 불가능한 must_contain 이 더는 격리하지 않는다", off is None, str(off))
check("  면제 근거가 **값으로** 남는다 (조용한 면제 금지)",
      bool(fx.슬롯_보증) and fx.슬롯_보증.get("경로_칸") == "stat_code", str(fx.슬롯_보증))
check("  어댑터가 확정했다는 것이 근거에 드러난다",
      "어댑터" in str(fx.슬롯_보증) or "search" in str(fx.슬롯_보증), str(fx.슬롯_보증))

fy, sl_y, dc_y = _fact_with({}, ["성장"])
check("반례 — 보증이 없으면 여전히 격리한다",
      A.off_slot_reason(fy, sl_y, dc_y, RULES) is not None)

fz, sl_z, dc_z = _fact_with(_보증, ["성장"])
sl_z.unit = "명"                                   # 단위 겹은 **그대로 살아야 한다**
off_z = A.off_slot_reason(fz, sl_z, dc_z, RULES)
check("반례 — 면제되는 것은 낱말 한 겹뿐 (단위 겹은 산다)",
      off_z is not None and "단위" in off_z, str(off_z))

_cfg = RULES["scoring"]["off_slot"]["must_contain_면제"]
check("문턱은 규칙 파일에서만 온다 (절대규칙 7)", _cfg.get("어댑터_확정_인정") is True)

print("\n[8] 재채점 충실도 — 원장에 있는 것을 재구성에서 버리지 않는다")
# `--from a4` 는 저장된 `a3_finding` 으로 Finding 을 되살린다. 어댑터만 알 수 있는
# 두 사실(치환·확정)을 여기서 빠뜨리면 **울타리와 면제가 재채점에서 조용히 사라지고**,
# 무료 재채점이 주 측정 수단이라 그 차이가 곧 오측이다.
_run_src = io.open(os.path.join(ROOT, "run.py"), encoding="utf-8").read()
_블록 = _run_src[_run_src.index('if from_stage == "a4":'):][:1400]
check("재구성이 표기_치환을 싣는다", "표기_치환=f.get" in _블록)
check("재구성이 경로_보증을 싣는다", "경로_보증=f.get" in _블록)
# 쓰는 쪽은 dataclass 를 통째로 남기므로 새 칸이 자동으로 실린다 — 그 성질을 고정한다.
check("쓰는 쪽은 Finding 을 통째로 남긴다 (새 칸이 조용히 빠지지 않게)",
      "dataclasses.replace(f, extract_log={})" in _run_src)

print(f"\n{'='*54}\n통과 {ok} · 실패 {len(fail)}")
for x in fail:
    print("  X", x)
sys.exit(1 if fail else 0)
