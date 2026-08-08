# V2-10B — Concept Semantic Distinctness 결과

## 결과

구현 완료. 기존 canonical/major hash와 character 3-gram을 cheap filter로 유지하면서, 13개 실제 사업 축의 structured fingerprint와 ambiguous pair 전용 `CONCEPT_DISTINCTNESS_JUDGE`를 추가했다. semantic duplicate는 Legal 호출 전에 거부되고 bounded replacement를 사용한다.

## 변경 파일

- Backend: `ConceptFingerprint`, `ConceptSemanticDistinctnessResult`, `ConceptFactoryExecutionService`, `ConceptFactoryWorker`, `TaskType`, `ProjectJobQueryService`, 관련 테스트
- AI: `concept_distinctness_judge` task, Concept Candidate input/prompt, internal execution routing/type alignment, 관련 테스트·smoke fixture
- 계약: Master Plan, Product Spec, 본 RESULT와 USER_VERIFICATION

## 구현 계약

- 이름은 비교에서 제외하고 target/problem/value/mechanism/commercial/channel/platform/operation/partner/transaction/3개 역할을 비교한다.
- deterministic 결과는 `DUPLICATE`, `AMBIGUOUS`, `DISTINCT`로 분류한다.
- AMBIGUOUS pair만 AI judge에 보내고 출력은 decision, overlappingDimensions, materiallyDifferentDimensions, safeSummary만 허용한다.
- 후보 생성 입력에는 적격 후보의 structured fingerprint만 최대 5개 전달한다.
- 순서는 schema → LOCKED/origin → deterministic → semantic if needed → legal이다.
- duplicate는 `DUPLICATE_CONCEPT`로 기록하고 기존 replacement round/candidate cap/redesign 한도를 유지한다.

## 실제 실행한 검사

- AI targeted: distinctness judge, Concept schema, Java/FastAPI type alignment `10 passed`.
- Backend `compileJava`, `compileTestJava`, `ConceptFingerprintTests`, `ConceptFactoryWorkerTests`: 성공. 첫 compile은 신규 TaskType switch 누락을 발견해 보정한 뒤 재검증했다.
- `git diff --check`: 성공(LF→CRLF 안내만 존재).

## 의도적으로 생략한 검사

- 전체 suite, postgresTest, Docker/browser/provider smoke, frontend production build.

## 남은 위험

- 실제 semantic judge 품질과 latency는 V2-10G provider/runtime acceptance 전까지 미승인이다.
- AI judge 장애 시 후보를 Legal로 통과시키지 않고 해당 slot을 실패시키는 보수적 경계다.

## 정확한 계속 지점

V2-10C는 현재 TechOps prefill/decision JSON과 Finance 3개년 목표 schema를 canonical shared contract로 통합하는 지점에서 시작한다.
