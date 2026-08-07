# IDEA-ZOMBIE-DERIVING-AND-TERMINAL-JOB-REUSE-FIX User Verification

DB를 수동으로 수정하지 않고 현재 stuck project를 그대로 사용한다.

1. 기존 stuck project를 reload하고 `RECOVERY` 상태가 표시되는지 확인한다.
2. old jobId `d30f6c46-b5aa-4060-a9c8-bbd8f4e0372f`를 기록한다.
3. **다시 분석하기**를 선택한다.
4. 새 `activeJobId`가 old jobId와 다른지 확인한다.
5. DB에서 old TaskRun이 `SUCCEEDED`로 보존되었는지 확인한다.
6. new TaskRun이 `QUEUED → RUNNING → NEEDS_INPUT` 또는 `SUCCEEDED`로 전이하는지 확인한다.
7. old job event sequence에 새 Event가 추가되지 않았는지 확인한다.
8. new jobId의 Event sequence가 1부터 시작하는지 확인한다.
9. 결과가 `NEEDS_INPUT`이면 IdeaBrief, TaskRun, terminal JobEvent가 모두 `NEEDS_INPUT`인지 확인한다.
10. 누락 필드 입력 또는 Review를 진행한다.
11. Idea Brief를 Confirm한다.
12. Concept Factory로 이동한다.

다음 상태는 허용하지 않는다.

- IdeaBrief `DERIVING` + active TaskRun terminal
- terminal JobEvent 뒤 같은 jobId의 `QUEUED`
- IdeaBrief `NEEDS_INPUT` + TaskRun `SUCCEEDED`
