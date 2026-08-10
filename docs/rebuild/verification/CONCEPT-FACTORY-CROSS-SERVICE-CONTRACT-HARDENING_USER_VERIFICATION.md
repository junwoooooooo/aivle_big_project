# Concept Factory Cross-Service Contract Hardening 사용자 검증

## 목적과 경계

새 Run에서 Backend→AI 21필드 계약, run-fatal taxonomy, 폐기 Timeline/DB/UI 정합성, Job Center 선택 표시를 검증한다. 기존 실패 Run `52bd0ee4-1353-4cc7-8940-d02d8c01bda9`, `e8ca55a3-03b7-48a3-800a-e52a7cf6468d`는 수정하거나 resume하지 않는다. Docker volume을 삭제하지 않는다.

## 1. 사전 확인

저장소 루트 PowerShell에서 실행한다.

```powershell
git branch --show-current
git status --short
docker --version
docker compose version
```

기대 결과:

- branch: `rebuild/new-pipeline-v1`
- Docker daemon 사용 가능
- `.env`의 Provider, 내부 service token, DB, MinIO 필수 설정 존재
- secret 값을 화면이나 공유 로그에 출력하지 않음

## 2. V8 포함 이미지 build 및 시작

Debug Trace를 확인하려면 build 전에 다음 값을 설정한다.

```powershell
$env:VITE_ENABLE_PIPELINE_DEBUG = 'true'
docker compose -f compose.yaml -f compose.e2e.yaml build ai-server backend frontend
docker compose -f compose.yaml -f compose.e2e.yaml up -d
docker compose -f compose.yaml -f compose.e2e.yaml ps
```

`postgres`, `minio`, `ai-server`, `backend`, `frontend`가 healthy여야 한다. `down -v`, volume 삭제, DB 초기화는 실행하지 않는다.

## 3. V8 migration 확인

```powershell
docker compose -f compose.yaml -f compose.e2e.yaml logs backend --since 10m | Select-String 'Flyway|V8|migration|ERROR'
docker compose -f compose.yaml -f compose.e2e.yaml exec -T postgres psql -U aivle -d aivle -c "select version,description,success from flyway_schema_history order by installed_rank;"
docker compose -f compose.yaml -f compose.e2e.yaml exec -T postgres psql -U aivle -d aivle -c "select column_name,data_type,is_nullable from information_schema.columns where table_name='concept_rejection_summaries' and column_name='attempt_id';"
```

성공 기준:

- V8 `concept factory cross service contract hardening` 성공
- `concept_rejection_summaries.attempt_id` 존재
- 기존 데이터와 volume 유지

## 4. 새 Concept Factory Run

1. `http://localhost:3000`을 연다.
2. 기존 confirmed Idea Brief를 사용한다. Idea Brief를 다시 만들 필요는 없다.
3. 기존 실패 Run의 `이어서 시도`를 누르지 않는다.
4. `처음부터 새로 만들기`로 새 Run을 한 번만 생성한다.
5. 반환/화면의 새 `<RUN_ID>`와 Job Center의 `<TASK_RUN_ID>`를 기록한다.
6. Slot 1 이후 Slot 2가 실제 후보 생성 단계에 진입하는지 관찰한다.

버튼 pending 중 중복 클릭하지 않는다. Provider rate limit이 발생하면 즉시 반복 실행하지 않는다.

## 5. Cross-service 오류 0건 확인

```powershell
$AiLogs = docker compose -f compose.yaml -f compose.e2e.yaml logs ai-server --since 30m
$ContractErrors = $AiLogs | Select-String 'input\.(acceptedConceptFingerprints|rejectedConceptFingerprints|currentSlotPreviousFingerprints).*extra_forbidden'
$ContractErrors.Count
$AiLogs | Select-String 'CONCEPT_CANDIDATE|CONCEPT_DISTINCTNESS_JUDGE|CONCEPT_REDESIGN|CONCEPT_LEGAL_REVIEW|FIELD_CONSTRAINT_VIOLATION|REQUEST_CONTRACT_INVALID|extra_forbidden'
```

첫 번째 성공 기준은 `$ContractErrors.Count`가 `0`인 것이다. `FIELD_CONSTRAINT_VIOLATION`이 다른 입력에서 발생했다면 field path와 safe reason만 수집하고 raw prompt/provider body는 공유하지 않는다.

## 6. Slot 진행과 Distinctness 확인

```powershell
docker compose -f compose.yaml -f compose.e2e.yaml logs backend --since 30m | Select-String 'Concept Factory|CONCEPT_CANDIDATE|CONCEPT_DISTINCTNESS_JUDGE|REQUEST_CONTRACT_INVALID|FIELD_CONSTRAINT_VIOLATION|rejected|redesign|fatal'
```

성공 기준:

- Slot 2가 실제 `CONCEPT_CANDIDATE` 호출에 진입
- 첫 Slot의 요청 계약 오류 때문에 Slot 2~5가 반복 실패하지 않음
- deterministic comparison이 `AMBIGUOUS`가 된 경우에만 `CONCEPT_DISTINCTNESS_JUDGE`가 호출되고 21필드 입력을 정상 처리
- 실제 `REQUEST_CONTRACT_INVALID`가 발생했다면 그 Run은 즉시 `FAILED`, `retryable=false`, `canResume=false`이며 이후 Slot Attempt가 없어야 함

## 7. DB 정합성 확인

아래 `<RUN_ID>`를 새 Run ID로 교체한다. `.env`에서 DB 사용자/DB 이름을 바꿨다면 `aivle`을 실제 값으로 바꾼다.

```powershell
docker compose -f compose.yaml -f compose.e2e.yaml exec -T postgres psql -U aivle -d aivle -c "select id,status,replacement_rounds,inspected_candidate_count,provider_transient_retry_count,task_run_id,updated_at from concept_factory_runs where id='<RUN_ID>';"
docker compose -f compose.yaml -f compose.e2e.yaml exec -T postgres psql -U aivle -d aivle -c "select slot_number,variation_focus,status,legal_redesign_count,replacement_rounds,attempt_count,updated_at from concept_slots where run_id='<RUN_ID>' order by slot_number;"
docker compose -f compose.yaml -f compose.e2e.yaml exec -T postgres psql -U aivle -d aivle -c "select s.slot_number,a.id as attempt_id,a.attempt_number,a.phase,a.error_classification,a.safe_error_code,a.retryable,(a.result_json is not null) as has_result,a.task_run_id from concept_attempts a join concept_slots s on s.id=a.slot_id where s.run_id='<RUN_ID>' order by s.slot_number,a.attempt_number;"
docker compose -f compose.yaml -f compose.e2e.yaml exec -T postgres psql -U aivle -d aivle -c "select s.slot_number,r.attempt_id,r.reason_code,r.safe_summary,r.created_at from concept_rejection_summaries r join concept_slots s on s.id=r.slot_id where s.run_id='<RUN_ID>' and r.deleted_at is null order by r.created_at;"
docker compose -f compose.yaml -f compose.e2e.yaml exec -T postgres psql -U aivle -d aivle -c "select count(*) as discard_rows,count(distinct r.attempt_id) as distinct_discard_attempts from concept_rejection_summaries r join concept_slots s on s.id=r.slot_id where s.run_id='<RUN_ID>' and r.deleted_at is null;"
docker compose -f compose.yaml -f compose.e2e.yaml exec -T postgres psql -U aivle -d aivle -c "select count(*) as rejection_events from job_events where task_run_id=(select task_run_id from concept_factory_runs where id='<RUN_ID>') and event_type='job.concept.slot.rejected' and deleted_at is null;"
```

성공 기준:

- `discard_rows == distinct_discard_attempts`
- Timeline rejection event 수와 해당 Run의 discard row 수 일치
- `LEGAL_REDESIGN_EXHAUSTED`가 발생한 Candidate는 `reason_code`와 non-null `attempt_id`를 가진 summary 1건 존재
- Provider/request contract failure Attempt는 rejection summary에 없음
- API/화면의 검토 완료 후보는 `inspected_candidate_count`와 일치

## 8. Summary metric 확인

Concept Factory 화면에서 다음 항목을 확인한다.

- 법률검토 통과: ELIGIBLE Slot 수 / 5
- 신규 후보 생성: INITIAL 성공 수
- 대체 후보 생성: REPLACEMENT 성공 수
- 재설계 성공: 성공한 REDESIGN 수
- 검토 완료 후보: persisted `inspected_candidate_count`
- 폐기 후보: `concept_rejection_summaries` 수
- 생성/시스템 실패: result 없이 오류로 끝난 비법률 Attempt 수
- Provider 재시도: persisted transient retry 수

새로고침 전후 숫자가 SSE event 개수에 따라 증감하지 않고 DB 값과 일치해야 한다.

## 9. Debug Trace 확인

`VITE_ENABLE_PIPELINE_DEBUG=true`로 build했다면 `실행 상세 보기`에서 failure event에 다음 항목이 표시되는지 확인한다.

- 분류: `errorClassification`
- 오류: `safeErrorCode`
- 진단: `safeReason`
- 필드: `failedField`
- 재시도 가능

raw prompt, raw Provider body, Candidate 전체 원문, stack trace, API key/token은 표시되면 안 된다.

## 10. Job Center 확인

1. 새 active Concept Factory job이 생성되면, 과거 작업을 수동 선택하지 않은 상태에서 해당 job이 자동 선택되는지 확인한다.
2. active job이 terminal이 되면 같은 job Timeline이 유지되는지 확인한다.
3. 과거 job을 직접 선택한 뒤 새로고침해도 선택이 강제로 바뀌지 않는지 확인한다.
4. Timeline header가 `CONCEPT FACTORY RUN · 진행 중/실패 · 시각` 형태로 현재 선택을 표시하는지 확인한다.
5. 종료 notice가 `CONCEPT FACTORY RUN 작업이 실패/완료 상태로 종료되었습니다.`처럼 task type을 포함하는지 확인한다.

## 11. 전체 Runtime 성공 기준

- fingerprint `extra_forbidden` 0건
- Slot 2+ 실제 Candidate 호출
- AMBIGUOUS가 발생한 경우 Distinctness Judge 정상 처리
- 실제 폐기마다 Timeline/DB/UI +1 일치
- Run이 계속 진행돼 Slot 1~5 `ELIGIBLE`
- 공개 Concept 5개

Selection, 7 Hypotheses, Market Seed finalize와 module handoff는 이 계약 hardening의 후속 제품 Runtime acceptance이며, Concept 5개 공개가 성공한 뒤 기존 검증 절차로 진행한다.

## 12. 실패 시 수집 자료

- 새 Project ID, Run ID, TaskRun ID
- 최초 실패 시각과 Slot/phase/task type
- 안전하게 필터링한 Backend/AI 로그
- Debug Trace의 classification/code/reason/failedField/retryable
- 위 DB 조회 결과

API key, Authorization, internal service token, raw prompt, raw Provider body, Candidate 전체 JSON은 제거한다. 첫 동일 오류가 확인되면 새 Run을 반복 생성하지 말고 자료를 수집한다.
