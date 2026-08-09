# Concept Factory 런타임 완성 사용자 검증

## 목적

자동 검증에서 수행하지 못한 실제 Provider smoke와 Docker 신규 Run E2E를 검증한다. 기존 실패 Run의 counter나 DB row를 수정하지 말고 반드시 새 Run을 사용한다.

## 1. 사전 확인

저장소 루트에서 실행한다.

```powershell
git branch --show-current
git status --short
docker --version
docker compose version
```

기대 결과:

- 브랜치: `rebuild/new-pipeline-v1`
- Docker daemon 사용 가능
- `.env`에 필요한 값이 설정됨
- 인증정보 값은 터미널이나 보고서에 출력하지 않음

## 2. 이미지 생성과 서비스 시작

기존 persistent volume을 삭제하지 않는다.

```powershell
docker compose -f compose.yaml -f compose.e2e.yaml build ai-server backend frontend
docker compose -f compose.yaml -f compose.e2e.yaml up -d
docker compose -f compose.yaml -f compose.e2e.yaml ps
```

`postgres`, `minio`, `ai-server`, `backend`, `frontend`가 healthy여야 한다.

## 3. Migration 확인

```powershell
docker compose -f compose.yaml -f compose.e2e.yaml logs backend --since 10m | Select-String 'Flyway|V7|migration|ERROR'
```

DB에서 확인한다. 기본 사용자/DB가 다르면 `.env` 설정값으로 변경한다.

```powershell
docker compose -f compose.yaml -f compose.e2e.yaml exec -T postgres psql -U aivle -d aivle -c "select version, description, success from flyway_schema_history order by installed_rank;"
docker compose -f compose.yaml -f compose.e2e.yaml exec -T postgres psql -U aivle -d aivle -c "select column_name, data_type, column_default from information_schema.columns where table_name='concept_slots' and column_name='replacement_rounds';"
```

기대 결과: V7 성공, `replacement_rounds` 존재.

## 4. 실제 Provider smoke

한 번만 직렬 실행한다.

```powershell
docker compose -f compose.yaml -f compose.e2e.yaml exec -T ai-server python -m app.tools.concept_factory_provider_smoke
```

기대 결과:

- CandidateV2 생성 성공
- 법률 검토 결과 생성 성공
- REDESIGNABLE이면 REDESIGN 성공
- `floating-point JSON numbers are not canonical task input` 없음
- secret/raw prompt/raw provider response가 출력되지 않음

429가 발생하면 즉시 반복 실행하지 말고 `Retry-After` 또는 충분한 대기 후 한 번만 재검증한다.

## 5. 신규 Project / Idea Brief

브라우저에서 `http://localhost:3000`을 연다.

새 프로젝트를 만든 뒤 다음 아이디어로 Idea Brief를 생성·derive·확인·confirm한다.

> 사용자의 식습관, 선호 재료, 알레르기를 바탕으로 일주일 치 밑반찬 식단을 만들고 1~2회분만 소분해 주 1~2회 신선 배송하는 구독 서비스. 1인 가구의 잔반, 메뉴 고민, 배달음식 과식, 같은 반찬 반복 문제를 줄인다. 주요 사용자는 요리할 시간이 부족하고 집밥을 원하는 2030 및 건강하고 다양한 반찬을 원하는 가정이다.

`targetRegion`은 `대한민국`으로 확정한다.

## 6. Concept Factory 신규 Run

1. `5개 컨셉 만들기`를 한 번만 누른다.
2. 버튼 pending 중 중복 클릭이 불가능한지 확인한다.
3. Slot 1~5 진행을 관찰한다.
4. `VITE_ENABLE_PIPELINE_DEBUG=true` 환경에서는 `실행 상세 보기`를 연다.
5. 429가 보이면 즉시 replacement가 발생하지 않고 backoff 후 같은 작업이 재시도되는지 확인한다.
6. retry가 소진되면 나머지 Slot 호출이 중단되고 Run이 `FAILED`, `canResume=true`가 되는지 확인한다.
7. `이어서 시도`는 새 idempotency key로 한 번만 실행한다.

성공 기준:

- Slot 1~5 모두 `ELIGIBLE`
- Run `COMPLETED`
- 공개 Concept 정확히 5개
- 이름만 바꾼 clone 없음
- 동일 core idea를 유지하면서 다섯 focus 축이 실질적으로 다름
- 429/timeout이 `후보 폐기`나 `대체 생성`으로 집계되지 않음

## 7. 로그 확인

```powershell
docker compose -f compose.yaml -f compose.e2e.yaml logs backend --since 30m | Select-String 'Concept Factory|taskType|provider|RATE_LIMITED|retry|rejected|redesign|legal|terminal'
docker compose -f compose.yaml -f compose.e2e.yaml logs ai-server --since 30m | Select-String 'CONCEPT_CANDIDATE|CONCEPT_REDESIGN|CONCEPT_LEGAL_REVIEW|code=|reason=|retryable=|schemaName=|upstreamStatus='
```

다음 문자열이 있으면 실패다.

- `floating-point JSON numbers are not canonical task input`
- 정상적인 domain 15개 소진이 아닌 `candidate inspection limit exceeded`
- 429 직후 밀리초 단위 연속 호출 storm
- `RATE_LIMITED` 직후 Candidate replacement

로그를 공유할 때 API key, Authorization, raw prompt, Provider raw body는 제거한다.

## 8. DB 정합성 확인

아래 `<RUN_ID>`를 신규 Run ID로 교체한다.

```powershell
docker compose -f compose.yaml -f compose.e2e.yaml exec -T postgres psql -U aivle -d aivle -c "select id,status,replacement_rounds,inspected_candidate_count,provider_transient_retry_count,task_run_id,updated_at from concept_factory_runs where id='<RUN_ID>';"
docker compose -f compose.yaml -f compose.e2e.yaml exec -T postgres psql -U aivle -d aivle -c "select slot_number,variation_focus,status,legal_redesign_count,replacement_rounds,attempt_count,updated_at from concept_slots where run_id='<RUN_ID>' order by slot_number;"
docker compose -f compose.yaml -f compose.e2e.yaml exec -T postgres psql -U aivle -d aivle -c "select s.slot_number,a.attempt_number,a.phase,a.error_classification,a.safe_error_code,a.retryable,(a.result_json is not null) as has_result,a.task_run_id from concept_attempts a join concept_slots s on s.id=a.slot_id where s.run_id='<RUN_ID>' order by s.slot_number,a.attempt_number;"
docker compose -f compose.yaml -f compose.e2e.yaml exec -T postgres psql -U aivle -d aivle -c "select count(*) as dangling_attempts from concept_attempts a join concept_slots s on s.id=a.slot_id join concept_factory_runs r on r.id=s.run_id where r.id='<RUN_ID>' and r.status in ('COMPLETED','FAILED','NEEDS_INPUT','STALE') and a.result_json is null and a.error_classification is null and a.safe_error_code is null;"
docker compose -f compose.yaml -f compose.e2e.yaml exec -T postgres psql -U aivle -d aivle -c "select count(*) as public_concepts from concepts where run_id='<RUN_ID>' and published=true and deleted_at is null;"
```

기대 결과:

- dangling Attempt 0
- 완료 Run의 public Concept 5
- Provider 실패 Attempt에는 result 없음, 안전 error 있음
- 실제 Candidate 결과가 있는 Attempt만 검토 후보 통계에 반영
- Provider 실패 REDESIGN은 `legal_redesign_count`를 소비하지 않음

## 9. 통계와 시간 확인

Concept Factory 요약에서 다음 값이 새로고침과 retry 전후에도 DB 상태와 일치하는지 확인한다.

- 법률검토 통과
- 생성 성공 후보
- 생성 실패
- 검토 후보
- 재설계
- 대체 생성
- 폐기
- Provider 재시도

Slot 최근 갱신과 Timeline 시간이 같은 실제 시각을 가리켜야 한다. Asia/Seoul 브라우저에서 UTC `06:21`은 `15:21`로 표시돼야 한다.

## 10. Job Center 확인

retry가 한 번 이상 발생한 경우:

- initial FAILED TaskRun은 `최근 실패`와 `이전 실행`으로 표시
- retry RUNNING TaskRun은 `진행 중인 작업`과 `현재 실행`으로 표시
- 선택한 TaskRun의 Timeline만 표시
- 이전 실패 종료 문구와 현재 Run 진행 상태가 모순되지 않음
- debug 모드에서만 상세 식별정보 확인 가능

## 11. Selection / Market E2E

1. 공개된 Concept 1개를 선택한다.
2. 정확히 7개 Hypothesis decision이 생성되는지 확인한다.
3. 모두 accept 또는 edit한다.
4. Market Analysis Seed를 finalize한다.
5. Snapshot body에서 `originalSeed`, `aiInterpretation`, `selectedConcept`, `finalHypotheses`, `legalResult`를 확인한다.
6. `MARKET_ANALYSIS` module handoff가 생성됐는지 확인한다.

```powershell
docker compose -f compose.yaml -f compose.e2e.yaml exec -T postgres psql -U aivle -d aivle -c "select hypothesis_type,decision_status from concept_hypothesis_decisions where selection_id in (select id from concept_selections where project_id=<PROJECT_ID>) order by hypothesis_type;"
docker compose -f compose.yaml -f compose.e2e.yaml exec -T postgres psql -U aivle -d aivle -c "select id,concept_id,snapshot_hash,finalized_at from market_analysis_seed_snapshots where project_id=<PROJECT_ID> order by created_at desc limit 1;"
docker compose -f compose.yaml -f compose.e2e.yaml exec -T postgres psql -U aivle -d aivle -c "select module,input_snapshot_id,input_snapshot_hash,input_snapshot_json,status from module_handoffs where project_id=<PROJECT_ID> and module='MARKET_ANALYSIS' order by created_at desc limit 1;"
```

## 12. 실패 시 수집 자료

- 신규 Project ID, Run ID, 선택한 TaskRun ID
- 안전하게 필터링한 backend/ai-server 로그
- `실행 상세 보기`의 slot/phase/task/attempt/error/reason/failedField/retryable
- 위 DB 조회 결과
- 실패한 단계와 최초 발생 시각

첫 실패가 발생하면 반복 Provider 호출을 중단하고 자료를 수집한다.
