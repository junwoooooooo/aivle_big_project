# Workflow UX

- Status: CURRENT_AS_BUILT
- Baseline date: 2026-08-04

## 현재 Journey

- Idea 화면은 원문, AI 해석, Idea Origin Draft, 사용자 확정값, AI 가정과 보완 질문을 구분한다.
- Legal 화면은 Precheck 실행, 추가 질문, revision 제안, source/evidence와 Guardrail 결과를 구분한다.
- Concept 화면은 Origin Integrity와 Concept Legal Validation을 모두 통과한 적격 Concept만 표시한다.
- 최대 후보 budget 안에서 적격 3개를 확보하지 못하면 기준을 낮추지 않고 보완 필요를 표시한다.
- Concept 3개 표시가 현재 범위 종료이며 후속 MVP로 자동 이동하지 않는다.

## 보존 MVP

`ProjectLayout`은 현재 Journey 3단계와 보존 MVP 6개 화면을 별도 목록으로 표시한다. 보존 MVP 화면 내부의 다음 링크는 실험 화면 묶음 안에서만 이동하며 공식 Journey 자동 연결을 뜻하지 않는다.

Persona Interview는 독립 실행 결과로, Marketing comparison은 질적 비교로 표현한다. 실제 사용자 A/B 실험, 구매확률 또는 전환율로 표현하지 않는다. Final Report의 AI 제안과 사용자 결정을 분리한다.

## 오류와 실행 상태

Frontend는 공통 `apiClient`의 safe error normalization을 사용한다. Legal은 persistent TaskRun 진행/실패를 복원하고, Concept은 in-memory execution batch 상태를 복원한다. 동기 MVP 기능은 긴 request timeout을 사용하지만 202/polling으로 오인하지 않는다.
