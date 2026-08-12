# PHASE 5 사용자 검증

1. CI의 충분한 timeout으로 backend 전체 suite를 실행한다.
2. 시작 SHA 전용 worktree에서 frontend Auth/App 18개 실패를 재현해 baseline을 확정한다.
3. donor가 참조하는 `runs/p32-auto01` fixture 출처를 확인한 뒤 design score 2개를 실행한다.
4. PostgreSQL·MinIO·stub provider를 포함한 integration smoke를 실행한다.
5. 브라우저에서 SSE 404/JOB_NOT_FOUND가 무한 재연결되지 않는지 network panel로 확인한다.
