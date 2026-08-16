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


def _lines(words: list[dict], y_tol: float) -> list[str]:
    """단어를 원래 행으로 묶되, 행 안에서는 왼쪽에서 오른쪽으로 읽는다."""
    rows: list[list[dict]] = []
    for word in sorted(words, key=lambda item: (float(item.get("top") or 0),
                                                float(item.get("x0") or 0))):
        top = float(word.get("top") or 0)
        if not rows or abs(top - float(rows[-1][0].get("top") or 0)) > y_tol:
            rows.append([word])
        else:
            rows[-1].append(word)
    return [" ".join(str(word.get("text") or "")
                     for word in sorted(row, key=lambda item: float(item.get("x0") or 0))).strip()
            for row in rows if any(str(word.get("text") or "").strip() for word in row)]


def _gutters(words: list[dict], width: float, band: float, min_gap: float) -> list[tuple[float, float]]:
    """단어가 전혀 걸치지 않는 세로 띠 중 충분히 넓은 내부 띠만 반환한다."""
    if width <= 0 or band <= 0:
        return []
    count = max(1, int(width / band) + 1)
    occupied = [False] * count
    for word in words:
        left = max(0, min(count - 1, int(float(word.get("x0") or 0) / band)))
        right = max(left, min(count - 1, int(float(word.get("x1") or 0) / band)))
        for index in range(left, right + 1):
            occupied[index] = True
    gaps: list[tuple[float, float]] = []
    start = None
    for index, used in enumerate(occupied + [True]):
        if not used and start is None:
            start = index
        elif used and start is not None:
            left, right = start * band, min(index * band, width)
            if right - left >= min_gap and left > band and right < width - band:
                gaps.append((left, right))
            start = None
    return gaps


def _page_text(page, columns_cfg: dict) -> tuple[str, str]:
    """페이지 하나만 다단 판정한다. 불확실하면 그 페이지의 기존 추출값으로 돌아간다."""
    fallback = page.extract_text() or ""
    if not columns_cfg.get("enabled"):
        return fallback, "columns_disabled"
    try:
        words = page.extract_words() or []
    except Exception:
        return fallback, "word_extraction_failed"

    minimum = int(columns_cfg.get("min_words") or 40)
    if len(words) < minimum:
        return fallback, "too_few_words"
    width = float(getattr(page, "width", 0) or 0)
    gutters = _gutters(words, width,
                       float(columns_cfg.get("band") or 10),
                       float(columns_cfg.get("min_gap") or 25))
    if not gutters:
        return fallback, "no_gutter"

    cuts = [(left + right) / 2 for left, right in gutters]
    columns: list[list[dict]] = [[] for _ in range(len(cuts) + 1)]
    crossing: list[dict] = []
    margin = float(columns_cfg.get("cross_margin") or 2)
    for word in words:
        x0, x1 = float(word.get("x0") or 0), float(word.get("x1") or 0)
        crossed = [cut for cut in cuts if x0 < cut - margin and x1 > cut + margin]
        if crossed:
            crossing.append(word)
            continue
        index = sum(1 for cut in cuts if x0 >= cut)
        columns[index].append(word)

    if len(crossing) / len(words) > float(columns_cfg.get("max_cross_ratio") or 0.10):
        return fallback, "too_many_cross_column_words"
    minimum_share = float(columns_cfg.get("min_column_share") or 0.15)
    populated = [column for column in columns if column]
    if len(populated) < 2 or any(len(column) / len(words) < minimum_share for column in populated):
        return fallback, "unbalanced_columns"

    y_tol = float(columns_cfg.get("y_tolerance") or 3)
    parts = _lines(crossing, y_tol)
    for column in populated:
        parts.extend(_lines(column, y_tol))
    text = "\n".join(part for part in parts if part).strip()
    return (text, "column_order") if text else (fallback, "empty_column_result")


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
            columns_cfg = cfg.get("columns") or {}
            text = "\n".join(_page_text(pg, columns_cfg)[0] for pg in pages)
    except Exception as e:
        return "", f"파싱 실패: {type(e).__name__}: {str(e)[:80]}"
    if len(text.strip()) < int(cfg.get("min_text_len") or 1):
        return "", "텍스트층 없음(스캔본 추정)"
    return text, ""
