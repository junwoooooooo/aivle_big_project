# V19 Legal 결과 중복 제거·인접 Decision 탐색·후보 가시성·Validation Prep·Idea 재제출 결과

## 완료 상태

- 자동 기능 검증: **COMPLETE**
- 사용자 시각·배포 확인: **USER REVIEW PENDING**
- AI Core, Legal 판단 로직, Market/BM 계산 엔진, Hypothesis canonical, Market Seed handoff: 변경 없음
- Backend product code/API/DB migration: 변경 없음

## START SHA

| 항목 | 값 |
| --- | --- |
| branch | `full` |
| 시작 HEAD | `991b78edd378d0ac423e7cd61aee3e97aa282fc2` |
| fetched `origin/full` | `991b78edd378d0ac423e7cd61aee3e97aa282fc2` |
| 시작 작업트리 | clean |

## IDEA RESUBMIT ROOT CAUSE

사용자가 본 `입력 내용으로 아이디어 확장하기` 문구는 시작 시점의 `full` source와 기존 `frontEnd/dist` 어디에도 없었다. 현재 source CTA는 `입력 내용으로 사업안 만들기`였으며, 기존 빌드도 같은 현재 경로를 포함했다. CONFIRMED → edit → submit 시나리오를 현재 코드에서 재현했을 때 form submit, validation, derive 요청, DERIVING reconciliation은 정상 동작했다.

따라서 실제 no-op의 확인 가능한 원인 분류는 **F. stale frontend/build mismatch**다. 현재 배포가 fetched `full`과 다른 artifact 또는 cache를 사용했을 가능성이 높다. 코드 측에서는 다음 두 취약점을 함께 보강했다.

| 구간 | 이전 | V19 |
| --- | --- | --- |
| CTA | 입력 내용으로 사업안 만들기 | 입력 내용으로 아이디어 정리하기 |
| submit 직후 | RUNNING 전환 | RUNNING 전환 + stage top reset |
| pending | attachment upload만 별도 busy | `isOrganizing`, disabled, `아이디어를 정리하고 있습니다...` 계약 추가 |
| derive 실패 | FAILED 화면에서 refresh 시 수정 draft를 서버 값으로 덮을 수 있음 | READY 입력 화면으로 복귀, 오류 표시, 수정 draft 보존 |
| server | 기존 `forkConfirmed()` 계약 | 통합 테스트로 새 brief, 수정 seed, DERIVING TaskRun 고정 |

재제출 테스트는 derive 1회, 수정 payload, 새 active job, terminal refresh 후 READY_FOR_REVIEW를 검증한다. Backend 테스트는 확정 snapshot ID가 그대로 남고 새 current brief만 mutable하게 생성되는 것을 검증한다.

## LEGAL DUPLICATION ROOT CAUSE

V18 화면의 `사업 진행 전 확인할 내용`이 `requiredControls`, `requiredDisclosures`, partner 계열, `unknownFacts`를 먼저 출력한 뒤, 바로 아래 `특히 확인할 사항`이 같은 source를 다시 출력했다. 또한 advertising 고지와 일반 고지, 세 partner 배열은 단순 연결만 하고 중복 제거를 하지 않았다. PDF도 3번 실행 체크사항과 6/7/8번 상세 section에서 같은 문장을 반복했다.

## LEGAL BEFORE/AFTER MATRIX

| 영역 | 이전 | V19 |
| --- | --- | --- |
| 일반 UI primary | 사업 진행 전 확인 + 특히 확인할 사항 | 특히 확인할 사항만 유지 |
| 필수 고지 | 일반/광고에 동일 문장 반복 | 일반 고지는 한 번, 광고 고유 고지만 별도 표시 |
| partner | 세 source 배열 단순 연결 | 세 source 통합 후 안정적 dedupe |
| 빈 그룹 | 그룹마다 해당 사항 없음 반복 | 일반 빈 그룹 숨김, unknown 없음만 의미 있게 표시 |
| PDF 3번 | 실행 문장 전체 반복 | 주요 검토 결과 요약 건수만 표시 |
| PDF 6/7/8 | 원문 배열 | dedupe된 실제 문장 한 번만 표시 |

V17의 `한눈에 보는 검토 결과 → 특히 확인할 사항 → 사업 구조에서의 역할 → 관련 법률·규제 → 광고·표현 주의사항 → 상세 근거` 계층을 primary authority로 복원했다.

## LEGAL DEDUPE CONTRACT

- `uniqueLegalItems()`는 문자열을 trim하고 내부 whitespace를 한 칸으로 정규화한다.
- object는 `safeSummary`, 다음으로 `title`을 사용자 문장 key로 사용한다.
- 처음 등장한 원본 item을 보존하므로 Backend JSON과 official evidence는 변경하지 않는다.
- partner 계열 세 source는 하나의 group 안에서 dedupe한다.
- 광고 고지는 일반 `requiredDisclosures` key와 비교해 광고에만 추가되는 문장만 `광고에서 함께 표시할 내용`으로 출력한다.
- PDF 요약은 controls/disclosures/partners/unknownFacts의 dedupe 후 건수만 계산하며 새 AI 요약을 생성하지 않는다.

## ADJACENT NAV CONTRACT

| server authority | local `decisionView` | 허용 탐색 | API |
| --- | --- | --- | --- |
| BUSINESS_BASIS | BUSINESS_BASIS | 선택 변경/기준 편집 | 기존 domain action만 |
| LEGAL_REVIEW | LEGAL_REVIEW | 분석 기준 확정으로 돌아가기 | 없음 |
| LEGAL_REVIEW | BUSINESS_BASIS review | 선택 변경, 법률 결과로 돌아가기 | 순수 view 이동은 없음 |
| VALIDATION_PREP | LEGAL_REVIEW | 법률 결과 확인, 준비 화면 복귀 | 순수 view 이동은 없음 |

서버 `selection.status`와 `businessDecisionStage()`는 변경하지 않았다. 로컬 `decisionView`는 인접 section을 읽기 위한 presentation 상태다. 서버 단계가 실제로 변하거나 selection identity가 바뀔 때만 local view를 새 authority에 맞춘다. Legal/Validation 화면에는 선택 변경을 직접 노출하지 않는다.

## CANDIDATE VISIBILITY MATRIX

| request | primary UI | secondary transparency | raw provider question |
| --- | --- | --- | --- |
| supported field 1개 이상 | compact candidate + 필요 시 editor | 없음 | 미노출 |
| unsupported | primary card/form 없음 | `이번에 이어서 검토하지 못한 사업안 N개` disclosure | 미노출 |
| ANSWERED retry + supported field | 기존 continuation retry 계약 유지 | 없음 | 미노출 |

미지원 detail은 사업안명과 generic limitation만 보여준다. 후보마다 큰 오류 card나 답할 수 없는 form은 만들지 않았다.

## ENGINE FOLLOW-UP BOUNDARY

V19는 `affectedFields`가 현재 `CANDIDATE_FACT_FIELDS`에 연결되는 경우만 이어서 검토한다. 미지원 질문에 사용자가 실제 답변하고 candidate patch와 legal continuation까지 수행하려면 candidate schema, affectedFields, continuation model, prompt/validation, backend serialization을 함께 확장하는 별도 ENGINE 작업이 필요하다. generic free-text field나 provider 질문 번역을 임의로 추가하지 않았다.

## VALIDATION PREP BEFORE/AFTER

| 구분 | 이전 | V19 |
| --- | --- | --- |
| helper | header 한 문장 + 별도 2문단 aside | header의 정확한 한 문단으로 통합 |
| action | form 하단 저장, page 하단 back | 상단 Back/저장 action bar |
| submit | form 내부 하단 CTA | 외부 button `form` attribute로 같은 form submit/Enter 계약 유지 |
| 자원 | 좁은 sidebar 성격 | full-width compact surface, desktop 3열 |
| responsive | desktop split 후 mobile 1열 | desktop 3열, <=900px 2열, <=768px 1열 |
| input 대비 | 흰 editor 일부 | numeric/editor 모두 흰 surface + 명시 border/focus ring |
| 사업안 초안 | newline paragraph | list로 scan 가능, 사용 action 후에만 draft 반영 |

## BM SUGGESTION AUTHORITY

- `key_activities`, `key_resources`, `key_partners`의 기존 deterministic concept-derived 초안을 유지한다.
- 초안은 선택한 사업안에서 가져온 material이며 AI 추천으로 표시하지 않는다.
- 사용자가 `이 내용 사용`을 눌러야 draft에 반영되고 이후 수정값이 최종 authority다.
- `customer_relationship` 자동 suggestion은 추가하지 않았다.
- budget/months/team은 explicit 저장값만 사용하며 추정하지 않는다.
- 저장 순위 `사용자 수정값 > 저장된 BmPlan > 사용자가 적용한 concept-derived draft`를 유지한다.

## SCROLL MATRIX

| 전환 | 결과 |
| --- | --- |
| Legal → 분석 기준 Back | page top |
| 분석 기준 review → Legal | page top |
| Validation Prep → Legal | query 제거 + Legal view + page top |
| Idea submit → RUNNING | stage top |
| V18 route/query/market/legal PDF 전환 | 기존 계약 유지 |
| reduced motion | 기존 `scrollPageToTop`의 auto 계약 유지 |

## TEST MATRIX

| 검증 | 결과 |
| --- | --- |
| CONFIRMED → edit → modified derive payload 1회 | PASS |
| submit 즉시 RUNNING/activeJobId | PASS |
| terminal refresh → READY_FOR_REVIEW | PASS |
| derive 실패 draft/error 보존 | PASS |
| Backend confirmed fork + 새 DERIVING TaskRun | PASS |
| Legal controls/disclosure/partner 중복 1회 | PASS |
| 일반/광고 고지 cross-dedupe | PASS |
| PDF section 3 count-only + 상세 문장 1회 | PASS |
| Legal에서 selection change 미노출 | PASS |
| Legal ↔ Basis local navigation API 0회 | PASS |
| supported 1 / unsupported 1 가시성 | PASS |
| provider raw English 미노출 | PASS |
| Validation top action/form association | PASS |
| 하단 duplicate primary 미존재 | PASS |
| 자원 input width/min-width/background/focus 계약 | PASS |
| V18 Work Center/monotonic/exact-two/scroll 관련 회귀 | PASS |
| Frontend Vitest | 11 files, 111 tests PASS |
| Backend Idea integration target | PASS, Gradle BUILD SUCCESSFUL |
| 변경 JS/JSX ESLint | PASS, warning 0 |
| `git diff --check` | PASS |

Production build와 authenticated browser는 AGENTS.md fast execution 규칙 및 V19 완료 조건에 따라 실행하지 않았다. 실제 배포 artifact/cache 확인은 사용자 검증 항목으로 남긴다.

## 변경 파일

- Backend test: `IdeaBriefCanonicalizationIntegrationTests.java`
- Idea: `useIdeaIntake.js`, `IdeaIntakeForm.jsx`, `IdeaIntakePage.jsx`, 관련 tests/CSS
- Business Proposal: `BusinessProposalWorkspace.jsx`, `legalReportPresentation.js`, 관련 test/CSS
- Legal PDF: `LegalRegulatoryReportDocument.jsx`, test, print CSS
- Validation Prep/BM: `BusinessValidationPreparation.jsx`, `BmPlanForm.jsx`, test/CSS
- 문서: 본 RESULT와 V19 USER VERIFICATION

## USER REVIEW ITEMS

- 실제 서비스가 `991b78e…` 이후 V19 source로 다시 build/deploy됐는지와 브라우저 cache가 갱신됐는지
- CONFIRMED Idea 화면의 CTA가 `입력 내용으로 아이디어 정리하기`로 보이고 재제출이 RUNNING으로 즉시 전환되는지
- Legal 문장 중복이 실제 report data에서도 제거됐는지
- Legal ↔ 분석 기준 이동 시 page top과 읽기 흐름이 자연스러운지
- 미지원 후보 disclosure의 정보 밀도가 충분한지
- 1440px/390px에서 Validation 자원 input 대비와 overflow가 적절한지

## 남은 위험과 정확한 연속 지점

- 현재 source와 기존 local dist에는 사용자 관찰 문구가 없었으므로 실제 운영 no-op의 최종 폐쇄에는 배포 artifact/version과 cache 확인이 필요하다.
- 기능 계약은 source, component/state test, Backend integration test 기준 COMPLETE다.
- authenticated visual과 실제 배포 확인은 `USER REVIEW PENDING`이다.
- 후속 시각 피드백은 기존 selector authority를 직접 수정하고 override-only CSS를 추가하지 않는다.
