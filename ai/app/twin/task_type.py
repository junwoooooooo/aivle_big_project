"""판매 경계 — 코드로 못박은 게이트.

외적 타당성 시험(G3D)은 **종합 미달**로 끝났다. 유형별로만 성적이 갈렸고, 그 성적이
곧 무엇을 팔 수 있는지의 경계다. 이 파일이 그 경계다. 허용 유형이 아니면
**LLM을 한 번도 부르지 않고** 거절한다.

| 유형 | 근거 | 처리 |
|---|---|---|
| 명백한 우열형 | 관문 3 E 4/4 정식 통과 | 제공 |
| 가격형 | B 3/4 모듈 성적 | 제공 (지위·B1 오답 병기) |
| 윤리·가치형 | H1·H3·B1 전부 불일치 | **차단** |
| 미묘한 우열형·속성경합 | H2·H6 측정 한계 이하 | 차단 |
| 음성대조(동일 프로필) | 잴 것이 없다 | 차단 |

원본 분류기는 `combine_csv/_build/g3d/G3D_성능분석/perf_01_quant.py:31-42` 이고
쌍 id 접두어로 나눈다(자극이 모듈로 설계됐기 때문). 여기서는 같은 **의미 규칙**을
속성에서 기계적으로 도출한다.

⚠ **원본보다 엄격한 지점이 하나 있다.** 원본은 E 분기를 인증 분기보다 먼저 둬서
E1(인증만 다르고 가격 동일)을 우열형으로 분류했고, 실제로 통과했다. 여기서는 윤리 축이면
단독 차이여도 차단한다. 이유:
  · 그 허용을 받치는 근거가 **E1 한 쌍뿐**이다
  · 반면 틀린 3쌍(H1·H3·B1)은 **전부** 인증 쌍이었고, 원인 가설은
    「KMP에 환경·윤리 문항이 없다」 — 카드에 없는 것은 카드 조립으로 만들 수 없다
  · 이 게이트에서 관대함의 비용은 신뢰이고, 엄격함의 비용은 값싼 질문 하나다
"""

from dataclasses import dataclass, field

# 서비스 가능
DOMINANCE = "DOMINANCE"          # 명백한 우열형
PRICE = "PRICE"                  # 가격형
# 차단
ETHICAL_VALUE = "ETHICAL_VALUE"  # 윤리·가치형 — 영구 금지
UNMEASURABLE = "UNMEASURABLE"    # 다속성 경합·팽팽한 대비 — 측정 한계 이하
IDENTICAL = "IDENTICAL"          # 동일 프로필 — 음성대조지 상품 비교가 아니다

SERVICEABLE = frozenset({DOMINANCE, PRICE})

# 윤리·가치 어휘. 속성 이름·값 어느 쪽에 있어도 걸린다.
ETHICAL_TERMS = (
    "인증", "지속가능", "친환경", "환경", "ESG", "esg", "공정무역", "유기농", "무농약",
    "탄소", "비건", "동물복지", "재활용", "업사이클", "윤리", "사회공헌", "그린",
    "천연", "무해", "청정",
)


@dataclass(frozen=True)
class Verdict:
    task_type: str
    serviceable: bool
    reason: str
    differing: tuple[str, ...] = field(default=())
    price_differs: bool = False

    @property
    def blocked(self) -> bool:
        return not self.serviceable


def _ethical_hits(side: dict) -> list[str]:
    """속성 이름·값에서 윤리 어휘를 찾는다."""
    hits = []
    for name, value in (side.get("attrs") or {}).items():
        blob = f"{name} {value}"
        for term in ETHICAL_TERMS:
            if term in blob:
                hits.append(name)
                break
    return hits


def classify(pair: dict) -> Verdict:
    """자극 한 쌍을 유형으로 가른다. LLM을 부르지 않는다.

    `pair` 는 `{"pairId":…, "X":{"attrs":{…}, "priceKrw":int|None}, "Y":{…}}`.
    가격은 `attrs` 가 아니라 별도 필드다 — 그래야 "가격이 축인가"를 문자열 추측 없이 안다.
    """
    x, y = pair["X"], pair["Y"]
    x_attrs = x.get("attrs") or {}
    y_attrs = y.get("attrs") or {}

    keys = sorted(set(x_attrs) | set(y_attrs))
    differing = tuple(k for k in keys if x_attrs.get(k) != y_attrs.get(k))
    price_differs = x.get("priceKrw") != y.get("priceKrw")

    if not differing and not price_differs:
        return Verdict(IDENTICAL, False,
                       "두 안이 동일하다 — 잴 차이가 없다(음성대조 자극이다).")

    # 윤리·가치 축이 걸리면 다른 무엇보다 먼저 막는다. (원본과 갈리는 지점 — 모듈 주석 참조)
    ethical = sorted({k for k in differing
                      if k in set(_ethical_hits(x)) | set(_ethical_hits(y))})
    if ethical:
        return Verdict(
            ETHICAL_VALUE, False,
            f"윤리·가치 속성이 대비의 축이다({', '.join(ethical)}). "
            "이 유형은 외적 타당성 시험에서 전부 불일치했고, 원인이 카드에 없는 정보라 "
            "더 나은 프롬프트로도 고쳐지지 않는다. 예측을 제공하지 않는다.",
            differing, price_differs)

    if len(differing) == 0 and price_differs:
        # 가격만 다르다 = 단일 속성 지배. 원본에서 E3(3,000 vs 6,000원)이 우열형이다.
        return Verdict(DOMINANCE, True, "가격만 다른 단일 속성 대비다.",
                       differing, price_differs)

    if len(differing) == 1 and not price_differs:
        return Verdict(DOMINANCE, True,
                       f"가격이 같고 «{differing[0]}» 하나만 다른 단일 속성 대비다.",
                       differing, price_differs)

    if len(differing) == 1 and price_differs:
        return Verdict(PRICE, True,
                       f"«{differing[0]}» 프리미엄이 가격 핸디캡을 이기는지 묻는 지불의사다.",
                       differing, price_differs)

    return Verdict(
        UNMEASURABLE, False,
        f"비가격 속성이 {len(differing)}개 동시에 다르다({', '.join(differing)}). "
        "다속성 경합은 측정 한계 이하라 방향을 말할 수 없다. "
        "한 번에 한 속성만 바꿔서 다시 물어라.",
        differing, price_differs)


def classify_all(pairs: list[dict]) -> list[tuple[dict, Verdict]]:
    return [(p, classify(p)) for p in pairs]
