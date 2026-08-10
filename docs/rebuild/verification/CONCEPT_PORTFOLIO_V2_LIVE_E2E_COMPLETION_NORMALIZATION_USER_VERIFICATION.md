# Concept Portfolio V2 LIVE E2E Completion Normalization — 사용자 검증

## 사전 조건

1. branch가 `rebuild/new-pipeline-v1`인지 확인한다.
2. `ai/notebooks/.ipynb_checkpoints/`와 `ai/recordings/`를 삭제하거나 덮어쓰지 않는다.
3. LIVE 키는 환경변수에만 두고 Notebook에 입력하지 않는다.

## MOCK 검증

1. `ai`에서 Jupyter를 시작한다.
2. `concept_portfolio_v2_lab.ipynb`를 새 커널로 열고 `MODE='MOCK'`를 유지한다.
3. Run All한다.
4. 04 schema가 모두 PASS이고 Provider 호출이 0인지 확인한다.
5. 08에 실제 AI interpretation 표, 14에 requested 7/reserve 상태, 21·24에 actual mechanics code/labelKo가 표시되는지 확인한다.
6. 44에서 `READY_FULL`과 `CONTRACT_PASS`를 확인한다.

## LIVE staged 검증

1. `MODE='LIVE'`로 바꾸고 커널을 재시작한다.
2. 03 환경 상태와 04 schema PASS/외부 작업 0을 확인한다.
3. 06~18을 실행해 Idea Brief 호출 1회, 비어 있지 않은 interpretation, 한국어 Plan, pool/reserve 상태를 확인한다.
4. 19~27을 실행해 Candidate가 한국어이고 `targetRegion`이 `OPEN`이 아니며 actual mechanics가 Candidate 필드와 맞는지 확인한다.
5. 28에서 `RUN_FULL_LEGAL_C1=True`로 바꾸고 실행한다.
6. 실패하면 raw traceback 대신 failureCode, officialEvidenceCount, allowedIndexes, invalidReturnedIndexes가 있는 표를 보존한다.
7. 성공하면 29의 `RUN_REMAINING_LEGAL=True`를 켜고 30~33을 실행한다.
8. 34에서 Concept를 선택하고 35의 7개 가설을 검토한다.
9. 36에서 `CONFIRM_ALL_PROPOSED=True` 또는 필요한 edit를 명시한다.
10. 법률 민감 edit가 있으면 37 `RUN_DELTA_LEGAL=True`로 실제 Legal 결과를 얻는다.
11. 38~40에서 Interpretation, ACCEPT Legal, 7 hypotheses, Delta Legal, 두 snapshot hash와 `CONTRACT_PASS`를 확인한다.
12. 43에서 새 versioned recording의 manifest가 `REPLAY_READY`인지 확인한다.

## REPLAY 검증

1. 성공한 새 LIVE 입력을 그대로 유지한다.
2. `MODE='REPLAY'`, 45 `RUN_ONE_CLICK_REPLAY=True`로 실행한다.
3. 외부 상위 작업 수가 0이고 결과가 LIVE와 동일한 계약 상태인지 확인한다.
4. promptVersion을 바꾼 기록은 `REPLAY_MISS`가 되어야 한다.

## 기대 최종 상태

- LIVE Full Legal C1 성공 전: `PENDING USER RETEST`
- 성공 후 나머지 후보가 모두 수용되면 `READY_FULL`; 후보 범위 입력 대기 1개면 `READY_LIMITED`
- 전역 LOCK 충돌이면 `NEEDS_INPUT`
- 최종 handoff: `CONTRACT_PASS`

