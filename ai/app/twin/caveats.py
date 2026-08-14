"""경계 문구 — 값과 **같은 자리**에 실려 나간다.

이 저장소가 실제로 강제하는 방식은 페이지 배너가 아니라 `caveats` 데이터다
(`app/research/serialize.py` → 계약 검증 → `EvidenceCard.jsx` → 회귀 테스트).
같은 통로를 쓴다. 값만 떼어 쓰면 경계가 사라지므로, 쌍마다 붙이고 계약이 빈 배열을 거부한다.
"""

from app.twin.task_type import DOMINANCE, PRICE

# ── 0단계(계기 동등성 재측정) 판정이 가르는 단 하나의 상수 ────────────────
#
# G3D 성적(우열형 E 4/4 · 가격형 B 3/4)은 **Claude Code CLI 클린룸**으로 쟀다.
# 이 파이프라인은 OpenAI 호환 HTTP를 쓴다 — 다른 계기다. `combine_csv/_build/g3e/` 의
# 재측정이 통과하기 전까지 성적은 근거가 아니다.
#
# **기본값은 미달 쪽이다.** `g3e_09_verdict.py` 가 exit 0(동등성 확인)을 낸 뒤에만
# True 로 바꾼다. 런타임 환경변수로 두지 않았다 — 이건 운영 손잡이가 아니라
# 검증에 대한 주장이라, 바꾸려면 배포가 남아야 한다.
INSTRUMENT_EQUIVALENCE_CONFIRMED = False

ALWAYS = (
    "외적 타당성 시험 종합 미달 — 검증된 항목 목록 별첨",
    "실측 프로파일 기반 시뮬레이션 — 실존 인물 인터뷰가 아니다",
    "출처: 한국미디어패널조사(KISDI)",
    # 2026-08-10 문구 교체. 화면이 응답 구성 비율을 보이기로 하면서 옛 문장
    # (「크기·점유율·선택확률은 산출하지 않는다」)이 화면과 모순됐다. 경계를 지운 것이 아니라
    # **비율이 무엇인지 못박는 문장으로 바꿨다** — 막으려던 오독(시장 점유율로 읽는 것)은 그대로 막는다.
    "화면의 비율은 이 표본 응답자들의 구성이다 — 시장 점유율도 실제 구매확률도 아니다",
    "판정이 말하는 것은 방향과 신뢰구간까지다. 차이의 크기는 이 설계가 답하지 못한다",
    "선택지는 양방향으로 제시해 평균했다 — 위치편향은 제거가 아니라 분리된 것이다",
)

BY_TASK_TYPE = {
    DOMINANCE: ("명백한 우열형은 관문 3에서 4/4 통과한 유형이다. "
                "미묘한 대비는 이 성적이 받쳐주지 않는다.",
                # 이것만은 계기가 바뀌어도 유지되는 것이 실측됐다 — 세 계기(CLI·gpt-4o-mini·
                # gpt-5.6-terra)가 우열형 3쌍에서 같은 방향을 냈다. 가격형은 그렇지 않아 막혔다.
                "지불의사가 개입하지 않는 대비라, 실행 모델을 바꿔도 방향이 유지되는 것을 "
                "재측정으로 확인했다.",),
}
# PRICE 항목은 없앴다 — `task_type.SERVICEABLE` 에서 빠져 여기까지 도달하지 못한다.

INSTRUMENT_UNVERIFIED = (
    "검증 계기와 서비스 계기가 다르다 — 성적 미전이. "
    "위 유형별 성적은 다른 실행 계기에서 잰 것이며 이 파이프라인으로 재현 확인되지 않았다.",
)

INSTRUMENT_VERIFIED = (
    "계기 동등성 확인 — 서비스 계기가 검증 계기와 같은 Δ를 재는 것을 재측정으로 확인했다.",
)


def for_pair(task_type: str) -> list[str]:
    """쌍 하나에 붙일 경계 문구. 비어 있을 수 없다."""
    notes = list(ALWAYS)
    notes.extend(BY_TASK_TYPE.get(task_type, ()))
    notes.extend(INSTRUMENT_VERIFIED if INSTRUMENT_EQUIVALENCE_CONFIRMED
                 else INSTRUMENT_UNVERIFIED)
    return notes


def for_unmeasurable() -> list[str]:
    """측정 한계 이하로 방향을 못 낸 쌍에 붙인다.

    「차이 없음」이 아니다. 못 잰 것이다 — 이 구분을 흐리면 없는 결론이 생긴다.
    """
    return ["«못 잼»과 «차이 없음»은 다르다. 이 결과는 앞의 것이다",
            "표본을 키우면 잴 수도 있다 — 측정 한계는 표본 크기의 함수다"]
