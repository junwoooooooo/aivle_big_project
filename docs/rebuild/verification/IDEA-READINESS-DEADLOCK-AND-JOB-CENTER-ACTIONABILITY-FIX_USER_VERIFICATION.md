# IDEA-READINESS-DEADLOCK-AND-JOB-CENTER-ACTIONABILITY-FIX User Verification

Use a clean project. Do not modify TaskRun or JobEvent rows manually.

## Idea Brief browser gate

1. Open the clean project Idea screen and enter an idea.
2. Submit Question Round 1 answers.
3. If Question Round 2 appears, answer every question.
4. Wait for `FINAL_SYNTHESIS` to finish.
5. Confirm that zero unanswered questions and zero required missing fields always open Review, never Recovery.
6. If blocking contradictions exist, confirm that Review displays them and the primary action says `저장하고 준비 상태 확인`.
7. Edit the fields related to a contradiction and submit the Review action.
8. Confirm that PATCH fields starts a new `FINAL_SYNTHESIS` job and the contradiction/readiness assessment is recalculated.
9. When contradictions are zero and the assessment is current, confirm that the primary action changes to `이 내용으로 컨셉 만들기`.
10. Confirm the Idea Brief and verify navigation to Concept Factory.

Fail this gate if `RECOVERY -> 다시 분석하기 -> RECOVERY` repeats for a response with no questions and no required missing fields.

## Job Center gate

1. During an unanswered Idea question, open Job Center.
2. Confirm that only the latest unresolved job appears under `입력이 필요한 작업` with `입력 필요`.
3. Record that job ID, submit the answer, and wait for the next TaskRun to start.
4. Refresh Job Center.
5. Confirm that the new `QUEUED`, `READY`, `RUNNING`, or latest unresolved `NEEDS_INPUT` job is active.
6. Confirm that the recorded old raw `NEEDS_INPUT` job is absent from `입력이 필요한 작업`.
7. Confirm that the old job appears under recent completion/processing history as `입력 반영 완료`.
8. If the old job was selected when its terminal event arrived, confirm that the old `입력 필요` notice clears or changes to `입력 반영 완료` after refresh.
9. If a later Job B also ends in `NEEDS_INPUT`, confirm that only Job B is actionable and Job A remains resolved history.

## Immutable history check

Inspect through the existing TaskRun/JobEvent diagnostic surface if available:

- Old TaskRun raw status remains `NEEDS_INPUT`; it is not rewritten to `SUCCEEDED`.
- Old terminal JobEvent sequence has no appended events.
- Project Job projection reports the old job as `rawStatus=NEEDS_INPUT`, `actionable=false`, `presentationStatus=RESOLVED_INPUT`.
