# V2-10E0 사용자 검증

## 준비

1. fresh DB 또는 V1 적용 DB에 Flyway V2 migration이 성공하는지 확인한다.
2. Backend, AI, Frontend를 실행하고 외부 AI/Legal Provider 호출 로그와 `task_runs`, `job_events`를 조회할 수 있게 준비한다.

## 1. Commitment 재평가

1. 필수 세 Seed만 입력하고 자유문장에 `월 9,900원 구독` 같은 가격/수익모델을 포함한다.
2. Review에서 commitment를 `확인`한다.
3. 화면이 즉시 RUNNING으로 바뀌고 Interpretation patch/Confirm 요청이 함께 전송되지 않는지 확인한다.
4. 새 `FINAL_SYNTHESIS`가 끝나면 Recovery가 아니라 Review로 복귀하는지 확인한다.
5. 새 assessment에서 `assessmentCurrent=true`, readiness 충족 시 Confirm 가능한지 확인한다.
6. 같은 commitment HTTP command를 같은 Idempotency-Key로 재전송해 TaskRun이 하나뿐인지 확인한다.
7. `수정 후 확인`, 이미 확인한 값을 `OPEN으로 복귀`하는 경우도 각각 새 TaskRun이 생기는지 확인한다.
8. canonical 값/provenance/state가 실제로 바뀌지 않는 action에는 TaskRun이 생기지 않는지 확인한다.

예시 SQL:

```sql
select id, task_type, subject_id, state, idempotency_key, created_at
from task_runs
where task_type = 'IDEA_BRIEF_DERIVATION'
order by created_at desc;
```

## 2. REDESIGN distinctness

1. 첫 Legal 결과가 `REDESIGNABLE`이 되도록 테스트 Provider fixture를 사용한다.
2. redesign 후보를 기존 후보와 애매하게 유사하게 반환한다.
3. semantic judge가 `DISTINCT`이면 그 후보에만 후속 Legal 호출이 생기는지 확인한다.
4. `DUPLICATE`이면 redesign 후보의 Legal 호출 없이 replacement로 가는지 확인한다.
5. judge schema/technical failure이면 해당 후보가 Legal로 넘어가지 않고 safe failure로 남는지 확인한다.

## 3. TARGET_REGION 7개 결정

1. Seed에서 `대한민국`을 dedicated optional 입력으로 넣은 경우 선택 화면의 대상 지역이 `확정됨/읽기 전용`인지 확인한다.
2. 자유문장 commitment로 region을 확인한 경우 source가 `USER_CONFIRMED`, locked/final인지 확인한다.
3. region을 비워 생성한 경우 Concept의 region semantics가 `AI_HYPOTHESIS + OPEN + PROPOSED`인지 확인한다.
4. 선택 후 정확히 7개 decision이 보이고 7개가 모두 완료되기 전 Market Seed finalize가 차단되는지 확인한다.
5. Snapshot의 `finalHypotheses.targetRegion`이 최종 decision 값과 같은지 확인한다.

```sql
select hypothesis_type, source, decision_status, final_value_json, locked, proposal_version
from concept_hypothesis_decisions
where selection_id = :selection_id
order by hypothesis_type, proposal_version desc;
```

## 4. KR-only jurisdiction

1. locked targetRegion을 `미국 캘리포니아`로 확정하고 Concept Factory를 시작한다.
2. `LEGAL_JURISDICTION_UNSUPPORTED`와 다음 문구가 표시되는지 확인한다.

> 현재 공식 법률 검토는 대한민국을 대상으로 지원합니다. 대상 지역을 대한민국으로 변경하면 계속 진행할 수 있습니다.

3. 이 경우 Concept run/slot과 Legal Provider 호출이 생성되지 않았는지 확인한다.
4. 열린 region에서 AI가 foreign region을 반환하는 fixture는 candidate rejection/replacement가 되고 Legal 호출은 0회인지 확인한다.
5. 선택 후 TARGET_REGION을 foreign region으로 수정해도 Delta Legal Provider 호출 전에 같은 code로 차단되는지 확인한다.

## 5. NEEDS_FACTS dead-end 제거

1. Legal Provider가 정상 응답으로 `NEEDS_FACTS`를 반환하도록 한다.
2. Task/provider technical failure가 아니라 candidate business rejection으로 기록되는지 확인한다.
3. slot/run이 `NEEDS_INPUT`에 멈추지 않고 `LEGAL_EXTERNAL_FACT_UNRESOLVED` 후 replacement로 진행하는지 확인한다.
4. 사용자 legal 질문 UI가 생성되지 않는지 확인한다.

```sql
select a.error_classification, a.safe_error_code, s.status as slot_status, r.status as run_status
from concept_attempts a
join concept_slots s on s.id = a.slot_id
join concept_factory_runs r on r.id = s.run_id
where r.id = :run_id
order by a.created_at desc;
```

## 완료 판정

- 위 동작과 DB 상태가 모두 일치하면 E0 runtime acceptance를 승인한다.
- Browser/Provider/Docker 검증은 Codex Fast profile에서 실행하지 않았으므로 현재 문서 상태는 `implementation complete / user runtime acceptance pending`이다.
