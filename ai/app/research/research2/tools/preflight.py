# -*- coding: utf-8 -*-
"""0단계 가용성 핑 — **문턱에서 확인하고 안 들어간다** (판 ⑫ 지시 ⓑ).

    python tools/preflight.py                 # 전부 확인
    python tools/preflight.py --need openai   # 이번 판에 필요한 것만
    python tools/preflight.py --no-paid       # 유료 핑(LLM 1회)을 빼고 키 존재만

왜 있는가: 판 ⑫ 는 **크레딧 0 인 줄 모르고 유료 판에 들어갔다가** 하네스 첫 호출에서
쓰러졌다. 유료 판이 중간에 쓰러지는 것보다 문턱에서 확인하고 **안 들어가는 게 싸다.**

**핑은 「키가 있는가」가 아니라 「지금 쓸 수 있는가」를 묻는다.** 판 ⑫ 실측에서 키는
멀쩡했고(`sk-proj-…A9AA`) 크레딧만 0이었다 — 존재 확인만 했으면 통과시켰을 것이다.

⚠ **OpenAI 핑은 그 자체가 유료 호출이다**(최소 토큰 1회). `--no-paid` 로 뺄 수 있지만,
그러면 **판 ⑫ 를 쓰러뜨린 바로 그 상태를 못 잡는다.** 기본은 유료다.

산출: `runs/preflight/<stamp>.json` — 판정하지 않고 보이는 것을 적는다.
"""
from __future__ import annotations

import argparse
import io
import json
import os
import sys
import urllib.request
from datetime import datetime

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
for p in (ROOT, os.path.join(ROOT, "adapters")):
    sys.path.insert(0, p)

import runpath                                           # noqa: E402
from base import load_env_key                                    # noqa: E402

#: 상태 어휘 — **셋을 섞지 않는다.**
#:   ok         쓸 수 있다
#:   no_credit  키는 맞는데 **돈이 없다** (판 ⑫ 를 쓰러뜨린 상태)
#:   bad_key    키가 틀렸다·없다
#:   unreachable 네트워크·서버
STATES = ("ok", "no_credit", "bad_key", "unreachable", "not_checked")


#: **유료 진입점마다 「그 코드가 실제로 읽는 환경변수」를 적는다.**
#:
#: 판 ⑫ 실측: 엔진은 `AI_API_KEY`, 하네스는 `OPENAI_API_KEY` 를 읽고 있었다.
#: 하나만 확인하면 **다른 쪽이 죽어 있어도 「진입 가능」이 나온다** — 실제로 그랬다:
#: 핑은 ok 였고 수집은 첫 호출에서 429 로 죽었다.
#:
#: **키를 통일한 뒤에도 이 표는 남긴다**(판 ⑫ ⑴). 지금은 둘 다 `OPENAI_API_KEY` 라
#: 같은 값을 두 번 묻는 셈이지만, 재는 것은 **「엔진이 실제로 읽는 값」**이지
#: 「엔진이 읽을 것이라고 우리가 생각하는 값」이 아니다. 표를 접으면 다음에 또
#: 갈라졌을 때 **핑이 그것을 못 본다** — 이번에 정확히 그렇게 당했다.
#: ⚠ 진입점 코드의 환경변수를 바꾸면 **이 표도 같이 고쳐야 한다.**
ENTRYPOINTS = [("engine", "OPENAI_API_KEY", "run.py — 수집(SEARCH·EXTRACT)"),
               ("harness", "OPENAI_API_KEY", "slot_harness — 슬롯 초안")]


def _one_openai(env_name: str, paid: bool) -> dict:
    key = load_env_key(env_name)
    if not key:
        return {"상태": "bad_key", "why": f"{env_name} 없음"}
    if not paid:
        return {"상태": "not_checked",
                "why": "--no-paid — 키만 있고 **쓸 수 있는지는 안 물었다**"}
    try:
        from openai import OpenAI
        # **키를 명시해 만든다.** `OpenAI()` 로 만들면 환경변수 하나만 보게 되어
        # 진입점별 확인이 통째로 무의미해진다(그게 이 수리의 이유다).
        r = OpenAI(api_key=key).responses.create(model="gpt-4o-mini", input="ping",
                                                 max_output_tokens=16)
        u = getattr(r, "usage", None)
        return {"상태": "ok", "why": "최소 호출 1회 성공",
                "tokens": ((getattr(u, "input_tokens", 0) or 0)
                           + (getattr(u, "output_tokens", 0) or 0))}
    except Exception as e:
        msg = str(e)
        name = type(e).__name__
        # **크레딧 소진과 키 오류를 가른다.** 뭉개면 「키를 다시 발급」 같은 엉뚱한 처방이 나온다.
        low = msg.lower()
        if "credit" in low or "quota" in low or "billing" in low:
            st = "no_credit"
        elif "401" in msg or "invalid_api_key" in low or "authentication" in low:
            st = "bad_key"
        elif "429" in msg:
            st = "no_credit"          # 429 는 실측상 대부분 크레딧이다(판 ⑫)
        else:
            st = "unreachable"
        return {"상태": st, "why": f"{name}: {msg[:200]}"}


def _openai_ping(paid: bool) -> dict:
    """**두 진입점을 각각** 확인하고, 하나라도 못 쓰면 전체를 그 상태로 내린다."""
    per = {}
    for name, env_name, why in ENTRYPOINTS:
        per[name] = {**_one_openai(env_name, paid), "env": env_name, "쓰는_곳": why}
    bad = [k for k, v in per.items() if v["상태"] in ("no_credit", "bad_key", "unreachable")]
    if not bad:
        st = "ok" if all(v["상태"] == "ok" for v in per.values()) else "not_checked"
        return {"상태": st, "why": " · ".join(f"{k}={v['상태']}" for k, v in per.items()),
                "진입점별": per}
    return {"상태": per[bad[0]]["상태"],
            "why": " · ".join(f"{k}({per[k]['env']})={per[k]['상태']}" for k in bad),
            "진입점별": per}


def _get(url: str, timeout: int = 20) -> tuple:
    try:
        req = urllib.request.Request(url, headers={"User-Agent": "preflight/1.0"})
        with urllib.request.urlopen(req, timeout=timeout) as r:
            return r.status, r.read(400).decode("utf-8", "replace")
    except Exception as e:
        return None, f"{type(e).__name__}: {e}"


def _kosis_ping() -> dict:
    key = load_env_key("KOSIS_API_KEY")
    if not key:
        return {"상태": "bad_key", "why": "KOSIS_API_KEY 없음"}
    code, body = _get("https://kosis.kr/openapi/statisticsList.do?method=getList"
                      f"&apiKey={key}&vwCd=MT_ZTITLE&parentListId=A&format=json&jsonVD=Y")
    if code is None:
        return {"상태": "unreachable", "why": body[:160]}
    # KOSIS 는 오류도 200 으로 준다 — 본문을 봐야 한다(어댑터와 같은 눈).
    if '"err"' in body and ('"11"' in body or '"32"' in body or '"33"' in body):
        return {"상태": "bad_key", "why": body[:160]}
    return {"상태": "ok", "why": f"HTTP {code}"}


def _dart_ping() -> dict:
    key = load_env_key("DART_API_KEY")
    if not key:
        return {"상태": "bad_key", "why": "DART_API_KEY 없음"}
    code, body = _get("https://opendart.fss.or.kr/api/list.json"
                      f"?crtfc_key={key}&bgn_de=20240101&end_de=20240102&page_count=1")
    if code is None:
        return {"상태": "unreachable", "why": body[:160]}
    if '"status":"01' in body.replace(" ", ""):      # 010/011/012 = 키 문제
        return {"상태": "bad_key", "why": body[:160]}
    return {"상태": "ok", "why": f"HTTP {code}"}


def _tavily_ping() -> dict:
    key = load_env_key("TAVILY_API_KEY")
    if not key:
        return {"상태": "bad_key", "why": "TAVILY_API_KEY 없음"}
    # 검색을 실제로 쏘지 않는다 — Tavily 는 호출당 과금이다. 키 존재까지만 본다.
    return {"상태": "not_checked", "why": "키 존재만 확인 — 실호출은 과금이라 쏘지 않는다"}


def _modules_ping() -> dict:
    """**키가 아닌 결함으로 유료 4판이 쓰러졌다** (판 ㉞).

    컨테이너에 `pdfplumber` 가 없어 PDF 48건을 통째로 버렸는데, 이 도구는 키만 묻고
    있었으므로 「진입 가능」을 네 번 내줬다. 해석기가 없는 것은 크레딧이 없는 것과
    같은 종류의 막힘이다 — 둘 다 **돈을 쓰고도 자료를 못 얻는다.**

    ⚠ **컨테이너 안에서 돌려야 뜻이 있다.** 로컬에는 다 깔려 있어 항상 ok 다.
    유료 호출 0회라 `--no-paid` 와 무관하게 돈다.
    """
    from importlib.metadata import version
    ver, 없음 = {}, []
    for name in runlog_capabilities():
        if name == "python":
            continue
        try:
            ver[name] = version(name)
        except Exception:
            ver[name] = None
            없음.append(name)
    적힌 = " · ".join(f"{k} {v or '없음'}" for k, v in ver.items())
    if 없음:
        return {"상태": "unreachable", "why": f"설치 안 됨: {', '.join(없음)}  ({적힌})",
                "버전": ver}
    return {"상태": "ok", "why": 적힌, "버전": ver}


def runlog_capabilities() -> tuple:
    """엔진이 재는 목록과 **같은 목록**을 쓴다. 두 곳에 따로 적으면 갈라진다 —
    이번 사고의 물리적 원인이 정확히 그것이었다(엔진이 쓰는 것 vs 이미지가 설치하는 것).
    """
    import runlog
    return ("python",) + tuple(runlog.CAPABILITY_PACKAGES)


CHECKS = {"openai": _openai_ping, "kosis": _kosis_ping,
          "dart": _dart_ping, "tavily": _tavily_ping, "modules": _modules_ping}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--need", action="append", default=None,
                    help="이번 판에 필요한 것만. 기본은 전부")
    ap.add_argument("--no-paid", action="store_true",
                    help="유료 핑(OpenAI 최소 호출)을 뺀다. ⚠ 그러면 크레딧 0 을 못 잡는다")
    a = ap.parse_args()
    need = [n for n in (a.need or list(CHECKS)) if n in CHECKS]

    out = {}
    for name in need:
        out[name] = (_openai_ping(not a.no_paid) if name == "openai" else CHECKS[name]())
        print(f"  [{out[name]['상태']:<12}] {name}  {out[name]['why'][:110]}")

    # **막는 것은 「ok 가 아닌 것」이 아니라 「쓸 수 없는 것」이다.**
    # `not_checked` 는 못 쓴다는 뜻이 아니라 **안 물어봤다**는 뜻이라 막지 않는다 —
    # 대신 그 사실이 기록에 남아 「확인했다」로 오독되지 않는다.
    blocked = {k: v for k, v in out.items() if v["상태"] in ("no_credit", "bad_key",
                                                             "unreachable")}
    rec = {
        "_규칙": ("문턱 확인. 「키가 있는가」가 아니라 「지금 쓸 수 있는가」를 묻는다. "
                "`not_checked` 는 «못 쓴다»가 아니라 «안 물어봤다»다 — 섞지 않는다."),
        "at": datetime.now().isoformat(timespec="seconds"),
        "확인": out, "막힘": sorted(blocked),
        "판정": "진입 가능" if not blocked else "진입 금지 — 유료 판에 들어가지 않는다",
    }
    d = runpath.write_dir("preflight")
    os.makedirs(d, exist_ok=True)
    p = os.path.join(d, datetime.now().strftime("%Y%m%d-%H%M%S") + ".json")
    io.open(p, "w", encoding="utf-8").write(json.dumps(rec, ensure_ascii=False, indent=1))

    print(f"\n{rec['판정']}")
    if blocked:
        for k, v in blocked.items():
            print(f"  {k}: {v['상태']} — {v['why'][:160]}")
    print(f"기록: {p}")
    return 1 if blocked else 0


if __name__ == "__main__":
    sys.exit(main())
