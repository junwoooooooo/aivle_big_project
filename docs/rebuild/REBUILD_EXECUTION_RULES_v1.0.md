# REBUILD EXECUTION RULES v1.0

## 1. Preflight

HEAD, branch, status, AGENTS, 관련 코드·Migration·Test, 현재 단계 문서를 확인한다. 브랜치가 다르면 중단한다. 자동 branch 전환·reset·clean·revert·commit·push를 하지 않는다.

## 2. 문서 우선순위

Master → Product → UI/UX → Data/API → Handoff → Implementation → Current Stage Prompt → Result.

Legacy redesign 문서는 구현 근거일 뿐 계약이 아니다.

## 3. 범위

현재 R 단계만 수행한다. 미래 단계 선구현, 임의 Default, 상태 추가, 계약 완화, Schema 검증 우회를 금지한다.

## 4. Legacy

새 코드에서 legacy import 금지. 이전 UI가 Route·Navigation에 나타나면 즉시 실패다.

## 5. 테스트

실패 Test 단독 → Targeted → Compile/Lint/Build → 조건부 전체. Test 삭제·skip·assert 완화·hook bypass 금지.

## 6. Provider

Provider Output Schema 변경 시 실제 Provider Smoke가 필수다. Mock만으로 성공 주장 금지.

## 7. 문서

각 R 단계는 `docs/rebuild/progress/Rn_RESULT.md`를 작성한다. 실제 실행·생략·수동 미검증을 구분한다.

## 8. 종료

다음 단계로 자동 진행하지 않는다. 변경 파일·계약·테스트·남은 위험을 보고하고 멈춘다.
