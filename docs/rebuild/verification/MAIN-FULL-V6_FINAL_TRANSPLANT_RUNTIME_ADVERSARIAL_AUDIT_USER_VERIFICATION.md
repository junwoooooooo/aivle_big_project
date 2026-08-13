# MAIN-FULL-V6 사용자 실검증 절차

이 절차는 Codex 셸에서 Docker CLI를 사용할 수 없어 남은 live gate를 사용자가 검증하기 위한 것이다. 실제 비밀값을 로그나 이 문서에 붙여 넣지 않는다. 유료 Market 실행은 무료 gate가 모두 통과한 뒤 정확히 한 번만 수행한다.

## 1. 로컬 설정 존재 여부

저장소 루트에서 실행한다.

```powershell
python scripts/check_local_env.py --compose
```

출력은 값이 아니라 `SET`, `MISSING`, `EXISTS`만 보여야 한다. 특히 `AI_API_KEY`가 있어도 `MARKET_RESEARCH_OPENAI_API_KEY` 또는 `OPENAI_API_KEY`가 없으면 `MARKET_KEY MISSING`이어야 한다.

## 2. Compose 정적 계약

```powershell
docker compose config --quiet
docker compose --env-file .env.e2e.example -f compose.yaml -f compose.e2e.yaml config --quiet
```

확인 항목:

- unresolved/empty required variable 없음
- backend의 AI URL이 Compose service 이름을 가리킴
- internal token의 backend/AI 이름이 일치함
- PostgreSQL/MinIO credential 쌍이 일치함
- E2E Twin Bank는 synthetic fixture를 사용하고 실제 Twin Bank를 포함하지 않음
- `AI_SERVER_READ_TIMEOUT=30s`
- `AI_SERVER_LONG_READ_TIMEOUT=7m`
- `AI_SERVER_MARKET_RESEARCH_READ_TIMEOUT=22m`

## 3. Build와 기동

```powershell
docker compose build
docker compose up -d
docker compose ps
```

health 확인:

```powershell
docker compose exec ai-server python -c "import urllib.request; print(urllib.request.urlopen('http://127.0.0.1:8000/health').status)"
docker compose exec ai-server python -c "import urllib.request; print(urllib.request.urlopen('http://127.0.0.1:8000/health/live').status)"
docker compose exec ai-server python -c "import urllib.request; print(urllib.request.urlopen('http://127.0.0.1:8000/health/ready').status)"
docker compose exec backend curl --fail --silent http://127.0.0.1:8080/actuator/health
Invoke-WebRequest http://localhost:3000/healthz
```

기본 Compose는 frontend만 host에 publish한다. 마지막 명령의 host port가 다르면
`docker compose ps`에 표시된 `${FRONTEND_PORT}`를 사용한다.

## 4. PostgreSQL/Flyway

새 빈 DB 또는 사용자 데이터가 없는 검증용 DB에서만 수행한다. 기존 사용자 DB volume을 삭제하지 않는다.

확인할 로그/상태:

```powershell
docker compose logs backend --since 10m
```

- Flyway V1부터 현재 최신 V24까지 성공
- duplicate/missing migration 없음
- Hibernate `ddl-auto=validate` 성공
- `market_research_ledger_artifacts` 및 TaskRun/Attempt/JobEvent/current-history 테이블 검증
- V23 competitor seed FK/CHECK/UNIQUE/index 검증

## 5. 무료 transport/runtime gate

```powershell
cd backend
.\gradlew.bat test --tests "com.aivle.backend.integration.ai.AiServerClientConfigurationTests" --tests "com.aivle.backend.taskrun.integration.InternalAiExecutionClientRoutingTests" --tests "com.aivle.backend.pipeline.market.MarketResearchRuntimeContractTests" --tests "com.aivle.backend.integration.ai.AiServerTransportContractTests" --no-daemon
cd ..
```

예상: 4 classes, 9 tests, 실패 0.

## 6. 실제 Market 1회 검증

무료 gate가 통과하고 비용 사용을 승인한 뒤 새 CPV2 사업안으로 정확히 한 번 실행한다. 다음 값을 기록한다.

| 필드 | 기록값 |
|---|---|
| taskRunId | |
| attemptId | |
| 시작 UTC | |
| 종료 UTC | |
| elapsed | |
| progress event sequence | |
| Backend timeout/error code | |
| AI request completion | |
| Market state | |
| result version | |
| evidence count | |
| artifact committed | |
| current version | |
| raw ledger artifact identity | |

관찰 명령 예:

```powershell
docker compose logs -f backend ai
```

통과 기준:

- 300~301초 지점에서 transport timeout으로 끊기지 않음
- 진행 중 Market 화면 또는 Work Center에 heartbeat가 갱신됨
- 성공 시 canonical current Market version이 생성됨
- raw ledger artifact가 Object Storage와 DB metadata에 함께 결속됨
- timeout이면 사용자 오류가 `TIMEOUT/DEADLINE` 의미로 표시되고 generic `EXECUTION_FAILED`로 덮이지 않음

## 7. Recollect 실검증

Market A 성공 후 로컬 AI temporary workspace에 의존하지 않는 상태에서 Market B recollect를 실행한다.

통과 기준:

```text
TaskRun A SUCCESS
→ Object Storage ledger committed
→ temporary workspace 제거/프로세스 재시작
→ TaskRun B가 같은 sourceRun artifact 복원
→ checksum/ownership/concept/revision 검증
→ exact Research2 recollect
→ 새 canonical result
```

다음은 실패해야 한다.

- object missing/corrupt
- manifest/file checksum mismatch
- wrong project/concept/revision
- stale concept
- unauthorized user
- incomplete upload

silent fresh FULL fallback이 발생하면 실패다.

## 8. 검증 결과 보존

비밀값을 제거한 아래 자료만 보존한다.

- `docker compose ps`
- 각 health status code
- backend/AI 관련 구간 로그
- 위 Market 실행 기록표
- Flyway와 `ddl-auto=validate` 성공 구간
- ledger artifact의 ID/path/checksum metadata(내용과 secret 제외)

실패 시 container를 무작정 재시작하기 전에 taskRunId, attemptId, UTC timestamp, 마지막 JobEvent와 error code를 먼저 기록한다.
