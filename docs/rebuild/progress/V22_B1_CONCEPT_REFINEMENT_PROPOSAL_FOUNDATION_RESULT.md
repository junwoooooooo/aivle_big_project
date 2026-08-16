# V22-B1 Concept Refinement Proposal Foundation 결과

- START SHA: `06977c2f3809193de6f4916bc46651eb584ee118`
- DONOR SHA: `4ee74359a1b231359dc3131fb8eecb126462d2bf`
- 상태: **READY FOR V22-B2**

## 구현 계약

- 수동 API command만 `REFINE_FROM_MARKET` TaskRun과 `PROPOSING` round를 같은 transaction에서 만든다.
- round는 Business Validation session, exact Market/BM version, seed, selection/BM revision, `REFINEMENT_POLICY_V1`, TaskRun/attempt를 저장한다.
- exact version material만 사용하며 Market evidence는 BM 인용 우선 deterministic 순서로 최대 200개를 전달한다.
- Python은 proposal 생성 뒤 evidence gate와 drift gate를 실행한다. Java materialization은 exact task/round/source와 사용자 표시 필드를 재검증한다.
- 성공은 proposal/rejection을 저장하고 `AWAITING_DECISION` 또는 `NO_CHANGES`에서 멈춘다. 실패는 round만 `FAILED`로 만들고 Selection 상태를 유지한다.
- same-source 중복 start를 막고, same-key canonical input conflict 및 최대 3회의 explicit retry를 적용했다.
- source 변경 late result는 채택하지 않고 round를 `STALE`로 만든다.

## 변경 파일

- Backend: `BusinessValidationCoordinator`, Selection domain/materialization/worker, 신규 `pipeline/refinement/*`, V30 migration
- AI: `tasks/concept_refinement.py`, `validation/drift.py`, Selection input/output 및 facade branch
- Tests: Backend refinement focused 3 classes, AI refinement focused 1 file, 기존 constructor test 1개 호환 수정
- Docs: 이 결과 문서와 사용자 검증 문서

## 실제 검증

- Backend command: `.\gradlew.bat test --tests "com.aivle.backend.pipeline.refinement.*Tests"`
  - command 시도 5회: sandbox 다운로드 차단 1, Mockito test 실패 1, 코드 변경 후 PASS 실행 3
  - 실제 test suite 실행 4회, 최종 **11 tests PASS / failure·error·skipped 0**
- AI command: `& '.\ai\.venv\Scripts\python.exe' -m pytest ai/tests/test_concept_refinement_foundation.py -q`
  - Python runtime 탐색용 실패 command 2회, venv test suite 실행 2회
  - 최종 **9 passed**, 외부 AI 호출 0
- `git diff --check`: PASS
- 전체 Backend/Frontend suite, Frontend build, Docker, browser, 실제 AI/Market Research, PostgreSQL migration은 생략했다.
- Migration 환경 검증은 **ENVIRONMENT VERIFICATION PENDING**이다.

## 남은 위험과 계속 지점

- PostgreSQL에서 V30 FK/DDL 적용은 아직 실행하지 않았다.
- B1은 proposal을 적용하지 않으며 UI도 없다. B2에서 human-decision UI와 dedicated partial merge/apply를 별도 설계한다.
