# Internal AI v1 Contract Fixtures

- Status: MACHINE_CONSUMED
- Baseline date: 2026-08-04
- Scope: Spring–FastAPI v1 contract fixtures and validator
- Implementation Status: IMPLEMENTED; USER_VERIFICATION_PENDING

이 디렉터리는 [Internal Spring–AI API v1 Contract](../../INTERNAL_AI_API_V1_CONTRACT.md)의 executable fixture다. Production DTO 자체는 아니지만 validator가 계약 표와 manifest를 직접 읽는다.

## Coverage

- 13개 Java/FastAPI TaskType의 valid request/response
- `IDEA_LEGAL_PRECHECK` source pipeline 계약
- `CONCEPT_LEGAL_VALIDATION` Guardrail Batch 계약
- `TEXT`/`ko-KR` canonical text request
- `PLAIN_TEXT`, 잘못된 locale/language negative case
- deadline retryable 및 service token missing/invalid error fixture
- response identity/hash 및 기존 task schema negative case

Concept Legal Batch의 candidateKey 누락·중복·알 수 없는 값과 extra field 거부 정책은 FastAPI Pydantic/domain tests와 Spring domain validator에서 함께 유지한다.

## Structure

- `manifest.json`: fixture category, task type, schema coverage와 expected validator rule
- `validate_fixtures.py`: 표준 라이브러리 기반 field-table/envelope/domain validator
- `common/`: canonical hash와 stable error fixture
- `tasks/`: 13개 task의 positive request/response
- `negative/`: 하나의 primary invariant를 위반하는 negative fixture

## User-run commands

```powershell
python docs/contracts/fixtures/internal-ai-v1/validate_fixtures.py
python -m py_compile docs/contracts/fixtures/internal-ai-v1/validate_fixtures.py
```

Validator는 network, DB, Object Storage, Spring/FastAPI runtime이나 외부 package를 사용하지 않는다. 실제 secret, provider raw response와 사용자 payload를 오류 출력에 포함하지 않는다.

모든 JSON은 UTF-8 without BOM, LF, comment 없는 단일 contract object다. Positive fixture는 credential/JWT, provider/model identity, Storage URL/key, presigned URL, local path와 FILE bytes를 포함하지 않는다. 각 manifest `coveredSchemas`는 실제 fixture에서 관찰되는 named schema와 정확히 일치해야 한다. Positive 집합은 전체 named schema를 커버하고 negative fixture는 선언한 단일 invariant가 정확한 validator rule로 실패해야 한다.
