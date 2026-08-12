# MAIN-TECHOPS-RESYNC 사용자 검증

## 1. 사전 조건

- 테스트용 PostgreSQL, Backend, AI, Frontend, ObjectStorage를 실행한다.
- 실제 운영 데이터나 Twin Bank 대신 별도 테스트 프로젝트를 사용한다.
- provider key 값은 문서나 화면에 복사하지 않는다.

## 2. Migration

1. 새 데이터베이스에 Flyway를 실행한다.
2. `V22__tech_ops_advisory_reports.sql`이 성공했는지 확인한다.
3. 기존 V1~V21 checksum이 변하지 않았는지 확인한다.
4. `tech_ops_advisory_reports.task_run_id` unique 및 source FK가 생성됐는지 확인한다.

## 3. Source lineage

1. CPV2에서 Concept를 선택하고 Market Seed를 생성한다.
2. 해당 Seed로 Market FULL을 완료하고 그 정확한 Market version으로 BM을 완료한다.
3. TechOps Phase A 입력을 확정해 Snapshot을 만든다.
4. Advisory를 시작하고 TaskRun immutable input의 selection, concept, seed, Market, BM, Snapshot ID가 화면의 current source와 일치하는지 확인한다.
5. 다른 프로젝트의 source ID를 사용한 요청은 거부되는지 확인한다.

## 4. Phase B 사용자 흐름

1. 기술·운영 분석 화면에 Phase A와 Phase B가 함께 보이는지 확인한다.
2. Advisory 시작 시 Work Center와 화면 타임라인이 QUEUED/RUNNING으로 갱신되는지 확인한다.
3. 완료 결과에서 decision, summary, 7개 advice, 6개 이상 gates, 5개 이상 operating costs, 4개 readiness, pilot plan을 확인한다.
4. Layer 1 facts, Layer 2 evidence 링크, 사용자 evidence가 서로 분리되어 보이는지 확인한다.
5. 재실행 후 이전 결과가 history에 남고 새 TaskRun 결과가 current로 표시되는지 확인한다.
6. 브라우저 localStorage를 비워도 결과가 사라지지 않는지 확인한다.

## 5. Stale 및 실패

1. Advisory 실행 중 current Market 또는 BM을 새 버전으로 바꾼다.
2. 이전 source 실행이 `STALE`로 끝나고 report가 current 결과로 저장되지 않는지 확인한다.
3. provider를 실패시키고 fake/sample 결과 대신 안전한 FAILED 상태와 재실행 CTA가 보이는지 확인한다.
4. 존재하지 않는 job의 SSE가 404 또는 `JOB_NOT_FOUND` 뒤 무한 재연결하지 않는지 개발자 도구 Network에서 확인한다.

## 6. Navigation과 Finance 독립성

1. 표시 순서가 아이디어→사업안→시장 분석→사업 모델 분석→기술·운영 분석→재무 분석→트윈 패널 조사→마케팅 콘텐츠 제작인지 확인한다.
2. 내부 URL과 API module ID가 기존 값으로 유지되는지 확인한다.
3. TechOps 결과가 없어도 current Market FULL과 exact BM lineage가 있으면 Finance 준비가 가능한지 확인한다.
4. Finance 화면에 TechOps prerequisite 문구나 TechOps 이동 CTA가 없는지 확인한다.

## 7. External evidence

1. 외부 키가 없는 상태에서도 Advisory가 Layer 1 facts만으로 완료 가능한지 확인한다.
2. Tavily/KOSIS 실패가 전체 실행 실패로 전파되지 않는지 확인한다.
3. DART opt-in이 false이면 법인 조회가 실행되지 않는지 확인한다.
4. Layer 2 evidence 링크가 실제 provider 결과에만 존재하는지 확인한다.

## 8. 합격 기준

- source ownership 및 exact lineage 위반 요청이 모두 차단된다.
- Advisory 결과 contract의 개수와 basis ID가 모두 유효하다.
- TaskRun/JobEvent/SSE/DB가 canonical이며 localStorage나 동기 장기 HTTP가 필요 없다.
- 실패 시 fake/demo/sample 성공 결과가 표시되지 않는다.
- Finance는 TechOps와 독립이다.
