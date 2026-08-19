# -*- coding: utf-8 -*-
"""칸 → 근거 매핑 — **LLM 0회.** 근거 id · 출처 라벨 · 상태를 **기계가 확정한다.**

<b>왜 이 층이 있나.</b> 캔버스를 쓰는 것은 모델이고(`bm/flow.py` — 「모델 호출은 정확히
1회다」), 모델은 자기가 인용할 근거도 스스로 고른다. 실측(2026-08-13, 프로젝트 3 HMR):
원장에 근거 17건이 있고 BM 봉투가 그것을 전부 실어 보냈는데 **캔버스가 인용한 것은 0건**,
그러면서 관측 3칸이 `labels=['concept_snapshot']` 로 `VERIFIED` 였다. `concept_snapshot`
은 **사용자가 쓴 컨셉 서술문**이다 — 모델이 자기 입력을 자기가 확인했다고 도장 찍었다.

<b>그래서 셋을 모델에게 안 맡긴다.</b> 카드의 `칸` 은 원장이 이미 알고 있고, 라벨은 그
`칸` 에서 나오고, 상태는 근거 개수와 `verdict` 도장에서 나온다. 셋 다 모델이 지어낼 수
없는 사실이다. 모델에게 남는 것은 문장(`content`·`reason`)과 해석뿐이다.

<b>덮는 대상은 관측 4칸뿐이다.</b> 계획 5칸(`gate.PLANNED_CELLS`)은 손대지 않는다. 이유 둘:

① 계획 칸에 근거 id 가 하나라도 붙으면 `serialize._stamp_user_plan()` 이 그 칸을 통째로
   건너뛰어(`serialize.py:517`) **PLAN 도장과 「사용자가 입력한 실행 계획이다 — 관측이
   아니다」 경계 문구가 사라진다.**
② 계획 5칸 상태를 여기서 파생하면 `gate` G4(계획 5칸 전부 관측 미달)가 상시 발동해
   판정 상한이 영구 `CONDITIONAL` 이 된다.

<b>돌리는 자리는 `serialize.canvas_cells()` 보다 반드시 앞이다.</b> 그 함수가
`marketEvidenceIds` 에서 칸의 경계(`caveats`)를 파생하고 `assert_caveats_reached()` 와
자바 `requireCaveats` 가 같은 불변식을 두 층에서 막는다 — 뒤에서 id 를 바꾸면 BM 결과가
통째로 거부된다. 순서는 **mapping → citation.enforce → _stamp_user_plan → gate** 다.
"""
from __future__ import annotations

from ..research.bm.contracts import CanvasStatus
from ..research.bm.prompt import ALLOWED_CANVAS_SOURCE_LABELS
from .gate import OBSERVED_CELLS, _MARKET_LABELS

#: 카드의 `칸` → 캔버스 칸. 정본은 `research2/harness/vocab.json` 의
#: `canvas.측정판정.cells`(칸 이름은 한글, 값이 claim_type)이고 여기는 **enum 이름으로만**
#: 바꾼다.
#:
#: 관측 카드의 `칸` 은 **항상 claim_type 이다.** `cards.py:99` 는 `_canvas_cell or
#: claim_type` 이지만 슬롯 정의(`research2/data/slots_*.json` 40개 중 37개)가 싣는
#: `_canvas_cell`(한글)이 원장까지 못 온다 — `run.py:42 mk_slot` 이 `_` 접두 키를 통째로
#: 버리기 때문이다(절대 규칙 6, 자기확인 회로 차단). 그래서 항상 claim_type 으로 떨어진다.
#: ⚠ `Slot` 에 `canvas_cell` 이 승격되면 이 전제가 뒤집힌다 — 그때는 `_cell_of` 가 관측
#:   카드를 **어느 칸에도 안 붙여** 관측 4칸이 근거 0건이 되고 G1 이 네 번 걸린다(fail-closed).
#:   `tests/test_validation_mapping.py` 가 그 모양을 못박아 둔다.
#:
#: ⚠ `GROWTH` 는 vocab 의 `claim_types_by_formula.F_GROWTH` 예외로 고객 세그먼트에 실린다.
CLAIM_TYPE_CELL = {
    "TAM": "CUSTOMER_SEGMENTS",
    "SAM": "CUSTOMER_SEGMENTS",
    "GROWTH": "CUSTOMER_SEGMENTS",
    "PAIN": "VALUE_PROPOSITIONS",
    "COMP": "VALUE_PROPOSITIONS",
    "COMPARABLE": "VALUE_PROPOSITIONS",
    "CHANNEL": "CHANNELS",
    "PRICE": "REVENUE_STREAMS",
    "ALT": "REVENUE_STREAMS",
}

#: **절(section) → 캔버스 칸.** 판 ㊸ 이 승격한 절 사실 카드가 오는 길이다.
#:
#: 승격 카드(`tools/promote_cards.py`)는 슬롯 카드와 **모양이 다르다** — `칸`(claim_type)이
#: 없고 `_절`(`PG.절()` 이 정한 게재 절)만 있다. 그래서 위 `CLAIM_TYPE_CELL` 로는 어느 칸에도
#: 안 붙고, 절 조사가 찾아낸 사실 128건이 캔버스에서 **인용 0건**이 된다.
#:
#: ⚠ **셋만 잇는다.** 절이 말하는 주장과 칸이 말하는 주장이 «같은 것»만이다.
#:   `MARKET_SIZE`·`GROWTH`·`COMPETITOR` 는 **일부러 뺐다** — 시장이 11조라는 것과
#:   「수도권 25~44세 1인 가구」라는 **세그먼트 정의가 맞다는 것**은 다른 주장이다. 이으면
#:   고객 세그먼트 칸의 근거 수가 불어 배지가 「근거 있음」으로 굳고, 승격 카드를 슬롯 판정에
#:   안 넣기로 한 규율이 **배지 층에서 도로 뚫린다.** (실측 결함 「가치 제안 근거 3장이 전부
#:   경쟁사 전사 매출」이 같은 뿌리다.) 그 셋은 시장분석 화면의 **시장 배경 근거**로만 남는다.
#: ⚠ `UNIT_ECONOMICS`·`REGULATION` 도 안 잇는다 — 대응하는 관측 칸이 없다. 계획 5칸에 붙이면
#:   `serialize._stamp_user_plan()` 이 그 칸을 건너뛰어 「사용자가 입력한 계획이다」 경계가
#:   사라진다. **모르는 것을 아는 척하지 않는다.**
#: ⚠⚠ **2026-08-15 — 비웠다. 자료가 그 칸의 주장이 아니었다.**
#:
#: 유료 실행(`p46-bm-01`)으로 실제 자료를 붙여 놓고 사람이 읽었다. 결과:
#:
#:   DEMAND → 가치 제안 (105건) — 「가업승계 비율」·「기부 경험 비율」·「자원봉사 참여 의사」
#:     ·「해외여행 경험율」·「국민의 취침 시각」·「장애인과의 유대관계」·「학생 평균 학습시간」
#:     ·「전북도민 외로움」… **간편식·1인 가구 수요와 무관한 것이 8할이 넘는다.**
#:   PRICE → 수익원 (105건) — 「자가가구의 주택가격 평균」·「전세보증금 평균」·「CPM」
#:     ·「TV 광고 도달」이 **판매가와 나란히** 실린다.
#:   CHANNEL → 채널 (4건) — 「귀촌 전 거주지역 구성비」·우체국 택배 배송기간 2건.
#:     쓸 만한 것은 「간편식 온라인 쇼핑몰 구입 19.7%」 **한 건뿐**이다.
#:
#: 미리 못박아 둔 실패선은 「한 칸에서 «아니다»가 1/3 이상이면 그 칸 배지는 거짓」이었고,
#: **세 칸 모두 그것을 넘겼다.** 근거표에 이 목록이 뜨면 사업가는 「내 사업의 수요 근거
#: 105건」으로 읽는다 — **빈 칸보다 나쁘다.**
#:
#: ⚠ **배선을 지운 것이 아니다.** `_cell_of`·`_label_of` 와 시험은 그대로 두고 표만 비웠다.
#:   병은 이 층이 아니라 **절 배정**에 있다(`tools/publish_gate.절()` — 시장조사 판 소유).
#:   「취침 시각」이 DEMAND 절로 가는 것을 여기서 고칠 수는 없다. 그쪽이 절 배정을 손보면
#:   **이 표에 세 줄을 되돌리는 것으로 다시 켜진다.** 되켤 때는 같은 잣대로 다시 읽는다.
#:
#: ★★ **2026-08-15 재측정 — 「우리 세그먼트만 고르면 켤 수 있나」. 답: 아직 아니다.**
#:
#: 우회로를 하나 재 봤다. 카드에는 이미 갈래 꼬리표가 실려 오므로(`promote_cards`
#: `_갈래`), `_갈래 == "OURS_SEGMENT"` 인 것만 고르면 절 배정을 안 고치고도 켤 수 있을까.
#: **0원 · LLM 0회** — `mapping` 은 순수 함수라 이미 있는 원장(`runs-generated/p43-wire/
#: publish.json`, 승격 128장)에 걸어 세었다. 같은 실패선(1/3)으로 인용문을 사람이 읽었다.
#:
#:   승격 128장 → OURS_SEGMENT **24장** (DEMAND 13 · MARKET_SIZE 7 · PRICE 3 · COMPETITOR 1)
#:
#:   DEMAND → 가치 제안 (13장) — **실패선을 넘는다.** 「소비자안전불안 경험률」·「국민의
#:     필수시간 11시간 32분」·「**전업주부** 점심 집밥 80.5%」·「주말 저녁 **가족과 함께**
#:     60.0%」가 섞이고, 인용이 숫자 하나뿐인 것이 3장(「61.2」·「67.8」·「39.1」),
#:     주제는 「평균 식사일」인데 값이 `%` 인 **깨진 짝**이 2장이다. 쓸 만한 것은
#:     「1인가구 균형 잡힌 식사의 어려움 42.6%」와 「평일 점심 혼자 식사 39.1%」 정도다.
#:   PRICE → 수익원 (3장) — 「신선식품지수 -2.3%」는 우리 판매가가 아니다. **3장은
#:     판정할 표본이 아니다.**
#:   CHANNEL → 채널 (**0장**) — 필터가 22장을 전부 걷어낸다. 켜도 **빈 칸 그대로**다.
#:
#: **판정: 켜지 않는다.** 갈래 꼬리표는 「이 수가 누구의 수인가」를 가르지만
#: **「이 수가 이 칸의 주장을 받치는가」는 못 가른다** — 병이 절 배정에 있다는 위 진단이
#: 재측정으로 한 번 더 확인됐다. 세 줄을 되돌리는 것은 **그쪽이 절 배정을 고친 뒤**다.
#: ⚠ 원장이 갈렸다: 위 「105건」은 `p46-bm-01`, 이번 재측정은 `p43-wire` 다
#:   (`p46-bm-01` 의 원본 `publish.json` 이 디스크에 없다). 같은 컨셉·다른 실행이다.
SECTION_CELL: dict[str, str] = {}

#: 절 → 출처 라벨. 값은 전부 `gate._MARKET_LABELS` 안에 있어야 한다 —
#: 그렇지 않으면 근거를 붙여 놓고도 게이트 G1 이 「시장 근거 0건」이라고 말한다.
SECTION_LABEL = {
    "CHANNEL": "channel_analysis",
    "PRICE": "price_analysis",
    "DEMAND": "demand_evidence",
}

#: 한글 칸 이름 → 캔버스 칸. **계산 카드(`C-CALC-*`) 전용 보조 경로다** — 그 카드만 이 길로
#: 온다(`cards.py:165` 가 「고객 세그먼트」를 글자 그대로 박는다). 저장소에 한글↔enum
#: 사상표가 없어 여기서 새로 만든다. `tests/test_validation_mapping.py` 가 vocab.json 과 대조한다.
#:
#: ⚠ **관측 카드에는 이 길을 열지 않는다.** 열어 두면 위 전제가 뒤집혔을 때(=`칸` 이 한글로
#:   오는 날) 근거 id 는 붙는데 라벨은 0건이 되고, `_labels_for` 폴백이 모델이 쓴
#:   `concept_snapshot` 을 되살린다 — 이 층이 막으려던 바로 그 상태로 **조용히** 되돌아간다.
#:   막힌 채 시끄럽게 실패하는 편(G1)이 낫다.
CELL_NAME_KO = {
    "고객 세그먼트": "CUSTOMER_SEGMENTS",
    "가치 제안": "VALUE_PROPOSITIONS",
    "채널": "CHANNELS",
    "수익원": "REVENUE_STREAMS",
}

#: `칸`(claim_type) → 출처 라벨. 값은 전부 `gate._MARKET_LABELS` 안에 있다 —
#: 라벨 화이트리스트는 이미 세 벌(`bm/prompt.py`·`gate.py`·자바)이라 **네 번째를 만들지
#: 않는다.** 이 표는 그 화이트리스트가 아니라 **칸 → 라벨 사상**이고, 값은 전부 그쪽 목록
#: 안에 있어야 한다.
#:
#: ⚠ 예전에는 `CHANNEL` 자리가 **비어 있었다**(「맞는 라벨이 화이트리스트에 없다」).
#:   그 결과 채널 칸의 파생 라벨이 **언제나 0건**이었고, `_labels_for()` 폴백이 모델이 쓴
#:   라벨을 되살려 `concept_snapshot`(= 사용자가 쓴 컨셉 서술문)이 근거 자리에 앉았다 —
#:   이 층이 막으려던 「자기 입력을 자기가 확인」이 **채널 칸에서만** 열려 있었다.
#:   그래서 `channel_analysis` 를 화이트리스트 넷(`bm/prompt.py` 집합·같은 파일 프롬프트
#:   본문·자바 `SOURCE_LABELS`·`gate._MARKET_LABELS`)에 **더하고** 여기를 채웠다.
CLAIM_TYPE_LABEL = {
    "TAM": "market_size",
    "SAM": "market_size",
    "GROWTH": "growth_rate",
    "COMP": "competitor_analysis",
    "COMPARABLE": "competitor_analysis",
    "PRICE": "price_analysis",
    "ALT": "price_analysis",
    "PAIN": "demand_evidence",
    "CHANNEL": "channel_analysis",
}

#: 계산 카드 id 접미사 → 라벨. `C-CALC-{TAM,SAM,성장률}`(`cards.py:163`).
_CALC_LABEL = {"TAM": "market_size", "SAM": "market_size", "성장률": "growth_rate"}

#: `verdict["판정"]` 의 도장 키 → 캔버스 칸. **넷 중 셋만 칸이 있다**
#: (`verdict.py:745-750`) — `9_SOM_초기점유` 는 캔버스 칸이 아니고, 거꾸로
#: `CUSTOMER_SEGMENTS` 에 대응하는 도장이 없다. 그 칸은 근거 개수만으로 정한다.
STAMP_CELL = {
    "6_수익_가격": "REVENUE_STREAMS",
    "7_채널": "CHANNELS",
    "8_차별점": "VALUE_PROPOSITIONS",
}

#: 도장이 있는 칸의 상태. 근거가 0건이면 도장과 무관하게 `UNVERIFIED` 다.
_STAMP_STATUS = {"검증됨": CanvasStatus.VERIFIED}

#: 도장이 없는 칸(`CUSTOMER_SEGMENTS`)에서 `VERIFIED` 로 보는 최소 근거 수.
#: 시장 크기·성장률이 같은 칸에 실리므로 한 건은 「부분」이다.
_VERIFIED_MIN_EVIDENCE = 2


def _cell_of(card: dict) -> str | None:
    """카드 하나가 어느 칸인가. 못 정하면 `None`(그 카드는 어느 칸에도 안 붙는다).

    한글 칸 이름은 **계산 카드에서만** 읽는다(`CELL_NAME_KO` 주석 참조).
    승격 카드는 `칸` 이 없고 `_절` 만 있어 `SECTION_CELL` 로 간다.
    """
    name = str(card.get("칸") or "")
    cell = CLAIM_TYPE_CELL.get(name)
    if cell:
        return cell
    if _calc_suffix(card) is not None:
        return CELL_NAME_KO.get(name)
    return SECTION_CELL.get(str(card.get("_절") or ""))


def _calc_suffix(card: dict) -> str | None:
    """계산 카드면 id 접미사(`TAM`·`SAM`·`성장률`), 아니면 `None`."""
    card_id = str(card.get("카드_id") or "")
    prefix = "C-CALC-"
    return card_id[len(prefix):] if card_id.startswith(prefix) else None


def _label_of(card: dict) -> str | None:
    """카드 하나가 만드는 출처 라벨. 계산 카드는 id 접미사에서, 승격 카드는 `_절` 에서 읽는다."""
    name = str(card.get("칸") or "")
    label = CLAIM_TYPE_LABEL.get(name)
    if label:
        return label
    suffix = _calc_suffix(card)
    if suffix is not None:
        return _CALC_LABEL.get(suffix)
    return SECTION_LABEL.get(str(card.get("_절") or ""))


def _is_promoted(card: dict) -> bool:
    """승격 카드인가 — **`칸`(claim_type)이 없고 `_절` 로만 붙은 카드**.

    슬롯 카드는 수집이 «슬롯 정의에 맞춰» 채택한 것이고, 승격 카드는 절 조사가 문서에서
    건져 「그 절에 실을 만하다」고 본 것이다. **후자는 칸의 주장을 겨냥해 모은 것이 아니다.**
    """
    return not str(card.get("칸") or "") and bool(str(card.get("_절") or ""))


def _stamps(verdict: dict | None) -> dict[str, str]:
    """`verdict` → 칸별 도장. 없으면 빈 dict."""
    out = {}
    for key, cell in STAMP_CELL.items():
        entry = ((verdict or {}).get("판정") or {}).get(key) or {}
        stamp = entry.get("도장")
        if stamp:
            out[cell] = str(stamp)
    return out


def derive(cards: list[dict], verdict: dict | None = None) -> dict[str, dict]:
    """카드(+`verdict`) → **관측 4칸**의 `marketEvidenceIds`·`sourceLabels`·`status`.

    근거 id 는 `serialize.evidence(cards)` 와 **같은 카드 리스트**에서만 뽑는다 — 자바
    계약이 `marketEvidenceIds ⊆ evidence[].id` 를 요구하므로 등급 등으로 거르면 안 된다.

    ⚠ **승격 카드는 근거로 «붙되» 상태를 «올리지 못한다»** (2026-08-15 신설). 실측으로 잡은
      결함이다 — 승격 카드를 개수에 같이 세니 채널 칸이 `UNVERIFIED`(근거 0건)에서
      `VERIFIED`(근거 4건)로 뒤집혔는데, 그 4건이 **귀촌 전 거주지역 구성비 · 우체국 택배
      배송기간 2건 · 온라인몰 구입 비율 1건**이었다. 즉 화면이 「채널은 시장이 확인해 줬다」고
      말하는데 근거가 귀촌 통계다. **0건 `UNVERIFIED` 는 참말이었고 4건 `VERIFIED` 는
      거짓말이다 — 빈손보다 나쁘다.**

      뿌리는 판 ㊸ 의 규율(「승격 카드를 `verdict`·`scorecard` 판정에 넣지 않는다」)을
      **상태 층에서 뚫은 것**이다. 승격 카드는 «그 절에 실을 만한 사실»이지 «이 칸의 주장을
      겨냥해 모은 근거»가 아니다(실측: 승격 396장 중 315장이 등급 「추정」).

      그래서 **id·라벨은 그대로 붙이고**(화면 근거표에 뜨고 자바 계약도 만족한다)
      **개수만 슬롯 카드로 센다.** 화면에는 「아직 확인 못 했지만 참고 근거 N건」이 선다.
    """
    ids: dict[str, list[str]] = {name: [] for name in OBSERVED_CELLS}
    labels: dict[str, list[str]] = {name: [] for name in OBSERVED_CELLS}
    #: 상태를 «올릴 수 있는» 근거 수 — 슬롯 카드만 센다. 위 ⚠ 참조.
    direct: dict[str, int] = {name: 0 for name in OBSERVED_CELLS}
    for card in cards or []:
        cell = _cell_of(card)
        if cell not in ids:
            continue
        card_id = str(card.get("카드_id") or "")
        if not card_id or card_id in ids[cell]:
            continue
        ids[cell].append(card_id)
        if not _is_promoted(card):
            direct[cell] += 1
        label = _label_of(card)
        if label and label not in labels[cell]:
            labels[cell].append(label)

    stamps = _stamps(verdict)
    out = {}
    for name in OBSERVED_CELLS:
        count = direct[name]
        if count == 0:
            # ⚠ 승격 근거가 아무리 많아도 여기다. 「참고 근거는 있으나 확인은 못 했다」가
            #   이 칸의 사실이고, `marketEvidenceIds` 에 그 참고 근거가 그대로 실려 나간다.
            status = CanvasStatus.UNVERIFIED
        elif name in stamps:
            status = _STAMP_STATUS.get(stamps[name], CanvasStatus.PARTIAL)
        elif count >= _VERIFIED_MIN_EVIDENCE:
            status = CanvasStatus.VERIFIED
        else:
            status = CanvasStatus.PARTIAL
        out[name] = {"marketEvidenceIds": ids[name],
                     "sourceLabels": labels[name],
                     "status": status}
    return out


#: 폴백이 세울 수 있는 마지막 라벨. **가장 약한 출처**이고, 이것이 사실이다 —
#: 기계가 시장 근거를 하나도 못 찾았다면 그 칸의 출처는 사용자가 쓴 컨셉 서술문뿐이다.
_WEAKEST_LABEL = "concept_snapshot"


def _labels_for(item, derived: list[str]) -> list[str]:
    """파생 라벨. **비우지 않는다** — `content` 가 있는데 라벨이 0건이면 자바가 거부한다
    (`MarketResearchContract.java:347`).

    ⚠ **폴백은 시장 라벨을 절대 되살리지 않는다.** 게이트 G1 은 두 문 중 하나만 통과하면
      안 걸린다(`gate.py:98-108`) — ①근거 id 가 있다 ②**시장 라벨이 하나라도 있다**.
      예전 폴백은 모델이 쓴 라벨을 화이트리스트로만 걸러 되살렸는데, 거기 `market_size`
      같은 시장 라벨이 섞여 있으면 ②가 통과했다. **근거 0건인데 게이트가 안 걸린 것이다.**
      즉 모델이 「이 칸은 market_size 에서 왔다」고 **쓰기만 하면** 반증을 피했다 —
      이 층이 존재하는 이유(「모델이 쓴 값을 안 믿는다」)가 라벨에서 새고 있었다.

      그래서 되살리는 것은 **시장 라벨이 아닌 것**(`concept_snapshot`·
      `execution_constraints`)뿐이고, 그것마저 없으면 `concept_snapshot` 하나를 세운다.
      자바 계약은 만족하고, G1 은 제대로 걸린다.
    """
    if derived or not item.content:
        return derived
    kept = [label for label in item.source_labels
            if label in ALLOWED_CANVAS_SOURCE_LABELS and label not in _MARKET_LABELS]
    return kept or [_WEAKEST_LABEL]


def apply(analysis, cards: list[dict], verdict: dict | None = None):
    """`BMAnalysisResult` → 관측 4칸을 기계 파생값으로 덮은 복사본.

    계획 5칸은 **그대로 돌려준다**(근거 id 가 붙는 순간 `USER_PLAN_CAVEAT` 이 사라진다).
    법률이 낸 `BLOCKED` 도 덮지 않는다 — 이 층은 판정을 올리는 자리가 아니다.

    ⚠ `model_copy(update=...)` 는 검증을 안 거친다. `status` 에 평문 문자열을 넣으면
      직렬화가 `status.value` 에서 터지므로 **enum 을 넣는다**.
    """
    derived = derive(cards, verdict)
    cells = []
    for item in analysis.canvas:
        found = derived.get(str(item.canvas_cell))
        if found is None:
            cells.append(item)                      # 계획 5칸 — 손대지 않는다
            continue
        update = {
            "market_evidence_ids": list(found["marketEvidenceIds"]),
            "source_labels": _labels_for(item, list(found["sourceLabels"])),
        }
        if item.status != CanvasStatus.BLOCKED:
            update["status"] = found["status"]
        cells.append(item.model_copy(update=update))
    return analysis.model_copy(update={"canvas": cells})
