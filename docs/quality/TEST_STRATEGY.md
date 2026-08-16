# Test Strategy

- Status: TARGET_CANONICAL
- Code Baseline Commit: e16bd316ac881f4c5fab076e65c14657f6a8c7d4
- Document Phase: P1
- Introduced In Commit: 1549a8efa0aeb2ca400f4795c1c44b34868e4722
- Scope: Stable Core, vertical slice, contract and evidence strategy
- Supersedes: Legacy testing and coverage documents
- Implementation Status: PARTIAL

## Layers

P3 evidence adds Target TaskRun state/attempt/result tests, Spring context migration validation, Spring internal-client boundary coverage and FastAPI execution tests. Full Stable Core, PostgreSQL, frontend, Docker E2E, fixture validator and OpenAPI lint remain required local/remote gates; an unexecuted gate is never recorded as passed.

- Stable Core: auth/JWT/refresh/admin/Project owner/Storage/Flyway/error/audit.
- Domain unit: version, state/gate, stale, selection and validation.
- Spring integration: API/security/owner query/transaction/persistence.
- Spring–AI/FastAPI: identity, timeout/error, forbidden data access.
- Frontend: client, state/view model, route guard, UX/accessibility.
- E2E/manual: critical path, recovery, export, operations.

Legacy tests는 기능 삭제와 대체 test 존재 후 제거한다.

| Phase | Minimum evidence |
|---|---|
| P1/P1.1 | Markdown link/metadata/table/diff/no-code |
| P2 | contract cross-reference/drift fixtures |
| P3 | full Stable Core, TaskRun concurrency, Spring–AI, FastAPI, Flyway |
| P4–P10 | affected Stable Core + backend/frontend/AI + owner/stale/error + E2E |
| P11 | Admin authorization/policy/audit와 Landing frontend/accessibility |
| P12 | replacement suite, baseline fresh/validate, legacy reference scan |
| P13 | full local suite, Docker E2E, manual testing, security, Remote CI |

## Local command set

- Backend Windows: cd backend; .\gradlew.bat test; .\gradlew.bat postgresTest; .\gradlew.bat minioTest
- Backend Linux/CI: cd backend; ./gradlew test; ./gradlew postgresTest; ./gradlew minioTest
- Frontend Windows: cd frontEnd; npm.cmd ci; npm.cmd run lint; npm.cmd run test:baseline; npm.cmd run build
- Frontend Linux/CI: cd frontEnd; npm ci; npm run lint; npm run test:baseline; npm run build
- AI: cd ai; python -m pytest
- E2E: ./scripts/docker-e2e-smoke.ps1 -EnvFile .env.e2e.example

실행 환경이 없으면 NOT_EXECUTED와 이유를 evidence에 기록한다.
