# Concept Factory 런타임 안정화 사용자 검증

## 사전 조건

- 저장소 루트에서 실행한다.
- `.env`의 Provider, MOLEG, DB, 내부 서비스 토큰 설정이 유효해야 한다.
- 아래 검증은 사용자가 직접 수행한다.
- 실패가 발생하면 같은 고비용 테스트를 반복하지 말고 해당 단계에서 자료를 수집한다.

예상 소요는 Provider smoke 1회 1~5분, 5회 반복 5~25분, 실제 5 Slot 실행 5~30분 범위다. Provider와 공식 법령 API 상태에 따라 달라질 수 있다.

## Test 1. 서비스 상태

```powershell
docker compose ps
```

성공 기준: `postgres`, `ai-server`, `backend`, `frontend`가 실행 중이며 재시작 루프가 없다.

실패 시:

```powershell
docker compose logs --tail 200 backend ai-server postgres
```

## Test 2. Provider smoke 1회

```powershell
docker compose exec ai-server python -m app.tools.concept_factory_provider_smoke
```

성공 기준: JSON 결과가 출력되고 process exit code가 0이다. `targetRegion=대한민국` LOCKED fixture가 semantics 추측 오류로 실패하지 않아야 한다.

실패 시 같은 명령을 반복하지 말고 다음 로그를 수집한다.

```powershell
docker compose logs --since 10m ai-server backend | Select-String "CONCEPT|Concept|schema|validation|REDESIGN|ERROR|WARN"
```

## Test 3. Provider smoke 5회 반복

Test 2가 통과한 경우에만 실행한다.

```powershell
1..5 | ForEach-Object { docker compose exec -T ai-server python -m app.tools.concept_factory_provider_smoke }
```

성공 기준: 5회 모두 exit code 0이고 `locked targetRegion semantics must be preserved`가 발생하지 않는다.

## Test 4. 실시간 안전 로그

별도 PowerShell 창에서 실행한다.

```powershell
docker compose logs -f backend ai-server | Select-String "Concept|CONCEPT|REDESIGN|schema|validation|ERROR|WARN"
```

성공 기준: 실패 시 task type, TaskRun/Attempt ID, correlation ID, code/reason, schema와 안전 validation field가 보인다. API key, 토큰, Authorization, raw prompt, Provider raw body는 보이면 안 된다.

## Test 5. 실제 신규 Project부터 Concept Factory까지

1. 신규 Project를 만든다.
2. Idea에 `ideaOverview`, `problem`, `targetUsers` 세 필드만 입력한다.
3. optional lock은 비워 둔다.
4. AI Interpretation을 확인하고 최종 Confirm한다.
5. Concept Factory를 시작한다.

성공 기준:

- generation strategy가 `EXPLORE`
- 5개 Slot 모두 `ELIGIBLE`
- Run은 5/5 이후에만 `COMPLETED`
- 이름만 다른 중복 후보가 반복 공개되지 않음
- REDESIGN이 발생하면 재설계 후보가 schema/origin/distinctness/legal을 다시 통과함
- `VITE_ENABLE_PIPELINE_DEBUG=true` 환경에서는 “실행 상세 보기”에 phase/task/attempt/error/duration이 표시됨

## Test 6. Concept Run·Slot·Attempt DB 확인

화면/API에서 확인한 Run ID를 넣는다. 기본 compose DB 이름과 사용자는 `aivle`이며 `.env`에서 변경했다면 맞춰 수정한다.

```powershell
$runId = "<RUN_ID>"
docker compose exec postgres psql -U aivle -d aivle -c "SELECT id, status, replacement_rounds, inspected_candidate_count, provider_transient_retry_count, task_run_id FROM concept_factory_runs WHERE id = '$runId';"
docker compose exec postgres psql -U aivle -d aivle -c "SELECT id, slot_number, variation_focus, status, attempt_count, legal_redesign_count FROM concept_slots WHERE run_id = '$runId' ORDER BY slot_number;"
docker compose exec postgres psql -U aivle -d aivle -c "SELECT cs.slot_number, ca.attempt_number, ca.phase, ca.error_classification, ca.safe_error_code, ca.retryable, ca.result_json IS NOT NULL AS has_result, ca.created_at, ca.updated_at FROM concept_attempts ca JOIN concept_slots cs ON cs.id = ca.slot_id WHERE cs.run_id = '$runId' ORDER BY cs.slot_number, ca.attempt_number;"
```

성공 기준:

- Slot 5개가 모두 `ELIGIBLE`
- schema/content 오류가 `INTERNAL_EXECUTION_ERROR`로 덮이지 않음
- rejected attempt는 result와 error가 함께 존재할 수 있음
- failed attempt는 error와 safe error code가 함께 존재함

terminal Run 이후 dangling Attempt 확인:

```powershell
docker compose exec postgres psql -U aivle -d aivle -c "SELECT ca.id, cs.slot_number, ca.attempt_number, ca.phase FROM concept_attempts ca JOIN concept_slots cs ON cs.id = ca.slot_id WHERE cs.run_id = '$runId' AND ca.result_json IS NULL AND ca.error_classification IS NULL;"
```

성공 기준: 0건. Run이 아직 실행 중일 때는 현재 active Attempt 1건이 일시적으로 보일 수 있으므로 terminal 이후에 판단한다.

## Test 7. 부모 TaskRun heartbeat·deadline 확인

```powershell
docker compose exec postgres psql -U aivle -d aivle -c "SELECT tr.id, tr.state, tr.last_error_code, tr.started_at, tr.finished_at, ta.state AS attempt_state, ta.claimed_at, ta.heartbeat_at, ta.lease_expires_at, ta.deadline_at, ta.normalized_error_code, ta.normalized_error_reason FROM task_runs tr LEFT JOIN task_attempts ta ON ta.id = tr.current_attempt_id WHERE tr.subject_id = '$runId' ORDER BY tr.created_at DESC;"
```

성공 기준: 장시간 실행 중 `heartbeat_at`이 슬롯 사이 갱신되고, 정상 완료 시 TaskRun과 Attempt가 terminal 성공 상태다.

## Test 8. Concept 선택과 7개 가설

1. 공개된 5개 Concept 중 하나를 선택한다.
2. `TARGET_REGION`, `REVENUE_MODEL`, `PRICE`, `CHANNELS`, `DIFFERENTIATORS`, `PRE_MARKET_SOM_SHARE`, `PRE_MARKET_SOM`을 확인한다.
3. LOCKED 값은 읽기 전용인지 확인한다.
4. AI 제안에는 채택, 수정 후 채택, 다른 제안이 가능한지 확인한다.

성공 기준: 7개 decision이 존재하고 final 상태이며, legal-sensitive 변경만 Delta Legal Review를 거친다.

## Test 9. MarketAnalysisSeedSnapshot

가설 결정을 완료한 뒤 Market Seed finalize를 실행한다.

```powershell
$projectId = <PROJECT_ID>
docker compose exec postgres psql -U aivle -d aivle -c "SELECT id, selection_id, concept_id, schema_version, source_snapshot_hash, snapshot_hash, finalized_at, snapshot_json FROM market_analysis_seed_snapshots WHERE project_id = $projectId ORDER BY finalized_at DESC LIMIT 1;"
```

성공 기준:

- `originalSeed`, `aiInterpretation`, `selectedConcept`, `finalHypotheses`, `legalResult` 존재
- `selectedConcept.identity`, `solution`, `operation`, `valueSemantics`, `canonicalHash` 존재
- 최종 `targetRegion`은 `TARGET_REGION` decision과 일치

## Test 10. Market module handoff

Market 화면에서 분석 시작을 요청한다.

```powershell
docker compose exec postgres psql -U aivle -d aivle -c "SELECT id, module, input_contract, input_snapshot_id, input_snapshot_hash, requested_operation, status, requested_at, input_snapshot_json FROM module_handoffs WHERE project_id = $projectId ORDER BY requested_at DESC LIMIT 1;"
```

성공 기준:

- `module = MARKET_ANALYSIS`
- `requested_operation = START_MARKET_ANALYSIS`
- `input_snapshot_id/hash/json`이 같은 Market Seed Snapshot을 가리킴
- 외부 모듈이 미연결이면 Run은 `NOT_CONNECTED`일 수 있으며 이를 분석 성공으로 간주하지 않음

## 실패 수집 가이드

아래 자료를 secret 제거 후 전달한다.

```powershell
docker compose logs --since 20m backend ai-server | Select-String "Concept|CONCEPT|REDESIGN|schema|validation|ERROR|WARN"
```

- 실패한 Run ID와 Slot 번호
- 프런트 “실행 상세 보기”의 phase/task/attempt/safe code/reason/failed field
- Test 6과 Test 7 SQL 결과
- Selection 이후 실패라면 Test 9와 Test 10 SQL 결과

절대로 API key, MOLEG key, 내부 서비스 토큰, Authorization, DB password, raw prompt, Provider raw body를 포함하지 않는다.

## 다음 단계 진행 조건

- Provider smoke 1회와 필요 시 5회 반복 통과
- 실제 5 Slot 모두 `ELIGIBLE`
- terminal 이후 dangling Attempt 0건
- REDESIGN 경로에서 구조화된 오류 또는 성공 결과 확인
- Selection 7개 가설, Market Seed Snapshot, module handoff 정합성 확인

위 조건을 사용자가 확인하기 전에는 런타임 인수 완료로 간주하지 않는다.
