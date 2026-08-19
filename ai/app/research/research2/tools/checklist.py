# -*- coding: utf-8 -*-
"""**모범답안 채점** — 사람이 만든 원본 보고서의 사실 목록과 산출을 대조한다. **LLM 0회.**
(판 ㊵ 항목 1 · `docs/market-research-redesign/MEASUREMENT.md`)

    python tools/checklist.py --extract ../../../../docs/market-research-redesign/market-report.html
    python tools/checklist.py runs-generated/p39-secFULL/publish.json

`--extract` 는 **체크리스트를 만들 때 한 번만** 쓴다. HTML 의 구조(스탯 상자·표·문단)를 그대로
따라가며 `숫자+단위` 후보를 뽑고, **무엇의 수인가(subject)를 구조에서 끌어온다** —
표는 「행 이름 + 열 머리」, 스탯 상자는 라벨. 문단은 앞뒤 40자.
사람이 그 초안을 줄여 `data/reference_facts.json` 으로 굳히고, **이후 손대지 않는다.**

채점은 두 단으로 나뉜다. **자동은 「적중 후보」까지만 낸다.**

    ① 자동 — 산출의 `number_raw + unit_raw` 를 정규화해 값으로 맞춘다
    ② 사람 — 적중 후보의 `subject` 를 눈으로 대조한다
       ⚠ 숫자만 맞으면 안 된다. 실측: `50%` 가 「자립준비청년 취업률」로도 걸린다

**모집단은 「절 머리에 서는 것」 하나로 고정한다**(`publish_gate.머리인가`). `--population` 은
진단용이며, 판정에 쓰지 않는다 — 눈금이 갈리면 처치를 못 잰다.

채점할 때마다 **게이트 규칙 버전**을 같이 찍는다. 안 적으면 다음 판과 비교가 안 된다.
"""
from __future__ import annotations

import argparse, io, json, os, re, sys
from collections import Counter

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
for _p in (ROOT, HERE, os.path.join(ROOT, "adapters")):
    if _p not in sys.path:
        sys.path.insert(0, _p)

import publish_gate as PG          # noqa: E402 — 「무엇이 실렸나」의 정본은 거기 하나다

REF = os.path.join(ROOT, "data", "reference_facts.json")
RULES = os.path.join(ROOT, "rules", "publish.v1.json")

# ── 값 정규화 ────────────────────────────────────────────────
# 한국어 수 표기는 자릿말이 붙어 온다. 「6조 8,000억」은 6e12 + 8000e8 이다.
MAG = {"조": 1e12, "억": 1e8, "만": 1e4, "천": 1e3}
_NUM = re.compile(r"(\d+(?:\.\d+)?)\s*([조억만천]?)")
# 범위·나열을 가르는 자리. 「3,900~6,500」 · 「97~99%」 · 「n=5,c=0」
_SPLIT = re.compile(r"[~〜–—/·,;]| 및 | 또는 ")


def values(text: str) -> set:
    """문자열에서 **비교 가능한 수**를 전부 뽑는다. 자릿말은 더한다.

    ⚠ 부호는 버린다 — `−20.2%` 와 `+20.2%` 를 같은 값으로 본다. 방향까지 자동으로 가르면
    표기(`감소 20.2%`)에 걸려 참인 적중을 놓친다. **방향은 사람이 subject 와 함께 본다.**
    """
    out = set()
    for part in _SPLIT.split((text or "").replace(",", "")):
        got, acc, plain = False, 0.0, None
        for m in _NUM.finditer(part):
            n, mag = float(m.group(1)), m.group(2)
            got = True
            if mag:
                acc += n * MAG[mag]
            elif plain is None:
                plain = n
            else:                      # 자릿말 없는 수가 둘 이상이면 각각이 별개 값이다
                out.add(plain)
                plain = n
        if not got:
            continue
        if acc and plain is not None and plain < 1000:
            acc += plain              # 「804만 5천」이 아니라 「3조 6,745억」류의 꼬리
            plain = None
        if acc:
            out.add(acc)
        if plain is not None:
            out.add(plain)
    return out


# 단위는 **갈래로만** 본다. 「원」과 「억원」을 다른 단위로 세면 전부 어긋난다.
_FAM = [
    ("퍼센트", ("%", "％", "%p", "퍼센트", "포인트", "p")),
    ("외화", ("usd", "$", "달러", "억usd", "억 usd")),
    ("통화", ("원", "억원", "만원", "조원", "억 원", "만 원")),
    ("배수", ("배", ":1", "배수")),
    ("온도", ("℃", "도")),
]


def family(unit: str) -> str:
    u = (unit or "").strip().lower()
    if not u:
        return ""
    for name, keys in _FAM:
        if any(k in u for k in keys):
            return name
    return "개수"          # 개·종·명·건·가구·회·개월·곳 …


def compatible(a: str, b: str) -> bool:
    """한쪽이 비면 통과시키되 **표시를 남긴다**(호출부에서 「단위없음」으로 찍는다)."""
    fa, fb = family(a), family(b)
    return not fa or not fb or fa == fb


# ── 후보 추출 (체크리스트 만들 때 1회) ───────────────────────
_TAG = re.compile(r"<[^>]+>")
_HASNUM = re.compile(r"\d")


def _txt(h: str) -> str:
    return re.sub(r"\s+", " ", _TAG.sub("", h)).replace("&nbsp;", " ").replace("&amp;", "&").strip()


def extract(path: str) -> list:
    h = io.open(path, encoding="utf-8").read()
    h = re.sub(r"<style.*?</style>|<script.*?</script>", "", h, flags=re.S)
    out = []
    for sec in re.split(r"(?=<section)", h)[1:]:
        m = re.search(r"<h2>(.*?)</h2>", sec, re.S)
        절 = _txt(m.group(1)) if m else "?"
        for lab, big, sub in re.findall(
                r'class="lab"[^>]*>(.*?)</div>\s*<div class="big"[^>]*>(.*?)</div>'
                r'(?:\s*<p class="sub"[^>]*>(.*?)</p>)?',
                sec, re.S):
            out.append({"절": 절, "값": _txt(big), "주어": _txt(lab), "곁": _txt(sub or ""), "출처": "스탯"})
        for tbl in re.findall(r"<table>(.*?)</table>", sec, re.S):
            head = [_txt(x) for x in re.findall(r"<th[^>]*>(.*?)</th>", tbl, re.S)]
            for tr in re.findall(r"<tr>(.*?)</tr>", tbl, re.S):
                cells = [_txt(x) for x in re.findall(r"<td[^>]*>(.*?)</td>", tr, re.S)]
                if not cells:
                    continue
                for i, c in enumerate(cells[1:], 1):
                    if not _HASNUM.search(c):
                        continue
                    col = head[i] if i < len(head) else ""
                    out.append({"절": 절, "값": c, "주어": f"{cells[0]} · {col}".strip(" ·"),
                                "곁": " | ".join(cells), "출처": "표"})
        body = re.sub(r"<table>.*?</table>|class=\"lab\">.*?</p>", "", sec, flags=re.S)
        for s in re.split(r"(?<=[.。])\s+|<br\s*/?>", _txt(body)):
            for m in re.finditer(r"[\d][\d,.~]*\s*(?:조|억|만|천)?\s*(?:원|%|%p|개|종|명|건|가구|회|배|℃|USD)?", s):
                v = m.group(0).strip()
                if not _HASNUM.search(v) or len(v) < 2:
                    continue
                out.append({"절": 절, "값": v, "주어": s[max(0, m.start() - 40):m.end() + 20],
                            "곁": "", "출처": "문단"})
    return out


# ── 채점 ─────────────────────────────────────────────────────
def load_output(path: str, population: str) -> list:
    d = json.load(io.open(path, encoding="utf-8"))
    items = [it for r in d.get("문서별", []) for it in r["items"]]
    if population == "all":
        return items
    ok = [it for it in items if it.get("채택")]
    if population == "verified":
        return ok
    # ⚠ **모집단은 «절 머리»다** (판 ㊹ 3단계). 서랍(`밖`)까지 넣으면 모집단이 사실상
    #   전체가 되어 **before/after 를 정의상 못 재게 된다** — 처치를 재려고 만든 잣대가
    #   처치 때문에 무의미해지는 자리다.
    return [it for it in ok if PG.머리인가(it)]


def score(refs: list, items: list) -> tuple:
    """(적중 후보, 못 맞힌 것). **자동은 여기까지다** — subject 대조는 사람이 한다."""
    idx = []
    for it in items:
        idx.append((values(str(it.get("number_raw") or "")), it))
    hits, miss = [], []
    for r in refs:
        rv = values(str(r["값"]))
        cand = []
        for vs, it in idx:
            if rv & vs and compatible(r.get("단위", ""), str(it.get("unit_raw") or "")):
                cand.append(it)
        (hits if cand else miss).append((r, cand))
    return hits, miss


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("output", nargs="?", help="publish.json 경로")
    ap.add_argument("--extract", default="", help="원본 HTML → 후보 초안 (체크리스트 만들 때 1회)")
    ap.add_argument("--population", default="published",
                    choices=("published", "verified", "all"),
                    help="published=화면에 실린 것(**판정용 · 기본**) · 나머지는 진단용")
    ap.add_argument("--json", default="", help="적중 후보를 파일로 (사람이 subject 대조할 때)")
    a = ap.parse_args()

    if a.extract:
        c = extract(a.extract)
        print(f"후보 {len(c)}건 (스탯 {sum(1 for x in c if x['출처']=='스탯')} · "
              f"표 {sum(1 for x in c if x['출처']=='표')} · "
              f"문단 {sum(1 for x in c if x['출처']=='문단')})")
        uniq = {}
        for x in c:
            uniq.setdefault((x["절"], x["값"], x["주어"][:30]), x)
        print(f"고유 {len(uniq)}건")
        out = os.path.join(ROOT, "data", "reference_facts.draft.json")
        io.open(out, "w", encoding="utf-8").write(
            json.dumps(list(uniq.values()), ensure_ascii=False, indent=1))
        print(f"초안: {out}  — 사람이 줄여 reference_facts.json 으로 굳힌다")
        return 0

    if not a.output:
        ap.error("publish.json 경로가 필요하다 (또는 --extract)")

    ref = json.load(io.open(REF, encoding="utf-8"))
    refs = ref["facts"]
    items = load_output(a.output, a.population)
    hits, miss = score(refs, items)

    규칙 = json.load(io.open(RULES, encoding="utf-8")).get("version", "?")
    print(f"체크리스트 {ref['version']} · {len(refs)}개 | 게이트 규칙 {규칙} | "
          f"모집단 {a.population} {len(items)}건")
    if a.population != "published":
        print("⚠ 판정용 모집단이 아니다. 이 수를 기준선으로 적지 마라")

    print(f"\n**적중 후보 {len(hits)} / {len(refs)}**  — 이 수는 아직 점수가 아니다")
    절 = Counter(r["절"] for r, _ in hits)
    빈절 = Counter(r["절"] for r, _ in miss)
    print(f"\n  {'절':<14}{'적중후보':>8}{'못맞힘':>8}")
    for s in sorted(set(절) | set(빈절), key=lambda s: -절[s]):
        print(f"  {s:<14}{절[s]:>8}{빈절[s]:>8}")

    print("\n적중 후보 — **주어를 사람이 대조한다**")
    for r, cand in hits:
        print(f"\n  [{r['id']}] {r['값']}{r.get('단위','')}  «{r['주어']}»  ({r['절']})")
        for it in cand[:4]:
            u = str(it.get("unit_raw") or "")
            표 = "" if u else "  ⚠단위없음"
            print(f"      → {it.get('number_raw')}{u} «{it.get('subject')}»{표}")
        if len(cand) > 4:
            print(f"      … {len(cand)-4}건 더")

    if a.json:
        io.open(a.json, "w", encoding="utf-8").write(json.dumps(
            [{"기준": r, "후보": [{"number_raw": it.get("number_raw"),
                                 "unit_raw": it.get("unit_raw"),
                                 "subject": it.get("subject"),
                                 "section": it.get("section"),
                                 "게재": it.get("게재")} for it in c]}
             for r, c in hits], ensure_ascii=False, indent=1))
        print(f"\n기록: {a.json}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
