# NEW PIPELINE PRODUCT SPEC v1.0

## 1. 제품 정의

AI 사업검증 플랫폼은 사용자의 초기 아이디어를 구조화하고, 공식 근거 기반 법률 구현 가능성 검토를 통과한 5개의 구체적 컨셉을 만든다. 사용자는 하나를 선택해 시장분석으로 전달하고, 시장 근거를 반영해 최종 기획을 확정한 뒤 외부 BM·재무 및 페르소나 응답 모듈과 마케팅 콘텐츠 제작으로 이어간다.

## 2. 사용자

- 초기 사업 아이디어를 빠르게 구체화하려는 예비 창업자
- 신규 서비스 기획을 검증하려는 팀
- 규제 민감 사업의 초기 방향을 비교하려는 사용자

## 3. 핵심 사용자 작업

1. 아이디어 입력과 보완
2. Idea Brief 확인·확정
3. 컨셉 팩토리 실행과 진행 확인
4. 5개 컨셉 비교
5. 컨셉 선택
6. 시장분석으로 전달
7. 시장분석 변경안 판단
8. 최종 기획 확정
9. 외부 분석 결과 확인
10. 마케팅 콘텐츠 생성·편집·저장

## 4. 범위

### 포함
- Idea Brief
- 5 Concept Factory
- 법률 Context·Assessment·Evidence
- 비교·선택
- 외부 모듈 Handoff
- 기획 변화·최종 확정
- 마케팅 콘텐츠
- 비동기 작업 센터

### 제외
- 시장분석 내부 알고리즘
- BM·재무 내부 알고리즘
- 인터뷰지 생성 내부 알고리즘
- Persona 응답 엔진 내부 알고리즘
- 기술·운영 분석
- 마케팅 A/B 테스트와 Persona 재검증

## 5. 사용자 단계

1. 아이디어 정리
2. 컨셉 생성·법률검토
3. 컨셉 비교·선택
4. 시장분석·기획 확정
5. BM·재무 분석 + 페르소나 응답 테스트
6. 마케팅 콘텐츠 제작

## 6. 제품 불변식

- 사용자 확정 전 AI 제안은 사실 확정이 아니다.
- 법률검토 미통과 Draft는 공개하지 않는다.
- 컨셉 후보 5개는 서로 구별되어야 한다.
- 컨셉 생성 루프는 bounded하다.
- 외부 모듈은 Snapshot을 소비한다.
- Finalized Planning Snapshot이 후속 정본이다.
- BM·재무와 Persona 결과는 기획을 변경하지 않는다.
- 마케팅은 최종 기획을 사용한다.
- 페이지 접근은 열고 실행 Action만 조건을 확인한다.

## 7. 주요 성공 지표

- Idea Brief 확정 완료율
- 컨셉 5개 완성률
- 평균 후보 검사 수와 재설계 수
- 컨셉 선택 완료율
- 시장분석 Handoff 성공률
- 최종 기획 확정률
- 마케팅 콘텐츠 생성 완료율
- 비동기 작업 실패 복구율

## 8. 법률 표현

사용자에게 `완벽한 법률검토` 또는 법률 자문 완료를 암시하지 않는다. `공식 근거 기반 법률 구현 가능성 검토`로 표현하고, 사실관계·시점·전문가 확인 필요성을 표시한다.

## 9. Concept Provider 실패 경계

Concept Provider 실패는 사용자 진행 상태가 아니라 개별 Attempt 실행 오류다. Slot 상태에는 `PROVIDER_FAILURE`를 두지 않는다. Candidate 생성의 일시 오류는 동일 Slot에서 최대 1회 재시도하며, Candidate schema/domain 오류는 1회 Repair 후 replacement 정책을 따른다. Legal Review schema/source/provider/internal 오류는 법률 거절로 표시하거나 Candidate를 교체하지 않고, 생성된 Candidate를 보존한 `REVIEW_RETRY_PENDING`으로 종료한다.

## 10. Idea Brief synthesis와 assessment freshness

- clarification limit는 새로운 질문 생성 횟수의 상한이다. 마지막 질문 답변 뒤에는 `FINAL_SYNTHESIS`를 실행하며 질문은 빈 배열이어야 한다.
- 사용자의 답변, canonical field, overview, attachment 변경은 기존 assessment를 무효화한다. Review 수정 후에는 최신 summary·contradiction·readiness 분석이 끝날 때까지 Confirm할 수 없다.
- `READY_FOR_REVIEW`는 최신 canonical assessment hash가 일치할 때만 Confirm 가능하다. max round 도달 자체는 ready 조건이 아니다.
- 사용자에게는 내부 `STALE` 용어 대신 `변경 내용을 다시 정리하고 있습니다.`라고 표시한다.

## 11. Legal contract와 retry UX

- Screening AI는 관련 citation subset만 반환하고 omitted citation은 시스템이 excluded로 계산한다.
- 각 material legal finding은 자신의 official evidence reference를 포함한다. 사용자 문자열 배열과 전체 evidence union은 시스템이 파생한다.
- 법률 `REJECTED`만 rejected event와 Candidate replacement를 만든다. schema/provider/source/internal failure는 안전한 review retry/failed event를 사용한다.
- Slot 기본 카드는 후보 생성 횟수, 법률 검토 상태, 재설계 횟수를 표시한다. provider/repair call count는 기본 UI에서 숨긴다.
- Failed run은 `이어서 시도`와 `처음부터 새로 만들기`를 제공한다. 이어서 시도는 새 TaskRun과 새 activeJobId를 만들며 eligible Slot과 보존 Candidate를 재사용한다. `NEEDS_FACTS`는 Idea Brief 보완, snapshot 변경은 새 Run 생성을 안내한다.
- Shared Legal Context와 base official evidence는 snapshot별로 한 번 구축해 Slot 간 재사용하고 Candidate 신규 활동만 delta evidence retrieval 대상이 된다.

## 12. Actionable NEEDS_INPUT UX

- `NEEDS_INPUT`은 빈 화면이나 실패 반복 상태가 아니다. unanswered question이 있으면 질문을, 질문이 없고 `missingFieldKeys`가 있으면 canonical field 직접 입력 폼을 표시한다.
- 질문과 missing field가 모두 없는 비정상 `NEEDS_INPUT` 또는 AI derivation failure에는 새 `FINAL_SYNTHESIS` TaskRun을 만드는 재분석 Action을 제공한다.
- 누락 필드 입력은 Answers API가 아닌 fields PATCH를 사용한다. 변경이 반영되면 `DERIVING`과 새 `activeJobId`를 받고 최종 분석이 끝난 뒤 Review 또는 다시 actionable `NEEDS_INPUT`으로 이동한다.
- 질문이 0개인 상태에서는 빈 Answers request를 전송하지 않는다.
- Clarification 질문은 required/regulatory-sensitive missing field를 optional field보다 우선한다.
- `FINAL_SYNTHESIS`가 현재 사실에서 required field를 합리적으로 유추하면 `AI_PROPOSED`로 제안할 수 있다. 확정할 수 없는 required field는 manual completion 대상으로 유지한다.

## 13. Idea asynchronous execution identity

- Terminal TaskRun과 Job ID는 immutable execution history이며 새로운 사용자 Action에 재사용하지 않는다.
- 사용자 command idempotency와 canonical content hash를 분리한다. 동일 command key replay는 동일 execution을 반환하지만 새 command key는 canonical content가 같아도 새 TaskRun을 생성한다. 동일 input의 active TaskRun 차단은 유지한다.
- Idea Brief `NEEDS_INPUT`, TaskRun `NEEDS_INPUT`, terminal JobEvent `NEEDS_INPUT`은 같은 execution에서 일치해야 한다. `READY_FOR_REVIEW`는 TaskRun `SUCCEEDED` 및 JobEvent `COMPLETED`와 일치한다.
- `DERIVING`인데 active TaskRun이 terminal인 상태는 유효한 RUNNING 상태가 아니라 복구 가능한 invalid state다. 조회 화면은 spinner 대신 recovery Action을 제공하고 재분석은 과거 history를 수정하지 않은 새 TaskRun을 만든다.
- Terminal JobEvent 뒤에는 같은 jobId의 어떤 Event도 추가할 수 없다.
