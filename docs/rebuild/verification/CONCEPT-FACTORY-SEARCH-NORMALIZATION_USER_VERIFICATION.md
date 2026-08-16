# Concept Factory 검색 정규화 사용자 검증

## 검증 상태

현재 코드는 구현 및 정적/직접 관련 테스트를 완료했다. 실제 Provider·PostgreSQL·5-slot runtime acceptance는 아래 사용자 검증이 필요하다.

## 1. 환경 시작

한 세션에서 plain Compose 명령만 사용한다. e2e overlay를 섞지 않는다.

```powershell
$env:VITE_ENABLE_PIPELINE_DEBUG='true'
docker compose build ai-server backend frontend
docker compose up -d
docker compose ps
```

모든 서비스가 정상 상태인지 확인한다. 이 단계에서 `docker compose down -v` 또는 DB volume 삭제는 실행하지 않는다.

## 2. V9 migration 확인

Backend 로그에서 Flyway 최신 버전이 9인지 확인한다. 기존 V8 데이터베이스에서도 V9 한 건만 추가 적용되어야 한다.

확인 기준:

- `inspected_candidate_count` 16과 20이 DB constraint로 거부되지 않는다.
- 음수 inspected count는 `ck_concept_run_inspected`로 거부된다.
- `provider_transient_retry_count` 2 이상이 DB constraint로 거부되지 않는다.
- 음수 retry count는 `ck_concept_run_provider_retry`로 거부된다.

## 3. 정상 5-slot 실행

확정된 Idea Brief로 Concept Factory를 한 번 시작한다.

확인 기준:

- 다섯 슬롯이 각 variation focus에 맞는 실질적 차이를 가진다.
- 같은 문제·사용자·LOCKED 원본을 공유한다는 이유만으로 `DUPLICATE_CONCEPT`가 되지 않는다.
- 타 슬롯의 과거 거절 후보가 이후 슬롯의 hard blocker로 작동하지 않는다.
- 5개가 모두 적격일 때만 run이 `COMPLETED`가 되고 동시에 공개된다.
- canonical/major/high-confidence clone이 남아 있으면 `COMPLETED`가 되지 않는다.

## 4. Targeted replacement 확인

한 후보가 의도적으로 기존 적격 Concept 또는 현재 슬롯 후보와 충돌하는 입력을 사용한다.

확인 기준:

- 다음 교체 요청에 `replacementContext`가 포함된다.
- `conflictSource`, `closestConflict`, `overlappingDimensions`가 실제 충돌을 설명한다.
- `mustChangeDimensions`가 최소 2개다.
- 교체 후보가 지정 축에서 실질적으로 달라지면 법률 검토로 진행한다.
- 타 슬롯 soft negative 예시가 교체 후보를 deterministic duplicate로 만들지 않는다.

## 5. 법률 질문 의미 확인

다음 세 종류를 각각 확인한다.

- 결제 주체·정산 흐름·역할과 같은 설계 공백: `REDESIGNABLE`.
- 자격 보유 파트너 계약처럼 통제로 전환 가능한 외부 조건: evidence-backed Provider 판단 또는 `REDESIGNABLE`; `NEEDS_FACTS` 금지.
- 현재 보유 인허가·기존 필수 계약·실제 고정 관할처럼 Concept이 설계할 수 없는 외부 사실: `NEEDS_FACTS` → Backend `NEEDS_INPUT`.

마지막 경우의 확인 기준:

- 후보 discard/replacement가 발생하지 않는다.
- Run 응답 `requiredInputs`에 `code`, `question`, `source`, `candidateSlot`이 표시된다.
- `nextAction`은 `PROVIDE_REQUIRED_INPUTS`다.
- Job Center에서 terminal `NEEDS_INPUT`으로 표시된다.

## 6. Provider retry와 지표 확인

일시적 Provider 실패를 한 호출 안에서 2회 이하로 유도한다.

확인 기준:

- Provider retry는 replacement round와 inspected candidate를 소비하지 않는다.
- 누적 retry count가 2 이상이어도 DB 오류가 발생하지 않는다.
- candidate result가 validation에 들어간 경우 `initialCandidateSuccessCount + replacementCandidateCount + redesignCount = inspectedCandidateCount` 관계가 성립한다.

## 7. Job Center terminal 정합성 확인

실패하는 Concept Factory TaskRun 한 건을 만든다.

확인 기준:

- 실패 직후 active jobs에서 제거된다.
- recent jobs에 `FAILED`로 나타난다.
- terminal SSE 수신 후 active/recent/현재 선택 상세가 즉시 서버 상태로 갱신된다.
- rejection event의 `taskType`이 후보 생성, 법률 검토, 재설계 phase와 일치한다.

## 8. 검증 결과 기록

다음 값을 결과 문서에 추가한다.

- run ID와 source snapshot hash.
- 슬롯별 initial/replacement/redesign/법률 상태.
- inspected count와 Provider retry count.
- 각 replacement의 conflict source 및 must-change 축.
- 최종 5개 canonical/major hash.
- terminal Job Center active/recent 응답.

실제 5/5 적격 완료가 확인되기 전 상태는 다음을 유지한다.

- IMPLEMENTATION COMPLETE
- STATIC/TARGETED VERIFICATION COMPLETE
- RUNTIME ACCEPTANCE PENDING USER TEST
