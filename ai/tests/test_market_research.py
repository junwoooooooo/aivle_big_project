"""MARKET_RESEARCH 계약 테스트 — **HTTP 경계까지** 태운다.

`test_pipeline_envelope.py` 는 오케스트레이터를 직접 부른다. 여기서는 그 위의 두 겹을
같이 본다: `validate_text_contents`·canonical hash·실패 어휘 매핑.
과거에 버그가 **정확히 그 이음새에서만** 잡혔다(§4 「실스택 스모크를 빼지 말 것」).

⚠ **판 ㉝ 에서 통째로 다시 썼다.** 옛 판은 두 겹으로 낡아 **영원히 skip 되고 있었다**:
   ① 마운트 경로 `/app/research2` 를 봤는데 엔진은 이미 `ai/app/research/research2` 로
      이식됐다 → 조건이 절대 참이 안 됐다
   ② 결과 모양이 옛 러너의 것(`sourceRun`·`fromStage`·`metrics`·`ledger`)이었다.
      지금 계약은 봉투(`runId`·`mode`·`stages`·`evidence` …)다
   ③ 실패 사유 `SOURCE_RUN_INVALID`·`SOURCE_RUN_NOT_FOUND` 는 **백엔드 화이트리스트에
      없어서** P1 에서 `FIELD_CONSTRAINT_VIOLATION` 으로 접혔다
   **조용히 안 도는 검사는 없는 검사보다 나쁘다** — 있는 줄 알기 때문이다.
"""

import hashlib
import json
import os
import shutil
import sys
import unicodedata
import uuid
from datetime import datetime, timedelta, timezone

import pytest
from fastapi.testclient import TestClient

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from app.research.runner import RESEARCH_HOME  # noqa: E402

#: 씨앗 원장. `runs/` 는 저장소에 없으므로(`.gitignore`) 없으면 통째로 skip 된다.
#:
#: ⚠ 이전 값 `beauty-13b` 는 **이름과 달리** `CPT-CAFE-INV`(카페 재고 SaaS)로 기록돼 있다 —
#:   관측은 미용실인데 되짚기가 카페 컨셉을 집었다. 표(`pipeline.CONCEPTS`)에 든 원장으로 바꾼다.
SEED_RUN = "beauty-13"

pytestmark = pytest.mark.skipif(
    not os.path.isdir(os.path.join(RESEARCH_HOME, "runs", SEED_RUN)),
    reason=f"씨앗 원장 없음: {RESEARCH_HOME}/runs/{SEED_RUN}",
)

TOKEN = "market-research-test-token"
TEXT = "카페 구독 서비스 시장조사 실험 입력"


@pytest.fixture()
def client(monkeypatch):
    monkeypatch.setenv("AI_INTERNAL_SERVICE_TOKEN", TOKEN)
    from main import app
    return TestClient(app)


def _canonical(value):
    if isinstance(value, str):
        return unicodedata.normalize("NFC", value)
    if isinstance(value, list):
        return [_canonical(item) for item in value]
    if isinstance(value, dict):
        return {unicodedata.normalize("NFC", k): _canonical(v) for k, v in value.items()}
    return value


def _text_contents():
    digest = "sha256:" + hashlib.sha256(TEXT.encode()).hexdigest()
    return [{"contentKey": "concept", "contentType": "TEXT", "language": "ko-KR",
             "totalCharacters": len(TEXT), "contentHash": digest,
             "chunks": [{"index": 0, "text": TEXT, "characterCount": len(TEXT),
                         "chunkHash": digest}]}]


def _request(task_input, attempt, task_type="MARKET_RESEARCH"):
    body = {"contractVersion": "1.0", "taskType": task_type, "taskSchemaVersion": "1.0",
            "taskRunId": "run-market-research", "taskAttemptId": attempt,
            "correlationId": "corr-market-research",
            "deadlineAt": (datetime.now(timezone.utc) + timedelta(seconds=300))
            .isoformat(timespec="seconds").replace("+00:00", "Z"),
            "locale": "ko-KR", "input": task_input}
    subset = {key: body[key] for key in
              ("contractVersion", "taskType", "taskSchemaVersion", "locale", "input")}
    body["canonicalInputHash"] = "sha256:" + hashlib.sha256(
        json.dumps(_canonical(subset), ensure_ascii=False, sort_keys=True,
                   separators=(",", ":")).encode()).hexdigest()
    return body


def _post(client, body):
    return client.post("/internal/v1/ai/executions", json=body,
                       headers={"Authorization": f"Bearer {TOKEN}",
                                "X-Correlation-Id": body["correlationId"]})


def _reason(response):
    return response.json()["error"]["details"][0]["reason"]


@pytest.fixture()
def attempt_id():
    value = "test-" + uuid.uuid4().hex[:12]
    yield value
    shutil.rmtree(os.path.join(RESEARCH_HOME, "runs", value), ignore_errors=True)


def _rescore(attempt):
    """재채점 — **LLM 0회 · 네트워크 0회.** 테스트가 돈을 쓰지 않는다."""
    return {"textContents": _text_contents(), "mode": "RESCORE",
            "sourceRun": SEED_RUN, "conceptId": "beauty-noshow"}


def test_rescore_returns_the_full_envelope_over_http(client, attempt_id):
    response = _post(client, _request(_rescore(attempt_id), attempt_id))

    assert response.status_code == 200, response.text
    result = response.json()["result"]

    from app.research.serialize import ENVELOPE
    assert set(result) == set(ENVELOPE)
    assert result["mode"] == "FULL"
    assert result["runId"] == attempt_id
    assert result["conceptId"] == "beauty-noshow"
    # 7과목이 **전부** 있어야 한다. 빠진 과목은 「미확보」가 아니라 「안 쟀다」로 읽힌다.
    assert len(result["scorecard"]) == 7
    assert result["canvas"] is None and result["bm"] is None
    # ⑦행을 절대 빼지 않는다(§4).
    assert result["market"]["notFound"]
    assert sum(stage["llmCalls"] for stage in result["stages"]) == 0


def test_evidence_grades_survive_the_http_boundary(client, attempt_id):
    """값만 나가고 등급이 빠지면 **추정이 확정처럼 읽힌다.**"""
    response = _post(client, _request(_rescore(attempt_id), attempt_id))
    assert response.status_code == 200, response.text
    evidence = response.json()["result"]["evidence"]
    assert evidence
    for item in evidence:
        assert item["grade"] in ("확정", "실무 신뢰", "추정", "근거 없음")
        assert isinstance(item["caveats"], list)


def test_source_run_must_be_a_plain_directory_name(client):
    """`sourceRun="../../etc"` 이 그대로 경로가 되면 안 된다."""
    body = _request({"textContents": _text_contents(), "mode": "RESCORE",
                     "sourceRun": "../../etc", "conceptId": "x"}, "test-traversal")
    response = _post(client, body)
    assert response.status_code == 400
    # ⚠ 어휘는 백엔드 화이트리스트 안에서만 고른다(P1) — 상세는 사유가 아니라 메시지로 간다.
    assert _reason(response) == "FIELD_CONSTRAINT_VIOLATION"


def test_unknown_source_run_is_rejected_before_running(client):
    body = _request({"textContents": _text_contents(), "mode": "RESCORE",
                     "sourceRun": "no-such-run", "conceptId": "x"}, "test-missing")
    response = _post(client, body)
    assert response.status_code == 400
    assert _reason(response) == "FIELD_CONSTRAINT_VIOLATION"


def test_unknown_mode_is_rejected(client):
    body = _request({"textContents": _text_contents(), "mode": "MAGIC",
                     "sourceRun": SEED_RUN, "conceptId": "x"}, "test-mode")
    response = _post(client, body)
    assert response.status_code == 400
    assert _reason(response) == "FIELD_CONSTRAINT_VIOLATION"


def test_concept_id_is_required(client):
    """`conceptId` 는 **echo 값**이다. 없으면 결과가 어느 컨셉의 것인지 말할 수 없다."""
    body = _request({"textContents": _text_contents(), "mode": "RESCORE",
                     "sourceRun": SEED_RUN}, "test-no-concept")
    response = _post(client, body)
    assert response.status_code == 400
    assert _reason(response) == "FIELD_CONSTRAINT_VIOLATION"


def test_market_rescore_does_not_require_unrelated_document_text_contents(client):
    # Target Product boundary는 MARKET_RESEARCH 입력을 immutable snapshot/sourceRun 계약으로 받는다.
    # 문서 처리용 textContents를 강제하면 공식 CPV2 Market 입력이 실행될 수 없다.
    body = _request({"mode": "RESCORE", "sourceRun": SEED_RUN, "conceptId": "x"},
                    "test-no-contents")
    response = _post(client, body)
    assert response.status_code == 200


def test_market_execution_passes_request_identity_to_bm_diagnostics(client, monkeypatch):
    from app.research import product_pipeline as pipeline

    captured = {}

    async def fake_run(_input, _run_id, _timeout, event_sink=None,
                       diagnostic_context=None):
        captured.update(diagnostic_context or {})
        return {"mode": "BM"}

    monkeypatch.setattr(pipeline, "run_market_research", fake_run)
    body = _request({"mode": "BM"}, "attempt-bm-diagnostics")

    response = _post(client, body)

    assert response.status_code == 200, response.text
    assert captured == {
        "taskRunId": "run-market-research",
        "taskAttemptId": "attempt-bm-diagnostics",
        "correlationId": "corr-market-research",
        "canonicalInputHash": body["canonicalInputHash"],
    }


# ══════════════════════════════════════════════════════════════
# 이름표 표 — 되짚기가 조용히 다른 컨셉을 집던 자리를 대신한다
# ══════════════════════════════════════════════════════════════
def test_concept_table_entries_agree_with_their_ledgers():
    """**카페 사고를 막는 그물.**

    표의 각 항목에 대해 「컨셉 파일의 `concept_id`」 == 「원장이 기록한 `concept_id`」.
    `data/concept.json` 은 작업용 파일이라 판마다 갈아 끼워졌고, 그때 `concept_id` 를
    안 고친 원장이 `CPT-CAFE-INV` 로 남았다 — 되짚으면 관측은 미용실인데 잣대가 카페가 된다.
    """
    import io

    from app.research import pipeline

    for label, (concept_path, source_run) in pipeline.CONCEPTS.items():
        run_dir = os.path.join(RESEARCH_HOME, "runs", source_run)
        if not os.path.isdir(run_dir):
            pytest.skip(f"원장 없음: runs/{source_run}")
        with io.open(os.path.join(RESEARCH_HOME, concept_path), encoding="utf-8") as handle:
            declared = json.load(handle).get("concept_id")
        with io.open(os.path.join(run_dir, "result.json"), encoding="utf-8") as handle:
            recorded = ((json.load(handle).get("input") or {}).get("concept") or {}).get("concept_id")
        assert declared == recorded, (
            f"{label}: 컨셉 파일은 {declared!r} 인데 원장 {source_run} 은 {recorded!r} 로 기록됐다")


def test_label_alone_resolves_the_ledger(client, attempt_id):
    """`sourceRun` 없이 **이름표만** 보내도 돈다 — 백엔드가 원장을 알 필요가 없다."""
    body = _request({"textContents": _text_contents(), "mode": "RESCORE",
                     "conceptId": "beauty-noshow"}, attempt_id)
    response = _post(client, body)

    assert response.status_code == 200, response.text
    result = response.json()["result"]
    assert result["conceptId"] == "beauty-noshow"
    assert result["scorecard"], "성적표가 비었다 — 원장을 못 찾았다는 뜻이다"


def test_unknown_label_without_source_run_is_rejected(client):
    """조용한 기본값을 만들지 않는다 — 모르는 이름표는 **실패**한다."""
    body = _request({"textContents": _text_contents(), "mode": "RESCORE",
                     "conceptId": "no-such-label"}, "test-unknown-label")
    response = _post(client, body)
    assert response.status_code == 400
    assert _reason(response) == "FIELD_CONSTRAINT_VIOLATION"
