"""Δ 분해·λ·MDE — 검증된 집계의 이식본.

원본: `combine_csv/_build/g3e/g3e_aggregate.py` (= `g3d_08_gate.analyze` 이식본).
그 이식본은 G3D 원장 19,994셀을 재집계해 `g3d_agg.json` 과 16/16쌍, 10개 수치 필드 +
분류 카운트까지 오차 1e-9로 일치함을 실측했다. **여기서 수식을 고치면 그 증명이 끊긴다.**

    Δ_fwd = (1/n_p)Σ s(d_i^fwd),  s(X)=+1, s(Y)=−1
    A_p(내용) = (Δ_fwd+Δ_rev)/2 = Δ_avg      P_p(위치) = (Δ_fwd−Δ_rev)/2
    c_i = (s_fwd+s_rev)/2 ∈ {−1,0,+1};  λ_p = E[c²] = (content_X+content_Y)/n_p
    Var_p = λ_p − Δ_avg²,  sd_p = √(Var_p/n_p),  MDE_p = 2.80 × sd_p

위치편향은 **제거되는 것이 아니라 분리되는 것**이다. 양방향 전수 제시로 P_p를 따로 뽑아
Δ_avg에서 빼낸다. G3D 실측 위치편향은 P=+1.0000 — 한 방향만 물으면 답이 뒤집힌다.
"""

from collections import Counter, defaultdict

from app.twin.stimuli import DIRECTIONS, decide_adaptive, to_xy

Z_MDE = 2.80          # 양측 α=.05, 검정력 80%
Z_CI = 1.96           # 신뢰구간·Wilson 하한

# 만장일치 쌍(λ=1, |Δ|=1)은 Var = λ − Δ² = 0 이라 MDE도 0으로 퇴화한다. 그대로 두면
# 응답자 한 명 차이에도 "차이 있음"이 된다. 정규근사가 p=1에서 무너지는 자리라 관문이
# 아니라 산식의 한계다. 무사건 상한(rule of three)으로 바닥을 깐다:
# 관측 0회일 때 비율 95% 상한이 3/n 이고, 이탈 1명이 Δ를 2/n 움직이므로 6/n.
MDE_FLOOR_K = 6.0


def wilson_lower(k: int, n: int, z: float = Z_CI) -> float:
    """이항 비율의 Wilson 95% 하한."""
    if n <= 0:
        return 0.0
    p = k / n
    denominator = 1 + z * z / n
    center = p + z * z / (2 * n)
    radius = z * ((p * (1 - p) / n + z * z / (4 * n * n)) ** 0.5)
    return max(0.0, (center - radius) / denominator)


def mde_effective(mde_p: float | None, n_p: int) -> float | None:
    """만장일치 퇴화를 막은 유효 측정 한계."""
    if not n_p:
        return None
    return max(mde_p or 0.0, MDE_FLOOR_K / n_p)


def classify_subjects(rows: list[dict], pair_id: str) -> dict[str, str]:
    """응답자 한 명씩의 분류. `analyze` 가 세는 것과 **같은 규칙**을 사람 단위로 낸다.

    ⚠ `analyze()` 를 고쳐서 여기에 쓰지 않는다. 그 함수에는 G3D 원장 19,994셀을
    16/16쌍·오차 1e-9 로 재현한다는 증명이 걸려 있고, 반환 모양을 바꾸면 그 증명이 끊긴다.
    규칙이 갈리지 않게 **분류 분기는 아래 한 곳만 보고 고친다** —
    `analyze` 의 `cls` 계산과 문장이 같아야 한다.

    반환은 «화면에 앉힐 사람»을 고르는 데 쓴다. 위치응답(`position_driven`·`anti_position`)은
    내용이 아니라 순서를 보고 고른 사람이라, 인터뷰 인용은 그들을 빼고 고른다.
    """
    by = defaultdict(lambda: defaultdict(dict))
    for r in rows:
        if r["pair_id"] == pair_id:
            by[r["subject"]][r["direction"]][r["rep"]] = to_xy(r.get("choice"), r["direction"])
    decided = {s: {d: decide_adaptive(dirs.get(d, {})) for d in DIRECTIONS}
               for s, dirs in by.items()}

    out: dict[str, str] = {}
    for subject, d in decided.items():
        f, v = d["fwd"], d["rev"]
        if f not in ("X", "Y") or v not in ("X", "Y"):
            out[subject] = "undecided"
        elif f == "X" and v == "Y":
            out[subject] = "position_driven"
        elif f == "Y" and v == "X":
            out[subject] = "anti_position"
        elif f == "X":
            out[subject] = "content_X"
        else:
            out[subject] = "content_Y"
    return out


def analyze(rows: list[dict], pair_id: str) -> dict:
    """쌍 하나의 Δ 분해 + λ_p 실측. `rows` 는 ok=True 만 넘어와야 한다."""
    by = defaultdict(lambda: defaultdict(dict))
    for r in rows:
        if r["pair_id"] == pair_id:
            by[r["subject"]][r["direction"]][r["rep"]] = to_xy(r.get("choice"), r["direction"])
    decided = {s: {d: decide_adaptive(dirs.get(d, {})) for d in DIRECTIONS}
               for s, dirs in by.items()}

    # 대응표본 — 양방향 모두 확정된 응시자만 분모에 든다
    paired = [s for s, d in decided.items()
              if d["fwd"] in ("X", "Y") and d["rev"] in ("X", "Y")]
    n_p = len(paired)
    sign = {"X": 1, "Y": -1}
    d_fwd = sum(sign[decided[s]["fwd"]] for s in paired) / n_p if n_p else None
    d_rev = sum(sign[decided[s]["rev"]] for s in paired) / n_p if n_p else None
    delta_avg = (d_fwd + d_rev) / 2 if n_p else None
    position = (d_fwd - d_rev) / 2 if n_p else None

    cls = Counter()
    for _, d in decided.items():
        f, v = d["fwd"], d["rev"]
        if f not in ("X", "Y") or v not in ("X", "Y"):
            cls["undecided"] += 1
        elif f == "X" and v == "Y":
            cls["position_driven"] += 1        # 양방향 모두 A 위치를 골랐다
        elif f == "Y" and v == "X":
            cls["anti_position"] += 1          # 양방향 모두 B 위치
        elif f == "X":
            cls["content_X"] += 1
        else:
            cls["content_Y"] += 1

    k_content = cls["content_X"] + cls["content_Y"]
    lam = k_content / n_p if n_p else None
    lam_lo = wilson_lower(k_content, n_p) if n_p else None
    var = (lam - delta_avg * delta_avg) if n_p else None
    sd = ((var / n_p) ** 0.5) if (n_p and var is not None and var > 0) else 0.0
    mde = Z_MDE * sd if n_p else None

    return {"pair_id": pair_id, "n_subjects": len(decided), "n_p": n_p,
            "delta_fwd": d_fwd, "delta_rev": d_rev, "delta_avg": delta_avg,
            "position": position, "lambda_p": lam, "lambda_wilson_lo": lam_lo,
            "var_p": var, "sd_p": sd, "mde_p": mde, "cls": dict(cls)}


def verdict(stats: dict) -> dict:
    """집계를 «어느 쪽이 이겼나 / 못 잰다»로 옮긴다.

    **크기를 말하지 않는다.** 방향과 신뢰구간까지만이다 — SSR 앵커가 명목폭의 52%로
    압축돼 있고 트윈의 극단 회피가 같은 방향으로 겹쳐, 크기 주장은 구조적으로 못 한다.
    """
    n_p = stats["n_p"]
    if not n_p:
        return {"winner": None, "measurable": False,
                "reason": "양방향 모두 확정된 응답자가 없다."}

    delta = stats["delta_avg"]
    mde = mde_effective(stats["mde_p"], n_p)
    half = Z_CI * stats["sd_p"]
    interval = {"low": delta - half, "high": delta + half}

    if abs(delta) <= mde:
        return {"winner": "TIE", "measurable": False, "confidenceInterval": interval,
                "reason": (f"차이(|Δ|={abs(delta):.3f})가 이 표본의 측정 한계"
                           f"(MDE={mde:.3f}) 이하다. 방향을 말할 수 없다 — "
                           "«차이 없음»이 아니라 «못 잼»이다. 표본을 키우면 잴 수도 있다.")}

    return {"winner": "X" if delta > 0 else "Y", "measurable": True,
            "confidenceInterval": interval,
            "reason": (f"방향이 측정 한계를 넘었다(|Δ|={abs(delta):.3f} > MDE={mde:.3f}). "
                       "크기는 말하지 않는다 — 이 파이프라인은 방향과 구간까지만 낸다.")}
