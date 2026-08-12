# 02. 기능 명세 — 여정 8단계

- 정본: `frontEnd/src/app/routing/AppRouter.jsx` (라우트) + `ProjectModuleStatusService.findAll()` (칸)
- 실스택 확인: 2026-08-11
- 서술 원칙: **"사용자가 뭘 하면 → 뭐가 일어나는가"**. 요구사항 ID가 아니라 동작으로 쓴다.

---

## 1. 여정 한눈에

```
아이디어 → 사업안 → 시장 분석 → BM 캔버스 → 기술·운영 → 재무 → 패널 트윈 조사 → 마케팅
  /idea    /concepts   /market   /business-model  /tech-ops  /finance  /panel-survey  /marketing
```

**각 단계는 앞 단계의 확정 산출물을 입력으로 요구한다.** 게이트의 정본은
`ProjectModuleStatusService.findAll()` 한 메서드다.

| # | 화면 | 라우트 | `PipelineModuleType` | AI TaskType |
|---|---|---|---|---|
| 1 | 아이디어 | `…/idea` | `IDEA` | `IDEA_BRIEF_DERIVATION` |
| 2 | 사업안 (생성·비교·선택) | `…/concepts` (+`/compare`) | `CONCEPT_PORTFOLIO` | `CONCEPT_PORTFOLIO_V2_{RUN,CONTINUE,SELECTION_ACTION}` |
| 3 | 시장 분석 | `…/market` | `MARKET_ANALYSIS` | `MARKET_RESEARCH` (mode=FULL) |
| 4 | BM 분석 | `…/business-model` | `BUSINESS_MODEL` | `MARKET_RESEARCH` (mode=BM) |
| 5 | 기술·운영 | `…/tech-ops` | `TECH_OPS` | `TECH_OPS_PROPOSAL` |
| 6 | 재무 | `…/finance` | `FINANCE` | `FINANCE_ESTIMATE` |
| 7 | 패널 트윈 조사 | `…/panel-survey` | `PANEL_SURVEY` | `TWIN_SURVEY`, `TWIN_STIMULUS_DRAFT` |
| 8 | 마케팅 콘텐츠 | `…/marketing` | `MARKETING` | `MARKETING_CONTENT_GENERATION` |

> 발표에서 쓸 만한 설계 포인트: **3·4번은 같은 TaskType을 `mode`로 가른다.**
> 시장 분석 결과를 BM이 재사용하기 때문이고, 그래서 BM의 taskInput은
> `sourceRun` 문자열뿐이다 — 시장 데이터 덩어리를 계약 경계 너머로 넘기지 않고
> **AI 서버가 원장에서 직접 읽는다.**
>
> ⚠ 사내 기록에는 그 이유가 *"`CanonicalInputHasher`가 부동소수점을 거부하기 때문"*이라고
> 적혀 있으나 **현재 코드는 유한 소수를 허용한다**(→ `03_ARCHITECTURE.md` §4, X-09).
> 설계 판단 자체는 유효하다(큰 페이로드를 안 넘기는 것) — **이유만 갱신하면 된다.**

---

## 2. 단계별 상세

### 2-1. 아이디어 (`/idea`)

**사용자가 하는 것** — 아이디어를 자유 문장으로 쓴다. 파일(DOCX·텍스트)을 올려도 된다.

**일어나는 것**
1. Spring이 파일을 받아 저장·검증하고 **텍스트만 추출**한다 (AI 서버에는 bytes를 안 준다)
2. `IDEA_BRIEF_DERIVATION` 실행 → 아이디어를 구조화된 브리프로 정규화
3. 빠진 항목이 있으면 **보완 질문**을 만들어 사용자에게 되묻는다
4. 사용자가 확정하면 다음 칸이 열린다

**설계 포인트** — 질문 응답으로 받은 입력도 TEXT 소스로 기록된다. 입력 수단이 달라도
출처 추적은 같다.

### 2-2. 사업안 (`/concepts`)

**사용자가 하는 것** — 「사업안 생성」을 누른다. 나온 후보들을 비교하고 하나를 고른다.
(생성과 비교가 **한 화면**이고 `/compare`는 같은 화면의 비교 모드다.)

**일어나는 것**
1. `CONCEPT_PORTFOLIO_V2_RUN` — 후보 생성.
   개수는 요청 파라미터 **`maxConcepts` (1~5, 기본 5)** 로 사용자가 정한다
   (`ConceptPortfolioApiModels`: `@Min(1) @Max(5)`)
2. **후보 거버넌스** — 잠금 필드(`targetRegion`·`revenueModel`·`price`·`channels`·`differentiators`)가
   사용자 아이디어에서 벗어나지 않게 고정
3. **차별성 판정** — `CONCEPT_DISTINCTNESS_JUDGE`로 후보끼리 겹치는지 본다
4. **법률 검토** — `CONCEPT_LEGAL_REVIEW` / `CONCEPT_DELTA_LEGAL_REVIEW`.
   법제처 Open API 실연동
5. 부족하면 `CONCEPT_PORTFOLIO_V2_CONTINUE`로 이어 만들고,
   사용자가 하나를 선택하면 `CONCEPT_PORTFOLIO_V2_SELECTION_ACTION`

⚠ **못 채우면 성공을 가장하지 않는다** — 사실이나 근거가 부족하면 입력 필요 또는 실패 상태로 끝낸다.

> ⚠ **인용 주의.** `README.md`의 "적격 컨셉 5개 · 최대 15개 후보 검사"와
> `AS_BUILT §3-3`의 "목표 3개 · 교체 2라운드 · 후보 상한 9"는 **둘 다 현행 모듈의 값이 아니다.**
> - `AS_BUILT`가 인용한 `CONCEPT_TARGET_ELIGIBLE_COUNT`는 **코드에 존재하지 않는다**(grep 0건)
> - `README`의 15는 옛 `ConceptFactoryLimits`에서 온 값인데 그 클래스도 지금은
>   `SLOT_COUNT=5 · MAX_REPLACEMENT_ROUNDS=2 · MAX_LEGAL_REDESIGNS_PER_SLOT=1`
>   → **`MAX_INSPECTED_CANDIDATES = 20`** 이다. 게다가 그 팩토리는 **죽은 코드**다
>
> **발표에는 `maxConcepts` 1~5(기본 5)만 쓴다.**

### 2-3. 시장 분석 (`/market`)

**사용자가 하는 것** — 경쟁사 씨앗을 몇 줄 적고(선택) 「시장조사 실행」을 누른다.
90~266초 폴링 후 결과를 본다.

**일어나는 것** — 자세한 것은 `05_AI_PIPELINE.md` §1. 요약하면:
1. **설계** — 무엇을 조사할지 슬롯(질문 단위)을 하네스가 스스로 짠다
2. **수집** — 웹 검색(Tavily) · KOSIS 국가통계 · DART 전자공시 · PDF에서 문서를 모은다
3. **발췌** — 문서에서 값과 인용문을 뽑는다
4. **검증** — 출처·단위·자릿수·일관성을 규칙 파일이 검사한다
5. **성적표** — 7과목으로 채점

**사용자가 보는 것**
- 7과목 성적표: ①시장크기 ②성장률 ③경쟁사 ④가격 ⑤수요 ⑥계산(TAM) **⑦못 찾은 것**
- 각 칸은 `채워짐 / 부분 / 미확보` 3값
- 근거 카드 — 값을 누르면 원문 인용과 URL로 드릴다운

**설계 포인트** — **⑦번 과목이 항상 있다.** 못 찾은 것을 못 찾았다고 말하는 칸을
성적표에 고정으로 두는 것이 이 제품의 태도다.

### 2-4. BM 캔버스 (`/business-model`)

**사용자가 하는 것** — 컨셉 계약이 주지 않는 4칸(고객 관계 등)을 직접 채우고,
「BM 캔버스 만들기」를 누른다.

**일어나는 것** — 3단계 분석 결과를 재사용해 9칸을 만든다.

**⭐ 9칸의 성격이 둘로 갈린다** (정본: `harness/vocab.json`의 canvas 라우팅 표)

| 성격 | 칸 | 요건 | 원천 |
|---|---|---|---|
| **측정·판정** (4칸) | 고객 세그먼트 · 가치 제안 · 채널 · 수익원 | 담당 조사 슬롯 ≥ 1 | 시장조사 근거 |
| **계획** (5칸) | 고객 관계 · 핵심 자원 · 핵심 활동 · 핵심 파트너 · 비용 구조 | **슬롯 불필요** | 컨셉 스냅샷 · 실행 제약 · 사용자 입력 |

> **9칸을 전부 근거로 채우는 것은 설계가 아니다.** 계획 5칸에 근거가 없는 것은 결함이 아니라
> 정상이다. 화면이 이 구분을 하지 않으면 정상 결과가 미완성으로 읽힌다.

계획 칸 재료의 **우선순위**: `사용자 입력 > 견본 스텁 > 컨셉 파생`.
사용자가 쓴 칸은 기계로 `PLAN` 도장을 찍고, 근거 인용이 0이면 도장을 내리며
"사용자가 입력한 실행 계획이다 — 관측이 아니다"를 경계에 더한다. **내리는 방향만 한다.**

### 2-5. 기술·운영 (`/tech-ops`)

**사용자가 하는 것** — 실행한다. **일어나는 것** — `TECH_OPS_PROPOSAL`로 기술 스택·운영 구조 제안.

### 2-6. 재무 (`/finance`)

**사용자가 하는 것** — 가정(단가·비용 등)을 확정하고 실행한다.
**일어나는 것** — `FINANCE_ESTIMATE`로 추정. 경계 문구: **"재무 자문이 아니며 외부 시장 데이터를 반영하지 않는다."**

### 2-7. 패널 트윈 조사 (`/panel-survey`)

**사용자가 하는 것** — 물어볼 것(자극)을 정한다. 초안은 `TWIN_STIMULUS_DRAFT`가 만들어 준다.

**일어나는 것**
1. 사전 구축된 **트윈 카드 뱅크**에서 조건에 맞는 가상 응답자를 뽑는다
2. `TWIN_SURVEY` — 각 트윈이 자극에 응답
3. 집계 → 대표 응답자 카드로 표시

**설계 포인트 + 경계** — **"가설이며 실제 고객 응답이 아니다."**
과거 설계에 있던 「페르소나 → 인터뷰 → 종합」 체인은 **없어졌고**, 그 자리를 이 조사가 대신한다.
인터뷰는 결과 안의 대표 응답자 카드다.

### 2-8. 마케팅 콘텐츠 (`/marketing`)

**사용자가 하는 것** — 시안을 만들고 비교한다.
**일어나는 것** — `MARKETING_CONTENT_GENERATION`. AI가 돌려준 ID를 **보낸 ID와 대조**해 환각을 막는다
(`MarketingReportJourneyService.validateComparison()`).

**경계** — A/B는 **시안 상대 비교**이지 실제 사용자 실험이나 전환율이 아니다.

---

## 3. 화면 전체 (별첨용)

`AppRouter.jsx` (121줄) 기준 — **Route 선언 52개 / `path` 지정 39개.**
와일드카드 2개와 오버레이(모달) 중복 5개를 빼면 **고유 화면 32개**.

| 구분 | 개수 | 화면 |
|---|---|---|
| 공개 | 5 | 랜딩 `/` · 재무 모듈 단독 데모 `/module` · 로그인 · 회원가입 · 비밀번호 재설정 |
| 워크스페이스 | 6 | `/app` · 프로젝트 목록 · 생성 · 설정(+프로필·보안) |
| **프로젝트 여정** | **11** | 개요 + **여정 8단계** + `concepts/compare`(비교 모드) + 프로젝트 설정 |
| 관리자 | 10 | 개요·사용자(+상세)·프로젝트(+상세)·운영·작업·감사(+상세)·설정 |
| 오버레이(중복 경로) | 5 | 프로젝트 생성·설정, admin 상세 3종 — 모달용 재선언 |

### ⚠ 레거시 라우트는 이미 **제거**됐다

`AS_BUILT_ARCHITECTURE.md` §2는 *"`AppRouter.jsx` 82–134행이 옛 경로(`plan/`·`structured-plan`·
`review/*`·`validate/*`·`report` 등)를 전부 journey로 넘긴다"*고 적고 있으나,
**현재 파일에는 그 경로가 하나도 없다**(grep 0건). `Navigate`는 프로젝트 라우트 정규화용 1곳뿐이다.

남아 있는 것은 **프론트 feature 폴더 3개**(`feasibility/` · `financial/` · `report/`)뿐이고
`structured-plan`·`documents`·`legal-review`·`personas`·`validation`·`marketing` 폴더는 **삭제됐다.**
백엔드 `/api/v1` 레거시 컨트롤러(`legal-reviews`·`feasibility-assessments` 등)도 **grep 0건**이다.

> **발표에서 "죽은 코드가 많다"고 말하면 사실과 다르다.** 정리는 이미 상당히 진행됐다.
> → `99_MISSING_MATERIALS.md` X-07

---

## 4. AI TaskType 전체 (18종)

`backend/.../taskrun/domain/TaskType.java`

```
IDEA_ATTACHMENT_PARSE          IDEA_BRIEF_DERIVATION
CONCEPT_PORTFOLIO_V2_RUN       CONCEPT_PORTFOLIO_V2_CONTINUE
CONCEPT_PORTFOLIO_V2_SELECTION_ACTION
CONCEPT_FACTORY_RUN            CONCEPT_CANDIDATE
CONCEPT_DISTINCTNESS_JUDGE     CONCEPT_LEGAL_REVIEW
CONCEPT_REDESIGN               CONCEPT_HYPOTHESIS_ALTERNATIVE
CONCEPT_DELTA_LEGAL_REVIEW     TECH_OPS_PROPOSAL
FINANCE_ESTIMATE               MARKETING_CONTENT_GENERATION
MARKET_RESEARCH                TWIN_SURVEY
TWIN_STIMULUS_DRAFT
```

⚠ `AS_BUILT_ARCHITECTURE.md`와 `CURRENT_BASELINE.md`는 **"13종"**이라고 적고 있다. **18종이 맞다.**
(→ `99_MISSING_MATERIALS.md` X-05)
