# 99. 앞으로 만들어야 할 자료 — 관리 대장

- 작성: 2026-08-12
- 성격: **이 저장소의 자료만으로는 쓸 수 없는 것들의 목록.** 발표자료의 빈칸이 어디인지 고정한다.
- 규칙: 채워지면 `상태`를 `DONE`으로 바꾸고 `산출물 경로`를 적는다. **행을 지우지 않는다** —
  "무엇이 없었는지"가 기록으로 남아야 나중에 같은 구멍을 다시 판다.

> 이 문서를 가장 먼저 쓴 이유: 없는 것을 먼저 고정해야 나머지 13개 문서가 정직해진다.
> 자료가 없는 칸을 그럴듯한 문장으로 메우기 시작하면 발표 전체의 신뢰가 무너진다.

---

## A. 기획 파트 — 덱의 앞부분을 여는 열쇠

| # | 산출물 | 왜 지금 못 만드나 | 필요한 행동 | 담당 | 상태 | 산출물 경로 |
|---|---|---|---|---|---|---|
| **M-01** | **문제 근거 자료집** (뉴스·통계 + 출처) | `docs/` 전역을 뒤졌으나 **이 제품의 문제 정의를 뒷받침하는 근거 0건.** 검색에 걸리는 시장 수치는 전부 *제품이 분석 대상으로 삼는 견본 컨셉*(뷰티/펫/HMR)의 것이지 우리 문제 정의가 아니다 | 외부 조사. 최소 5건: ① 신사업·창업 실패율 ② 시장조사 외주 단가·소요기간 ③ 사업계획 반려·재작성 비율 ④ 근거 없는 의사결정의 비용 ⑤ 생성형 AI 답변의 출처 부재 문제. **매체·발행일·URL 3종 세트 필수** | | TODO | |
| **M-02** | **기존 방식 / 경쟁 조사서** | 0건. 그렙 히트는 전부 제품이 *생성하는* `competitors` 필드 배선 | 지금 사람들이 이 일을 어떻게 하는가 3~4가지와 각각의 한계: ① 컨설팅 외주 ② 직접 데스크리서치 ③ ChatGPT 단발 질의 ④ 사내 템플릿. → `01_SERVICE_CONCEPT.md` 대조표 좌열로 들어간다 | | TODO | |
| **M-03** | **팀 역할 분담표** | 사람 이름 기반 R&R 문서 0건. `docs/governance/`의 "Owner"는 문서 소유권 롤이지 팀원이 아니다 | 이름 / 파트(AI·BE·FE·Infra) / 담당 기능 2~3개. **사용자만 아는 정보** | | TODO | |
| **M-10** | 기대효과 산출 근거 | "근거 없는 % 금지" 원칙(→ `00_DECK_OUTLINE.md`) | 정량 효과를 주장할 거면 계산식·가정·출처를 별첨에. 못 대겠으면 **정성 서술로 낮춘다** | | TODO | |
| **M-15** | **제품명 확정** | 이름이 갈려 있다 — `README.md`는 "New Pipeline Platform"(저장소 설명에 가깝다), `PRODUCT_VISION.md`는 "Venture Verify"(단, 문서 상태가 `TARGET_CANONICAL/NOT_STARTED`). 코드에는 제품명 상수가 없다 | 팀이 이름을 정한다. PIILOT은 `PII + Pilot`처럼 **이름의 뜻이 곧 제품 설명**이라 표지 한 장으로 제품이 전달됐다. 뜻을 풀 수 있는 이름을 고를 것 | | TODO | |

## B. 그림 자산

| # | 산출물 | 왜 지금 못 만드나 | 필요한 행동 | 담당 | 상태 | 산출물 경로 |
|---|---|---|---|---|---|---|
| **M-04** | **제품 화면 캡처 10장** | 저장소 전체 이미지 8개뿐이고 그중 제품 화면은 **0장** (`hero.png`·favicon·디자인 참고 png 2장뿐) | `docker compose up` 후 `11_DEMO_SCRIPT.md`의 목록대로 캡처 → `ppt/assets/` | | TODO | |
| **M-05** | **ERD 다이어그램 이미지** | 소재(마이그레이션 21개·테이블 **57개**)는 있으나 그림 파일이 없음. 과거 ERD 문서는 `DOMAIN_OVERVIEW.md`가 대체하며 제거됨 | `04_DATA_MODEL.md`의 작도 지시서대로 작도 | | TODO | |
| **M-06** | **시스템 구성도 이미지** | mermaid 텍스트만 존재(`AS_BUILT_ARCHITECTURE.md` 16–25행) | 렌더 + 외부 연동 4종(KOSIS·DART·Tavily·법제처) 얹기 | | TODO | |
| **M-12** | 파이프라인 다이어그램 이미지 | `05_AI_PIPELINE.md`에 텍스트 단계도만 | 시장조사 5단계 + 하네스 루프를 작도 | | TODO | |

## C. 수치 — 재실행하면 나오는 것 (전부 LLM 0회 = 무료)

| # | 산출물 | 왜 지금 못 만드나 | 필요한 행동 | 담당 | 상태 | 산출물 경로 |
|---|---|---|---|---|---|---|
| **M-07** | **funnel Before/After 한 쌍** | 저장된 것은 `funnel_before.json` 1건뿐. 게다가 그 파일은 `"_원천": "note 역산"` — **직접 측정이 아니라 기록에서 역산한 값**이다 | `tools/funnel.py`를 개선 전/후 원장에 각각 실행해 **직접 측정판**을 만든다. 판㉛ 기록의 54.1% → 87.3%을 재현 | | TODO | |
| **M-08** | scorecard·eval 결과 파일 | 두 도구가 표준출력으로만 찍어 산출물이 안 남는다 | 대표 원장에 재실행 후 JSON으로 저장. `07_MEASUREMENTS.md` 빈칸을 채운다 | | TODO | |
| **M-11** | 최신 테스트 실행 리포트 | `backend/build/test-results/`에 12건짜리 1개만 남아 있음 | ai pytest · backend gradle · frontEnd `test:baseline` 3영역 1회 실행 후 리포트 보관 | | TODO | |
| **M-13** | 유료 전 구간 실측 | 시장조사 전 구간(A1~A3)이 아직 제품 경로에 안 붙었고 `SKIPPED / degradation: NOT_WIRED`로 값에 남아 있음 | 유료 실행 1회. **비용이 드는 유일한 항목** | | TODO | |

## D. 결정이 필요한 것

| # | 산출물 | 왜 지금 못 만드나 | 필요한 행동 | 담당 | 상태 | 산출물 경로 |
|---|---|---|---|---|---|---|
| **M-09** | 모델 선정 비교표 | `docs/product/OPEN_DECISIONS.md` **OD-008이 `DEFERRED`** — 모델/provider/library 선택을 각 구현 slice 진입 전으로 연기해 둔 상태. 코드에는 `gpt-4o-mini`·`gpt-4o`·`gpt-5.4-nano`가 하드코딩돼 있으나 **왜 그것인지 측정한 기록이 없다** | 둘 중 택1: ⓐ 지금 비교·측정해서 결정한다 ⓑ **"의도적으로 미결로 두었다"를 그대로 발표한다**(provider-neutral 계약이라는 설계 근거가 실제로 있음). ⓑ가 정직하고 비용이 0이다 | | TODO | |
| **M-14** | 발표 범위 확정 | 문서 간 제품 범위가 불일치. `CURRENT_BASELINE.md`(2026-08-04)는 "적격 Concept 3개 표시에서 끝난다", `AS_BUILT §2`(2026-08-11 실스택 확인)는 **8단계 여정** | 발표에 쓸 "현재 범위"를 **하나로 고정**한다. 실측이 최신인 8단계를 권장 | | TODO | |

---

## E. 자료는 있으나 손봐야 하는 것 (문서-코드 불일치)

발표에서 그대로 인용하면 **사실 오류**가 되는 항목. `03_ARCHITECTURE.md`는 이미 교정본으로 썼다.

| # | 어디 | 문서가 말하는 것 | 코드 실제 | 조치 |
|---|---|---|---|---|
| X-01 | `AS_BUILT_ARCHITECTURE.md` §6 | 마이그레이션 `V1`–`V36` + Java 마이그레이션 V5·V10, "다음 빈 버전 V37" | **V1–V21 (21개 파일)**. `backend/src/main/java/db/migration/` 디렉터리 **없음** | `03`·`04`에 교정본 반영함. 원문서도 고칠 것 |
| X-02 | `CLAUDE.md` §3 | "V13–V16 컨셉 포트폴리오 v2. **다음 빈 버전은 V17**" | V17–V21이 이미 존재(재무·BM계획·경쟁씨앗). **다음은 V22** | `CLAUDE.md` 갱신 필요 |
| X-03 | `AS_BUILT_ARCHITECTURE.md` §2 | 라우터 정본 `frontEnd/src/app/router/AppRouter.jsx` | 실제는 `app/**routing**/AppRouter.jsx` | 경로 한 글자 |
| X-04 | `AS_BUILT_ARCHITECTURE.md` §5 | `app/api/tasks.py` · `app/api/marketing.py` · `app/services/task_service.py` · `banner_service.py` | **전부 미존재.** 실제 `ai/app/api/`는 `errors.py`·`executions.py`·`financial.py` 3개, `ai/app/services/`는 `journey_provider.py` 1개 | §5·§7 재작성 필요 |
| X-05 | `AS_BUILT_ARCHITECTURE.md` §4·§7 / `CURRENT_BASELINE.md` | "TaskType 13종" | **18종** (`TaskType.java`) | 숫자 정정 |
| X-06 | `CURRENT_BASELINE.md` | "Flyway는 `V1__baseline_schema.sql` 하나" | 실제 baseline 파일명은 `V1__new_pipeline_baseline.sql`이고 그 위에 V2–V21 | 정정 |
| X-07 | `AS_BUILT_ARCHITECTURE.md` §2 | "`AppRouter.jsx` 82–134행이 옛 경로(`plan/`·`structured-plan`·`review/*`·`validate/*`·`report`)를 전부 journey로 리다이렉트한다" | **그 경로가 하나도 없다**(grep 0건). 파일은 121줄이고 `Navigate`는 1곳뿐. 프론트 feature 폴더도 `structured-plan`·`documents`·`legal-review`·`personas`·`validation`·`marketing`이 **삭제**됐고 `feasibility`·`financial`·`report` 3개만 남음. 백엔드 v1 레거시 컨트롤러도 grep 0건 | 원문서 §2 재작성. **"죽은 코드가 많다"고 발표하면 사실과 다르다** |
| X-08 | `README.md` / `AS_BUILT §3-3` | "적격 컨셉 **5개**, 최대 **15개** 후보" / "목표 **3개**, 교체 2라운드, 후보 상한 **9개**" | 현행 모듈은 요청 파라미터 **`maxConcepts` 1~5(기본 5)**. `CONCEPT_TARGET_ELIGIBLE_COUNT`는 **코드에 없다**(grep 0건). 옛 `ConceptFactoryLimits`는 지금 `MAX_INSPECTED_CANDIDATES=20`이고 그 팩토리 자체가 죽은 코드 | `01`·`02`에 교정본 반영함 |
| **X-09** ⚠ | `AS_BUILT §4` 규칙 5 · `CLAUDE.md` §5 규칙 2 | **"task input에 부동소수점 금지 — canonical hash가 거부한다. 런타임에만 터진다"** | **거짓. 유한 소수는 허용된다.** `CanonicalInputHasher.canonicalNumber()`는 `isFloatingPointNumber() && !Double.isFinite()`일 때만 던지고, 유한 값은 BigDecimal로 정규화한다. 메서드 주석이 *"finite JSON numbers are interpreted as decimal values"*라고 **의도적 지원**임을 밝힌다. AI 서버 오류 메시지도 `"canonical JSON with finite numbers"`. 문서가 인용한 문자열 `"floating-point JSON numbers are not canonical task input"`은 **코드에 없다**(git 이력상 과거엔 있었고 제거됨) | **가장 위험한 항목.** 이 지침을 믿고 정수 basis point로 우회 설계한 코드가 있을 수 있다. `03` 교정 반영함 |
| **X-10** | — (아무 문서도 안 적음) | — | **`AppRouter.jsx`가 두 벌 있다.** `app/routing/AppRouter.jsx`(실사용 — `App.jsx`가 import)와 `app/router/AppRouter.jsx`(12KB, 내용 다름, **import 0곳**). 후자는 죽은 중복본 | 삭제 후보. "죽은 코드가 정리됐다"(X-07)는 진술을 이만큼 보정해야 한다 |
| **X-11** | `AS_BUILT §3-2·§9-2` | "`TaskRunWorker.execute()`가 트랜잭션을 확인한다", "`TaskRunWorker.rejectForbiddenFields()`", "`TaskRunWorker.validateResult`는 3개 TaskType만 안다" | **`TaskRunWorker` 클래스가 존재하지 않는다.** 트랜잭션 가드는 `journey/`의 **3개 클래스**(MarketResearchWorker · TwinSurveyWorker · TwinSurveyStimulusDraftService), 금칙 필드 검사는 **2개 클래스**(MarketResearchWorker · TwinSurveyWorker)에만 있다 | `03` 교정 반영. **"전역 강제"라고 발표하면 과장** |

---

## F. 자료 유실 위험 ⚠

발표자료의 원천이 될 실측 산출물 대부분이 **git에 없다.**

| 대상 | 실태 | 대응 |
|---|---|---|
| `ai/app/research/research2/runs/` · `runs-generated/` | 파일 **1,068개** 중 git 추적은 **11개**(견본 컨셉 3개 원장). `.gitignore`가 의도적으로 배제 — 232MB이고 재생성 가능하기 때문 | **정책은 옳다. 따라서 수치를 파일 경로로 가리키지 말고 `07_MEASUREMENTS.md` 안에 값을 옮겨 적는다.** 이 원칙은 이미 적용함 |
| `시장조사/` · `문서/` · `법률/` | git 미추적. 다른 팀원 클론에는 **아예 없다** | 인용할 내용은 `ppt/` 문서 본문으로 옮겨 적는다. 판㉛·판㉜ 기록은 `docs/CONCEPT_TO_RESEARCH_HANDOFF.md`(추적됨)에 정본이 있어 그쪽을 인용함 |
| `model/` (K-Means 페르소나) | git 미추적 | `07_MEASUREMENTS.md`에 수치를 옮겨 적음 |

---

## 진행 요약

| 구분 | 건수 | TODO | DONE |
|---|---|---|---|
| A. 기획 | 5 (M-01·02·03·10·15) | 5 | 0 |
| B. 그림 | 4 (M-04·05·06·12) | 4 | 0 |
| C. 수치 | 4 (M-07·08·11·13) | 4 | 0 |
| D. 결정 | 2 (M-09·14) | 2 | 0 |
| **합계** | **15** | **15** | **0** |

### 착수 우선순위

1. **M-01 · M-02** — 없으면 덱 앞부분(문제 정의·서비스 제안)이 아예 안 만들어진다
2. **M-04** — "만들었다"의 증명. 기동만 하면 되는데 아무도 안 찍어 놨다
3. **M-15** — 표지가 안 나온다
4. **M-03** — 사용자만 아는 정보라 대신 만들 수 없다
5. **M-07 · M-08 · M-11** — 전부 무료. 시간만 있으면 된다
6. M-05 · M-06 · M-12 — 작도
7. M-09 · M-14 — 결정
8. **M-13** — 유일하게 돈이 든다. 마지막에

E(문서-코드 불일치 **11건**)와 F(유실 위험 3건)는 별도 관리 — E는 `ppt/` 문서에 교정본을 반영했고,
F는 "값을 옮겨 적는다"는 원칙으로 이미 대응했다.

> ⚠ **E절이 이 작업의 부산물 중 가장 값어치 있다.** `ppt/` 문서를 쓰면서 주장을 하나씩 코드와
> 대조한 결과 **원본 문서 11곳이 틀렸다.** 특히 **X-09(부동소수점 금지)** 는 지침으로 쓰이던
> 것이라 설계 판단에 영향을 줬을 수 있다. 발표 준비와 별개로 `docs/`를 고칠 근거가 된다.

---

## G. E절을 어떻게 찾았나

각 주장을 **문서가 아니라 코드에서** 확인했다. 개수는 직접 세고, 파일·클래스는 존재를 확인하고,
"~가 금지된다"류는 **그 에러 문자열을 코드에서 직접 찾았다.** 그 결과 11건이 나왔다.

특히 X-09는 이 방식이 아니면 못 찾았다 — 9개 문서가 입을 모아 "부동소수점이 금지된다"고
적고 있었으므로, 문서끼리 대조했다면 전부 일치해서 통과했을 것이다.
코드에서 그 에러 문자열을 찾아보니 **없었다.**

**그래서 이 디렉터리의 수치는 전부 코드에서 확인한 것이다.**
같은 확인을 다시 하려면 `scripts/verify-docs.py` (개발용 도구, 이 디렉터리 밖에 있다).
