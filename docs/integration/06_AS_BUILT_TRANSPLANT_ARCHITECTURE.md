# Session 5 As-Built Transplant Architecture

기준 HEAD는 Session 4 완료 커밋 `329c1e9`이며, 이 문서는 Session 1~4 이식 결과를 현재 Target Production Platform 관점에서 고정한다. donor는 분석 기능의 출처일 뿐 runtime authority가 아니다.

## 1. 최종 플랫폼 경계

```text
Project Shell
  → Backend Product API (인증/프로젝트 ownership)
  → TaskRun + TaskAttempt (실행/재시도/history authority)
  → Module Worker
  → POST /internal/v1/ai/executions (service Bearer token)
  → 분석 결과 검증 및 materialization
  → JobEvent
  → Job SSE + Project SSE
  → Frontend invalidation
  → canonical REST GET
  → Work Center 및 모듈 화면
```

- SSE payload는 최종 결과가 아니라 invalidation/progress 신호다.
- Market, BM, Twin, Finance, Marketing canonical GET은 읽기 전용이다. 초기화·실행·확정은 명시적 command endpoint다.
- 장시간 작업의 상태는 TaskRun/TaskAttempt/JobEvent가, 현재 제품 결과는 각 module current/version/snapshot이 정본이다.
- 재시도는 기존 TaskRun을 되살리지 않고 새 TaskRun을 생성한다. 실패 이력은 유지된다.

## 2. 공식 모듈 및 route

| 순서 | 모듈 | 공식 route | current authority |
|---:|---|---|---|
| 1 | Idea | `/app/projects/:projectId/idea` | current IdeaBrief/confirmed snapshot |
| 2 | Business Proposal / CPV2 | `/app/projects/:projectId/concepts` | current CPV2 run/selection/Market Seed |
| 3 | Market Research | `/app/projects/:projectId/market` | selected CPV2 lineage의 FULL run/version |
| 4 | Business Model | `/app/projects/:projectId/business-model` | current Market version + BM plan/run/version |
| 5 | Twin Survey | `/app/projects/:projectId/twin-survey` | survey run/version 및 current Market Seed lineage |
| 6 | TechOps | `/app/projects/:projectId/tech-ops` | Target TechOps preparation/snapshot |
| 7 | Finance | `/app/projects/:projectId/finance` | TechOps/Market/BM 결속 preparation/snapshot/analysis |
| 8 | Marketing | `/app/projects/:projectId/marketing` | Marketing Source/Content/Revision + Visual Task/Artifact |

ProjectLayout이 left journey, 이전/현재/다음, 잠금 이유, 현재 모듈 표시, mobile navigation 및 Project Helper를 공통 제공한다. Persona, Persona Interview, Persona Marketing, VirtualMarket, Finance sandbox, sample Market은 공식 route/navigation에 없다.

## 3. service/data authority

| 경계 | authority | 비고 |
|---|---|---|
| 인증/소유권 | Backend user + project ownership | Browser→AI 직접 호출 없음 |
| 실행 | TaskRun/TaskAttempt | idempotency, lease, retry, terminal state |
| 이벤트 | JobEvent | 사람말 message key와 안전한 실패 정보 |
| live refresh | Job/Project SSE | REST polling fallback 제거; cursor 기반 SSE 재연결 |
| 분석 결과 | module version/snapshot/current REST | GET에서 materialize/transition 금지 |
| 파일 | Project-owned Artifact + MinIO | 내부 object URL은 Browser에 직접 노출하지 않음 |
| Marketing Visual | TaskResult metadata + Project Artifact | AI local output은 authority가 아님 |

## 4. 최종 lineage

| 연결 | immutable binding |
|---|---|
| CPV2 Selection → Market | portfolio selection id, selection revision, Market Seed id/hash |
| Market → BM | source MarketResearchVersion id, BM plan revision |
| Market/BM/TechOps → Finance | source TechOps snapshot id, Market version id, BM version id, source hash |
| Marketing → Visual | Marketing Source id, Content id, Revision id, source image Artifact id, legal controls |

Source가 바뀌면 과거 결과를 덮어쓰지 않고 STALE/history로 남긴다. Twin의 stimulus draft는 survey 완료 authority가 아니며, Visual 실패는 완료된 Marketing Content 자체를 실패로 되돌리지 않는다.

## 5. donor → Target 결과

| donor | 보존 기능 | Target seam |
|---|---|---|
| donor-market | Market collection/ledger/evidence/scorecard, BM join/BMC, Twin gate/sampling/result | Product API, TaskRun workers, version materialization, SSE, Project Shell |
| donor-mini | Finance preparation/추정/계산/Monte Carlo/report, TechOps 동등분 | Target lineage, worker, current snapshot, Work Center; TechOps Target 유지 |
| donor-aidev | banner copy/prompt/image/Pillow/font/visual UI | Marketing Source/Revision 입력, Internal AI, Project Artifact/MinIO |
| donor-main / integration-local | 비교·계약 기준 | donor runtime/polling/direct API는 정본으로 채택하지 않음 |

CPV2 Core, Market/BM/Twin 알고리즘, Finance 계산식 및 Target TechOps는 Session 5에서 변경하지 않았다. 기존 migration도 수정하지 않았고 신규 migration은 없다.

## 7. Session 5 검증 기록

- Backend cross-module/security/event/ownership targeted test: PASS.
- PostgreSQL Testcontainers: clean V1~V19(gap V17) apply, Flyway validate, JPA validate, V13→current upgrade 및 대표 lineage FK/active unique 검사 PASS.
- AI fixture/mock targeted: 111 PASS, `compileall` PASS.
- Frontend Project Shell/Module Status/Work Center/SSE/Market/BM/Twin/TechOps/Finance/Marketing targeted: 231 PASS.
- Frontend production build: PASS. 기존 500 kB chunk warning만 존재한다.
- `git diff --check`: PASS.
- Compose YAML의 service/dependency/Twin read-only mount/env 구조는 정적 검증 PASS. 현재 Codex 호스트에는 `docker` CLI가 없어 `docker compose config --quiet` 실행은 미검증이며 `09_DOCKER_FINAL_VERIFICATION_RUNBOOK.md`의 첫 runtime gate로 이관한다.

## 6. legacy/dev 경계

- `frontEnd/src/page/VirtualMarket.jsx`: legacy code, 공식 route 없음.
- Finance demo/sandbox API와 UI: DEV_ONLY/NOT_OFFICIAL.
- Market fixture/local research tools: test/dev 전용; FULL Product 실행의 current authority가 아님.
- donor FastAPI banner public endpoint: 공식 Product 경로가 아니며 Browser에 노출하지 않음.
- Twin Bank 두 파일은 저장소 밖 `TWIN_BANK_HOST_DIR`에서 `/app/app/twin/bank`로 read-only mount하며 Git/Docker image에서 제외한다. 세부 계약은 `10_TWIN_BANK_ASSET_CONTRACT.md`를 따른다.

## 7. CUTOVER-R1 관측 경계

Market FULL과 Twin Survey는 AI 내부 실제 단계에서 안전 progress event를 bounded queue로 Backend `/internal/v1/ai/task-progress`에 보낸다. 이 경계는 결과 정본이 아니며 callback timeout·거부·queue 포화가 분석 결과를 실패시키지 않는다. Backend allowlist는 `CONCEPT_PORTFOLIO_V2_RUN`, `MARKET_RESEARCH`, `TWIN_SURVEY`뿐이다.

Market은 별도 JSONL side-channel로 A1~A4와 B/C 및 직렬화 경계를 관측하고, Twin은 gate·bank·sampling·wave·aggregate 경계를 관측한다. query, URL, evidence 본문, Twin card, `pid_hash`, prompt/provider body는 progress payload에 넣지 않는다. canonical 결과 authority는 기존 materialization과 REST 조회다.
