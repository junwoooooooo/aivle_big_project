"""카드 뱅크 로딩과 층화추출.

뱅크는 KISDI 한국미디어패널조사 파생 마이크로데이터라 **이미지에 굽지 않는다**
(재배포 금지 조항). `TWIN_BANK_DIR` 에 읽기 전용으로 바인드 마운트한다 —
`research2/runs` 와 같은 방식이다. 없으면 **시끄럽게** 실패한다: 조용히 빈 표본으로
도는 것보다 낫다.

만드는 쪽은 `combine_csv/_build/twin/twin_export.py` (오프라인 1회).
현재 뱅크: 8,604명 / 10.8 MB. 만 20세 이상, 미성년 제외 — 검증(G3B·G3D)이 쓴 모집단과 같다.
"""

import csv
import json
import logging
import os
from collections import defaultdict

from app.providers import ProviderFailure

logger = logging.getLogger(__name__)

CARDS_FILE = "twin_cards_generic.jsonl"
FRAME_FILE = "twin_frame.csv"

# 층은 성×연령 10셀. G3B 쿼터와 같은 축이다.
BANDS = ("20대", "30대", "40대", "50대", "60+")
GENDERS = ("남", "여")

_cache: tuple[dict[str, str], list[dict]] | None = None


def _bank_dir() -> str:
    path = os.getenv("TWIN_BANK_DIR", "").strip()
    if not path:
        raise ProviderFailure("DEPENDENCY_UNAVAILABLE", "TWIN_BANK_UNAVAILABLE", 503, False,
                              safe_diagnostics={"reason": "TWIN_BANK_DIR is not set"})
    return path


def load() -> tuple[dict[str, str], list[dict]]:
    """(pid_hash → 카드 본문, 표집틀 행들). 프로세스당 1회만 읽는다(약 10.8 MB 상주)."""
    global _cache
    if _cache is not None:
        return _cache

    directory = _bank_dir()
    cards_path = os.path.join(directory, CARDS_FILE)
    frame_path = os.path.join(directory, FRAME_FILE)
    for path in (cards_path, frame_path):
        if not os.path.exists(path):
            raise ProviderFailure(
                "DEPENDENCY_UNAVAILABLE", "TWIN_BANK_UNAVAILABLE", 503, False,
                safe_diagnostics={"missing": os.path.basename(path)})

    cards: dict[str, str] = {}
    # 잘린 줄은 **건너뛰되 센다.** 뱅크는 8,596줄짜리 로컬 자산이고 실제로 한 줄이 문장
    # 중간에서 잘려 있었다(41행). 그 한 줄 때문에 `json.loads` 가 터져 **8,595명이 통째로**
    # 막혔고, 화면에는 「카드 뱅크가 서버에 붙어 있지 않다」는 엉뚱한 말이 떴다 — 붙어 있었다.
    #
    # ⚠ 조용히 넘기지는 않는다. 몇 줄을 버렸는지 로그에 남기고, **1% 를 넘으면 실패시킨다.**
    #   파일이 절반쯤 썩어도 도는 코드는 표본이 조용히 갈린 채 조사를 계속하게 만든다.
    damaged = 0
    with open(cards_path, encoding="utf-8") as handle:
        for number, line in enumerate(handle, 1):
            if not line.strip():
                continue
            try:
                record = json.loads(line)
            except json.JSONDecodeError:
                damaged += 1
                logger.warning("twin bank card line %d is malformed — skipped", number)
                continue
            text = record.get("text")
            if not isinstance(text, str) or not text.strip():
                raise ProviderFailure(
                    "DEPENDENCY_UNAVAILABLE", "TWIN_BANK_UNAVAILABLE", 503, False,
                    safe_diagnostics={"reason": "card without text"})
            cards[record["pid_hash"]] = text

    if damaged and damaged * 100 > len(cards):
        raise ProviderFailure(
            "DEPENDENCY_UNAVAILABLE", "TWIN_BANK_UNAVAILABLE", 503, False,
            safe_diagnostics={"reason": "bank is damaged", "skipped": damaged,
                              "loaded": len(cards)})
    if damaged:
        logger.warning("twin bank loaded with %d damaged card line(s) skipped", damaged)

    with open(frame_path, encoding="utf-8-sig") as handle:
        frame = [row for row in csv.DictReader(handle) if row["pid_hash"] in cards]

    if not frame:
        raise ProviderFailure("DEPENDENCY_UNAVAILABLE", "TWIN_BANK_UNAVAILABLE", 503, False,
                              safe_diagnostics={"reason": "empty frame"})
    logger.info("twin bank loaded cards=%d frame=%d dir=%s", len(cards), len(frame), directory)
    _cache = (cards, frame)
    return _cache


def stratified_sample(frame: list[dict], size: int) -> tuple[list[dict], dict]:
    """성×연령 10셀 비례 배분 + 최대잉여. **결정론적이다** — 난수를 쓰지 않는다.

    규칙은 `combine_csv/_build/g3d/g3d_04_sample.py` 와 같다: 비례 목표를 바닥으로 깎고
    잉여가 큰 셀부터 하나씩 채우되, 동률이면 `(gender, band)` 오름차순. 셀 내부는
    `pid_hash` 오름차순 앞에서부터.

    같은 표본 크기면 늘 같은 사람들이 뽑힌다. 재현 가능성을 택한 것이고, 조사 간 비교가
    사람 교체가 아니라 자극 차이만 반영하게 하려는 것이기도 하다.
    """
    cells: dict[tuple[str, str], list[dict]] = defaultdict(list)
    for row in frame:
        if row["gender"] in GENDERS and row["band"] in BANDS:
            cells[(row["gender"], row["band"])].append(row)
    for rows in cells.values():
        rows.sort(key=lambda r: r["pid_hash"])

    total = sum(len(rows) for rows in cells.values())
    if total < size:
        raise ProviderFailure(
            "INVALID_REQUEST", "TWIN_SAMPLE_TOO_LARGE", 400, False,
            safe_diagnostics={"requested": size, "available": total})

    target = {key: len(rows) * size / total for key, rows in cells.items()}
    quota = {key: int(value) for key, value in target.items()}
    remainder = size - sum(quota.values())
    order = sorted(cells, key=lambda key: (-(target[key] - quota[key]), key))
    for key in order[:remainder]:
        quota[key] += 1

    # 셀이 얕으면 목표를 못 채운다 — 조용히 다른 셀에서 채우지 않고 사실로 남긴다.
    # 뱅크는 60대 이상이 3,676명인 반면 20·30대는 각 850여 명이라 젊은 층이 먼저 마른다.
    short = {}
    picked: list[dict] = []
    for key in sorted(cells):
        want = quota[key]
        have = cells[key][:want]
        if len(have) < want:
            short[f"{key[0]} {key[1]}"] = {"quota": want, "available": len(have)}
        picked.extend(have)

    strata = {f"{key[0]} {key[1]}": quota[key] for key in sorted(cells)}
    return picked, {"requested": size, "drawn": len(picked), "strata": strata,
                    "shortCells": short}
