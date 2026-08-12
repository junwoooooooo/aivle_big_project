# PHASE 7 사용자 검증

1. 최종 보고서의 각 capability 경로를 main SHA와 비교한다.
2. `git diff FULL_START_SHA --`에서 보호 영역 변경이 없는지 확인한다.
3. `git diff --check`를 재실행한다.
4. 미검증으로 표기된 PostgreSQL/Flyway, Docker, provider, MinIO, browser journey를 실행한다.
5. 저장소 관리자가 변경 범위와 테스트 부채 분류를 승인한 뒤 커밋한다.
