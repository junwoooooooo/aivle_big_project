# -*- coding: utf-8 -*-
"""실행 기록 — 파일 2개만 만든다.

    runs/<run_id>/
      run.jsonl     노드별 이벤트 전부 (append-only)
      result.json   입력·규칙·판정·계측 스냅샷

`trace_id` 로 **슬롯 → 쿼리 → URL → 인용 → 사실 → 판정**이 한 줄로 꿰인다.
판정 하나에서 원본 URL 까지 역추적 가능해야 한다.

`result.json` 에는 `rules/*.json` 을 **값째로 복사**한다.
참조로 두면 규칙을 고쳤을 때 과거 실행 기록이 소급해서 바뀐다(절대규칙 7).
"""
from __future__ import annotations

import copy, hashlib, io, json, os, re, threading, time
from datetime import date, datetime

HERE = os.path.dirname(os.path.abspath(__file__))
RULES_DIR = os.path.join(HERE, "rules")
# 원장 위치. **환경변수로 옮길 수 있어야 한다** (판 ㉝ 이식).
#   이미지 안 `runs/` 는 재기동마다 증발하고 232MB 급이라 이미지에 넣을 것도 아니다.
#   컨테이너에서는 볼륨(`/research2-runs`)을 가리키고, 로컬 연구 세션에서는 기본값 그대로.
#   ⚠ 기본값을 바꾸지 않는다 — 949개 테스트가 이 자리를 전제한다.
#   ⚠ **쓰기 자리와 읽기 자리가 갈렸다.** 수집이 원장을 만들기 시작하면서 씨앗 `runs/` 는
#     컨테이너에서 `:ro` 인 채로 두고 새 원장은 `runs-generated/` 로 간다. 답은
#     `runpath` 한 곳에 있다 — 여기서 다시 계산하면 두 곳이 갈린다.
import runpath                                                   # noqa: E402

RUNS_DIR = runpath.RUNS_DIR


# ══════════════════════════════════════════════════════════════
# 규칙 로딩 — 한 번 읽어 값으로 들고 다닌다
# ══════════════════════════════════════════════════════════════
# ⚠ **핀은 여기 없다.** `rules/rule_pins.json` 이 단일 원천이다(판 ㉙ S0).
# 코드에 파일명을 적어 두었더니 유리벽 밖 적재기가 **리터럴 사본**(whitelist.v5.json)을
# 따로 들고 v5 에 멈춰 있었다 — 엔진은 v8. 적재기는 `runlog` 를 import 할 수 없으므로
# (엔진 import 0) 핀은 **코드가 아니라 데이터**여야 둘이 같은 것을 읽는다.
# 옛 버전 파일을 지우지 않는 이유(과거 실행의 목록을 열 수 있어야 한다)는
# 그 파일 `_규율` 에 값으로 옮겨 적었다.
PINS_FILE = os.path.join(RULES_DIR, "rule_pins.json")

with io.open(PINS_FILE, encoding="utf-8") as _f:
    RULE_FILES = json.load(_f)["pins"]


def load_rules(rules_dir: str = RULES_DIR) -> dict:
    out = {}
    for key, fname in RULE_FILES.items():
        with io.open(os.path.join(rules_dir, fname), encoding="utf-8") as f:
            out[key] = json.load(f)
    out["_versions"] = {k: v.get("version") for k, v in out.items() if isinstance(v, dict)}
    return out


# ══════════════════════════════════════════════════════════════
# 사전등록 계측 (판 ⑪ ②)
#   판 ⑥·판 ⑩ 이 연속으로 `expected.md` 사전등록을 건너뛰었다. **규칙만으로는 안 지켜진다**가
#   두 번 실측됐으므로 계측기를 단다 — 「사전등록했다」도 부재 주장이고, 재는 것이 없으면
#   영원히 주장이다(판 ⑩ ②의 교훈을 이 자리에 적용한 것이다).
#
#   ⚠ **이것은 차단이 아니라 기록이다.** 지시서 ②가 요구한 것이 「result 에 기록」이고,
#     차단으로 만들지 말지는 사람 결정이다. 그리고 차단으로 만들어도 착수 1초 전에 쓰면
#     통과한다 — 이 장치가 실제로 만드는 것은 **감사 가능성**이지 물리적 강제가 아니다.
#     그 한계를 값에도 적어 둔다(`_한계`).
# ══════════════════════════════════════════════════════════════
EXPECTED_MD = os.path.join(HERE, "expected.md")
_APPENDIX = re.compile(r"^#{1,2}\s*부록\s+([A-Z])\b", re.M)


def prereg_stamp(at: float, path: str = EXPECTED_MD) -> dict:
    """사전등록 상태를 값으로 만든다. `at` 은 재는 시점(유료 호출 직전)의 epoch 초.

    **델타 부호가 판정이다**: `expected.md` 가 먼저면 양수(사전등록), 실행이 먼저면 음수(이탈).
    """
    if not os.path.exists(path):
        return {"판정": "미측정", "why": f"{os.path.basename(path)} 없음",
                "부록": None, "델타_초": None}
    mt = os.path.getmtime(path)
    try:
        anchors = _APPENDIX.findall(io.open(path, encoding="utf-8").read())
    except Exception:
        anchors = []
    delta = round(at - mt, 1)
    return {
        # **마지막 부록이 현재 판의 것**이다 — 이 파일은 판마다 append 되기 때문이다.
        "부록": (f"부록 {anchors[-1]}" if anchors else None),
        "부록_총수": len(anchors),
        "expected_mtime": datetime.fromtimestamp(mt).isoformat(timespec="seconds"),
        "잰_시점": datetime.fromtimestamp(at).isoformat(timespec="seconds"),
        "델타_초": delta,
        "판정": "사전등록" if delta >= 0 else "이탈 — 실행이 먼저다",
        "_한계": ("mtime 비교라 **착수 직전에 써도 통과한다.** 이 값이 만드는 것은 차단이 "
                "아니라 감사 가능성이다 — 언제 썼는지가 원장에 남는다."),
    }


def sha(obj) -> str:
    return hashlib.sha256(
        json.dumps(obj, ensure_ascii=False, sort_keys=True, default=str).encode()
    ).hexdigest()[:12]


# ══════════════════════════════════════════════════════════════
# 실행 능력 지문 (판 ㉟ ①)
#   **수집한 그 프로세스가 자기 능력을 적는다.** 판 ㉞ 에서 컨테이너에 `pdfplumber` 가
#   없어 PDF 48건을 통째로 버렸는데, 원장 어디에도 「해석기가 없었다」가 없어서
#   유료 4판(≈252회)을 결함 위에서 쟀다. 이 한 칸만 있었으면 첫 판에서 끝났다.
#
#   ⚠ **없는 것은 예외가 아니라 `None` 이다.** 계측이 실행을 죽이면 안 된다.
#   ⚠ 로컬과 컨테이너가 다르다는 것이 사고의 본체였다 — 그래서 사람이 따로 돌리는
#     `tools/preflight.py` 가 아니라 **실행 자신**이 남긴다.
CAPABILITY_PACKAGES = ("pdfplumber", "trafilatura", "requests", "openai")


def capability_fingerprint() -> dict:
    import platform
    from importlib.metadata import version
    out: dict = {"python": platform.python_version()}
    for name in CAPABILITY_PACKAGES:
        try:
            out[name] = version(name)
        except Exception:
            out[name] = None
    return out


# ══════════════════════════════════════════════════════════════
# 실행 하나 = Run 하나
# ══════════════════════════════════════════════════════════════
class Run:
    def __init__(self, run_id: str, rules: dict | None = None,
                 reference_date: str | None = None):
        self.run_id = run_id
        # **쓰기는 언제나 `runs-generated/` 다.** 씨앗 원장(`runs/`)은 컨테이너에서 `:ro` 이고,
        # 그 보호가 「컨테이너가 측정 원장을 덮어쓸 수 없다」는 규율 그 자체다.
        self.dir = runpath.write_dir(run_id)
        os.makedirs(self.dir, exist_ok=True)
        self.jsonl = os.path.join(self.dir, "run.jsonl")

        # 규칙은 이 시점의 **값**을 붙든다. 실행 도중 파일이 바뀌어도 영향받지 않는다.
        self.rules = copy.deepcopy(rules if rules is not None else load_rules())

        # 기준일 — 신선도 계산이 '오늘'에 의존하면 재현성이 깨진다(수용기준 3)
        self.reference_date = reference_date or date.today().isoformat()

        self.t0 = time.time()
        self._lock = threading.Lock()
        self.counters: dict[str, float] = {}
        self.adapters: dict[str, str] = {}      # kosis/dart/web → ok | not_configured | error
        self.notes: list[str] = []

        # ── 무인 계측기 (판 ⑪ ①) ──────────────────────────────
        # **부재 주장에는 계측기가 필요하다.** 「사람 개입 0」은 개입을 세는 카운터가
        # 돌면서 0을 기록했을 때만 관측이고, 카운터가 없으면 영원히 주장이다 —
        # 판 ⑧ 의 「무인 실증」이 판 ⑩ 에서 「주장 — 디스크 미확인」으로 강등된 것이
        # 그 실측이다. 이 두 리스트가 그 강등을 되돌리기 위한 자리다.
        #
        # ⚠ **빈 리스트가 곧 증거다.** 「개입 0」을 말하려면 이 칸이 산출물에
        #   `[]` 로 **존재해야** 한다. 칸 자체가 없으면 0 이 아니라 **미측정**이다.
        self.interventions: list[dict] = []     # 사람을 불러야 했던 사건
        self.decisions: list[dict] = []         # 스스로 내린 결정 + 근거 규칙

        # 사전등록 (판 ⑪ ②) — **첫 유료 호출 직전에** 잰다.
        # 실행 시작이 아니라 «돈이 나가는 순간»이 기준이다: 실행은 시작했지만 유료 호출이
        # 0회인 실행(재채점·replay)은 사전등록을 요구할 대상이 아니다.
        self.prereg: dict | None = None

    # ── 스냅샷 — 로그 줄이 아니라 통째로 남기는 것 (본문 전문 등) ──
    def snapshot(self, name: str, obj) -> None:
        from schema import to_dict
        io.open(os.path.join(self.dir, f"{name}.json"), "w", encoding="utf-8").write(
            json.dumps(to_dict(obj), ensure_ascii=False, indent=2, default=str))

    # ── 이벤트 한 줄 ──────────────────────────────────────────
    def log(self, node: str, payload, *, slot_id: str | None = None,
            trace_id: str | None = None, status: str = "ok",
            input_hash: str | None = None) -> None:
        from schema import to_dict
        p = to_dict(payload)
        if isinstance(p, dict):
            slot_id = slot_id or p.get("slot_id")
            trace_id = trace_id or p.get("trace_id")
            status = p.get("status", status) if isinstance(p.get("status"), str) else status
        line = {
            "run_id": self.run_id, "node": node,
            "slot_id": slot_id, "trace_id": trace_id,
            "ts": datetime.now().isoformat(timespec="seconds"),
            "status": status,
            "input_hash": input_hash or sha(p),
            "payload": p,
        }
        with self._lock, io.open(self.jsonl, "a", encoding="utf-8") as f:
            f.write(json.dumps(line, ensure_ascii=False, default=str) + "\n")
        self.count(f"{node}.rows")
        self.count(f"{node}.{status}")

    def log_many(self, node: str, payloads: list, **kw) -> None:
        for p in payloads:
            self.log(node, p, **kw)

    # ── 다시 읽기 — 앞 노드부터 재실행할 때 ────────────────────
    def read(self, node: str) -> list[dict]:
        if not os.path.exists(self.jsonl):
            return []
        out = []
        for l in io.open(self.jsonl, encoding="utf-8"):
            if not l.strip():
                continue
            row = json.loads(l)
            if row["node"] == node:
                out.append(row["payload"])
        return out

    def trace(self, trace_id_prefix: str) -> list[dict]:
        """trace_id 로 한 줄 꿰기 — 판정에서 원본 URL 까지 역추적."""
        if not os.path.exists(self.jsonl):
            return []
        return [json.loads(l) for l in io.open(self.jsonl, encoding="utf-8")
                if l.strip() and (json.loads(l).get("trace_id") or "").startswith(trace_id_prefix)]

    # ── 계측 ──────────────────────────────────────────────────
    def count(self, key: str, n: float = 1) -> None:
        with self._lock:
            self.counters[key] = self.counters.get(key, 0) + n

    # ── 무인 계측기 (판 ⑪ ①) ─────────────────────────────────
    def decide(self, what: str, choice, *, rule: str, why: str = "",
               slot_id: str | None = None) -> None:
        """스스로 내린 결정 하나를 기록한다. **`rule` 이 빈 문자열이면 그것도 기록한다** —
        「규칙 없이 골랐다」가 가장 중요한 발견이기 때문이다. 조용히 고르지 않는다.
        """
        rec = {"무엇": what, "고른_것": choice, "근거_규칙": rule or "(없음 — 코드 판단)",
               "왜": why, "slot_id": slot_id,
               "at": datetime.now().isoformat(timespec="seconds")}
        with self._lock:
            self.decisions.append(rec)
        self.count("decision.total")
        self.count(f"decision.{'ruled' if rule else 'unruled'}")
        self.log("decision", rec, slot_id=slot_id)

    def intervene(self, kind: str, detail: str = "", *, blocking: bool = True) -> None:
        """**사람을 불러야 했던 사건.** `blocking=False` 는 「사람이 봐야 하지만 지금
        멈추지는 않았다」(예: `retry_hint` 발행)다.

        ⚠ **fail-open 은 여기 오지 않는다.** 그건 사람을 **안 부르고** 진행한 것이라
        `decide()` 쪽이다. 둘을 섞으면 「H3 가 작동했다」가 「개입이 있었다」로 뒤집힌다.
        """
        rec = {"종류": kind, "상세": detail, "멈췄나": blocking,
               "at": datetime.now().isoformat(timespec="seconds")}
        with self._lock:
            self.interventions.append(rec)
        self.count("intervention.total")
        self.count(f"intervention.{'blocking' if blocking else 'nonblocking'}")
        self.log("intervention", rec, status="error" if blocking else "ok")

    def set_adapter(self, name: str, status: str, note: str = "") -> None:
        """규칙 5 — not_configured 는 침묵이 아니다. result.json 과 보고서 §7 까지 간다."""
        self.adapters[name] = status
        if status != "ok" and note:
            self.notes.append(note)

    def coverage_caveat(self) -> str | None:
        """한계 고지. **어댑터가 꺼진 것과 해석기가 없는 것은 같은 종류의 한계다** —
        둘 다 「자료가 없다」가 아니라 「우리가 못 봤다」이므로 한 줄에 같이 실린다.
        하나가 다른 하나를 지우면 안 되니 갈아치우지 않고 이어 붙인다.
        """
        parts: list[str] = []
        off = [k for k, v in self.adapters.items() if v != "ok"]
        if off:
            if {"kosis", "dart"} & set(off):
                parts.append(f"통계 API 미사용({', '.join(sorted(off))}) — 커버리지 하한")
            else:
                parts.append(f"어댑터 미사용: {', '.join(sorted(off))} — 커버리지 하한")
        if capability_fingerprint().get("pdfplumber") is None:
            parts.append("PDF 해석기 없음 — PDF 출처 커버리지 0")
        return " / ".join(parts) if parts else None

    # ── 마무리 ────────────────────────────────────────────────
    def finish(self, *, concept=None, slots=None, verdict=None,
               report=None, extra: dict | None = None) -> dict:
        from schema import to_dict
        # 슬롯 세트 해시 — 비교 축이므로 **여기서 값으로 박는다.**
        # 읽는 쪽(eval·뷰어)이 매번 계산하면 그쪽 로직이 바뀔 때 과거 실행의 축이
        # 소급해서 달라 보인다. 규칙을 값째로 복사하는 것과 같은 이유다(절대규칙 7).
        # slot_id 로 정렬해 해시한다 — 순서만 다른 같은 슬롯 세트는 같은 축이어야 한다.
        slots_d = to_dict(slots) or []
        result = {
            "run_id": self.run_id,
            "reference_date": self.reference_date,
            "finished_at": datetime.now().isoformat(timespec="seconds"),
            "wall_clock_sec": round(time.time() - self.t0, 1),

            # 어댑터 상태와 한계 — 없으면 커버리지 저하의 원인을 구분할 수 없다
            "adapters": dict(self.adapters),
            # 「어댑터가 켜졌나」 옆이 「해석기가 있나」의 자리다 (판 ㉟ ①).
            # ⚠ 값이 None 이어도 **칸은 반드시 있어야 한다** — 칸이 없으면 0 이 아니라 미측정이다.
            "실행_능력": capability_fingerprint(),
            "coverage_caveat": self.coverage_caveat(),
            "notes": list(self.notes),

            "slot_set_hash": (sha(sorted(slots_d, key=lambda s: s.get("slot_id", "")))
                              if slots_d else None),

            # 무인 기록 — **빈 리스트여도 반드시 싣는다**(판 ⑪ ①).
            # 칸이 없으면 「개입 0」이 아니라 「미측정」이고, 그 둘을 구별하지 못한 것이
            # 판 ⑧ 주장이 판 ⑩ 에서 강등된 이유다.
            "무인_기록": {
                "_규칙": ("개입 = 사람을 불러야 했던 사건. fail-open 은 개입이 아니라 "
                        "결정이다(사람을 안 부르고 진행). 둘을 섞지 않는다."),
                "개입_횟수": len(self.interventions),
                "개입_멈춤": sum(1 for x in self.interventions if x.get("멈췄나")),
                "개입": list(self.interventions),
                "결정_횟수": len(self.decisions),
                "결정_규칙없음": sum(1 for x in self.decisions
                                 if x.get("근거_규칙", "").startswith("(없음")),
                "결정": list(self.decisions),
                # 유료 호출이 0회면 `None` 이다 — 「사전등록 안 함」이 아니라 「잴 일이 없었다」.
                # 둘을 같은 값으로 두면 무료 재채점이 전부 이탈로 보인다.
                #
                # ⚠ **하네스(`gate.json`)와 같은 자리에 둔다.** 한쪽은 최상위, 한쪽은
                #   `무인_기록` 안이면 두 산출물을 나란히 못 읽는다 — 「이 컨셉은 개입 0에
                #   사전등록됨으로 돌았다」는 두 기록을 합쳐야만 나오는 문장이다.
                #   (판 ⑪ 의 테스트가 이 비대칭을 실제로 잡았다.)
                "사전등록": self.prereg,
            },

            "input": {"concept": to_dict(concept), "slots": slots_d},
            "verdict": to_dict(verdict),
            "report": to_dict(report),
            "metrics": dict(self.counters),

            # 규칙을 **값째로** 복사 — 참조로 두면 과거 기록이 소급해 바뀐다
            "rules": self.rules,
        }
        # extra 를 받아만 두고 안 쓰면 파생 실행의 출처(source_run)가 사라진다 — 실제로 그랬다
        result.update(extra or {})
        io.open(os.path.join(self.dir, "result.json"), "w", encoding="utf-8").write(
            json.dumps(result, ensure_ascii=False, indent=2, default=str))
        return result


# ══════════════════════════════════════════════════════════════
# LLM 호출 계측 — A블록에서만 쓰인다 (B·C 에 들어가면 절대규칙 1 위반)
# ══════════════════════════════════════════════════════════════
class Meter:
    """호출을 세기만 하고 응답은 손대지 않는다."""

    def __init__(self, client, run: Run):
        self._c, self._run = client, run

    def create(self, node: str, *, tag: dict | None = None, **kw):
        """`tag` 는 **API 로 안 나간다.** `**kw` 는 그대로 `responses.create` 로 흘러가므로
        원장에만 남길 것은 키워드 전용 인자로 받아야 한다. 값은 `a3_web_query` 노드의
        머리(`slot_id`·`trace_id` 등)로 쓰인다.
        """
        t = time.time()
        # **유료 진입점 ①** (판 ⑪ ②). 첫 호출에서만 잰다 — 매 호출 재면 델타가
        # 「마지막 호출 기준」이 되어 실행이 길수록 사전등록이 잘 지켜진 것처럼 보인다.
        if self._run.prereg is None:
            self._run.prereg = {**prereg_stamp(t), "진입점": "Meter.create", "node": node}
        try:
            r = self._c.responses.create(**kw)
        except Exception as e:
            self._run.count(f"llm.{node}.error")
            self._run.log(f"{node}.llm_error", {"error": f"{type(e).__name__}: {e}"},
                          status="error")
            raise
        self._run.count("llm.calls")
        self._run.count(f"llm.{node}.calls")
        self._run.count(f"llm.{node}.sec", round(time.time() - t, 1))
        try:
            u = r.usage
            self._run.count("llm.tokens_in", u.input_tokens or 0)
            self._run.count("llm.tokens_out", u.output_tokens or 0)
        except Exception:
            pass
        try:                       # web_search 1콜 안에서 검색어 여러 개가 나간다
            # 판 ㉟ ② — **세기만 하고 버리던 것을 남긴다.** r4 실측: 호출 22 · 질의 209.
            # 수만 있으면 두 판이 갈렸을 때 「질의가 달랐나 결과가 달랐나」를 못 가른다.
            # ⚠ 질의와 URL 을 **조인하지 않는다.** `_citations` 는 응답 전체를 훑으므로
            #   어느 URL 이 어느 질의에서 왔는지 복원 불가다 — 없는 조인은 다음 판의 오진이다.
            qs: list[str] = []
            raw: list[str] = []
            for it in r.output or []:
                if getattr(it, "type", "") == "web_search_call":
                    a = getattr(it, "action", None)
                    got = getattr(a, "queries", None)
                    if not got:                                  # 복수형이 없으면 단수형
                        one = getattr(a, "query", None)
                        got = [one] if one else None
                    # 세는 규칙은 판 ㉞ 이전과 **같다** — 못 읽어도 호출 1건당 1로 센다
                    self._run.count("llm.web_queries", len(got or [1]))
                    if got:
                        qs.extend(str(x) for x in got)
                    elif a is not None:                          # 모르는 모양은 접어서 남긴다
                        raw.append(str(a)[:200])
            if tag is not None:
                # 질의 0 도 관측이다 — 「모델이 검색을 안 했다」는 그 자체로 발견이다
                self._run.log("a3_web_query",
                              {**tag, "queries": qs, "n": len(qs),
                               **({"raw": raw} if raw else {})})
        except Exception:
            pass
        return r
