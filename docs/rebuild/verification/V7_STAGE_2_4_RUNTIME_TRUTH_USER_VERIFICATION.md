# V7 Stage 2 / Stage 4 사용자 runtime 검증

## 전제

- current/non-stale seed, portfolio selection, BM plan이 있는 Golden project 하나만 사용한다.
- 먼저 20명까지만 실행하고 성공 evidence를 기록한 뒤 같은 프로젝트에서 40명을 실행한다.
- TaskRun ID, subjectType, source version/revision, terminal state만 기록한다. prompt/provider raw response,
  credential, 전체 respondent 원문은 로그에 복사하지 않는다.

## Stage 2 자동 왕복

1. `/app/projects/{projectId}/market`을 연다.
2. 상단 `2. 사업 검증` 아래 `1. 시장 분석`, `2. 사업 모델`, `3. 컨셉 다듬기`가 각각
   `/market`, `/business-model`, `/concept-refinement`로 이동하는지 확인한다.
3. 시장 분석을 한 번 시작하고 FULL TaskRun ID를 기록한다. subjectType은
   `MARKET_RESEARCH_FULL`이어야 한다.
4. Market current가 완료되고 exact `MarketResearchVersion(FULL)`이 생긴 뒤 사용자가 중간 버튼을
   누르지 않아도 BM TaskRun이 생기는지 확인한다.
5. BM의 idempotency key가 `auto-bm-{FULL taskRunId}`이고 source market task/version, seed,
   selection revision, 시작 시 고정된 BM plan revision이 session과 일치하는지 확인한다.
6. BM current가 완료된 뒤 사용자가 버튼을 누르지 않아도 refinement round 1 TaskRun이 생기는지
   확인한다. key는 `auto-refinement-{BM taskRunId}`이어야 한다.
7. `/concept-refinement`가 FULL v3 refinement current/final을 표시하고 MAIN v2 endpoint를 호출하지
   않는지 확인한다.
8. `/business-validation`을 직접 열면 `/market`으로 이동하는지 확인한다.

성공 기준:

- FULL 한 건 -> exact BM 한 건 -> refinement round 1 한 건이다.
- Coordinator/Reconciler/Event listener가 별도 BM/refinement를 만들지 않는다.
- BM scheduling을 일시적으로 실패시켜도 완료된 FULL version은 SUCCEEDED로 남는다.
- refinement scheduling을 일시적으로 실패시켜도 완료된 BM version은 SUCCEEDED로 남는다.
- 위 두 실패에는 해당 화면의 수동 복구 동작을 사용할 수 있다.
- seed/selection/BM plan revision을 변경하면 이전 session/refinement는 stale이며 current로 사용되지 않는다.

## Stage 4 — 20명

1. `/app/projects/{projectId}/market-interview`를 연다.
2. `1 보여줄 것 확인`에서 현재 자극, 타겟 표현 범위, 20명 표본을 확인한다.
3. 20명으로 한 번 시작하고 During의 실제 event 진행과 TaskRun ID를 기록한다.
4. deterministic failure fixture를 사용할 수 있는 검증 환경에서는 respondent 한 명의 coding만
   최종 실패시키고 respondent 생성은 성공시킨다.
5. 완료 뒤 `usableInterviewCount=20`, `codedInterviewCount=19`, `codingFailureCount=1`인지 확인한다.
6. 실패 respondent의 transcript는 보존되고 codingTrace는 `UNCLASSIFIED`, theme participantIds에는
   포함되지 않는지 확인한다.
7. 결과가 주요 발견 -> 표본 범위 -> theme -> 이해도/차별점 -> 대표 응답자/원문 -> 실행 기록 순인지
   확인한다. theme을 누르면 실제 연결 respondent만 보이고 quote가 그 respondent answer의 실제
   substring인지 확인한다.

성공 기준: 개별 coding 실패로 전체 TaskRun이 FAILED가 되지 않으며 20 usable/19 coded 결과가 저장된다.

## Stage 4 — 40명

1. 20명 검증이 끝난 같은 Golden project에서 40명 새 실행을 시작한다.
2. Worker deadline 10분, lease 13분, Backend read timeout 14분보다 먼저 transport/lease가 끊기지
   않는지 확인한다.
3. 완료 결과에서 응답자 40명이 목록으로만 제공되고 기본 상태에서 원문 40개가 모두 펼쳐지지
   않는지 확인한다.
4. theme -> respondent -> raw answer 연결, comprehension/differentiation count, sampling/targeting,
   source seed/selection/BM plan revision과 실행 기록을 확인한다.

성공 기준: 40명 workload가 정상 terminal 상태에 도달하고 lineage와 모든 count가 persisted result와 같다.

## Hard-failure fixture 확인

- target 조건 0건: respondent 호출 전에 `MARKET_INTERVIEW_TARGET_UNAVAILABLE`로 실패한다.
- 20명 요청에서 usable이 10명 미만 또는 8명 미만: 전체 실패한다.
- codebook 자체가 repair 후에도 계약을 충족하지 못함: 전체 실패한다.
- unknown theme/unclassified row: 없는 의미를 만들지 않고 해당 coding만 집계에서 제외한다.

## 수집할 최소 evidence

- project ID 하나
- FULL/BM/refinement/Market Interview TaskRun ID와 terminal state
- subjectType과 idempotency key
- Market/BM version ID, seed ID, selection revision, BM plan revision
- Market Interview requested/attempted/usable/coded/failure count
- 오류 fixture의 safe code/reason과 stage

