# Data and Storage Architecture

- Status: TARGET_CANONICAL
- Code Baseline Commit: e16bd316ac881f4c5fab076e65c14657f6a8c7d4
- Document Phase: P2
- Introduced In Commit: 1549a8efa0aeb2ca400f4795c1c44b34868e4722
- Scope: RDB, Object Storage, files and AI result flows
- Supersedes: Legacy data model and object storage documents
- Implementation Status: NOT_STARTED

## Ownership

Spring이 RDB와 Object Storage의 유일한 관리 주체다. RDB는 aggregate state, versions, TaskRun 계열, provenance, audit와 FinalReport snapshot을 저장한다. Object Storage는 사용자 FILE source, 검증된 binary AI result, report export를 저장한다.

## User file input

1. Frontend가 인증된 Spring API에 파일을 전달한다.
2. Spring이 Project owner, Service Policy, type, size, filename과 content를 검증한다. 초기 allowlist는 DOCX와 일반 텍스트이며 PDF, XLSX, PPTX는 제외한다.
3. Spring이 checksum과 collision-safe object key를 생성한다.
4. Spring이 bytes를 Storage에 저장하고 metadata를 transaction 정책에 따라 RDB에 기록한다.
5. 실패 시 orphan을 남기지 않도록 보상/reconciliation 방향을 적용한다.
6. Spring parser/extractor가 내용을 text로 변환한다. parser는 IdeaSource 처리 구성요소이며 DOCX 전용 Workflow를 만들지 않는다.
7. AI Server에는 원본 FILE bytes나 Storage reference가 아니라 검증된 추출 text/chunk만 전달한다.

## AI JSON result

AI Server가 [internal v1 contract](../contracts/INTERNAL_AI_API_V1_CONTRACT.md)의 bounded JSON을 Spring response로 반환한다. Spring은 task identity, canonical input hash, contract version, body size, schema, request-local reference, provenance와 domain invariant를 검증한다. 검증 실패 결과는 domain table에 채택하지 않고 TaskAttempt 실패 evidence로 제한한다. AI Server는 RDB/Storage lookup을 하지 않으며 request-local key를 Spring이 실제 Domain reference로 매핑한다.

## AI binary result

AI Server는 Storage에 접근하지 않는다. 초기 Spring–AI 계약은 binary/streaming transport를 제공하지 않고 bounded inline JSON/text chunk를 사용한다. 후속 binary 확장도 Spring-mediated여야 하며 Storage/presigned URL 또는 임시 공유 Storage를 허용하지 않는다. Spring은 content type, magic bytes, size, checksum, expected task/result identity를 검증한 후에만 binary를 Storage에 저장할 수 있다.

## Lifecycle

RDB reference가 artifact lifecycle의 source of truth다. Final Report의 초기 HTML view는 RDB structured snapshot에 기반하고 Spring이 생성한 PDF export만 Object Storage에 저장한다. soft delete, retention, quarantine, orphan reconciliation과 report export 보존 기간의 상세는 구현 Phase에서 결정한다. legacy artifact는 참조 해제 후 삭제한다.

## Migration

Flyway V1–V26은 수정하지 않는다. 신규 schema는 새 migration으로 추가하고 Target 전환 후 legacy FK/index/table을 신규 migration으로 제거한다. legacy 데이터는 이관하지 않는다.
