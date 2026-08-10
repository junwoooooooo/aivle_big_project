# V2-10 Final Runtime Acceptance User Verification

## 판정 원칙

이 문서는 V2-10E0, E1, E2, E3, F 구현 이후 사용자가 실제 PostgreSQL, Object Storage, 브라우저, Worker, AI Provider 환경에서 수행할 최종 런타임 게이트다. Codex는 Fast profile의 정적 검사와 targeted test까지만 수행했다. 아래 절차와 SQL이 모두 기대 결과를 만족하기 전에는 `PASS`가 아니라 `implementation complete / user runtime acceptance pending`이다.

검증 중 다음 값을 기록한다.

- 사용자 ID와 프로젝트 ID
- 각 Snapshot ID, schemaVersion, snapshotHash
- 각 비동기 요청의 Idempotency-Key, TaskRun ID, Job ID
- 요청 시각, HTTP 상태, 첫 화면 응답 시간, terminal 상태와 최종 Query 상태
- Evidence artifact ID, reference ID, 파일명, 크기, SHA-256
- 실패 시험의 공개 오류 코드와 서버 로그 correlation ID

## 사전 조건

- 새 PostgreSQL volume과 Flyway가 활성화되어 있어야 한다.
- Worker scheduling과 SSE를 켜고 AI Provider credential을 정상 설정한다.
- `OBJECT_STORAGE_LOCAL_ROOT`가 쓰기 가능한 안전한 로컬 루트이거나 S3-compatible storage가 설정되어 있어야 한다.
- 브라우저 개발자 도구의 Network 탭과 서버 로그를 함께 연다.
- 검증 파일은 민감 정보가 없는 작은 PDF 또는 PNG를 사용한다.

## STEP 1 — Fresh DB volume

1. 기존 검증용 컨테이너/volume과 분리된 새 DB volume으로 기동한다.
2. Flyway가 V1부터 V5까지 순서대로 성공했는지 확인한다.
3. 애플리케이션 시작 로그에 schema validation, worker polling, SSE 초기화 오류가 없는지 확인한다.

기대 결과: 새 파이프라인 테이블과 V2-10E0/E1/E2/F migration이 적용되고 데이터가 없는 상태에서 서비스가 정상 기동한다.

## STEP 2 — 새 project 생성

1. 새 사용자로 로그인하거나 검증 전용 사용자를 사용한다.
2. 프로젝트를 하나 생성하고 프로젝트 ID를 기록한다.
3. URL과 Network 요청이 새 `/api/v3/projects/{projectId}` 제품 흐름을 사용하는지 확인한다.

기대 결과: legacy journey/planning 화면이나 controller로 이동하지 않는다.

## STEP 3 — 최소 세 개 Seed만 입력

1. Idea Intake에서 required seed 세 개만 입력한다.
2. 아직 자유문장이나 선택 필드를 추가하지 않은 상태로 최초 derivation을 시작한다.
3. POST가 즉시 비동기 응답을 반환하고 Job Center에 Idea 작업이 active로 나타나는지 확인한다.

기대 결과: initial required 계약은 세 개이며, Provider 작업은 HTTP transaction 밖의 Worker에서 실행된다.

## STEP 4 — 자유문장에 구체값 포함

다음처럼 사용자가 이미 결정한 값을 한 개 이상 포함하는 자유문장을 입력한다.

> 서울 마포구의 20~35세 1인 가구에게 월 9,900원 구독으로 냉장고 재고를 관리하고, 제휴 매장 수수료 12%를 받는다.

지역, 가격, 수익모델 값이 AI 제안으로 덮이지 않고 commitment 후보로 추출되는지 확인한다.

기대 결과: 확인 전에는 dedicated optional field의 `USER_INPUT` 후보이며, generic notes로만 축약되거나 사라지지 않는다.

## STEP 5 — Commitment 후보 → Final Synthesis → Review → Confirm

1. commitment review에서 위 구체값을 각각 확인한다.
2. 한 값을 변경해 reassessment가 다시 수행되고 stale assessment dead-end가 생기지 않는지 확인한다.
3. Final Synthesis를 실행하고 Review 화면에서 값을 재확인한다.
4. Confirm한다.

기대 결과: 사용자 확인 값은 `LOCKED`로 승격되고 이후 AI가 변경하지 않는다. 실제 채워진 구체도를 기준으로 `EXPLORE`, `REFINE`, `AS_IS`가 판정된다. `READY_FOR_REVIEW`와 `readyForConfirm`은 구분되며, `RECOVERY`는 실행 불일치에만 사용된다.

## STEP 6 — Concept 5개 생성

1. Concept Factory를 시작한다.
2. 페이지가 즉시 작업 중 상태로 돌아오고 새로고침 후에도 active task가 복원되는지 확인한다.
3. 완료 후 공개 Concept이 정확히 다섯 개인지 확인한다.
4. 이름만 다른 의미상 중복 Concept이 없는지 확인한다.

기대 결과: AS_IS 후보의 원본 값이 보존된다. INITIAL/REPLACEMENT/REDESIGN 모두 동일한 distinctness 경로를 거치며, 의미상 중복은 Legal 전에 걸러지고 replacement는 bounded다.

## STEP 7 — Legal 5개 검증

1. 각 Concept의 완전한 candidate를 기준으로 Legal 결과를 확인한다.
2. 현재 지원 관할인 `KR`이 명시되어 있는지 확인한다.
3. 통제된 API 요청으로 미지원 관할을 전달해 본다.

기대 결과: 다섯 Concept 모두 Legal 결과를 갖고, pre-market SOM은 Legal fact로 사용되지 않는다. 미지원 관할이 조용히 한국법 검토로 통과하지 않으며, Provider 기술 실패가 `LEGAL_REJECTED`로 기록되지 않는다. `NEEDS_FACTS`가 사용자 dead-end를 만들지 않는다.

## STEP 8 — Concept 선택

1. 다섯 Concept 중 하나를 선택한다.
2. 선택 이유를 입력하고 확정한다.

기대 결과: 선택된 Concept ID와 해당 Legal 결과가 이후 Snapshot source로 이어진다.

## STEP 9 — 7개 Hypothesis decision 확인

1. Concept Selection 화면에서 decision이 정확히 일곱 개인지 센다.
2. `TARGET_REGION`이 포함되어 있는지 확인한다.
3. Idea Brief의 LOCKED 값에서 온 decision을 변경하려고 시도한다.

기대 결과: LOCKED decision은 변경할 수 없고, open decision만 accept/reject/edit할 수 있다.

## STEP 10 — Alternative 비동기 요청

1. open hypothesis 하나를 reject한 뒤 alternative를 요청한다.
2. HTTP `202`, 새 TaskRun ID와 Job ID가 즉시 반환되는지 확인한다.
3. Provider가 동작 중이어도 다른 UI 조작이 가능한지 확인한다.
4. Job Center/SSE를 관찰하고 완료 후 Query에 새 proposal version이 나타나는지 확인한다.

기대 결과: alternative는 background job이며 같은 command replay만 같은 응답을 재사용한다. 실제 새 요청은 새 TaskRun ID를 사용한다.

## STEP 11 — Legal-sensitive 수정과 Delta Legal

1. Legal-sensitive hypothesis의 기준값을 변경해 accept한다.
2. HTTP `202`와 `CONCEPT_DELTA_LEGAL_REVIEW` TaskRun을 확인한다.
3. 완료 전에는 decision이 최종 accept되지 않는지 확인한다.
4. 통제된 Provider 실패와 실제 Legal rejected 결과를 각각 시험한다.

기대 결과: 기술 실패는 안전한 작업 실패이며 Legal rejection이 아니다. Legal 통과 후에만 변경값이 accepted 상태가 된다.

## STEP 12 — MarketAnalysisSeedSnapshot

1. 일곱 decision을 모두 확정하고 Market seed를 finalize한다.
2. Snapshot ID, schemaVersion, snapshotHash를 기록한다.
3. selected Concept, 일곱 최종 decision, Legal source가 포함되는지 확인한다.

기대 결과: Snapshot은 immutable하고 이후 원본 selection/decision 수정으로 내용이 바뀌지 않는다.

## STEP 13 — TechOps 준비와 비동기 Proposal

1. TechOps에 처음 진입한다.
2. 세 required proposal이 background에서 생성되는 동안 화면과 다른 API가 응답하는지 확인한다.
3. `TECH_OPS_PROPOSAL` TaskRun/Job을 확인한다.
4. Provider 완료 전후 모두 각 required field에 직접 입력할 수 있는지 확인한다.
5. proposal을 accept, edit-and-accept, alternative 요청해 본다.

기대 결과: initial proposal과 alternative는 비동기다. 사용자 manual override는 항상 가능하며, Product Spec은 사용자 확인을 거친다. `ThreeYearTargets`는 canonical 구조를 사용한다.

## STEP 14 — 실제 TechOps Evidence 파일 upload

1. TechOps Evidence 파일 picker로 실제 PDF 또는 PNG를 선택한다.
2. multipart `/api/v3/projects/{projectId}/evidence-artifacts`가 먼저 HTTP `201`과 artifact ID를 반환하는지 확인한다.
3. 이어지는 Evidence 등록 JSON이 `artifactId`를 사용하고 `artifactRef`, storage key, 로컬 경로를 보내지 않는지 확인한다.
4. 다른 프로젝트 사용자의 다운로드/등록 시도, 확장자 위장 파일, 삭제된 artifact 등록을 거부하는지 확인한다.
5. 소유자로 다운로드한 파일의 SHA-256이 업로드 원본과 같은지 확인한다.

기대 결과: UUID 기반 안전 key, 크기/서명/allowlist 검증, project ownership, soft delete, `nosniff` 다운로드가 적용된다.

## STEP 15 — TechOpsInputSnapshot

1. 세 proposal decision, Product Spec, required facts를 완료하고 Snapshot을 finalize한다.
2. Snapshot ID, schemaVersion, snapshotHash를 기록한다.
3. Evidence 항목에 artifact ID, 표시 메타데이터, SHA-256만 존재하는지 확인한다.

기대 결과: raw storage path, storage key, presigned URL, 파일 bytes, legacy artifactRef가 Snapshot에 없다.

## STEP 16 — Finance 진입

1. Finance에 처음 진입하되 어떤 `AI 추천 받기` 버튼도 누르지 않는다.
2. 화면이 즉시 열리고 TechOps의 `ThreeYearTargets`가 read-only로 재사용되는지 확인한다.
3. 바로 아래의 Finance initialize 금지상태 SQL을 실행한다.

기대 결과: initialize의 Provider call은 0회이며 여러 `FINANCE_ESTIMATE` TaskRun이 자동 생성되지 않는다. 비어 있는 eligible field만 추천 버튼을 제공하고 CAC는 AI 대상이 아니다.

## STEP 17 — 특정 Finance field AI 추천

1. 특정 eligible field 하나에서만 `AI 추천 받기`를 누른다.
2. HTTP `202`, 새 TaskRun/Job ID, field key를 확인한다.
3. 작업 중 새로고침해도 Query가 해당 field의 queued/running 상태를 복원하는지 확인한다.
4. 완료 후 proposal이 final value를 자동 변경하지 않는지 확인한다.
5. accept, edit-and-accept, alternative와 Provider 실패 후 직접 입력을 각각 확인한다.

기대 결과: estimate와 alternative는 field 단위 비동기다. 사용자의 동기 결정이 late result보다 우선하고, AI estimate는 실제 견적 Evidence처럼 취급되지 않는다.

## STEP 18 — FinancialInputSnapshot

1. required financial field를 채우되 생성된 AI proposal 하나는 accept하지 않고 남겨 둔다.
2. Snapshot을 finalize하고 ID, schemaVersion, snapshotHash를 기록한다.
3. 미채택 proposal이 final values와 Snapshot assistance의 proposal value에 포함되지 않는지 확인한다.
4. 마케팅비 1,000, 영업비 500, 신규고객 30으로 CAC가 서버 계산값 50.00인지 확인한다.

기대 결과: Snapshot은 immutable하며 TechOps target을 복사해 변형하지 않고 source snapshot ID/hash 경계로 참조한다.

## STEP 19 — Marketing source/content

1. Marketing Source Snapshot을 만든다.
2. source에 selected Concept, 최종 accepted hypotheses, Legal 결과, Market seed가 포함되는지 확인한다.
3. content generation을 실행하고 Job Center/SSE/Query를 확인한다.

기대 결과: 제거된 planning-change dependency가 다시 나타나지 않는다. 생성 성공은 Provider 호출만이 아니라 결과 검증, domain commit, terminal JobEvent까지 완료되었음을 뜻한다.

## 데이터베이스 확인 SQL

아래 예시는 PostgreSQL `psql` 기준이다. 먼저 실제 프로젝트 ID로 변수를 설정한다.

```sql
\set project_id 123
```

### 1. TaskRuns / TaskAttempts / JobEvents

```sql
SELECT id, task_type, subject_type, subject_id, state, attempt_count,
       max_attempts, retryable, idempotency_key, created_at, started_at, finished_at
FROM task_runs
WHERE project_id = :project_id
ORDER BY created_at, id;

SELECT a.id, a.task_run_id, a.attempt_number, a.state, a.retryable,
       a.claimed_at, a.started_at, a.finished_at, a.normalized_error_code
FROM task_attempts a
JOIN task_runs r ON r.id = a.task_run_id
WHERE r.project_id = :project_id
ORDER BY r.created_at, a.attempt_number;

SELECT job_id, task_run_id, sequence, stage, event_type, status,
       technical_code, occurred_at
FROM job_events
WHERE project_id = :project_id
ORDER BY job_id, sequence;
```

### 2. IdeaBrief / HypothesisDecision / MarketSeedSnapshot

```sql
SELECT id, project_id, status, active_task_run_id, snapshot_hash,
       ai_readiness_status, readiness_score, clarification_round, created_at, updated_at
FROM idea_briefs
WHERE project_id = :project_id
ORDER BY created_at;

SELECT id, selection_id, hypothesis_type, source, decision_status, locked,
       proposal_version, legal_impact, legal_review_status, decided_at
FROM concept_hypothesis_decisions
WHERE project_id = :project_id
ORDER BY hypothesis_type, proposal_version;

SELECT id, selection_id, concept_id, schema_version, source_snapshot_hash,
       snapshot_hash, finalized_at
FROM market_analysis_seed_snapshots
WHERE project_id = :project_id
ORDER BY finalized_at;
```

### 3. TechOpsPreparation / TechOpsSnapshot

```sql
SELECT id, source_market_seed_snapshot_id, source_snapshot_hash, revision,
       proposal_generation_status, active_proposal_task_run_id, safe_proposal_error,
       created_at, updated_at
FROM tech_ops_input_preparations
WHERE project_id = :project_id
ORDER BY created_at;

SELECT id, preparation_id, source_market_seed_snapshot_id, schema_version,
       snapshot_hash, finalized_at
FROM tech_ops_input_snapshots
WHERE project_id = :project_id
ORDER BY finalized_at;
```

### 4. FinancialPreparation / FinancialSnapshot

```sql
SELECT id, source_tech_ops_snapshot_id, source_market_seed_snapshot_id,
       source_snapshot_hash, revision, assistance_json, created_at, updated_at
FROM financial_input_preparations
WHERE project_id = :project_id
ORDER BY created_at;

SELECT id, preparation_id, source_tech_ops_snapshot_id,
       source_market_seed_snapshot_id, schema_version, snapshot_hash, finalized_at
FROM financial_input_snapshots
WHERE project_id = :project_id
ORDER BY finalized_at;
```

### 5. Evidence Artifact / Reference

```sql
SELECT id, project_id, storage_type, original_filename, media_type,
       size_bytes, sha256, created_by_user_id, deleted_at
FROM project_evidence_artifacts
WHERE project_id = :project_id
ORDER BY created_at;

SELECT r.id, r.preparation_id, r.evidence_type, r.display_name,
       r.artifact_id, r.artifact_ref, r.deleted_at,
       a.original_filename, a.media_type, a.size_bytes, a.sha256, a.deleted_at AS artifact_deleted_at
FROM tech_ops_evidence_references r
LEFT JOIN project_evidence_artifacts a
  ON a.id = r.artifact_id AND a.project_id = r.project_id
WHERE r.project_id = :project_id
ORDER BY r.created_at;
```

새로 생성된 reference의 `artifact_ref`는 `NULL`이어야 한다. `storage_key`는 운영자 저장소 진단 외에는 API/Snapshot에 노출하지 않는다.

## 금지 상태 확인 SQL

각 쿼리의 기대 결과는 **0 rows**다.

### Forbidden 1 — Domain RUNNING + terminal TaskRun

```sql
WITH mismatch AS (
  SELECT 'IDEA' AS domain_type, b.id AS domain_id, b.status AS domain_status,
         r.id AS task_run_id, r.state AS task_state
  FROM idea_briefs b JOIN task_runs r ON r.id = b.active_task_run_id
  WHERE b.project_id = :project_id AND b.status = 'DERIVING'
    AND r.state IN ('SUCCEEDED','NEEDS_INPUT','FAILED','CANCELLED','TIMED_OUT')
  UNION ALL
  SELECT 'CONCEPT_FACTORY', c.id, c.status, r.id, r.state
  FROM concept_factory_runs c JOIN task_runs r ON r.id = c.task_run_id
  WHERE c.project_id = :project_id
    AND c.status IN ('QUEUED','GENERATING','VALIDATING','REPLACING')
    AND r.state IN ('SUCCEEDED','NEEDS_INPUT','FAILED','CANCELLED','TIMED_OUT')
  UNION ALL
  SELECT 'CONCEPT_SELECTION', s.id::text, s.action_status, r.id, r.state
  FROM concept_selections s JOIN task_runs r ON r.id = s.active_action_task_run_id
  WHERE s.project_id = :project_id AND s.action_status IN ('QUEUED','RUNNING')
    AND r.state IN ('SUCCEEDED','NEEDS_INPUT','FAILED','CANCELLED','TIMED_OUT')
  UNION ALL
  SELECT 'TECH_OPS', p.id, p.proposal_generation_status, r.id, r.state
  FROM tech_ops_input_preparations p JOIN task_runs r ON r.id = p.active_proposal_task_run_id
  WHERE p.project_id = :project_id AND p.proposal_generation_status IN ('QUEUED','RUNNING')
    AND r.state IN ('SUCCEEDED','NEEDS_INPUT','FAILED','CANCELLED','TIMED_OUT')
  UNION ALL
  SELECT 'FINANCE:' || a.key, p.id, a.value->>'estimateStatus', r.id, r.state
  FROM financial_input_preparations p
  CROSS JOIN LATERAL jsonb_each(p.assistance_json::jsonb) a
  JOIN task_runs r ON r.id = a.value->>'activeTaskRunId'
  WHERE p.project_id = :project_id
    AND a.value->>'estimateStatus' IN ('QUEUED','RUNNING')
    AND r.state IN ('SUCCEEDED','NEEDS_INPUT','FAILED','CANCELLED','TIMED_OUT')
)
SELECT * FROM mismatch;
```

### Forbidden 2 — terminal JobEvent 뒤 같은 jobId에 QUEUED event append

```sql
WITH first_terminal AS (
  SELECT job_id, MIN(sequence) AS terminal_sequence
  FROM job_events
  WHERE project_id = :project_id
    AND status IN ('COMPLETED','NEEDS_INPUT','FAILED','BLOCKED')
  GROUP BY job_id
)
SELECT e.job_id, t.terminal_sequence, e.sequence, e.status, e.event_type
FROM first_terminal t
JOIN job_events e ON e.job_id = t.job_id AND e.sequence > t.terminal_sequence
WHERE e.project_id = :project_id AND e.status = 'QUEUED';
```

### Forbidden 3 — accepted hypothesis + failed Delta Legal

```sql
SELECT id, selection_id, hypothesis_type, decision_status, legal_review_status,
       proposal_version, decided_at
FROM concept_hypothesis_decisions
WHERE project_id = :project_id
  AND decision_status IN ('ACCEPTED','USER_EDITED_ACCEPTED')
  AND legal_review_status = 'FAILED';
```

### Forbidden 4 — 동일 사용자 retry가 terminal TaskRun을 재사용

```sql
SELECT id, task_type, subject_type, subject_id, state,
       last_retry_idempotency_key, attempt_count, created_at, finished_at
FROM task_runs
WHERE project_id = :project_id
  AND state IN ('SUCCEEDED','NEEDS_INPUT','FAILED','CANCELLED','TIMED_OUT')
  AND last_retry_idempotency_key IS NOT NULL;
```

제품별 retry 전후에 다음 쿼리로 같은 subject의 TaskRun ID가 서로 다른지도 확인한다.

```sql
SELECT task_type, subject_type, subject_id,
       COUNT(*) AS run_count, COUNT(DISTINCT id) AS distinct_run_ids,
       array_agg(id ORDER BY created_at) AS run_ids
FROM task_runs
WHERE project_id = :project_id
GROUP BY task_type, subject_type, subject_id
HAVING COUNT(*) > 1 AND COUNT(*) <> COUNT(DISTINCT id);
```

### Forbidden 5 — Finance initialize 하나로 여러 FINANCE_ESTIMATE 자동 생성

STEP 16에서 추천 버튼을 누르기 전에 즉시 실행한다.

```sql
SELECT p.id AS preparation_id, COUNT(r.id) AS auto_estimate_runs
FROM financial_input_preparations p
LEFT JOIN task_runs r
  ON r.project_id = p.project_id
 AND r.task_type = 'FINANCE_ESTIMATE'
 AND r.subject_type = 'FINANCIAL_PREPARATION'
 AND r.subject_id = p.id
WHERE p.project_id = :project_id
GROUP BY p.id
HAVING COUNT(r.id) <> 0;
```

## Job Center 최종 확인

1. 실행 중인 각 새 async type이 안전한 module label과 route를 갖는지 확인한다.
2. `QUEUED`, `READY`, `RUNNING`만 항상 active에 나타나는지 확인한다.
3. 해결되지 않은 action과 연결된 `NEEDS_INPUT`만 active에 남는지 확인한다.
4. 완료/실패/취소/시간초과와 해결된 `NEEDS_INPUT`이 recent history로 이동하는지 확인한다.
5. SSE를 끊었다 다시 연결하거나 새로고침하고도 Query API가 동일 상태를 복원하는지 확인한다.

검증 대상 type: `CONCEPT_HYPOTHESIS_ALTERNATIVE`, `CONCEPT_DELTA_LEGAL_REVIEW`, `TECH_OPS_PROPOSAL`, `FINANCE_ESTIMATE`.

## 최종 승인 기록

- 모든 STEP 1~19와 SQL이 기대 결과를 만족하면 사용자 승인자, 일시, 환경 식별자, project ID를 기록한다.
- 하나라도 실패하면 관련 TaskRun/Job ID, correlation ID, Snapshot hash, 재현 순서를 기록하고 runtime acceptance를 보류한다.
- 현재 Codex 판정: **implementation complete / user runtime acceptance pending**.
