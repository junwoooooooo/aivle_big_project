# MAIN-LATEST-FULL-COMPLETE-RESYNC-V3 PHASE 3 결과

## 조사 및 변경

- full 기존 migration V1~V22를 확인했다. main의 competitor seed V22 의미는 번호 충돌 때문에 신규 `V23__research_competitor_seeds.sql`로 추가했다.
- 기존 migration은 수정·rename하지 않았다.
- Research2 PDF extraction에 필요한 `pdfplumber==0.11.9`를 AI requirements에 추가했다.
- Vite 개발 `/api` proxy를 추가했다.
- Research2 로컬 검증 출력 `runs-generated/`을 canonical 결과로 오인·추적하지 않도록 ignore 처리했다.
- TAVILY/KOSIS/DART/TechOps DART toggle/AI timeout/CORS 설정을 대조했다. 실제 `.env`와 secret은 읽지 않았다.

## main과 다르게 둔 것

- main migration 번호를 복사하지 않고 full 다음 번호를 사용했다.
- main의 제거된 legacy frontend/finance port는 full active runtime에 복원하지 않았다.
- `TAVILY_API_KEY`를 canonical 이름으로 유지하며 별도 `TAVILY_KEY` 중복 authority를 만들지 않았다.

## 검증

- backend compile 및 targeted migration 소비 경로: passed
- production DB Flyway: 미실행
- Docker/Compose/MinIO/provider: 미실행

## 남은 위험 및 계속 지점

- 빈 PostgreSQL과 기존 데이터가 있는 PostgreSQL 양쪽에서 V23 적용을 사용자가 확인해야 한다.
- PHASE 4에서 frontend 제품 경로와 상태 UX를 검증한다.
