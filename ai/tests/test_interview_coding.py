"""주제 코딩의 뒷단 — 숫자와 인용문을 코드가 만드는지. LLM 호출 0회.

이 파일이 지키는 계약 둘.

1. **LLM 은 이름표만 붙인다.** 언급 수는 배정표를 뒤집은 결과여야 하고, 인용문은 그 사람이
   실제로 쓴 문장이어야 한다.
2. **덤프가 구조적으로 불가능하다.** 2026-08-12 에 n=40 실행이 모든 주제를 40/40 으로
   냈고 대안 3개가 동시에 40/40 이었다. 그 회귀를 여기서 잡는다.
"""

from app.interview import (_alternatives, _buckets, _differentiation, _quote, _themes,
                           _transcripts)
from app.interview.coding import (Assignment, Codebook, CodebookAlternative, CodebookTheme,
                                  _assignment_message, _codebook_message, verify)


def answer(**overrides) -> dict:
    base = {"firstImpression": "첫인상", "restatement": "다시 말하기", "like": "좋은 점",
            "concern": "걸리는 점", "differentiation": "다른 점", "relevance": "필요성",
            "usageScene": "쓸 장면", "barrier": "장벽", "suggestion": "제안"}
    base.update(overrides)
    return base


ANSWERS = {f"R{i}": answer(like=f"{i}번이 말한 좋은 점") for i in range(1, 6)}


def codebook(themes=(), alternatives=(), misread=()) -> Codebook:
    return Codebook(themes=[CodebookTheme(axis=axis, label=label) for axis, label in themes],
                    alternatives=[CodebookAlternative(label=label) for label in alternatives],
                    misreadPoints=list(misread))


def assign(rid, comprehension="accurate", verdict="different", resolved=False,
           like=(), concern=(), differentiation=(), usage=(), barrier=(), suggestion=(),
           alternative="") -> Assignment:
    return Assignment(id=rid, comprehension=comprehension, differentiationVerdict=verdict,
                      barrierResolved=resolved, likeLabels=list(like),
                      concernLabels=list(concern), differentiationLabels=list(differentiation),
                      usageSceneLabels=list(usage), barrierLabels=list(barrier),
                      suggestionLabels=list(suggestion), alternativeLabel=alternative)


# ── 40/40 회귀 — 관측된 고장을 그대로 재현한다 ────────────────────────
def test_the_codebook_schema_has_no_place_to_dump_respondent_ids():
    """1패스가 덤프할 수 없는 이유는 프롬프트 문구가 아니라 **칸이 없기 때문**이다."""
    fields = set(Codebook.model_fields) | set(CodebookTheme.model_fields)
    assert "respondentIds" not in fields
    assert not any("respondent" in name.lower() for name in fields)


def test_one_person_can_only_carry_one_alternative():
    """관측된 고장: 대안 3개가 동시에 40/40 이었다 — 한 사람이 셋을 다 한다는 뜻이다.

    배정표의 `alternativeLabel` 이 단수라 합계가 응답자 수를 넘는 것이 원리상 불가능하다.
    """
    book = codebook(alternatives=["가끔 요리한다", "간편식·배달", "그냥 참는다"])
    rows = [assign("R1", alternative="가끔 요리한다"),
            assign("R2", alternative="간편식·배달"),
            assign("R3", alternative="그냥 참는다"),
            assign("R4", alternative="가끔 요리한다"),
            assign("R5", alternative="")]
    result = _alternatives(verify(book, rows, ANSWERS))
    assert sum(row["mentionCount"] for row in result) <= len(ANSWERS)
    assert [(r["label"], r["mentionCount"]) for r in result] == [
        ("가끔 요리한다", 2), ("간편식·배달", 1), ("그냥 참는다", 1)]


def test_a_respondent_who_was_not_assigned_does_not_enter_the_theme():
    """옛 구조는 「그 id 가 존재하나」만 봤다. 이제는 그 사람이 그 줄에서 골랐어야 든다."""
    book = codebook(themes=[("LIKE", "간편함")])
    rows = [assign("R1", like=["간편함"]), assign("R2"), assign("R3")]
    coded = verify(book, rows, ANSWERS)
    assert coded.themes == [{"axis": "LIKE", "label": "간편함", "respondentIds": ["R1"]}]


def test_labels_the_model_invented_are_dropped():
    book = codebook(themes=[("LIKE", "간편함")])
    rows = [assign("R1", like=["간편함", "코드북에 없는 이름표"])]
    coded = verify(book, rows, ANSWERS)
    assert [t["label"] for t in coded.themes] == ["간편함"]


def test_a_respondent_cannot_take_more_than_three_labels_in_one_axis():
    """여기서도 덤프를 막는다 — 한 사람이 축 전체를 먹으면 이름표가 의미를 잃는다."""
    book = codebook(themes=[("CONCERN", f"걱정{i}") for i in range(1, 6)])
    rows = [assign("R1", concern=[f"걱정{i}" for i in range(1, 6)])]
    coded = verify(book, rows, ANSWERS)
    assert len(coded.assignments["R1"]["concernLabels"]) == 3
    assert sum(len(t["respondentIds"]) for t in coded.themes) == 3


# ── 환각 방지 — 보낸 id 와 대조 ───────────────────────────────────────
def test_unknown_respondent_ids_are_dropped():
    book = codebook(themes=[("LIKE", "간편함")])
    rows = [assign("R99", like=["간편함"]), assign("R2", like=["간편함"])]
    coded = verify(book, rows, ANSWERS)
    assert coded.themes[0]["respondentIds"] == ["R2"]
    assert "R99" not in coded.assignments


def test_a_second_row_for_the_same_person_is_ignored():
    book = codebook(themes=[("LIKE", "간편함")])
    rows = [assign("R1", comprehension="accurate"),
            assign("R1", comprehension="misunderstood", like=["간편함"])]
    coded = verify(book, rows, ANSWERS)
    assert coded.comprehension["accurate"] == ["R1"]
    assert coded.themes == []


def test_ids_are_reordered_by_number_not_by_the_order_the_model_gave():
    """LLM 이 준 순서를 믿으면 인용문 선택이 실행마다 흔들린다."""
    book = codebook(themes=[("LIKE", "간편함")])
    rows = [assign(rid, like=["간편함"]) for rid in ("R4", "R1", "R2")]
    coded = verify(book, rows, ANSWERS)
    assert coded.themes[0]["respondentIds"] == ["R1", "R2", "R4"]


def test_a_theme_nobody_was_assigned_to_is_dropped_entirely():
    book = codebook(themes=[("LIKE", "아무도 안 든 주제")])
    assert verify(book, [assign("R1")], ANSWERS).themes == []


# ── 숫자는 코드가 센다 ────────────────────────────────────────────────
def test_mention_count_is_the_length_of_the_id_list():
    book = codebook(themes=[("LIKE", "간편함")])
    rows = [assign(rid, like=["간편함"]) for rid in ("R1", "R2")]
    result = _themes(verify(book, rows, ANSWERS), ANSWERS, set())
    assert result[0]["mentionCount"] == 2
    assert result[0]["respondentIds"] == ["R1", "R2"]


def test_themes_are_sorted_by_axis_then_count_then_label():
    book = codebook(themes=[("SUGGESTION", "값을 내려라"), ("LIKE", "적게 나온 것"),
                            ("LIKE", "많이 나온 것"), ("CONCERN", "비싸다")])
    rows = [assign("R1", like=["적게 나온 것", "많이 나온 것"], concern=["비싸다"],
                   suggestion=["값을 내려라"]),
            assign("R2", like=["많이 나온 것"], concern=["비싸다"]),
            assign("R3", like=["많이 나온 것"])]
    result = _themes(verify(book, rows, ANSWERS), ANSWERS, set())
    assert [(r["axis"], r["label"]) for r in result] == [
        ("LIKE", "많이 나온 것"), ("LIKE", "적게 나온 것"),
        ("CONCERN", "비싸다"), ("SUGGESTION", "값을 내려라")]


def test_alternatives_are_sorted_by_count_then_label():
    book = codebook(alternatives=["그냥 참는다", "직접 전화한다"])
    rows = [assign("R1", alternative="그냥 참는다"),
            *(assign(rid, alternative="직접 전화한다") for rid in ("R2", "R3", "R4"))]
    result = _alternatives(verify(book, rows, ANSWERS))
    assert [r["label"] for r in result] == ["직접 전화한다", "그냥 참는다"]
    assert [r["mentionCount"] for r in result] == [3, 1]


def test_an_alternative_the_model_invented_is_folded_into_no_answer():
    book = codebook(alternatives=["그냥 참는다"])
    coded = verify(book, [assign("R1", alternative="지어낸 대안")], ANSWERS)
    assert _alternatives(coded) == []
    assert coded.assignments["R1"]["alternativeLabel"] == ""


# ── 인용문은 실제로 쓴 문장이어야 한다 ────────────────────────────────
def test_quote_comes_from_the_first_respondent_who_actually_wrote_that_field():
    book = codebook(themes=[("LIKE", "간편함")])
    rows = [assign(rid, like=["간편함"]) for rid in ("R3", "R2")]
    result = _themes(verify(book, rows, ANSWERS), ANSWERS, set())
    assert result[0]["quote"] == "2번이 말한 좋은 점"      # id 오름차순이라 R2 가 먼저다


def test_quote_skips_respondents_who_left_the_field_blank():
    answers = {"R1": answer(concern="   "), "R2": answer(concern="배터리가 걱정된다")}
    assert _quote(["R1", "R2"], answers, "concern") == "배터리가 걱정된다"


def test_quote_is_none_when_nobody_wrote_anything():
    answers = {"R1": answer(concern=""), "R2": answer(concern="")}
    assert _quote(["R1", "R2"], answers, "concern") is None


def test_quote_uses_the_field_that_matches_the_axis():
    answers = {"R1": answer(like="좋다", barrier="비싸서 못 산다")}
    book = codebook(themes=[("BARRIER", "가격")])
    result = _themes(verify(book, [assign("R1", barrier=["가격"])], answers), answers, set())
    assert result[0]["quote"] == "비싸서 못 산다"


def test_new_axes_pull_their_quotes_from_the_new_answer_fields():
    answers = {"R1": answer(differentiation="딱히 다를 게 없어 보여요",
                            usageScene="평일 저녁에 쓸 것 같아요")}
    book = codebook(themes=[("DIFFERENTIATION", "차이 없음"), ("USAGE_SCENE", "평일 저녁")])
    result = _themes(verify(book, [assign("R1", differentiation=["차이 없음"],
                                          usage=["평일 저녁"])], answers), answers, set())
    quotes = {row["axis"]: row["quote"] for row in result}
    assert quotes["DIFFERENTIATION"] == "딱히 다를 게 없어 보여요"
    assert quotes["USAGE_SCENE"] == "평일 저녁에 쓸 것 같아요"


# ── 장벽 해소 발언 ───────────────────────────────────────────────────
def test_resolved_count_only_counts_people_who_said_it():
    """전원 true 면 모델이 추측한 것이다 — 세는 것은 「말한 사람」뿐이다."""
    book = codebook(themes=[("BARRIER", "가격")])
    rows = [assign("R1", barrier=["가격"], resolved=True),
            assign("R2", barrier=["가격"], resolved=False)]
    coded = verify(book, rows, ANSWERS)
    assert coded.barrierResolvedIds == ["R1"]
    assert _themes(coded, ANSWERS, set(coded.barrierResolvedIds))[0]["resolvedCount"] == 1


# ── 이해도·차별성 3분류 — 배정이 1인 1값이라 배타가 구조로 성립한다 ──
def test_each_respondent_lands_in_exactly_one_comprehension_bucket():
    rows = [assign("R1"), assign("R2"), assign("R3", comprehension="partial"),
            assign("R4", comprehension="misunderstood")]
    summary, buckets = _buckets(verify(codebook(), rows, ANSWERS), ANSWERS)
    assert (summary["accurate"], summary["partial"], summary["misunderstood"]) == (2, 1, 1)
    assert sorted(sum(buckets.values(), [])) == ["R1", "R2", "R3", "R4"]


def test_respondents_left_out_are_counted_as_unclassified_not_folded_into_partial():
    """조용히 «부분»에 몰아넣으면 없는 판정이 생긴다."""
    summary, _ = _buckets(verify(codebook(), [assign("R1")], ANSWERS), ANSWERS)
    assert summary["accurate"] == 1 and summary["unclassified"] == 4


def test_misread_points_are_trimmed_and_blank_ones_dropped():
    book = codebook(misread=["  위치 추적기로 오해  ", "   "])
    summary, _ = _buckets(verify(book, [assign("R1")], ANSWERS), ANSWERS)
    assert summary["misreadPoints"] == ["위치 추적기로 오해"]


def test_differentiation_is_exclusive_and_counts_the_leftovers():
    rows = [assign("R1", verdict="different"), assign("R2", verdict="similar"),
            assign("R3", verdict="unclear")]
    summary = _differentiation(verify(codebook(), rows, ANSWERS), ANSWERS)
    assert summary == {"different": 1, "similar": 1, "unclear": 1, "unclassified": 2}


# ── 전원 응답 ────────────────────────────────────────────────────────
def test_transcripts_carry_everyone_and_mark_the_target_split():
    profiles = {rid: {"age": 40, "gender": "여성"} for rid in ANSWERS}
    rows = _transcripts(ANSWERS, profiles, {"R1", "R2"})
    assert [row["id"] for row in rows] == ["R1", "R2", "R3", "R4", "R5"]
    assert [row["target"] for row in rows] == [True, True, False, False, False]
    assert rows[0]["usageScene"] == "쓸 장면"


# ── 코딩 프롬프트 ────────────────────────────────────────────────────
def test_codebook_prompt_carries_no_respondent_ids_at_all():
    """1패스에 id 를 보내면 「전부 넣어라」의 유혹이 생긴다. 아예 안 보낸다."""
    import json

    message = json.loads(_codebook_message("이름: 밴드", ANSWERS))
    keys = {key for response in message["responses"] for key in response}
    assert "id" not in keys
    assert message["productDescription"] == "이름: 밴드"


def test_assignment_prompt_keeps_id_order_and_omits_first_impression():
    import json

    book = codebook(themes=[("LIKE", "간편함")], alternatives=["참는다"])
    message = json.loads(
        _assignment_message("이름: 밴드", book, ["R1", "R2", "R3"], ANSWERS))
    assert [r["id"] for r in message["responses"]] == ["R1", "R2", "R3"]
    assert message["codebook"]["themes"] == [{"axis": "LIKE", "label": "간편함"}]
    assert message["codebook"]["alternatives"] == ["참는다"]
    keys = {key for response in message["responses"] for key in response}
    assert keys == {"id", "restatement", "like", "concern", "differentiation",
                    "relevance", "usageScene", "barrier", "suggestion"}


def test_coding_prompts_never_carry_the_respondent_pid():
    """카드 본문도 pid_hash 도 코딩 프롬프트에 실리지 않는다 — id 는 R번호뿐이다."""
    import json

    book = codebook()
    for message in (_codebook_message("이름: 밴드", ANSWERS),
                    _assignment_message("이름: 밴드", book, ["R1"], ANSWERS)):
        assert "pid" not in json.dumps(json.loads(message), ensure_ascii=False)
