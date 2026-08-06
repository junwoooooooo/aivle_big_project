# G6 Docker·브라우저 사용자 검증 절차

Codex는 이 수동 검증을 완료했다고 주장하지 않는다. 아래 절차는 사용자가 Docker, 실제 OpenAI 호출, 브라우저·반응형·키보드 동작을 확인하기 위한 것이다.

## 1. 환경과 실행

1. `.env.example`을 `.env`로 복사하고 실제 비밀값은 저장소에 commit하지 않는다.
2. 최소값을 설정한다.
   - `POSTGRES_PASSWORD`, `JWT_SECRET`, `AI_INTERNAL_SERVICE_TOKEN`
   - `AI_PROVIDER=openai`, `AI_API_KEY`, `AI_MODEL`, 필요 시 `AI_MODEL_CONCEPT_VALIDATION`
   - `MOLEG_API_KEY`
   - `AI_FIXTURE_MODE=false`
   - `AI_CONCEPT_GENERATION_CONCURRENCY=1`
   - `AI_CONCEPT_TEST_FAILURE_INJECTION=false`
   - `VITE_CONVERSATIONAL_VALIDATION_WORKSPACE_ENABLED=true`
3. 실행한다.

```powershell
docker compose config
docker compose up -d --build postgres minio minio-init ai-server backend frontend
docker compose ps
docker compose logs --tail=100 backend ai-server frontend
```

브라우저에서 `http://localhost:3000`에 접속하고 로그인한 뒤 테스트 프로젝트를 연다. 인증 Token을 URL이나 로그에 복사하지 않는다.

## 2. 입력 예시와 선행 상태

Idea Workspace에서 다음 예시를 대화로 입력한다.

> 서울의 아파트 입주민이 재활용품 배출을 예약하고 포인트를 받는 서비스를 검토한다. 플랫폼은 직접 수거하지 않고 허가된 파트너가 운반한다. 원하는 결과는 배출 편의와 재활용률 개선이다. 대상 지역은 서울이다.

질문에는 운영 주체를 “허가된 파트너”, 플랫폼 역할을 “예약·정보·보상 관리”로 답한다. problem, targetCustomer/beneficiaries, desiredOutcome, targetRegion과 규제 민감 활동을 확인한 뒤 Brief 전체 확인을 수행한다. AI 제안이 사용자 확인 전 USER_CONFIRMED/LOCKED가 되지 않는지 확인한다.

Regulatory Boundary를 실행하고 다음을 확인한다.

- 상태 `READY`
- Brief version/hash와 Boundary 참조 version/hash 일치
- 직접 무자격 수거 금지, 파트너/통제/고지 Rule이 공식 Evidence와 연결
- Source warning이 있으면 숨기지 않음

## 3. Workboard 정상 흐름

1. READY Boundary 아래 `Concept 탐색 시작`을 한 번 누른다. 빠르게 연속 클릭해도 Batch가 중복 생성되지 않아야 한다.
2. Desktop에서 왼쪽 약 30%에 Conversation 접근·Confirmed Brief·READY Boundary·version/hash가, 오른쪽 약 70%에 Batch와 Slot 1~3이 표시되는지 확인한다.
3. Slot 위치가 항상 다음 순서인지 확인한다.
   - Slot 1 고객 경험 중심
   - Slot 2 운영·파트너 중심
   - Slot 3 수익·채널 중심
4. 진행 중에는 focus/status/attempt/timeline만 보이고 Concept 이름·역할·거래·데이터·수익 상세와 실패 Draft가 보이지 않아야 한다.
5. Timeline에서 실제 생성·검증·retry/repair/redesign/replacement event만 보이고 percent, technicalCode, Prompt, provider body가 보이지 않아야 한다.
6. 완료 전에 한두 Slot이 ELIGIBLE이 되어도 상세가 하나씩 나타나지 않아야 한다.
7. Batch `COMPLETED`, ELIGIBLE 3개, public concepts 3개가 모두 충족된 순간 Card 3개가 동시에 표시되는지 확인한다.
8. Card에서 요약, actor roles, transaction/data/physical flow, feature/channel/pricing/risks, 통제·파트너·고지·금지 변형·미해결 가정을 펼쳐 확인한다. 법령 원문 전체나 내부 trace JSON은 없어야 한다.

## 4. 복원·상태별 확인

- 실행 중 새로고침: 같은 Batch/Slot index/attemptCount/Timeline이 Last-Event-ID replay로 복원되고 Draft 상세는 계속 숨겨져야 한다.
- 완료 후 새로고침: public Concept 3개가 다시 동시에 표시되어야 한다.
- NEEDS_INPUT: 안전한 설명과 Brief 수정 Action이 나타나고 Brief는 자동 수정되지 않아야 한다.
- FAILED retryable: 안전한 오류와 `다시 실행` Action이 나타나며 같은 TaskRun이 재큐잉되어야 한다. configuration/permanent 오류는 무한 재시도하지 않아야 한다.
- STALE: Brief 또는 Boundary를 새 version으로 변경·확정한 뒤 이전 Batch에 Stale banner가 나타나고 기존 Concept Card가 기본 결과로 노출되지 않아야 한다.
- Feature Flag OFF: `.env`의 flag를 false로 바꾸고 frontend를 재빌드하면 기존 Idea Journey가 그대로 표시되어야 한다.

Mixed failure는 `G5_DOCKER_OPENAI_VERIFICATION.md`의 개발 전용 plan을 사용하되 운영 모드에서는 injection을 켜지 않는다. 사용 후 반드시 `AI_CONCEPT_TEST_FAILURE_INJECTION=false`로 되돌린다.

## 5. Mobile·접근성

브라우저 개발자 도구에서 390×844와 768px 경계를 확인한다.

- Workboard가 Summary보다 먼저 표시되고 Slot이 세로 배열이어야 한다.
- Brief/Boundary Summary가 접이식이며 다시 열 수 있어야 한다.
- 가로 scroll이 없어야 한다.
- Tab 순서가 Summary → Batch → Slot 1 → Slot 2 → Slot 3을 따르는지 확인한다.
- Timeline button을 Enter/Space로 열고 `aria-expanded`가 바뀌는지 확인한다.
- Screen reader가 Slot index와 안전한 상태 문구를 함께 읽는지 확인한다.
- 오류가 alert로, 진행 상태가 polite live region으로 전달되는지 확인한다.
- OS reduced motion에서 불필요한 transition이 제거되고 색상 없이도 상태를 구분할 수 있어야 한다.

## 6. DB·Event 확인

```powershell
docker compose exec postgres psql -U aivle -d aivle -c "select id, project_id, status, brief_version_id, boundary_version_id, brief_snapshot_hash, boundary_snapshot_hash, task_run_id from concept_exploration_batches order by id desc limit 5;"
docker compose exec postgres psql -U aivle -d aivle -c "select batch_id, slot_index, variation_focus, status, attempt_count, legal_state from concept_slots order by batch_id desc, slot_index;"
docker compose exec postgres psql -U aivle -d aivle -c "select slot_id, attempt_number, phase, outcome, duplicate_status from concept_attempts order by id desc limit 20;"
docker compose exec postgres psql -U aivle -d aivle -c "select batch_id, legal_state, assessment_version, validated_snapshot_hash from exploration_concepts order by id desc limit 10;"
docker compose exec postgres psql -U aivle -d aivle -c "select job_id, sequence, stage, status, message_key, message_params_json from job_events where message_key like 'job.concept.%' order by job_id, sequence;"
```

성공 기준:

- 한 Batch의 Slot index가 고유하고 0,1,2 순서로 조회된다.
- 완료 Batch의 공개 Concept가 정확히 3개이고 모두 허용 legal state, 동일 Brief/Boundary hash, UNIQUE duplicate status다.
- Job Event sequence에 중복/누락이 없고 safe params 외 원문이 없다.
- domain terminal commit 이후 terminal event가 기록된다.

## 7. 로그와 실패 자료 수집

Prompt, Authorization, 사용자 전체 원문, provider raw body가 아래 로그에 없는지 함께 확인한다.

```powershell
docker compose logs --since=20m backend ai-server frontend
docker compose ps
docker compose exec postgres psql -U aivle -d aivle -c "select id, task_type, state, attempt_count, retryable, last_error_code from task_runs order by created_at desc limit 20;"
docker compose exec postgres psql -U aivle -d aivle -c "select job_id, sequence, stage, status, message_key, technical_code, occurred_at from job_events order by id desc limit 100;"
```

실패 시 브라우저 Network에서 endpoint/status/content-type과 Console의 안전한 오류만 저장한다. Authorization header와 response의 민감 원문은 공유 전에 제거한다. 재현 입력, project ID, batch/job ID, 발생 시각, Slot index/attempt phase를 함께 기록한다.
