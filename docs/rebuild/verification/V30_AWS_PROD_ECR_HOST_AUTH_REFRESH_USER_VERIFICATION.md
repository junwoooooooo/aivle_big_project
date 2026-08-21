# V30 AWS Production ECR Host Auth Refresh — User Verification

## Preconditions

1. Obtain explicit deployment approval.
2. Confirm the EC2 instance role permits `ecr:GetAuthorizationToken` and repository pull actions.
3. Confirm the current known-good SHA images remain cached on the EC2 host and rollback metadata is intact.

## Deploy Verification

1. Start the normal production deploy for a full 40-character SHA.
2. Confirm the EC2/SSM deploy log reports `refreshing EC2 host ECR authentication` before `pulling SHA-pinned images`.
3. Confirm ECR login succeeds and no authorization token or password appears in logs.
4. Confirm `.env.production` contains only the requested SHA-pinned AI, backend, and frontend references after login succeeds.
5. Confirm all three pulls, rolling service recreations, Twin Bank mount validation, and local frontend health check succeed.
6. Confirm `deployed-sha` records the requested SHA only after successful health checks.

## Fail-fast Verification

1. In a controlled non-production host test, deny `ecr:GetAuthorizationToken` or provide an invalid region.
2. Run the deploy script with a valid test SHA.
3. Confirm the script exits before replacing `compose.prod.yaml` or changing any of the three production image references.
4. Confirm no image pull or service recreation starts.
5. Confirm no credential value is present in logs.

## Rollback Fallback Verification

1. In a controlled host test with known-good rollback images cached locally, make ECR login unavailable.
2. Run the rollback script.
3. Confirm it logs `ECR login failed; trying locally cached images` and continues to recreate all three services.
4. Repeat with ECR login succeeding but image pull failing; confirm it logs `image pull failed; trying locally cached images` and continues.
5. Confirm all services and `/healthz` become healthy using the cached rollback images.
