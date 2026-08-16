# Stable Core Regression

- Status: TARGET_CANONICAL
- Code Baseline Commit: e16bd316ac881f4c5fab076e65c14657f6a8c7d4
- Document Phase: P1
- Introduced In Commit: 1549a8efa0aeb2ca400f4795c1c44b34868e4722
- Scope: Protected capabilities and concrete regression items
- Supersedes: Stable-core portions of legacy coverage documents
- Implementation Status: PARTIAL

| Area | Required checks | Success condition |
|---|---|---|
| Auth | signup/login/logout, invalid credentials, account state | expected status, no secret leak |
| JWT/refresh | validation, rotation/revocation, security version | old/revoked token rejected |
| Admin | authorization, last active admin protection | server enforcement |
| Reauth | purpose, expiry, one-time action token | mismatch/reuse rejected |
| Project CRUD | create/list/detail/update/delete | owner data only |
| Owner scope | every child query/mutation | cross-owner non-disclosing 404 |
| Storage | key/checksum/type/size/missing/orphan | unsafe/corrupt rejected |
| Flyway | empty PostgreSQL Baseline fresh, validate, clean disabled | V1 적용과 validation 성공 |
| Common error | correlation, validation/conflict/internal | safe envelope |
| Audit | success/failure admin/security, redaction | event without secrets |

P3에서 명시적 suite로 분리하기 전 현재 관련 테스트를 삭제하지 않는다. Evidence 방향은 backend test report, PostgreSQL/MinIO results, frontend/AI output, E2E log와 remote CI URL이다.
