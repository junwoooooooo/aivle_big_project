# NEW PIPELINE IMPLEMENTATION PLAN v1.0

## R0 Design Freeze

- 모든 신규 문서 승인
- 파일 작업 Manifest 검토
- API·Schema Fixture 검증
- Legacy 참조 목록 고정

## R1 Hard Cutover Foundation

- 새 Project Shell·Route
- 모듈 상태 API
- 전역 Stage 의존 제거
- Legacy Navigation 제거
- DB Baseline
- TaskRun·JobEvent 연결

완료 Gate: 기존 Journey 메뉴 0, 새 Route 직접 접근, clean DB start.

## R2 Idea Brief

- Form·첨부
- AI derive
- Question Card
- Brief review·confirm
- 비동기 Event·refresh recovery

완료 Gate: 실제 Provider로 한 Turn과 Brief 확정.

## R3 5 Concept Factory

- Legal Context
- 5 Slot·Attempt
- Generate·validate·legal·redesign·replace
- Workboard·Timeline
- 5개 동시 공개

완료 Gate: 실제 Provider·DB·브라우저에서 5개 완성.

## R4 Compare·Select·Handoff

- 카드·비교표
- selection snapshot
- market handoff stub
- external status shell

## R5 Planning Revision·External Shell

- market result fixture
- change proposal UX
- finalization
- BM·재무+Persona Shell

## R6 Marketing

- AIdev 선별 포팅
- 새 source snapshot
- generate·edit·save·download

## R7 Cleanup·Stabilization

- Legacy code·route·table·test 삭제
- 전체 backend/AI/frontend/postgres
- Docker E2E
- Browser·mobile·a11y
- provider smoke

## 작업 묶음

R1+R2, R3+R4, R5+R6은 한 세션에서 수행 가능하나 각 단계 Gate와 결과 문서는 분리한다. R0와 R7은 단독 수행한다.
