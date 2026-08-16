# Current User Journey

- Status: CURRENT_CANONICAL
- Baseline date: 2026-08-04

| Step | 사용자 경험 | 시스템 결과 | 범위 |
|---|---|---|---|
| Idea 입력 | TEXT 또는 FILE과 선택 title 입력 | IdeaSource 저장 | 현재 Journey |
| AI 해석 | 저장한 Idea를 해석하도록 요청 | Interpretation과 Origin Draft 생성 | 현재 Journey |
| Origin 보완 | 질문별 답변과 확인 출처 입력 | USER_CONFIRMED 값 누적 | 현재 Journey |
| Origin 확정 | 보완 내용을 적용 | CONFIRMED Origin version | 현재 Journey |
| Legal Precheck | 실행 후 진행/실패 상태 확인 | Precheck result와 source/evidence | 현재 Journey |
| Legal 보완 | 질문 답변 또는 Category별 통합 수정 계획을 한 번에 승인 | 새 Origin 1개와 자동 재검토 Run | 현재 Journey |
| Legal 보완 | 추가 질문 답변 또는 revision 제안 수락 | 새 Origin draft/version | 현재 Journey |
| Legal Guardrail | 통과 가능한 법률 결과 확인 | Concept용 Guardrail | 현재 Journey |
| Concept 생성 | 후보 생성 요청 | Origin/Legal 검증과 내부 대체 생성 | 현재 Journey |
| 적격 3개 확인 | 통과 후보를 함께 비교 | ELIGIBLE Concept 3개 | 현재 Journey 종료 |

Concept 결과에서 자동으로 분석·선택 화면으로 이동하지 않는다.

Quick/Detailed/Selection/Persona/Interview/Marketing/Final Report 화면은 Route로 접근 가능한 보존 MVP 실험 기능이다. 이 화면에서 제공하는 내부 다음 링크는 보존 MVP 묶음의 실험 흐름일 뿐 현재 공식 Journey의 제품 gate가 아니다.

실패 경험은 입력 보완 필요, 재시도 가능, AI dependency 장애, 결과 검증 실패를 구분한다. AI 결과는 사용자 확정값이나 사용자 최종 결정을 자동으로 덮어쓰지 않는다.
