# Conversational Validation Workspace

이 디렉터리는 대화형 AI 사업검증 워크스페이스 재설계의 정본을 보관한다.

## 문서 우선순위

1. `CONVERSATIONAL_VALIDATION_WORKSPACE_SPEC_v1.0.md`
2. `CONVERSATIONAL_VALIDATION_WORKSPACE_IMPLEMENTATION_PLAN_v1.0.md`
3. 승인된 ADR 및 Current-to-Target Map
4. `CODEX_EXECUTION_PROMPTS_v1.0.md`
5. `reference/`의 배포용 문서

## 구현 원칙

- 단계 G0부터 G11까지 한 단계씩 진행한다.
- 한 Codex 작업에서 두 단계를 함께 구현하지 않는다.
- 사용자 승인 없이 다음 단계로 넘어가지 않는다.
- 외부 계약, DB, 상태 전이가 변경되면 문서와 테스트를 함께 수정한다.
- 불변식 변경은 `decisions/DECISION_LOG.md`에 ADR을 남기고 승인받는다.
- 기존 Journey는 신규 E2E가 완성되기 전까지 제거하지 않는다.

## 기준선

- 저장소: `chamgo260210/bp_new_2`
- 최초 Design Freeze 기준 SHA: `967c19a8eca17ff24f159175fb3e7ecc9fb6cf9d`
- Design Freeze: v1.0