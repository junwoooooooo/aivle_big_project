# V20 사용자 검증 가이드

## 준비

1. V20 frontend를 배포한 뒤 hard refresh한다.
2. 법률 보고서와 시장 분석 준비가 완료된 실제 프로젝트를 연다.
3. Desktop과 390px mobile에서 확인한다.
4. PDF 검증은 Chrome 또는 Edge의 `인쇄 → PDF로 저장`을 사용한다.

## 1. 풍부한 Legal 결과

법률·규제 확인 단계를 연다.

기대 결과:

- 순서가 `한눈에 보는 검토 결과 → 특히 확인할 사항 → 사업 구조 검토 → 관련 법률·규제 → 광고·표현 주의사항 → 상세 검토 내용`이다.
- 사업 구조 검토에서 값이 있는 플랫폼/판매/제공/중개 역할과 거래/결제/개인정보/물리 활동을 바로 볼 수 있다.
- 관련 법률은 법률명 하나 아래 조항이 묶이고, 요약·시행일·법령 원문 링크가 보존된다.
- 상세 검토 내용은 앞 section의 같은 문장을 다시 나열하지 않는다.

## 2. 의미 중복 제거

`공급업체와의 계약`과 `공급업체와의 계약이 필요함.`처럼 끝 표현만 다른 데이터가 있는 보고서를 확인한다.

기대 결과:

- 같은 요구는 한 번만 보인다.
- 개인정보 수집 동의와 제3자 제공 동의처럼 실질적으로 다른 요구는 각각 보인다.
- 일반 필수 고지와 같은 광고 고지는 광고 section에서 반복되지 않는다.
- 광고에만 있는 고지는 `광고에서 함께 표시할 내용`에 보인다.

## 3. Decision 1·2·3·4 탐색

READY_FOR_MARKET 상태에서 `4 → 3 → 2 → 1 → 3` 순으로 rail의 단계 이름을 누른다.

기대 결과:

- 네 단계가 모두 클릭 가능하다.
- 클릭할 때마다 page top에서 시작한다.
- 1단계는 현재 선택을 강조한 조회 화면이며, 클릭만으로 선택 변경이나 API 실행이 시작되지 않는다.
- 실제 변경은 별도 `선택 변경`을 눌러야 한다.
- 2단계에는 확정 기준 전체와 시장 목표의 점유율·기간·근거·시장 규모·통화·계산 기준이 보인다.
- 3단계에는 full Legal 결과가 보인다.
- 4단계에는 저장한 고객 관계·핵심 활동·핵심 자원·파트너와 예산·기간·인원이 보인다.
- 미도달 단계는 클릭되지 않는다.
- mobile rail은 가로 overflow 없이 사용할 수 있다.

## 4. Validation Prep 진입

LEGAL_REPORT_READY 상태에서 `시장 분석 준비하기`를 누른다.

기대 결과:

- 4단계가 현재 단계가 된다.
- 저장 form이 열리고, 법률 결과로 돌아갈 수 있다.
- READY_FOR_MARKET 후 4단계를 다시 열면 저장한 내용은 read-only review로 보이고 `시장 분석 시작하기`가 있다.

## 5. Legal PDF

법률·규제 보고서 PDF를 열고 Print Preview를 확인한다.

기대 결과:

- 3번 `주요 검토 결과 요약`은 `항목 / 건수`의 2열 table이다.
- 필요한 조치, 필수 고지, 파트너·자격, 추가 확인이 A4 폭에서 겹치지 않는다.
- summary count와 6/7/8번 실제 문장 수의 semantic dedupe 결과가 일치한다.
- 한글이 셀 밖으로 넘치지 않는다.
- summary table이 중간에서 불필요하게 분리되지 않는다.
- 본문 바로가기, topbar, 화면 button은 PDF에 나오지 않는다.
- 법령 원문 링크는 클릭할 수 있다.
- 저장 대화상자의 제안 파일명에 사업안명과 생성시각이 들어간다.

## 보호 회귀

- V19 Idea 확정 후 수정·재제출
- V18 Work Center 상세 직접 열기
- Concept execution monotonic progress
- exact-two comparison과 single candidate 비교 숨김
- selection identity 재선택
- Legal evidence grouping/official links/generatedAt title
- Scroll-to-top
- Hypothesis canonical, Market Seed handoff, BM Plan user authority

문제가 있으면 project ID, selection status, 현재 단계, viewport, PDF 브라우저/버전, 재현 순서와 화면을 함께 기록한다.
