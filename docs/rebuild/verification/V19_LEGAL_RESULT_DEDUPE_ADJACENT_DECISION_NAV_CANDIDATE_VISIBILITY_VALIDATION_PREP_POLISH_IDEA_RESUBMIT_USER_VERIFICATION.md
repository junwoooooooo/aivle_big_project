# V19 사용자 검증 가이드

## 준비

1. 배포된 frontend가 V19 결과를 포함하는지 확인하고 브라우저 hard refresh를 수행한다.
2. 확정된 Idea, 법률 보고서가 준비된 selection, supported/unsupported candidate가 함께 있는 프로젝트를 준비한다.
3. Desktop 1440px 이상과 Mobile 390px에서 각각 확인한다.

## 1. Idea 확정 후 재제출

1. 확정된 Idea 화면에서 `아이디어 수정`을 누른다.
2. 필수 입력 중 하나를 눈에 띄게 수정한다.
3. CTA가 `입력 내용으로 아이디어 정리하기`인지 확인한다.
4. submit한다.

기대 결과:

- 화면이 즉시 `아이디어를 정리하고 있습니다` 실행 경험으로 바뀐다.
- 페이지 중간 scroll이 남지 않고 stage top에서 시작한다.
- Work Center에 새 Idea 정리 Job이 연결된다.
- 완료 후 수정한 값이 Review에 반영된다.
- 요청이 실패하면 입력 화면에 원인이 표시되고 수정값이 사라지지 않는다.

사용자 화면에 `입력 내용으로 아이디어 확장하기`가 남아 있으면 현재 배포 artifact/cache가 V19 source와 일치하지 않는 상태다.

## 2. Legal 중복

1. 동일한 고지/partner 문장이 여러 backend source에 들어간 법률 결과를 연다.
2. `특히 확인할 사항`, `광고·표현 주의사항`, 관련 법률, 상세 근거를 확인한다.

기대 결과:

- `사업 진행 전 확인할 내용` block은 없다.
- `특히 확인할 사항`이 primary section이다.
- 같은 조치, 고지, partner 문장은 한 번만 보인다.
- 일반 필수 고지와 같은 광고 고지는 광고 section에서 반복되지 않는다.
- 광고에만 추가되는 고지는 `광고에서 함께 표시할 내용`에 보인다.
- 빈 일반 group마다 `해당 사항이 없습니다`가 반복되지 않는다.

## 3. Legal PDF

1. 법률·규제 보고서 PDF route를 연다.
2. 3번 section과 6/7/8번 section을 확인한다.
3. Print Preview도 확인한다.

기대 결과:

- 3번 제목은 `주요 검토 결과 요약`이며 조치/고지/partner/추가 확인 건수만 보인다.
- 실제 문장은 6/7/8번에 한 번만 보인다.
- 공식 법령 링크, skip-link redaction, V18 기본 제안 파일명은 유지된다.

## 4. 인접 Decision 탐색

1. Legal 결과 화면을 연다.
2. `분석 기준 확정으로 돌아가기`를 누른다.
3. 전체 기준값을 확인한다.
4. `법률·규제 결과로 돌아가기`를 누른다.

기대 결과:

- Legal 화면에는 `선택 변경`이 없다.
- Back 후 분석 기준은 read-only 전체 보기로 보인다.
- 분석 기준 review에서는 `선택 변경`이 허용된다.
- Back/Forward는 API 실행 없이 같은 route의 view만 바꾼다.
- 이동할 때마다 page top에서 시작한다.

## 5. Candidate 가시성

1. supported 후보 1개와 unsupported 후보 1개가 있는 상태를 연다.

기대 결과:

- `추가 정보가 있으면 검토를 이어갈 수 있는 사업안`에는 supported 후보만 보인다.
- unsupported 후보의 큰 오류 card와 답변 form은 없다.
- 아래에 `이번에 이어서 검토하지 못한 사업안 1개` disclosure가 보인다.
- 펼치면 generic 한국어 제한 설명만 보이고 provider raw English 질문은 보이지 않는다.

## 6. Validation Prep

1. Legal에서 `시장 분석 준비하기`를 누른다.
2. 상단 helper/action과 운영 정보 form을 확인한다.

기대 결과:

- helper는 한 문단이고 별도 큰 reason aside가 없다.
- 상단 왼쪽에 `법률·규제 결과로 돌아가기`, 오른쪽에 `저장하고 계속`이 있다.
- form 하단에 duplicate 저장 CTA가 없다.
- Enter submit이 유지된다.
- `현재 사용할 수 있는 자원`은 Desktop 3열, 좁은 화면 2열/1열로 바뀐다.
- numeric input은 흰 surface, 보이는 border, focus ring을 가진다.
- 390px horizontal overflow가 없다.
- 선택 사업안 초안은 목록으로 보이며 `이 내용 사용` 전에는 저장 draft에 자동 적용되지 않는다.

## 보호 회귀

- Work Center 상세 직접 열기
- Concept execution monotonic phase
- 사업안 1개 비교 UI 숨김과 exact-two compare
- Legal evidence grouping/official links/PDF title
- selection identity 재선택
- Scroll-to-top
- Hypothesis canonical, Market Seed, BM user authority

위 항목에 이상이 있으면 화면, viewport, project ID, 현재 selection status, 재현 순서를 함께 기록한다.
