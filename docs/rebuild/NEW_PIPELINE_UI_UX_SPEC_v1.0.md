# NEW PIPELINE UI/UX SPEC v1.0

## 1. 설계 원칙

- 사용자는 내부 구조보다 현재 결과와 다음 행동을 이해해야 한다.
- 진행 상태는 실제 Event에 기반한다.
- 페이지 접근을 막지 않는다.
- 긴 법률·분석 정보는 요약 후 펼쳐본다.
- 버전 숫자 대신 변화 의미를 표시한다.
- Desktop·Tablet·Mobile과 키보드·스크린리더를 동일 기능 수준으로 지원한다.

## 2. 공통 Shell

### Desktop
- Sidebar 240px
- 프로젝트명·현재 단계·작업 센터가 있는 Header
- Main 최대 폭 1440px

### Mobile
- 프로젝트 Header
- 현재 단계 Selector
- 작업 센터 Icon
- 하단 Sticky Primary Action

## 3. Screen Inventory

| ID | 화면 | 핵심 목적 |
|---|---|---|
| P-01 | 프로젝트 개요 | 전체 모듈 상태와 다음 행동 |
| I-01 | 아이디어 입력 | 개요·선택 항목·파일 입력 |
| I-02 | AI 정리 진행 | 실제 Job Event 표시 |
| I-03 | 후속 질문 | Question Card 응답 |
| I-04 | Idea Brief 검토 | Field·출처·상태 확인·확정 |
| C-01 | 컨셉 팩토리 Workboard | 5 Slot 진행·Timeline |
| C-02 | 컨셉 상세 | 기획·법률 결과 확인 |
| S-01 | 컨셉 비교 | 카드·비교표 |
| S-02 | 선택 확인 | 선택 Snapshot 확인 |
| M-01 | 시장 모듈 상태 | Handoff·외부 상태 |
| M-02 | 시장 결과 | 요약·경쟁상품·변경 제안 |
| M-03 | 변경안 검토 | 채택·일부 채택·거절 |
| M-04 | 최종 기획 | 의미 기반 변화 이력·확정 |
| B-01 | BM·재무+Persona Shell | 외부 결과 3영역 |
| MK-01 | 콘텐츠 목록 | Source 기준과 결과 목록 |
| MK-02 | 콘텐츠 제작 | 설정·Canvas·편집 |
| J-01 | 전역 작업 센터 | 모든 비동기 Run 확인 |

## 4. 화면별 요구

### P-01 프로젝트 개요
- 프로젝트 요약
- 현재 선택 컨셉·최종 기획
- 6개 모듈 상태 카드
- 진행 중 작업
- 최근 활동
- 다음 행동

### I-01 아이디어 입력
- 자유 입력 중심
- 보조 Field는 선택
- 파일 첨부
- Primary: `AI로 아이디어 정리하기`

### I-03 후속 질문
- 2~4개 묶음
- 선택형은 키보드 조작
- 아직 결정하지 않음 제공
- 응답 임시저장

### I-04 Brief 검토
- 사업 아이디어·사업 조건·규제 민감 정보
- 출처 Badge
- AI 제안 수정 가능
- Primary: `이 내용으로 컨셉 만들기`

### C-01 Workboard
- 상단 통과 0~5, 검사 후보 수
- 5 Slot 3+2 Grid
- 오른쪽 Timeline
- Slot 상태와 최근 갱신
- 완료 전 상세 Draft 비공개

### C-02 컨셉 상세
- 요약·사업 구조·법률 검토 Tab 또는 Section
- Evidence Accordion
- 인쇄 가능한 구조

### S-01 비교
- 카드 보기와 비교표 전환
- 설명형 Tag
- 5개 중 선택
- Mobile은 2개씩 비교 또는 Card Swipe

### M-03 변경안 검토
- 현재/제안/이유/영향/근거
- 채택·일부 채택·거절
- 일부 채택 편집
- 처리된 제안 필터

### MK-02 콘텐츠 제작
- Desktop 3열
- Source 요약 고정
- 유형·채널·목적·톤·길이 설정
- Preview·부분 편집·재생성·저장
- 법률 표현 경고

## 5. 상태와 Microcopy

### 공통 상태
- Initial Loading
- Empty
- Not Ready
- Ready
- Queued
- Running
- Needs Input
- Completed
- Failed Retryable
- Failed Permanent
- Stale
- Not Connected
- Offline

### 예시
- `이 작업은 화면을 벗어나도 계속됩니다.`
- `현재까지 통과된 결과는 보존되었습니다.`
- `외부 분석 모듈 연결 준비 중입니다.`
- `최신 확정 기획과 다른 입력으로 생성된 결과입니다.`

## 6. 의미 기반 Revision UI

- 선택한 원안
- 시장분석 제안
- 시장분석 반영안 — 타깃·운영모델 조정
- 최종 확정 기획
- 이전 기획

내부 번호는 보조 Metadata로만 표시한다.

## 7. Typography

- 프로젝트 제목 24~28
- 단계 제목 18~20
- Section 15~16
- Card 14~15
- 본문 13~14
- Helper 11.5~12.5

## 8. 접근성

- Tab 순서와 Focus 표시
- aria-live와 alert
- Accordion aria-expanded
- Modal Focus Trap·복원
- 상태를 색상만으로 표현하지 않음
- 44px Touch Target
- Reduced Motion
- Table의 Mobile Card 대체

## 9. 디자인 QA

- 390×844, 768×1024, 1280 이상
- 200% 확대
- 키보드 단독
- Screen Reader 기본 흐름
- 긴 한국어 줄바꿈
- 인쇄·PDF 캡처
