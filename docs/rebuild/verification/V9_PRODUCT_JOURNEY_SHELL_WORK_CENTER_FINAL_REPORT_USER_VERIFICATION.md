# V9 Product Journey·Shell·Work Center·Final Report 사용자 검증

## 1. 검증 목적

기술 모듈 8개를 사용자 내비게이션에 직접 노출하던 구조가 `프로젝트 개요 + 6개 업무 Journey`로 바뀌었는지, 기존 내부 route와 실행 계약을 유지하면서 프로젝트 본문·작업 센터·최종 보고서가 정상 동작하는지 확인한다.

## 2. 사전 준비

저장소 루트에서 다음을 실행한다.

```powershell
git branch --show-current
git rev-parse HEAD
git status --short
python scripts/check_local_env.py --compose
docker compose up --build
```

- 예상 소요: 이미지가 준비된 환경 3~8분, 최초 build 10~25분
- 성공 기준: `docker compose ps`에서 frontend/backend/postgres/minio/ai-server가 healthy이고 `http://localhost:3000`이 열린다.
- 실패 로그:

```powershell
docker compose ps
docker compose logs --tail 200 frontend backend postgres minio ai-server
```

## 3. Navigation 및 Project Shell

1. 로그인 후 프로젝트 하나를 연다.
2. 프로젝트 개요에서 다음 6개 항목만 표시되는지 확인한다.
   - 사업 기획
   - 사업 검증
   - 출시 준비
   - 가상 인터뷰
   - 마케팅 전략
   - 최종 보고서
3. `프로젝트 설정`이 Journey 목록에는 없고 프로젝트 Header 버튼으로만 접근되는지 확인한다.
4. 기존 bookmark를 직접 열어 모두 정상 진입하는지 확인한다.

```text
/idea
/concepts
/concepts/compare
/market
/business-model
/tech-ops
/finance
/twin-survey
/marketing
/final-report
```

5. 데스크톱 1440px 이상에서 좌측 상시 Sidebar, 우측 상시 Work Center, 좌하단 고정 도움말이 없는지 확인한다.
6. 표·차트·canvas가 있는 업무 화면에서 본문이 넓어진 것을 확인한다.
7. 사업 기획·사업 검증·출시 준비 화면 상단에 현재 하위 업무를 나타내는 작은 substep indicator가 있고, 클릭 시 기존 내부 route로 이동하는지 확인한다.

## 4. Topbar Project Context Tool

1. 프로젝트 route에서 검색창 왼쪽에 `도움말`, `단계`, `작업`이 표시되는지 확인한다.
2. Home과 전체 프로젝트 목록에서는 세 도구가 숨겨지는지 확인한다.
3. 각 trigger의 재클릭으로 팝오버가 닫히는지 확인한다.
4. 도움말을 연 뒤 단계를 클릭하면 도움말이 닫히는지 확인한다.
5. 단계를 연 뒤 작업을 클릭하면 단계가 닫히는지 확인한다.
6. 팝오버 바깥 클릭과 `Escape`로 닫히고, `Escape` 후 trigger로 초점이 돌아오는지 확인한다.
7. 단계 팝오버 확장 시 `프로젝트 개요 + 6개 Journey` 총 7개 항목과 상태가 표시되는지 확인한다.

## 5. Work Center

1. `작업` Quick Panel의 숫자 명칭이 `현재 진행`, `입력 필요`, `최근 작업`인지 확인한다.
2. 현재 진행 3건일 때 1건과 `+ 외 2건`만 표시되는지 확인한다.
3. 입력 필요 2건일 때 1건과 `+ 외 1건`만 표시되는지 확인한다.
4. 최근 작업 20건일 때 3건과 `+ 외 17건`만 표시되는지 확인한다.
5. `전체 작업 보기`에서 최근 20건이 모두 표시되고 `최근 20건 기준` 문구가 보이는지 확인한다.
6. 목록에 별도 `이동` 링크가 없고 행 전체 클릭으로 작업 상세가 열리는지 확인한다.
7. 상세의 작은 `‹` 버튼에 스크린리더 이름 `전체 작업으로 돌아가기`가 있는지 확인한다.
8. 필요한 경우에만 상세의 `작업 화면 열기`로 실제 내부 모듈 route가 열리는지 확인한다.
9. Full Work Center 우상단 `×`, backdrop, `Escape`가 모두 닫기 동작을 하는지 확인한다.
10. Full Work Center 내부에만 7px 내외의 compact scrollbar가 적용되는지 확인한다.
11. 프로젝트 두 개를 준비해 한 프로젝트의 작업이 다른 프로젝트 Work Center에 섞이지 않는지 확인한다.

## 6. 프로젝트 목록과 Home

1. 프로젝트 목록이 데스크톱에서도 1프로젝트 1행인지 확인한다.
2. 각 행에 프로젝트명, 사업 분야, 현재 업무, 프로젝트 상태, `N / 6` 진행률, 최근 수정 시각이 표시되는지 확인한다.
3. 행 전체 클릭으로 프로젝트 개요가 열리고 우측 action menu는 별도로 동작하는지 확인한다.
4. Home 최근 프로젝트가 동일한 ProjectRow 언어를 compact density로 사용하는지 확인한다.
5. 800px 이하에서 각 행의 정보가 세로로 자연스럽게 쌓이고 가로 overflow가 없는지 확인한다.

## 7. 최종 보고서

1. `/app/projects/{projectId}/final-report`를 앞 단계 미완료 상태에서도 열 수 있는지 확인한다.
2. 준비 상태에 사업 기획·사업 검증·출시 준비·가상 인터뷰·마케팅 전략 상태가 표시되는지 확인한다.
3. 없는 자료가 `자료 없음 · 미완료`로 표시되고 새 사실이나 임의 수치가 나타나지 않는지 확인한다.
4. 필수 current source가 모두 준비되면 `최종 보고서 생성`을 실행한다.
5. 생성 후 `최신 보고서`, snapshot version, source manifest hash, section별 Source ID가 표시되는지 확인한다.
6. Market/BM/Finance/Twin/Marketing 중 하나를 변경한 뒤 보고서를 다시 열어 `갱신 필요`가 표시되는지 확인한다.
7. `보고서 갱신` 후 version이 증가하고 다시 `최신 보고서`가 되는지 확인한다.
8. 다른 프로젝트의 source ID가 manifest에 들어가지 않는지 확인한다.
9. 다른 사용자 계정으로 접근했을 때 프로젝트가 노출되지 않는지 확인한다.

## 8. Print/PDF

1. current 보고서에서 `PDF로 저장`을 누른다.
2. 브라우저 인쇄 미리보기에서 A4가 선택되고 다음 UI가 빠지는지 확인한다.
   - App Topbar
   - Project Header
   - Context Tool
   - action button
   - 준비/갱신 안내
3. 보고서 본문, 번호 heading, 표, Source lineage만 인쇄되는지 확인한다.
4. heading이 페이지 하단에 홀로 남지 않고 표와 source block이 불필요하게 갈라지지 않는지 확인한다.
5. PDF로 저장한 뒤 한글, 표 border, 장문 hash 줄바꿈을 확인한다.

## 9. 반응형·키보드·접근성

- 1440px 이상: 본문 wide, Topbar 도구 label 표시
- 768~1100px: 도구 icon 유지, 팝오버가 viewport 안에 위치
- 390px: 도움말·단계·작업 모두 접근 가능, 팝오버가 좌우 1rem 안쪽, 가로 overflow 없음
- 모든 trigger: `aria-label`, `aria-expanded`, `aria-controls`
- Full Work Center: `role=dialog`, `aria-modal=true`
- Navigator link: Tab/Shift+Tab 및 Enter 동작
- `Escape`: quick popover와 full sheet 닫기 및 trigger focus 복귀

## 10. 자동 검증 명령

```powershell
cd frontEnd
npm.cmd run test:run -- src/app/module-status/projectJourneyModel.test.js src/app/module-status/projectModuleModel.test.js src/app/project-shell/ProjectLayout.test.jsx src/app/project-shell/ProjectContextTools.test.jsx src/features/job-center/JobCenter.test.jsx src/features/projects/ProjectPages.test.jsx src/features/final-report/FinalReportPage.test.jsx
```

예상 소요 5~15초, 성공 기준 `7 passed`, `29 passed`.

```powershell
cd backend
.\gradlew.bat compileJava compileTestJava test --tests com.aivle.backend.pipeline.finalreport.application.FinalReportComposerTests --tests com.aivle.backend.taskrun.service.ProjectJobQueryServiceTests --tests com.aivle.backend.taskrun.api.ProjectJobControllerTests
```

예상 소요 10~40초, 성공 기준 `BUILD SUCCESSFUL`.

## 11. 현재 검증 상태와 다음 단계 진행 조건

- 실행 완료: 공개 Landing의 6단계 Journey를 1440×1000, 390×844 viewport에서 확인. 6개 항목과 반응형 stack, 가로 overflow 없음 확인.
- 실행 완료: 프런트 대상 테스트 29개, changed-file ESLint, 백엔드 compileJava/compileTestJava, 대상 테스트.
- 실행 완료: 프런트 프로덕션 빌드(`npm.cmd run build`). 267개 모듈 변환 및 산출물 생성 성공. 기존 500 kB 초과 chunk 경고는 남아 있다.
- 미실행: 인증된 프로젝트 화면의 브라우저 검증, 실제 Print Preview/PDF 저장.
- 미실행 사유: 격리 H2 실행은 기존 `V1__new_pipeline_baseline.sql`의 PostgreSQL partial index 문법을 H2가 지원하지 않아 시작 단계에서 중단됨. 제품 코드를 H2에 맞춰 우회 수정하지 않았다.
- 다음 단계 진행 가능 조건: Docker/PostgreSQL 공식 실행 환경에서 3~9절을 확인하고, 특히 Full Work Center 20건·보고서 STALE·A4 인쇄를 통과해야 한다.
