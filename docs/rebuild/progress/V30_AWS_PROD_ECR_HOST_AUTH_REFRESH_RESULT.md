# V30 AWS Production ECR Host Auth Refresh — Result

## IMPLEMENTED

- Production deploy now defaults `AWS_REGION` to `us-east-1` while preserving the existing `ECR_REGISTRY` contract.
- The EC2 host refreshes ECR authentication with its instance role after validating the candidate compose file and before replacing the production compose file or image references.
- A deploy-side ECR login failure exits through `set -Eeuo pipefail` before any new SHA is applied and before the automatic rollback trap is armed.
- Rollback now attempts the same host-side ECR authentication refresh before its remote image pull. Login or pull failure remains non-fatal so Docker can recreate services from locally cached rollback images.
- GitHub Actions workflow, SHA pinning, Twin Bank validation, health checks, and existing rollback state contracts were not changed.

## ROOT CAUSE REMOVED

The GitHub Actions runner authenticated only its own Docker client. The separate Docker client on the SSM-managed EC2 host retained an expired ECR authorization token, and `docker compose pull` therefore failed even though runner build and push succeeded. ECR authentication is now refreshed on the host for every deploy and rollback attempt.

## FILES CHANGED

- `scripts/aws-prod-deploy.sh`
- `scripts/aws-prod-rollback.sh`
- This result document
- `docs/rebuild/verification/V30_AWS_PROD_ECR_HOST_AUTH_REFRESH_USER_VERIFICATION.md`

## CONTRACTS IMPLEMENTED

- Deploy order: validate inputs and candidate compose -> host ECR login -> install new compose and SHA-pinned env references -> pull -> rolling recreation and health checks.
- Deploy ECR login failure cannot mutate the production compose or production image references to the requested SHA.
- Rollback order after restoring prior references: validate compose -> best-effort host ECR login -> best-effort pull -> cached-image recreation fallback -> health checks.
- Authentication password remains on stdin and is never stored in a shell variable or printed by these scripts.

## CHECKS ACTUALLY RUN

- Git Bash syntax check: `bash -n scripts/aws-prod-deploy.sh` — PASS.
- Git Bash syntax check: `bash -n scripts/aws-prod-rollback.sh` — PASS.
- Scoped `git diff --check` for both scripts — PASS.
- `.github/workflows/deploy-aws.yml` scoped diff check — PASS, unchanged.

## CHECKS INTENTIONALLY OMITTED

- No AWS deployment, ECR login, remote image pull, container restart, or production health check was executed.
- No unrelated backend, frontend, or AI test suite was run because this substage changes only host deployment shell scripts.

## REMAINING RISKS

- The EC2 instance role must continue to allow `ecr:GetAuthorizationToken`, and the host must have working AWS CLI and Docker binaries.
- Runtime authentication and cached-image fallback still require controlled production verification after explicit user approval.

## CONTINUATION

Wait for explicit user approval before deployment. Then follow the V30 user verification steps and retain the existing known-good cached images until all health checks pass.
