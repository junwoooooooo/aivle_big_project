# V22-A.1 사용자 검증

1. 사업 검증 시작 후 BM Plan을 변경한다. Market 완료 뒤 BM이 시작되지 않고 기존 결과를 보존한 `STALE` 안내가 보여야 한다.
2. 실행 중 시작 버튼을 빠르게 두 번 요청해도 Work Center에 Market FULL이 하나만 생겨야 한다.
3. BM 실패 후 Plan을 변경하고 BM 재시도를 누르면 BM Job이 새로 생기지 않고 전체 재검증 안내가 보여야 한다.
4. Plan을 변경하지 않은 정상 흐름은 Market 완료 후 BM으로 이어져야 한다.

실제 PostgreSQL migration과 장시간 AI 실행은 **USER/ENVIRONMENT VERIFICATION PENDING**이다.
