# 시장조사(research2) → AI 서버 통합 — 실험용 얇은 슬라이스

- 작성 2026-08-06
- 상태: **AI 서버 구간 구현·검증 완료. 도커 실행만 미확인**
- 범위: 붙는지만 확인한다. 백엔드 도메인·프론트·Flyway는 건드리지 않는다.

---

## 1. 왜 이 모양인가

`ai/app/legal/`이 이미 같은 문제를 푼 선례다 — 외부 API(law.go.kr) + 다단계 파이프라인 +
결정론 규칙. `executions.py`가 legal TaskType을 `execute_journey_task`(프롬프트 1회 → Pydantic
1개)로 보내지 않고 **전용 파이프라인으로 분기**시킨다. 시장조사도 같은 분기를 하나 더 탄다.

그 덕에 `AI_MODULE_INTEGRATION_GUIDE.md`가 경고한 이중 dict 지뢰
(`_load_prompts.folders` / `model_types`)를 **구조적으로 안 밟는다.**

```
POST /internal/v1/ai/executions   taskType=MARKET_RESEARCH
  └─ (공통 검증) 토큰 · correlation · contractVersion · deadline · canonical hash
  └─ validate_text_contents(input)         ← taskType 무관하게 무조건 돈다
  └─ app/research/runner.py
       subprocess: python run.py --from a4 --source-run <seed> --id <taskAttemptId>
                   cwd=/app/research2
       └─ runs/<taskAttemptId>/result.json (UTF-8) 파싱
```

## 2. 왜 `--from a4`(채점만)인가

동기 계약에 전 구간이 안 들어간다.

| | |
|---|---|
| `AI_PROVIDER_TIMEOUT_SECONDS` / `AI_SERVER_READ_TIMEOUT` / nginx | 60s / 75s / 90s |
| research2 전 구간 (27슬롯) | **3.5분 · LLM 82회** |
| research2 고정 슬롯 6개 | ~43s+ · LLM 18회 |
| **`--from a4` (저장된 수집 재채점)** | **수초 · LLM 0회 · 네트워크 0회** |

채점만 돌리면 시간 변수가 사라져서 **계약·배선·오류매핑만 정확히 잰다.** 붙는지 보는 데는
이게 제일 정확한 자다.

전 구간을 돌리려면 패턴 B(TaskRun 워커)로 옮겨야 하고, 그때
**`TaskRunWorker.validateResult()`에 분기 추가가 필수**다 — 빠뜨리면 AI 비용은 다 쓰고 결과만
조용히 버려진다. 컴파일 에러도 테스트 실패도 안 난다.

## 3. 배치 — 볼륨 마운트

research2는 git 미추적 로컬 자료이고 `runs/`만 119MB다. 이미지에 넣지 않는다.

```yaml
ai-server:
  environment:
    RESEARCH2_HOME: /app/research2
  volumes:
    - ./시장조사/research2:/app/research2:ro        # 코드·rules·data
    - ./시장조사/research2/runs:/app/research2/runs  # 여기만 쓰기
```

코드를 읽기전용으로 둔 이유: 서버가 규칙 파일을 못 망친다. `runs/`만 쓰기인 이유:
`runlog.RUNS_DIR = os.path.join(HERE, "runs")`가 **하드코딩이라 환경변수로 못 옮긴다.**

원본 `runs/`에 직접 쓰므로 실험 결과를 기존 `eval.py`·`viewer.html`로 바로 열어볼 수 있다.

## 4. 입력 계약

`executions.py`의 `validate_text_contents(body.input)`는 **taskType과 무관하게 무조건** 돈다.
그래서 새 TaskType도 청크·해시가 유효한 `textContents`를 반드시 실어야 한다.

```json
{ "textContents": [ … 청크·해시 유효 … ], "sourceRun": "route12-02" }
```

> ⚠ **얇은 슬라이스에서 `textContents`는 껍데기다.** 슬롯은 `sourceRun`의 저장된 수집에서
> 복원되므로 여기 컨셉을 넣어도 **반영되지 않는다.** 전 구간(A1부터)으로 갈 때 비로소 쓰인다.

`sourceRun` 같은 추가 최상위 키는 허용된다 — legal이 `validationMode`로 이미 그렇게 쓴다.
**부동소수점은 넣지 않는다** — canonical hash가 거부하고 런타임에만 터진다.

## 5. 출력

`result.json`은 88KB다. 그대로 실으면 붙은 건 알아도 뭐가 나왔는지는 안 보인다.
판정에 쓰는 것만 싣는다 (실측 2.9KB):

```
runId · sourceRun · fromStage · metrics · adapters · conclusion · ledger · notFound · coverageCaveat
```

`conclusion`·`ledger`를 반드시 포함한다 — **경계 문구(`scope_note`)가 거기 실려 있다.**
("전사 매출은 시장 내 매출이 아니라 상한선으로만 읽을 것" 등). 지우면 안 되는 표시다.

## 6. 실패 매핑

모르는 것을 성공으로 덮지 않는다 — research2 자신의 규율(`unknown_code` → `stopped`)과 같다.

| research2 | 계약 | retryable |
|---|---|---|
| `sourceRun` 형식 위반 (경로 탈출 포함) | `INVALID_REQUEST` / 400 | false |
| `sourceRun` 디렉터리 없음 | `INVALID_REQUEST` / 400 | false |
| `--id` 이미 존재 | `INVALID_REQUEST` / 400 | false |
| 서브프로세스 non-zero exit | `MODEL_EXECUTION_FAILED` / 502 | true |
| deadline 초과 | `DEADLINE_EXCEEDED` / 504 | true |
| `result.json` 없음·파싱 실패·모양 이상 | `RESULT_SCHEMA_INVALID` / 502 | false |
| 파이썬 실행 자체 불가 | `DEPENDENCY_UNAVAILABLE` / 503 | true |

`--id` 중복을 막는 이유: `run.jsonl`이 **append-only**라 같은 id로 두 번 돌리면 지표가 2배로
보인다. 조용히 틀리는 종류다.

stdout은 판정에 쓰지 않는다 — Windows 콘솔에서 CP949로 깨진다. 정본은 `result.json`(UTF-8)이다.

## 7. 성공 기준

씨앗 실행 `route12-02`를 `--from a4`로 재채점하면 결정론적으로 같은 값이 나온다.
로컬에서 `route12-02-yearfix`와 `metrics` **완전 일치(diff 0)** 확인함.

```
사실 2 · 확인됨(원장) 2 · adapters {kosis: ok, web: ok} · LLM 0회
```

이 값이 계약을 타고 그대로 나오면 성공이다. 다르면 붙긴 붙었는데 뭔가 갈린 것이고,
그 자체가 실험 결과다.

## 8. 손댄 파일

| 파일 | 무엇 |
|---|---|
| `ai/app/research/runner.py` (신규) | 서브프로세스 호출 · result.json 파싱 · 실패 매핑 |
| `ai/app/api/executions.py` | TaskType **두 집합 모두** + 분기 |
| `ai/requirements.txt` | `openai` · `requests` · `trafilatura` |
| `ai/tests/test_market_research.py` (신규) | 계약 테스트 (research2 없으면 skip) |
| `compose.yaml` | 마운트 + `RESEARCH2_HOME` |
| `backend/.../taskrun/domain/TaskType.java` | enum 값 1개 |
| `ai/tests/test_internal_task_type_alignment.py` | 13 → 14 |

마지막 둘은 **원래 계획에 없었다.** `test_internal_task_type_alignment`가 FastAPI ≡ Java enum
≡ 13개를 삼중 등식으로 강제한다. AI 서버만 고치면 깨진다. 테스트를 느슨하게 만드는 대신
Java enum에도 넣어 맞췄다 — 그 가드는 정확히 이 표류를 잡으려고 있는 것이다.

Java enum 값 추가는 무해함을 확인했다: TaskType에 대한 switch 없음, `task_type` 컬럼은 제약
없는 `VARCHAR(40)/(50)`이라 마이그레이션 불필요, 이 TaskType으로 `TaskRun`을 만드는 코드 없음.

## 9. 실험이 끝나면 되돌리는 법

`compose.yaml`의 `volumes` 블록과 `ai/requirements.txt`의 세 줄을 지우면 끝난다.
`ai/app/research/`와 테스트는 남겨도 무해하다(마운트가 없으면 skip된다).

## 10. 다음 단계 — 조건과 함께

| # | 무엇 | 조건 |
|---|---|---|
| 1 | 고정 슬롯 6개 **실수집** | 도커 실행 확인 후. 60s 프로바이더 타임아웃 경계를 실측한다 |
| 2 | 전 구간(A1부터) | 패턴 B 전환이 전제. `TaskRunWorker.validateResult()` 분기 필수 |
| 3 | 사이드카 컨테이너로 분리 | 제품화 시. ai-server 이미지(원래 4개 의존성)를 안 더럽힌다 |
| 4 | 백엔드 도메인·프론트 | 실험이 붙고 나서. 가이드 §1의 26개 파일 |
