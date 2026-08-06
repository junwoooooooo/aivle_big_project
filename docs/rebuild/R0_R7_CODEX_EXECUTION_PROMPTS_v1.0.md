# R0-R7 CODEX EXECUTION PROMPTS v1.0

## 공통

`REBUILD_EXECUTION_RULES_v1.0.md`를 먼저 적용한다.

## R0

문서와 현재 코드 경로를 대조해 Manifest를 확정한다. 코드는 변경하지 않는다. 충돌·누락·의존성을 보고한다.

## R1

새 Project Shell, Route, Module Status, DB Baseline을 구현한다. Legacy Navigation을 즉시 제거한다. 기능 화면은 Empty/Not Connected Shell로 제공한다.

## R2

Idea Brief Form, AI derive, Question Card, Review, Confirm, TaskRun/Event/restore를 구현한다. Conversational UI를 삭제한다.

## R3

5 Slot Concept Factory와 Legal Context·Assessment·Evidence·bounded redesign/replacement·Workboard를 구현한다. 5개 동시 공개 Gate를 검증한다. Provider 실패는 Concept Attempt 오류로 분류하고 Slot 상태에 `PROVIDER_FAILURE`를 추가하지 않는다. transient retry와 schema repair는 각각 1회로 제한하며 permanent provider failure는 retry 불가 terminal failure로 처리한다.

## R4

5개 비교·선택·Selected Snapshot·Market Handoff Stub·상태 Shell을 구현한다. Quick Assessment를 만들지 않는다.

## R5

Market Result Fixture, 의미 기반 Change Proposal UX, Finalized Planning Snapshot, Business/Persona 외부 Shell을 구현한다. 외부 알고리즘을 만들지 않는다.

## R6

AIdev에서 Marketing Generation 관련 코드만 선별 포팅하고 신규 Source Snapshot·Canvas·Editor·저장·다운로드를 구현한다.

## R7

Legacy 코드·Controller·Route·Migration·Test를 삭제하고 전체 회귀, DB clean, Docker E2E, Provider Smoke, Browser·Mobile·A11y를 완료한다.
