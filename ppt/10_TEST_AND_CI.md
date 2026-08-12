# 10. 테스트와 CI

---

## 1. 규모

| 영역 | 개수 | 세는 법 |
|---|---|---|
| AI 테스트 함수 | **487개** / 35파일 | `grep -rho 'def test_' ai/tests/` |
| research2 테스트 | **24파일** | ⚠ pytest로 안 모인다 (아래 §2) |
| 백엔드 테스트 | **469개** / 123파일 | ⚠ `@Test` 만 세면 `@TestConfiguration` 이 섞여 470 이 된다 |
| 프론트 테스트 파일 | **70개** | vitest |

⚠ **함수 수 ≠ 통과 수.** 실제 실행 기대치는 아래와 같고, 최신 리포트는 **M-11**로 남아 있다.

```powershell
cd ai       ;  python -m pytest -q                    # 기대: 131 passed / 0 skipped
cd backend  ;  .\gradlew.bat test --console=plain -q  # 기대: 275 / 0 failed
cd frontEnd ;  npm.cmd run test:baseline              # 판정은 이것으로 한다 (§3)
```

---

## 2. ⚠ research2 테스트는 pytest로 안 모인다

`ai/pytest.ini`가 명시적으로 배제한다.

```ini
testpaths = tests
norecursedirs = app/research/research2 .git __pycache__
```

주석에 이유가 적혀 있다:

> *"research2를 `app/research/research2/` 안으로 옮기면서 `python -m pytest -q`가
> 그쪽 `tests/*.py`까지 줍게 됐다. 그것들은 pytest 테스트가 아니라 **스크립트**다
> (끝에서 `sys.exit`) — 수집 단계에서 INTERNALERROR로 죽는다.
> research2 테스트는 설계상 **파일별로** 돈다: `python tests/test_step1.py`."*

**발표에서 "테스트 몇 개"를 말할 때 이 24파일을 빼먹거나 중복해서 세지 않도록 주의한다.**

---

## 3. ⭐ 프론트 게이트는 `test:run`이 아니라 `test:baseline`

`frontEnd/test-debt-baseline.json`

| 항목 | 값 |
|---|---|
| 소유자 | `frontend-maintainers` |
| **만료일** | **2026-09-30** |
| 허용 실패 목록 | **22건** |
| 정책 | *"New failures and stale allowlist entries fail CI. **The allowlist may only shrink from here.**"* |

**이 장치의 설계 의도**

1. 선행 실패를 **0으로 만들 때까지 CI를 못 돌리는 상황**을 피한다
2. 대신 **새 실패는 즉시 CI를 깬다**
3. 목록에 있는데 실제로는 통과하는 항목(stale)도 CI를 깬다 — **부채를 갚으면 목록에서 지워야 한다**
4. **목록은 줄어들기만 한다**
5. **만료일이 있다** — 부채가 영구화되지 않는다

> 발표 한 줄: **"테스트 부채를 숨기지 않고 만료일을 붙여 관리한다."**
> 이건 PIILOT 덱에 없던 종류의 성숙도다.

⚠ `CLAUDE.md`에 이 규칙이 빠져 있다. 판정은 `npm run test:run`이 아니라 `test:baseline`이다.

---

## 4. CI — `.github/workflows/ci.yml`

| 항목 | 값 |
|---|---|
| 트리거 | `push`(main) · `pull_request` · `workflow_dispatch` |
| 동시성 | `cancel-in-progress: true` — 같은 브랜치의 이전 실행을 취소 |
| 권한 | `contents: read` (최소 권한) |
| 잡 | **3개 병렬**, 각 timeout 15분 |

### 잡 구성

| 잡 | 실행 |
|---|---|
| **frontend** | `npm ci` → `lint` → **`test:baseline`** → `build` |
| **ai** | `pip install` → **계약 픽스처 검증**(`validate_fixtures.py`) → `pytest` |
| **backend** | gradle `test` + `postgresTest` |

⭐ **ai 잡이 테스트보다 먼저 계약 픽스처를 검증한다.**
`docs/contracts/fixtures/internal-ai-v1/`의 valid/negative 픽스처가 실제 계약과 맞는지 본다 —
**문서가 코드와 갈라지는 것을 CI가 막는 구조**다.

### CI 범위 밖 (정직하게)

| 항목 | 이유 |
|---|---|
| 실제 AI provider 호출 | 비용·키 |
| 법제처 실연동 | 외부 의존 |
| 전체 Docker E2E | 시간 |
| **CD / 배포** | **아예 없다** — workflow가 ci.yml 하나뿐 |

---

## 5. 테스트가 실제로 잡는 것 (예시)

발표에서 "테스트 469개 있습니다"보다 **무엇을 잡는지**가 강하다.

| 테스트 | 무엇을 고정하나 |
|---|---|
| `test_user_plan_beats_the_sample_stub` | **사용자 입력이 견본 스텁을 이긴다.** 예전에는 순서가 반대라 사용자가 채운 칸이 조용히 무시됐다 — 화면이 "입력을 받았다"고 해 놓고 안 쓰는 것은 거짓말이다 |
| `test_customer_relationship_stays_empty_until_the_schema_has_it` | **공백을 공백으로 고정한다.** 유추해서 채우면 프롬프트 규칙을 어기는 것이라, 비어 있다는 사실 자체를 검사로 박았다 |
| `test_generated_concept_fills_the_plan_cells` | 컨셉 → BM 계획 칸 파생 배선 |
| `test_no_duplicate_research2` | 엔진이 두 벌 존재하지 않음 |
| `test_failure_vocabulary` | 실패 코드가 화이트리스트 안에 있는가 |
| `test_bm_contract_parity` (9) | 파이썬·자바 계약 일치 |
| `test_pipeline_envelope` (13) | 결과 봉투 형식 |
| `test_design_score.py` 7번 | **설계 점수가 충분조건이 아님을 미리 못 박음** (pin-07이 4축 만점인데 성적표는 4/6) |

> ⭐ 2번과 8번이 특이하다 — **"안 되는 것"과 "한계"를 테스트로 고정**해 뒀다.
> 보통은 되는 것만 테스트한다.

---

## 6. 품질 게이트 문서

`docs/quality/` — `QUALITY_GATES.md` · `TEST_STRATEGY.md` · `STABLE_CORE_REGRESSION.md` ·
`ACCEPTANCE_CRITERIA.md`

⚠ **전부 `Implementation Status: PARTIAL`이다.** 게이트 표는 있으나 실측 수치가 붙어 있지 않다.
발표에서 이 문서들을 "완비된 품질 체계"로 소개하면 과장이 된다.
**실제로 돌고 있는 게이트는 `ci.yml` 3잡과 `test:baseline` 하나다.**
