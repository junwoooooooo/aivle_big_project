# -*- coding: utf-8 -*-
"""**게재 판정** — 뽑아 놓은 사실을 「어디에 실을지」 정한다. **LLM 0회.** (판 ㊴ 항목 1)

    python tools/publish_gate.py runs-generated/p39-secFULL/sections.json \
           --concept data/concept_hmr-product.json

**버리는 게 아니라 자리를 정한다.** 0순위 철학이 「문서는 하나도 안 버린다」이므로
②읽기에서 버린 것은 없고, 여기서 **무엇을 실을지**만 고른다. 실제로 버리는 것은
`OFF_TOPIC` 뿐이고 그것도 **사유를 남긴다**(규칙 5 — 실패는 값이다).

다섯 갈래:

| 갈래 | 뜻 | 어디로 |
|---|---|---|
| `OURS_SEGMENT` | 우리 세그먼트의 실측 | 해당 절 |
| `OURS_UMBRELLA` | 상위 범주 값 | 해당 절 + **「상한으로만 읽을 것」** |
| `SUBSTITUTE` | 소비자가 대체 수단에 내는 값 | 절 안의 비교 칸(상한 있음) |
| `COMPETITOR_FIRM` | 특정 회사의 사업 수치 | `COMPETITOR` 절로 **재배정** |
| `밖` | 남의 업종 · 해외 · 상위 범주 밖 · 중복 | **싣는다.** 서랍에 접고 「어떻게 읽을지」를 붙인다 |
| `OFF_TOPIC` | **사실이 아니다** — 값 없음 · 값 자리에 숫자 없음 · 인용에 값 없음 | 안 싣고 사유만 |

⚠ **대상 어휘를 코드에 박지 않는다.** 컨셉 파일에서 뽑고, 무엇을 뽑았는지 **출력에 찍는다**.
역할 표지(지불·산업내부·상위범주)만 `rules/publish.v1.json` 에 있다 — 그건 업종과 무관하다.

인용 대조도 **여기서 다시 매긴다**(`read_sections._norm` 이 문장부호까지 관용해졌다).
원장 본문을 다시 읽을 뿐이라 **돈이 들지 않는다.**
"""
from __future__ import annotations

import argparse, io, json, os, re, sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
for p in (ROOT, HERE, os.path.join(ROOT, "adapters")):
    sys.path.insert(0, p)

import read_sections as RS   # _corpus · _norm — 한 잣대를 두 곳에서 쓰지 않는다

TOKEN = re.compile(r"[가-힣A-Za-z0-9]+")
DIGIT = re.compile(r"[0-9A-Za-z]")
NUM = re.compile(r"[0-9]")        # **값 자리에 숫자가 있나** — DIGIT 은 영문도 참이라 못 쓴다


def _rules() -> dict:
    p = os.path.join(ROOT, "rules", "publish.v1.json")
    return json.load(io.open(p, encoding="utf-8"))


def _tokens(text: str, R: dict) -> set:
    """한글 텍스트에서 **대상이 될 만한 말**만 뽑는다. 형태소 분석기 없이 조사만 벗긴다."""
    out = set()
    for w in TOKEN.findall(text or ""):
        for j in sorted(R["조사"], key=len, reverse=True):
            if len(w) - len(j) >= R["최소_길이"] and w.endswith(j):
                w = w[:-len(j)]
                break
        if len(w) < R["최소_길이"] or w in R["불용어"]:
            continue
        if w.endswith(tuple(R["용언_꼬리"])):
            continue
        # 조사를 못 벗긴 두 글자(「곳이·끼를」)는 말이 아니라 찌꺼기다
        if len(w) == R["최소_길이"] and w[-1] in "은는이가을를에의와과도로만":
            continue
        out.add(w)
    return out


def _수(it: dict):
    """`number_raw` 를 수로. 못 읽으면 `None` — **0 으로 치지 않는다**(합이 왜곡된다)."""
    m = re.search(r"[0-9][0-9,]*(?:\.[0-9]+)?", str(it.get("number_raw") or ""))
    return float(m.group(0).replace(",", "")) if m else None


def _matcher(word: str):
    """낱말 하나를 찾는 함수. **숫자·영문이 섞인 말은 경계를 본다.**

    실측: 컨셉 `target` 의 「25~44세」에서 나온 토큰 `25` 가 「**20**25년」·「GS**25**」·
    「6**25**,822,442원」에 전부 걸려, 실린 161건 중 **44건(27%)**이 그것 하나 때문이었다.
    한글은 조사가 붙어 오므로 경계를 못 건다 — 「가정간편식」의 '간편식'을 놓치게 된다.
    """
    if not DIGIT.search(word):
        return lambda t: word in t
    rx = re.compile(r"(?<![0-9A-Za-z가-힣])" + re.escape(word) + r"(?![0-9])")
    return lambda t: bool(rx.search(t))


def _vocab(concept: dict, R: dict) -> dict:
    """컨셉에서 어휘를 **갈래별로** 뽑는다. 갈래가 섞이면 판정이 무너진다.

    ⚠ 회사·채널 이름은 **그 자체로 통과 사유가 아니다**(`상호`). 컨셉이 「쿠팡」을 채널로
    적었다고 「쿠팡 개인정보 유출 3,370만 명」이 우리 보고서에 실려서는 안 된다.
    """
    j = lambda *xs: " ".join(str(x or "") for x in xs)
    d5 = concept.get("_다듬기5") or {}
    h2 = concept.get("_hypotheses_v2") or {}

    세그먼트 = _tokens(j(concept.get("name"),
                      (d5.get("4_업종_분류") or {}).get("명칭"),
                      d5.get("3_핵심_가치"),
                      concept.get("solution")), R)
    대상 = _tokens(concept.get("target"), R)

    # **컨셉이 problem 에서 지목한 것이 대체재다.** 채널 목록보다 이쪽이 우선한다 —
    # 실측: 「편의점」이 채널이기도 해서 상호로 밀리자, 컨셉이 직접 「저가 편의점 제품」이라
    # 적은 도시락 가격 12건이 통째로 떨어졌다. 차별점이 「편의점 저가와 배달 사이의 빈
    # 가격 구간」인데 그 양끝 중 하나를 잃는 것이다.
    대체 = _tokens(concept.get("problem"), R) - 세그먼트 - 대상

    # **컨셉이 실명으로 적은 회사 이름은 용언 꼬리를 깎지 않는다** (판 ㊷ R1).
    # 실측: `_tokens("오뚜기")` → `[]`. '기'가 용언 꼬리라서다. 「프레시지」는 '지'로,
    # 「비비고」는 '고'가 조사로 깎여 `비비` 가 됐다 — 컨셉이 회사 이름 5개를 적었는데
    # 어휘에 온전히 남은 것은 **2개뿐**이었다. `_용언_꼬리_왜` 는 「해결하·간편하게」를
    # 막겠다고 적혀 있지 **고유명사를 막겠다는 말이 없다** — 규칙이 자기 이유보다 넓었다.
    상호실명, 발행사실명 = set(), set()
    for s in (concept.get("_경쟁_씨앗") or {}).get("seeds") or []:
        for w in TOKEN.findall(j(s.get("이름"), s.get("운영사"))):
            if len(w) >= R["최소_길이"] and w not in R["불용어"]:
                상호실명.add(w)
        # **발행사 판정에는 회사 이름만 쓴다** (판 ㊷). `이름` 은 「풀무원 **간편식**」처럼
        # 상표에 품목이 붙어 있어, 그것으로 발행사를 재면 **품목 낱말이 회사 행세를 한다** —
        # 실측: 편의점 지주사 공시가 「간편식」 하나로 「참여자」가 되어 그 안의 화학수지
        # 단가까지 경쟁 지형에 실렸다. `운영사` 는 그 자체가 「이 회사」라는 뜻이다.
        for w in TOKEN.findall(str(s.get("운영사") or "")):
            if len(w) >= R["최소_길이"] and w not in R["불용어"]:
                발행사실명.add(w)
    실명 = 발행사실명 or 상호실명     # 운영사가 없는 컨셉이면 이름으로 물러선다

    상호 = set(상호실명)
    상호 |= _tokens(j(*((h2.get("7_채널") or {}).get("제안값") or [])), R)
    상호 -= (세그먼트 | 대상 | 대체)   # 「오뚜기 냉동식품」의 '냉동식품'은 상호가 아니다

    # 실명은 **발행사 판정**(R3)에만 쓴다 — 「이 공시를 낸 회사가 이 시장의 참여자인가」.
    # 상호와 달리 세그먼트를 빼지 않는다: 문서 본문에서 회사를 찾는 것이 목적이다.
    return {"세그먼트": 세그먼트, "대상": 대상, "상호": 상호, "대체": 대체, "실명": 실명}


_M: dict = {}


def _hit(text: str, words) -> list:
    out = []
    for w in words:
        if not w:
            continue
        f = _M.get(w)
        if f is None:
            f = _M[w] = _matcher(w)
        if f(text):
            out.append(w)
    return sorted(out)


# ══════════════════════════════════════════════════════════════
# ★ 세 무리를 가르는 **한 곳** (판 ㊹ 3단계)
#
# 판 ㊸ 까지는 「`게재 != OFF_TOPIC`」이 여덟 군데에 베껴져 있었고, `밖` 이 생기는 순간
# **그 여덟이 전부 조용히 뜻이 달라진다** — 서랍값이 판단 문장·성적표·채점 모집단으로 샌다.
# 답은 여기 셋뿐이다. **새로 세는 곳을 만들지 말고 이것을 부른다.**
# ══════════════════════════════════════════════════════════════
BURIED = "OFF_TOPIC"    #: 사실이 아니다 — 값 없음·숫자 아님·인용에 값 없음. **버린다**
DRAWER = "밖"           #: 우리 주제 밖·해외·중복. **싣고 접는다**


def 버렸나(it: dict) -> bool:
    """**사실이 아니라 버린 것.** 판정을 못 받은 것(`게재` 없음)도 여기다."""
    return not it.get("게재") or it["게재"] == BURIED


def 머리인가(it: dict) -> bool:
    """**절 머리에 서는 것.** 판단 문장·성적표·채점 모집단은 이것만 본다."""
    return bool(it.get("게재")) and it["게재"] not in (BURIED, DRAWER)


def 실었나(it: dict) -> bool:
    """**화면에 가는 것 전부** — 머리 + 서랍. 근거 카드·보고서 렌더가 이것을 본다."""
    return bool(it.get("게재")) and it["게재"] != BURIED


def 재배정(it: dict, R: dict) -> str | None:
    """**추출이 절을 잘못 고른 것을 바로잡는다.** 옮겼으면 새 절, 아니면 `None`.

    실측(판 ㊹ 4단계): 목표 보고서 6절 머릿값 「식료품제조업 영업이익률은 4% 미만」이
    **`PRICE` 절에** 앉아 있었다 — 인용을 되찾아 살려 놓고도 원가 절 머리에는 못 섰다.

    ⚠ **표지가 하나도 안 걸리면 손대지 않는다. 둘 이상 걸려도 손대지 않는다.**
      추측으로 절을 옮기면 사업가가 값을 **엉뚱한 결정에서** 만난다 —
      「없는 것보다 나쁜 것은 틀린 자리에 있는 것」이다.
    """
    표지 = R.get("절_표지") or {}
    주제 = str(it.get("subject") or "")
    지금 = str(it.get("section") or "")
    걸린 = [sec for sec, ws in 표지.items() if any(w in 주제 for w in ws)]
    if len(걸린) != 1 or 걸린[0] == 지금:
        return None
    # 지금 절의 표지도 걸려 있으면 두 주장이 부딪는 것이다 — 안 옮긴다
    if any(w in 주제 for w in 표지.get(지금) or []):
        return None
    return 걸린[0]


def 표지적중(it: dict, R: dict | None = None) -> bool:
    """**이 사실이 그 절이 묻는 것에 «정면으로» 답하나.**

    ★ 판 ㊺ — 갈래(`placement`)와 등급만으로는 못 가르는 자리가 있다. 실측: 1절에서
    「지역자율형바우처 지원 규모 20억원」(정부 사이트 → **확정**)이 「가정간편식 국내
    판매액 6조 8천억」(농경연 PDF → **추정**)을 이겼다 — 둘 다 `OURS_SEGMENT` 라
    갈래로 안 갈리고, 등급으로 세우면 **출처가 훌륭한 곁가지가 내용이 훌륭한 본론을 이긴다.**

    표지가 그 둘을 가른다. 「판매액」은 걸리고 「지원 규모」는 안 걸린다.

    ⚠ **버리는 데 쓰지 않는다.** 표지에 안 걸려도 실린다 — 뒤로 설 뿐이다.
      표지는 어휘라 언제나 빠뜨리는 것이 있고, 그걸로 버리면 조용히 사라진다.
    """
    R = R if R is not None else _rules()
    말 = (R.get("절_표지") or {}).get(절(it)) or []
    주제 = f"{it.get('subject') or ''} {it.get('table_context') or ''}"
    return any(w in 주제 for w in 말)


def 절(it: dict) -> str:
    """**이 사실이 실릴 절.** 게재 판정 뒤의 재배정을 반영한다.

    ⚠ 이 셈이 여섯 군데에 베껴져 있었다(판 ㊸ 2단계에 승격이 일곱째가 될 뻔했다).
    한 곳이라도 어긋나면 **보고서와 화면이 같은 사실을 다른 절에 넣는다** — 그 어긋남은
    테스트가 안 잡고 사람이 두 화면을 나란히 봐야 보인다. 답은 여기 하나다.
    """
    if it.get("게재_제자리"):
        return it["section"]
    return "COMPETITOR" if it.get("게재") == "COMPETITOR_FIRM" else it["section"]


def _첫화면(url: str) -> bool:
    """포털·기업 사이트의 **첫 화면 / 검색 화면**인가.

    ⚠ 도메인으로 막지 않는다 — `kosis.kr` 은 통계표 페이지가 재료의 핵심이다.
      막는 것은 **경로**다: 뿌리(`/`)이거나 마지막 조각이 `index`·`main`·`home`·`default`.
    """
    from urllib.parse import urlparse                               # noqa: PLC0415
    path = (urlparse(url or "").path or "/").rstrip("/")
    if not path:
        return True
    last = path.split("/")[-1].lower().rsplit(".", 1)[0]
    return last in ("index", "main", "home", "default")


def _단위갈래(unit: str, num: str, 허용: list, R: dict) -> bool:
    """단위가 `허용` 갈래(금액·비율…) 중 하나인가.

    ⚠ **단위 칸만 보지 않는다.** 발췌가 `unit_raw` 를 비우고 `number_raw` 에 「1조 1,666억원」
      처럼 단위를 통째로 넣는 일이 잦다 — 단위 칸만 보면 그 값이 통째로 떨어진다.
    """
    표 = R.get("_단위_갈래") or {}
    hay = f"{unit or ''} {num or ''}"
    return any(any(w in hay for w in (표.get(g) or [])) for g in 허용)


def 분류(it: dict, V: dict, R: dict, url: str = "", 발행사: bool = None) -> tuple:
    """(갈래, 왜, **제자리**). **판정 순서가 곧 우선순위다.**

    `제자리` 가 참이면 `COMPETITOR_FIRM` 꼬리표를 **단 채로 자기 절에 남는다**(판 ㊷ R2).
    갈래는 「이 수가 누구의 수인가」(출처)이고 절은 「사업가가 어느 결정에서 보나」(자리)라,
    둘은 다른 물음이다. 목표선 보고서도 6,513원을 **가격 절**에 실으면서 「오뚜기 사업보고서」
    꼬리표를 붙였다.

    `발행사` 는 「이 공시를 낸 회사가 컨셉이 말한 시장의 참여자인가」다(R3). `None` 이면
    판정하지 않는다(공시가 아닌 문서).
    """
    t = " ".join(str(it.get(k) or "")
                 for k in ("subject", "quote", "table_context"))
    역할 = R["역할_어휘"]
    unit = str(it.get("unit_raw") or "")
    num = str(it.get("number_raw") or "").strip()
    q = str(it.get("quote") or "")
    주어0 = str(it.get("subject") or "")

    # ══════════════════════════════════════════════════════════
    # ★ 판 ㊹ 3단계 — **「사실이 아니다」와 「우리 주제가 아니다」를 가른다.**
    #
    # 여기 아래 세 문(값 없음 · 숫자 아님 · 인용에 값 없음)만 `OFF_TOPIC` 으로 **버린다.**
    # 그 셋은 «사실이 아니다» — 실을 것이 애초에 없다.
    #
    # **주제로 버리던 것은 전부 `밖` 으로 «싣는다».** 사유는 그대로 남기고 화면이 접는다.
    # 왜 — 실측(판 ㊸ 유료 스모크): 폐기 450건 중 **333건이 주제 판정**이었고, 그 안에
    # 「식료품 제조업 영업이익률」·「세계 즉석조리식품 1,465억 달러」처럼 **목표 보고서가
    # 머리에 세운 값**들이 들어 있었다. **갈래 이름은 버릴 이유가 아니라 읽는 법이다.**
    # ══════════════════════════════════════════════════════════
    # ★ **규제 절의 사실은 «수»가 아니라 «지켜야 할 것»이다** (판 ㊹ 3단계).
    #   실측(p41-merged): REGULATION 44건이 **전부** 「값이 없다」로 버려졌고, 그 정체는
    #   「HACCP 인증」·「영양표시 의무화」였다 — 목표 보고서 7절이 바로 그 자리다.
    #   ⚠ 아무 문장이나 살리면 판 ㊷ R5 가 막던 병(「세균수」가 값 자리에 앉아 「규제 조사
    #     완료」로 읽히던 것)이 돌아온다. **인용에 요건 표지가 있을 때만** 살린다 —
    #     이름표(「세균수」)와 요건(「세균수 1g당 100 이하」)을 가르는 문이다.
    #   ⚠⚠ **남의 «인증 취득 연혁»은 우리가 지켜야 할 것이 아니다** (판 ㊹ 6단계 정정).
    #     「획득·취득·보유·수상」이 걸리면 요건이 아니라 **그 회사의 이력**이다.
    if (not num and it.get("section") == "REGULATION"
            and _hit(q, R.get("요건_표지") or [])
            and not _hit(f"{주어0} {q}", R.get("요건_반증") or [])):
        return "OURS_SEGMENT", "수가 아니라 지켜야 할 요건", True
    if not num:
        return "OFF_TOPIC", "값이 없다", False
    # **값 자리에 숫자가 없으면 값이 아니다** (판 ㊷ R5). 실측: 규제 절에 실린 9건의
    # `number_raw` 가 「세균수·대장균·살모넬라…」였고 지켜야 할 기준치는 하나도 없었다.
    # 사업가는 그것을 「규제 조사 완료」로 읽는다. `생산능력 산출 방법 = "- -"` 도 같은 문이다.
    #   ⚠ **규제 절은 값이 «인용 안»에 있다.** 실측(판 ㊹ 6단계): 「세균수·대장균·살모넬라·
    #     냉장온도·온장온도」 11건이 전부 여기서 죽었는데, 인용에는 「1g당 100 이하」·
    #     「0~10℃」 같은 **진짜 기준치**가 들어 있었다. 목표 보고서 7절이 그 표다.
    #     `number_raw` 가 이름표인 것은 추출의 흠이지 «사실이 없다»는 뜻이 아니다.
    if (not NUM.search(num) and it.get("section") == "REGULATION"
            and NUM.search(q) and _hit(q, R.get("요건_표지") or [])
            and not _hit(f"{주어0} {q}", R.get("요건_반증") or [])):
        return "OURS_SEGMENT", f"기준치가 인용 안에 있다({num})", True
    if not NUM.search(num):
        return "OFF_TOPIC", f"값 자리에 숫자가 없다({num})", False
    # **값이 인용 안에 있어야 한다** (판 ㊲ 다섯째 겹). 인용 대조를 통과해도 그 인용이
    # 그 수의 근거라는 보장은 없다 — 실측: 「식품서비스유통 부문 매출 8,980억원」의 인용이
    # 「2024년 11월에는 '흑백요리사' 셰프를 브랜드 앰버서더로 발탁하여」였다.
    if RS._norm(num.split("~")[0]) not in RS._norm(q):
        return "OFF_TOPIC", "값이 인용 안에 없다", False
    if unit in R["외화_단위"]:
        return "밖", f"국내 값이 아니다(단위 {unit})", False

    # ★ 판 ㊺ — **사이트 첫 화면·검색 화면의 수는 절의 답이 아니다.**
    #   왜인지는 `rules/publish.v1.json` 의 `_첫화면_왜` 에 실측과 함께 적었다.
    #   ⚠ **버리지 않고 «밖»으로 싣는다** — 값이 거짓인 게 아니라 그 절의 답이 아닐 뿐이다.
    if _첫화면(url):
        return "밖", "사이트 첫 화면·검색 화면의 수다", False

    # ★ 판 ㊺ — **절마다 답의 «단위»가 있다.** `_절_단위_왜` 에 실측과 함께 적었다.
    #   ⚠ **금지가 아니라 허용**이라, 목록에 없는 절은 한 줄도 안 바뀐다.
    허용 = (R.get("절_단위_허용") or {}).get(it.get("section") or "")
    if 허용 and not _단위갈래(unit, num, 허용, R):
        return "밖", f"이 절이 묻는 단위가 아니다({unit or num})", False

    # **문서가 무엇인지가 그 안의 수가 무엇인지를 정한다.** 문장만으로는 못 가른다.
    출처 = R["출처_유형"]
    u = (url or "").lower()
    if any(x in u for x in 출처["플랫폼_파트너"]):
        return "밖", "입점업체용 문서의 수(업주 부담)", False
    if any(x in u for x in 출처["공시"]):
        상 = _hit(t, V["상호"])
        꼬리 = f"공시({'·'.join(상) or '기업'})"

        # ── ① 절-존중 구멍 (판 ㊷ R2) ────────────────────────────────
        # 공시 사업보고서에는 **그 회사의 실적만이 아니라 시장의 유통 구조·원가 구조**가
        # 같이 실린다. 규칙의 `_왜` 는 「당기 매출액은 그 회사의 수다」 한 예만 대고 있었고,
        # 「매출처별 판매비중」 표까지 전부 경쟁 절로 보내 **4절 채널이 게재 0건**이었다.
        # ⚠ 꼬리표(`COMPETITOR_FIRM`)는 **떼지 않는다** — 떼면 회사의 수가 「우리 시장의 수」로
        #    둔갑한다(판 ㊵ 의 시장크기 23줄 사고). 자리만 지킨다.
        구멍 = [("CHANNEL", "유통_채널"), ("UNIT_ECONOMICS", "원가_수익")]
        for 절코드, 어휘 in 구멍:
            if it.get("section") == 절코드:
                맞 = _hit(t, 역할[어휘])
                if 맞:
                    return "COMPETITOR_FIRM", f"{꼬리} — {절코드} 제자리({'·'.join(맞)})", True
        # 품목별 판매단가는 **우리 가격을 잴 유일한 잣대**다(6,513원). 다만 우리 세그먼트의
        # 품목일 때만 — 같은 표의 참기름·쨈 단가는 세그먼트 어휘가 없어 이 문으로 못 나간다.
        if it.get("section") == "PRICE" and "단가" in t and _hit(t, V["세그먼트"]):
            세 = _hit(t, V["세그먼트"])
            return "COMPETITOR_FIRM", f"{꼬리} — PRICE 제자리(단가·{'·'.join(세)})", True

        # ── ② 발행사 판정 (판 ㊷ R3) ─────────────────────────────────
        # 구멍에 안 걸린 것은 「그 회사의 수」다. 그럼 **그 회사가 이 시장의 참여자인가**만
        # 남는다. 실측: 게임회사 증권신고서의 넥슨·엔씨·넷마블·크래프톤·컴투스 매출 5건과
        # 화학수지 PA6/PP 5건이 「경쟁 지형」에 실려 있었다 — 냉동 간편식 사업가가 보는 곳에.
        # ⚠ 항목 층에서는 못 가른다. **이름은 문서에 있지 항목에 없다**(실측: 상호 히트 0이
        #    77건 중 72건). 그래서 발행사는 문서 본문에서 찾아 넘겨받는다.
        if 발행사 is False:
            return "밖", f"경쟁사 아닌 회사의 실적{'(' + '·'.join(상) + ')' if 상 else ''}", False
        return "COMPETITOR_FIRM", f"공시 문서의 수({'·'.join(상) or '기업'})", False
        # ⚠ 공시 항목은 **여기서 반드시 끝난다** — 아래 `OURS_*` 로 못 내려간다(R4).
        #   내려가게 두면 실측대로 샌다: 「2025년 당기 매출액(상미식품) 1,250억」이
        #   「우리 세그먼트(식품)」로 시장 크기 절에 실린다.

    타지역 = _hit(t, R["타지역_표지"])
    if 타지역:
        # 참말이지만 우리 시장이 아니다. **사유를 남긴다** — 참고 칸이 생기면 되살릴 수 있게.
        return "밖", f"우리 지역이 아니다({'·'.join(타지역)})", False

    내부 = _hit(t, 역할["산업_내부_경제"])
    if 내부:
        # **SUBSTITUTE 의 소속 시험.** 대체재 산업이 자기들끼리 주고받는 돈은
        # 우리 사업의 어떤 결정도 바꾸지 않는다 — 컨셉 낱말이 겹쳐도 버린다.
        return "밖", f"대체재 산업의 내부 경제({'·'.join(내부)})", False

    상호 = _hit(t, V["상호"])
    실적 = _hit(t, 역할["기업_실적"])
    if 상호 and 실적:
        return "COMPETITOR_FIRM", f"{'·'.join(상호)}의 {'·'.join(실적)}", False

    세그 = _hit(t, V["세그먼트"])
    대상 = _hit(t, V["대상"])
    상위 = _hit(t, 역할["상위_범주"])
    지불 = bool(_hit(t, 역할["소비자_지불"])) or unit in 역할["통화_단위"]
    대체 = _hit(t, V["대체"])

    if not (세그 or 대상 or 상위 or 대체):
        why = "회사·채널 이름만 겹친다" if 상호 else "컨셉이 말하지 않은 대상"
        return "밖", why, False
    # **기업 실적 어휘가 주어에 있으면 「우리 시장」이 될 수 없다** (판 ㊷ 보완).
    # 「영업이익·매출·점유율」은 정의상 **어떤 회사의** 수이지 시장의 크기가 아니다.
    # 실측: 공시 보장이 `kind.krx`·`dart.fss` 두 URL 에만 걸려 있어 **경쟁사 자사 IR
    # 페이지가 통째로 우회**했다 — 「영업이익 620억원 | 우리 시장 | pulmuone.co.kr」·
    # 「당기 매출비중 6.1% | 우리 시장 | otoki.com」. 감시선(공시 출처의 OURS_* 0건)은
    # 통과했지만 보장하려던 명제가 이 길로 뚫렸다.
    # ⚠⚠ **산업 전체의 실적은 «한 회사의 실적»이 아니다** (판 ㊹ 4단계).
    #   실측: 목표 보고서 6절 머릿값 **「식품 제조업 영업이익률 4% 미만」**이
    #   「회사의 실적(발행사)」으로 판정돼 「어느 회사 한 곳」 딱지를 달았다 —
    #   **주어에 상위 범주 표지(「식품 제조업」)가 박혀 있는데도** 「영업이익률」이
    #   기업 실적 어휘라 그 문이 먼저 열렸다. 사업가는 그 수의 정체를 반대로 읽는다.
    #   → **주어에 상위 범주 표지가 있으면 업계 전체다.** 그 표지가 있다는 것 자체가
    #     「한 회사가 아니다」라는 신호이고, 이 순서 하나가 6절의 머릿값을 정한다.
    #   ⚠ 공백을 접고 본다 — 원문 표기가 「식품 제조업」과 「식품제조업」으로 갈리고,
    #     그 한 칸 때문에 상위 범주 표지가 안 걸려 이 문이 열렸다(실측).
    주어 = str(it.get("subject") or "")
    if _hit(주어, 역할["기업_실적"]) and not _hit(주어.replace(" ", ""), 역할["상위_범주"]):
        상 = _hit(t, V["상호"])
        return "COMPETITOR_FIRM", f"회사의 실적({'·'.join(상) or '발행사'})", False
    # **상위 범주 표지가 있으면 세그먼트 낱말이 겹쳐도 상한이다** (판 ㊷ 보완).
    # 실측: 「국내 식품제조업 시장 규모 **159조**」가 「우리 시장」으로 실렸다 — `식품제조업`
    # 이 상위 범주 표지인데 세그먼트의 `식품`·`제조업` 이 먼저 걸려서다. 상위 범주 목록은
    # **넓은 수를 잡으려고 있는 것**인데 일반 토큰에 밀리면 존재 이유가 없어진다.
    # (규칙 파일이 스스로 적은 그 병: 「음·식료품 38조를 냉동 간편식 시장이라 불렀다」)
    if 상위:
        # 버리지 않는다 — **「상한으로만 읽을 것」** 을 강제한다.
        return "OURS_UMBRELLA", f"상위 범주({'·'.join(상위)}) — 상한으로만", False
    if 세그:
        return "OURS_SEGMENT", f"우리 세그먼트({'·'.join(세그)})", False
    if 대체 and 지불:
        return "SUBSTITUTE", f"대체 수단에 내는 값({'·'.join(대체)})", False
    if 대상:
        # **우리 고객이라는 것만으로는 부족하다.** 실측: 「1인」 하나가 걸려 1인가구
        # 사회통계(삶의 만족 77.0% · 결혼 강요 16.8% · 성희롱 1.5% · 주말 여가 75.7%)가
        # 수요 절을 덮었다. 주제 어휘 없이 통과하는 것은 **모집단 규모**뿐이다.
        if unit in R["모집단_단위"]:
            return "OURS_SEGMENT", f"우리 고객의 모집단({'·'.join(대상)})", False
        return "밖", f"우리 고객이지만 우리 주제가 아니다({'·'.join(대상)})", False
    return "밖", f"대상은 맞으나 지불성이 없다({'·'.join(대체)})", False


_PCT = re.compile(r"([0-9]+(?:\.[0-9]+)?)\s*%")


def _퍼센트_파생(d: dict) -> int:
    """표 한 행에 값이 **둘**일 때 둘째 값(비중)을 항목으로 세운다. **LLM 0회** (판 ㊷ R6).

        | 대형 마트 | 1,140,941 | 31.05% |
          number_raw = 1,140,941 (백만원)   ← 뽑힌 것
          31.05%                            ← **인용 안에만 있었다**

    추출기는 한 행에서 수를 하나만 세운다. 그래서 채널 절에 절대액만 실리고, 사업가는
    「대형마트 1,140,941 백만원」을 봐도 **채널끼리 비교를 못 한다**. 모범답안이 요구하는
    것도 비중 쪽이다.

    ⚠ **새 사실을 만드는 것이 아니다.** 인용은 원문 그대로라 인용 대조 다섯 겹을 그대로
    통과하고, 값도 그 인용 안에 있다. 재질문(유료)이 아니라 이미 받아 둔 글자를 읽는 것이다.
    """
    n = 0
    for r in d["문서별"]:
        새 = []
        for it in r["items"]:
            q = str(it.get("quote") or "")
            num = str(it.get("number_raw") or "").strip()
            if "|" not in q or not num:
                continue
            pcts = _PCT.findall(q)
            if len(pcts) != 1:
                continue                      # 둘 이상이면 어느 것이 이 행의 비중인지 모른다
            p = pcts[0]
            if p == num.replace(",", "") or str(it.get("unit_raw") or "") == "%":
                continue                      # 이미 비중을 뽑은 행이다
            if num.replace(",", "") not in q.replace(",", ""):
                continue                      # 이 행의 절대액이 아니다
            새.append({**it,
                       "number_raw": p, "unit_raw": "%",
                       "subject": f"{it.get('subject')} — 비중",
                       "파생": "표 같은 행의 둘째 값", "파생_원본": num})
            n += 1
        r["items"].extend(새)
    return n


def build(d: dict, concept: dict, *, refetch: bool = False, cache_dir: str = "") -> dict:
    """**게재 판정을 여기서 한다.** `d`(sections.json 의 내용)를 **제자리에서 고쳐** 돌려준다.

    판 ㊸ 1단계에서 `main()` 밖으로 꺼냈다 — 제품 경로(`pipeline`)가 부를 자리가 필요한데
    argparse 를 통과시킬 수는 없기 때문이다. **`main()` 은 이 함수를 부른다** — 두 구현이
    되면 CLI 로 잰 성적과 화면에 나가는 것이 갈린다.

    사람이 읽는 진단은 `print` 로 그대로 둔다. 서버에서는 로그로 흐르고, 지금까지의 측정
    기록이 전부 이 출력으로 만들어졌다 — **잣대를 조용하게 만들 이유가 없다.**
    """
    R = _rules()
    V = _vocab(concept, R)

    print("컨셉에서 뽑은 어휘 —")
    for k in ("세그먼트", "대상", "상호", "대체"):
        print(f"  {k:<5} {len(V[k]):>3}개  {' · '.join(sorted(V[k])[:22])}")

    # ── 인용 재채점 (LLM 0회) ─────────────────────────────────
    docs = RS._corpus(d["source_run"])
    if refetch:
        cache = os.path.join(cache_dir, "refetched.json")
        if os.path.exists(cache):
            got = json.load(io.open(cache, encoding="utf-8"))
            docs = [{**x, "text": got.get(x["trace_id"], x["text"])} for x in docs]
            print(f"다시 뽑은 본문 {len(got)}건을 캐시에서 읽었다")
        else:
            docs = RS._refetch_pdfs(docs)
            io.open(cache, "w", encoding="utf-8").write(json.dumps(
                {x["trace_id"]: x["text"] for x in docs
                 if "다시 뽑음" in str(x.get("재추출"))}, ensure_ascii=False))
    본문 = {}
    for doc in docs:
        for tid in [doc["trace_id"]] + doc["별칭"]:
            본문[tid] = doc["text"][:d["cap"]]

    옛채택 = sum(1 for r in d["문서별"] for it in r["items"] if it["채택"])
    되살림 = 0
    for r in d["문서별"]:
        hay = RS._norm(본문.get(r["trace_id"], ""))
        for it in r["items"]:
            q = str(it.get("quote") or "")
            v = bool(q) and RS._norm(q) in hay
            if v and not it["quote_verified"]:
                되살림 += 1
            it["quote_verified"] = v
            it["채택"] = v and it["section_valid"]
            it["탈락_사유"] = ("" if it["채택"] else
                            ("인용이 본문에 없다" if not v else "절 코드가 아니다"))

    # ── 표 한 행의 둘째 값 (판 ㊷ R6) ──────────────────────────
    파생 = _퍼센트_파생(d)
    if 파생:
        print(f"\n표 둘째 값(비중) 파생 {파생}건 — **LLM 0회.** 절대액만 실리면 채널 비교가 안 된다")

    items = [it for r in d["문서별"] for it in r["items"]]
    ok = [it for it in items if it["채택"]]
    print(f"\n인용 재채점 — 채택 {옛채택} → {len(ok)} "
          f"(문장부호 관용으로 {되살림}건 되살림 · 표 둘째 값 파생 {파생}건 포함)")

    # ── 발행사 판정 (판 ㊷ R3) ─────────────────────────────────
    # **이름은 문서에 있지 항목에 없다.** 그래서 문서 본문에서 한 번 찾아 항목에 넘긴다.
    발행사 = {}
    for r in d["문서별"]:
        u = (r.get("url") or "").lower()
        if not any(x in u for x in R["출처_유형"]["공시"]):
            continue
        t = 본문.get(r["trace_id"], "")
        맞 = sorted({w for w in V["실명"] if w in t})
        발행사[r["trace_id"]] = (bool(맞), 맞, len(t))
    if 발행사:
        print("\n공시 문서의 발행사 판정 — 컨셉 경쟁 씨앗 실명이 본문에 있나")
        for tid, (ok_, 맞, n) in sorted(발행사.items(), key=lambda x: -len(x[1][1])):
            print(f"  {tid:<14}{'참여자' if ok_ else '**아님**':<10}{n:>7}자  {'·'.join(맞) or '—'}")

    # ── 게재 판정 ─────────────────────────────────────────────
    from collections import Counter
    갈래 = Counter()
    절 = {}
    url, tid_of = {}, {}
    for r in d["문서별"]:
        for it in r["items"]:
            url[id(it)] = r.get("url") or ""
            tid_of[id(it)] = r["trace_id"]
    # ── 절 재배정 — **분류보다 «먼저»** 한다 ──────────────────
    # 절이 바뀌면 갈래 판정도 달라진다(규제 절만 요건 예외를 받는다). 뒤에 하면
    # 「PRICE 로 판정받고 UNIT_ECONOMICS 에 앉는」 어긋남이 생긴다.
    옮김 = Counter()
    for it in ok:
        새 = 재배정(it, R)
        if 새:
            옮김[f"{it.get('section')} → {새}"] += 1
            it["절_원래"], it["section"] = it.get("section"), 새
    if 옮김:
        print("\n절 재배정 — **추출이 잘못 고른 자리를 표지로 바로잡았다**")
        for k, n in 옮김.most_common():
            print(f"  {n:>4}  {k}")

    for it in ok:
        발 = 발행사.get(tid_of.get(id(it)))
        c, why, 제자리 = 분류(it, V, R, url.get(id(it), ""),
                           발 if 발 is None else 발[0])
        it["게재"], it["게재_사유"], it["게재_제자리"] = c, why, 제자리
        if 발:
            it["게재_발행사"] = "·".join(발[1])     # 꼬리표에 **회사 이름**을 남긴다

    # ── 표 무결성 (판 ㊷ 보완) ────────────────────────────────
    # **한 표의 행이 두 절로 찢어지면 표가 거짓말을 한다.** 실측: 오뚜기 매출처별 판매비중
    # 5행 중 대형마트·대리점·편의점만 채널 절에 실리고 **특약점 29.65% · 기타 23.10% 는
    # 경쟁 절에 남아**, 채널 절 합이 47% 가 됐다. 사업가는 「대형마트 압도적 1위」로 읽지만
    # 실제 표에서는 특약점이 대형마트와 대등하다(29.65 vs 31.05).
    # 원인은 「특약점」이 유통 채널 어휘에 없어서인데, **낱말을 더 넣어 푸는 것은 답안지를
    # 보는 길이다.** 대신 **표를 안 찢는다** — 표는 원문이 정한 단위이고, 이 규칙은
    # 답안지 없이 성립한다.
    # ⚠ **모든 표가 아니라 「부분의 합이 뜻을 갖는 표」에만 적용한다.** 품목별 판매단가 표는
    # 찢어져도 거짓말이 아니다(참기름과 냉동식품 단가의 합은 아무 뜻이 없다). 거짓말이 되는
    # 것은 **구성비 표**다 — 합이 100인데 절반만 보이면 1위가 뒤바뀐다.
    # 그리고 **연도로도 가른다** — 같은 표가 2개년이면 합이 200이 되고, 9절이 2025년 5.99%와
    # 2024년 32.37%를 한 문장에서 견주는 사고가 실제로 났다.
    붙임, 구성비표 = 0, 0
    표 = {}
    for it in ok:
        tc = str(it.get("table_context") or "")
        if tc:
            표.setdefault((tid_of.get(id(it)), tc, str(it.get("year") or "")), []).append(it)
    for (_, tc, yr), 행 in 표.items():
        pct = [_수(x) for x in 행 if str(x.get("unit_raw") or "").strip() == "%"]
        pct = [v for v in pct if v is not None]
        if not (len(pct) >= 3 and 90 <= sum(pct) <= 110):
            continue                       # 구성비 표가 아니다 — 찢어져도 거짓말이 아니다
        구성비표 += 1
        자리 = {x["section"] for x in 행 if x.get("게재_제자리")}
        if len(자리) != 1:
            continue                       # 아무 행도 제자리를 못 얻었거나, 절이 갈렸다
        절코드 = 자리.pop()
        for x in 행:
            if x.get("게재_제자리") or x["게재"] in ("OFF_TOPIC", "밖"):
                continue
            x["section"], x["게재_제자리"] = 절코드, True
            x["게재_사유"] += f" — 같은 구성비 표(「{tc}」 {yr})의 나머지 행"
            붙임 += 1
    if 구성비표:
        print(f"\n표 무결성 — 구성비 표 {구성비표}개에서 찢어진 행 {붙임}건을 같은 절로 붙였다\n"
              f"  (**합이 100인 표가 절반만 보이면 1위가 뒤바뀐다.** 실측: 채널 절 합이 47%였고 "
              f"숨은 특약점 29.65%가 대형마트 31.05%와 대등했다)")

    # ── 같은 값 중복 접기 (판 ㊵ 이월 · 판 ㊷ 보완) ────────────
    # 「804만 5천 가구」가 한 절에 **네 번**(표기만 다르게) 실려 있었다. 사업가에게 근거가
    # 넷인 것처럼 보이지만 **하나다.** 9절 합성이 이 표를 근거 풀로 쓰는 지금이 접을 자리다.
    # ⚠ 절 안에서만 접는다 — 다른 절에 같은 값이 있는 것은 중복이 아니라 **다른 뜻**이다.
    접힘, 봄 = 0, set()
    for it in ok:
        if it["게재"] in ("OFF_TOPIC", "밖"):
            continue
        sec = (it["section"] if it.get("게재_제자리")
               else "COMPETITOR" if it["게재"] == "COMPETITOR_FIRM" else it["section"])
        k = (sec, _수(it), str(it.get("unit_raw") or "").strip(), str(it.get("year") or ""))
        if k in 봄 and k[1] is not None:
            # **버리지 않고 접는다** (판 ㊹ 3단계). 같은 값이 두 번 나온 것은
            # 「사실이 아니다」가 아니라 **교차 근거**다 — 서랍에 넣고 사유를 남긴다.
            it["게재"], it["게재_사유"] = "밖", "같은 값이 이 절에 이미 실렸다"
            접힘 += 1
            continue
        봄.add(k)
    if 접힘:
        print(f"\n같은 값 중복 {접힘}건을 접었다 "
              f"(**근거가 여럿인 것처럼 보이지만 하나다** — 판 ㊵ 「804만 5천 ×4」)")

    for it in ok:
        c = it["게재"]
        갈래[c] += 1
        sec = (it["section"] if it.get("게재_제자리")
               else "COMPETITOR" if c == "COMPETITOR_FIRM" else it["section"])
        절.setdefault(sec, Counter())[c] += 1

    print(f"\n게재 판정 ({len(ok)}건)")
    for c, n in 갈래.most_common():
        print(f"  {c:<16}{n:>5}  ({100 * n / len(ok):.1f}%)")
    실림 = len(ok) - 갈래["OFF_TOPIC"] - 갈래["밖"]
    print(f"  ── 절 머리에 서는 것 {실림}건 ({100 * 실림 / len(ok):.1f}%)")
    print(f"  ── 서랍에 싣는 것(«밖») {갈래['밖']}건 — **버린 것이 아니다**")
    print(f"  ── 버리는 것(사실이 아니다) {갈래['OFF_TOPIC']}건")

    print("\n절별 (재배정 반영)")
    order = ["OURS_SEGMENT", "OURS_UMBRELLA", "SUBSTITUTE", "COMPETITOR_FIRM", "밖", "OFF_TOPIC"]
    print(f"  {'절':<16}" + "".join(f"{c[:9]:>11}" for c in order))
    for sec in sorted(절, key=lambda s: -sum(v for k, v in 절[s].items() if k != "OFF_TOPIC")):
        print(f"  {sec:<16}" + "".join(f"{절[sec][c]:>11}" for c in order))

    바깥 = Counter(it["게재_사유"].split("(")[0] for it in ok if it["게재"] == "밖")
    print("\n서랍에 넣는 사유 — **싣는다. 접을 뿐이다**")
    for w, n in 바깥.most_common():
        print(f"  {n:>4}  {w}")

    떨어진 = Counter(it["게재_사유"].split("(")[0] for it in ok if it["게재"] == "OFF_TOPIC")
    print("\n버리는 사유 — **사실이 아니다**")
    for w, n in 떨어진.most_common():
        print(f"  {n:>4}  {w}")

    d["게재_어휘"] = {k: sorted(v) for k, v in V.items()}
    d["게재_요약"] = dict(갈래)
    return d


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("sections", help="sections.json 경로")
    ap.add_argument("--concept", required=True)
    ap.add_argument("--out", default="")
    ap.add_argument("--pdf-refetch", dest="refetch", action="store_true",
                    help="PDF 를 다시 받아 **지금의** pdf_text 로 본문을 다시 뽑아 대조한다. "
                         "LLM 0회 — 내려받기만 한다. 원장 본문은 다단 정정 **전**의 추출기가 "
                         "만든 것이라, 문장 한가운데에 옆 단 제목이 끼어 있어 참인 인용이 "
                         "구조적으로 탈락한다(실측: 「간편식 국내 판매액 6조 8천억」 3건 전부)")
    a = ap.parse_args()

    d = build(json.load(io.open(a.sections, encoding="utf-8")),
              json.load(io.open(a.concept, encoding="utf-8")),
              refetch=a.refetch, cache_dir=os.path.dirname(a.sections))

    out = a.out or os.path.join(os.path.dirname(a.sections), "publish.json")
    io.open(out, "w", encoding="utf-8").write(
        json.dumps(d, ensure_ascii=False, indent=1))
    print(f"\n기록: {out}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
