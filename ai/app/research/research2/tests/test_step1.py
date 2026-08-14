# -*- coding: utf-8 -*-
"""단계 1 검증 — 타입과 규칙 파일이 약속대로인지만 본다. 로직은 아직 없다.

    python tests/test_step1.py
"""
from __future__ import annotations
import io, json, os, sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
sys.path.insert(0, ROOT)

import schema
from schema import (Concept, Slot, Coverage, Reconciliation, Finding, FindingItem,
                    FORBIDDEN_LLM_FIELDS)
from runlog import Run, load_rules

ok, fail = 0, []


def check(name, cond, detail=""):
    global ok
    if cond:
        ok += 1
        print(f"  OK  {name}")
    else:
        fail.append(f"{name} {detail}")
        print(f"  X   {name} {detail}")


print("[1] 규칙 파일 4개가 읽히고 버전이 있다")
rules = load_rules()
for k in ("whitelist", "scoring", "units", "consistency"):
    check(f"rules/{k} 로드 + version", bool(rules[k].get("version")), rules[k].get("version", ""))

print("\n[2] 규칙 2 — LLM 출력 타입에 금지 필드가 없다")
llm_fields = set(FindingItem.__dataclass_fields__) | set(Finding.__dataclass_fields__)
bad = llm_fields & FORBIDDEN_LLM_FIELDS
check("Finding/FindingItem 에 등급 칸 없음", not bad, f"발견: {bad}")

print("\n[3] 규칙 4 — 근거 0건이면 라벨을 만들 수 없다 (타입이 막는다)")
try:
    Coverage(slot_id="S1", status="보강필요", confirmed=0, total=0, evidence_ids=[])
    check("근거 0건 라벨 거부", False, "예외가 안 났다")
except ValueError:
    check("근거 0건 라벨 거부", True)
c = Coverage(slot_id="S1", status="공백", confirmed=0, total=0, evidence_ids=[])
check("공백은 근거 0건으로 만들 수 있다", c.status == "공백")

print("\n[4] B3 — diverged 면 adopted 가 있을 수 없다")
try:
    Reconciliation(target="SAM", topdown=[1, 2], bottomup=[9, 10], overlap=None,
                   gap_ratio=5.0, status="diverged", adopted=[1, 2])
    check("diverged + adopted 거부", False, "예외가 안 났다")
except ValueError:
    check("diverged + adopted 거부", True)

print("\n[5] 규칙 6 — constraint·가격가설이 조사 뷰에서 빠진다")
cc = Concept(concept_id="C1", name="테스트", problem="p", target="t", solution="s",
             price_hypothesis_krw=30000, constraint={"team": 3, "budget": 1000})
rv = cc.research_view()
check("research_view 에 price_hypothesis_krw 없음", "price_hypothesis_krw" not in rv)
check("research_view 에 constraint 없음", "constraint" not in rv)

print("\n[6] match_key 는 코드로 만들어진다 (자유 텍스트 아님)")
s = Slot(slot_id="S1", var_id="V1", formula_id="F1", claim_type="TAM",
         subject="국내 커피전문점", subject_code="KSIC-56221",
         metric="사업체 수", period="2023", unit="개")
check("subject_code 우선", s.match_key(2022) == "KSIC-56221|사업체 수|대한민국|2022",
      s.match_key(2022))
# 판 ④ — **지역이 키에 든다.** 없던 시절엔 「서울 …2024」와 「전국 …2024」가 같은 키라
# 두 값이 한 버킷에 앉았고 R11(같은 지표가 두 값)이 **blocker 로 터졌다**(dry-04b 실측).
_seoul = Slot(slot_id="S5", var_id="V6", formula_id="F1", claim_type="SAM",
              subject="국내 커피전문점", subject_code="KSIC-56221",
              metric="사업체 수", period="2023", unit="개", region="서울")
check("지역이 다르면 키가 갈린다 (전국 ≠ 서울)",
      _seoul.match_key(2022) != s.match_key(2022),
      f"{_seoul.match_key(2022)} vs {s.match_key(2022)}")
check("  같은 지역·연도면 여전히 같은 키다 (교차확인은 살아 있다)",
      Slot(slot_id="SX", var_id="VX", formula_id="F1", claim_type="TAM",
           subject="국내 커피전문점", subject_code="KSIC-56221", metric="사업체 수",
           period="2024", unit="개").match_key(2022) == s.match_key(2022))

print("\n[7] 로깅 — run.jsonl 한 줄에 trace_id·input_hash 가 붙는다")
run = Run("test-step1", rules=rules, reference_date="2026-08-05")
run.log("a3_extract", Finding(slot_id="S1", trace_id="S1-q0-u3", status="found",
                              findings=[FindingItem(quote="q", number_raw="30만", unit_raw="명")]))
rows = [json.loads(l) for l in io.open(run.jsonl, encoding="utf-8") if l.strip()]
last = rows[-1]
check("trace_id 기록", last["trace_id"] == "S1-q0-u3")
check("input_hash 기록", bool(last["input_hash"]))
check("status 기록", last["status"] == "found")
check("trace() 역추적", len(run.trace("S1-q0")) >= 1)

print("\n[7-b] 구멍 1 - min_facts 미달 슬롯은 '충족'이어도 thin 으로 표시된다")
thin = Coverage(slot_id="S2", status="충족", confirmed=1, total=1,
                evidence_ids=["F001"], min_facts=2)
check("1건짜리 '충족' 이 thin 으로 잡힘", thin.thin is True)
check("retry_hint 자동 생성", "재조사" in (thin.retry_hint or ""), thin.retry_hint or "")
fat = Coverage(slot_id="S3", status="충족", confirmed=3, total=5,
               evidence_ids=["F002", "F003", "F004"], min_facts=2)
check("기준 충족 슬롯은 thin 아님", fat.thin is False)
check("Slot.accept 기본값에 min_facts", s.accept.get("min_facts") == 2, str(s.accept))
check("scoring 규칙에 default_min_facts", rules["scoring"]["coverage"]["default_min_facts"] == 2)
check("retry_hint 발생처가 A4/C2 둘",
      set(rules["consistency"]["retry_hint"]["sources"]) == {"A4", "C2"})

print("\n[7-c] 보고서 7장 키 목록이 정의돼 있다 (침묵 방지)")
check("NOT_FOUND_KEYS 필수 항목 전부",
      {"empty_slots", "thin_slots", "unfilled_vars", "suspect_var", "off_slot",
       "adapters", "retry_hints", "unknown_error_codes"} <= set(schema.NOT_FOUND_KEYS),
      str(schema.NOT_FOUND_KEYS))
check("thin_slots 포함", "thin_slots" in schema.NOT_FOUND_KEYS)
check("adapters 포함", "adapters" in schema.NOT_FOUND_KEYS)

print("\n[8] 규칙 5 — not_configured 가 result.json 까지 간다")
run.set_adapter("kosis", "not_configured", "KOSIS 키 없음")
run.set_adapter("dart", "not_configured", "DART 키 없음")
run.set_adapter("web", "ok")
res = run.finish(concept=cc, slots=[s])
check("adapters 기록", res["adapters"]["kosis"] == "not_configured")
check("coverage_caveat 생성", "통계 API 미사용" in (res["coverage_caveat"] or ""))

print("\n[9] 규칙 7 — result.json 에 규칙이 값으로 복사된다")
check("rules 값 복사", res["rules"]["scoring"]["version"] == rules["scoring"]["version"])
rules["scoring"]["fresh_years"] = 999          # 원본을 바꿔도
res2 = json.load(io.open(os.path.join(run.dir, "result.json"), encoding="utf-8"))
check("과거 기록이 소급해 안 바뀜", res2["rules"]["scoring"]["fresh_years"] != 999,
      str(res2["rules"]["scoring"]["fresh_years"]))

print("\n[10] 기준일이 실행에 박힌다 (오늘 날짜에 의존하지 않는다)")
check("reference_date 고정", res["reference_date"] == "2026-08-05")

print(f"\n===== {ok} 통과 / {len(fail)} 실패")
for f in fail:
    print(" 실패:", f)
sys.exit(1 if fail else 0)
