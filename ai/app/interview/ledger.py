"""응답 원장 — 같은 응답에 **코딩만 바꿔** 다시 돌리기 위한 기록.

이 파일이 생긴 이유는 하나다. n=40 실행에서 모든 주제가 40/40 으로 나왔을 때, 그것이
코딩 탓인지 합성 응답자 탓인지 가르는 데 **유료 1판을 다시 써야 했다**. 응답이 남아
있었다면 0원이었다.

그래서 목적을 하나로 좁힌다: **응답을 그대로 두고 코딩만 다시 돌려 진단표를 비교한다.**
읽는 쪽은 `tools/recode_ledger.py`.

규율 셋.

1. **기본은 꺼져 있다.** `MARKET_INTERVIEW_LEDGER_DIR` 이 비면 아무것도 하지 않는다.
2. **원장 때문에 조사가 죽지 않는다.** 쓰기 실패는 삼키고 경고만 남긴다.
3. **`pid_hash` 와 카드 원문을 쓰지 않는다.** 뱅크는 재배포 금지 마이크로데이터다.
   남기는 것은 R 번호와 프로필 6필드, 그리고 응답자가 쓴 답뿐이다.

답은 **자르지 않는다.** 원장은 화면이 아니라 진단 도구라, 300자 상한을 걸면 재코딩이
원본과 다른 입력을 보게 된다.
"""

import hashlib
import json
import logging
import os
from datetime import datetime, timezone

from app.interview.questions import TEMPLATE

logger = logging.getLogger(__name__)

__all__ = ["write", "read"]

ENV_DIR = "MARKET_INTERVIEW_LEDGER_DIR"


def _directory() -> str | None:
    return (os.getenv(ENV_DIR, "") or "").strip() or None


def _digest(text: str) -> str:
    return hashlib.sha256(text.encode("utf-8")).hexdigest()


def write(answers: dict[str, dict], profiles: dict[str, dict], coded, result: dict) -> str | None:
    """원장 한 판을 쓰고 경로를 돌려준다. 꺼져 있거나 실패하면 `None`."""
    directory = _directory()
    if not directory:
        return None
    try:
        from app.interview.coding import ASSIGNMENT_PROMPT, CODEBOOK_PROMPT

        os.makedirs(directory, exist_ok=True)
        board = result["conceptBoard"]["rendered"]
        stamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
        path = os.path.join(
            directory, f"{stamp}_{_digest(board)[:8]}_n{result['sampleSize']}.jsonl")
        lines = [{
            "row": "meta",
            "board": board,
            "sampleSize": result["sampleSize"],
            "answered": result["telemetry"].get("answered"),
            "model": result["telemetry"].get("model"),
            "targeting": result["targeting"],
            "telemetry": result["telemetry"],
            # 어느 프롬프트가 만든 응답인지 — 이게 없으면 두 원장을 비교할 수 없다.
            "guideSha256": _digest(TEMPLATE),
            "codebookPromptSha256": _digest(CODEBOOK_PROMPT),
            "assignmentPromptSha256": _digest(ASSIGNMENT_PROMPT),
        }]
        lines.extend({"row": "response", "id": rid, "profile": profiles.get(rid) or {},
                      **answers[rid]} for rid in sorted(answers, key=lambda r: int(r[1:])))
        lines.append({
            "row": "coding",
            "assignments": coded.assignments,
            "themes": result["themes"],
            "alternatives": result["alternatives"],
            "comprehension": result["comprehension"],
            "differentiation": result["differentiation"],
            "homogeneity": result["telemetry"]["homogeneity"],
        })
        with open(path, "w", encoding="utf-8") as handle:
            for line in lines:
                handle.write(json.dumps(line, ensure_ascii=False) + "\n")
    except (OSError, KeyError, TypeError, ValueError) as failure:
        # 원장은 부가 기록이다. 여기서 조사를 죽이면 본말이 뒤집힌다.
        logger.warning("market interview ledger not written: %s", failure)
        return None
    logger.info("market interview ledger written path=%s", path)
    return path


def read(path: str) -> tuple[dict, dict[str, dict], dict[str, dict], dict]:
    """`(meta, answers, profiles, coding)`. 재코딩 하니스가 쓰는 유일한 입구다."""
    meta: dict = {}
    answers: dict[str, dict] = {}
    profiles: dict[str, dict] = {}
    coding: dict = {}
    with open(path, encoding="utf-8") as handle:
        for line in handle:
            if not line.strip():
                continue
            record = json.loads(line)
            kind = record.pop("row", None)
            if kind == "meta":
                meta = record
            elif kind == "coding":
                coding = record
            elif kind == "response":
                rid = record.pop("id")
                profiles[rid] = record.pop("profile", {})
                answers[rid] = record
    return meta, answers, profiles, coding
