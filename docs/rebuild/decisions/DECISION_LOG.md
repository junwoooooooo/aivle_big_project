# DECISION LOG

## D-001 새 파이프라인 전환

- 상태: 승인 대기
- 결정: 기존 G7 이후 계획을 폐기하고 R0~R7 재구축 체계로 전환한다.

## D-002 법률 검토 위치

- 결정: 별도 사용자 단계에서 제거하고 5 Concept Factory 내부 Loop로 통합한다.

## D-003 전역 Stage

- 결정: 전역 Stage 잠금을 제거하고 모듈별 상태와 Soft Dependency를 사용한다.

## D-004 외부 분석

- 결정: Entity·DB 공유 대신 Snapshot Handoff를 사용한다.

## D-005 기획 Revision 표시

- 결정: 사용자에게 v1/v2 대신 의미 기반 이름을 표시한다.

## D-006 DB

- 결정: 보존 데이터가 없으므로 새 Baseline으로 초기화한다.

## D-007 마케팅

- 결정: AIdev의 생성·편집 부분만 선별 이식하고 검증·A/B·Persona 종속은 제외한다.
