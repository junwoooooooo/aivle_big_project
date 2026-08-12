# -*- coding: utf-8 -*-
"""노드 간 타입. **이 파일만 읽으면 파이프라인 전체가 보인다.**

    Concept  →(A1)→ Formula[] → Slot[] →(A2)→ Route →(A3)→ Candidate[]
             →(A3)→ Document → Finding →(A4)→ Fact[] → Ledger
             →(B1·B2)→ Estimate[] →(B3)→ Reconciliation
             →(C1·C2)→ Violation[] →(C3)→ Report

이름이 바뀐다 = 데이터가 실제로 변형됐다. 변형이 없으면 그 노드는 필요 없다.

두 가지를 **타입으로** 강제한다:
  · 규칙 2 — LLM 출력(Finding)에 등급 칸이 없다. FORBIDDEN_LLM_FIELDS 로 테스트한다.
  · 규칙 4 — 근거 0건이면 라벨을 만들 수 없다. Coverage.__post_init__ 가 막는다.
"""
from __future__ import annotations

from dataclasses import dataclass, field, asdict
from typing import Literal

# ══════════════════════════════════════════════════════════════
# 규칙 2 — 모델이 절대 채울 수 없는 칸. 프롬프트 테스트가 이 목록을 쓴다.
# ══════════════════════════════════════════════════════════════
FORBIDDEN_LLM_FIELDS = frozenset({
    "kind", "tier", "score", "conf", "confidence",
    "verdict", "label", "role", "relation",
})

ClaimType = Literal["TAM", "SAM", "COMP", "COMPARABLE", "CHANNEL", "ALT", "PAIN", "PRICE",
                    "LEGAL", "GROWTH"]
Adapter = Literal["kosis", "dart", "web"]
Label = Literal["확인됨", "출처약함", "미확인", "off_slot", "미검증"]

# 격리 라벨 — **점수를 매기기 전에** 걸러진 것들. 근거로 세지 않는다.
#   off_slot : 슬롯과 안 맞음 (4겹)
#   미검증   : 인용문이 본문에 없음 — 애초에 근거가 아니다 (F7)
# 잘못된 값이 원장에 '존재할 여지' 자체를 없앤다. 나중에 score>=5 로 필터링해도 딸려오지 않는다.
QUARANTINE_LABELS = frozenset({"off_slot", "미검증"})


def to_dict(x):
    """dataclass → dict (기록용). 리스트·중첩도 처리한다."""
    if hasattr(x, "__dataclass_fields__"):
        return asdict(x)
    if isinstance(x, list):
        return [to_dict(i) for i in x]
    return x


# ══════════════════════════════════════════════════════════════
# 입력
# ══════════════════════════════════════════════════════════════
@dataclass
class Concept:
    """사용자 입력. constraint 는 **조사에 넘기지 않는다** (규칙 6)."""
    concept_id: str
    name: str
    problem: str
    target: str
    solution: str
    region: str = "대한민국"
    hypotheses: list[str] = field(default_factory=list)
    # ↓ 아래 둘은 A블록(수집) 프롬프트에 절대 들어가지 않는다
    price_hypothesis_krw: int | None = None
    constraint: dict = field(default_factory=dict)      # 팀·예산·기간

    def research_view(self) -> dict:
        """수집 단계에 넘겨도 되는 필드만. A1·A3 는 반드시 이걸 쓴다."""
        return {"name": self.name, "problem": self.problem, "target": self.target,
                "solution": self.solution, "region": self.region,
                "hypotheses": list(self.hypotheses)}


# ══════════════════════════════════════════════════════════════
# A1 — 식과 슬롯
# ══════════════════════════════════════════════════════════════
FORMULA_TEMPLATES = {
    "T1": "상위시장규모 × 세그먼트비중",
    "T2": "인구 또는 사업체수 × 침투율 × 단가",
    "T3": "상위N사매출합 ÷ 추정점유율",
    "T4": "거래건수 × 건당금액",
    "T5": "직접조회 (통계표에 값이 그대로 있음)",
    # T6 은 **비워 둔다** — 백로그 33 이 CAGR 템플릿 자리로 먼저 이름을 잡았다.
    # 남의 예약 번호를 가져다 쓰면 지도와 코드가 어긋난다.
    "T7": "시장거래액 × 추정점유율",   # 계열 C (제품·이커머스). 백로그 49
}


@dataclass
class FormulaVar:
    var_id: str
    var_role: str             # "사업체수" "침투율" "단가"
                              # (금지 필드 'role' 과 이름을 분리했다 — 의미가 다르다)
    subject: str
    metric: str
    period: str
    unit: str
    subject_code: str | None = None
    stat_code: str | None = None
    corp_name: str | None = None


@dataclass
class Formula:
    formula_id: str
    target: ClaimType
    path: Literal["topdown", "bottomup"]
    template: str                       # T1~T5. 자유 서술 금지
    vars: list[FormulaVar] = field(default_factory=list)


#: 옛 `_` 접두 이름 → 승격된 정식 필드 (판 ㉘ 도장). **단일 원천 — 사본 금지.**
#: `run.mk_slot` 과 `a_design.overlay_human_slots` 가 **이 표 하나를** 본다.
#: 사본을 두면 「같은 물음을 두 곳이 각자 푼다」의 여섯 번째가 된다.
경계_승격 = {"_경계": "경계", "_proxy_선언": "proxy_선언",
           "_proxy_사유": "proxy_사유", "_경계_proxy": "경계_proxy"}


@dataclass
class Slot:
    """식의 변수 하나 = 슬롯 하나.

    must_contain / must_not_contain / value_range 는 **사람이 적는다.**
    LLM 이 채우면 off_slot 4겹 중 3겹이 무의미해진다 (F6).
    """
    slot_id: str
    var_id: str
    formula_id: str
    claim_type: ClaimType
    subject: str
    metric: str
    period: str
    unit: str
    region: str = "대한민국"
    subject_code: str | None = None
    stat_code: str | None = None
    corp_name: str | None = None
    must_contain: list[str] = field(default_factory=list)
    # **표기 변종** (판 ㉛). `subject` 를 가리키는 다른 표기들 — 「NAVER」·「네이버주식회사」.
    # `must_contain` 이 낱말 하나로 막을 때 A4 가 이 목록으로 다리를 놓는다.
    # ⚠ **하네스가 슬롯을 설계할 때 LLM 이 한 번 뽑고, A4 는 결정론적 문자열 대조만 한다.**
    #   판정 한가운데에서 물으면 `--from` 재실행이 같은 원장에 다른 답을 내고, 그러면
    #   before/after 를 못 잰다. 어느 별칭이 통과시켰는지는 `Fact.표기_다리` 에 남는다.
    subject_aliases: list[str] = field(default_factory=list)
    must_not_contain: list[str] = field(default_factory=list)
    value_range: list[float] | None = None

    # 기간 겹(off_slot 5겹째)이 쓰는 창. **A1 이 계산해 값으로 저장한다.**
    # 비교 시점에 계산하면 창이 기록에 남지 않아 "이 슬롯이 왜 이걸 잘랐는지"를 못 따진다.
    # 하한은 `as_of_year - fresh_years` 아래로 내려가지 않는다 — 내려가면 통과시킨 뒤
    # 신선도로 감점하는 슬롯이 생긴다(`slotcheck.period.clamp_window_to_fresh`).
    period_min: int | None = None
    period_max: int | None = None

    # accept — **"언제 충분한가"의 칸.** 위 셋(must_*/value_range)은 불량품을 걸러내는 칸이고,
    # 이건 모자란 것을 알아채는 칸이다. 걸러내는 칸만 있으면 1건짜리 슬롯이 조용히 통과한다.
    #   min_score   : 이 점수 이상이어야 '확인됨'
    #   min_facts   : 이 밑이면 슬롯이 '빈약(thin)' — 라벨은 붙되 보고서 §7 로 간다
    # ⚠ `min_sources` 는 **읽는 코드가 0** 이었다(판 ㉙ S0 전수 확인 — 교차 문턱은
    #   `scoring.cross_min_sources` 가 따로 들고 있다). 슬롯 파일을 읽는 사람이
    #   「교차 2건이 채움 조건」으로 오해하는 원인이라 **기본값에서 뺀다.**
    #   기존 슬롯 파일에 남아 있는 값은 건드리지 않는다 — 스냅샷은 보존한다.
    accept: dict = field(default_factory=lambda: {"min_score": 5, "min_facts": 2})

    # ── 경계급 필드 (판 ㉘ 승격 · 도장) ──────────────────────────────────
    # **경계는 쓴 곳이 아니라 도달한 곳에서만 존재한다.** 이 넷은 원래 `_` 접두로
    # 태어났고, `run.py` 가 `_` 키를 버리는 바람에 **최종 매체까지 가는 길이 없었다** —
    # §4 가 이름까지 박아 둔 「전사 매출 — 시장 매출 아님」이 지워진 게 아니라
    # **애초에 전달 경로가 없었다**(판 ㉘ 감사 (나) 1건).
    #
    # 승격 기준을 값으로: **수신자의 해석을 바꾸는 정보 = 1급 필드 /
    #                      제작 과정 기록 = `_` 임시 키.**
    # `상한_울타리`·`표기_다리` 가 살아남은 이유가 정확히 **정식 Fact 필드였기 때문**이고,
    # 이 넷은 **같은 급의 정보인데 임시 키 문법에 실려** 사라졌다.
    #
    # ⚠ **쓴 주체(하네스/사람)로 필드를 가르지 않는다** — 갈랐으면 여섯 번째 분열이다.
    #    주체는 `경계_출처` 에 **기록으로** 남긴다.
    경계: str | None = None            # 이 관측을 어떻게 읽지 말아야 하는가 (사람·하네스 공용)
    경계_출처: str | None = None       # "사람" | "하네스" — 필드가 아니라 기록으로 가른다
    proxy_선언: dict | None = None     # {대상, 사유} — 고객 단위를 벗어난 관측의 유일한 정당화
    proxy_사유: str | None = None
    경계_proxy: str | None = None      # proxy 선언에 **코드가** 붙이는 경계 문장

    #: 연도 미상 버킷. **슬롯 period 로 폴백하지 않는다.**
    UNKNOWN_YEAR = "unknown"

    def match_key(self, year: int | None = None) -> str:
        """교차확인의 기준. 자유 텍스트가 아니라 코드로 비교한다 (F4).

        ⚠ **연도 미상이면 `unknown` 버킷이다 — 슬롯 `period` 로 폴백하지 않는다.**
        폴백하던 시절엔 「연도 미상」과 「연도가 슬롯 기간과 같음」이 **같은 키**가 되어
        조용히 한 그룹으로 묶였다. 실측(gate4-01): 세 사실 중 하나만 연도가 채워지자
        나머지가 `period` 폴백으로 남아 **교차가 갈렸는데 사유가 아무 데도 안 보였다.**
        미상은 미상끼리만 묶이고, 그 사실이 키에 드러난다.

        ⚠ **`region` 이 키에 들어간다 (판 ④, 2026-08-08).** 없던 시절엔
        「서울 두발 미용업 사업체 수 2024」와 「전국 두발 미용업 사업체 수 2024」가
        **같은 키**였다. 두 값(19,026 · 115,310)이 같은 버킷에 앉자 R11(같은 지표가
        두 값)이 **blocker 로 터졌다** — 서로 다른 지표인데 모순으로 보고된 것이다.
        키는 «같은 것을 재고 있는가»를 물어야 하고, 지역이 다르면 다른 것을 재고 있다.
        버킷은 **쪼개지기만 하므로**(더 촘촘한 키) 교차확인 수가 늘지는 않는다 —
        없던 교차가 생기는 방향의 변화가 아니다.
        """
        subj = self.subject_code or self.subject
        y = year if year is not None else self.UNKNOWN_YEAR
        reg = (self.region or "").strip()
        return f"{subj}|{self.metric}|{reg}|{y}"


@dataclass
class Route:
    slot_id: str
    adapter: Adapter
    why: str                            # 어느 규칙으로 이 어댑터가 정해졌는지
    # 그 어댑터가 **못 찾았을 때** 어디로 떨어뜨릴지. 빈 문자열이면 폴백 없음.
    # full-02 는 폴백이 없어 kosis 로 보낸 10슬롯이 그대로 죽었다 — 라우팅과 폴백은 한 쌍이다.
    fallback_to: str = ""


# ══════════════════════════════════════════════════════════════
# A3 — 수집
# ══════════════════════════════════════════════════════════════
@dataclass
class Candidate:
    """search 의 출력 — URL 과 제목만. 검색 요약은 버린다."""
    slot_id: str
    trace_id: str
    url: str
    title: str = ""
    from_query: str = ""
    status: Literal["ok", "no_result", "filtered"] = "ok"
    # URL 필터에 걸린 사유. **버리지 않고 값으로 남긴다**(규칙 5) — 보고서 §7 로 간다.
    filter_reason: str = ""


@dataclass
class Document:
    """fetch 의 출력 — 순수 HTTP. LLM 이 개입하지 않는다.

    http_status 와 content_status 는 **다른 얘기다** (F9).
    HTTP 200 이어도 JS 껍데기면 본문에 숫자가 없다.
    """
    slot_id: str
    trace_id: str
    url: str
    text: str = ""
    published_at_raw: str | None = None
    http_status: Literal["ok", "blocked", "timeout", "not_html", "error"] = "ok"
    content_status: Literal["usable", "js_shell", "paywall", "empty", "mojibake",
                            "pdf_unreadable"] = "usable"
    text_len: int = 0
    digit_count: int = 0
    has_table: bool = False
    # direct_url — **검색이 아니라 사람이 URL 을 지정해 넣은 문서.**
    # 등급은 그대로 발행자로 매기되(원칙은 '누가 발행했는가'), **회수율·검색 지표에서는 뺀다** —
    # 검색이 못 한 일을 검색 성적으로 계상하면 지표가 거짓말한다.
    channel: Literal["kosis_api", "dart_api", "web", "direct_url"] = "web"
    http_code: int | None = None
    error: str | None = None
    # **우리가 이 페이지를 언제 받아 왔는가.** `published_at_raw`(문서가 말하는 발행일)와
    # 다른 시점이다 — 상시 게시물(요금표)에는 발행일이 없고 조회 시점만 있다.
    # 판 ⑩ ②-a: 자기 요금 페이지의 연도 감점 예외는 **이 칸이 채워진 문서에만** 허용한다.
    # 없으면 예외를 주지 않는다 — 「언제 본 값인지 모르는 가격」을 밴드에 넣지 않기 위해서다.
    # 옛 실행에서 복원한 문서는 이 칸이 없어 None 이고, 그때는 예외가 **안 걸린다**(의도한 동작).
    retrieved_at: str | None = None
    # **이 문서가 PDF 였는가.** 판 ㉟ ②-b — `content_status` 만으로는 성공한 PDF 와 HTML 이
    # 구별되지 않는다. 해석기가 들어와 PDF 가 살아나는 순간 「PDF 였다」가 원장에서 사라지고
    # 「PDF 를 되살렸다」를 잴 수 없게 된다. 표적 URL 은 `filedown.php`·`download.do` 라
    # 확장자 추측도 안 먹는다 — 받은 자리에서 값으로 박아 두는 수밖에 없다.
    # ⚠ 옛 원장에서 복원한 문서는 이 칸이 없어 False 다. **0 이 아니라 미측정**이므로
    #   읽는 쪽(`tools/funnel.py`)이 그렇게 표시한다.
    is_pdf: bool = False


@dataclass
class FindingItem:
    """LLM 이 채우는 유일한 구조. **등급 칸이 없다** (규칙 2)."""
    quote: str                 # 원문 그대로
    number_raw: str            # 원문 표기 그대로 ("10만 729")
    unit_raw: str              # 원문 단위 그대로
    url: str = ""
    context: str = ""          # 인용 앞뒤 1문장
    # 어댑터가 채우는 **출처 계정의 정체**. LLM 이 채우는 칸이 아니다(규칙 2 무관 —
    # 등급이 아니라 사실의 출처 식별자다). 이게 없으면 A4 가 '이 숫자가 무슨 계정인지'
    # 를 알 방법이 없어, 재무상태표 계정이 매출 슬롯의 사실로 흘러도 못 막는다(full-04).
    account_id: str = ""       # IFRS/DART 분류코드. 한글 계정명은 회사마다 다르다
    sj_div: str = ""           # 재무제표 구분 BS/IS/CIS/CF/SCE
    # 이 숫자가 **무엇의 범위인가**. 값이 맞아도 범위가 다르면 다른 사실이다.
    #   company_total = 그 회사 **전체** 매출. 우리가 묻는 시장 안의 매출이 아니다
    # 빈 문자열이면 표시하지 않는다 — 모르는 것을 단정하지 않는다.
    scope: str = ""


@dataclass
class Finding:
    slot_id: str
    trace_id: str
    status: Literal["found", "not_found", "fetch_failed", "not_configured"]
    findings: list[FindingItem] = field(default_factory=list)
    note: str = ""             # not_found 사유 — 조용히 사라지지 않게 (규칙 5)
    # 발췌 깔때기의 **값**. 예전에는 「상한 5 으로 2개 제외: [...]」처럼 `note` 문자열
    # 안에만 있었다 — 문자열은 셀 수 없어 「우리가 버렸다」가 「자료가 없다」와 구별되지
    # 않았다. `run.py` 가 이것만 떼어 `a3_extract` 원장 노드로 남기고, a3_finding 에는
    # 싣지 않는다(같은 사실을 두 곳에 두면 갈라진다). 읽는 쪽은 `tools/funnel.py`.
    extract_log: dict = field(default_factory=dict)
    # 어댑터가 **다른 이름의 집계를 가져왔다**는 사실. [{슬롯_표기, 통계_표기}].
    # 판 ㉛ A: 상위 카테고리 울타리는 `off_slot_reason` 의 다리 갈래에서만 붙었고
    # 그 갈래는 `must_contain` 이 있어야 실행됐다 — 하네스가 그 칸을 비우면 34.8조가
    # **경계 없이** TAM 에 앉는다. 치환이 일어난 자리(어댑터)에서 값으로 내려보낸다.
    표기_치환: list = field(default_factory=list)
    # 어댑터가 **조회로 대상을 확정했다**는 사실. {경로_칸, 값, 어떻게}.
    # 슬롯이 `stat_code` 를 선언하지 않아도 어댑터는 검색으로 표를 확정한다 —
    # 보증은 「슬롯이 적었는가」가 아니라 「대상이 확정됐는가」다(판 ㉛A 도장).
    경로_보증: dict = field(default_factory=dict)


# ══════════════════════════════════════════════════════════════
# A4 — 사실과 원장
# ══════════════════════════════════════════════════════════════
@dataclass
class Fact:
    fact_id: str
    slot_id: str
    var_id: str
    trace_id: str
    url: str
    quote: str
    value_num: float | None
    unit_norm: str | None
    # ⚠ 시점이 둘이다. 섞으면 같은 사실이 갈라진다.
    #   year           = **사실의 시점** ("2023년 사업체 수") → match_key · 신선도 · 기간 겹
    #   published_year = 문서 발행 시점 (2025년 기사가 2023년 통계를 인용) → 참고용
    # 발행일로 사실 연도를 메우지 않는다 — 그건 조용한 추측이다.
    year: int | None
    dedup_key: str             # URL 정규화 후 같으면 1건 (F3)
    match_key: str             # subject_code|metric|period (F4)
    quote_verified: bool       # 인용문이 Document.text 에 실재하는가 (F7 방어선)
    content_status: str        # 상한 판정에 쓴다 (규칙 3)
    channel: str = "web"
    published_year: int | None = None    # 참고용. **match_key 에 넣지 않는다**
    # 어댑터가 준 계정 정체를 A4 까지 실어 나른다. web 경로에서는 빈 문자열이고,
    # 빈 값이면 계정 겹은 **판정하지 않는다** — 없는 기준으로 벌하지 않는다.
    account_id: str = ""
    sj_div: str = ""
    # 이 숫자가 **무엇의 범위인가**. 값이 맞아도 범위가 다르면 다른 사실이다.
    #   company_total = 그 회사 **전체** 매출. 우리가 묻는 시장 안의 매출이 아니다
    # 빈 문자열이면 표시하지 않는다 — 모르는 것을 단정하지 않는다.
    scope: str = ""
    # 연도를 어디서 얻었는가 (`scoring.year_fields.order` 의 3단계 중 하나).
    # 계측용이며 판정에는 쓰지 않는다. **slot.period 를 옮기면 문맥 추정 창(±3)도 같이
    # 움직여 year 자체가 바뀐다.** 이 칸이 없으면 연도 분포가 변했을 때 '필터가 열려서'인지
    # '문맥 매칭이 달라져서'인지 구분할 수 없다.
    year_source: str | None = None
    # **기대 밖 플래그** (판 ⑲). `value_range` 를 벗어났지만 **자릿수 차이가 작아** 통과시킨
    # 경우의 표시. 비어 있으면 기대 안이다. **통과가 곧 확정은 아니다** — 등급은 따로 매긴다.
    기대_밖: dict = field(default_factory=dict)
    # **수 재선택** (판 ㉜). 발췌 프롬프트는 슬롯 단위를 **일부러 안 본다**(「슬롯과 맞는지
    # 판단하지 마라」 — 모델이 조용히 버리면 그 판단이 아무 데도 안 남기 때문이다).
    # 그 대가로 모델이 **같은 문장 안에서 단위가 다른 수**를 고르는 일이 생긴다 —
    # 실측: 「중개수수료 7.8%에 배달비 2,400~3,400원」에서 `원` 슬롯에 7.8(%)을 골랐다.
    # 그때 코드가 인용 안에서 단위 맞는 수로 **바꿔 읽고**, 바꿨다는 사실을 여기 남긴다.
    # 비어 있으면 모델이 고른 것을 그대로 쓴 것이다 — **조용한 덮어쓰기가 없다는 증거**다.
    수_재선택: dict = field(default_factory=dict)
    # **범위 쪼갬** (판 ㉜). 「2,400~3,400원」 같은 범위 표기에서 갈라져 나온 사실이면
    # 어느 쪽(하한·상한)인지와 원문 표기가 여기 남는다. 원장에 2건으로 보이는 **이유**다.
    범위_쪼갬: dict = field(default_factory=dict)
    # **표기 다리** (판 ⑰). 슬롯 어휘와 통계 어휘가 달라 `must_contain` 이 막을 때,
    # `subject_별칭` 표가 통과시켰다면 **어느 별칭이 통과시켰는지** 여기 값으로 남는다.
    # 비어 있으면 다리를 안 탄 것이다 — **조용한 치환이 없다는 증거**이기도 하다.
    표기_다리: list = field(default_factory=list)
    # **슬롯 보증** (판 ㉛). `must_contain` 을 **건너뛴** 경우 그 근거를 값으로 남긴다.
    # 경로가 정체를 이미 확정한 자리 — `stat_code`(통계표 확정) · `corp_name`(corpCode 로
    # 법인 확정) — 에서만 붙는다. 비어 있으면 낱말 대조를 정상적으로 통과한 것이다.
    # ⚠ 면제를 **조용히** 하지 않기 위한 칸이다. 이 값이 없으면 「왜 통과했는지」를
    #   나중에 코드를 읽어야만 알 수 있고, 그건 기록이 아니라 추론이다(표기_다리와 같은 계보).
    슬롯_보증: dict = field(default_factory=dict)
    # 어댑터가 조회로 확정한 대상 — `Finding.경로_보증` 이 그대로 내려온 것.
    # 슬롯이 칸을 비워 뒀어도 보증은 **실제로 있었다**. 위 `슬롯_보증` 은 그 **판정 결과**고
    # 이것은 **판정 재료**다 — 둘을 한 칸에 섞으면 「무엇이 면제했는가」를 못 따진다.
    경로_보증: dict = field(default_factory=dict)
    # 문서를 받아 온 시점(`Document.retrieved_at` 을 그대로 실어 나른다).
    # **연도 감점 예외의 전제 조건**이라 판정에 쓰인다 — year_source 와 달리 계측용이 아니다.
    retrieved_at: str | None = None


@dataclass
class LedgerRow:
    """등급이 붙는 **유일한** 곳. LLM 은 이 타입을 만들지 못한다."""
    fact_id: str
    slot_id: str
    url: str
    kind: str
    kind_by: str
    score: int
    label: Label
    cross: int = 0
    reasons: list[str] = field(default_factory=list)
    # 값이 갈리는 교차 — 가점은 0 이지만 **사라지면 안 된다.** 「같은 것을 묻는데 출처마다
    # 값이 다르다」는 확인 실패가 아니라 그 자체가 조사 결과다(B3 의 diverged 와 같은 철학).
    conflict: str = ""
    off_slot_reason: str | None = None
    # 값의 범위 꼬리표. 원장에서 보고서까지 **값 옆에 붙어 간다** — 3,147억이
    # '카페 SaaS 시장 매출' 이 아니라 '카페24 전체 매출' 이라는 사실이 사라지면
    # 읽는 사람이 시장규모로 오해한다. 상한선으로만 읽어야 한다.
    scope: str = ""
    scope_note: str = ""

    # ── 기준 v2 — 직교 두 축 (판 ㉙ S1) ────────────────────────────────
    # **채움과 등급은 다른 물음이다.** 지금까지 `label == "확인됨"` 하나가 둘을 겸했고,
    # 그 탓에 「확실하지 않다」가 「쓸 수 없다」와 같은 뜻이 되어 성적표의 미확보
    # 상당수가 자료 부재가 아니라 **심사 사망**이었다.
    #   채택 : 4요건(관측·url·retrieved_at·quote_verified)을 다 채웠는가 — **채우는가**
    #   등급 : 그 값이 얼마나 확실한가 — **표기**
    # ⚠ `label`·`score` 는 한 글자도 안 바뀐다. 옛 축은 그대로 살아 있고 새 축이 **옆에** 선다.
    #   두 축이 모순되면 `tools/grade_audit.py` 가 실패시킨다(공존 봉쇄).
    채택: bool = False
    채택_불가_사유: list[str] = field(default_factory=list)
    등급: str = "추정"
    등급_근거: str = ""
    # ★ 지금까지 LedgerRow 에 없었다 — 4요건을 **원장 행만으로** 판정하려면 필수다.
    #   `Fact.retrieved_at`(= `Document.retrieved_at` 승계)을 그대로 실어 나른다. 백필 금지.
    retrieved_at: str | None = None


@dataclass
class Ledger:
    rows: list[LedgerRow] = field(default_factory=list)
    facts: dict[str, Fact] = field(default_factory=dict)

    def by_slot(self, slot_id: str) -> list[LedgerRow]:
        return [r for r in self.rows if r.slot_id == slot_id]

    def confirmed(self, slot_id: str) -> list[LedgerRow]:
        import fillaxis as _fx
        return [r for r in self.by_slot(slot_id)
                if _fx.filled(r, "schema.Ledger.confirmed")]


@dataclass
class Coverage:
    """규칙 4 — 근거 0건이면 라벨을 만들 수 없다. **타입이 막는다.**
    (이전 버전에서 근거 0건 항목에 '부분지지' 가 붙었다 — F1)

    그리고 **'충족'과 '충분'은 다른 얘기다.** min_confirmed 를 겨우 넘긴 1건짜리 슬롯도
    '충족'이 된다. 그런 슬롯은 `thin=True` 로 표시해 보고서 §7 로 보낸다.
    루프를 돌지는 않지만, 무엇이 얇은지는 사람이 보게 한다.
    """
    slot_id: str
    status: Literal["충족", "보강필요", "공백"]
    confirmed: int
    total: int
    evidence_ids: list[str] = field(default_factory=list)
    min_facts: int = 0                 # 슬롯의 accept.min_facts
    thin: bool = False                 # confirmed < min_facts
    retry_hint: str | None = None      # A4 가 낸 재조사 힌트 (자동 루프 금지, 사람 승인 1회)
    # 판 ㉕ — 공식 통계 연 계열이라 **독립 교차가 구조적으로 불가**한 슬롯에 붙는 딱지.
    # ⚠ **얇음의 해소가 아니라 사유의 표기다.** 수신 모듈은 이 값이 교차확인된 것인지
    #   알 권리가 있고, 딱지를 떼면 「2건이 서로 확인했다」와 구별되지 않는다.
    단일_원천: str | None = None

    def __post_init__(self):
        if self.status != "공백" and not self.evidence_ids:
            raise ValueError(
                f"{self.slot_id}: 근거 0건인데 '{self.status}' 라벨을 만들 수 없다 (규칙 4)")
        if self.confirmed and not self.evidence_ids:
            raise ValueError(f"{self.slot_id}: confirmed>0 인데 evidence_ids 가 비었다")
        # thin 은 **'충족'에만** 의미가 있다 — "라벨은 붙었는데 표본이 얇다".
        # 공백·보강필요는 애초에 계산에 못 쓰므로 thin 으로 또 표시하면 §7 이 중복 보고된다
        # (empty_slots 6 + thin_slots 6 이 동시에 뜨는 상태).
        if self.status == "충족" and self.min_facts and self.confirmed < self.min_facts:
            self.thin = True
            if not self.retry_hint:
                self.retry_hint = (f"{self.slot_id}: 확인된 사실 {self.confirmed}건 "
                                   f"< 기준 {self.min_facts}건 — 소스를 늘려 재조사 권고")


# ══════════════════════════════════════════════════════════════
# B — 추정과 대조 (LLM 0회)
# ══════════════════════════════════════════════════════════════
@dataclass
class EstimateInput:
    var_id: str
    from_fact: str | None = None       # 원장의 fact_id
    confirmed: bool = False
    assumption: float | None = None    # 가정으로 채운 값
    basis: str = ""                    # 가정의 근거 (rules/ 또는 원장. 코드 상수 금지)


@dataclass
class Estimate:
    formula_id: str
    target: ClaimType
    path: Literal["topdown", "bottomup"]
    value: list[float] | None                  # [하한, 상한]
    inputs: list[EstimateInput] = field(default_factory=list)
    assumption_count: int = 0                  # 0=사실 / 1~2=추정 / 3+=시나리오
    sensitivity: list[dict] = field(default_factory=list)
    falsified_if: str = ""
    # unit_mismatch — 단위가 어긋나면 **변환하지 않고 멈춘다.** 조용히 100배 틀린 값이
    #                 흘러가는 것보다 시끄럽게 서는 게 낫다.
    status: Literal["ok", "insufficient", "unit_mismatch"] = "ok"
    # range_capped — 범위 상한에 부딪혔다 = "가정이 너무 많아 범위가 무의미하다"는 신호.
    #                그냥 잘리면 그 정보가 사라지고 무의미한 범위가 의미 있어 보인다.
    range_capped: bool = False
    unit_note: str = ""

    @property
    def badge(self) -> str:
        return "사실" if self.assumption_count == 0 else (
            "추정" if self.assumption_count <= 2 else "시나리오")


@dataclass
class Reconciliation:
    target: ClaimType
    topdown: list[float] | None
    bottomup: list[float] | None
    overlap: list[float] | None
    gap_ratio: float | None
    # single_path — 경로가 하나뿐이라 대조하지 못했다. **값은 쓰되 검증된 값이 아니다.**
    status: Literal["converged", "partial_overlap", "diverged", "insufficient", "single_path"]
    suspect_var: str | None = None
    adopted: list[float] | None = None         # diverged 면 반드시 None

    def __post_init__(self):
        if self.status == "diverged" and self.adopted:
            raise ValueError("diverged 인데 adopted 가 있다 — 그럴듯한 쪽을 고르지 않는다")


# ══════════════════════════════════════════════════════════════
# C — 논리 사슬 (LLM 0회)
# ══════════════════════════════════════════════════════════════
@dataclass
class ChainCell:
    """출처 없이 나타나는 숫자가 없어야 한다."""
    key: str                    # "TAM" "SAM" "SOM" "revenue_y1" ...
    value: float | None
    source: Literal["computed", "ledger", "user_input", "missing"]
    origin: str = ""            # 계산식 / fact_id / 입력 필드명
    unit: str = ""


@dataclass
class Violation:
    rule_id: str
    name: str
    severity: Literal["blocker", "warn"]
    passed: bool
    # blocker 가 깨지면 그 아래 의존 규칙은 검사하지 않는다.
    # 위반 12개가 줄줄이 뜨는 것보다 "1번이 깨져서 3·4번은 검사 안 함"이 읽기 쉽다.
    status: Literal["passed", "violated", "skipped", "not_applicable"] = "passed"
    skipped_by: str | None = None
    detail: str = ""
    cells: list[str] = field(default_factory=list)
    retry_hint: str | None = None      # 사람이 승인해 1회만 재조사 (자동 루프 금지)


@dataclass
class Report:
    conclusion: list[str] = field(default_factory=list)          # 1. 결론 3문장
    headline_numbers: list[dict] = field(default_factory=list)   # 2. 핵심 숫자 3개 + 배지
    how_computed: list[dict] = field(default_factory=list)       # 3. 어떻게 계산했나
    falsifiers: list[str] = field(default_factory=list)          # 4. 틀릴 수 있는 지점
    reconciliations: list[dict] = field(default_factory=list)    # 5. 두 경로 대조
    ledger: list[dict] = field(default_factory=list)             # 6. 근거 원장
    not_found: dict = field(default_factory=dict)                # 7. 못 찾은 것 ← 빼지 않는다
    # 진단 — 주입 문서가 발췌를 통과했는가 (백로그 25). **§7 과 섞지 않는다**:
    # §7 은 «못 찾았다»고 이건 «넣은 것이 읽혔는가»다. 주입이 유일 경로인 판에서
    # 이게 없으면 실패 원인을 «자료가 없다»와 «못 읽었다»로 가를 수 없다.
    injected_extract: list = field(default_factory=list)


# §7 "못 찾은 것" 에 반드시 들어가는 키. 하나라도 빠지면 침묵이 생긴다.
NOT_FOUND_KEYS = (
    "empty_slots",        # 공백 슬롯 (수집 0건)
    "thin_slots",         # 라벨은 붙었으나 min_facts 미달 — '충족'과 '충분'은 다르다
    "unfilled_vars",      # 식에서 가정으로 채운 변수
    "suspect_var",        # B3 의 재조사 힌트
    "off_slot",           # 격리 보관된 사실 요약
    "adapters",           # not_configured 어댑터 (커버리지 하한 고지)
    "retry_hints",        # A4·C2 가 낸 힌트 (자동 루프 금지 — 사람이 승인해 1회)
    "unknown_error_codes",  # 분류 못 한 외부 응답 코드 — 발견할 때마다 규칙 파일에 추가한다
    "contradictions",     # 같은 대상·단위인데 값이 갈린 것 · 스케일 의심 (백로그 7)
    "url_filtered",       # A3 에서 URL 로 거른 후보 — 무엇을 안 열었는지 밝힌다 (12-4)
    # 발췌 상한에 걸려 **모델에게 물어보지도 않은** usable 문서 (판 ㉛).
    # 예전엔 `note` 문자열 안에만 있어 성적표에서 「못 찾았다」와 구별되지 않았다 —
    # 「우리가 안 물었다」는 `url_filtered` 와 같은 부류지 자료 부재가 아니다.
    "extract_capped",
    # HTTP 200 을 받고도 본문이 0자였던 문서 (판 ㉛). **거른 것이 아니라 못 가져온 것**이다.
    # 실측 6건이 전부 JS 렌더 페이지였다 — 다음 행동이 「재조사」가 아니라 「수집 수단」이라
    # `empty_slots`(근거 0건인 조사 칸)와 섞지 않는다.
    "fetch_empty",
    "independent_topdown_blocked",   # '더 찾아라' 가 아니라 '찾아도 없다' — retry_hints 와 다르다
    # **자료 부재 확정** (판 ⑯ ②). 「아직 못 찾았다」가 아니라 **「그 형태로 발행되지 않는다」**다.
    # `retry_hints`(더 찾아라)·`empty_slots`(수집 0건)와 **섞지 않는다** — 셋은 다음 행동이 다르다:
    #   retry_hints → 재조사 승인 · empty_slots → 수집 진단 · 여기 → **가정 승격 또는 종착**
    # 판 ⑭ 3분류의 (c) 가 이 칸의 재료다. **산문이 아니라 값으로 싣는다** —
    # 수신 코드는 산문을 못 읽고, 그것이 `대조_기반`(n=1)을 값으로 만든 것과 같은 계보다.
    "자료_부재_확정",
)
