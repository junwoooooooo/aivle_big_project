# Full Transplant 감사 README

- 감사일: 2026-08-11 (Asia/Seoul)
- 대상 브랜치: `integration/full-transplant-v1`
- Target HEAD: `7d9546e6c6f38c52484ed104de064fca6169eca5` (`RUNTIME RECOVERY + WORK CENTER/NAVIGATION FIX`)
- 성격: Session 1 감사 전용. 제품 코드 변경 없음.

## 1. 감사 범위와 원칙

실제 파일, `git log`, `git merge-base`, `git diff`, 계약 검증 코드, 테스트, 화면 렌더링 코드를 읽어 `target/`과 다섯 donor를 비교했다. donor worktree는 수정하지 않았다. Persona, Persona Interview, Persona Marketing은 감사와 이식 후보에서 제외했다. Twin 화면의 제품 용어인 “가상 페르소나 수”는 Twin 표본 크기를 뜻하며 제외 대상 Persona 모듈이 아니다.

Target의 CPV2, Project Shell, ProjectModuleStatus, TaskRun/TaskAttempt, JobEvent, Project/Job SSE, Work Center, retry/stale/history/idempotency/current-state, ownership, MinIO/Artifact 기반은 정본으로 유지한다. donor의 분석 알고리즘·계약·결과 정보는 보존하되 polling, GET synchronize, 로컬 파일 결과, 직접 장시간 호출은 integration seam으로 취급한다.

## 2. 감사한 저장소 상태

| 작업 트리 | HEAD | 시각 | 상태/관계 |
|---|---|---|---|
| `target` | `7d9546e` | 2026-08-11 09:59 +09:00 | `integration/full-transplant-v1`, 감사 시작 시 clean |
| `donor-main` | `06d1947` | 2026-08-10 20:10 +09:00 | market-research-v2 PR #35 병합점 |
| `donor-market` | `f3d6dbd` | 2026-08-11 13:09 +09:00 | `donor-main`보다 12커밋 앞, 뒤처진 커밋 0 |
| `donor-mini` | `c7f7946` | 2026-08-10 22:22 +09:00 | `donor-main`보다 21커밋 앞, Finance 확장 포함 |
| `donor-aidev` | `e6fdeb1` | 2026-08-10 21:34 +09:00 | 공통 merge-base `e8386a2` 뒤 AIdev 고유 6커밋 |
| `donor-integration-local` | `9acbc1c` | 2026-08-11 10:11 +09:00 | CPV2 제품 이식 계보; 해당 migration은 Target과 동일 |

`donor-main`과 `donor-market`의 merge-base는 정확히 `06d1947`이고 `rev-list --left-right --count` 결과는 `0 12`다. 따라서 Market/BM/Twin의 최신 donor는 추측 없이 `donor-market@f3d6dbd`로 판정한다.

## 3. 기능별 source of truth

| 기능 | 정본 donor | 보조 donor | 판정 근거 |
|---|---|---|---|
| 시장조사 AI/계약/UI | `donor-market@f3d6dbd` | `donor-main@06d1947` | main 병합 후 pipeline/serialize/verdict/scorecard/UI와 BM 계획이 추가 수정됨 |
| BM 분석/BMC/financial handoff | `donor-market@f3d6dbd` | 없음 | `b6780d1`에서 실행계획 4칸, 제약, AssumptionLedger, BMC UI가 추가 발전 |
| Twin Survey | `donor-market@f3d6dbd` | `donor-main@06d1947` | `58ffedb`, `1166b88`, `70a043f`에서 인터뷰·프로파일·초안·결과 UI가 추가됨 |
| Finance 확장 | `donor-mini@c7f7946` | Target의 기존 pipeline Finance | deterministic/Monte Carlo/AI report/Tavily/분석 UI/샌드박스가 mini 고유 |
| TechOps | `target@7d9546e` | `donor-mini@c7f7946` | 관련 30개 제품/테스트/프론트 파일의 SHA-256이 전부 동일; mini 전용 파일 0 |
| Marketing Content 기본 기능 | `target@7d9546e` | `donor-aidev@e6fdeb1` | AI task, Backend pipeline, Frontend feature 파일이 전부 동일 |
| AIdev Visual banner | `donor-aidev@e6fdeb1` | 없음 | banner copy/image/edit/composition/font/API/client가 Target에 없음 |
| CPV2 제품 기반 | Target | `donor-integration-local` | Target V10~V13과 integration-local V10~V13이 파일명·해시까지 동일 |

## 4. 가장 중요한 감사 결론

1. Market Product 실행은 완전한 A1~A3 수집 실행이 아니다. `research2/run.py`에는 A1 수식·슬롯 설계, A2 어댑터 라우팅, A3 KOSIS/DART/Web 수집이 구현돼 있지만 `research/pipeline.py::run_market_research()`의 FULL 경로는 `harness`, `dryrun`, `collect`를 `SKIPPED/NOT_WIRED`로 기록하고 저장된 `research2/runs/<sourceRun>` 원장을 읽는다.
2. 현재 Product 경로는 3개 샘플 라벨(`beauty-noshow`, `household-ledger`, `pet-treat`)과 저장 원장에 의존한다. 임의 Concept는 명시적인 `sourceRun`과 `conceptPath`가 모두 존재할 때 AI 함수 수준에서만 가능하고, Backend `MarketResearchInputFactory`는 이를 제공하지 않는다. 따라서 arbitrary selected Concept는 현재 end-to-end 미지원이다.
3. BM은 시장 결과를 축약하지 않는다. MarketJoin 계약, evidence id 검증, caveat 전파, BMC 9칸, market fit/consistency, 강점·약점·위험, legal 요약, financial handoff를 보존해야 한다.
4. Twin 알고리즘과 gate는 수정 대상이 아니다. Git 밖 Twin Bank, 50/100/300 표본, 층화표집, 양방향 자극, 적응 반복, Δ/CI/MDE, 응답자 분류, 대표 인터뷰, caveat와 “못 잼” 의미를 그대로 보존한다.
5. mini Finance에는 공식 project Finance 확장과 별도 sandbox/demo/중복 API가 함께 있다. 이번 감사에서는 삭제 결정을 하지 않고 모두 분류했다.
6. AIdev의 Marketing Content 기본 기능은 이미 Target과 동일하다. 고유 기능은 별도 FastAPI banner API, copy 생성, `gpt-image-2` 편집, Pillow 텍스트 합성, Noto Sans KR 폰트, 로컬 output, orphan Backend client, legacy 데모 UI다.
7. Target migration 최대 번호는 V13이다. donor의 Market/Twin/BM/Finance migration은 그대로 복사할 수 없고 V14 이후로 새 파일이 필요하다. CPV2 donor migration은 이미 Target에 존재하므로 재적용하지 않는다.

## 5. 이식 시 지켜야 할 경계

- 분석 결과 봉투와 user-visible 정보는 `02_DONOR_UI_INFORMATION_INVENTORY.md`의 모든 `YES` 행을 보존한다.
- 필요한 TaskType/API/Async 변환은 `03_TASKTYPE_API_ASYNC_MAP.md`를 따른다.
- 기존 V1~V13은 수정하지 않고 `04_MIGRATION_RENUMBER_PLAN.md`의 후속 번호만 사용한다.
- 저장 원장과 AIdev 로컬 output을 Target의 current-state/MinIO/Artifact 정본으로 바꾸되, 데이터 내용과 실패 의미는 바꾸지 않는다.
- donor의 polling/GET synchronize를 그대로 이식하지 않는다. Worker가 materialize하고 JobEvent를 내보내며 Project/Job SSE 후 canonical REST를 새로 읽는다.
- Provider LIVE, MOLEG LIVE, 실 Twin Bank 대규모 실행, 전체 Browser E2E, 실 사용자 Docker 전체 검증은 이번 Session에서 수행하지 않는다.

## 6. 문서 안내

- `01_BRANCH_TRANSPLANT_MATRIX.md`: 브랜치/기능/파일 단위 정본과 전체 inventory
- `02_DONOR_UI_INFORMATION_INVENTORY.md`: Market/BM/Twin/Finance/AIdev 사용자 정보 보존 행렬
- `03_TASKTYPE_API_ASYNC_MAP.md`: TaskType, API, polling/direct execution 교체 seam
- `04_MIGRATION_RENUMBER_PLAN.md`: 실제 migration 충돌과 V14+ 계획
- `05_RUNTIME_DEPENDENCY_AND_ENV_MAP.md`: 외부 의존성, 환경변수, mount, Artifact 계약
