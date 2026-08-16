# -*- coding: utf-8 -*-
"""PDF 본문 추출 — **잎 모듈**. 엔진도 적재기도 여기만 부른다.

왜 별도 파일인가: `adapters/web.py`(엔진 안)와 `harness/doc_intake.py`(유리벽 밖)가
**같은 잣대**로 PDF 를 읽어야 한다. 한쪽만 고치면 「사람이 넣은 PDF」와 「검색이 물어온 PDF」의
본문이 갈리고, 그 갈림은 원장에서 보이지 않는다. 적재기가 `blocks/` 를 import 하지 않는다는
유리벽을 지키려면 공통 코드는 **어느 쪽에도 속하지 않는 잎**이어야 한다.

LLM 0회. 순수 함수. 값은 `rules/scoring.v1.json` 의 `content_status.pdf` 에서 온다(규약 ①).

**왜 필요했나**: `mss.go.kr` 노쇼 실태조사 보도자료는 본문 HTML 에 제목·담당부서만 있고
수치는 **첨부 PDF 에만** 있다. 지금까지 그 문서는 `content_status="empty"` 로 떨어져
「빈 페이지」와 구분되지 않았다 — 「PDF 라 못 읽었다」는 사실이 원장에서 사라졌다(백로그 23·24).
"""
from __future__ import annotations

import io as _io
import json
import os

HERE = os.path.dirname(os.path.abspath(__file__))

# PDF 라 못 읽었다는 것을 **empty 와 구분해** 원장 끝까지 보낸다 (절대규칙 5 — 실패는 값이다).
UNREADABLE = "pdf_unreadable"


def load_pdf_cfg() -> dict:
    """`rules/scoring.v1.json` 의 `content_status.pdf`.

    규칙 **파일을 읽을 뿐** 엔진을 import 하지 않는다 — 적재기도 부를 수 있어야 한다.
    """
    p = os.path.join(HERE, "rules", "scoring.v1.json")
    return ((json.load(_io.open(p, encoding="utf-8")).get("content_status") or {})
            .get("pdf") or {})


def is_pdf(raw: bytes, content_type: str, cfg: dict) -> bool:
    """매직바이트가 **먼저**다.

    Content-Type 만 보면 놓친다 — 관공서 다운로드 엔드포인트(`Download.do`)는 첨부를
    `application/octet-stream` 으로 준다. 반대로 매직바이트는 서버 설정과 무관하게 참이다.
    """
    magic = (cfg.get("magic") or "%PDF-").encode("ascii", "ignore")
    if raw[:len(magic)] == magic:
        return True
    ct = (content_type or "").lower()
    return any(x in ct for x in (cfg.get("content_type_contains") or []))


def extract(raw: bytes, cfg: dict) -> tuple:
    """(본문, 사유). 본문이 있으면 사유는 빈 문자열.

    텍스트층이 없는 **스캔본**과 파싱 실패를 사유로 갈라 둔다 — 둘 다 `pdf_unreadable`
    이지만 「글자가 없는 PDF」와 「깨진 PDF」는 다음 수가 다르다. 상태값을 둘로 쪼개는 대신
    사유 문자열로 남긴다(읽는 쪽이 아직 없는 구분을 enum 으로 만들지 않는다).
    """
    try:
        import pdfplumber
    except Exception as e:                       # pragma: no cover - 설치돼 있다
        return "", f"pdfplumber 없음: {type(e).__name__}"
    max_pages = int(cfg.get("max_pages") or 40)
    try:
        with pdfplumber.open(_io.BytesIO(raw)) as pdf:
            pages = pdf.pages[:max_pages]
            text = "\n".join((pg.extract_text() or "") for pg in pages)
    except Exception as e:
        return "", f"파싱 실패: {type(e).__name__}: {str(e)[:80]}"
    if len(text.strip()) < int(cfg.get("min_text_len") or 1):
        return "", "텍스트층 없음(스캔본 추정)"
    return text, ""
