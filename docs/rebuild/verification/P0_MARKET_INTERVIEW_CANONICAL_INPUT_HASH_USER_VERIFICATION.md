# P0 Market Interview canonical input hash 사용자 검증

1. 선택 사업안이 유효한 프로젝트의 `4. 가상 인터뷰` 화면으로 이동한다.
2. sampleSize 20으로 Market Interview 시작 요청을 보낸다.
3. 응답이 HTTP 202이고 `CANONICAL_INPUT_HASH_MISMATCH`가 발생하지 않는지 확인한다.
4. 생성된 TaskRun에서 다음을 확인한다.
   - `contract_version = 1.0`
   - `task_schema_version = 1.0`
   - `locale = ko-KR`
   - input snapshot의 `contract = market-interview-input-v2`
   - input snapshot의 `schemaVersion = 2.0`
   - input snapshot의 `sampleSize = 20`
5. 실제 provider 호출은 수행하지 않고 RUNNING 진입 및 TaskRun 생성까지만 확인한다.

