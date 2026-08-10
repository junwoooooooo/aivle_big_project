# New Pipeline Rebuild Documentation

이 디렉터리는 `bp_new_3` 전면 재구축의 유일한 신규 기준 문서다.

## 우선순위

1. NEW_PIPELINE_MASTER_PLAN
2. PRODUCT SPEC
3. UI/UX SPEC
4. DATA/API·EXTERNAL CONTRACT
5. REPOSITORY·DB·IMPLEMENTATION PLAN
6. EXECUTION RULES·PROMPTS
7. DECISION LOG·PROGRESS RESULT

`docs/archive/**`는 참고 자료이며 신규 계약을 변경하지 않는다.

## 시작 절차

1. R0에서 Manifest를 실제 `git ls-files`와 대조한다.
2. 문서 충돌을 해결한다.
3. R1부터 단계별로 진행한다.
4. 각 단계 완료 전 실제 수동 Gate를 확인한다.
