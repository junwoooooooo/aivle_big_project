# V2-10F — TechOps Evidence Upload Wiring User Verification

## Preconditions

- Start the application with Flyway enabled and a writable `OBJECT_STORAGE_LOCAL_ROOT`, or a configured S3-compatible bucket.
- Confirm `EVIDENCE_ARTIFACT_MAX_SIZE` is appropriate; default is 20 MB.
- Sign in as a project owner and open an editable TechOps preparation.
- Prepare small valid samples: PDF, CSV/TXT, XLSX/XLS, DOCX, PNG, and JPEG. Do not use sensitive production files.

## 1. UI and active API shape

1. Open the `실제 근거 자료` section.
2. Confirm there is a real `근거 파일` picker and no text field labeled `파일 또는 자료 참조`.
3. Select evidence type and a valid file, then press `파일 업로드 및 근거 추가`.
4. Inspect Network order.

Expected: multipart upload to `/api/v3/projects/{projectId}/evidence-artifacts` returns `201` and an `artifactId`; only then does JSON registration to `/tech-ops/preparation/evidence` send `evidenceType`, `artifactId`, and optional description. No `artifactRef`, storage key, or absolute path is sent by the active UI.

## 2. Metadata, hash, and storage safety

1. Confirm the Evidence list shows original filename, evidence type, canonical media type, size, and `sha256:` digest.
2. Inspect the database artifact row and storage root/bucket as an operator.
3. Compare the file SHA-256 with the stored metadata.

Expected: hashes and sizes match. The stored filename/key uses UUIDs under the project prefix. The API/Query never returns `storageKey`, stored absolute path, or raw bytes.

## 3. Filename traversal and content policy

1. Upload through an API client using a multipart filename such as `../../quote.pdf` or `C:\fakepath\quote.pdf` with valid PDF bytes.
2. Confirm the returned original filename is only `quote.pdf` and storage remains below the configured root/project prefix.
3. Try `.exe`, `.js`, `.zip`, and another unsupported extension.
4. Rename executable/plain bytes to `.pdf`, `.png`, `.docx`, or `.xlsx` and upload.

Expected: traversal components are removed; unsupported extensions return `FILE_TYPE_UNSUPPORTED`; signature/content mismatches return `FILE_SIGNATURE_INVALID`. The claimed client Content-Type alone never authorizes the file.

## 4. Size and empty-file limits

1. Upload an empty allowed-extension file.
2. Upload a file larger than `EVIDENCE_ARTIFACT_MAX_SIZE` and the configured multipart limit.

Expected: empty returns `FILE_EMPTY`; oversized returns HTTP 413/`FILE_TOO_LARGE`; neither creates referenceable metadata or leftover normal storage objects.

## 5. Ownership and deleted artifacts

1. As another user, try to upload to, download from, or delete an artifact under the owner's project ID.
2. As the owner, soft-delete a test artifact through the artifact delete endpoint.
3. Try downloading it and registering its ID as new TechOps Evidence.

Expected: foreign-project operations are denied without metadata leakage. Deleted artifact download/reference returns `EVIDENCE_ARTIFACT_NOT_FOUND`.

## 6. Reference versus artifact lifecycle

1. Upload one artifact and register it as Evidence.
2. Press the Evidence row's `삭제` button.
3. Verify the TechOps reference is soft-deleted.
4. Verify the artifact metadata/storage object was not automatically deleted.

Expected: reference removal and artifact deletion are distinct operations.

## 7. Snapshot metadata boundary

1. Register at least one valid artifact Evidence and finalize a ready TechOps Snapshot.
2. Inspect `evidenceReferences` in the snapshot.

Expected: each item has `artifactId`, `evidenceType`, `originalFilename`, `displayName`, `mediaType`, `sizeBytes`, `sha256`, optional description, and `USER_PROVIDED_EVIDENCE`. It contains no `artifactRef`, `storageKey`, stored filename/path, presigned URL, or raw file bytes.

## 8. Download

1. As the owner, call `/api/v3/projects/{projectId}/evidence-artifacts/{artifactId}/download` with normal authentication.
2. Verify the downloaded bytes/hash.
3. Inspect headers.

Expected: HTTP 200, canonical Content-Type, attachment filename, exact Content-Length, and `X-Content-Type-Options: nosniff`. The bytes match the uploaded file.

## Acceptance record

Record project/artifact/evidence IDs, storage provider, sanitized filename, size, SHA-256, upload/reference HTTP statuses, ownership-denial result, delete/download result, and finalized Snapshot hash. Runtime acceptance remains pending until these checks pass in the user's environment.
