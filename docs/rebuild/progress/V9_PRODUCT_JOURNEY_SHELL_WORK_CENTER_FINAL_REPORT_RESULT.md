# V9 Product Journey·Shell·Work Center·Final Report 결과

## 1. START SHA

| 항목 | 값 |
|---|---|
| Branch | `full` |
| START SHA | `491c11cfe2ede039b0b035707f457f774a950bf7` |
| origin/full | `491c11cfe2ede039b0b035707f457f774a950bf7` |
| 시작 Worktree | clean |

## 2. CHANGED FILES

| 영역 | 파일 |
|---|---|
| Journey 모델 | `frontEnd/src/app/module-status/projectJourneyModel.js`, `projectJourneyModel.test.js` |
| Project Chrome | `ProjectChromeContext.jsx`, `ProjectContextTools.jsx`, `ProjectContextTools.test.jsx` |
| Project Shell | `ProjectLayout.jsx`, `ProjectLayout.test.jsx`, `ProjectModulePages.jsx`, `project-shell.css`, `project-shell-polish.css` |
| App Topbar | `AppShell.jsx`, `layouts.css` |
| Routing | `projectRoutes.js`, `AppRouter.jsx` |
| Work Center | `JobCenter.jsx`, `JobCenter.test.jsx` |
| Project/Home | `ProjectPages.jsx`, `ProjectPages.test.jsx`, `WorkspaceHomePage.jsx`, `ProjectRow.jsx`, `projectViewModel.js`, `projects.css` |
| Landing | `WorkflowSection.jsx`, `landingData.js` |
| Final Report Frontend | `features/final-report/finalReportApi.js`, `FinalReportPage.jsx`, `FinalReportPage.test.jsx`, `final-report.css` |
| Final Report Backend | `pipeline/finalreport/api/*`, `application/*`, `domain/*`, `repository/*`, `FinalReportComposerTests.java` |
| 기존 source 조회 확장 | `FinancialInputSnapshotRepository.java` |
| Project summary | `ProjectSummaryResponse.java`, `ProjectService.java` |
| DB | `V25__final_report_snapshots.sql` |
| 문서 | 본 결과 문서, V9 사용자 검증 문서 |

## 3. OLD → NEW INFORMATION ARCHITECTURE

| OLD | NEW |
|---|---|
| 개요 + 8개 기술 모듈 + 설정 | 개요 + 6개 사용자 업무 Journey |
| 좌측 단계 Sidebar | Topbar 단계 Navigator |
| 우측 상시 Work Center | Topbar Quick Panel + Full sheet |
| 좌하단 fixed 도움말 | Topbar 도움말 popover |
| 12rem + main + 17rem | 최대 1600px wide main |
| 설정을 단계처럼 노출 | Project Header의 프로젝트 설정 버튼 |
| 마지막 산출물이 마케팅 | canonical source 기반 최종 보고서 |

## 4. MODULE → JOURNEY MAP

| Journey | 내부 모듈 | 기존 route |
|---|---|---|
| 사업 기획 | IDEA, CONCEPT_PORTFOLIO/SELECTION | `/idea`, `/concepts`, `/concepts/compare` |
| 사업 검증 | MARKET_ANALYSIS, BUSINESS_MODEL | `/market`, `/business-model` |
| 출시 준비 | TECH_OPS, FINANCE | `/tech-ops`, `/finance` |
| 가상 인터뷰 | TWIN_SURVEY | `/twin-survey` |
| 마케팅 전략 | MARKETING_CONTENT/VISUAL | `/marketing` |
| 최종 보고서 | FinalReportSnapshot presentation/domain | `/final-report` |

내부 TaskType, TaskRun, Attempt, JobEvent, SSE 계약은 병합하거나 완화하지 않았다.

## 5. JOURNEY STATUS MATRIX

| child 상태 | Journey 상태 |
|---|---|
| 모두 COMPLETED | COMPLETED |
| 하나라도 NEEDS_INPUT | NEEDS_INPUT |
| 하나라도 FAILED | ATTENTION |
| 하나라도 STALE | STALE |
| RUNNING/QUEUED 존재 | IN_PROGRESS |
| 일부 완료, 나머지 준비 | IN_PROGRESS |
| READY 존재, 완료 없음 | READY |
| 전부 미준비 | NOT_STARTED |

우선순위와 집계는 `aggregateJourneyStatus`에서 결정적으로 처리한다.

## 6. ROUTE COMPATIBILITY MATRIX

| 경로 | Journey | 보존 |
|---|---|---|
| `/overview` | 개요 | 예 |
| `/idea`, `/concepts`, `/concepts/compare` | 사업 기획 | 예 |
| `/market`, `/business-model` | 사업 검증 | 예 |
| `/tech-ops`, `/finance` | 출시 준비 | 예 |
| `/twin-survey` | 가상 인터뷰 | 예 |
| `/marketing` | 마케팅 전략 | 예 |
| `/settings` | Header action | 예 |
| `/final-report` | 최종 보고서 | 신규 |

상위 Journey 진입은 첫 미완료 child를 선택하고, 모두 완료되면 마지막 child를 선택한다.

## 7. TOPBAR TOOL MATRIX

| Tool | Quick 내용 | 동작 |
|---|---|---|
| 도움말 | 현재 Journey, substep, 상태, 다음 할 일 | trigger 재클릭/외부 클릭/Escape 닫기 |
| 단계 | 이전·현재·다음 Journey, 확장 7개 항목 | 현재 Journey 강조, 기존 route 이동 |
| 작업 | 3개 숫자와 1/1/3 요약 | Full Work Center 열기 |

세 도구는 `openTool` 단일 상태로 상호 배타적이며 ProjectLayout이 등록한 데이터를 AppShell이 소비한다. `useProjectModuleStatuses`와 `useProjectJobs`를 AppShell과 Layout에서 중복 호출하지 않는다.

## 8. WORK CENTER DISPLAY MATRIX

| 범위 | 현재 진행 | 입력 필요 | 최근 작업 |
|---|---:|---:|---:|
| Quick | 최대 1 | 최대 1 | 최대 3 |
| 나머지 | `+ 외 N건` | `+ 외 N건` | `+ 외 N건` |
| Full | 전체 | 전체 | API 반환 전체, 현재 최대 20 |

- `최근 완료` 문구 제거, `최근 작업`으로 통일.
- Full view의 `slice(0, 10)` 제거.
- 목록의 별도 `이동` 링크 제거.
- Detail에서만 `작업 화면 열기` 제공.
- Full sheet close icon, dialog semantics, 내부 scrollbar 적용.
- Backend projectId scope와 MAX_RESULTS=20은 변경하지 않았다.

## 9. PROJECT LIST MATRIX

| 화면 | OLD | NEW |
|---|---|---|
| Project List desktop | 2열 card | 1프로젝트 1행 |
| Home recent | 별도 compact 언어 | 동일 ProjectRow의 compact density |
| 진행률 | 8개 내부 모듈 문구 | 완료 Journey `N / 6` |
| 현재 위치 | 8단계 모듈 | 현재 업무 Journey |

Project summary API는 module status를 Journey 단위로 집계하고 current Final Report를 여섯 번째 완료 단계로 계산한다.

## 10. FINAL REPORT SOURCE MATRIX

| Source | Authority | 누락 표시 |
|---|---|---|
| PROJECT | owned Project version | 프로젝트 없음은 접근 거부 |
| IDEA | confirmed IdeaBrief snapshot/hash | 자료 없음·미완료 |
| SELECTED_CONCEPT | current portfolio selection + concept canonicalHash | 자료 없음·미완료 |
| LEGAL | selected concept legal review/baseLegalHash | 자료 없음 |
| MARKET | latest FULL materialized version/result | 자료 없음·미완료 |
| BUSINESS_MODEL | latest BM materialized version/result | 자료 없음·미완료 |
| TECH_OPS | latest advisory report | 자료 없음·미완료 |
| FINANCE | latest finalized financial snapshot | 자료 없음·미완료 |
| FINANCE_REPORT | adopted provider TaskResult가 있을 때만 | deterministic source만 유지 |
| TWIN_SURVEY | latest materialized Twin version | 자료 없음·미완료 |
| MARKETING | 현재 최신 content가 FINALIZED일 때 revision | 자료 없음·미완료 |
| MARKETING_ASSETS | finalized content artifact refs | 자료 없음 |

보고서는 JSON 원천을 결정적으로 편집하며, 누락 내용을 추정하거나 AI TaskType을 새로 만들지 않는다.

## 11. FINAL REPORT LINEAGE MATRIX

| 항목 | 구현 |
|---|---|
| Snapshot ID | UUID |
| Project scope | `project_id` + ownership 확인 |
| Report version | 프로젝트별 증가 `report_version` |
| Source manifest | type/id/version/revision/resultHash/generatedAt |
| Manifest hash | canonical JSON SHA-256 |
| Stored report | immutable report JSON snapshot |
| CURRENT | stored hash = current hash 이고 모든 필수 Journey/source current |
| STALE | source hash 변경 또는 readiness/current 조건 이탈 |
| NOT_READY | snapshot 없음 또는 생성 시 필수 current source 부족 |

## 12. RESPONSIVE MATRIX

| 폭 | 동작 |
|---|---|
| Desktop ≥ 1100 | Topbar label 표시, wide main, popover 우측 정렬 |
| Tablet 700~1100 | Topbar icon 유지, 본문 wide |
| Mobile ≤ 700 | 도구 icon 유지, popover fixed inset 1rem, Project Header stack |
| Project Row ≤ 800 | 행 정보 세로 stack |
| Report ≤ 700 | 문서 padding 축소, metadata/source 1열 |

공개 Landing은 실제 1440×1000과 390×844 viewport에서 6단계 항목과 responsive stack을 확인했다.

## 13. ACCESSIBILITY MATRIX

| 항목 | 구현 |
|---|---|
| Context trigger | aria-label/expanded/controls |
| Popover | 외부 클릭, Escape, focus return |
| Navigator | Link 기반 keyboard access, current highlight |
| Substep | nav label, aria-current=step |
| Job detail back | aria-label=`전체 작업으로 돌아가기` |
| Full Work Center | role=dialog, aria-modal=true, 명시적 close |
| Print | app chrome/action 제거 |

## 14. TEST MATRIX

| 검증 | 결과 |
|---|---|
| Journey path/entry/status unit | 통과 |
| Project Shell 상시 rail 제거 test | 통과 |
| Topbar mutual exclusion/Escape/focus test | 통과 |
| Work Center 1/1/3 및 +외N test | 통과 |
| Project List 6단계 표현 test | 통과 |
| Final Report current/not-ready/generate frontend test | 통과 |
| Frontend targeted | 7 files, 29 tests 통과 |
| Changed-file ESLint | 통과 |
| Backend compileJava/compileTestJava | 통과 |
| FinalReport manifest deterministic hash | 통과 |
| ProjectJobQueryService/Controller | 통과 |
| git diff --check | 통과 |
| Frontend production build | 통과(267 modules transformed). 500 kB 초과 chunk 경고 있음 |
| 전체 backend/postgresTest | 저장소 Fast Execution 규칙에 따라 미실행 |

## 15. REMAINING GAP

1. Docker/PostgreSQL 공식 환경에서 인증된 Project route의 시각 검증이 필요하다.
2. 실제 source 변경 뒤 CURRENT→STALE→CURRENT 전체 흐름의 통합 테스트가 필요하다.
3. Browser Print Preview와 생성된 PDF의 페이지 나눔을 수동 검증해야 한다.
4. Project list summary는 프로젝트별 current source를 정확히 계산하는 대신 조회 비용이 증가할 수 있어 다수 프로젝트 환경의 query 성능 측정이 필요하다.
5. DOCX export는 구현하지 않았다. HTML Preview와 Print/PDF가 V9 필수 결과이며, 기존 Apache POI를 이용한 DOCX는 별도 후속 범위로 둔다.
6. 격리 H2 브라우저 실행은 기존 V1 migration의 PostgreSQL partial index 문법 때문에 불가능했다. 공식 실행 표면인 Docker/PostgreSQL에서 확인해야 한다.

따라서 코드 구현과 대상 자동 검증은 완료했으나, 지시서의 완료 금지 조건 중 `print layout 미검증`이 남아 있어 이 결과 문서에서는 전체 작업을 `COMPLETE`로 선언하지 않는다.

## 정확한 continuation point

`docs/rebuild/verification/V9_PRODUCT_JOURNEY_SHELL_WORK_CENTER_FINAL_REPORT_USER_VERIFICATION.md`의 3~9절을 Docker/PostgreSQL 환경에서 수행한다. Full Work Center 최근 20건, 다른 프로젝트 작업 비혼입, Final Report STALE 전환, A4 PDF 페이지 나눔을 통과하면 V9 완료 판정을 갱신한다.
