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
| 명백한 우열형 | 관문 3 **E 4/4 정식 통과** (λ=0.988, MDE₃₀₀=0.0136) | 제공 |
| 가격형 | **B 3/4 모듈 성적** (λ=0.823, MDE₃₀₀=0.1359) | 제공 — 지위·B1 오답 병기 |
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
