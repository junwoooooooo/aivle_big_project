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


def _lines(words: list, y_tol: float) -> list:
    """낱말을 같은 줄로 묶어 문자열 목록으로. y 가 `y_tol` 안이면 한 줄이다."""
    rows, cur, cy = [], [], None
    for w in sorted(words, key=lambda w: (round(w["top"], 1), w["x0"])):
        if cy is None or abs(w["top"] - cy) <= y_tol:
            cur.append(w)
            cy = w["top"] if cy is None else cy
        else:
            rows.append(cur)
            cur, cy = [w], w["top"]
    if cur:
        rows.append(cur)
    return [" ".join(x["text"] for x in sorted(r, key=lambda w: w["x0"])) for r in rows]


def _gutters(words: list, width: float, band: float, min_gap: float) -> list:
    """**낱말이 하나도 안 걸치는 세로 빈 띠**를 전부 찾는다. 그것이 단 사이 틈이다.

    단 수를 미리 정하지 않는다 — 실측 문서가 2단이 아니라 **3단**이었다.
    """
    cov: dict = {}
    for w in words:
        for b in range(int(w["x0"] // band), int(w["x1"] // band) + 1):
            cov[b] = cov.get(b, 0) + 1
    out, run = [], None
    for b in range(int(width // band) + 1):
        if cov.get(b, 0) == 0:
            run = b if run is None else run
        elif run is not None:
            out.append((run * band, b * band))
            run = None
    if run is not None:
        out.append((run * band, (int(width // band) + 1) * band))
    # 양끝 여백은 단 사이 틈이 아니다
    return [(a, b) for a, b in out
            if b - a >= min_gap and a > width * 0.05 and b < width * 0.95]


def _page_text(pg, c: dict) -> tuple:
    """(본문, 어떻게 읽었나). **못 하겠으면 옛 방식으로 떨어진다** — 조용히가 아니라 사유와 함께."""
    fallback = pg.extract_text() or ""
    try:
        words = pg.extract_words()
    except Exception as e:
        return fallback, f"낱말 실패({type(e).__name__})"
    if len(words) < 40:
        return fallback, "낱말이 적다"
    band = float(c.get("band") or 10.0)
    gs = _gutters(words, pg.width, band, float(c.get("min_gap") or 25.0))
    if not gs:
        return fallback, "단 없음"
    cuts = [(a + b) / 2 for a, b in gs]
    edges = [0.0] + cuts + [pg.width]
    cols: list = [[] for _ in range(len(edges) - 1)]
    cross: list = []
    for w in words:
        if any(w["x0"] < g - 2.0 and w["x1"] > g + 2.0 for g in cuts):
            cross.append(w)        # 단을 가로지르는 것(제목·표)은 따로 둔다
            continue
        mid = (w["x0"] + w["x1"]) / 2
        i = 0
        for k in range(len(edges) - 1):
            if edges[k] <= mid < edges[k + 1]:
                i = k
                break
        cols[i].append(w)
    if len(cross) / len(words) > float(c.get("max_cross") or 0.10):
        return fallback, f"걸침 과다({100 * len(cross) / len(words):.1f}%)"
    # **가짜 단 가드.** 발표자료는 요소 사이가 넓어 세로 빈 띠가 진짜로 생기는데 그건
    # 읽기 순서가 아니다(실측: 회사소개 슬라이드에서 4단·3단이 잡혔다). 나뉜 단이
    # 저마다 충분한 몫을 갖지 못하면 단이 아니다.
    share = float(c.get("min_col_share") or 0.15)
    used = [col for col in cols if col]
    if len(used) < 2 or any(len(col) < share * len(words) for col in used):
        return fallback, "단이 고르지 않다"
    y_tol = float(c.get("y_tol") or 3.0)
    parts = _lines(cross, y_tol) if cross else []
    for col in cols:
        if col:
            parts += _lines(col, y_tol)
    return "\n".join(parts), f"{len(cols)}단"


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
    cols = (cfg.get("columns") or {})
    try:
        with pdfplumber.open(_io.BytesIO(raw)) as pdf:
            pages = pdf.pages[:max_pages]
            if cols.get("enabled"):
                # **다단 조판을 단 단위로 읽는다.** 안 그러면 좌우 단이 줄 단위로 끼어들어
                # 온전한 문장이 하나도 안 남고, 인용 대조가 **맞는 사실을 지어낸 것으로**
                # 판정한다(판 ㊳ 실측: 그 문서 인용 8건 중 7건). 쪽마다 따로 판정하고
                # 못 하겠으면 그 쪽만 옛 방식으로 떨어진다.
                got = [_page_text(pg, cols) for pg in pages]
                text = "\n".join(t for t, _ in got)
            else:
                text = "\n".join((pg.extract_text() or "") for pg in pages)
    except Exception as e:
        return "", f"파싱 실패: {type(e).__name__}: {str(e)[:80]}"
    if len(text.strip()) < int(cfg.get("min_text_len") or 1):
        return "", "텍스트층 없음(스캔본 추정)"
    return text, ""
