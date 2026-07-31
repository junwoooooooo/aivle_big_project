# 재무 분석: 타당성 2단계 — 결정론 계산 + 가정 provenance + 실시간 what-if

> **상태: 부분 구현.** 계산 정책·AI 계약·Mock·V17까지 완료됐고 job·서비스·화면이 남았다.
> 무엇이 끝났는지는 `docs/handoff.md` §0-4, 불변식 요약은 `CLAUDE.md` §6-3을 본다.
> 이 문서는 승인된 설계서 원본이며, 남은 작업의 계약(§2)과 테스트 계획(§4)이 정본이다.

## Context

`문서/image.png` 구조도의 마지막 수렴점이다: **사업성 분석 = 규제 검토 → (BM·시장·기술운영) → 재무 분석.**
규제 검토(법률)와 타당성 3묶음 개편은 완료·커밋(`9f6898d`)됐다. 이번 작업으로 파이프라인이 끝까지 이어진다.

뼈대는 V1부터 예약돼 있고 **로직 0%**다: `financial_analyses` 테이블(V1:142),
`FinancialAnalysis` 엔티티, `FinancialAnalysisRepository`(빈 인터페이스),
`JobType.FINANCIAL_ANALYSIS`, `ReportType.FINANCIAL`, `ProjectStage.FINANCIAL`.

### 선행 확정 (재확인 불필요)

1. **재무는 FEASIBILITY stage 내부의 2단계다.** stage 전이 없음. `ProjectStage.FINANCIAL`은 예약
   유지, `Project.enterFinancial()`은 만들지 않으며,
   `PersonaRecommendationCommandService:63`의 `stage == FEASIBILITY` 가드는 **손대지 않는다**.
2. 타당성 화면의 "다음: 재무 분석" 버튼을 활성화한다. 단계 이동이 아니라 **같은 화면의 후속 분석**이며
   결과는 묶음 카드 3장 아래 섹션으로 열린다.
3. 입력 = 타당성 묶음 결과 + 기획서의 원가·매출 섹션.

### 착수 전 정정된 사실 (지시서 원문과 다름 — 확인 완료)

#### ★ 트리 역전 논란 — **시나리오 B(저장소 재구성) 확정.** 근거는 git 이력이다.

`ba2479d update` 커밋이 **같은 커밋에서** renew 트리를 루트로 승격했다:
```
git log --diff-filter=D -- front+back_renew/backend/**/FeasibilityCommandService.java  → ba2479d
git log --diff-filter=A -- backend/**/FeasibilityCommandService.java                   → ba2479d
```
같은 파일이 한쪽에서 삭제되고 다른 쪽에 추가된 **이동 커밋**이다. 추가 실측:

| 검증 | 결과 |
|---|---|
| `git ls-files "*db/migration/V16*" "*db/migration/V1__*"` | 둘 다 **`backend/`** 아래에만 |
| `git ls-files "*FeasibilityCommandService.java"` | **`backend/`** 아래 1건 |
| 루트 `backend/` 규모 | **Java 403개 파일 / 24,388줄** — "15파일 458줄 프로토타입"이 아니다 |
| `git ls-files front+back_renew` | **0건** (추적 파일 없음. 빌드 산출물·node_modules뿐) |

**즉 지시서 §3-1·§3-3의 경로가 오진이고 정정이 맞다.** 그대로 착수했다면 재무 코드가
git이 추적조차 하지 않는 빌드 잔재 폴더에 꽂혔을 것이다.

**단, 조건 ①의 "CLAUDE.md §1·§9-1 갱신"은 불필요하다 — 이미 갱신돼 있다.**
디스크의 CLAUDE.md는 **한 부뿐**이고(`find . -name CLAUDE.md` → `./CLAUDE.md`),
헤더가 *"작성 기준: 2026-07-29 / 저장소 재편(615beb7) 반영"*이며 §1 표는
`backend/(루트) = 주력 · 활성 — 여기서 작업할 것`, `front+back_renew/ = 사장 — 건드리지 말 것`,
§9-1은 *"새 기능은 루트 backend/·frontEnd/·ai/에 작업한다"*로 이미 정확하다.
낡은 것은 저장소의 지도가 아니라 **손에 든 사본**이다(CLAUDE.md는 전역 gitignore 대상이라
커밋되지 않으므로 사본이 갈라지기 쉽다 — 이 점만 §9에 한 줄 남긴다).

#### 그 밖의 정정

| 지시서 | 실제 |
|---|---|
| peak funding "계산 기존재 — 차트 마커 비용뿐" | **없다.** `analysis/financial/`엔 엔티티+빈 리포지토리 2개뿐, 현금흐름·BEP 계산 0줄 |
| (미언급) 차트 수단 | 프론트 의존성에 **차트 라이브러리 없음**. SVG 선례는 아이콘뿐 → **손수 SVG polyline**(승인됨) |

**추출 가능성 실측** (`docs/example/…Pentacle Stand….docx`): 얻을 수 있는 것은 단가(38,000/29,800/19,000),
변동비(8,500+2,500=11,000), 초기투자(2,000만), 연차 수량(8,000/25,000/60,000), 연차 매출(2.8억/8.5억/20억).
**없는 것: 월 고정비·채널 믹스·할인율·세금·월 단위 분해.** 게다가 단가×수량 ≠ 명시 매출(3.04억 vs 2.8억)이라
문서가 truth를 답하지 않는다. 숫자는 표가 아니라 한국어 산문("약 8,500원", "2억 8천만 원")이다.
→ **계획서 단독 자동 계산은 성립하지 않는다. 사용자 확정 단계가 필수다.**

---

## 0. 설계 원칙 (전 계층 관통 — 위반 시 리뷰 반려)

1. **숫자보다 가정이 주인공.** 외부 실데이터 미연동(CLAUDE.md §7)이므로 모든 수치는
   *기획서가 스스로 적은 가정*의 결정론 재계산이다. 시장 규모 기반 매출 추정·업계 평균 마진 비교·
   성장률 예측 **금지**. 타당성의 `"Never invent TAM/SAM/SOM, growth rates…"` 방어선을 재무 프롬프트에도 넣는다.
2. **계산은 결정론, 서술은 AI.** 지표는 순수 함수. AI 역할은 ① 기획서에서 기준 가정 추출
   ② headline·해석 서술, 둘뿐이다.
3. **공식 결과 vs 샌드박스 구분.** **저장되는 사용자 입력은 확정 단계(§1-0)를 통해서만.**
   슬라이더 what-if는 프론트 계산이며 **저장하지 않는다.** 조정 즉시 "조정됨 · 저장되지 않음" 배지 +
   "확정 가정으로 되돌리기". 샌드박스 구분은 그대로 살아 있다 — 확정은 명시적 행위, 슬라이더는 아니다.
4. **결측의 1급 시민화.** 기획서에 없는 값(할인율 등)은 기본값을 쓰되 **"기획서에 없음 · 기본값 적용"으로
   자수**한다. 계산 불가 지표는 숨기지 말고 "확인 필요"로 노출하고 어느 섹션을 보완하면 되는지 안내한다.
5. **가정 provenance.** 모든 가정에 기획서 인용(sectionLabel + quote)을 붙이고, 인용문은
   **원문 부분문자열 검증**을 통과해야 한다(법률 라우팅 인용 검증과 동일 발상 — 지어낸 가정 구조적 차단).
6. **불능 상태는 정상 출력값.** 공헌이익 음수·손익분기 도달 불가는 에러가 아니라 결과다.
   정책 함수는 항상 성공하고(null + 사유 코드) 표시 계층이 번역한다.

---

## 1. 흐름과 지표

### 1-0. 가정 확정 단계 (실측이 요구한 필수 단계)

Pentacle 실측이 던진 질문에 계약이 답해야 한다. **"버튼 → job → 결과"로는 성립하지 않는다.**

| 실측이 드러낸 문제 | 확정 단계에서 푸는 방법 |
|---|---|
| 단가가 3종(38,000 B2C / 29,800 얼리버드 / 19,000 B2B) | draft에 **후보 3개를 모두 싣고** 사용자가 하나 선택(또는 채널 믹스 비율 입력) |
| 연차 수량만 있고 월 분해가 없음 | 기본 규칙(연 수량 ÷ 12 균등)을 **`DEFAULT`로 자수**하고, 사용자가 바꿀 수 있게 |
| 단가×수량 ≠ 명시 매출 (3.04억 vs 2.8억) | **모순을 화면에 드러내고** 사용자가 truth 선택(단가×수량 기준 / 명시 매출 기준) |
| **월 고정비가 통째로 없음** | `USER` 입력 **필수**. 미입력이면 손익분기·peak funding은 "확인 필요"로 강등되고 그 사실을 첫 화면에 알린다 |
| 할인율 없음 | `DEFAULT` 기본값 + 자수 |

**흐름:**
```
[다음: 재무 분석] → job 실행(AI 추출) → status=NEEDS_ASSUMPTIONS
   → 가정 확정 화면: draft 표시(인용·DEFAULT·후보 선택·모순 경고) + 결측 입력
   → [가정 확정] → 결정론 계산 → status=COMPLETED → 결과 섹션
   → (결과 화면에서) 슬라이더 what-if = 저장 없는 샌드박스
```
- `FinancialAnalysis.status`(V1의 `status VARCHAR(30) NOT NULL`)를 그대로 쓴다:
  `NEEDS_ASSUMPTIONS` → `COMPLETED`. **스키마 추가 없이** 상태 필드는 `assumptions_json` 안에 넣는다.
- 재확정(가정 수정 후 다시 확정)은 같은 row를 갱신한다. 이력 버전 관리는 v2.
- **탈출구가 필요한 이유**: 확정된 기획서는 불변이고 reopen이 없다(§7 Medium 갭).
  "기획서 수정 → 재분석"만 남기면 사실상 막다른 길이므로, `USER` 가정이 정식 경로다.

### 1-1. v1 지표 (전부 확정 가정의 결정론 재조합 — 추가 LLM 호출 없음)

| 그룹 | 지표 | 계산 | 비고 |
|---|---|---|---|
| 수익성 | 공헌이익/건 | 객단가 × (1 − 변동원가율) | 첫 화면 최상단. 건당 손해면 이후 지표가 무의미 |
| 수익성 | 3년 ROI | (36개월 누적이익 − 초기투자) / 초기투자 | |
| 수익성 | NPV(36개월) | −초기투자 + Σ 월이익/(1+r/12)^t | 할인율 결측 시 기본값 자수(원칙 4) |
| 수익성 | IRR | NPV=0인 r (이분법 근사) | 할인율 시비에서 자유로운 짝 지표 |
| 생존 | 손익분기 수량/월 | 고정비 / 공헌이익 | 판매 목표와 비교해 **안전 여유율** 병기 |
| 생존 | 손익분기 시점 | 초기투자 / 월이익 (ceil) | 월이익 ≤ 0이면 "도달 불가" |
| 생존 | 최대 자금 필요 시점 | 누적 현금흐름 최저점 (금액·월) | **신규 구현** (기존재 아님) |

**시나리오**: 보수(−20%) / 기준 / 낙관(+20%) — 판매량·객단가에 곱하는 결정론 조정. 프론트 계산, 저장 없음.
종합 카드 headline은 판정만이 아니라 **민감도 한 문장**을 포함한다
(예: "판매량 가정 30% 미달 시 손익분기 27개월") — 페르소나 검증 단계로의 연결 고리다.

**v2로 미룸**: 민감도 토네이도 · 조달 갭 분석 · 통합 리포트 재무 반영.
**금지**(외부 실데이터 연동 전까지): 시장 규모 기반 추정 · 업계 평균 비교 · 경쟁사 벤치마크 · 성장률 예측.

---

## 2. 데이터 계약

### 2-1. `assumptions` — draft(AI 추출) → 확정(사용자 반영)
```jsonc
{
  "state": "NEEDS_ASSUMPTIONS",          // → "CONFIRMED"
  "confirmedAt": null,
  "items": [{
    "key": "UNIT_PRICE", "label": "객단가", "value": 38000, "unit": "KRW",
    "source": { "type": "PLAN", "sectionLabel": "비즈니스 모델",
                "quote": "소비자가(MSRP): 38,000원" },
    "candidates": [                       // 후보가 여럿이면 사용자가 고른다
      { "value": 38000, "label": "B2C 소비자가", "quote": "소비자가(MSRP): 38,000원" },
      { "value": 19000, "label": "B2B 공급가",   "quote": "B2B 공급가: 19,000원" }
    ],
    "adjustable": true
  }],
  "conflicts": [{                          // 문서 자체가 모순일 때 사용자가 truth를 고른다
    "kind": "REVENUE_MISMATCH",
    "message": "단가×수량(3.04억)과 기획서에 적힌 매출(2.8억)이 다릅니다.",
    "options": ["UNIT_TIMES_VOLUME", "STATED_REVENUE"], "chosen": null
  }]
}
```
- `source.type` = `PLAN`(인용, **부분문자열 검증 필수**) | `DEFAULT`(기본값, `note` 필수)
  | **`USER`**(사용자가 채우거나 고침 — "사용자 입력" 배지로 자수. 자수 원칙 유지)
- 필수 키: `UNIT_PRICE`, `VARIABLE_COST_RATE`, `MONTHLY_VOLUME`, `MONTHLY_FIXED_COST`,
  `INITIAL_INVESTMENT`, `DISCOUNT_RATE`.
  **`MONTHLY_FIXED_COST`는 기획서에서 나오지 않는 것이 정상**이므로 `USER` 입력을 전제한다.
  끝까지 결측인 키는 종속 지표를 "확인 필요"로 강등하고 **무엇을 채우면 풀리는지** 함께 표시한다.

### 2-2. `results` — 백엔드 정책 산출물
`{ contributionMargin, breakEvenQty, breakEvenMonth, safetyMarginPct, roi3y, npv36m, irr,
peakFunding{amount,month}, verdict }` — 도달 불가는 null + 사유 코드.
verdict 임계값은 신규 정의하되 `FeasibilityScorePolicy`의 `Verdict`(HIGH_RISK/CONDITIONAL/PROMISING) 문법을 따른다.

### 2-3. `narrative` — AI 서술
`{ headline, summary, sensitivityNote, verifyFirst[] }` — 없으면 null, 화면은 지표만 표시(우아한 강등).

### 2-4. 저장 — `financial_analyses` **재활용**(사전 판단 완료)

V1:142 컬럼과 §2-2를 대조한 결과 **재활용이 맞다**:
- 이미 있음: `status`, `currency`, `analysis_period_months`, `expected_revenue`, `expected_cost`,
  `break_even_point_months`, `roi`, `npv`, `irr`, `summary`, `assumptions_json`, `result_json`
- `analysis_job_id UNIQUE`는 **걸림돌이 아니다** — 재무는 1 job → 1행이다
  (법률·타당성이 이 제약 때문에 새 테이블을 판 것과 상황이 다르다)
- **V17에서 추가**: `narrative_json TEXT`, `verdict VARCHAR(40)`,
  `structured_plan_id`·`feasibility_assessment_id` FK(입력 출처 고정 + 멱등키용),
  `prompt_version`·`catalog_version`(재실행 판단)
- Flyway **V1–V16 불변**, V17 신규 파일로만 (현재 V16이 최신)

---

## 3. 변경 지점

### 3-1. 백엔드 — **루트 `backend/`** (지시서의 `front+back_renew/backend/`는 사장 트리다)

| 파일 | 변경 |
|---|---|
| `analysis/financial/application/FinancialCalculationPolicy.java` (신규) | §1 지표 전부 순수 함수. 불능 규약(원칙 6). `FeasibilityScorePolicy` 패턴 준수 — AI·저장 접근 없음 |
| `analysis/financial/application/FinancialCommandService.java` (신규) | job 생성. 가드: 타당성 assessment 선행(`FeasibilityCommandService:54` 패턴). 멱등키 (project, jobType) — 실패 row는 `AnalysisJob.requeueTerminated()` 재사용(§8-2) |
| `analysis/financial/application/Financial{JobContext,Persistence,Query}Service.java` (신규) | 저장·조회. validate: `source.type=PLAN` 인용 **부분문자열 재검증**(2차 방어). draft 저장 시엔 필수 키 결측을 **허용**한다(확정 전이므로) |
| `analysis/financial/application/FinancialAssumptionService.java` (신규) | **확정 단계**(§1-0). 사용자 입력 병합 → `USER` 태깅 → 후보 선택·모순 해소 반영 → 필수 키 충족 검사 → 정책 계산 → `COMPLETED` 저장. 낙관적 락은 `UpdateMissingFieldRequest` 전례대로 요청 본문 `version` + 409 `RESOURCE_VERSION_CONFLICT` |
| `analysis/financial/dto/`, `controller/` (신규) | `POST /projects/{id}/financial-analyses`(시작), `GET …/latest`, **`POST …/{id}/assumptions`(확정)**. openapi.yaml 경로 추가 후 redocly lint 통과 |
| `integration/ai/financial/FinancialAiRequest/Response.java` (신규) | Request = 묶음 결과 + 원가·매출 섹션 텍스트. Response = §2-1 assumptions + §2-3 narrative. **`results`는 AI 산출물이 아니다** |
| `integration/ai/financial/MockFinancialAiClient.java` (신규) | 섹션 텍스트에서 결정적 규칙으로 가정 추출. 가정마다 다른 문구. **결측(할인율 없음)·후보 다수(단가 3종)·모순(단가×수량 ≠ 명시 매출) 세 케이스를 각각 최소 1건씩 결정적으로 방출**한다 — 그래야 원칙 4 경로와 §1-0 확정 화면(후보 라디오·truth 선택)이 Mock 스모크에서 실제로 검증된다 |
| `integration/ai/financial/OpenAiFinancialAdapter.java` (신규) | TAM/SAM 금지 문구 동일 삽입. 인용 검증 실패 가정은 DEFAULT 강등/결측 처리 — **통째 실패시키지 않음** |
| job 러너에 `FINANCIAL_ANALYSIS` 등록 | `nextAttemptAt`은 **null**(UTC 지뢰 §8-3). 감사 메타데이터는 화이트리스트 키만(§8-4) — 기존 `jobId`/`status`/`verdict`/`assessmentId` 재사용하면 화이트리스트 수정 불필요 |
| `db/migration/V17__add_financial_analysis_columns.sql` (신규) | §2-4 |
| `CLAUDE.md` §9 (한 줄 추가) | **이번 혼선의 근본 원인 차단**: CLAUDE.md는 전역 gitignore 대상이라 커밋되지 않아 사본이 갈라진다. "지도(§1)와 실제가 어긋나 보이면 기억이 아니라 `git ls-files`로 판별할 것"을 §9에 남긴다. §1·§9-1 본문은 **이미 정확하므로 수정하지 않는다** |

### 3-2. 프론트 — `frontEnd/src/features/feasibility/` (재무는 타당성 2단계이므로 신규 feature 폴더가 아니다)

- **`model/financialViewModel.js`** (신규, 전부 순수 함수):
  `computeFinancials(assumptions)` — §1 지표 전부. **백엔드 정책과 동일 공식이며 §1 표가 정본**이다.
  `applyScenario(assumptions, mode)`, `isDirty(current, base)`.
- **`components/AssumptionConfirmForm.jsx`** (신규, §1-0): draft 가정 표 —
  인용 펼침 · `PLAN`/`DEFAULT`/`USER` 배지 · 후보 라디오(단가 3종) · 모순 경고와 truth 선택 ·
  결측 필수 키 입력(특히 월 고정비). 입력은 `FormField` + `TextInput type="number" inputMode="decimal"`
  (전용 NumberInput 없음 — `PasswordInput`이 `type` 전달하는 선례를 따른다).
  409 충돌 시 최신값 재조회 후 비교 UI — `StructuredPlanCompletion.jsx`의 충돌 처리 패턴 재사용.
- **`components/FinancialSection.jsx`** (신규, 확정 후): 종합 카드(headline + verdict + Mock 배지) →
  지표 카드(수익성/생존 소그룹, 지표마다 "가정 N건 사용 ▾" 펼침) → 시나리오 토글 →
  **가정 패널(상단에 「가정 수정」 버튼 — 누르면 `AssumptionConfirmForm`으로 돌아간다)** ·
  인용 펼침 · 출처 배지 → **누적 현금흐름 차트** → 확인 필요 → 경계 문구.
  접기는 `<details>` — 법률 `OverallVerdictCard` 패턴 재사용.
  이 버튼이 §1-0의 **재확정 진입점**이다. 없으면 `COMPLETED` 이후 가정을 고칠 문이 사라진다.
- **`components/CashFlowChart.jsx`** (신규): **손수 SVG**(승인됨). 36포인트 `polyline` +
  손익분기·peak funding 마커 + 0선. 좌표 변환은 순수 함수로 분리해 단위 테스트. 의존성 추가 없음.
  `<title>`/`aria-label`로 접근성 확보하고 수치는 표로도 병기.
- **what-if 상태 설계**: `useState`는 **가정 6개뿐**, 지표·차트는 전부 렌더 중 파생 계산.
  파생 상태를 state에 넣지 않는다. dirty 시 배지 + 되돌리기(원칙 3).
  **슬라이더에는 저장 버튼을 만들지 않는다.** 안내 문구는
  **"반영하려면 가정을 다시 확정하세요"** — 확정 화면으로 보내는 것이 정식 경로다.
  (이전 판의 *"기획서 수정 → 재분석뿐"* 문구는 §1-0이 뒤집은 화석이므로 쓰지 않는다.
  기획서는 확정 후 불변이고 reopen이 없어 그 경로만 남기면 막다른 길이다.)
- **`FeasibilityPage.jsx`**: "다음: 재무 분석" 버튼 활성화(문구 유지). 실행 → job progress(기존 패턴) →
  `NEEDS_ASSUMPTIONS`면 `AssumptionConfirmForm`, `COMPLETED`면 `FinancialSection` 렌더 + 앵커.
  상태 복구는 `useFeasibility`의 3단 폴백 패턴(결과 → job → ready)을 그대로 따른다.
- **경계 문구(제거 금지)**: "재무 자문이 아닙니다 · 모든 수치는 기획서에 적힌 가정의 계산 결과이며
  외부 시장 데이터가 반영되지 않았습니다" + Mock 배지.

### 3-3. 손대지 않는 것
`ProjectStage` 전이 · `Project.enterFinancial()` · 페르소나 시작 가드(선행 확정 1) ·
페르소나 차원 코드 4개 회귀 테스트(green 유지) · `reportViewModel.js`(재무 반영은 v2) ·
Flyway V1–V16 · **`front+back_renew/`(사장)** · 법률 파이프라인 일체.

---

## 4. 테스트

**백엔드**
- `FinancialCalculationPolicyTests`: 기준 케이스(§1 공식 수치), 경계값(공헌이익 0·음수 → 불능 규약),
  NPV/IRR 알려진 답, peak funding.
- `MockFinancialAiClientTests`: 추출 결정성, 결측 가정 → DEFAULT/결측 경로,
  **인용이 섹션 텍스트의 부분문자열임을 단언**,
  **후보 다수 케이스와 모순 케이스가 각각 최소 1건 방출되는지**(스모크가 의존하는 계약).
- Persistence validate: **지어낸 인용(부분문자열 아님) 거부**, 필수 키 결측 시 종속 지표 null 저장.
- `FinancialAssumptionServiceTests`: 사용자 입력이 `USER`로 태깅되는지, 후보 선택·모순 해소가
  계산에 반영되는지, **필수 키(월 고정비) 결측 상태로는 확정이 거부**되는지,
  버전 불일치 시 409(`RESOURCE_VERSION_CONFLICT`).
- vertical-slice: job 실행 → `NEEDS_ASSUMPTIONS` → 확정 → `COMPLETED` **전 구간 왕복**,
  타당성 미완료 시 시작 거부 가드.

**프론트**
- `computeFinancials` 단위: **백엔드 정책 테스트와 같은 수치 케이스를 공유**해 두 구현의 일치를 못박는다
  (공식 이원화의 회귀 방어).
- `applyScenario`·`isDirty` 단위, 차트 좌표 변환 단위, 불능 상태 렌더("도달 불가").
- `AssumptionConfirmForm`: 후보 라디오 선택, 모순 경고 표시, **월 고정비 미입력 시 확정 버튼 비활성**,
  `PLAN`/`DEFAULT`/`USER` 배지가 각각 보이는지.
- `FinancialSection`: dirty 배지, 되돌리기, `DEFAULT` 가정 자수 표시, 경계 문구 존재 단언,
  **「가정 수정」 버튼이 확정 화면으로 되돌리는지**(재확정 진입점),
  what-if 안내 문구가 **"반영하려면 가정을 다시 확정하세요"**인지(잔재 ① 회귀 방어).
- 회귀: 타당성 묶음·페르소나·법률 기존 테스트 green.

---

## 5. 검증

```powershell
# 백엔드 — 베이스라인 정본은 docs/handoff.md: 사전 실패 4건 + 플레이크 1건
#   (freshH2Schema 메시지는 V17 추가로 expected "10" but was "17"로 바뀐다 — 같은 실패 1건)
cd backend ; .\gradlew.bat test --tests "*Financial*"
.\gradlew.bat test

# 프론트 — Node 플래그는 vite.config 내재화 완료. NODE_OPTIONS 불필요
cd ..\frontEnd ; npx.cmd vitest run src/features/feasibility/
npm.cmd run lint ; npm.cmd run test:run

# 계약
cd .. ; npx.cmd --yes @redocly/cli@2.20.5 lint docs/api/openapi.yaml

# UI 스모크 — .env.demo 를 LEGAL_PROVIDER=mock 으로 임시 전환 후
.\scripts\demo-start.ps1
#   scratchpad/smoke.py 로 FEASIBILITY 단계까지 → 타당성 실행 → "다음: 재무 분석" →
#   [확정 화면] 단가 후보 선택·매출 모순 truth 선택·월 고정비 입력·DEFAULT 배지 확인 → 확정 →
#   [결과 화면] 지표·가정 인용 펼침·슬라이더 dirty 배지·되돌리기·
#   불능 상태(판매량을 극단으로 낮춰 "도달 불가")·차트 마커·경계 문구 점검
#   [재확정] 「가정 수정」 → 월 고정비를 바꿔 다시 확정 → 지표가 갱신되는지 확인
#   끝나면 .env.demo 를 pipeline 으로 복원
```

**법률 파이프라인 실행 불필요** — 이 작업은 mock provider만으로 전 경로가 검증된다.
법률 전체 재실행(§9-7 "마지막 1회" 규율)을 이 작업 검증에 끌어들이지 말 것.

---

## 6. 주의 (지뢰 재확인)

- `nextAttemptAt`에 `LocalDateTime.now()` 금지 — **null이 안전** (§8-3).
- 감사 메타데이터 임의 키 금지 (§8-4). 기존 화이트리스트 키 재사용으로 수정 회피 가능.
- 재무 AI는 **Mock/OpenAI 이분법**이며 파이프라인 provider가 없다. 같은 포트에 `@Component`를
  중복 등록해 빈 2개를 만들지 말 것(§3 전례 — 기동 실패).
- 표시 숫자는 전부 반올림/포맷 경유 — JS 부동소수점 잔재가 화면에 노출되지 않게.
- Mock/Real 구분 배지·경계 문구는 어떤 리팩터에서도 **제거 금지** (§9-4·9-5).

## 7. 다음 작업으로 넘김

민감도 토네이도(→ 검증 우선순위 자동 도출, 페르소나 연결) · 조달 갭 분석 ·
통합 리포트 재무 반영 · `ReportType.FINANCIAL` 활성화 여부 결정.
