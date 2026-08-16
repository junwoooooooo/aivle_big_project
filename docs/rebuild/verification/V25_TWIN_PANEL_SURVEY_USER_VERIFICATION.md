# V25 Twin Panel Survey — User Verification

Status: USER REVIEW PENDING — FINAL INTEGRATION GATE.

## Product verification

1. Open `/app/projects/{projectId}/twin-survey` and confirm the title is **트윈 패널 조사** and the visible notice says this is an AI virtual-panel simulation, not a real consumer survey.
2. Confirm no survey starts on page entry, Market Interview completion, Business Validation completion, or Refinement completion.
3. Prepare comparison stimuli and verify only 50, 100, and 300 are selectable, with 100 selected by default and no representation/error-margin promise.
4. Start explicitly and confirm RUNNING copy refers to virtual-panel simulation rather than contacting real consumers.
5. Confirm completed percentages are described only as results inside the virtual panel and are not presented as population purchase rates.
6. Change the authoritative concept/BM, return to the page, and confirm the previous result is historical with **현재 사업안으로 다시 조사**; it must not auto-run.
7. Confirm a current FAILED run offers **다시 시도**, while a FAILED run from an old source cannot be retried.
8. Simulate an ambiguous start/retry response and confirm the client performs one current GET and does not resend the mutation.
9. Confirm Market Interview and Twin Panel Survey remain independent routes and neither result is automatically used as the other's input.
10. Review desktop/mobile layout for the disclaimer, sample selector, action group, result sections, and stale warning.

## Final Integration Gate backlog

- V23 Market Research real-provider quality smoke.
- V24 Market Interview Backend focused tests.
- V24 Market Interview AI focused tests.
- V24 Market Interview Frontend focused tests.
- V24 Market Interview bounded real-provider smoke.
- V25 Twin Panel Backend focused tests.
- V25 Twin Panel AI focused tests.
- V25 Twin Panel Frontend focused tests.
- V25 bounded real-provider smoke.
- Business Validation → Refinement → Market Interview → Twin Panel Survey source-lineage integration.
- Desktop/mobile visual verification.

Visual: USER REVIEW PENDING — FINAL INTEGRATION GATE.
