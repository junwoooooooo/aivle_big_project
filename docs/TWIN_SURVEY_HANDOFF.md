# 패널 트윈 조사(TWIN_SURVEY) 인수인계

작성: **2026-08-10** · 브랜치 `market-research-v2` · **트랙 A·B 모두 완료**
(커밋 `993b2b7`→`57427a6`. §4 의 체크박스는 착수 시점 기록이고, 실제 결과는 §8 을 볼 것)

새 세션이 이어받기 위한 문서다. 사실만 적는다. 추정에는 「추정」이라고 쓴다.
계획서 원본: `~/.claude/plans/downloads-20260729-cryptic-pumpkin.md`

---

## 0. 지금 한 줄

한국미디어패널조사(KISDI) 기반 **디지털 트윈 9,102명**에게 선택형 설문을 돌려
「두 상품안 중 어느 쪽이 이기나」를 방향과 신뢰구간으로 답하는 기능을,
제품 여정의 **재무분석 → [패널 트윈 조사] → 마케팅** 자리에 새 단계로 넣는 작업.

**엔진과 화면 부품은 다 만들었고 전부 테스트 green이다. 남은 것은 배선뿐이다.**

---

## 1. 팔 수 있는 것과 없는 것 — 이게 이 기능의 전부다

외적 타당성 시험(G3D, 2026-08-05 종결)은 **종합 미달**이다. 유형별로만 성적이 갈렸고,
그 성적이 판매 경계다. 봉인 자산이 소진돼 **재시험은 불가능**하다.

| 유형 | 근거 | 처리 |
|---|---|---|
| 명백한 우열형 | 관문 3 **E 4/4 정식 통과** (λ=0.988, MDE₃₀₀=0.0136) + **계기 3종에서 방향 일치**(§9) | 제공 |
| 가격형 | B 3/4 모듈 성적 → **계기 재측정에서 방향 반전**(§9) | **차단**(2026-08-10) |
| 윤리·가치형(ESG·인증) | H1·H3·B1 전부 불일치 | **영구 금지** |
| 미묘한 우열형·다속성 경합 | H2·H6 측정 한계 이하 | 차단 — 측정 불가 명시 |

윤리·가치형이 **영구** 금지인 이유: 원인 가설이 「KMP에 환경·윤리 문항이 없다」다.
**카드에 없는 것은 카드 조립으로 만들 수 없다.** 프롬프트를 고쳐도 안 된다.

상시 제약: **크기·점유율·선택확률 주장 금지**(방향과 CI까지만),
**양방향 제시 + 평균 필수**(위치편향 실측 P=+1.0000 — 한 방향만 물으면 답이 뒤집힌다),
**1인당 복수 추출 후 평균**.

---

## 2. 저장소 밖 자산 — `~/Downloads/원시자료 데이터_20260729/combine_csv`

제품 저장소가 아니다. 별도 git 저장소이고 원자료는 재배포 금지다.

| 위치 | 정체 | 상태 |
|---|---|---|
| `_build/c1/c1_cards_A_v2.jsonl` | 원본 카드 뱅크 9,102명 / 128 MiB | 있음 |
| `_build/g3d/` | 완결된 외적 타당성 시험(원장 19,994셀, 집계, 성능분석) | 완결·동결 |
| `_build/g3e/` | **계기 동등성 재측정** — 이번에 신설 | 코드 완료, **실행 안 함** |
| `_build/twin/out/` | 제품용 export 8,604명 / 10.8 MB | **생성 완료** |

### 2-1. `_build/g3e/` — 0단계, 아직 실행하지 않았다

**왜 필요한가.** G3D 성적은 **Claude Code CLI 클린룸**(구독, `claude-sonnet-5`,
`argv_sha256` 동결)으로 쟀다. 제품 `ai/` 는 **OpenAI 호환 HTTP** 만 쓴다.
계기가 다르면 성적이 그 계기로 잰 것이 아니다.

- 상태: 배관검사 15항 통과(LLM 0회), 집계 이식 검증 통과, 합성 원장으로 통과/음성 경로 실증
- **실행은 사용자가 직접 하기로 했다.** 환경변수 3개 + 약 $10~20 + 3,600셀
- 순서는 `_build/g3e/README.md` — 예열(`--limit 20`) → 1파 → 2파 → 판정
- 판정 5관문: H(건강) · N(음성대조) · λ(신호) · D(방향) · Δ(동등)
- **결과가 제품 상수 하나를 정한다** — `ai/app/twin/caveats.py` 의
  `INSTRUMENT_EQUIVALENCE_CONFIRMED` (기본값 `False` = 미달 쪽)
  - 통과 → 「계기 동등성 확인」 문구, G3D 성적을 근거로 인용 가능
  - 미달 → 「검증 계기와 서비스 계기가 다르다 — 성적 미전이」 상시 병기, 성적은 참고 수치로 격하
  - **기능은 어느 쪽이든 나간다. 주장의 크기만 바뀐다.**

### 2-2. `_build/twin/out/` — 카드 뱅크(생성 완료, 배치 안 함)

`twin_export.py` 로 만들었다. `TWIN_PID_SALT` 환경변수 필요.

- `twin_cards_generic.jsonl` **8,604명 / 10.8 MB** (`{pid_hash, text}` 만)
- `twin_frame.csv` (`pid_hash, gender, age, band, weight, screen_exclude`)
- `twin_bank_manifest.json`

**9,102명이 아니라 8,604명이다** — 미성년 498명 제외. 결과적으로 검증(G3B·G3D)이 쓴
만 20세 이상 모집단과 같아진다. 스크립트가 검증 300명 전원 포함을 assert 한다(300/300 확인).

⚠ **재배포 금지 자산이다. 이미지에 굽지 않는다.** `research2/runs` 와 같이 `:ro`
바인드 마운트로 붙인다. 가림(pid 해시)만으로는 부족하다 — 카드 본문에 만나이·성별·지역·
가구형태·소득대가 문장으로 들어 있다(준식별자 다발).

⚠ 층 분포가 고르지 않다: 60대 이상 3,676명, 20·30대 각 850여 명. **젊은 층이 먼저 마른다.**

---

## 3. 제품 저장소에 만든 것 — 트랙 A (전부 **미추적 신규 파일**)

**한 줄도 기존 파일을 고치지 않았다.** 시장분석 머지와 충돌하지 않게 한 것이다.

```
ai/app/twin/            __init__ aggregate bank caveats models runner stimuli task_type   (8)
ai/tests/               test_twin_{aggregate,gate_parity,golden,runner,stimuli,survey,task_type}.py (7)
ai/tests/fixtures/twin_survey/   survey.json  gate_cases.json                             (2)
frontEnd/src/features/twin-survey/
    twinSurveyResult.js/.test.js  taskTypeGate.js/.test.js  sampleSize.js/.test.js
    SampleSizePicker.jsx/.test.jsx  StimulusEditor.jsx/.test.jsx                          (10)
```

### 성적 (2026-08-10 실측)

- `python -m pytest -q` → **393 passed** (트윈 이전 372 + 신규 21)
- `npx vitest run src/features/twin-survey/` → **69 passed**
- `npx eslint src/features/twin-survey/` → clean

### 이식 출처 — 새로 짠 것이 아니다

| 제품 | 원본 | 증명 |
|---|---|---|
| `aggregate.analyze` | `g3e_aggregate.py` (= `g3d_08_gate.analyze`) | G3D 원장 19,994셀을 **16/16쌍, 10개 수치 필드 + 분류 카운트, 오차 1e-9** 로 재현 확인 |
| `runner.build_body/parse_choice/fingerprint` | `g3e_runner.py` 동명 함수 | 0단계가 이 본문으로 잰다. 갈라지면 결론이 전이되지 않는다 |
| `stimuli.build_prompt/to_xy/decide_adaptive/needs_wave2` | `g3d_spec.py` | 양방향·적응식 k |
| 템플릿 | `g3b_template.txt` (바이트 동결) | 연어 상황으로 렌더 시 sha256 `6c734c5b4dbedc75…` 일치 확인 |

### 설계 결정 셋 (고치기 전에 이유를 읽을 것)

1. **구조화 출력을 쓰지 않는다.** 검증된 템플릿은 "이유 2~3문장 → 마지막 줄 `선택: A`"다.
   JSON 스키마 모드는 프롬프트를 바꾸고 곧 계기를 바꾼다. 파싱은 마지막 비어있지 않은 줄이
   `^선택: (A|B|없음)$` 에 **정확 일치**할 때만 채택하고, **형식 위반은 재시도하지 않는다**(측정치다).
2. **`temperature=1.0`.** 0이면 rep1==rep2 라 적응식 k가 죽고 G2가 실측한 생성 분산(Δ_T 0.477)이
   사라진다. 설계가 무너진다.
3. **MDE 바닥 `6/n`.** 만장일치 쌍은 `Var = λ − Δ² = 0` 이라 MDE가 0으로 퇴화해 한 명 차이에도
   「차이 있음」이 된다. 정규근사가 p=1에서 무너지는 자리라 무사건 상한으로 바닥을 깔았다.

### 원본과 의도적으로 갈라진 곳 하나

`task_type.py` 는 원본(`_build/g3d/G3D_성능분석/perf_01_quant.py:31-42`)보다 **엄격하다**.
원본은 E 분기를 인증 분기보다 먼저 둬 **E1(인증만 다르고 가격 동일)을 우열형으로 통과**시켰고
실제로 4/4에 포함됐다. 여기서는 윤리 축이면 단독 차이여도 막는다.

이유: 그 허용을 받치는 근거가 **E1 한 쌍뿐**인 반면, 틀린 3쌍(H1·H3·B1)은 **전부** 인증 쌍이었다.
되돌리려면 `task_type.classify` 에서 윤리 검사를 단일속성 분기 **뒤로** 옮기면 된다.

### 교차층 결합 둘 — 깨지면 즉시 빨개진다

- `ai/tests/fixtures/twin_survey/survey.json` (골든 결과)
  ← `ai/tests/test_twin_golden.py` + `frontEnd/.../twinSurveyResult.test.js`
- `ai/tests/fixtures/twin_survey/gate_cases.json` (게이트 14사례)
  ← `ai/tests/test_twin_gate_parity.py` + `frontEnd/.../taskTypeGate.test.js`

두 번째가 특히 중요하다. 게이트 거울이 갈리면 **화면은 실행 버튼을 열어주고 서버가 422로 막는다**.

골든 픽스처에 정직한 경로가 들어 있다: n=100 가격형은 MDE 0.252 > Δ 0.180 이라 **「못 잼」**으로
떨어진다. 「차이 없음」이 아니다.

---

## 4. 남은 일 — 트랙 B

### 4-1. 등록은 두 곳이 아니라 **세 곳**이다

`CLAUDE.md` 는 두 곳이라고 하지만 틀렸다. 셋을 **원자적으로 함께** 고쳐야 한다.

- [ ] `ai/app/api/executions.py:21` `TASK_TYPES` 에 `"TWIN_SURVEY"`
- [ ] `ai/app/api/executions.py:185` 분기 체인에 `execute_twin_survey` (빠뜨리면 `:242` 에서 422)
- [ ] `ai/tests/test_internal_task_type_alignment.py` — `EXPECTED_TASK_TYPES` 추가 +
      **`len(java_task_types) == 13` → `== 14`**

세 번째가 FastAPI `TASK_TYPES` 와 Java enum 을 대조하며 개수를 하드코딩한다.
한쪽만 고치면 그 순간 pytest 가 깨진다. 트랙 A가 `TASK_TYPES` 를 건드리지 않은 이유다.

### 4-2. 백엔드 (`MARKET_RESEARCH`/V10 을 본으로)

- [ ] `taskrun/domain/TaskType.java` — `TWIN_SURVEY`
      (`task_runs.task_type` 은 VARCHAR(50) 무제약 → 이 값 자체는 마이그레이션 불필요)
- [ ] `taskrun/contract/TwinSurveyContract.java` — `exact()` 필드집합 일치 +
      **쌍별 결과마다 `caveats` 비어있지 않을 것** (`MarketResearchContract.java:111,150,189` 패턴)
- [ ] `integration/ai/AiServerClientConfiguration.java` — `aiServerSurveyRestClient`
      (`app.ai-server.survey-read-timeout`, 기본 **900초**)
- [ ] `taskrun/integration/InternalAiExecutionClient.java:120-123` — `clientFor()` 분기.
      **빠뜨리면 조용히 75초 클라이언트를 쓴다.**
- [ ] `journey/TwinSurveyRun|Version` + 리포지토리, `TwinSurveyService`, `TwinSurveyInputFactory`
- [ ] `journey/TwinSurveyWorker.java` — 패턴 B 폴러. `BUDGET=12분`, `LEASE=BUDGET+3분`,
      `maxAttempts=1`. **`MarketResearchWorker` 에 없는 `recover()` 를 넣는다**
      (`FinancialEstimateWorker.java:38-43`). 진행률은 `JobEventPublisher`.
- [ ] `pipeline/module/PipelineModuleType.java` — `FINANCE` 와 `MARKETING` 사이 `PANEL_SURVEY`
- [ ] `pipeline/module/ProjectModuleStatusService.java` — `List.of(...)` 에 FINANCE 다음 항목.
      **게이트를 새로 만들어야 한다**: 재무·마케팅은 원래 데이터로 연결돼 있지 않다
      (마케팅 게이트는 `selectedSnapshot` 기반). `requiredInputs` 를 `financialSnapshotId` 로 잡는다.
- [ ] `V11__twin_survey.sql` — **번호를 만들기 직전에 재확인**할 것

**부동소수점 금지**: taskInput 에는 `n`·가격(원, 정수)·문자열만. 실수는
`MarketResearchInputFactory:31-33` 수법대로 JSON 문자열로 감싸 `textContents` 에 넣는다.

### 4-3. 프론트 배선

- [ ] `twinSurveyApi.js`, `useTwinSurveyPolling.js`(2초, **캡 20분**)
- [ ] `projectModuleModel.js` — `finance` 다음 `panelSurvey`, **마케팅 8→9**, `API_MODULE_IDS`
- [ ] `projectRoutes.js` + `AppRouter.jsx` 라우트, 페이지 조립(부품은 다 있다)
- [ ] **순서 테스트 2개가 곧 계약이다** — `projectModuleModel.test.js:18-27`,
      `AppRouter.cutover.test.js:12-23`
- [ ] CSS 파일은 **일부러 안 만들었다**. 컴포넌트가 className 만 갖고 있으니 페이지 조립할 때 만들 것.

### 4-4. 배치

- [ ] `compose.yaml` — `TWIN_BANK_DIR: /app/app/twin/bank` +
      `./ai/app/twin/bank:/app/app/twin/bank:ro`
      (named volume 은 빈 채로 덮어쓴다 — **반드시 바인드 마운트**)
- [ ] `ai/.dockerignore` + `.gitignore` 에 `app/twin/bank`
- [ ] `_build/twin/out/` 의 3개 파일을 `ai/app/twin/bank/` 로 복사

### 4-5. 실스택 스모크 `scripts/twin-survey-smoke.ps1` — 빼지 말 것

DTO/record 를 먼저 읽고 짠다. 확인 항목:
n=50·2쌍이 12분 예산 안에 `COMPLETED` / 윤리형 자극이 **LLM 호출 0회**로 거절 /
뱅크 미마운트 시 `TWIN_BANK_UNAVAILABLE` / 응답 2 MiB 미만 / `caveats` 가 수치와 **같은 카드**에 렌더.

---

## 5. 경계 문구 — 값과 같은 자리에 둔다

이 저장소가 실제로 강제하는 방식은 배너가 아니라 **`caveats` 데이터**다
(`app/research/serialize.py` → 계약 검증 → 화면 → 회귀 테스트). 같은 통로를 쓴다.
`ai/app/twin/caveats.py` 에 이미 구현돼 있고 테스트가 문구 생존을 확인한다.

금지 표현: 「부분 검증됨」, 지위 라벨 없는 수치 인용, 크기·점유율 주장.

### 2026-08-10 — 비율 표기를 열었다 (규칙 변경)

옛 문구는 「크기·점유율·선택확률은 이 파이프라인이 산출하지 않는다」였고, 화면은 Δ·λ·MDE만
보였다. 페르소나 인터뷰 화면이 **응답 구성 비율**(「58% vs 34% · 미결정 8%」)을 헤드라인에
쓰기로 하면서 그 문장이 화면과 모순됐다.

**지운 것이 아니라 정확한 문장으로 바꿨다** — 막으려던 오독(시장 점유율로 읽는 것)은 그대로
막는다:

> 화면의 비율은 이 표본 응답자들의 구성이다 — 시장 점유율도 실제 구매확률도 아니다
> 판정이 말하는 것은 방향과 신뢰구간까지다. 차이의 크기는 이 설계가 답하지 못한다

**여전히 금지**인 것: 이 비율을 모집단으로 확대하는 문장, 「고객의 58%가 산다」류의 표현.
비율은 `respondentClasses ÷ nRespondents` 이고 계약이 그 원자료를 그대로 싣는다 —
프론트가 계산하므로 백엔드가 비율을 저장하지 않는다(저장하면 그것이 주장이 된다).

---

## 6. 함정

- **`git add -A` 금지.** 미추적 파일이 8,000개 넘는다(`ai/legal/출력/` 로컬 원자료).
  경로를 명시해 add 하고 `git diff --cached --name-only HEAD` 로 확인할 것.
- **내 것이 아닌 변경이 워킹트리에 있다.** 다른 세션이 BM 작업을 커밋하는 중이었다
  (`ai/app/research/*`, `frontEnd/src/features/market/*`, `useCellFocus.js` 등).
  건드리지 말 것. `git status` 로 매번 다시 셀 것.
- **프론트 테스트 판정은 `npm run test:run` 이 아니라 `npm run test:baseline` 이다.**
  현재 허용 실패 24건(물려받은 것). 새로 깨지는 것만 잡힌다.
- `frontEnd/src/features/market/EvidenceCard.jsx` 는 **존재하지 않는다**(옛 탐색 보고의 오류).
- 제품에 **페르소나·인터뷰 기능은 없다.** `CLAUDE.md` 의 "페르소나 → 인터뷰 → 종합" 서술은 낡았다.
  실제 체인은 `idea → concepts → conceptCompare → market → businessModel → techOps → finance → marketing`.

---

## 7. 다음 세션이 할 일

1. **0단계(`_build/g3e/`)를 돌린다.** 아직 안 돌렸다. 통과하면
   `ai/app/twin/caveats.py` 의 `INSTRUMENT_EQUIVALENCE_CONFIRMED` 를 `True` 로 바꾸는 것이
   전부다 — 그 상수를 읽는 곳은 그 파일 하나다. 기능은 지금도 나간다.
2. 게이트 앞단 연결: 지금 `PANEL_SURVEY` 는 `financialSnapshotId` 를 요구하지만,
   자극은 사용자가 손으로 만든다. 컨셉/재무 결과에서 자극 초안을 채워 주는 다리는 없다.
3. 물려받은 실패 2건(`ConceptFactoryReplacementIntegrationTests`·`IdeaBriefControllerTests`)은
   여전히 빨갛다. 이 작업과 무관하고 손대지 않았다.

---

## 8. 트랙 B 결과 (2026-08-10)

커밋 여섯 개. `993b2b7`(엔진·부품) → `1c6f1a8`(등록) → `955bca0`(뱅크) →
`82b0ad0`(백엔드) → `bc6a004`(프론트) → `57427a6`(스모크·수정 3건).

### 착수 전 문서가 틀렸던 곳

- **등록은 3곳이 아니라 5곳이다.** `TaskType.java`(정합성 테스트가 파일로 읽는다),
  `ProjectJobQueryService.module()`(exhaustive switch), `ActiveSurfaceCleanupTests`(enum 목록을
  통째로 못박는다)가 더 있었다.
- **`textContents` 봉투를 쓰지 않는다.** 그것은 시장조사 전용이고, 트윈 입력 모델은
  `extra="forbid"` 라 넣으면 400 이다. 트윈 입력엔 실수가 없어 감쌀 이유도 없었다.

### 실스택 스모크가 잡은 것 셋 (`scripts/twin-survey-smoke.ps1`)

1. `task_runs.subject_id` 가 NOT NULL — 첫 POST 가 500.
2. 거절 이유가 `AI_RESULT_INVALID` 로 접혔다. 화이트리스트 **두 곳**
   (`InternalAiExecutionClient.ERROR_REASONS`, `TaskRunService.mapPublic`)에 등록해야 산다.
3. 계약이 응답자 분류를 3종으로 못박았는데 실제는 5종이고 **나온 것만 실린다**.
   전원 미결정 실행이 통째로 폐기됐다.

스모크 자체도 한 번 거짓말을 했다 — PowerShell 5.1 이 본문을 ANSI 로 보내 한글 속성명이
깨졌고, 윤리·가치형이 게이트를 그냥 통과해 **막혔어야 할 조사가 실제로 돌았다**(LLM 206회).
본문을 UTF-8 바이트로 보내 고쳤다.

### 실측 성적

| 항목 | 결과 |
|---|---|
| 뱅크 | 컨테이너 안 카드 8,604 · 표집틀 8,604 |
| 뱅크 미마운트 | `TWIN_BANK_UNAVAILABLE` (조용한 빈 표본 아님) |
| 윤리·가치형 | `TWIN_TASK_TYPE_NOT_SERVICEABLE` · LLM 0회 |
| n=50·2쌍 | **76초** COMPLETED (예산 780초) · 셀 432 · 형식 위반 0 · 실패 0 |
| 응답 크기 | 14.5 KiB (상한 2 MiB) |
| 경계 | 쌍마다 7개 · `caveatCount` 물질화 확인 |
| 테스트 | ai 393 · backend 321 중 319(물려받은 2건 제외) · front 289 + 허용 22 |

---

## 9. 계기 동등성 재측정 (0단계) 결과 — 2026-08-10

**돌렸고, 판정은 「동등성 미확인」이다.** `INSTRUMENT_EQUIVALENCE_CONFIRMED` 는 `False`
그대로이고 화면에는 「성적 미전이」가 계속 병기된다. **기능은 나간다 — 주장만 작다.**

원장·판정은 모델별로 보관돼 있다 (`combine_csv/_build/g3e/`):

| 계기 | 셀 | H | N | λ | D | Δ | 파일 |
|---|---|---|---|---|---|---|---|
| gpt-4o-mini (n=100) | 3,358 | PASS | FAIL | PASS | **FAIL** | **FAIL** | `g3e_raw.gpt-4o-mini.jsonl` |
| gpt-5.6-terra (n=25) | 825 | PASS | PASS | PASS | **FAIL** | **FAIL** | `g3e_raw.gpt-5.6-terra.n25.jsonl` |

### 무엇이 갈렸나 — 같은 25명·같은 자극

| 쌍 | 질문 | CLI(300) | CLI(같은25) | gpt-4o-mini | gpt-5.6-terra |
|---|---|---|---|---|---|
| E2 | 신선 vs 냉동 · **동가** | +0.97 | +1.00 | +0.83 | +1.00 |
| E3 | 신선 3,000 vs 6,000 | +1.00 | +1.00 | +1.00 | +1.00 |
| E4 | 한국산 vs 칠레산 · **동가** | +0.99 | +0.96 | +1.00 | +1.00 |
| **B3** | 신선 5,000 vs 냉동 4,500 | +0.23 | +0.44 | **−0.68** | **+1.00** |
| **B4** | 신선 6,600 vs 냉동 4,500 | −0.42 | −0.36 | **−0.92** | **+0.72** |

**우열형 3쌍은 세 계기가 전부 일치했다. 가격형 2쌍은 셋 다 달랐다.**

세 계기 모두 카드를 읽는다(가구소득과 «비싼 쪽 선택»의 상관 +0.56 / +0.56 / +0.47).
갈리는 것은 민감도가 아니라 **지불의사의 절대 임계**다 — mini 는 임계가 높아 거의 전원이
싼 쪽(응답자 간 분산 0.077), terra 는 낮아 거의 전원이 비싼 쪽(0.269), CLI 만 사람마다
갈린다(0.655). 임계는 응답자 카드가 아니라 모델이 가진 값이라 **더 나은 모델을 찾는
문제로 풀리지 않는다.** 그래서 가격형을 막았다(§1).

교란 하나는 남는다: 이 시험은 전송(CLI→HTTP)만 바꾸도록 설계됐는데 제품 `AI_MODEL` 이
`gpt-4o-mini` 라 **모델까지 함께 바뀌었다.** 미달이 전송 탓인지 모델 탓인지 이 데이터로는
가르지 못한다. 다만 「지금 제품 설정이 그 성적을 재현하지 못한다」는 결론은 유효하다.

### 실행하며 고친 것

- `g3e_09_verdict.py` — 관문 N 이 `n_p=0`(전원 미결정)일 때 **출력에서** 죽었다. 판정 자체는
  실행 전부터 `None → 미달` 로 정해져 있었고, 표시만 「못 잼」으로 바꿨다.
- `g3e_runner.build_body` — `max_tokens` → `max_completion_tokens`.
  gpt-5.6-terra 가 옛 이름을 400 으로 거절한다. gpt-4o-mini 도 새 이름을 받는 것을 실측했다.
  ⚠ **제품 `ai/app/twin/runner.py` 는 아직 `max_tokens` 를 보낸다** — 이 모델로 서비스하려면
  같이 바꿔야 하고, 안 바꾸면 한 셀도 못 돈다.

---

## 10. 페르소나 인터뷰 화면 — 2026-08-10

목업 `~/Downloads/persona_interview_mockup.html` 대로 결과부를 다시 그렸다.
**판정 한 줄 + 구성 막대 + 대표 응답자 5명의 인터뷰.** Δ·λ·MDE 는 「측정치 보기」로 접었다.

- **계약이 바뀌었다** — `pair.rationaleExcerpts`(문자열 배열) → `pair.interviews`
  (`{choice, profile:{age,gender,household,region,income,job}, quote}` ≤5).
  `pid_hash` 는 싣지 않는다. 카드 원문도 싣지 않는다 — 6필드만 뽑는다(`ai/app/twin/profile.py`).
- **대표 선정은 결정론적이다** — 이긴 쪽 2 · 진 쪽 2 · 미결정 1, 성×연령 층이 겹치지 않게,
  `pid_hash` 오름차순. 위치응답자는 제외한다. 배분을 못 채우면 남은 사람으로 메운다
  (우열형은 한쪽이 만장일치에 가까워 «진 쪽»이 0명인 일이 흔하다 — 실측에서 카드가 3장만
  나와 잡았다).
- **프로필 파서는 8,604장 전수에서 6필드 100% 파싱**된다. 실패는 필드별 `None` 이고 예외를
  던지지 않는다.

### 실측 (2026-08-10)

| 항목 | 결과 |
|---|---|
| 윤리·가치형 / **가격형** | 둘 다 `TWIN_TASK_TYPE_NOT_SERVICEABLE` · LLM 0회 |
| n=50·우열형 2쌍 | **37초** COMPLETED · 셀 477 · 형식 위반 1 · 실패 0 |
| 인터뷰 | 쌍마다 **5장** · 프로필 6칸 완전 5장 |
| 응답 크기 | 17.3 KiB (상한 2 MiB) |
| 테스트 | ai **419** · backend 계약 green · front twin-survey **78** |

### ⚠ 프론트 게이트가 흔들린다 (이 작업과 무관)

`npm run test:baseline` 이 실행마다 다른 답을 낸다 — 같은 트리에서 예상 밖 실패가
0 / 1 / 2 / 3건으로 오갔다. 물려받은 통합 테스트(`App`·`Landing`·`Auth`·`ProjectPages`)가
렌더 대기에 걸려 부하에 밀리는 것이고, 그 파일들만 단독으로 돌리면 8회 연속 통과한다.
`--no-file-parallelism` 을 주면 흔들림이 줄지만(24→22~23) 없어지지는 않고 7분이 걸린다.

이 게이트는 구조적으로 flaky 를 다루지 못한다 — 허용목록에 넣으면 「통과했는데 목록에 있다」로,
빼면 「예상 밖 실패」로 빨개진다. **어느 쪽도 초록이 되지 않는다.** 판정은 당분간
`npx vitest run <바꾼 폴더>` 로 하고, 게이트 자체를 고치는 일은 별건으로 남긴다.

---

## 11. 다음 세션 착수점 — 「붙어 있는 기능」으로 만들기 (2026-08-11)

계획서 원본: `~/.claude/plans/c-users-user-downloads-persona-interview-snug-perlis.md`
(이 절만 읽어도 착수할 수 있게 요약해 둔다. 충돌하면 이 절이 아니라 **코드**가 정본이다.)

### 진단 — 왜 아직 못 쓰나

엔진·배관·화면은 끝났다. 못 쓰는 이유는 하나다: **자극을 사용자가 전부 손으로 만든다.**

- `TwinSurveyPage.jsx` 의 `INITIAL_PAIRS` 가 `attrs: { 형태: '' }` 빈 칸이다.
  속성명·양쪽 값·라벨·가격을 직접 타이핑해야 하고, 「가격은 양쪽 같게, 속성은 하나만」이라는
  규칙까지 사용자가 지켜야 한다. 시장분석은 컨셉 하나 고르고 누르면 끝이다.
- `TwinSurveyService`·`TwinSurveyInputFactory` 에 스냅샷·컨셉 참조가 **한 줄도 없다**.
  정작 재료는 마켓 시드 스냅샷에 있다 — `selectedConcept.solution.featureSet`,
  `finalHypotheses.differentiators`, `finalHypotheses.price`
  (`MarketAnalysisSeedSnapshotFactory` 참조).
- 트윈 결과를 읽는 곳은 `ProjectModuleStatusService` 하나뿐이다.

### 0단계 — 메뉴가 안 보이는 건 코드가 아니라 배포다

사이드바에 「8. 패널 트윈 조사」가 없고 마케팅이 아직 8번이면 **프론트 컨테이너가 낡은 것**이다.
2026-08-10 실측: 컨테이너 빌드 17:24 / 커밋 `bc6a004` 18:35, 번들에 `panel-survey` 0건.

```powershell
docker compose up -d --build frontend
```

⚠ 이후 작업마다 **frontend 도 같이 빌드**한다. 어제 ai-server·backend 만 올리고 빠뜨렸다.

### 할 일 (승인된 계획, 마케팅 연결은 제외)

1. **게이트 정정** — `ProjectModuleStatusService.twinOrGate` 가 컨셉·재무 **둘 다** 보게.
   `requiredInputs` 는 없는 것부터: `marketAnalysisSeedSnapshotId` → `financialSnapshotId`.
   실행이 있으면 게이트와 무관하게 그 상태를 보이는 규칙은 유지.
2. **자극 초안 AI 태스크 `TWIN_STIMULUS_DRAFT`** — 동기 인라인(패턴 A).
   `TaskRunService` 에 새 메서드가 **필요 없다**: `create()` → `claim(runId, workerId, lease,
   timeout)` → `startExecution()` → `client.execute()` → `adopt()`.
   AI 호출은 트랜잭션 밖(`TransactionSynchronizationManager` 방어를 `TwinSurveyWorker` 처럼).
   - 등록 **5곳**: `executions.py`(TASK_TYPES + 분기) · `test_internal_task_type_alignment.py`
     (**14 → 15**) · `TaskType.java` · `ProjectJobQueryService.module()` switch ·
     `ActiveSurfaceCleanupTests`
   - 입력: 스냅샷 전체가 아니라 `conceptName`·`targetUsers`·`problemScenario`·`featureSet`·
     `differentiators`·`price` 만. **부동소수점 금지**(가격은 원 단위 정수)
   - 출력: `{situation, pairs:[{pairId, axis, X, Y, rationale}]}` 3~4쌍.
     `app/twin/models.py` 의 `Side`·`Pair` 제약을 그대로 재사용해 검증
   - **뽑은 쌍을 `task_type.classify` 로 거른다** — 우열형이 아니면 버린다. 프롬프트로
     부탁하지 않고 코드로 막는다. 0쌍이면 정직하게 실패
   - 새 파일: `ai/app/twin/stimulus_draft.py` · `ai/prompts/twin_stimulus_draft/` ·
     `journey/TwinSurveyStimulusDraftService.java` · `taskrun/contract/TwinStimulusDraftContract.java`
   - 엔드포인트: `POST /api/v2/projects/{id}/twin-survey/stimulus-draft` (동기 200)
3. **화면** — `INITIAL_PAIRS` 빈 칸을 없애고 첫 화면은 「자극 초안 만들기」 버튼 하나.
   초안 3~4쌍을 카드로 보이고 체크로 고른 뒤 기존 `StimulusEditor` 로 다듬는다.
   `gateSurvey()` 거울을 그대로 쓴다. 0쌍이면 「차별점을 하나 이상 확정하라」로 막되
   손으로 만드는 길은 남긴다.
4. **문서** — `CLAUDE.md` §2 여정 서술이 아직 「페르소나 → 인터뷰 → 종합」이라는 **없어진 옛
   체인**이다. 실제 체인(`… techOps → finance → panelSurvey → marketing`)으로 고친다.

### 검증

```powershell
cd ai       ; python -m pytest -q
cd frontEnd ; npx.cmd vitest run src/features/twin-survey/
cd backend  ; .\gradlew.bat test --tests "*Twin*" --tests "*ModuleStatus*"
docker compose up -d --build frontend ai-server backend
```

⚠ 백엔드 전체 `gradlew test` 는 이 기계에서 10시간 넘게 걸린 적이 있다 — `--tests` 로 좁힌다.
⚠ 프론트 판정은 `test:baseline` 을 믿지 않는다(§10 — 실행마다 답이 달라진다).
`npx vitest run <바꾼 폴더>` 로 판정한다.

스모크에 무료 검사 2개를 더한다: **초안이 우열형만 돌려주는지**, **0쌍일 때 정직하게 실패하는지**.

### 손대지 않기로 한 것

- **마케팅 연결**(사용자 결정 2026-08-11). 붙일 자리는 조사해 뒀다 — AI 마케팅 입력이
  `{source: marketing-source-snapshot-v1, request: marketing-content-request-v1}` 이고
  `source` 는 계약이 굳어 있어 **`request` 에 선택 블록**을 더하는 것이 가장 얕다.
  손댈 곳: `MarketingContentService.enqueue()` · `ai/app/tasks/marketing_content/models.py` ·
  마케팅 프롬프트 · `MarketingResultContract`.
- 0단계 재측정 · 가격형 되살리기 · 프론트 게이트 flaky 수정.

### 워킹트리 주의

2026-08-11 기준 **다른 세션이 시장조사 화면 작업을 진행 중**이다
(`ai/app/research/*`, `frontEnd/src/features/market/*`, `MarketResearchContract` 등).
`git add -A` 금지. 경로를 명시해 add 하고 `git diff --cached --name-only HEAD` 로 확인한다.

---

## 12. §11 실행 결과 — 2026-08-11 (미커밋)

**§11 의 0~4단계를 전부 했다.** 아래는 결과이고, 충돌하면 이 절이 아니라 코드가 정본이다.

| 항목 | 결과 |
|---|---|
| 0. 프론트 컨테이너 | 재빌드함. 번들에 `panel-survey` 확인 |
| 1. 게이트 정정 | `twinOrGate` 가 컨셉·재무 둘 다 본다. `requiredInputs` 는 빠진 것을 여정 순서대로 전부 센다 |
| 2. `TWIN_STIMULUS_DRAFT` | 동기 인라인(패턴 A)으로 붙었다. 등록 5곳 + 계약 검증 |
| 3. 화면 | 첫 화면이 「자극 초안 만들기」 버튼 하나. 빈 표는 「직접 만들기」 뒤로 |
| 4. 문서 | `CLAUDE.md` §2 를 실제 체인으로 고쳤다 (라우터 경로도 `app/routing/` 으로 정정) |

### §11 계획과 갈린 지점 — 셋

1. **`ai/prompts/twin_stimulus_draft/` 를 만들지 않았다.** 그 디렉터리도 `_load_prompts`
   기구도 저장소에 **없다**(`git ls-files` 확인). 지금 관례대로 프롬프트를
   `stimulus_draft.py` 안에 뒀다 — `tasks/idea_brief/service.py` 와 같은 모양이다.
2. **LLM 이 속성 dict 도 가격도 만들지 않는다.** 「축 이름 하나 + 양쪽 값 둘」만 받고
   dict 는 시스템이 조립하며, 가격은 입력값을 양쪽에 그대로 얹는다. 그래서
   「속성을 둘 바꿨다」·「가격을 다르게 매겼다」가 **표현될 수 없다** — 프롬프트가 회귀해도
   가격형·다속성이 안 나온다. (OpenAI strict json_schema 가 자유 dict 를 못 받는 것도 겹친다.)
3. **확정 가격은 깨끗하게 읽히는 것만 넘긴다.** `finalHypotheses.price` 는 자유문장이라
   「월 9,900원」→9900, 「3만원」→null 이다. 억지로 읽어 틀린 가격을 자극에 앉히지 않는다.
   못 읽으면 양쪽 다 null 이고, 사용자가 편집기에서 넣으면 된다.

### 스모크에서 잡힌 것 — 스모크가 «검사하는 척»만 하고 있었다

`scripts/twin-survey-smoke.ps1` 의 파이썬 프로브는 stdin 으로 한글을 보내는데,
**PowerShell 5.1 의 기본 `$OutputEncoding` 이 ASCII** 라 자극의 속성이 양쪽 다 «?» 가 됐다.
그러면 게이트가 `IDENTICAL` 로 판정해 「뱅크 미마운트」 검사가 엉뚱한 이유로 통과/실패한다.
스크립트 첫머리에서 `$OutputEncoding` 을 UTF-8 로 못박았다. `Invoke-Json` 이 본문을 바이트로
만들어 보내는 것과 같은 종류의 지뢰이고, **호출자가 pwsh 인지 powershell.exe 인지에 따라
결과가 달라지고 있었다.**

무료 검사 3개를 더했다: 초안이 우열형만 돌려주는지 · 0쌍일 때 정직하게 실패하는지 ·
초안 엔드포인트가 실제로 서 있는지(컨셉 없는 프로젝트 → 404, LLM 0회).

### 검증한 것 / 안 한 것

- 통과: `ai` 431 passed · 백엔드 `*Twin*`·`*ModuleStatus*`·`*ActiveSurfaceCleanup*`·`*ProjectJob*` ·
  프론트 `twin-survey/`·`module-status/` 86 passed · 실스택 스모크(무료 구간) PASSED
- **안 했다: 초안의 유료 왕복.** 실제로 LLM 을 태워 초안을 받아 본 적은 없다.
  확정된 컨셉이 있는 프로젝트가 필요하고 돈이 든다. 프롬프트가 규칙을 얼마나 지키는지는
  **아직 실측되지 않았다** — 다만 안 지켜도 게이트가 버리므로 실패 방향은 「초안 0쌍」이다.
- 백엔드 전체 `gradlew test` 는 돌리지 않았다(이 기계에서 10시간 전례).

### 12-2. 견본 컨셉 경로 (2026-08-11, 사용자 결정)

**컨셉 파이프라인이 아직 안 찼다** — 실측: 프로젝트 15개 전부 `concept_selections` 0건,
`market_analysis_seed_snapshots` 0건. 스냅샷만 보던 초안 엔드포인트는 이 환경에서
**구조적으로 못 쓴다.** 시장조사와 **같은 규율**로 열었다:

- 화면이 견본 컨셉 **이름표만** 보낸다 (`beauty-noshow`·`household-ledger`·`pet-treat`)
- 재료는 AI 서버가 들고 있는 것을 쓴다 — 시장조사와 **같은 표**(`research.pipeline.CONCEPTS`).
  따로 들고 있으면 두 화면이 다른 컨셉을 본다
- **확정된 컨셉이 있으면 그것이 이긴다.** 이름표는 시연·시험용 길이지 기본값이 아니다
- 견본 → 초안 입력 대응: `name`→conceptName · `target`→targetUsers · `problem`→problemScenario ·
  `solution`→differentiators(자유문장) · `price_hypothesis_krw`→priceKrw(정수 아니면 null)
- 백엔드는 아는 이름 목록을 **들지 않는다**. 경로에 못 쓸 글자만 막고, 모르는 이름은 AI 가 막는다

**유료 왕복을 실측했다**(§12 에서 「안 했다」고 적은 그것이다). `beauty-noshow` 로 4쌍이
나왔고 전부 우열형, `dropped` 0, 가격은 양쪽 null, 라벨은 양쪽 다르다:
예약 통합 관리 · 노쇼 방지(예치금) · 자동 응답 · 대기자 통보(자동/수동).
**프롬프트가 규칙을 지켰다** — 다만 표본 1회다.

### 12-3. 화면 오류 문구 회귀 (2026-08-11)

컨셉 없는 프로젝트에서 초안을 누르면 「잠시 후 다시 시도해 주세요」가 나왔다.
서버는 404 로 **무엇을 해야 하는지** 말해 주는데(`retryable:false` + safeMessage),
`getUserErrorMessage` 가 **코드로만** 매핑해서 그 문구를 버린 것이다.
`draftFailureText.js` 가 재시도로 안 풀리는 실패에는 서버 문구를 그대로 보인다.

### 12-4. 배포가 화면에 안 닿던 이유 — `index.html` 캐시

`frontEnd/nginx.conf` 가 `index.html` 에 `Cache-Control` 을 안 붙여서 브라우저가
Last-Modified 로 임의 추정 캐시를 했다. 컨테이너에는 새 번들뿐인데 화면은 옛 화면이 나온다 —
서버만 봐서는 원인이 안 보이는 자리다. `index.html` 은 `no-cache`,
해시 붙은 `/assets/` 는 `immutable` 로 못박았다. **배포할 때마다 반복되던 문제다.**
