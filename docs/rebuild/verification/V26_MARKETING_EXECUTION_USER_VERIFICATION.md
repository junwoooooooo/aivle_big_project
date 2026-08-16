# V26 Marketing Execution User Verification

VISUAL: USER REVIEW PENDING

In a later browser integration gate, verify:

1. The page title is “마케팅 실행” and clearly says it uses the current confirmed concept.
2. The visible AI-draft notice is understandable before reviewing generated copy.
3. Channel, tone, purpose, value proposition/source summary, and other generation options are usable on desktop and narrow screens.
4. Queued/running, prepared, failed, and previous-concept states use friendly Korean copy without hashes, revisions, TaskRun names, or provider codes.
5. A failed exact-source generation exposes “다시 시도”; a successful or historical result exposes a distinct new-draft action.
6. Previous successful/stale results remain readable after a concept change.
7. A current-concept regenerate creates a new history item without replacing the previous result.
8. An ambiguous start/retry/regenerate response recovers from current state and does not send the mutation twice.
9. Completion never auto-starts or auto-navigates to Marketing Test.

Automated V26 checks covered Backend lineage/idempotency/stale/retry/history, AI input/result boundaries, Frontend states/CTAs/ambiguity recovery, and selective lint. V25 tests remain deferred to the final integration gate.
