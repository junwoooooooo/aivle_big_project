# V2-10F — TechOps Evidence Upload Wiring Result

## Outcome

IMPLEMENTATION COMPLETE. RUNTIME ACCEPTANCE PENDING.

The active TechOps UI no longer accepts a free-form `artifactRef`. It uploads a real project-scoped artifact through the existing local/S3-replaceable object storage port, then registers the returned artifact ID as a TechOps evidence reference.

## Existing infrastructure decision

- The repository had `ObjectStoragePort` with active local and S3-compatible adapters, safe normalized local path resolution, project-oriented object keys, and download capability.
- It did not have an active project-owned generic upload/controller/entity suitable for TechOps Evidence.
- F therefore adds the minimum V2 `ProjectEvidenceArtifact` metadata boundary while reusing `ObjectStoragePort`; no legacy document controller or conversational/document journey was reactivated.

## Files changed

Backend artifact boundary:

- `backend/src/main/java/com/aivle/backend/common/exception/ErrorCode.java`
- `backend/src/main/java/com/aivle/backend/file/object/ObjectKeyGenerator.java`
- `backend/src/main/java/com/aivle/backend/pipeline/artifact/api/ProjectEvidenceArtifactApiModels.java`
- `backend/src/main/java/com/aivle/backend/pipeline/artifact/api/ProjectEvidenceArtifactController.java`
- `backend/src/main/java/com/aivle/backend/pipeline/artifact/application/EvidenceArtifactUploadPolicy.java`
- `backend/src/main/java/com/aivle/backend/pipeline/artifact/application/ProjectEvidenceArtifactService.java`
- `backend/src/main/java/com/aivle/backend/pipeline/artifact/config/EvidenceArtifactProperties.java`
- `backend/src/main/java/com/aivle/backend/pipeline/artifact/domain/ProjectEvidenceArtifact.java`
- `backend/src/main/java/com/aivle/backend/pipeline/artifact/repository/ProjectEvidenceArtifactRepository.java`
- `backend/src/main/resources/application.yaml`
- `backend/src/main/resources/db/migration/V5__v2_10f_project_evidence_artifacts.sql`
- `backend/src/test/java/com/aivle/backend/pipeline/artifact/ProjectEvidenceArtifactTests.java`

Backend TechOps integration:

- `backend/src/main/java/com/aivle/backend/pipeline/techops/api/TechOpsApiModels.java`
- `backend/src/main/java/com/aivle/backend/pipeline/techops/application/TechOpsInputSnapshotFactory.java`
- `backend/src/main/java/com/aivle/backend/pipeline/techops/application/TechOpsService.java`
- `backend/src/main/java/com/aivle/backend/pipeline/techops/domain/TechOpsEvidenceReference.java`
- `backend/src/test/java/com/aivle/backend/pipeline/techops/TechOpsEvidenceArtifactTests.java`
- `backend/src/test/java/com/aivle/backend/pipeline/techops/TechOpsPreparationContractsTests.java`
- `backend/src/test/java/com/aivle/backend/pipeline/techops/TechOpsServiceAsyncTests.java`
- `backend/src/test/java/com/aivle/backend/pipeline/techops/TechOpsV2ContractTests.java`

Frontend:

- `frontEnd/src/features/tech-ops/api/techOpsApi.js`
- `frontEnd/src/features/tech-ops/hooks/useTechOps.js`
- `frontEnd/src/features/tech-ops/hooks/useTechOps.test.jsx`
- `frontEnd/src/features/tech-ops/pages/TechOpsPage.jsx`
- `frontEnd/src/features/tech-ops/pages/TechOpsPage.test.jsx`

Contracts:

- `docs/rebuild/NEW_PIPELINE_PRODUCT_SPEC_v1.0.md`
- `docs/rebuild/NEW_PIPELINE_UI_UX_SPEC_v1.0.md`
- `docs/rebuild/NEW_PIPELINE_DATA_MODEL_AND_API_CONTRACT_v1.0.md`
- `docs/rebuild/contracts/tech-ops-input-snapshot-v1.schema.json`

## Contracts implemented

- `POST /api/v3/projects/{projectId}/evidence-artifacts` accepts multipart `file`, verifies project ownership, and returns project-safe metadata with HTTP `201`.
- `GET .../{artifactId}/download` verifies ownership and non-deleted metadata, streams through the configured storage adapter, sets canonical content type, attachment filename, content length, and `nosniff`.
- Artifact deletion is a separate soft-delete endpoint and does not delete a TechOps evidence reference or immediately delete shared storage bytes.
- Storage keys and stored filenames are UUID-based under `projects/{projectId}/evidence/...`; normalized keys cannot escape the configured local root and the S3 adapter applies its existing object-key guard.
- Original filenames are NFC-normalized and reduced to a safe basename, including browser fake paths and traversal-like names.
- Allowlist: `.pdf`, `.csv`, `.xlsx`, `.xls`, `.docx`, `.txt`, `.png`, `.jpg`, `.jpeg`.
- The client `Content-Type` header is not authoritative. The server assigns canonical media type after PDF/image/OLE/OOXML signature or UTF-8 text validation. Executable/unsupported extensions and signature mismatches are rejected.
- Upload size is bounded by `app.evidence-artifact.max-size` (default 20 MB) and by Spring multipart limits; empty files are rejected.
- Object bytes remain outside the database. Metadata persists project ID, storage type/key, sanitized original filename, UUID stored filename, media type, byte size, `sha256:` digest, creator, timestamps, and soft-delete state.
- Storage and metadata hashes/sizes must match; failed persistence or transaction rollback schedules best-effort object cleanup.
- `TechOpsEvidenceReference` now uses a real `artifactId`; nullable legacy `artifact_ref` remains only for migration compatibility and the active UI/API never writes it.
- New references resolve artifact ID within the same project and exclude soft-deleted artifacts.
- Removing evidence soft-deletes only the reference. Artifact lifecycle is independent.
- TechOps Snapshot finalization rejects missing/deleted/legacy-only evidence artifacts and records only `artifactId`, evidence type, original/display filename, media type, size, SHA-256, optional description, and provenance. It never exposes storage key/path or raw bytes.
- The frontend implements `file select → artifact upload → artifactId evidence registration`, shows immutable file metadata, and removes the free-text artifact reference control.
- File parsing, BOM interpretation, OCR, AI estimate analysis, and supplier verification remain out of scope.

## Checks actually run

- `backend\gradlew.bat compileJava` — success.
- `backend\gradlew.bat testClasses` — success.
- Targeted Backend tests — 14 passed, 0 failed:
  - `ProjectEvidenceArtifactTests` 4
  - `TechOpsEvidenceArtifactTests` 2
  - `TechOpsPreparationContractsTests` 3
  - `TechOpsServiceAsyncTests` 3
  - `TechOpsV2ContractTests` 2
- The updated migration/schema assertions in `TechOpsV2ContractTests` were rerun after documentation/schema changes — 2 passed.
- Targeted Frontend Vitest — 8 passed, 0 failed:
  - `useTechOps.test.jsx` 4
  - `TechOpsPage.test.jsx` 2
  - `techOpsModel.test.js` 2
- Targeted ESLint for changed TechOps frontend files — success, no findings.
- TechOps Snapshot JSON Schema parse — success.
- `git diff --check` — success; only existing working-copy line-ending warnings were printed.

## Checks intentionally omitted

- Full backend regression and postgres/Testcontainers migration execution
- MinIO/S3 integration tests and real object-storage smoke
- Full frontend suite and production build
- Browser upload/download and mobile/accessibility manual acceptance
- Virus scanner/content disarm integration, file parsing, OCR, and AI analysis

These checks are intentionally deferred by `LOCAL_FAST_EXECUTION_PROFILE.md` or explicitly excluded by F.

## Remaining risks

- Flyway V5 must be exercised against the user's real PostgreSQL database; this Fast unit used source/contract tests rather than `postgresTest`.
- Local/S3 write, rollback cleanup, authenticated download streaming, reverse-proxy multipart limits, and Unicode download filenames need runtime verification.
- The policy detects supported signatures and rejects obvious content mismatches but is not malware scanning or content disarm.
- Existing legacy-only TechOps evidence rows must be removed and re-uploaded before Snapshot finalization.

## Exact continuation point

Next Unit is `V2-10G — FINAL CODE ACCEPTANCE + USER RUNTIME GATE`. Begin with the directive's bounded cross-unit code inspection and targeted acceptance matrix. Do not run prohibited full/Docker/browser/provider gates; write the final RESULT and exact runtime verification instructions.
