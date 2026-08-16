# MODULE-STATUS-JOB-CENTER RESULT

## 실행 기준

- Branch: `rebuild/new-pipeline-v1`
- Start HEAD: `31aa74b2201572d9430fb0a9de0dce8e7e11903e`
- 시작 시 worktree는 clean이었다.
- 전역 ProjectStage, Route Guard, Legacy workspace는 추가하지 않았다.

## 구현한 계약

- IDEA 상태는 current `IdeaBrief`의 status, active TaskRun, confirmed snapshot, updatedAt에서 계산한다. `Project.description`은 사용하지 않는다.
- Concept Factory 상태는 current run, source Idea Brief snapshot, TaskRun/job id, eligible slot count에서 계산하며 source 변경 시 STALE이다.
- Concept Selection은 current selection과 `SelectedConceptSnapshot`을 함께 확인한다.
- Market 및 Business/Persona 상태는 외부 `ModuleRun`과 현재 입력 snapshot 일치 여부를 조합하며 연결 부재는 NOT_CONNECTED이다.
- Marketing 상태는 current finalized planning snapshot과 최신 `MarketingContent`/TaskRun을 비교하며 source 변경 시 STALE이다.
- 소유권 확인 후 TaskRun 정본을 반환하는 `GET /api/v3/projects/{projectId}/active-jobs`와 `recent-jobs`를 추가했다.
- Job 응답은 job/task/subject/status, 안전한 title/message key, module, time, terminal/retryable, targetRoute를 제공한다. 현재 pipeline의 jobId는 TaskRun id와 같은 계약을 사용한다.
- Project Shell Job Center는 서버 Query로 목록을 먼저 복원하며, 선택한 작업 하나에만 공통 `useJobEvents`를 연결한다.
- Terminal Event 후 active/recent jobs와 module status를 재조회하고 완료/실패/입력 필요 알림을 Shell에 표시한다.
- LocalStorage Job Center와 Concept Factory의 LocalStorage 등록 경로를 제거했다.
- 모든 모듈 링크는 상태와 무관하게 유지되며 실행 전제조건을 Route Guard로 바꾸지 않았다.

## 변경 파일

### Backend

- `pipeline/module/ProjectModuleStatusService.java`
- `pipeline/module/ProjectModuleStatusResponse.java`
- `pipeline/concept/repository/ConceptSlotRepository.java`
- `pipeline/marketing/repository/MarketingContentRepository.java`
- `taskrun/repository/TaskRunRepository.java`
- `taskrun/api/ProjectJobController.java`
- `taskrun/api/ProjectJobView.java`
- `taskrun/service/ProjectJobQueryService.java`
- `pipeline/module/ProjectModuleStatusServiceTests.java`
- `taskrun/service/ProjectJobQueryServiceTests.java`
- `taskrun/api/ProjectJobControllerTests.java`

### Frontend

- `app/project-shell/ProjectLayout.jsx`
- `app/project-shell/project-shell.css`
- `app/module-status/projectModuleModel.js`
- `app/layouts/AppShell.jsx`
- `features/job-center/JobCenter.jsx`
- `features/job-center/jobCenterApi.js`
- `features/job-center/useProjectJobs.js`
- `features/job-center/JobCenter.test.jsx`
- `features/job-center/useProjectJobs.test.jsx`
- `features/job-center/jobCenterStore.js` (삭제)
- `features/concept-factory/hooks/useConceptFactory.js`

## 실제 실행한 검사

- `backend\\gradlew.bat compileJava` — 성공
- Backend targeted tests: `ProjectModuleStatusServiceTests`, `ProjectJobQueryServiceTests`, `ProjectJobControllerTests` — 5 tests 성공
- Frontend targeted Vitest: Job Center hook/component 및 module model — 6 tests 성공
- 변경 Frontend 파일 targeted ESLint — 성공
- `git diff --check` — 성공

Gradle 최초 sandbox 실행은 wrapper 다운로드 네트워크 제한으로 실패했으며, 동일 명령을 승인된 실행 환경에서 다시 실행해 성공 결과를 확인했다. PowerShell의 `npm.ps1` 실행 정책 오류 후 동일 npm 명령을 `npm.cmd`로 실행했다.

## 의도적으로 생략한 검사

- 전체 Backend test 및 `postgresTest`
- 전체 Frontend baseline/build
- Docker build/restart
- Browser E2E 및 실제 SSE 장기 연결 시험
- 외부 Module provider smoke

현재 Unit의 fast execution 범위 밖이므로 실행하지 않았고 사용자 검증 명령으로 넘긴다.

## 남은 위험

- Active/recent 조회는 최대 20개 TaskRun을 반환한다. 매우 오래된 이력의 별도 pagination은 이번 Unit 범위가 아니다.
- Job title/message는 안전한 key로 반환한다. 실제 다국어 문구 catalog 보강은 별도 UI localization 작업이 필요할 수 있다.
- 외부 ModuleRun이 없는 로컬 환경에서는 MARKET_ANALYSIS와 BUSINESS_PERSONA_TEST가 의도대로 NOT_CONNECTED로 보인다.
- 실제 인증 세션에서 새로고침 복원, SSE 차단 후 polling, 다중 Job 전환은 사용자 runtime 검증이 남아 있다.

## 정확한 계속 지점

이 문서의 사용자 검증을 수행해 실제 프로젝트에서 Module badge, Active/Recent Job 복원, Terminal 재조회와 페이지 이동을 확인한다. 실패 시 해당 Query 응답과 안전한 Event metadata만 수집해 이 Unit을 재개하며, 다음 rebuild Unit으로 자동 진행하지 않는다.
