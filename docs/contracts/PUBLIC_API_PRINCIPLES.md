# Public API Principles

- Status: DRAFT_CONTRACT
- Code Baseline Commit: e16bd316ac881f4c5fab076e65c14657f6a8c7d4
- Document Phase: P2
- Introduced In Commit: 1549a8efa0aeb2ca400f4795c1c44b34868e4722
- Scope: Public API versioning and ownership rules
- Supersedes: Legacy workflow API contract
- Implementation Status: NOT_STARTED

Stable core는 기존 /api/v1을 유지할 수 있다. 신규 Project Workflow는 /api/v2를 사용한다. legacy Workflow에 compatibility redirect나 endpoint를 추가하지 않고 전환 완료 후 삭제한다.

모든 Project resource는 owner scope, 일관된 오류와 request correlation을 적용한다. 초기 FILE upload는 Spring을 통해 DOCX와 일반 텍스트 allowlist만 허용하며 AI Server로 파일 URL이나 bytes를 노출하지 않는다. Final Report public contract는 persisted version의 HTML view와 PDF export를 제공하고 초기 Markdown export는 제공하지 않는다.

Implementation-ready resource path, envelope, idempotency, pagination, capability binding과 request/response schema는 [Public API v2 Contract](PUBLIC_API_V2_CONTRACT.md)를 따른다. 이 문서는 Target contract이며 legacy machine-consumed `docs/api/openapi.yaml`은 구현 교체 전까지 별도 current contract로 유지한다.
