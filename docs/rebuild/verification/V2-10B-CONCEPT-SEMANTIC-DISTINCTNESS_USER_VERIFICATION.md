# V2-10B 사용자 검증

```powershell
cd backend
.\gradlew.bat test --no-daemon --tests "com.aivle.backend.pipeline.concept.ConceptFingerprintTests" --tests "com.aivle.backend.pipeline.concept.worker.ConceptFactoryWorkerTests"

cd ..\ai
.\.venv\Scripts\python.exe -m pytest tests/test_concept_distinctness_judge.py tests/test_concept_factory_schema.py tests/test_internal_task_type_alignment.py -q
```

예상 1~4분. 모두 exit code 0이어야 한다.

브라우저/provider 검증에서는 `월 정액 멤버십`과 `매달 비용을 내는 구독 회원제`, `개인 참가자 즉석 팀 연결`과 `당일 경기 인원 자동 배정` pair가 동시에 공개되지 않는지 확인한다. 같은 사용자라도 mechanism/operation/revenue가 실질적으로 다른 pair는 둘 다 남아야 한다. duplicate 판정 후보의 Concept Legal Review 호출 수가 0인지 Task/Provider 로그로 확인한다.

실패 시 두 candidate structured summary, judge safe result, slot/attempt ID, Legal 호출 count를 수집한다.
