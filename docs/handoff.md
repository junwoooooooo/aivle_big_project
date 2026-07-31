# 피드백 루프 핸드오프 — 백엔드 완성, 파이프라인·프론트 이월

작성: 2026-07-29. 이 문서는 "검토→수정→재검토→발행" 피드백 루프 구현의 세션 간 인수인계다.
원 지시서(§5 E2E 시나리오)와 승인된 계획은 `C:\Users\User\.claude\plans\harmonic-wobbling-sky.md` 참조.

## 0. 2026-07-30 세션 갱신 (M4·M7 완료, M8 대부분 완료)

07-30 오전 세션이 M4(ai/legal 파이프라인)·M7(프론트+백엔드 지원 엔드포인트)을 전부 구현한 뒤
검증 단계에서 블루스크린(NVMe 절전 복귀 실패, Docker/WSL 무관)으로 끊겼고, 이후 세션이 검증을 완료했다.

- **M4 완료**: `ai/legal/test_feedback.py` 13/13 통과. mode/rerunCategories/confirmedFacts,
  filter_routes_for_categories, stage_revise/validate_revisions, related_route_ids 전부 구현 확인.
- **M7 완료**: 지원 엔드포인트 4개(`/review-cycles/active`, `/plan-versions`, plan by-id,
  `/publications/latest`) + 프론트 hooks/컴포넌트/테스트. 체크리스트 키 `cycle:{cycleId}` 전환(레거시 폴백 포함).
- **M8 — provider=mock 실스택 스모크 S1~S5 전부 통과** (API 워크스루, `scratchpad/smoke.py`):
  FULL 검토→수정요청·질문·NEEDS_ACTION → 수정안 승인 v2 → INCREMENTAL(rerun=[광고], carried 9,
  diff {1,0,5}, finding 10개 보증) → 답변 v3+확정정보 → CONVERGED → 발행(PUBLISHED, FEASIBILITY 전이)
  → 발행 후 편집 v4 + 새 DRAFT 사이클 + 발행물 보존.
- **UI 스크린샷 워크스루도 완료** (같은 날 오후): 가입→업로드→구조화 확정→S1(수정요청 카드·질문·할일 5)
  →S2(수정안 A 적용→v2 배너→INCREMENTAL, diff "해결 1·신규 0·유지 5"+승계 문구, RESOLVED 회색 보존)
  →S3(답변 저장→v3→재검토→추가확인배지 소멸)→S4(발행 스냅샷: 버전/해결 이력+이행 예정 5건, Overview가
  타당성 단계로 전이)까지 UI에서 재현 확인.
  **유일한 잔여: S5(발행 후 편집)의 프론트 진입 UI가 없음** — `legalReviewApi.js`에 editPlan 함수 자체가
  미구현(계획 §5에는 있었음). 백엔드 API로는 검증 완료. M7 이월 항목.
- **V14 신규 마이그레이션** `V14__drop_orphaned_plan_source_unique_index.sql`:
  기존 H2 파일 DB에서 V13의 `DROP CONSTRAINT uk_plan_source_document_version`이 실행돼도
  fk_plan_source가 백킹 UNIQUE 인덱스를 소유해 인덱스가 살아남음 → v2 파생 시 23505.
  FK 재바인딩 후 고아 인덱스 제거. 신규 DB/PG에선 no-op. (신규 스키마 테스트는 이 문제가 없어 green이었음 —
  기존 DB에서만 재현되는 마이그레이션 결함이었다.)
- 프론트 `LegalReviewPage.test.jsx` "pivots..." 테스트는 풀스위트 병렬 부하에서 5초 타임아웃으로만
  실패(단독 통과)해 타임아웃 15초로 상향. auth/landing/ProjectPages 실패는 기존 기준선(새 작업과 무관 확인).
- 참고: `/review-cycles/{id}/publish`에 잘못된 cycleId 타입이 오면 400이 아닌 500
  (MethodArgumentTypeMismatch 핸들러 공백, 기존 이슈, 미수정).

## 1. 이번 세션 완료분 (백엔드 루프 전체)

**§5 시나리오 1~5가 백엔드 통합 테스트로 전부 통과한다.**
테스트: `backend/src/test/java/com/aivle/backend/analysis/legal/feedback/FeedbackLoopScenarioTest.java`
실행: `cd backend; .\gradlew.bat test --tests "*FeedbackLoopScenarioTest"` (provider=mock, 결정론)

구현된 것:

| 구성요소 | 파일 |
|---|---|
| V13 마이그레이션 | `db/migration/V13__add_feedback_loop.sql` — review_cycles, confirmed_facts, revision_requests, revision_suggestions, publications + 기존 테이블 컬럼 확장. **uk_plan_source_document_version 삭제됨** |
| plan 버전 계보 | `StructuredPlan.deriveFrom()` (parent/origin, CONFIRMED 복사본), `StructuredPlanSection.copyOf()`, versionNumber는 프로젝트별 max+1 |
| 버전 생성 3경로 | `feedback/PlanVersionService` — acceptSuggestion(문장 교체)/answerQuestion(본문 무변경+ConfirmedFact)/userEdit(사이클 없으면 DRAFT 새 사이클) |
| 상태 머신 | `feedback/ReviewCycle` (DRAFT→REVIEWING→NEEDS_ACTION→CONVERGED→PUBLISHED), `ReviewCycleService.recomputeState` (OPEN 수정요청 0 && OPEN 질문 0 → CONVERGED. **할일 미포함**) |
| 증분 재검토 | `feedback/IncrementalReviewPlanner` (sourceSectionCodes 역인덱스 + 섹션 diff + 확정정보 범주), `LegalReviewJobContextService`가 실행 시점 산출, `LegalReviewPersistenceService`가 승계 범주 finding 복사 |
| diff 계약 | `feedback/ReviewDiffService` — 수정요청 키=(category, anchorSectionCode), RESOLVED는 `resolvedInVersion` 기록(삭제 금지), ACCEPTED 재방출→NEW 행. diff 배너 = 수정요청 ∪ 할일 |
| 질문 라이프사이클 | 승계 범주 OPEN 질문 carriedFrom 복사, 재실행 범주 텍스트 매칭(미방출→resolved), ANSWERED 동일 텍스트 억제 |
| 발행 | `feedback/PublicationService` — CONVERGED 가드, snapshot_json(버전 이력·해결 이력·이행 예정), **enterFeasibility()가 발행 시점으로 이동** (기존 첫 검토 완료 시점에서 제거됨) |
| Mock | `MockLegalReviewAiClient` — "악취 30%" → 광고 수정요청(A/B), "활성탄" 확정정보 부재 → 질문, 할일 5개, INCREMENTAL 시 rerun 범주만 생성, `invocations()` 노출 |
| 계약 레코드 | `LegalReviewAiRequest` +mode/rerunCategories/confirmedFacts, `LegalReviewAiResponse` +revisionRequests/질문 categories (하위호환 생성자 유지) |

신규 API (모두 `/api/v1/projects/{projectId}` 하위, ApiResponse 래핑):
- `POST /revision-requests/{id}/accept` `{suggestionId}` → `{newPlanId,newVersionNumber,origin}`
- `POST /revision-requests/{id}/dismiss`
- `POST /legal-questions/{id}/answer` `{answer,factKey,source}` → `+confirmedFactId`
- `POST /review-cycles/{id}/publish` `{completedActions[]}`
- `POST /structured-plans/{id}/edit` `{sections:[{code,sourceText}]}`
- `POST /legal-reviews` body에 optional `{"mode":"FULL"|"INCREMENTAL"}` (기본: 첫 검토 FULL, 이후 INCREMENTAL)
- `GET /legal-reviews/latest` 응답에 `mode/rerunCategories/carriedCategories/diff{resolved,added,maintained}`, finding에 `carried` 추가 (기존 필드 무변경)

## 2. 커밋 상태 (중요)

- `14561b3` — V13+엔티티 기반(M1)은 커밋됨 (푸시 안 함).
- **그 이후 작업(서비스·컨트롤러·테스트·Mock)은 전부 미커밋** — 사용자 지시("커밋하지마")로 중단.
  커밋 시 스테이징 주의: 작업 트리에는 **세션 이전 미커밋분**(ai/, frontEnd/, scripts/,
  .env.demo.example)이 섞여 있고, `법률/`·`model/`·`문서/`는 개인 원자료 포함으로 **커밋 금지**.
- `CLAUDE.md`는 사용자 전역 gitignore(`~/.config/git/ignore`) 대상이라 커밋되지 않는다.

## 0-2. 2026-07-30 오후: 종합 판정 + 근거 설명 (완료)

법률 결과 화면의 "법 범주별 근거"(요약 테이블 + 평면 카드 10개)를 **종합 판정 카드 1개**로
접고, 각 근거 조문에 **쉬운 설명**과 **5단 논리 사슬**을 붙였다. 재료는 전부 파이프라인에
있었으나 버려지던 것이다(저장된 실 실행 `ai/legal/출력/작업/`으로 확인).

| 계층 | 변경 |
|---|---|
| Python | `SCREENING_INSTRUCTIONS`에 `plain_summary`(조문이 요구하는 것을 일상어로) 추가 — **추가 LLM 호출 없음**. `aggregator.build`가 evidence를 구조화 객체 배열로, `reasoning` 5단 사슬 신설. `finding`의 `" ".join(notes)` 런온 제거, `rationale`에서 **route_id·MST 노출 제거**(`law_registry.json`의 `topic`으로 치환), 표 셀 인용은 구분자만 ` · `로 정제 |
| 백엔드 | `LegalReviewAiResponse`에 `Evidence`/`EvidenceRole`/`Reasoning` 레코드. Evidence는 `@JsonCreator(DELEGATING)`으로 **문자열 근거도 수용**(구 리뷰·비정형 LLM 응답). `V15__add_finding_reasoning.sql`(reasoning_json), 구조화 evidence는 기존 evidence_json 재사용 |
| Mock | 범주 10개가 **각각 다른** 실재 조문·설명·사슬을 냄 (이전엔 9개가 동일 보일러플레이트라 화면이 전부 똑같았다) |
| 프론트 | `components/OverallVerdictCard.jsx` 신설(위험도 그룹 → `<details>` 범주 행 → 사슬 + 조문 카드). `buildOverallVerdict`·`parseReasoning`·`evidenceList` 추가, `parseEvidence`는 문자열/객체 양쪽 수용 |

**불변식(신규):** Mock은 **조문 원문 발췌를 만들지 않는다.** 축자 검증되지 않은 법령 문구가
Mock 화면 캡처만으로 실제 조문처럼 유통되면 안 된다. Mock 근거는 법령명·조문번호·쉬운 설명·
법제처 링크까지만 싣고 `excerpt`는 null이며, `MockLegalReviewAiClientTests`가 이를 강제한다.
실 파이프라인은 법제처 API 원문 발췌(350자, `filter_articles(excerpt_len=350)`)를 그대로 싣되
화면 라벨이 "조문 발췌"임을 명시한다 — "원문"이라 쓰면 거짓이 된다.

검증: Python 20/20 · 백엔드 시나리오 S1~S5 green · 프론트 legal-review 28/28 · lint 클린 ·
**저장된 실 실행(조문 233건) 재집계로 route_id·MST 미노출 확인**(LLM 재호출 없음).
`plain_summary`는 이 저장 데이터에 없어 결측 경로만 검증됨 — 실제 생성은 다음 전체 실행에서 확인할 것.

## 0-3. 2026-07-30 저녁: 타당성 3묶음 재구성 (완료)

`문서/image.png` 구조도(규제 검토 → 시장·BM·기술운영 → 재무 분석)에 맞춰 타당성 화면을
**평면 카드 10장 → 묶음 카드 3장**으로 재구성했다. 상세는 CLAUDE.md §6-2.

- 백엔드: `AnalysisType`(사장돼 있던 enum) 재활용, 카탈로그에 `group` 추가·`VERSION` v2,
  `FeasibilityScorePolicy.evaluateGroups`(44/44/12 정규화), `FeasibilityGroupResult` + **V16**,
  AI 응답에 `groups[]`(서술만, 점수 없음), Mock 묶음별 문구 차별화.
- 프론트: `components/AnalysisGroupCard.jsx` 신설(묶음 결론 → 강점/위험 → 먼저 할 일 →
  소속 차원 `<details>`), `groupDimensions` 순수 함수, 비활성 "재무 분석 실행 (준비 중)" 버튼.
- **차원 10개는 하나도 빼지 않았다** — 페르소나의 문자열 하드코딩 필터를 지키기 위한 의도적 선택.
- 검증: 백엔드 전체 **기준선 4건 정확히 일치**(신규 실패 0), 프론트 feasibility 13/13, lint 클린,
  실스택 UI 스모크로 묶음 3장(66/68/70·각기 다른 결론) 확인.

**Node 플래그 부채 해소**: `vite.config.js`의 `test.execArgv`에 `--no-experimental-webstorage`를
넣어 `NODE_OPTIONS` 없이 통과한다. 함정 — Vitest 4에서 `execArgv`는 **`test` 최상위 옵션**이며
v3식 `poolOptions.forks.execArgv`는 조용히 무시된다.

## 3. 남은 마일스톤 (다음 세션) — ※ §0 갱신 참조: M4·M7·M8(API 스모크)은 07-30 완료. 잔여 = UI 스크린샷 워크스루뿐.

1. **M4 — ai/legal 파이프라인 확장** (Java 어댑터 계약은 완료, Python만 남음):
   - `service.py` pydantic에 `mode/rerunCategories/confirmedFacts` 수용, 응답에 `revisionRequests`
   - `build_source_text(sections, confirmed_facts)` — 확정 정보를 **source_text에 직접 append**
     (`validate_routing`이 인용을 원문 대조로 화이트리스트하므로 별도 블록은 안 됨) + 로그 "확정 정보 n건 주입"
   - `filter_routes_for_categories` — category_map 교집합으로 라우트 필터, `build_plan` 전 적용 + 제외 로그
   - 수정안 스테이지 `stage_revise` + `validate_revisions` (quote가 정확히 한 섹션의 부분문자열)
   - 질문에 `related_route_ids` → categories 부여 (`ROUTING_INSTRUCTIONS` 확장, legacy 평문 수용)
   - `ai/legal/test_feedback.py` (LLM 불필요 단위 테스트)
2. **M7 — 프론트엔드**: 계획서 §5 참조. 추가로 백엔드에 프론트 지원 엔드포인트 3개가 필요하다
   (이번 세션 범위에서 제외됨): `GET /review-cycles/active`, `GET /plan-versions`,
   `GET /structured-plans/{planId}` (by-id — **`usePlanSnapshot`의 latest 가드는 v2가 생기는 순간
   정식 보고서를 침묵 실패시키므로 by-id 전환 필수**). 체크리스트 localStorage 키를
   `cycle:{cycleId}`로 전환.
3. **M8 — §7 수용 기준 전체 점검 + provider=mock UI 스모크 + 스크린샷 보고.**

## 4. 주의사항 (이번 세션에서 밟은 것 포함)

- **Hibernate 프록시 필드 접근 함정**: `deriveFrom`/`copyOf`처럼 다른 엔티티 인스턴스의 필드를
  직접 읽는 정적 팩토리는 반드시 **게터**로 접근할 것. `cycle.getCurrentPlan()`은 지연 프록시라
  필드 직접 접근 시 전부 null이었다 (project_id NOT NULL 위반으로 발현).
- **사전 실패 4건이 기준선**: 체크섬 3건(V1 해시 불일치) + `Phase3MigrationTests.freshH2Schema...`
  (최신 버전 "10" 고정 — V11부터 항상 실패). 고치지 말고 유지. CLAUDE.md §7에도 기록됨.
- `OpenAiFeasibilityAnalysisAdapterTests`는 부하 시 타임아웃 플레이크 — 단독 재실행으로 구분.
- **감사 메타데이터 키는 화이트리스트** (`DomainAuditService`). 새 키는 반드시 등록.
- 검토 시작 가드는 `ProjectStage.LEGAL_REVIEW` — 루프 도는 동안 이 단계에 머물고 발행 시
  FEASIBILITY로 전이한다. 검토 완료 직후 FEASIBILITY를 가정하는 프론트 라우팅이 있는지 M7에서 grep 확인.
- `uk_legal_review_plan_prompt`는 유효 — 같은 plan 버전 재검토는 멱등 반환된다.
  새 검토는 반드시 새 plan 버전 생성 후에만 가능 (FULL 강제 재실행도 동일).
- Mock 산출물 문자열은 시나리오 테스트가 정확히 의존한다 (할일 5개 파싱, "악취 30%" 앵커,
  수정안 A 텍스트). 바꾸면 `FeedbackLoopScenarioTest`와 함께 바꿀 것.
- H2 파일 DB는 실행 중인 백엔드가 잠근다 — 테스트 전 백엔드 종료.
- 프론트 테스트는 Node 22 (Node 25에서 jsdom 깨짐).

## 5. 검증 커맨드

```powershell
cd backend
.\gradlew.bat test --tests "*FeedbackLoopScenarioTest"   # 시나리오 5개 green
.\gradlew.bat test                                        # 전체 — 아래 기준선만 허용

cd ..\frontEnd
npm.cmd run test:run       # NODE_OPTIONS 불필요 (vite.config.js에 플래그 내재화됨)
```

### 기준선 정본 (2026-07-30 실측)

**백엔드: 총 189개 중 사전 실패 4건 + 플레이크 1건.**

| 구분 | 테스트 | 성격 |
|---|---|---|
| 사전 실패 | `Phase1BMigrationTests.v1AndV2MigrationBytesRemainUnchanged` | V1 SQL 해시 불일치 |
| 사전 실패 | `Phase1CMigrationTests.v1V2AndV3ChecksumsRemainUnchanged` | 〃 |
| 사전 실패 | `Phase3MigrationTests.v1ThroughV8MigrationBytesRemainUnchanged` | 〃 |
| 사전 실패 | `Phase3MigrationTests.freshH2SchemaAppliesV1ThroughV10AndValidatesNewTables` | 최신 버전을 `"10"`으로 하드코딩 — V11 이후 항상 실패. 마이그레이션을 추가할 때마다 기대값 메시지의 숫자만 바뀐다(현재 `expected "10" but was "15"`), **같은 실패 1건** |
| 플레이크 | `OpenAiFeasibilityAnalysisAdapterTests.parsesExactlyTenTypedDimensions` | 부하 시 타임아웃. **단독 재실행하면 통과** — 실패 목록에 있으면 반드시 단독으로 확인할 것 |

> 이전 계획서 `~/.claude/plans/harmonic-wobbling-sky.md` §6의 *"176/179 통과, 체크섬 3건"*은
> **V11 이전에 쓰인 낡은 수치**다. 위 표가 정본이다.

**프론트: auth/landing 일부가 사전 실패**(AuthTransitionProvider 누락, CLAUDE.md §8-9).
그 밖의 실패는 풀스위트 병렬 부하 타임아웃 플레이크로 **실행마다 대상 파일이 바뀐다** —
`documents`/`projects`/`structured-plan`/`App`/`AuthProjectFlow` 사이를 옮겨 다닌다.
내 변경 탓인지 판단하려면 해당 파일만 단독 실행할 것.
