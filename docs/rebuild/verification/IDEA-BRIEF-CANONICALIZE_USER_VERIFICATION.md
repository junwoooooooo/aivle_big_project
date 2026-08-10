# IDEA-BRIEF-CANONICALIZE User Verification

저장소 루트 `C:\Users\seewo\Desktop\big_proj_01\new_3`의 PowerShell에서 실행한다. 이번 구현 중 DB reset은 수행하지 않았다.

## 1. One-time clean PostgreSQL reset

Baseline V1이 변경되었으므로 기존 개발 DB를 그대로 기동하면 column mismatch가 발생한다. 아래 작업은 기존 개발 PostgreSQL 데이터를 복구 불가능하게 삭제하므로 보존할 데이터가 없음을 확인한 뒤 한 번만 실행한다. MinIO volume은 삭제하지 않는다.

```powershell
docker compose down
docker volume inspect aivle-big-project_postgres-data
docker volume rm aivle-big-project_postgres-data
docker compose up -d postgres minio minio-init ai-server backend frontend
docker compose ps
```

성공 기준: 모든 필수 서비스가 healthy이고 Flyway가 clean DB에 V1을 적용한다.

## 2. Baseline columns

```powershell
docker compose exec -T postgres sh -lc 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -P pager=off -c "SELECT column_name FROM information_schema.columns WHERE table_name = ''idea_briefs'' AND column_name IN (''overview_text'',''user_facing_summary'',''contradictions_json'',''missing_field_keys_json'',''ai_readiness_status'',''readiness_score'',''clarification_round'') ORDER BY column_name;"'
docker compose exec -T postgres sh -lc 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -P pager=off -c "SELECT column_name FROM information_schema.columns WHERE table_name = ''idea_questions'' AND column_name IN (''active'',''clarification_round'') ORDER BY column_name;"'
```

성공 기준: 첫 조회는 7개, 두 번째 조회는 2개 column을 반환한다.

## 3. Targeted automated checks

```powershell
cd backend
.\gradlew.bat compileJava
.\gradlew.bat test --tests "com.aivle.backend.pipeline.idea.IdeaBriefFieldCatalogTests" --tests "com.aivle.backend.pipeline.idea.IdeaBriefReadinessTests" --tests "com.aivle.backend.pipeline.idea.IdeaBriefCanonicalizationIntegrationTests" --tests "com.aivle.backend.pipeline.idea.IdeaBriefDerivationCommitServiceTests" --tests "com.aivle.backend.pipeline.idea.IdeaBriefSnapshotTests"
cd ..\frontEnd
npm.cmd exec vitest run src/features/idea-intake/model/ideaIntakeModel.test.js src/features/idea-intake/components/IdeaBriefReview.test.jsx src/features/idea-intake/hooks/useIdeaIntake.test.jsx
npm.cmd exec eslint src/features/idea-intake/model/ideaIntakeModel.js src/features/idea-intake/components/IdeaBriefReview.jsx src/features/idea-intake/hooks/useIdeaIntake.js src/features/idea-intake/pages/IdeaIntakePage.jsx
cd ..
git diff --check
```

예상 소요는 1~3분이며 모두 exit code 0이어야 한다.

## 4. API derive and Query contract

```powershell
$projectId = '<PROJECT_ID>'
$accessToken = '<ACCESS_TOKEN>'
$base = "http://localhost:3000/api/v3/projects/$projectId/idea-brief"
$headers = @{ Authorization = "Bearer $accessToken"; 'Idempotency-Key' = "idea-derive-$([guid]::NewGuid())" }
$derive = Invoke-RestMethod -Method Post -Uri "$base/derive" -Headers $headers -ContentType 'application/json' -Body (@{
  overview = '지역 음식점의 남는 식재료를 필요한 이웃과 연결한다.'
  fields = @(
    @{ fieldKey='problem'; value='식재료 폐기'; decisionState='PREFERRED' },
    @{ fieldKey='targetRegion'; value='서울'; decisionState='PREFERRED' }
  )
  attachmentFileIds = @()
} | ConvertTo-Json -Depth 6)
$derive.data | ConvertTo-Json -Depth 10
```

성공 기준:

- `overview`가 원문 그대로 반환된다.
- assumptions Field가 overview와 같은 값으로 자동 생성되지 않는다.
- `fieldCatalog`에 15개 항목과 metadata가 있다.
- readiness `totalRequiredFieldCount`는 저장된 Field 수와 무관하게 10이다.
- `activeJobId`와 실제 queued Job Event가 있다.

## 5. AI result persistence and follow-up rounds

AI 작업 종료 뒤 조회한다.

```powershell
$brief = Invoke-RestMethod -Uri $base -Headers @{ Authorization = "Bearer $accessToken" }
$brief.data | ConvertTo-Json -Depth 12
docker compose exec -T postgres sh -lc 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -P pager=off -c "SELECT id,status,overview_text,user_facing_summary,contradictions_json,missing_field_keys_json,ai_readiness_status,readiness_score,clarification_round FROM idea_briefs WHERE deleted_at IS NULL ORDER BY brief_sequence DESC LIMIT 1;"'
```

성공 기준: 새로고침 뒤에도 summary, contradiction, missing keys, score와 질문이 유지된다. Prompt, provider body, raw user input이나 secret은 Job Event/로그에 노출되지 않아야 한다.

화면의 후속 질문에 답한다. 답변 제출마다 다음을 확인한다.

```powershell
docker compose exec -T postgres sh -lc 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -P pager=off -c "SELECT q.target_field_key,q.question_type,q.answered,q.active,q.clarification_round,f.field_value,f.decision_state,f.provenance FROM idea_questions q LEFT JOIN idea_brief_fields f ON f.brief_id=q.brief_id AND f.field_key=q.target_field_key WHERE q.deleted_at IS NULL ORDER BY q.created_at,q.display_order;"'
docker compose exec -T postgres sh -lc 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -P pager=off -c "SELECT state,task_type,subject_id,created_at FROM task_runs WHERE task_type=''IDEA_BRIEF_DERIVATION'' ORDER BY created_at;"'
```

성공 기준:

- Answer row와 target Field가 함께 저장된다.
- 일반 답변 Field provenance는 `USER_CONFIRMED`이다.
- `__UNDECIDED__` 답변은 확정값이 아니라 `MISSING`으로 남는다.
- multi-select 값은 안정적인 JSON 문자열이다.
- 이전 질문은 inactive history로 보존된다.
- follow-up TaskRun은 clarification round 1, 2까지만 생성되고 3은 생성되지 않는다.

## 6. Review and confirmation

브라우저에서 `/app/projects/<PROJECT_ID>/idea`를 직접 연다.

확인 항목:

1. AI 정리 요약, Field별 출처, 미정 Field, 충돌, readiness 점수가 보인다.
2. 각 Field에서 `반드시 유지`, `선호`, `열어 두기`, `가정`을 직접 선택할 수 있다.
3. 사용자 입력 Field가 모두 `반드시 유지`로 바뀌지 않는다.
4. 최대 follow-up round 뒤에도 페이지에서 Field를 직접 수정할 수 있다.
5. `저장하고 준비 상태 확인`은 PATCH의 최신 readiness가 false이면 confirm을 호출하지 않는다.
6. required missing, 미응답 질문, blocking contradiction이 모두 해소된 뒤에만 confirm된다.

확정 뒤 DB를 확인한다.

```powershell
docker compose exec -T postgres sh -lc 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -P pager=off -c "SELECT id,brief_sequence,parent_brief_id,status,confirmed_snapshot_id,snapshot_hash,overview_text FROM idea_briefs WHERE project_id=<PROJECT_ID> AND deleted_at IS NULL ORDER BY brief_sequence;"'
```

성공 기준: confirmed row는 `status=CONFIRMED`, `confirmed_snapshot_id=id`, `snapshot_hash=sha256:...`이며 수정되지 않는다. 확정 후 다시 편집하면 parent가 confirmed id인 새 Draft sequence가 생성된다.

## Failure evidence

실패 시 아래 정보만 수집하고 Authorization, secret, prompt, provider raw body 및 사용자 원문은 제거한다.

```powershell
docker compose ps
docker compose logs --since=10m backend
docker compose logs --since=10m ai-server
docker compose logs --since=10m frontend
```

다음 실행 단위 진행 조건은 clean DB에서 overview 분리, 15개 Catalog, assessment 새로고침 보존, answer-to-field, 2회 bound, readiness gate, immutable confirm snapshot이 모두 확인되는 것이다.
