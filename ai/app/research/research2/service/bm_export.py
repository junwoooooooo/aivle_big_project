# -*- coding: utf-8 -*-
"""BM 보고서 내보내기 — **서비스 층 2.5호.** LLM 0회 · 네트워크 0회 · 원장 읽기 전용.

    python -m service.bm_export <run_id>          # research2/ 에서 실행
    python service/bm_export.py <run_id>

`BAF-09-07` ③ 「BM 분석 결과를 보고서로 다운로드 가능」의 산출물이다.

**이 파일은 아무것도 해석하지 않는다.** `bm_layer.build()` 결과를 Markdown 으로 **표현만**
바꾼다. 내용을 더하거나 요약하지 않는다 — 더하는 순간 근거 없는 문장이 파일에 실린다.

보존 의무 (테스트로 고정):
  · 선언 꼬리표(**선언(원장 관측 아님) · 근거 · 만료조건**)
  · 조립 꼬리표(`조립(템플릿 v1)`) — 「생성」이라 적지 않는다
  · 공백 선언 · `single_path` 등 검증 상태 꼬리표
  · 채널 한계 문구는 **뒷문장까지**("채널 없이 BM 이 성립한다는 뜻이 아니다")

**fail-closed** — 원장이 없거나 조립이 실패하면 **파일을 만들지 않는다.**
빈 파일·부분 파일은 「보고서가 나왔다」로 읽히므로 그것이 최악이다.

서버 세션과의 계약(유리벽의 문):
  · 성공 → **stdout 마지막 줄에 파일 경로**, exit 0
  · 실패 → stderr 에 사유, **exit 비0**, 파일 없음
  · 서버는 **산출물(md)을 읽기만 한다.** `research2/` 내부 수정은 연구 세션 전용이다.
"""
from __future__ import annotations

import argparse, datetime, io, os, sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
sys.path.insert(0, HERE)

import bm_layer                                    # 같은 서비스 층 (엔진 아님)

OUT_DIR = os.path.join(ROOT, "outputs")


def build_markdown(run_id: str, now: str) -> str:
    """`bm_layer` 출력 → Markdown. **표현 변환만.**"""
    doc = bm_layer.build(run_id)
    head = [
        f"# BM 분석 보고서 — {run_id}",
        "",
        f"- 원장(run_id): `{run_id}`",
        f"- 생성 시각: {now}",
        f"- **단일 원장 기준** — 여러 실행을 종합하지 않았다",
        f"- 서술 방식: {doc['narrative']['evidence']}  ← LLM 생성이 아니라 **조립**이다",
        f"- 요구 근거: {doc['requirement']}",
        "",
        "> 이 파일은 `service/bm_layer.py` 출력의 **표현 변환**이다. 내용을 더하거나",
        "> 해석하지 않는다. 모든 값에는 근거(원장 `trace_id` / 성적표 인용 / 공백 선언)가 붙는다.",
        "",
        "---",
        "",
    ]
    return "\n".join(head) + bm_layer.render(doc) + "\n"


def export(run_id: str, now: str | None = None, out_dir: str | None = None) -> str:
    """성공하면 파일 경로. **실패하면 예외를 올리고 파일을 만들지 않는다.**

    `out_dir` 은 **테스트가 임시 폴더로 격리하기 위한 것**이다. 기본값은 `outputs/` 이고
    CLI 동작은 변하지 않는다. 테스트가 납품 디렉터리에 쓰면 **부산물과 납품물이 파일만
    봐서는 구분되지 않는다** — 실제로 `report3-04` 의 생성 시각이 테스트 고정값
    `2026-01-01T00:00:00` 으로 남아 있었다(검수 2026-08-07).
    """
    now = now or datetime.datetime.now().isoformat(timespec="seconds")
    out_dir = out_dir or OUT_DIR
    md = build_markdown(run_id, now)          # ← 먼저 전부 만든다
    if not md.strip():
        raise RuntimeError("빈 보고서 — 파일을 만들지 않는다")
    os.makedirs(out_dir, exist_ok=True)
    path = os.path.join(out_dir, f"bm_report_{run_id}.md")
    io.open(path, "w", encoding="utf-8").write(md)   # ← 성공이 확정된 뒤에만 쓴다
    return path


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("run_id")
    ap.add_argument("--out-dir", default=None,
                    help="산출 폴더(기본 outputs/). 테스트 격리용 — 서버는 쓰지 않는다")
    a = ap.parse_args()
    try:
        path = export(a.run_id, out_dir=a.out_dir)
    except FileNotFoundError as e:
        print(f"원장을 찾을 수 없다: {e}", file=sys.stderr)
        return 2
    except Exception as e:
        print(f"보고서 생성 실패({type(e).__name__}): {e}", file=sys.stderr)
        return 3
    print(path)                                # stdout 마지막 줄 = 파일 경로
    return 0


if __name__ == "__main__":
    sys.exit(main())
