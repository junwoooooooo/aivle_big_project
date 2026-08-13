# V10 사용자 검증 절차

## 1. 실행 준비

저장소 루트에서 유효한 로컬 환경 변수를 준비한 뒤 공식 스택을 실행한다.

```powershell
docker compose config --quiet
docker compose up -d --build postgres minio minio-init ai-server backend frontend
docker compose ps
```

`postgres`, `backend`, `frontend`가 정상 상태인지 확인한다. 비밀번호, 토큰, API 키는 검증 기록에 복사하지 않는다.

## 2. 로그인과 표본 프로젝트

1. 브라우저에서 `http://localhost:3000`을 연다.
2. 실제 사용자로 로그인한다.
3. 다음 표본을 준비한다.
   - 아무 실행도 하지 않은 신규 프로젝트
   - 일부 단계가 완료된 진행 중 프로젝트
   - 입력 필요·실패·업데이트 필요 중 하나가 있는 프로젝트
   - 모든 여정과 최신 보고서가 완료된 프로젝트
4. 작업 이력 페이지 검증용 프로젝트는 가능하면 작업 21건 이상을 사용한다.

## 3. 워크스페이스 홈

- [ ] 제목이 `내 워크스페이스`로 표시된다.
- [ ] 프로젝트가 있으면 `이어서 할 프로젝트`, `확인할 항목`, `최근 프로젝트` 순서다.
- [ ] 신규 프로젝트가 `확인 필요`가 아니라 `시작 전`이다.
- [ ] 확인 항목은 최대 3개이며 실제 사유와 행동 링크가 있다.
- [ ] 프로젝트가 없을 때만 시작 안내 레일이 보인다.

## 4. 프로젝트 목록과 필터

- [ ] 필터가 `전체 / 시작 전 / 진행 중 / 확인 필요 / 완료` 순서다.
- [ ] 각 필터 결과가 프로젝트 행의 표시 상태와 일치한다.
- [ ] 프로젝트 행에 이름, 업종, 현재 단계, 진행률, 상태, 최근 변경이 표시된다.
- [ ] 원시 저장 상태나 영문 상태 코드가 기본 화면에 보이지 않는다.

## 5. 프로젝트 헤더와 탐색기

- [ ] 헤더 왼쪽에 업종·이름·현재 위치가 표시된다.
- [ ] 헤더 오른쪽에 전체 표시 상태와 설정 행동이 있다.
- [ ] 상단 행동 순서가 프로젝트 탐색기 → 검색 → 계정이다.
- [ ] 탐색기는 `지금 하는 일`, 상태, `다음에 할 일`, `N/7`을 표시한다.
- [ ] 첫 단계 이전과 마지막 단계 다음 버튼이 비활성이다.
- [ ] 모바일 서랍에서도 프로젝트 탐색기를 열 수 있다.

## 6. 개요와 여정

- [ ] 데스크톱에서 단계가 하나의 굽은 여정으로 읽힌다.
- [ ] 모바일에서 세로 타임라인으로 바뀐다.
- [ ] 행동 문구가 상태에 맞게 `시작하기`, `계속하기`, `입력하기`, `확인하기`, `업데이트하기`, `결과 보기` 중 하나다.
- [ ] 문자형 이동 화살표 대신 일관된 SVG 아이콘을 사용한다.

## 7. 작업센터

- [ ] 빠른 목록은 실행 중 1건, 확인 필요 1건, 최근 3건을 넘지 않는다.
- [ ] `전체 작업 보기`가 데스크톱 오른쪽 서랍과 모바일 전체 화면으로 열린다.
- [ ] 전체 이력은 최신순이며 필터가 적용된다.
- [ ] 21건 이상이면 `이전 작업 더 보기`가 보이고 누르면 중복 없이 누적된다.
- [ ] 상세 상단 대표 행동은 `업무 화면 열기` 하나다.
- [ ] 실패 상세의 재시도는 재시도 가능한 경우에만 실패 맥락 안에 보인다.
- [ ] 작업 ID·오류 코드·검증 필드 등은 접힌 `기술 정보` 안에만 있다.

## 8. 반응형·접근성

각 폭에서 홈, 목록, 개요, 작업센터 목록과 상세를 확인한다.

| 폭 | 확인 항목 |
|---|---|
| 1440px | 여정 지도, 오른쪽 작업 서랍, 프로젝트 헤더 정렬 |
| 1024px | 헤더 행동과 프로젝트 행의 겹침 없음 |
| 768px | 탐색·작업센터 전환, 카드와 표의 잘림 없음 |
| 390px | 가로 넘침 없음, 모바일 서랍 도구, 전체 화면 작업센터 |

공통 확인:

- [ ] 키보드 Tab 순서가 시각 순서와 일치한다.
- [ ] 아이콘 전용 버튼에 읽을 수 있는 이름이 있다.
- [ ] 현재 위치에 `aria-current`가 적용된다.
- [ ] 닫기 후 포커스가 작업센터를 연 버튼으로 돌아온다.
- [ ] 200% 확대에서도 핵심 행동이 가려지지 않는다.

## 9. 자동 검증 재실행

```powershell
cd backend
.\gradlew.bat test --tests com.aivle.backend.project.ProjectServicePresentationTests --tests com.aivle.backend.taskrun.service.ProjectJobQueryServiceTests --tests com.aivle.backend.taskrun.api.ProjectJobControllerTests

cd ..\frontEnd
npm.cmd run test:run -- src/app/module-status/projectJourneyModel.test.js src/features/projects/model/projectPresentation.test.js src/app/project-shell/ProjectLayout.test.jsx src/app/project-shell/ProjectContextTools.test.jsx src/app/project-shell/ProjectModulePages.test.jsx src/features/job-center/JobCenter.test.jsx src/features/projects/ProjectPages.test.jsx src/features/projects/WorkspaceHomePage.test.jsx src/features/concept-portfolio/pages/BusinessProposalWorkspace.test.jsx src/features/marketing-content/components/MarketingVisualSection.test.jsx
npm.cmd run build
```

## 10. 완료 판정

위 체크리스트를 실제 인증 프로젝트에서 모두 확인하고 브라우저 콘솔 오류가 없을 때만 V10을 `COMPLETE`로 판정한다. 한 항목이라도 확인하지 못하면 해당 화면·폭·프로젝트 상태를 결과 문서의 `REMAINING GAP`에 남긴다.

