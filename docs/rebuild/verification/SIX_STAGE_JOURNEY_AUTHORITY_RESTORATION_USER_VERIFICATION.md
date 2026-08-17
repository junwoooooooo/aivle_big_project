# Six-Stage Journey Authority Restoration — User Verification

## Browser checklist

Browser acceptance is not performed by this patch. At the next manual checkpoint, verify:

1. Project overview shows exactly six stations in this order: 사업 기획, 사업 검증, 출시 준비, 가상 인터뷰, 마케팅 전략, 최종 보고서.
2. 사업 검증 enters the current consolidated Business Validation screen.
3. 가상 인터뷰 enters 시장 인터뷰 when incomplete, then 트윈 패널 조사 after Market Interview completes.
4. Workspace onboarding, project help, and public landing all describe six stages.
5. Market Interview and Twin Panel Survey remain separate child experiences and retain synthetic/virtual disclosures.
6. Current E2E TaskRun routes and Docker failure harness behavior remain unchanged.

## Current evidence

- Normal Docker E2E: PASS — user observed before this taxonomy-only patch
- Failure E2E (`ai-down`, `minio-down`, `malformed`, `checksum`, `timeout`, `stale`): PASS — user observed
- Paid provider: NOT RUN
- Browser visual review: NOT RUN
- Static guard, focused regression, frontend lint/baseline/build: PASS
