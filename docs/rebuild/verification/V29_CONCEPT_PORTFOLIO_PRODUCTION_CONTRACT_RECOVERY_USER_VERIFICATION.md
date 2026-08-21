# V29 Concept Portfolio Production Contract Recovery — User Verification

## Preconditions

1. Build new frontend, backend, and AI images from the reviewed working tree/commit.
2. Do not mutate or delete historical Idea Brief, Concept Portfolio, selection, hypothesis, Delta Legal, or TaskRun rows.
3. Record the deployed image digests and current Project 3 selection revision/status before retry.

## 1. Legacy Delta Legal Recovery

1. Open Project 3 Concept Portfolio and confirm the current selection is `DELTA_LEGAL_FAILED`, revision `3` (or the unchanged production revision).
2. Click Delta Legal retry once.
3. Verify the UI immediately shows a running/pending state and a new selection action `taskRunId` is subscribed through job events.
4. Verify duplicate clicks do not create a second task while the first request is active.
5. Query the current latest hypothesis rows:
   - `PRICE.final_value_json` remains the existing JSON string.
   - `CHANNELS.final_value_json` is now a JSON string containing the same ordered channel items, not an array or Python/Java list rendering.
   - historical lower proposal-version CHANNELS rows are unchanged.
   - `hypothesis_revision` is unchanged by repair alone.
6. Verify the selection transitions `DELTA_LEGAL_FAILED -> DELTA_LEGAL_PENDING -> READY_FOR_LEGAL_REPORT`, or returns to `DELTA_LEGAL_FAILED` with a truthful failure.
7. On terminal event, verify the screen refreshes and no stale task ID remains active.

## 2. Legal Diagnostics

1. In a non-production test environment, submit a malformed legal input with `channels.value` as an array.
2. Verify the failure remains `INVALID_REQUEST / FIELD_CONSTRAINT_VIOLATION`.
3. Verify `validationFields` includes:
   - `path=legalFactPattern.hypotheses.channels.value`
   - `expectedType=string`
   - `category=string_type`
4. Verify the actual channel text, API keys, and business-sensitive values are absent from logs.

## 3. New Refinement Contract

1. Run a refinement whose CHANNELS proposal is a flat list of two nonblank strings.
2. Accept it and verify persisted current `CHANNELS.final_value_json` is the deterministic one-line JSON string with both ordered items.
3. Submit nested object, object value, blank item, and arbitrary array variants; verify each is rejected as a contract error and is not persisted.
4. Verify normal existing string values are byte-for-byte unchanged.
5. Verify the two SOM hypotheses remain structured JSON objects.

## 4. Idea v1 -> Idea v2 -> Concept v2

1. Start with confirmed Idea v1 and current Concept Run v1.
2. Enter Idea edit. Change a field and verify the page marks the edit unsaved.
3. Try another module link and verify the warning appears; cancel and remain on the Idea page.
4. Use `수정 내용 반영하고 다시 정리하기`, complete review, and confirm.
5. Verify a new immutable Idea Brief snapshot v2 is created and v1 remains unchanged.
6. Verify module status refreshes immediately and Concept is authoritative `STALE`.
7. Open Concept Portfolio and verify Concept Run v1 results are not shown as current. Confirm the message `아이디어가 변경되어 사업안을 다시 생성해야 합니다.` and replacement CTA.
8. Start the replacement run and verify its `sourceIdeaBriefSnapshotId` equals Idea v2 `confirmedSnapshotId`.
9. Verify Concept Run v1 remains immutable history and Concept Run v2 becomes the current run.

## 5. Canonical Hash Invariant

1. Select a newly created TaskRun and read its persisted `input_snapshot_json` without reserializing it.
2. Recompute with the deployed backend dedicated hasher and deployed AI canonical function.
3. Verify all four values are identical:
   - recomputed persisted snapshot hash
   - `task_runs.canonical_input_hash`
   - internal AI request `canonicalInputHash`
   - AI server recomputation
4. Repeat with the sanitized shared DELTA vector and confirm `sha256:ed11cbe4aa78409c736efd57bdbd36af5621541f6e97eed9b6dbe6d3e6212108`.

## 6. Deployment Decision

- Required images: frontend = yes, backend = yes, AI = yes.
- Deploy only after explicit user approval.
- After deployment, keep rollback references to the prior three image digests. Do not roll back or rewrite database history as part of normal verification.
