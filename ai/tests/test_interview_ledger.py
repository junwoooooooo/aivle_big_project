"""응답 원장 — 남기되 새지 않는지. LLM 호출 0회.

원장은 부가 기록이다. 두 방향으로 틀릴 수 있고 둘 다 여기서 잡는다.
  · **덜 안전한 쪽** — 재배포 금지 마이크로데이터(pid·카드 원문)가 파일로 흘러나간다
  · **덜 튼튼한 쪽** — 원장을 못 쓴다고 조사가 죽는다
"""

import json

from app.interview import ledger
from app.interview.coding import Assignment, Codebook, CodebookAlternative, CodebookTheme, verify

ANSWERS = {"R1": {"firstImpression": "첫인상", "restatement": "이해", "like": "좋다",
                  "concern": "걸린다", "differentiation": "다르다", "relevance": "필요",
                  "usageScene": "저녁에", "barrier": "비싸다", "suggestion": "싸게"},
           "R2": {"firstImpression": "둘", "restatement": "둘", "like": "둘",
                  "concern": "둘", "differentiation": "둘", "relevance": "둘",
                  "usageScene": "둘", "barrier": "둘", "suggestion": "둘"}}

PROFILES = {"R1": {"age": 41, "gender": "여성", "household": "3인 가구", "region": "서울",
                   "income": "월소득 300~400만 원", "job": "사무직"},
            "R2": {"age": 68, "gender": "남성", "household": "2인 가구", "region": "부산",
                   "income": "개인 소득 없음", "job": "무직"}}


def _coded():
    book = Codebook(themes=[CodebookTheme(axis="LIKE", label="간편함")],
                    alternatives=[CodebookAlternative(label="참는다")], misreadPoints=[])
    rows = [Assignment(id=rid, comprehension="accurate", differentiationVerdict="different",
                       barrierResolved=False, likeLabels=["간편함"], concernLabels=[],
                       differentiationLabels=[], usageSceneLabels=[], barrierLabels=[],
                       suggestionLabels=[], alternativeLabel="참는다")
            for rid in ANSWERS]
    return verify(book, rows, ANSWERS)


RESULT = {
    "conceptBoard": {"rendered": "이름: 밴드\n가격: 39,000원"},
    "sampleSize": 20,
    "targeting": {"criteriaText": "조건 없음"},
    "themes": [{"axis": "LIKE", "label": "간편함", "mentionCount": 2}],
    "alternatives": [{"label": "참는다", "mentionCount": 2}],
    "comprehension": {"accurate": 2, "partial": 0, "misunderstood": 0, "unclassified": 0},
    "differentiation": {"different": 2, "similar": 0, "unclear": 0, "unclassified": 0},
    "telemetry": {"answered": 2, "model": "gpt-4o-mini",
                  "homogeneity": {"alternativeSum": 2, "saturatedThemes": []}},
}


def test_nothing_is_written_when_the_ledger_is_off(tmp_path, monkeypatch):
    """운영 기본은 꺼짐이다. 환경변수가 비면 파일이 생기지 않는다."""
    monkeypatch.delenv(ledger.ENV_DIR, raising=False)
    assert ledger.write(ANSWERS, PROFILES, _coded(), RESULT) is None
    assert list(tmp_path.iterdir()) == []


def test_a_write_failure_never_kills_the_survey(tmp_path, monkeypatch):
    """원장 때문에 조사가 죽으면 본말이 뒤집힌다."""
    monkeypatch.setenv(ledger.ENV_DIR, "  ")               # 공백뿐이면 꺼진 것으로 본다
    assert ledger.write(ANSWERS, PROFILES, _coded(), RESULT) is None

    blocked = tmp_path / "여기는 파일이다"                    # 디렉터리를 만들 수 없는 자리
    blocked.write_text("x", encoding="utf-8")
    monkeypatch.setenv(ledger.ENV_DIR, str(blocked))
    assert ledger.write(ANSWERS, PROFILES, _coded(), RESULT) is None

    monkeypatch.setenv(ledger.ENV_DIR, str(tmp_path / "ok"))
    assert ledger.write(ANSWERS, PROFILES, _coded(), {"conceptBoard": {}}) is None


def test_the_ledger_carries_no_pid_and_no_raw_card(tmp_path, monkeypatch):
    """뱅크는 재배포 금지 자산이다. 남기는 것은 R번호와 프로필 6필드뿐이다."""
    monkeypatch.setenv(ledger.ENV_DIR, str(tmp_path))
    path = ledger.write(ANSWERS, PROFILES, _coded(), RESULT)
    blob = open(path, encoding="utf-8").read()
    assert "pid" not in blob
    assert "임금 근로자" not in blob
    assert "card" not in blob.lower()


def test_answers_are_stored_whole_so_recoding_sees_the_same_input(tmp_path, monkeypatch):
    """300자 상한을 걸면 재코딩이 원본과 다른 입력을 본다."""
    monkeypatch.setenv(ledger.ENV_DIR, str(tmp_path))
    long_answer = {**ANSWERS, "R1": {**ANSWERS["R1"], "concern": "가" * 900}}
    path = ledger.write(long_answer, PROFILES, _coded(), RESULT)
    _meta, answers, _profiles, _coding = ledger.read(path)
    assert len(answers["R1"]["concern"]) == 900


def test_round_trip_gives_back_what_the_harness_needs(tmp_path, monkeypatch):
    monkeypatch.setenv(ledger.ENV_DIR, str(tmp_path))
    path = ledger.write(ANSWERS, PROFILES, _coded(), RESULT)
    meta, answers, profiles, coding = ledger.read(path)
    assert meta["board"] == RESULT["conceptBoard"]["rendered"]
    assert meta["answered"] == 2
    assert set(answers) == {"R1", "R2"}
    assert profiles["R1"]["region"] == "서울"
    assert coding["themes"] == RESULT["themes"]
    assert coding["homogeneity"]["alternativeSum"] == 2


def test_prompt_hashes_are_recorded_so_two_ledgers_can_be_compared(tmp_path, monkeypatch):
    """어느 프롬프트가 만든 응답인지 모르면 두 판을 나란히 놓을 수 없다."""
    monkeypatch.setenv(ledger.ENV_DIR, str(tmp_path))
    meta, _a, _p, _c = ledger.read(ledger.write(ANSWERS, PROFILES, _coded(), RESULT))
    for key in ("guideSha256", "codebookPromptSha256", "assignmentPromptSha256"):
        assert len(meta[key]) == 64


def test_every_line_is_valid_json(tmp_path, monkeypatch):
    monkeypatch.setenv(ledger.ENV_DIR, str(tmp_path))
    path = ledger.write(ANSWERS, PROFILES, _coded(), RESULT)
    rows = [json.loads(line) for line in open(path, encoding="utf-8") if line.strip()]
    assert [rows[0]["row"], rows[-1]["row"]] == ["meta", "coding"]
    assert len(rows) == 4                                  # meta + 응답 2 + coding
