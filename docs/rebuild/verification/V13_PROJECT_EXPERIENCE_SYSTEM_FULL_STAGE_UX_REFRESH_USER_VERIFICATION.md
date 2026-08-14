# V13 프로젝트 경험 시스템 사용자 검증

## 검증 전 준비

1. 로컬 서비스를 실행하고 로그인한다.
2. Idea부터 Final Report까지 실제 데이터가 있는 프로젝트를 연다.
3. 브라우저 zoom은 100%로 둔다.
4. Desktop 1440×900, Desktop 1920×1080, Tablet 1024, Mobile 390×844 순서로 확인한다.

현재 자동 검증 환경에는 인증 세션이 없어 아래 항목은 사용자 검증이 필요하다.

## 1. Quick Work Center

1. 프로젝트 상단의 빠른 작업 버튼을 누른다.
2. header, 3개 요약 지표, 작업 그룹, ‘전체 작업 보기’ 버튼이 같은 폭을 사용하는지 확인한다.
3. 오른쪽에 의미 없는 큰 빈 공간이 없는지 확인한다.
4. 개발자 도구에서 아래 rect를 기록한다.

```js
const selectors = [
  '.project-work-popover', '.job-center--compact', '.job-center--compact > header',
  '.job-center--compact .job-center__summary', '.job-center--compact .job-center__groups',
  '.job-center--compact .job-center__detail-button'
];
Object.fromEntries(selectors.map((selector) => {
  const element = document.querySelector(selector);
  return [selector, element && { rect: element.getBoundingClientRect().toJSON(),
    display: getComputedStyle(element).display, width: getComputedStyle(element).width,
    maxWidth: getComputedStyle(element).maxWidth, minWidth: getComputedStyle(element).minWidth,
    flex: getComputedStyle(element).flex, gridTemplateColumns: getComputedStyle(element).gridTemplateColumns }];
}));
```

PASS 기준은 compact/popover content, summary/compact, detail button/compact 폭 비율이 각각 0.98 이상이다.

## 2. Idea action 정렬

1. Idea 입력 화면을 연다.
2. 1440과 1920에서 필수 입력/선택 입력이 2열인지 확인한다.
3. ‘입력 내용으로 사업안 만들기’ 버튼의 오른쪽 끝이 split workspace 오른쪽 끝과 맞는지 확인한다.
4. `actionRight <= splitWorkspaceRight + 1px`인지 기록한다.
5. 1024와 390에서는 필수→참고 자료→선택 입력 순으로 한 열인지 확인한다.

## 3. 실제 첨부 문서

1. 20MB 이하 `sample.docx`를 선택한다. 선택 목록과 크기가 보여야 한다.
2. 20MB 이하 UTF-8 `sample.txt`와 `sample.md`도 추가한다.
3. `image.png`를 선택한다. ‘DOCX, TXT, MD 파일만 추가할 수 있습니다.’가 표시되고 목록에 들어가지 않아야 한다.
4. 제출한다. 업로드 중 문구가 표시되고 버튼이 비활성화돼야 한다.
5. 네트워크에서 각 파일의 `POST /idea-brief/attachments`가 201과 numeric `attachmentFileId`를 반환하는지 확인한다.
6. 이어지는 `/derive` body의 `attachmentFileIds`가 빈 배열이 아니며 반환 ID를 포함하는지 확인한다.
7. DB에서 `stored_files`, `idea_attachment_uploads`, `idea_brief_attachments` 연결을 확인한다.
8. 다른 프로젝트에서 얻은 ID를 derive에 넣으면 400으로 거부되는지 확인한다.
9. 확장자만 `.txt`로 바꾼 바이너리 파일과 손상 DOCX가 서버에서 거부되는지 확인한다.

## 4. 단계별 화면

| 화면 | 확인 사항 |
|---|---|
| Idea | 목적형 제목, 92rem split, attachment, action 정렬 |
| Idea Review | 핵심 내용과 수정/확인 항목의 우선순위 |
| Concept | 후보 이름→가치→고객→차별점→선택 순서 |
| Market | 넓은 분석 화면, 관련 결과별 section, source는 보조 |
| BM | canvas 폭 유지, 일반 form split 강제 없음 |
| TechOps | 핵심 사실 좌측, 조건/evidence 우측 |
| Finance | 입력 split, 결과 table/chart wide |
| Twin | 질문 준비→대상 설정→실행→결과 step 순서 |
| Marketing | 컨셉 확인→생성 설정→결과 확인 3-step 유지 |
| Final Report | dashboard가 아닌 document width, appendix 접힘 |

## 5. Motion과 접근성

1. stage 진입은 짧은 fade/상향 이동이며 레이아웃이 흔들리지 않아야 한다.
2. 버튼을 누를 때 미세 press 반응만 있어야 한다.
3. 선택 입력 accordion은 Enter/Space로 열리고 값이 보존돼야 한다.
4. OS의 ‘동작 줄이기’를 켜면 stage, accordion, progress 회전, press transform이 없어야 한다.
5. 키보드 Tab으로 단계 제목 이후 입력과 primary action에 도달할 수 있어야 한다.

## 6. Full Work Center 회귀

1. Quick을 연 뒤 Full을 열면 Quick이 닫혀야 한다.
2. Full은 Bottom Sheet이며 document.body portal을 유지해야 한다.
3. 전체→진행 중 0건→입력 필요 0건→완료·종료를 바꿔도 outer height 차이가 1px 이하여야 한다.
4. 완료 작업 상세에서 저장된 event가 있으면 표시되고, REST 결과가 실제 0건일 때만 기록 없음 문구가 보여야 한다.
5. 닫은 뒤 body scroll이 복구되는지 확인한다.

## 7. 반응형 PASS 기준

| viewport | PASS 기준 |
|---|---|
| 1920×1080 | 입력 bounded, 분석 wide, 보고서 76rem |
| 1440×900 | Idea/TechOps/Finance 2-pane, action 정상 flow |
| 1024 | split 1-column, context가 main 뒤에 위치 |
| 390×844 | horizontal overflow 0, accordion/action/Bottom Sheet 접근 가능 |

## 8. 결과 기록

다음 값을 결과 문서의 LIVE VISUAL MATRIX에 추가한다.

- viewport별 screenshot 경로
- Quick 6개 selector rect 및 폭 비율
- Idea split/action rect
- Full Work Center filter별 x/y/width/height
- top-level content rect
- DOCX/TXT/MD upload ID 및 derive request 확인
- PNG/손상 파일 거부 응답

모든 항목이 충족되기 전에는 V13 COMPLETE로 변경하지 않는다.
