# V23-C Market Research Controlled Smoke Closure 결과

## Authority

- START SHA: `ab8bcccb5ed616c5f90aaf13ff4e1771affac35d`
- DONOR SHA: `4ee74359a1b231359dc3131fb8eecb126462d2bf`
- Phase A authority: focused 90 tests PASS. 이번 continuation에서는 재실행하지 않았다.
- 사용자는 `pet-treat-15` 기존 본문 최대 8개, 문서당 30,000자, section 최대 10회, summary 최대 3회의 OpenAI Responses API 전송을 명시 승인했다.

## Corpus

- source run: `pet-treat-15`
- deterministic 구조 검사: usable documents 38, eligible documents 38.
- 원본 `result.json`, `a3_bodies.json`, `run.jsonl`은 수정하지 않았다.

## 실제 실행 결과

- smoke script invocation: 1회.
- 실제 provider request / 유료 호출: 0회.
- 원인: provider client 생성 전 ignored runner가 `scorecard` 모듈의 `research2/tools` 경로를 포함하지 않아 `ModuleNotFoundError`로 종료됐다.
- 추가 smoke invocation: 0회. 사용자 승인 범위에 따라 자동 재실행하지 않았다.
- ignored runner의 import path는 오프라인으로 수정했고 `SMOKE_RUNNER_IMPORT_OK`를 확인했다. tracked 제품 source 변경은 없다.

## 측정값

- selected documents: 0
- base attempts: 0
- re-asks: 0
- total attempts: 0
- successful responses / failures / timeouts / bad JSON: `0 / 0 / 0 / 0`
- candidate passages / verified / rejected: `0 / 0 / 0`
- exact quote ratio: 측정 불가(분모 0)
- promoted before/after cap: `0 / 0`
- section counts: 측정 불가
- CHANNEL total / BM eligible: `0 / 0`
- summary calls: 0
- payload bytes: 생성 전 실패로 측정 불가

## 판정

Closure gate의 `real paid smoke count = 1`, candidate passage, quote ratio, promotion, payload, summary, 실제 FULL→BM bridge 측정을 충족하지 못했다.

**V23-C BLOCKED — SMOKE RUNNER IMPORT PATH PREVENTED THE APPROVED PROVIDER SMOKE**

