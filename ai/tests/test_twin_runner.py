"""러너 계약 — LLM 호출 0회.

요청 본문과 지문은 `combine_csv/_build/g3e/g3e_runner.py` 와 **같아야 한다**.
0단계(계기 동등성 재측정)가 그 본문으로 재고, 본문이 갈라지면 그 시험의 결론이
이 파이프라인에 전이되지 않는다.
"""

import hashlib
import json

from app.twin import runner as R
from app.twin.stimuli import SYSTEM


def test_request_body_has_exactly_the_contracted_fields():
    """구조화 출력을 쓰지 않는다는 결정이 여기서 지켜지는지 본다."""
    body = R.build_body("프롬프트", SYSTEM, "some-model", 1.0, 1024)

    assert set(body) == {"model", "messages", "temperature", "max_tokens"}
    assert "response_format" not in body, "구조화 출력이 들어갔다 — 계기가 바뀐다"
    assert "stream" not in body
    assert "tools" not in body


def test_request_body_puts_system_in_its_own_message():
    body = R.build_body("프롬프트", SYSTEM, "some-model", 1.0, 1024)
    assert body["messages"] == [
        {"role": "system", "content": SYSTEM},
        {"role": "user", "content": "프롬프트"},
    ]


def test_temperature_is_not_zero():
    """0이면 rep1 == rep2 라 적응식 k 가 죽고 생성 분산이 사라진다."""
    assert R.TEMPERATURE > 0


def test_fingerprint_is_deterministic_and_covers_the_instrument():
    endpoint = "https://example.test/v1/chat/completions"
    first, fields = R.fingerprint(SYSTEM, "m", 1.0, 1024, endpoint)
    again, _ = R.fingerprint(SYSTEM, "m", 1.0, 1024, endpoint)
    colder, _ = R.fingerprint(SYSTEM, "m", 0.0, 1024, endpoint)
    elsewhere, _ = R.fingerprint(SYSTEM, "m", 1.0, 1024, "https://other.test/v1/chat/completions")

    assert first == again
    assert first != colder, "temperature 가 지문에 반영되지 않는다"
    assert first != elsewhere, "endpoint 가 지문에 반영되지 않는다"
    assert set(fields) == set(R.FINGERPRINT_FIELDS)


def test_fingerprint_redacts_the_system_prompt():
    _, fields = R.fingerprint(SYSTEM, "m", 1.0, 1024, "https://example.test/v1/chat/completions")
    assert SYSTEM not in json.dumps(fields, ensure_ascii=False)
    assert fields["system_sha256"] == hashlib.sha256(SYSTEM.encode("utf-8")).hexdigest()


def test_parse_choice_accepts_only_an_exact_final_line():
    assert R.parse_choice("이유를 씁니다.\n\n선택: A") == "A"
    assert R.parse_choice("선택: B\n") == "B"
    assert R.parse_choice("선택: 없음") == "없음"


def test_parse_choice_rejects_anything_less_than_exact():
    """관대하게 읽으면 트윈이 규칙을 어긴 것을 파서가 덮는다."""
    assert R.parse_choice("선택: A 입니다") is None
    assert R.parse_choice("선택:A") is None
    assert R.parse_choice("선택: C") is None
    assert R.parse_choice("선택: A\n그런데 다시 생각하면") is None
    assert R.parse_choice("") is None
    assert R.parse_choice(None) is None


def test_configuration_rejects_non_openai_providers(monkeypatch):
    """Mock 이 없다 — 키가 없으면 가짜 결과 대신 실패한다."""
    monkeypatch.setenv("AI_PROVIDER", "anthropic")
    monkeypatch.setenv("AI_API_KEY", "k")
    monkeypatch.setenv("AI_MODEL", "m")
    try:
        R._configuration()
    except Exception as failure:                        # ProviderFailure
        assert failure.reason == "AI_CONFIGURATION_INVALID"
        return
    raise AssertionError("지원하지 않는 provider 를 통과시켰다")


def test_configuration_rejects_missing_key(monkeypatch):
    monkeypatch.setenv("AI_PROVIDER", "openai")
    monkeypatch.delenv("AI_API_KEY", raising=False)
    monkeypatch.setenv("AI_MODEL", "m")
    try:
        R._configuration()
    except Exception as failure:
        assert failure.reason == "AI_CONFIGURATION_INVALID"
        return
    raise AssertionError("키 없이 통과시켰다")
