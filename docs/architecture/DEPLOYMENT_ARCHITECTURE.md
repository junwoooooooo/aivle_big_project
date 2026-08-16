# Deployment Architecture

- Status: TARGET_CANONICAL
- Reviewed Against Current Baseline: 3aeff219d72e1be502ba4ad1cade7f7aca83d10e
- Document Phase: P1
- Introduced In Commit: 1549a8efa0aeb2ca400f4795c1c44b34868e4722
- Scope: Deployable boundaries, health, readiness and isolation
- Supersedes: Legacy local and Compose architecture documents
- Implementation Status: PARTIALLY_IMPLEMENTED_TARGET

## Currently implemented

- React Frontend, Spring Backend, FastAPI AI Server, PostgreSQL과 MinIO의 Compose 경계
- 서비스 healthcheck와 Spring–FastAPI Internal v1 호출 경계
- 빈 PostgreSQL용 Flyway Baseline V1

## Retained compatibility

- `/api/v1`, AnalysisJob 기반 실행과 보존 MVP Route는 현재 소비가 남아 있다.
- Legacy demo는 Backend/Frontend 직접 실행용이며 공식 Compose topology 증거가 아니다.

## Remaining target direction

- Production network policy와 secret manager
- 수평 scaling, full observability와 circuit breaker
- 배포 자동화와 unified durable execution

## Deployable boundaries

Frontend, Spring WAS, AI Server, RDB, Object Storage를 분리한다. Browser는 Spring public endpoint만 접근한다. AI Server는 internal network에서 Spring 요청만 받고 provider/MCP outbound만 허용한다. RDB/Storage network policy는 Spring workload만 허용하는 방향이다.

## Health and readiness

| Component | Liveness | Readiness direction |
|---|---|---|
| Frontend | static server process | Spring public endpoint reachability는 별도 관측 |
| Spring | process/JVM | RDB, Storage와 필수 내부 configuration |
| AI Server | process/event loop | selected provider/MCP configuration; dependency별 상태 분리 |
| RDB | server | connection/validation |
| Object Storage | service | bucket/access/integrity probe |

Admin은 AI Server, Storage, 법령 API 연결 상태를 구분해 표시해야 한다. 외부 provider 장애가 Spring core readiness 전체를 반드시 내리지는 않으며 Service Policy/TaskRun 실패로 격리하는 방향이다.

## Failure isolation

- AI Server 장애 중에도 auth, Project 조회와 저장된 report 조회를 가능한 범위에서 유지한다.
- 법령 API 장애는 LegalReviewRun에 격리한다.
- PersonaInterview 또는 Marketing run 하나의 실패는 다른 run을 손상시키지 않는다.
- Storage 장애는 file/export 작업을 차단하되 RDB에 성공으로 기록하지 않는다.
- retry storm을 막기 위한 backoff, concurrency와 circuit 정책은 구현 Phase에서 결정한다.

## Current versus Target

현재 Compose와 health 설정은 local baseline이며 production network policy, secret manager, scaling, observability 또는 배포 완료를 의미하지 않는다. Repository-local CI는 검증 workflow이며 deployment automation은 아니다.
