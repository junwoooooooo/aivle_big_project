# Local Docker 실행

## 1. 환경변수 준비

```powershell
Copy-Item .env.example .env
```

`.env`에는 `AI_PROVIDER`, `AI_API_KEY`, `AI_MODEL`, `AI_INTERNAL_SERVICE_TOKEN`, `JWT_SECRET`, `POSTGRES_PASSWORD`, `MINIO_ROOT_PASSWORD`를 설정한다. Market Research2를 실행하려면 `MARKET_RESEARCH_OPENAI_API_KEY` 또는 `OPENAI_API_KEY`도 별도로 필요하다. OpenAI 호환 Provider라면 필요에 따라 `AI_BASE_URL`을 설정하되, Research2용 `OPENAI_BASE_URL`은 Responses API와 `web_search`를 지원해야 한다. 실제 Secret은 저장소에 커밋하지 않는다.

값을 노출하지 않고 필수 설정과 Twin Bank 경로를 확인한다.

```powershell
python scripts/check_local_env.py --compose
```

```powershell
docker compose up --build
```

- 서비스: http://localhost:3000
- 회원가입: http://localhost:3000/auth/signup
- 로그인: http://localhost:3000/auth/login

## A. 현재 공식 Journey 확인

1. 사업 기획 — Idea, Concept
2. 사업 검증 — current concept의 exact Market/BM lineage 사용
3. 출시 준비 — Technology, Operations, Finance
4. 가상 인터뷰 — 시장 인터뷰 정성 탐색, 트윈 패널 정량 시뮬레이션
5. 마케팅 전략 — Marketing
6. 최종 보고서

Market fresh collection은 최대 20분 execution budget을 사용하며, 화면과 Work Center에 heartbeat가
계속 표시된다. 5분 전후에 중단되면 정상 timeout이 아니라 transport 설정 회귀이므로
`AI_SERVER_MARKET_RESEARCH_READ_TIMEOUT`과 Backend/AI 로그를 확인한다.

## B. 보존된 기존 MVP 실험 기능 확인

`/api/v1` 중심 Legacy stable-core 경로는 현재 6단계 공식 Journey와 별개의 개발·실험 호환 경로다. 공식 제품 완료 여부나 다음 단계 판단에는 current `/api/v3` Journey authority를 사용한다.

`.env.demo.example`과 `scripts/demo-start.ps1`은 Backend와 Frontend만 직접 실행하는 `/api/v1` 중심 Legacy stable-core 데모다. FastAPI, PostgreSQL, MinIO를 포함한 공식 전체 Journey 검증이 아니다.

## C. 실패 확인과 로그

```powershell
docker compose ps
docker compose logs -f backend ai-server
docker compose logs --tail 200 backend ai-server postgres minio
```

관리자 Role 계정은 `/admin`에서 사용자, 프로젝트, 최근 TaskRun과 서비스 설정 상태를 확인할 수 있다. 화면과 로그에 Provider API Key나 내부 Token 원문을 남기지 않는다.

## 4. 로컬 데이터 초기화

주의: 다음 명령은 PostgreSQL과 MinIO의 Docker Volume 및 모든 로컬 데이터를 삭제한다. Baseline V1은 기존 V1~V36 DB의 in-place upgrade를 지원하지 않으므로 이전 Volume을 재사용하지 않는다.

```powershell
docker compose down -v
docker compose up --build
```
