# P0 Final Blocker Closure 사용자 검증

## 1. Docker 및 fresh migration

Docker가 가능한 환경에서 현재 working tree를 그대로 사용한다. 새 DB volume에서
`docker compose build`, `docker compose up -d`를 실행하고 backend, ai-server, postgres,
minio, frontend health를 확인한다. 3000 포트가 충돌하면 frontend port를 13000으로 지정한다.

## 2. Launch requestId 증거

backend 로그에서 requestId `2e080add-a4e9-4be0-a6f3-0c2af13bccc3`와
`launch-readiness`, `technology`, `operations`, `Exception`, `Caused by`를 함께 조회한다.
project 1의 최근 TaskRun에서 Technology/Operations task type, state, last error code와 시간을 확인한다.
secret과 Authorization header는 반환하지 않는다.

## 3. Browser acceptance

- Stage 2: Market 성공 → BM 성공 → refinement 적용 → Stage 2 완료
- Launch Technology: current selected concept + valid professional DOCX → 500 없이 시작·완료
- Launch Operations: current selected concept + valid professional DOCX → 500 없이 시작·완료
- Stage 4: Market Interview 완료만으로 Stage 4 완료, Twin 미완료가 block하지 않음
- Finance: 세 narrative 목록에서 `153200000원`이
  `153,200,000원 (1억 5,320만원)`으로 표시됨

invalid DOCX는 400 계열의 명시적 validation이어야 하며 generic internal error여서는 안 된다.

## 4. Market provider

현재 상태는 `PAID_PROVIDER_APPROVAL_REQUIRED`다. 승인 후 한 번만 재현하고 다음을 함께 보존한다.

- TaskRun state와 last error code
- backend root exception
- AI-server root exception
- provider HTTP status/error category
- Research2 schema/materialization 단계

key, token, Authorization header, raw secret은 출력하지 않는다.

## 5. E2E

normal Docker E2E 후 `ai-down`, `minio-down`, `malformed`, `checksum`, `timeout`, `stale`를
현재 working tree에서 각각 실행한다. 각 scenario와 fresh migration 결과를 별도로 기록한다.
