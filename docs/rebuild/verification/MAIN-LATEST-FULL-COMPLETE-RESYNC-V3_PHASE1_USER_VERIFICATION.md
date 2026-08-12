# PHASE 1 사용자 검증

1. TechOps 실행 payload가 `runtime_adapter.py`를 거쳐 donor engine의 7개 advice, gates, operatingCosts, readiness, pilotPlan, layer1Facts, layer2Evidence를 반환하는지 확인한다.
2. Market FULL 실행에서 선택한 Concept와 다른 source가 들어오면 AI 호출 전에 실패하는지 확인한다.
3. Research2 재수집 결과가 `runs-generated` 로컬 출력이 아니라 TaskRun 결과로 materialize되는지 확인한다.
4. OpenAI/Tavily/KOSIS/DART 키를 넣은 격리 환경에서 provider timeout과 5xx 오류 로그를 확인한다.
5. donor에 없는 `runs/p32-auto01` fixture를 제공한 뒤 design score의 남은 2개 테스트를 별도 확인한다.
