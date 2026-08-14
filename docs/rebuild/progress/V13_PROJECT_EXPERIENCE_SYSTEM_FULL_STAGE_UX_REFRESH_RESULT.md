# V13 프로젝트 경험 시스템 및 전체 단계 UX 개편 결과

## 판정

**PARTIAL / LIVE VISUAL HOLD**

구조·기능·테스트 구현은 완료했지만, 인증된 프로젝트 화면에서 요구된 1440×900·1920×1080·1024·390×844 실측과 실제 파일 업로드를 수행하지 못했다. 로컬 서비스는 열렸으나 `/app/projects/1/overview`가 `/auth/login`으로 이동했고 브라우저에 인증 세션이 없었다. 따라서 이 문서는 UX COMPLETE를 선언하지 않는다.

## START SHA

| 항목 | 값 |
|---|---|
| branch | `full` |
| START HEAD | `c940a911af0de4f995899eae490ce4d2c7ecd21b` |
| local `origin/full` | `c940a911af0de4f995899eae490ce4d2c7ecd21b` |
| expected | `c940a911af0de4f995899eae490ce4d2c7ecd21b` |
| fetch | GitHub 네트워크 연결 불가. 로컬 추적 ref와 HEAD 일치 확인 |
| 시작 worktree | clean |

V12의 인증 Project 화면 및 bounding rect 미검증 사실을 이어받았다. V11/V12의 Bottom Sheet, portal, body scroll lock, Quick/Full 상호 배제, history pagination 계약은 변경하지 않았다.

## QUICK WORK CENTER ROOT CAUSE

compact 렌더에도 루트 클래스 `pipeline-task-center job-center job-center--compact`가 사용되어, Full Work Center용 flex/레이아웃 계약과 compact grid 계약이 같은 노드에 함께 적용됐다. CSS 우선순위 보강만으로는 이 구조적 혼합을 제거할 수 없었다.

수정 후 루트 계약은 다음과 같다.

| 변형 | 루트 클래스 |
|---|---|
| Quick | `job-center job-center--compact` |
| Full | `pipeline-task-center job-center` |

compact에는 1열 grid, `width:100%`, `min-width:0`, `max-width:none`, stretch를 명시했다. summary, groups, group/list/button, detail button도 동일 content width 계약을 갖는다. 단위 테스트에서 compact 루트에 `pipeline-task-center`가 없음을 고정했다.

## QUICK RECT BEFORE/AFTER

| viewport | before | after | 판정 |
|---|---:|---:|---|
| 1440×900 | 사용자 화면에서 좌측 쏠림 확인, 수치 미수집 | 인증 세션 부재로 미수집 | HOLD |
| 1920×1080 | 수치 미수집 | 인증 세션 부재로 미수집 | HOLD |

요구 비율 `jobCenter/popover >= .98`, `summary/jobCenter >= .98`, `detailButton/jobCenter >= .98`은 코드 계약만 확인했으며 실제 PASS로 기록하지 않았다.

## IDEA ACTION RECT

`ProjectWorkspaceActions`를 추가해 Idea의 primary action을 split workspace와 같은 `max-width:92rem`, 가운데 정렬, 우측 정렬 컨테이너에 배치했다.

| 항목 | 결과 |
|---|---|
| 정적 계약 | split/action 모두 최대 92rem |
| `actionRight <= splitWorkspaceRight + 1px` | 인증 세션 부재로 미수집, HOLD |

## IDEA ATTACHMENT DATA FLOW

```text
FileDropzone
  → 프런트 확장자·크기·개수 검사
  → POST /api/v3/projects/{projectId}/idea-brief/attachments
  → 서버 DOCX/TXT/MD 내용 검증
  → ObjectStorage 저장 + stored_files Long ID
  → idea_attachment_uploads 프로젝트/사용자 소유권 저장
  → derive 요청 attachmentFileIds
  → 소유권 재검증 + 저장 객체 본문 추출
  → TaskRun input attachmentDocuments
  → AI provider 입력에 문서 filename/mediaType/content 포함
```

기존 `IdeaBrief.attachmentFileIds`의 Long 계약은 유지했다. 별도 `idea_attachment_uploads` 테이블로 `stored_file_id`, `project_id`, `uploaded_by_user_id`를 묶어 다른 프로젝트 파일 참조를 차단했다. DOCX는 Apache POI, TXT/MD는 UTF-8로 추출한다. 파일당 20,000자, 전체 60,000자로 AI 입력을 제한하며 ID 순서로 정렬해 canonical input을 결정적으로 만든다.

## FILE TYPE POLICY

| 형식 | 프런트 | 서버 | 내용 검사 | AI source |
|---|---|---|---|---|
| DOCX | 허용 | 허용 | ZIP/OOXML + `word/document.xml` | 본문 추출 |
| TXT | 허용 | 허용 | NUL 금지 + strict UTF-8 | 본문 사용 |
| MD | 허용 | 허용 | NUL 금지 + strict UTF-8 | 본문 사용 |
| PNG/JPG/PDF/기타 | 거부 | 거부 | 확장자 단계 거부 | 미사용 |
| 빈 파일 | 거부 | 거부 | size 0 | 미사용 |
| 20MB 초과 | 거부 | 거부 | bounded read | 미사용 |

프런트 `accept`는 선택 편의일 뿐이며 서버 정책을 authority로 둔다. 최대 20개, 파일당 20MB다. 업로드 재시도 시 같은 `name:size:lastModified` 파일의 반환 ID를 재사용해 중복 업로드를 줄인다.

## PAGE INVENTORY / PAGE → EXPERIENCE MODE MATRIX

| PAGE / 상태 | OLD PATTERN | NEW MODE | PRIMARY TASK | CONTEXT | PRIMARY ACTION | MOTION | COPY CHANGES |
|---|---|---|---|---|---|---|---|
| Project Overview | journey map | ANALYZE | 전체 단계 파악 | 단계 상태 | 단계 열기 | 기존 hover 유지 | 유지 |
| Idea Intake | wide form | COMPOSE | 필수·선택 입력 | 참고 문서/선택 조건 | 사업안 만들기 | stage/accordion | 사용자 목적 중심 |
| Idea 질문/보완 | card form | COMPOSE | 누락 답변 | 질문 이유 | 답변 제출 | disclosure | 기술 강조 축소 기반 |
| Idea 검토/확정 | mixed review | REVIEW | 정리 내용 확인 | 수정/결정 항목 | 확정 | stage | “정리된 사업 아이디어” 기반 |
| Concept 생성 전/진행 | mixed state | DECIDE | 후보 생성 | 진행 상태 | 검토 시작 | stage/progress token | “사업안” 통일 |
| Concept 후보 | card grid | DECIDE | 후보 비교/선택 | 가설/법률 | 선택 | stage/press | Concept 기본 노출 축소 |
| Concept 비교 | repeated cards | DECIDE | 같은 기준 비교 | 결정 panel | 선택 | stage | 사업안 중심 |
| Legal | warning-heavy result | REVIEW | 판단/주의/근거 확인 | 기술 상세 접기 | 검토 확정 | disclosure | 내부 용어 축소 기반 |
| Market Research | result cards | ANALYZE | 시장·경쟁·고객 확인 | source accordion | 조사 실행 | stage | 목적형 제목 적용 |
| Market Integration | market 내부 상태 | ANALYZE | 결과 통합 | 근거 | 최신화 | stage | 사용자 상태 유지 |
| Market 재수집 | details/options | COMPOSE 보조 | 고급 재수집 조건 | 원장 근거 | 다시 수집 | disclosure | 기술 옵션은 접힘 유지 |
| BM Plan | input phase | COMPOSE | 실행 계획 입력 | 시장 결과 | 캔버스 만들기 | 기존 전환 | 유지 |
| BM Canvas | wide canvas | ANALYZE | 수익 구조 확인 | 판단/근거 | 다음 단계 | stage | 목적형 제목 적용 |
| TechOps 준비 | long split form | COMPOSE | 운영 핵심 사실 입력 | 조건/근거 | 입력 저장 | stage/press | Concept→선택 사업안 |
| TechOps Advisory | appended result | REVIEW | 상용화 자문 확인 | 근거 접기 | 자문 실행 | stage | current→최신 자료 |
| Finance 입력 | split form | COMPOSE | 비용·가격·목표 입력 | CAC/고급 가정 | 저장/분석 | stage/press | 목적형 제목 적용 |
| Finance 결과 | wide table/chart | ANALYZE | 손익 결과 확인 | 가정 | 다음 단계 | stage | Seed→재현 기준 |
| Finance AI Report | report groups | ANALYZE | 권고 검토 | 근거 | 갱신 | stage | 내부 상태 기본 노출 억제 |
| Twin Stimulus | card flow | ANALYZE | 질문 준비 | 사업안 | 초안 만들기 | stage + stepper | 목적형 제목 적용 |
| Twin Sample | sequential step | COMPOSE | 대상/표본 설정 | 비교안 | 인터뷰 실행 | 기존 stepper | “조사” 사용자 언어 유지 |
| Twin Progress | inline | ANALYZE | 진행 확인 | 경과 | 대기 | progress token 기반 | 진행 상태 통일 기반 |
| Twin Result | result sections | ANALYZE | 반응/패턴 확인 | 해석 주의 | 마케팅 이동 | stage | 목적형 제목 적용 |
| Marketing Source | 3-step | REVIEW | 사업안 확인 | 법률 조건 | 콘텐츠 만들기 | stage/stepper | Concept→사업안 |
| Marketing Setup | form | COMPOSE | 생성 조건 입력 | style/reference | 생성 | press | 유지 |
| Marketing Progress | inline panel | REVIEW | 처리 상태 확인 | events | 대기/재시도 | 기존 + token | 상태 언어 유지 |
| Marketing Preview/Style/Legal/Visual/Revision | strong nested panels | REVIEW | 편집/검토 | editor rail | 최종 저장 | stage/press | 목적형 최상단 제목 |
| Final readiness | dashboard-like readiness | DOCUMENT | 준비 상태 확인 | missing sources | 보고서 만들기 | stage | “만들기/업데이트” |
| Final report/current/stale/print | document + toolbar | DOCUMENT | 읽기/갱신/인쇄 | source appendix | PDF/업데이트 | stage | document 목적형 제목 |
| Work Quick | mixed flex/grid | COMPOSE 보조 | 최근 작업 확인 | summary | 전체 작업 보기 | press/popover 계약 | 유지 |
| Work Full | Bottom Sheet | ANALYZE 보조 | 전체 작업/기록 확인 | filters/detail | 닫기/입력 | 기존 sheet | V12 유지 |

## INPUT UX MATRIX

| 화면 | main | context | 폭 계약 |
|---|---|---|---|
| Idea | 필수 입력 + 파일 | 선택 입력 10개 | COMPOSE 92rem, desktop split |
| TechOps | 핵심 운영 사실 | 제안 결정 + evidence | COMPOSE, 기존 split 유지 |
| Finance | 비용·가격·매출 목표 | CAC·고급 가정 | COMPOSE, 기존 split 유지 |
| BM Plan | 실행 계획 | 시장 근거 | 별도 input phase, canvas에는 split 미강제 |
| Twin 설정 | 비교 상황·자극 | 표본 | 순차 flow, split 미강제 |
| Marketing Setup | 생성 설정 | source/style | 3-step 보존 |

## RESULT UX MATRIX

| 화면 | 결과 구조 | width |
|---|---|---|
| Market | 시장 규모→경쟁→고객→근거 | ANALYZE wide |
| BM | 판단→캔버스→근거 | ANALYZE wide |
| Finance | table/chart/report | ANALYZE wide |
| Twin | 한눈 요약→의견→패턴→주의 | ANALYZE wide |
| Marketing | preview main + editor context | REVIEW |
| Final Report | toolbar/status + document + appendix | DOCUMENT 76rem |

## COPY AUDIT MATRIX

| 기존 | 변경 | 위치 |
|---|---|---|
| Concept | 사업안 / 선택한 사업안 | Marketing, TechOps 사용자 UI |
| current 소스 | 앞 단계의 최신 자료 | TechOps stale 안내 |
| proposal version | 새 제안 | TechOps helper |
| Seed | 재현 기준 | Finance simulation |
| 아이디어 입력 | 사업 아이디어의 출발점을 알려주세요 | Idea heading |
| 시장조사 결과 | 시장 상황과 경쟁 환경을 확인하세요 | Market heading |
| BM 분석 | 가치 전달과 수익 방식을 확인하세요 | BM heading |
| 확정된 Concept를 실제 콘텐츠로 | 확정한 사업안을 고객에게 보여줄 콘텐츠로 | Marketing heading |

ID/hash/version은 Final Report의 ‘기술 정보’ disclosure와 TechOps 기술 정보처럼 명시적으로 접힌 영역에만 남겼다.

## MOTION MATRIX

| 대상 | token / 동작 | layout 영향 | reduced motion |
|---|---|---|---|
| Stage 진입 | 420ms emphasized, opacity + 8px translate | 없음 | 제거 |
| 버튼 press | 140ms, scale .985 | 없음 | 제거 |
| Accordion panel | 220ms, opacity + 5px translate | 열림 후 DOM flow | 제거 |
| Progress indicator | 800ms rotate | 고정 크기 | 제거 |
| 기존 dialog/sheet | V11/V12 token 유지 | overlay | 기존 대응 유지 |

height/width 애니메이션을 새로 추가하지 않았고 transform/opacity 중심이다.

## RESPONSIVE MATRIX

| viewport | 기대 계약 | 자동/정적 확인 | live |
|---|---|---|---|
| 1920×1080 | COMPOSE bounded, ANALYZE wide, DOCUMENT 76rem | CSS 확인 | HOLD |
| 1440×900 | Idea/TechOps/Finance split | 기존 breakpoint + CSS 확인 | HOLD |
| 1024 | split 1열, context 뒤 | 기존 1199px 계약 확인 | HOLD |
| 390×844 | 1열, action 접근, overflow 0 | mobile CSS 확인 | HOLD |

## ACCESSIBILITY MATRIX

| 항목 | 결과 |
|---|---|
| Stage title | semantic `h1`, 선택적 `titleId` |
| Stage number | `aria-label="N단계"` |
| Optional fields | button, `aria-expanded`, `aria-controls` 유지 |
| File error | `role=alert` 유지 |
| Progress | `aria-live=polite` |
| Status strip | `role=status` |
| reduced motion | 신규 stage/accordion/progress/press 모두 대응 |
| keyboard | 실제 button/link 사용, 기존 focus 계약 유지 |

## TEST MATRIX

| 영역 | 명령/범위 | 결과 |
|---|---|---|
| Front targeted | JobCenter, ProjectWorkspace, Idea, Concept, Market/BM, TechOps, Finance, Twin, Marketing, Final | 12 files, 59 tests PASS |
| Front protected contracts | ProjectContextTools, ProjectForm, ProjectWorkspace, JobCenter, useProjectJobs | 5 files, 25 tests PASS |
| Front build | `npm.cmd run build` | PASS, chunk size warning 1건 |
| Changed-file lint | 변경 JSX/JS 23개 | PASS |
| Backend attachment | `IdeaAttachmentTests` | 3 PASS |
| Backend Idea package | Idea 전체 관련 31 tests | PASS. 오래된 controller fixture를 현 DeriveRequest로 정정 후 재실행 |
| Backend storage regression | `ProjectEvidenceArtifactTests` | PASS |
| AI schema | `ai/.venv` Idea schema | 8 PASS |
| Java compile | `gradlew compileJava` | PASS |
| diff | `git diff --check` | PASS, Python LF→CRLF 경고만 존재 |

## LIVE VISUAL MATRIX

| 화면 | 접근 | 결과 |
|---|---|---|
| public landing | 성공 | 서비스 구동 확인 |
| `/app/projects/1/overview` | `/auth/login`으로 redirect | 인증 세션 없음 |
| Overview / Idea / Concept / Market / BM / TechOps / Finance / Twin / Marketing / Final | 불가 | HOLD |
| Work Quick / Full | 불가 | HOLD |
| Quick rect / Idea rect / Full rect / top-level rect | 미수집 | HOLD |
| sample.docx / sample.txt / image.png live upload | 미실행 | HOLD |

## REMAINING GAP

1. 인증된 실제 프로젝트 세션에서 1440×900, 1920×1080, 1024, 390×844 화면 캡처와 bounding rect를 수집해야 한다.
2. Quick width ratio와 Idea action right-edge 계약은 실제 브라우저 수치가 필요하다.
3. sample.docx, sample.txt의 UI→server→ID→derive→brief linkage 전체 live test와 image.png UI/server 거부 확인이 필요하다.
4. 인증 데이터의 세부 sub-state별 card nesting과 copy는 화면을 본 뒤 2차 미세 조정이 필요하다.

위 항목 때문에 V13은 COMPLETE가 아니라 PARTIAL / HOLD다.
