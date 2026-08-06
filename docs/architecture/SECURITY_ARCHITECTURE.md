# Security Architecture

- Status: TARGET_CANONICAL
- Code Baseline Commit: e16bd316ac881f4c5fab076e65c14657f6a8c7d4
- Document Phase: P2
- Introduced In Commit: 1549a8efa0aeb2ca400f4795c1c44b34868e4722
- Scope: Trust boundaries, authorization, secrets and secure processing
- Supersedes: Legacy security and admin policy documents
- Implementation Status: NOT_STARTED

## Trust boundaries

| Boundary | Untrusted input | Enforcement |
|---|---|---|
| Browser → Spring | token, identifiers, text, file, request metadata | authentication, authorization, owner scope, validation |
| Spring → AI | bounded JSON/text chunk와 correlation | internal auth, task allowlist, size/schema/chunk integrity; Storage URL 금지 |
| AI → provider/MCP | prompt/context, external response | endpoint/credential control, timeout, output validation |
| AI → Spring | structured JSON/error | identity/schema/provenance/domain validation; 초기 binary 금지 |
| Spring → Storage | key, bytes, metadata | generated key, checksum, content allowlist |

## Stable Core controls

JWT/refresh lifecycle, admin role, last active administrator protection, reauthentication/action token, session revocation, Project owner scope와 cross-owner 404를 유지한다. Controller뿐 아니라 service/repository query에서 소유권을 강제한다.

## Secrets and privacy

비밀값은 환경 또는 배포 secret mechanism으로 주입하고 코드·문서·client response·audit에 실제 값을 기록하지 않는다. 법령 MCP·법제처 API secret은 AI Server 환경변수로만 주입하고, 배포 secret mechanism을 사용하더라도 환경변수로 제공하며 Spring task payload에 포함하지 않는다. password, token, API key, provider raw body와 불필요한 개인정보는 logging에서 제외한다. 업무 입력을 provider에 전달하는 최소화/redaction 정책은 P2.3 이후 확정한다.

Spring–AI 호출은 TLS가 적용된 내부 network와 사용자 JWT가 아닌 service Bearer credential을 사용한다. Credential은 환경변수/deployment Secret으로 공급하고 request body와 log에 넣지 않는다. 기본 AI log는 correlation, task/version, duration/status, input hash prefix와 safe error code만 허용하며 full user text, prompt, raw response, FILE content와 개인정보 logging은 비활성화한다. 상세 규칙은 [internal v1 contract](../contracts/INTERNAL_AI_API_V1_CONTRACT.md)를 따른다.

## Secure coding

DOCX/일반 텍스트 allowlist와 함께 파일 path traversal, archive bomb, content spoofing, oversized input, SSRF, redirect, unsafe URL, injection, mass assignment와 stale owner check를 검증한다. AI/MCP/API 출력은 명령이나 신뢰된 schema가 아니라 untrusted data로 처리한다.

## Audit and failure

보안·관리 action은 actor/target/result/request correlation을 감사하되 secret을 제외한다. public 오류는 resource existence와 내부 topology를 누출하지 않는다. 상세 운영 정책은 [operations](../operations/ADMINISTRATION_POLICY.md)를 따른다.
