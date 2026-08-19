# -*- coding: utf-8 -*-
"""전 구간 실행기 — A1 → A2 → A3 → A4 → B → C.

    python run.py                          # 새 실행
    python run.py --id 2026-08-05-A
    python run.py --slots data/slots.json  # A1 을 건너뛰고 사람이 적은 슬롯으로
    python run.py --from a4                # 기록에서 읽어 A4 부터 다시 (LLM 0회)

병렬은 A3 슬롯별 수집만(워커 4~6). A4 부터는 전체를 모아 순차 —
중복 제거와 교차확인이 다른 슬롯 결과를 봐야 한다.
"""
from __future__ import annotations

import argparse, dataclasses, io, json, os, sys
from concurrent.futures import ThreadPoolExecutor
from datetime import date

HERE = os.path.dirname(os.path.abspath(__file__))
for p in (HERE, os.path.join(HERE, "blocks"), os.path.join(HERE, "adapters")):
    sys.path.insert(0, p)

import a_desk as A4
import a_design as A1
import b_estimate as B
import c_chain as C
import dart, kosis, web
import runpath
from base import load_env_key
from runlog import Meter, Run, load_rules
from schema import (Candidate, Concept, Document, Finding, FindingItem, Formula, FormulaVar,
                    Slot, to_dict, 경계_승격)

MAX_WORKERS = 5

def mk_slot(x: dict) -> Slot:
    """슬롯 dict → `Slot`. **경계급 키는 승격 필드로 실어 나른다.**

    이것이 없으면 「전사 매출 — 시장 매출 아님」 같은 경계가 **최종 매체에 도달하지 못한다**
    (판 ㉘ 감사 (나) 1건 — 지워진 게 아니라 **애초에 길이 없었다**).
    **경계는 쓴 곳이 아니라 도달한 곳에서만 존재한다.**
    """
    d = {k: v for k, v in x.items() if not k.startswith("_")}
    for old, new in 경계_승격.items():
        if x.get(old) not in (None, "", {}) and d.get(new) in (None, "", {}):
            d[new] = x[old]
    # 쓴 주체는 **필드가 아니라 기록으로** 가른다 — 주체별로 키가 갈리면 여섯 번째 분열이다.
    if d.get("경계") and not d.get("경계_출처"):
        d["경계_출처"] = "하네스" if x.get("_경계_proxy") else "사람"
    known = {f.name for f in dataclasses.fields(Slot)}
    return Slot(**{k: v for k, v in d.items() if k in known})


def load_concept(path: str) -> Concept:
    d = json.load(io.open(path, encoding="utf-8"))
    return Concept(**{k: v for k, v in d.items() if not k.startswith("_")})


def _load_collection(source_run: str, from_stage: str, slots, rules, meter, run):
    """원본 실행의 문서를 그대로 읽는다. from_stage=extract 면 발췌만 다시 한다."""
    import web
    # 재사용할 원장은 씨앗(`runs/`)일 수도 수집이 만든 것(`runs-generated/`)일 수도 있다.
    src = runpath.read_dir(source_run)
    rows = [json.loads(l) for l in io.open(os.path.join(src, "run.jsonl"), encoding="utf-8")
            if l.strip()]
    bodies = json.load(io.open(os.path.join(src, "a3_bodies.json"), encoding="utf-8"))
    res = json.load(io.open(os.path.join(src, "result.json"), encoding="utf-8"))

    if res.get("input", {}).get("slots"):        # 슬롯도 원본 것을 쓴다 (같은 조건으로)
        slots = [mk_slot(x) for x in res["input"]["slots"]]

    # 식도 원본 것을 복원한다. **없으면 B 가 통째로 빈손이 된다** — 추정도 대조도 안 나오고
    # 보고서의 headline_numbers 가 [] · unfilled_vars 가 0 으로 **거짓 보고**된다.
    # B 는 LLM 0회 결정론이라 파생 실행에서도 돌지 않을 이유가 없다.
    formulas = [Formula(**{**p, "vars": [FormulaVar(**v) for v in p.get("vars", [])]})
                for p in [r["payload"] for r in rows if r["node"] == "a1_formula"]]

    docs = {}
    downgraded: dict = {}
    for d in [r["payload"] for r in rows if r["node"] == "a3_document"]:
        d = dict(d)
        d["text"] = bodies.get(d["trace_id"], "")
        # 재실행에서 content_status 를 통째로 다시 매기지는 않는다 — 원본 판정을 존중한다.
        # 다만 **새로 생긴 강등만** 내려 적용한다. 인코딩이 깨진 본문이나 로딩 껍데기를
        # usable 로 두면 깨진 인용이 근거로 올라가고 추출률 분모가 부푼다.
        # 규칙을 고친 효과가 --from a4 로 검증돼야 한다. **올려 주지는 않는다.**
        if d.get("content_status") == "usable":
            new = ("mojibake" if A4.is_mojibake(d["text"], rules["scoring"])
                   else "js_shell" if A4.is_loading_shell(d["text"], rules["scoring"])
                   else None)
            if new:
                d["content_status"] = new
                downgraded[new] = downgraded.get(new, 0) + 1
        docs[d["trace_id"]] = Document(**d)
    if downgraded:
        # 무엇으로 내렸는지 종류별로 찍는다 — 'mojibake 4건' 으로 뭉뚱그리면
        # 로딩 껍데기를 인코딩 문제로 오진한다
        print("    재판정: " + " · ".join(f"usable → {k} {v}건"
                                          for k, v in sorted(downgraded.items())))

    if from_stage == "a4":
        findings = []
        for f in [r["payload"] for r in rows if r["node"] == "a3_finding"]:
            findings.append(Finding(
                slot_id=f["slot_id"], trace_id=f["trace_id"], status=f["status"],
                findings=[FindingItem(**i) for i in f.get("findings", [])],
                note=f.get("note", ""),
                # ⚠ **원장에 있는 것을 재구성에서 버리면 재채점이 원판과 달라진다.**
                #   이 둘은 어댑터만 알 수 있는 사실(무엇을 무엇으로 치환했는가 ·
                #   무엇으로 대상을 확정했는가)이라 `--from a4` 에서는 **되살릴 방법이 없다** —
                #   빠지면 상한 울타리와 낱말 대조 면제가 재채점에서 **조용히 사라진다.**
                #   무료 재채점이 이 프로젝트의 주 측정 수단이라 그 차이가 곧 오측이다.
                표기_치환=f.get("표기_치환") or [],
                경로_보증=f.get("경로_보증") or {}))
    else:                                        # extract 부터 — 저장된 문서로 발췌만 다시
        findings = []
        by_slot = {}
        for d in docs.values():
            by_slot.setdefault(d.slot_id, []).append(d)
        for s_ in slots:
            findings.append(web.extract(s_, by_slot.get(s_.slot_id, []), meter,
                                        trace_id=f"{s_.slot_id}-extract", rules=rules))
    return slots, formulas, findings, docs, dict(res.get("adapters") or {})


def _empty_docs(docs: dict) -> list:
    """HTTP 200 을 받고도 본문이 0자인 문서. §7 `fetch_empty` 의 재료.

    **거름망이 아니다.** 예전에는 이것이 `content_status="empty"` 한 칸에 묶여
    「걸렀다」로 세어졌고, 그래서 깔때기를 읽으면 자료가 나쁜 것처럼 보였다.
    실측(판 ㉛)은 6건이 전부 JS 렌더 페이지임을 보였다 — 다음 행동이 「더 찾아라」가
    아니라 「이 페이지는 이 수단으로 못 읽는다」다.
    """
    return [{"slot_id": d.slot_id, "url": d.url, "trace_id": d.trace_id,
             "why": "HTTP 200 인데 본문 0자 — 본문이 JS 로 그려졌을 수 있다"}
            for d in docs.values()
            if d.http_status == "ok" and d.content_status == "empty" and d.url]


def _capped_docs(findings: list) -> list:
    """발췌 상한에 걸려 **묻지도 않은** usable 문서. §7 `extract_capped` 의 재료.

    `url_filtered`(열지도 않은 URL)와 같은 부류다 — 「찾아도 없다」가 아니라
    「우리가 안 봤다」. 이 둘을 자료 부재와 섞으면 §7 이 거짓이 된다.
    """
    return [{"slot_id": f.slot_id, "trace_id": t,
             "why": f"발췌 상한(extract_max_docs)으로 제외 — 모델에게 묻지 않았다"}
            for f in findings
            for t in (getattr(f, "extract_log", None) or {}).get("cut") or []]


def _log_findings(run, findings: list) -> None:
    """`a3_finding` 과 `a3_extract` 를 **함께** 남긴다 — 로그 자리가 셋이라 한 곳에 모은다.

    발췌 깔때기는 예전에 `note` 문자열 안에만 있었다(「상한 5 으로 2개 제외: [...]」).
    문자열은 셀 수 없어서 「우리가 버렸다」와 「자료가 없다」가 구별되지 않았다 —
    성적표의 미확보가 무엇 때문인지 못 가르던 뿌리 중 하나다(백로그 26).

    ⚠ 같은 사실을 두 노드에 싣지 않는다. `a3_finding` 에서는 `extract_log` 를 **뺀다** —
      두 곳에 두면 갈라지고, 갈라지면 어느 쪽이 참인지 나중에 못 따진다(실측 6회).
    """
    ex = [{"slot_id": f.slot_id, "trace_id": f.trace_id, **f.extract_log}
          for f in findings if getattr(f, "extract_log", None)]
    if ex:
        run.log_many("a3_extract", ex)
    run.log_many("a3_finding",
                 [dataclasses.replace(f, extract_log={}) for f in findings])


class _WebState:
    """web 어댑터의 상태를 kosis/dart 의 AdapterResult 와 같은 모양으로 감싼다."""

    def __init__(self, state):
        self.adapter_state = state


def collect_slot(slot: Slot, route, rules: dict, meter):
    """슬롯 하나를 라우팅대로 수집한다.

    반환: `(어댑터, Finding, 문서, 후보, 결과, 곁들인 상태, 폴백 이벤트)`.
    **main 의 클로저가 아니라 모듈 함수인 이유는 폴백을 테스트로 잴 수 있어야 하기 때문이다** —
    full-02 는 폴백이 없어 10슬롯이 죽었는데 그게 실제 실행 전에는 안 보였다.
    """
    ad = route.adapter
    if ad == "kosis":
        res = kosis.collect(slot, rules)
        dmap = {res.document.trace_id: res.document}
        if not A4.should_fallback(route, res.finding, rules):
            return ad, res.finding, dmap, [], res, [], None
        # ── 폴백 — kosis 가 못 찾았다. full-02 는 여기가 없어 그대로 죽었다.
        #    kosis 의 실패 문서는 **버리지 않고 함께 남긴다** (실패는 값이다 — 규칙 5).
        f, wmap, cands, state = web.collect(slot, rules, meter, slot.slot_id)
        ev = {"slot_id": slot.slot_id, "from": "kosis", "to": route.fallback_to,
              "why": res.finding.note, "kosis_status": res.finding.status,
              "web_status": f.status}
        return (route.fallback_to, f, {**dmap, **wmap}, cands, _WebState(state),
                [("kosis", res.adapter_state)], ev)
    if ad == "dart":
        res = dart.collect(slot, rules)
        return ad, res.finding, {res.document.trace_id: res.document}, [], res, [], None
    f, dmap, cands, state = web.collect(slot, rules, meter, slot.slot_id)
    return ad, f, dmap, cands, _WebState(state), [], None


class CollectError(RuntimeError):
    """부르는 쪽이 잘못 줬다. **CLI 면 사용법 오류, 서버면 400 이다.**

    예전에는 이 자리가 `ap.error(...)` 였다 — argparse 객체를 본체가 들고 있어야 했고,
    그래서 본체를 함수로 부를 수가 없었다. 메시지는 그대로 옮긴다.
    """


@dataclasses.dataclass
class CollectOptions:
    """수집 한 판의 입력. **필드 이름은 CLI 인자의 `dest` 와 같아야 한다** —
    `main()` 이 `vars(argparse.Namespace)` 를 그대로 부어 만들기 때문이다.

    기본값도 CLI 와 같게 둔다. 두 곳이 갈리면 「CLI 로는 되는데 서버로는 안 된다」가 된다.
    """

    id: str
    concept: str
    slots: str = ""
    human_slots: str = ""
    as_of: str = ""
    from_stage: str = ""
    source_run: str = ""
    slots_from: str = "source"
    direct_only: bool = False
    direct_urls: str = ""
    collect_slots: str = ""
    formulas: str = ""
    search_prompt: str = ""
    #: 슬롯당 검색 표본 수. 0 이면 규칙값(기본 2). **검색어가 아니라 뽑기 횟수다.**
    search_samples: int = 0

    def __post_init__(self):
        # CLI 기본값과 같은 자리. 서버에서 부를 때 안 채워도 CLI 와 같게 돈다.
        self.human_slots = self.human_slots or os.path.join(HERE, "data", "slots.json")
        self.as_of = self.as_of or date.today().isoformat()


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--id", default=date.today().isoformat() + "-A")
    ap.add_argument("--concept", default=os.path.join(HERE, "data", "concept.json"))
    ap.add_argument("--slots", default="")
    ap.add_argument("--human-slots", default=os.path.join(HERE, "data", "slots.json"))
    ap.add_argument("--as-of", default=date.today().isoformat())
    ap.add_argument("--from", dest="from_stage", default="", choices=["", "extract", "a4"],
                    help="extract=저장된 문서로 발췌부터 · a4=채점부터(LLM 0회)")
    ap.add_argument("--source-run", default="",
                    help="--from 을 쓸 때 어느 실행의 수집을 재사용할지")
    # 사람 칸은 채점 규칙이라 파생 실행에서 갈아끼울 수 있다. 기본은 source — 기존 동작이
    # 바뀌지 않아야 지금까지의 파생 실행과 같은 축에 남는다.
    ap.add_argument("--slots-from", dest="slots_from", default="source",
                    choices=["source", "current"],
                    help="source=원본 실행의 슬롯 그대로(기본) · "
                         "current=사람 칸(must_contain·must_not_contain·value_range·"
                         "accept.min_facts)만 현재 data/slots.json 값으로 덮어쓴다")
    ap.add_argument("--direct-only", dest="direct_only", action="store_true",
                    help="직접 주입만 하고 검색을 돌리지 않는다(옛 배타 모드 · audit-final 재현용). "
                         "기본은 검색 결과에 **합치기**다")
    # **쉼표로 여러 사양**을 줄 수 있다. 채널 태그가 사양마다 하나라서다 — 사양을 합치면
    # 채널 분모가 뭉뚱그려진다. 예: user_doc 사양 + tavily 사양 + gov_doc 사양.
    ap.add_argument("--direct-urls", dest="direct_urls", default="",
                    help="사람이 지정한 URL 을 **검색 결과에 합쳐** 수집한다(주입분은 검색 0회). "
                         "단독 모드는 --direct-only. 본문은 저장 코퍼스에서 꺼내고 "
                         "심사는 동일하게 — quote_verified·off_slot·값 일치·화자 가드 전부")
    # **부분 수집.** `--from a4`(LLM 0)로 원본 수집을 복원한 뒤 **지정한 슬롯만** 새로
    # 수집해 합친다. 전체 재실행은 비싸기만 한 게 아니라 **위험하다** — 검색은 실행마다
    # 흔들려서(회수율 0.2↔0.5) 이미 확보한 확인됨이 사라질 수 있다. 재수집하지 않은
    # 슬롯은 원본 결과를 그대로 쓰므로 기존 확인됨이 **구조적으로 보존**된다.
    ap.add_argument("--collect-slots", dest="collect_slots", default="",
                    help="쉼표로 슬롯 id 목록. --from 과 함께 쓴다 — 그 슬롯만 새로 수집해 "
                         "복원한 원장에 합친다. 새 슬롯(원본에 없던 것)은 --slots 스냅샷에서 온다")
    ap.add_argument("--formulas", default="",
                    help="사람이 쓴 식 파일(data/formulas.json). 주면 A1 대신 이것을 쓴다 — "
                         "A1 은 슬롯 분산 때문에 켜지 않는다")
    ap.add_argument("--search-samples", dest="search_samples", type=int, default=0,
                    help="슬롯당 검색 표본 수. 기본은 rules.adapters.web.search_samples(=2). "
                         "**검색어가 아니라 뽑기 횟수다** — 문자열은 모델이 정하므로 "
                         "같은 프롬프트에서 독립 표본을 N번 뽑는다. 적중률 1-(1-q)^N")
    ap.add_argument("--search-prompt", dest="search_prompt", default="",
                    help="A3 SEARCH 문안. 기본은 rules.adapters.web.search_prompt(=v1). "
                         "v12-2 는 **미채택** 문안이라 명시적으로 골라야 쓰인다")
    a = ap.parse_args()
    # ★ 판 ㊳ — **`--from` 인데 `--concept` 을 안 주면 멈춘다.**
    #   `--concept` 의 기본값 `data/concept.json` 은 **판마다 갈아 끼우는 작업용 파일**이다.
    #   재채점은 원본의 관측을 그대로 쓰므로, 컨셉만 엉뚱하면 **관측은 HMR 인데 잣대는 카페**가
    #   되고 아무도 모른다 — 실측(2026-08-14): HMR 원장을 `--from a4` 로 재채점했더니
    #   `concept_id=CPT-CAFE-INV` 로 굳어 계열이 C→A 로 갈렸고, TAM 사유가
    #   「점유율 관측이 없다」에서 「전국 사업체 수 확인됨 0건」이라는 **틀린 말**로 바뀌었다.
    #   ⚠ 조용히 되짚어 고르지 않는다 — 되짚기가 틀린 원장이 이미 넷 있다(pipeline.py:59-63).
    #     사람이 명시하게 한다.
    if a.from_stage and not any(x == "--concept" or x.startswith("--concept=")
                                for x in sys.argv[1:]):
        ap.error("--from 을 쓸 때는 --concept 을 **명시**해야 한다. "
                 "기본값 data/concept.json 은 작업용 파일이라 원본 원장과 다를 수 있고, "
                 "그러면 관측은 그대로인데 잣대만 바뀐 채 조용히 채점된다. "
                 f"원본({a.source_run or '<--source-run>'})이 어느 컨셉이었는지는 "
                 "그 원장의 result.json `input.concept.concept_id` 에 있다")
    try:
        return collect(CollectOptions(**vars(a)))
    except CollectError as bad:
        ap.error(str(bad))                      # CLI 에서는 종전과 같은 사용법 오류다


def collect(a: CollectOptions) -> dict:
    """A1 → A2 → A3 → A4 → B → C. **인자 파싱 밖**이라 서버에서도 부를 수 있다.

    예전에는 이 본체가 `main()` 안에 있었고 `argparse.Namespace` 와 `ap.error` 에 묶여
    있어서 **오케스트레이터가 부를 방법이 없었다** — 그래서 수집은 사람이 CLI 를 순서대로
    치는 절차로만 존재했다. 가른 것은 파싱뿐이고 본체는 한 줄도 바꾸지 않았다.

    ⚠ **비싸다.** LLM ≈80회 · 3.5분 이상이고 외부 API(KOSIS·DART·Tavily)를 친다.
      부르는 쪽이 예산과 마감을 들고 있어야 한다.
    """
        # **엔진과 하네스는 같은 키를 쓴다** (판 ⑫ ⑴, 2026-08-09 사용자 결정).
    # 옛 코드는 `AI_API_KEY`(=본제품 AI 서버의 키)를 읽었다. 하네스는
    # `OPENAI_API_KEY` 를 읽었으므로 **한 판 안에서 두 계정에 과금**됐고,
    # 실제로 엔진 쪽 잔액만 0 이 되어 **관문은 돌고 수집만 죽었다.**
    # 「크레딧이 떨어졌다」로 두 번 오진한 원인이 이 분열이다.
    # ⚠ `.env` 의 `AI_API_KEY` 는 **지우지 않는다** — 본제품(compose·ai/·backend)이 쓴다.
    os.environ.setdefault("OPENAI_API_KEY", load_env_key("OPENAI_API_KEY") or "")
    rules = load_rules()
    # ⚠ 규칙 스냅샷(Run)이 뜨기 **전에** 반영한다. 나중에 바꾸면 result.json 에 복사된
    #   규칙은 v1 이라 적혀 있는데 실제로는 다른 문안으로 돌아, 비교 축이 조용히 거짓이 된다.
    if a.search_prompt:
        import prompts as _p
        _p.search_prompt(a.search_prompt)          # 모르는 이름이면 여기서 멈춘다
        rules["adapters"].setdefault("web", {})["search_prompt"] = a.search_prompt
    # 표본 수도 같은 자리에서 심는다. **규칙 파일을 손으로 고쳐 재면 측정 조건이 파일
    # 상태에 숨는다** — 판 ㊱ 에서 실제로 그렇게 쟀고, 다음 판이 무슨 조건이었는지
    # 알려면 파일 이력을 뒤져야 했다. 판마다 명시하면 result.json 에 값째로 남는다.
    if a.search_samples:
        rules["adapters"].setdefault("web", {})["search_samples"] = int(a.search_samples)
    run = Run(a.id, rules=rules, reference_date=a.as_of)
    print(f"    SEARCH 문안 = {rules['adapters'].get('web', {}).get('search_prompt', 'v1')}")
    as_of_year = int(a.as_of[:4])

    if a.from_stage and not a.source_run:
        raise CollectError("--from 을 쓰려면 --source-run 으로 원본 실행을 지정해야 한다")

    concept = load_concept(a.concept)
    # 사람이 쓴 식. **A1 을 켜지 않는 대신 여기서 온다** — 식은 승인 대상이라 파일에 있다.
    human_formulas = []
    if a.formulas:
        raw = json.load(io.open(a.formulas, encoding="utf-8"))["formulas"]
        human_formulas = [Formula(**{**{k: v for k, v in f.items() if not k.startswith("_")},
                                     "vars": [FormulaVar(**{k: v for k, v in x.items()
                                                            if not k.startswith("_")})
                                              for x in f.get("vars", [])]})
                          for f in raw]
        print(f"    식 파일 {a.formulas} → {len(human_formulas)}개 "
              f"({', '.join(f.formula_id for f in human_formulas)})")
    human = json.load(io.open(a.human_slots, encoding="utf-8")).get("slots", []) \
        if os.path.exists(a.human_slots) else []

    from openai import OpenAI
    meter = Meter(OpenAI(), run)

    # ── 재실행 지점 — A1 도 LLM 이다. --from 이면 A1·A2·A3 를 전부 건너뛴다 ──
    if a.from_stage:
        slots, formulas, findings, docs, adapter_states = _load_collection(
            a.source_run, a.from_stage, [], rules, meter, run)
        if human_formulas:
            # 원본 실행이 고정 슬롯 모드였으면 식이 **하나도 없다** — B·C 가 통째로 빈다.
            # 사람이 쓴 식을 여기서 얹는다. 수집은 그대로이므로 재채점의 정의를 벗어나지 않는다.
            formulas = human_formulas
        # 사람 칸은 **채점 규칙**이라 여기서 갈아끼울 수 있다. 이걸 안 하면 사람이
        # must_contain 을 고쳐도 재채점으로 잴 방법이 없다 — 파생 실행은 슬롯을 원본
        # result.json 에서 복원하므로 data/slots.json 이 구조적으로 도달하지 않는다.
        overlay_diff = []
        if a.slots_from == "current":
            slots, overlay_diff = A1.overlay_human_slots(slots, human)
            print(f"    슬롯 사람 칸 덮어씀: {len(overlay_diff)}개 슬롯 "
                  f"({', '.join(d['slot_id'] for d in overlay_diff) or '변화 없음'})")
        # ── 부분 수집 — **지정한 슬롯만** 새로 수집해 합친다 ──────
        if a.collect_slots:
            want = [x.strip() for x in a.collect_slots.split(",") if x.strip()]
            # 새 슬롯은 원본 원장에 없다 — 스냅샷(`--slots`)에서 가져와 얹는다.
            snap = {}
            if a.slots:
                snap = {s["slot_id"]: s for s in
                        json.load(io.open(a.slots, encoding="utf-8"))["slots"]}
            by_id = {s.slot_id: s for s in slots}
            for sid in want:
                if sid not in by_id and sid in snap:
                    s_ = mk_slot(snap[sid])
                    if s_.period_min is None and s_.period_max is None:
                        s_.period_min, s_.period_max = A1.period_window(
                            s_.period, rules, as_of_year)
                    slots.append(s_)
                    by_id[sid] = s_
                elif sid in by_id and sid in snap:
                    # 이미 있는 슬롯이면 **스냅샷 정의로 갈아끼운다** — stat_code 같은
                    # 수집 조건을 고쳤다면 그것이 반영돼야 재수집의 뜻이 산다.
                    s_ = mk_slot(snap[sid])
                    if s_.period_min is None and s_.period_max is None:
                        s_.period_min, s_.period_max = A1.period_window(
                            s_.period, rules, as_of_year)
                    slots[slots.index(by_id[sid])] = s_
                    by_id[sid] = s_
            missing = [x for x in want if x not in by_id]
            if missing:
                raise CollectError(f"--collect-slots 에 없는 슬롯: {missing} "
                                   f"(--slots 스냅샷에 있어야 한다)")
            targets = [by_id[x] for x in want]
            routes = {r.slot_id: r for r in A4.route_sources(targets, rules)}
            print(f"    부분 수집 {want} · 라우팅 "
                  f"{ {r.slot_id: r.adapter for r in routes.values()} }")
            # 재수집한 슬롯의 **옛 결과는 버린다** — 같은 슬롯에 옛 것과 새 것이 같이
            # 서면 어느 쪽이 원장에 앉았는지 말할 수 없다.
            findings = [f for f in findings if f.slot_id not in set(want)]
            docs = {t: d for t, d in docs.items() if d.slot_id not in set(want)}
            with ThreadPoolExecutor(max_workers=MAX_WORKERS) as ex:
                got = list(ex.map(lambda s: collect_slot(s, routes[s.slot_id], rules, meter),
                                  targets))
            for ad, f, dmap, cands, res, extra, ev in got:
                findings.append(f)
                docs.update(dmap)
                if cands:
                    run.log_many("a3_candidate", cands)
                adapter_states[ad] = res.adapter_state if res else "ok"
                for xad, xstate in extra:
                    adapter_states.setdefault(xad, xstate)
                if ev:
                    run.log("a3_fallback", ev)
                    run.count("a3_fallback")
                    # 폴백은 **스스로 내린 결정**이다 — 사람을 안 부르고 경로를 바꿨다.
                    run.decide("어댑터 폴백", ev.get("to") or ev,
                               rule="adapters.kosis.route_metrics.fallback_on",
                               why=str(ev.get("why") or ev)[:160],
                               slot_id=ev.get("slot_id"))
            print(f"    부분 수집 결과 · found "
                  f"{sum(1 for f in got if f[1].status == 'found')}/{len(got)}")

        # ── 파생 실행에도 **직접 주입을 합친다** ─────────────────
        # 예전에는 `--from` 이 여기서 곧장 반환해 `--direct-urls` 가 **조용히 무시**됐다.
        # 표적 1건을 얹으려고 `--from extract` 를 쓰면 17슬롯이 전부 재발췌되어 LLM 이
        # 1~2회가 아니라 17회가 된다. `--from a4`(LLM 0) + 주입 사양 1개면 발췌는
        # **주입된 슬롯 하나에만** 걸린다 — 이것이 「S16 만 발췌하는 경로」다.
        injected_diag = []
        if a.direct_urls:
            d_findings, d_docs, d_states, injected_diag = _merge_direct(
                a.direct_urls, slots, rules, meter, run, _seen_direct(docs))
            findings += d_findings
            docs.update(d_docs)
            for xad, xstate in d_states.items():
                adapter_states.setdefault(xad, xstate)
            print(f"    직접 주입 {len(d_docs)}개 병합 · 발췌 슬롯 "
                  f"{sorted({f.slot_id for f in d_findings})}")
        for ad, st in adapter_states.items():
            run.set_adapter(ad, st, f"{ad}: {st} (source={a.source_run})")
        run.log_many("a1_formula", formulas)
        run.log_many("a1_slot", slots)
        # 파생 실행도 **쓴 문서를 자기 기록에 남긴다.** 안 남기면 추출률 분모가 0 이 되어
        # 재판정(mojibake 등)의 효과를 잴 수 없고, 뷰어의 깔때기·체인도 끊긴다.
        run.log_many("a3_document", [Document(**{**to_dict(d), "text": (d.text or "")[:400]})
                                     for d in docs.values()])
        run.snapshot("a3_bodies", {t: d.text for t, d in docs.items()})
        _log_findings(run, findings)
        print(f"재실행 [--from {a.from_stage}] source={a.source_run} · 슬롯 {len(slots)}개 · "
              f"식 {len(formulas)}개 · 문서 {len(docs)}개 · found "
              f"{sum(1 for f in findings if f.status == 'found')}/{len(findings)}")
        ledger, coverage = A4.normalize_and_grade(findings, docs, slots, rules, as_of_year, run)
        print(f"A4  사실 {len(ledger.facts)}개 · 확인됨 "
              f"{sum(1 for r in ledger.rows if r.label == '확인됨')} · "
              f"격리 {sum(1 for r in ledger.rows if r.label in ('off_slot', '미검증'))} · "
              f"충족 슬롯 {sum(1 for c in coverage if c.status == '충족')}/{len(coverage)}")
        return _finish(a, run, concept, slots, formulas, [], [],
                       {"_note": f"--from {a.from_stage} (A1 은 원본 {a.source_run} 것)"},
                       ledger, coverage, rules, as_of_year, [],
                       slots_overlay_diff=overlay_diff, injected_diag=injected_diag)

    # ── A1 ────────────────────────────────────────────────────
    if a.slots:
        raw = json.load(io.open(a.slots, encoding="utf-8"))["slots"]
        slots = [mk_slot(s) for s in raw]
        # 사람이 적은 period 는 그대로 존중하되 **창은 채운다** — 안 채우면 기간 겹이
        # A1 슬롯에만 걸린다. 사람이 직접 적었으면 그것이 이긴다.
        for s_ in slots:
            if s_.period_min is None and s_.period_max is None:
                s_.period_min, s_.period_max = A1.period_window(s_.period, rules, as_of_year)
        formulas = human_formulas
        rejected, unguarded = [], []
        audit = {"_note": "A1 건너뜀 (사람 슬롯 사용)" +
                 (f" · 식은 {a.formulas} 에서" if human_formulas else " · 식 없음")}
    else:
        formulas, rejected = A1.design_formulas(concept, rules, meter)
        slots, unguarded = A1.slots_from_formulas(formulas, concept, human, rules)
        # **A1 출력의 내용을 강제한다.** 형식만 맞으면 빈 슬롯도 통과하던 자리다.
        slots, discarded, fixes = A1.enforce_slot_rules(slots, concept, rules, as_of_year)
        audit = A1.audit_slots(slots, formulas)
        audit["slotcheck_fixes"] = fixes
        audit["slotcheck_discarded"] = discarded
        run.count("a1_slots.discarded", len(discarded))
        for fx in fixes:
            run.count(f"a1_slotcheck.{fx['what']}")
        if discarded:
            print(f"    슬롯 폐기 {len(discarded)}개: "
                  f"{', '.join(d['slot_id'] for d in discarded)}")
        run.log_many("a1_formula", formulas)
    run.log_many("a1_slot", slots)
    run.log("a1_audit", audit)
    print(f"A1  식 {len(formulas)}개 (버림 {len(rejected)}) → 슬롯 {len(slots)}개 "
          f"· 가드 없는 슬롯 {len(unguarded)}개")
    if audit.get("stat_code_missing_ratio") is not None:
        print(f"    stat_code 미기재 {len(audit['stat_code_missing'])}/"
              f"{audit['stat_slots_total']} (TAM·SAM 슬롯 기준)")

    # ── A2 ────────────────────────────────────────────────────
    routes = {r.slot_id: r for r in A4.route_sources(slots, rules)}
    run.log_many("a2_route", list(routes.values()))
    by_adapter = {}
    for r in routes.values():
        by_adapter[r.adapter] = by_adapter.get(r.adapter, 0) + 1
    n_fb = sum(1 for r in routes.values() if r.fallback_to)
    print(f"A2  라우팅 {by_adapter}" + (f" · 폴백 대기 {n_fb}개" if n_fb else ""))

    # ── A3' — 직접 URL 주입 **단독** 모드 (검색·fetch 0회) ─────
    # 기본은 「검색에 합치기」다(아래 A3 뒤). 단독 모드는 audit-final 을 만든 옛 경로라
    # 재현용으로 남긴다 — `--direct-only` 를 명시해야 켜진다.
    if a.direct_urls and a.direct_only:
        findings, docs, adapter_states, unknown_codes = _collect_direct(
            a.direct_urls, slots, rules, meter, run)
        for ad, st in adapter_states.items():
            run.set_adapter(ad, st, f"{ad}: {st}")
        run.log_many("a3_document", [Document(**{**to_dict(d), "text": (d.text or "")[:400]})
                                     for d in docs.values()])
        run.snapshot("a3_bodies", {t_: d.text for t_, d in docs.items()})
        _log_findings(run, findings)
        print(f"A3' 직접 주입 문서 {len(docs)}개 · found "
              f"{sum(1 for f in findings if f.status == 'found')}/{len(findings)}")
        ledger, coverage = A4.normalize_and_grade(findings, docs, slots, rules, as_of_year, run)
        print(f"A4  사실 {len(ledger.facts)}개 · 확인됨 "
              f"{sum(1 for r in ledger.rows if r.label == '확인됨')} · "
              f"격리 {sum(1 for r in ledger.rows if r.label in ('off_slot', '미검증'))} · "
              f"충족 슬롯 {sum(1 for c in coverage if c.status == '충족')}/{len(coverage)}")
        return _finish(a, run, concept, slots, formulas, rejected, unguarded, audit,
                       ledger, coverage, rules, as_of_year, unknown_codes)

    # ── A3 (슬롯별 병렬) ──────────────────────────────────────
    smap = {s.slot_id: s for s in slots}
    adapter_states, unknown_codes = {}, []

    with ThreadPoolExecutor(max_workers=MAX_WORKERS) as ex:
        results = list(ex.map(lambda s: collect_slot(s, routes[s.slot_id], rules, meter),
                              slots))

    def _set_state(adapter: str, state: str):
        prev = adapter_states.get(adapter)
        adapter_states[adapter] = state if prev in (None, "ok") else prev

    findings, docs, fallbacks, url_filtered = [], {}, [], []
    for ad, f, dmap, cands, res, extra_states, ev in results:
        findings.append(f)
        docs.update(dmap)
        if cands:
            run.log_many("a3_candidate", cands)
            # **열지도 않고 거른 후보**를 §7 로 보낸다. 안 밝히면 커버리지가 낮을 때
            # "못 찾은 것"과 "우리가 안 연 것"을 구분할 수 없다 (규칙 5).
            url_filtered += [{"slot_id": c.slot_id, "url": c.url, "by": c.filter_reason}
                             for c in cands if c.status == "filtered"]
        _set_state(ad, res.adapter_state if res else "ok")
        for xad, xstate in extra_states:      # 폴백해도 kosis 상태는 따로 남는다
            _set_state(xad, xstate)
        if ev:
            fallbacks.append(ev)
            run.log("a3_fallback", ev)
            run.count("a3_fallback")
            run.decide("어댑터 폴백", ev.get("to") or ev,
                       rule="adapters.kosis.route_metrics.fallback_on",
                       why=str(ev.get("why") or ev)[:160], slot_id=ev.get("slot_id"))
        if res and res.adapter_state == "stopped":
            unknown_codes.append({"adapter": ad, "slot_id": f.slot_id, "note": f.note})

    # ── A3' 직접 주입을 **검색 결과에 합친다**(대체하지 않는다) ───
    # 예전에는 배타 모드라 「검색 원장」과 「주입 원장」이 갈렸고, 그래서 시장크기와
    # 경쟁 실명이 **한 원장에 같이 선 적이 없었다**. 심사는 하나도 안 봐준다 — 주입분도
    # extract·quote_verified·off_slot 5겹·화자 가드를 그대로 통과해야 한다.
    # 사양은 **여럿**일 수 있다(쉼표 구분). 채널 태그가 사양마다 하나이기 때문이다 —
    # user_doc·tavily·gov_doc 를 한 파일에 합치면 「어느 채널이 물어왔는가」의 분모가
    # 뭉뚱그려지고, 그러면 커버리지가 올라도 무엇 덕인지 말할 수 없다(expected.md A.4).
    # ⚠ trace_id 는 슬롯별 일련번호라 사양마다 0 부터 다시 매기면 **문서가 조용히
    #   덮어써진다**(docs 는 trace_id 키). 그래서 카운터를 사양 사이로 이어 넘긴다.
    injected, injected_diag = 0, []
    if a.direct_urls:
        d_findings, d_docs, d_states, injected_diag = _merge_direct(
            a.direct_urls, slots, rules, meter, run, _seen_direct(docs))
        findings += d_findings
        docs.update(d_docs)
        injected = len(d_docs)
        for xad, xstate in d_states.items():
            _set_state(xad, xstate)

    for ad, st in adapter_states.items():
        run.set_adapter(ad, st, f"{ad}: {st}")
    run.log_many("a3_document", [Document(**{**to_dict(d), "text": (d.text or "")[:400]})
                                 for d in docs.values()])
    run.snapshot("a3_bodies", {t: d.text for t, d in docs.items()})
    _log_findings(run, findings)
    print(f"A3  문서 {len(docs)}개 (usable "
          f"{sum(1 for d in docs.values() if d.content_status == 'usable')}) · "
          f"found {sum(1 for f in findings if f.status == 'found')}/{len(findings)}"
          + (f" · 직접 주입 {injected}개 병합" if injected else ""))
    if fallbacks:
        print("    폴백 kosis→web " + str(len(fallbacks)) + "건: " +
              " · ".join(f"{e['slot_id']}({e['web_status']})" for e in fallbacks))

    # ── A4 ────────────────────────────────────────────────────
    ledger, coverage = A4.normalize_and_grade(findings, docs, slots, rules, as_of_year, run)
    print(f"A4  사실 {len(ledger.facts)}개 · 확인됨 "
          f"{sum(1 for r in ledger.rows if r.label == '확인됨')} · "
          f"격리 {sum(1 for r in ledger.rows if r.label in ('off_slot', '미검증'))} · "
          f"충족 슬롯 {sum(1 for c in coverage if c.status == '충족')}/{len(coverage)}")

    if url_filtered:
        print(f"    URL 필터로 거른 후보 {len(url_filtered)}건 "
              f"({', '.join(sorted({x['by'] for x in url_filtered}))}) → §7 url_filtered")
    empties = _empty_docs(docs)
    if empties:
        print(f"    200 을 받고도 본문 0자인 문서 {len(empties)}건 → §7 fetch_empty")
    capped = _capped_docs(findings)
    if capped:
        print(f"    발췌 상한으로 안 물어본 문서 {len(capped)}건 → §7 extract_capped")
    return _finish(a, run, concept, slots, formulas, rejected, unguarded, audit,
                   ledger, coverage, rules, as_of_year, unknown_codes,
                   url_filtered=url_filtered, injected_diag=injected_diag,
                   extract_capped=capped, fetch_empty=empties)


def _seen_direct(docs: dict) -> dict:
    """이미 원장에 들어 있는 슬롯별 `-direct-` 문서 수.

    **파생 실행에서 필수다.** `--from` 으로 복원한 원장에는 이전 판의 주입 문서가 이미
    `S16-direct-0..7` 로 앉아 있다. 카운터를 0 부터 다시 매기면 새 문서가 그 자리를
    **조용히 덮어쓴다**(`docs` 는 trace_id 키 — run.py 의 사양 사이 이어매기와 같은 사고다).
    """
    out: dict = {}
    for d in docs.values():
        if "-direct-" in (d.trace_id or ""):
            out[d.slot_id] = out.get(d.slot_id, 0) + 1
    return out


def _merge_direct(direct_urls, slots, rules, meter, run, seen_per_slot=None):
    """직접 주입 사양들을 **검색 결과에 합칠 모양으로** 모은다.

    반환 `(findings, docs, states, diag)`.

    `diag` 가 **백로그 25 수리**다. 예전에는 주입분의 `not_found` 를 그냥 버렸다 —
    의도(§7 오염 방지)는 정당했지만 그 결과 **주입 문서의 발췌가 성공했는지조차 알 수 없었다.**
    버리는 것은 그대로 두고, 버린 사실을 **진단 채널로 따로** 남긴다.
    """
    findings, docs, states, diag = [], {}, {}, []
    for path in [p.strip() for p in direct_urls.split(",") if p.strip()]:
        d_findings, d_docs, d_states, _ = _collect_direct(
            path, slots, rules, meter, run, seen_per_slot)
        got: dict = {}
        for d in d_docs.values():
            got.setdefault(d.slot_id, []).append(d)
        for f in d_findings:
            # 주입 URL 이 **실제로 있었던** 슬롯만 진단에 남긴다. URL 이 없는 슬롯의
            # not_found 는 "못 찾았다"가 아니라 "이 사양이 그 슬롯을 안 다뤘다"일 뿐이다.
            if f.slot_id in got:
                diag.append({"spec": os.path.basename(path), "slot_id": f.slot_id,
                             "status": f.status, "docs": len(got[f.slot_id]),
                             "n_items": len(f.findings), "note": (f.note or "")[:160]})
        # 주입 URL 이 없는 슬롯의 not_found 는 **버린다.** 검색이 이미 그 슬롯을 다뤘는데
        # 가짜 not_found 를 얹으면 §7 이 "못 찾았다" 로 오염된다 (규칙 5 의 반대 방향 오류).
        # 버리되 **말없이 버리지는 않는다** — 위 diag 가 그 자리를 대신한다.
        findings += [f for f in d_findings if f.status != "not_found"]
        docs.update(d_docs)
        for xad, xstate in d_states.items():
            states[xad] = xstate
    return findings, docs, states, diag


def _collect_direct(path, slots, rules, meter, run, seen_per_slot=None):
    """사람이 지정한 URL 만 모은다. **검색 0회 · fetch 0회.**

    `seen_per_slot` 은 **여러 사양을 이어 부를 때** 슬롯별로 이미 넣은 건수를 나른다.
    없으면(단일 사양) 예전과 이름이 완전히 같다 — `S16-direct-0`, `S16-extract`.


    본문은 저장 코퍼스(`runs/*/a3_bodies.json`)에서 꺼낸다 — 네트워크 변동을 실험에서 뺀다.
    `channel="direct_url"` 로 표시해 회수율·검색 지표에서 제외되게 한다.
    **심사는 하나도 안 봐준다** — extract·quote_verified·off_slot 5겹·값 일치·화자 가드 동일.
    """
    import web
    from a_desk import canonical_url
    _raw = json.load(io.open(path, encoding="utf-8"))
    spec = _raw["by_slot"]
    # 채널 태그는 **사양이 정한다**(기본은 direct_url). 「검색으로 왔는가 · 사람이 넣었는가 ·
    # 다른 검색 API 가 물어왔는가」는 지표의 **분모를 가르는 값**이라 뭉뚱그리면 안 된다.
    _channel = _raw.get("channel") or "direct_url"
    # **재수집 사양** — 기본은 종전대로 코퍼스에서 꺼낸다(재현성). true 면 지금 다시 받는다.
    # 요금처럼 **연도 표기가 없고 값이 바뀌는** 자료는 「그때 그 본문」이 아니라
    # 「지금의 값 + 조회 시점」이 있어야 심사가 성립한다(판 ⑩ ②-a).
    _refetch = bool(_raw.get("refetch"))
    # 코퍼스 색인: canonical_url → (payload, 본문)
    idx = {}
    # 원장 위치는 `runpath` 하나로 본다 — 컨테이너에서는 볼륨을 가리킨다(판 ㉝).
    # 여기만 HERE/runs 로 남으면 주입용 코퍼스 색인이 **빈 채로 조용히** 돈다.
    # ⚠ **두 자리를 다 훑는다.** 수집이 만든 원장의 본문도 코퍼스다 — 한쪽만 보면
    #   주입 사양이 「본문 없음」으로 조용히 비고, 그것이 심사 실패로 오진된다.
    for base, r in ((b, r) for b in runpath.SEARCH_ORDER if os.path.isdir(b)
                    for r in sorted(os.listdir(b))):
        d = os.path.join(base, r)
        jl, bp = os.path.join(d, "run.jsonl"), os.path.join(d, "a3_bodies.json")
        if not (os.path.isfile(jl) and os.path.exists(bp)):
            continue
        try:
            bodies = json.load(io.open(bp, encoding="utf-8"))
        except Exception:
            continue
        for line in io.open(jl, encoding="utf-8"):
            if not line.strip():
                continue
            x = json.loads(line)
            if x["node"] != "a3_document":
                continue
            p_ = x["payload"]
            txt = bodies.get(p_["trace_id"]) or ""
            k = canonical_url(p_.get("url") or "")
            if k and txt and (k not in idx or len(txt) > len(idx[k][1])):
                idx[k] = (p_, txt)

    findings, docs, missing = [], {}, []
    for s_ in slots:
        urls = spec.get(s_.slot_id) or []
        mine = []
        # 앞선 사양이 이 슬롯에 이미 몇 건을 넣었는가. 0 이면 이름이 예전과 **완전히 같다**.
        base = (seen_per_slot or {}).get(s_.slot_id, 0)
        for u in urls:
            tid = f"{s_.slot_id}-direct-{base + len(mine)}"
            if _refetch:
                # **지금 다시 받아 온다.** 기본값이 아니라 사양이 명시할 때만이다 —
                # 코퍼스 재사용의 이유(네트워크 변동을 실험에서 뺀다)는 여전히 옳고,
                # 여기서 필요한 것은 「그때 그 본문」이 아니라 **「지금의 값과 조회 시점」**이다.
                # 상시 게시물(요금표)에는 발행 연도가 없어서, 언제 본 값인지를 우리가
                # 기록하지 않으면 아무도 모른다.
                doc = web.fetch(Candidate(slot_id=s_.slot_id, trace_id=tid, url=u), rules)
                doc.channel = _channel
                if doc.content_status != "usable":
                    # 실패를 조용히 코퍼스로 대체하지 않는다 — 그러면 「다시 받았다」가
                    # 거짓말이 된다. 값으로 남기고 넘어간다(절대규칙 5).
                    missing.append({"slot_id": s_.slot_id, "url": u,
                                    "note": f"재수집 실패({doc.http_status}/{doc.content_status})"})
                    continue
                mine.append(doc)
                continue
            hit = idx.get(canonical_url(u))
            if not hit:
                missing.append({"slot_id": s_.slot_id, "url": u, "note": "코퍼스에 본문 없음"})
                continue
            p_, txt = hit
            mine.append(Document(**{**{k: v for k, v in p_.items()
                                       if k not in ("slot_id", "trace_id", "channel", "text")},
                                    "slot_id": s_.slot_id, "trace_id": tid,
                                    "channel": _channel, "text": txt}))
        docs.update({d.trace_id: d for d in mine})
        if seen_per_slot is not None and mine:
            seen_per_slot[s_.slot_id] = base + len(mine)
        if mine:
            fnd = web.extract(s_, mine, meter,
                              trace_id=f"{s_.slot_id}-extract"
                                       + (f"-{base}" if base else ""), rules=rules)
            # ⚠ `web.collect` 가 하는 일을 여기서도 해야 한다 — 인용을 실제 문서에 붙인다.
            #   빠뜨렸더니 `grade` 가 `docs.get(f.trace_id)` 로 문서를 못 찾아 must_contain 을
            #   **quote 만으로** 판정했고, S7 이 「2만 매장」 인용만 남아 격리됐다(gate2-01).
            #   근본 문제는 normalize 가 URL 로 찾고 grade 가 trace_id 로 찾는 **비대칭**이다(버그 E).
            if fnd.status == "found":
                by_u = {d.url: d for d in mine if d.url}
                first = next((by_u[i.url].trace_id for i in fnd.findings if i.url in by_u), None)
                if first:
                    fnd.trace_id = first
            findings.append(fnd)
        else:
            findings.append(Finding(slot_id=s_.slot_id, trace_id=f"{s_.slot_id}-direct",
                                    status="not_found",
                                    note="direct_url 모드 — 이 슬롯에 지정된 URL 이 없다"))
    if missing:
        print(f"    ⚠ 코퍼스에 없는 URL {len(missing)}건: "
              f"{[m['url'][:48] for m in missing]}")
    return findings, docs, {"direct_url": "ok"}, missing


def _finish(a, run, concept, slots, formulas, rejected, unguarded, audit,
            ledger, coverage, rules, as_of_year, unknown_codes,
            slots_overlay_diff=None, url_filtered=None, injected_diag=None,
            extract_capped=None, fetch_empty=None):
    # ── B ─────────────────────────────────────────────────────
    estimates, recs = B.run_block_b(formulas, ledger, coverage, slots,
                                    rules["assumptions"]["by_role"], rules, as_of_year, run)
    print(f"B   추정 {len(estimates)}개 · 대조 {len(recs)}개 "
          f"({', '.join(f'{r.target}:{r.status}' for r in recs)})")

    # ── C ─────────────────────────────────────────────────────
    user_input = {"total_budget": (concept.constraint or {}).get("budget_krw"),
                  "price": concept.price_hypothesis_krw}
    cells, violations, report = C.run_block_c(
        recs, ledger, coverage, slots, estimates,
        {k: v for k, v in user_input.items() if v is not None},
        rules, adapters=run.adapters, coverage_caveat=run.coverage_caveat(),
        run=run, unknown_codes=unknown_codes, url_filtered=url_filtered,
        extract_capped=extract_capped, fetch_empty=fetch_empty)
    # 백로그 25 — 주입분 발췌 진단. **§7 이 아니다.** §7 은 "못 찾은 것"이고 이건
    # "주입한 문서가 발췌를 통과했는가"라 성격이 다르다. 섞으면 §7 이 오염된다.
    report.injected_extract = injected_diag or []
    if report.injected_extract:
        print("    주입분 발췌 진단: " + " · ".join(
            f"{x['slot_id']}({x['spec']}) {x['status']} 문서{x['docs']}→인용{x['n_items']}"
            for x in report.injected_extract))

    # `retry_hint` 는 **사람이 승인해야 1회 재조사**가 되는 자리다(§5 재조사 자동 루프 금지).
    # 지금 멈추지는 않지만 **사람이 봐야 하는 사건**이므로 비차단 개입으로 계측한다.
    # 이걸 안 세면 「개입 0」이 「사람이 볼 것이 없었다」로 읽힌다 — 실제로는 16건이 밀려 있다.
    for h in (report.not_found or {}).get("retry_hints", []) or []:
        run.intervene("retry_hint", str(h)[:200], blocking=False)

    blockers = [v for v in violations if v.status == "violated" and v.severity == "blocker"]
    print(f"C   위반 blocker {len(blockers)} · skipped "
          f"{sum(1 for v in violations if v.status == 'skipped')}")

    result = run.finish(concept=concept, slots=slots, verdict=None, report=report,
                        extra={"a1_audit": audit, "a1_rejected": rejected,
                               "unguarded_slots": unguarded,
                               # 파생 실행의 출처 — 수집이 같은 실행끼리만 채점 비교가 유효하다
                               "source_run": a.source_run or None,
                               "from_stage": a.from_stage or None,
                               # 사람 칸을 갈아끼웠는지. **비교 축이다** — source 끼리,
                               # current 끼리만 같은 그래프에 올린다.
                               "slots_overlay": bool(a.from_stage)
                               and a.slots_from == "current",
                               "slots_overlay_diff": slots_overlay_diff or []})
    print(f"\n§7 못 찾은 것: " +
          json.dumps({k: (len(v) if isinstance(v, (list, dict)) else v)
                      for k, v in report.not_found.items()}, ensure_ascii=False))
    print(f"기록: {run.dir}  ·  LLM {run.counters.get('llm.calls', 0)}회 "
          f"({run.counters.get('wall_clock_sec', 0)}) ")
    return result


if __name__ == "__main__":
    main()
