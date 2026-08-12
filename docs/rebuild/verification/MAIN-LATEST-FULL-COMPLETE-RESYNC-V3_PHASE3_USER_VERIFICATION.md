# PHASE 3 사용자 검증

1. 빈 PostgreSQL에서 V1~V23 전체 Flyway migration을 실행한다.
2. 기존 V22 적용 DB에서 V23만 추가 적용되고 기존 checksum이 바뀌지 않는지 확인한다.
3. AI container에 `pdfplumber 0.11.9`가 설치되고 PDF window extraction smoke가 통과하는지 확인한다.
4. Vite dev server에서 `/api`가 backend로 proxy되는지 확인한다.
5. 실제 `.env`에는 `TAVILY_API_KEY`, `KOSIS_API_KEY`, `DART_API_KEY`를 secret manager 정책에 따라 주입하되 저장소에 기록하지 않는다.
