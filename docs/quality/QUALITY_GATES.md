# Quality Gates

- Status: TARGET_CANONICAL
- Reviewed Against Current Baseline: 3aeff219d72e1be502ba4ad1cade7f7aca83d10e
- Document Phase: P1
- Introduced In Commit: 1549a8efa0aeb2ca400f4795c1c44b34868e4722
- Scope: Commands, success criteria, evidence and drift gates
- Supersedes: Legacy quality gate documents
- Implementation Status: PARTIAL

| Gate | Command/direction | Success | Evidence |
|---|---|---|---|
| Backend | Windows `.\gradlew.bat test`; Linux/CI `./gradlew test` | exit 0/no failures | backend/build/test-results/test |
| PostgreSQL/Flyway | Windows `.\gradlew.bat postgresTest`; Linux/CI `./gradlew postgresTest` | empty PostgreSQL에 Baseline fresh/validate와 JPA schema validation 통과 | backend/build/test-results/postgresTest |
| Storage | Windows `.\gradlew.bat minioTest`; Linux/CI `./gradlew minioTest` | integrity/boundary pass | backend/build/test-results/minioTest |
| Frontend lint | Windows `npm.cmd run lint`; Linux/CI `npm run lint` | exit 0 | local/remote log |
| Frontend test | Windows `npm.cmd run test:baseline`; Linux/CI `npm run test:baseline` | gate pass | command log |
| Frontend build | Windows `npm.cmd run build`; Linux/CI `npm run build` | exit 0 | dist/log |
| FastAPI | python -m pytest | selected tests pass | pytest output |
| Public contract | Redocly + drift direction | syntax/expected paths agree | contract job |
| Spring–AI | fixtures/integration | identity/schema/error/timeout/boundary pass | test reports |
| Docker E2E | smoke script | critical flow/recovery pass | log/artifact |
| Security | gitleaks/Trivy/dependency | configured gate pass | remote jobs |
| Docs | diff/links/metadata/tables | zero failures | governance evidence |

## Repository-local CI

`.github/workflows/ci.yml`은 push/PR에서 다음 gate를 자동화한다.

- Frontend: `npm ci`, lint, `test:baseline`, build. 허용 부채는 `test-debt-baseline.json`을 초과할 수 없다.
- AI: Internal fixture validator와 `ai/tests` pytest.
- Backend: 일반 test와 PostgreSQL/Testcontainers `postgresTest`.

실제 AI Provider, 법제처, `minioTest`, 전체 Docker E2E, Public OpenAPI 전면 drift 검사는 기본 CI 범위 밖이며 별도 명시적 검증으로 남는다.

Public controller, frontend client, legacy OpenAPI와 Target contract의 상태를 구분한다. P2 이후 반복 가능한 drift 검사를 마련한다.

Local exit 0과 remote CI를 구분한다. Remote 성공은 commit SHA와 job status/URL을 확인한 경우만 기록한다. 미실행은 이유와 영향, 후속 조건을 남기며 성공으로 기록하지 않는다.
