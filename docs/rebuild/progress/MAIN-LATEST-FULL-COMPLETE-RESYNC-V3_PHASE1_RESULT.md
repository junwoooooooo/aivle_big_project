# MAIN-LATEST-FULL-COMPLETE-RESYNC-V3 PHASE 1 결과

## 1. 조사한 범위

- TechOps advisor, input scaler, external evidence의 모델·prompt·정규화·검증·복구 계약
- Market/BM Research2의 수집, 재수집, run path, quote audit, design score, 숫자·단위 규칙, step 15~18 품질 가드
- Concept, Legal, Finance, Twin, Marketing AI의 main 역방향 차이와 full 강화 계약

## 2. 발견한 gap과 처리

- `REGRESSED`: full의 TechOps AI가 main 원형보다 축약되어 있었다. main의 7개 core 파일을 blob 단위로 이식했다.
- `PARTIAL`: Research2 엔진·규칙·fixture가 이전 버전이었다. main의 최신 runtime/rule/tool/test/resource를 이식했다.
- `MISSING`: quote audit, design score, funnel, PDF window, step 15~18 품질 테스트를 복구했다.
- `CONTRACT_MISMATCH`: main `run.py`는 `--progress-jsonl`을 받지 않는다. engine은 바꾸지 않고 full wrapper가 TaskRun progress를 발행한다.
- `QUALITY_GAP`: product run이 generic assumption을 읽을 수 있었다. full의 product assumption fail-closed adapter를 engine 외곽에 유지했다.

## 3. 변경 파일

- `ai/app/tasks/tech_ops_advisor/**`, `tech_ops_input_scaler/**`, `tech_ops_external_evidence/**`
- `ai/app/research/research2/**`, `ai/app/research/product_runner.py`, `runner.py`, `serialize.py`
- `ai/app/api/executions.py`, `ai/app/providers/structured.py`, 관련 AI tests

## 4. main에서 가져온 것

- TechOps core 7개 파일은 donor blob과 해시가 모두 일치한다.
- Research2 최신 수집·근거·검증·scoring·BM layer와 step 15~18 도구/fixture를 복구했다.
- provider 5xx의 안전한 오류 로깅을 이식했다.

## 5. full 때문에 adapter한 것

- `runtime_adapter.py`: canonical Concept/Market/BM/TechOps/legal input을 main TechOps engine 입력으로 변환하고 progress callback을 연결한다.
- `product_runner.py`: exact main runner 앞뒤에서 TaskRun progress를 발행한다.
- `runlog.py`, `verdict.py`: full product mode의 assumption isolation과 SOM fail-closed를 보존한다.

## 6. main과 일부러 다르게 둔 것

- main의 synchronous HTTP 진입점, sample fallback, local run 결과를 canonical persistence로 쓰는 방식은 full TaskRun/DB/ObjectStorage 계약과 충돌하여 이식하지 않았다.
- Twin runner의 progress observer는 engine 결과를 바꾸지 않는 full wrapper이므로 유지했다.

## 7. 검증

- TechOps core 7개 donor/full blob hash: 7/7 일치
- AI 관련 targeted: 134 passed
- Research2 step 15~18: 135 passed
- AI 전체: 594 passed, 4 failed, 0 skipped, warnings 6
- AI `compileall`: passed

AI 전체의 4개 실패는 CPV2 legacy raw seed fixture가 production facade의 canonical field를 제공하지 않는 기존 테스트 불일치이며 `FULL_START_SHA`에서 동일 4개 실패를 별도 tree로 재현했다. CPV2 core는 변경하지 않았다.

## 8. 남은 문제

- `test_design_score.py`: 17 passed, 2 failed. donor main tree에도 없는 `runs/p32-auto01` fixture를 참조하므로 `PRE_EXISTING_DONOR_FIXTURE_GAP`이다.
- OpenAI/Tavily/KOSIS/DART 실호출은 미검증이다.

## 9. 변경량

- PHASE 1 중심 변경은 Research2와 TechOps AI이며 전체 최종 diff stat에 합산한다.

## 10. 계속 지점

- PHASE 2에서 canonical backend source와 full TaskRun materialization을 검증한다.
