# V18 사업안 실행 상세·후보 연속 검토·스크롤·PDF·사업 검증 준비 UX 결과

## 완료 상태

- 자동 기능 검증: **COMPLETE**
- 사용자 시각 검토: **USER REVIEW PENDING**
- AI Concept generation core, Legal 판단 알고리즘, Market Research canonical input, BM 계산·판정 core: 변경 없음
- DB migration: 없음

## START SHA

| 항목 | 값 |
| --- | --- |
| branch | `full` |
| 시작 HEAD | `bfe786b5371e9db8bd494a3c8da737fcbd2a5d97` |
| fetched `origin/full` | `bfe786b5371e9db8bd494a3c8da737fcbd2a5d97` |
| 시작 작업트리 | clean |

## WORK CENTER DETAIL ROOT CAUSE

| 구분 | V17 | V18 |
| --- | --- | --- |
| 페이지 호출 | `outlet.openWorkCenterJob?.(jobId)` | 동일한 명시적 command 사용 |
| Outlet 제공 | command 없음 | `ProjectChromeContext`의 tool action registry를 `ProjectLayout` Outlet에 노출 |
| Sheet authority | `ProjectContextTools` 내부 | 그대로 유지 |
| 상세 열기 | 버튼은 보이지만 no-op | 기존 단일 Work Center를 `view=detail`, `focusJobId=jobId`로 직접 열기 |
| 중복 JobCenter | 없음 | 없음 |
| 버튼 문구 | 전체 처리 기록 보기 | 작업센터에서 상세 기록 보기 |

`ProjectContextTools.openSheet(jobId)`를 정식 tool action으로 등록했다. 페이지는 새 JobCenter를 렌더하지 않으며, command와 유효한 job ID가 모두 있을 때만 상세 버튼을 표시한다.

## EXECUTION PHASE BEFORE/AFTER

| 구분 | 이전 | V18 |
| --- | --- | --- |
| 단계 | 방향 구성 → 생성 → 법률 → 차별성 → 준비 | 조건 확인 → 탐색·구체화 → 법률·규제 검토 → 결과 정리 |
| 매핑 | 최신 이벤트 regex | 실제 `messageKey` allowlist |
| 차별성 확인 | 독립 순차 단계 | 탐색·구체화 내부 activity |
| 현재 단계 | 최신 이벤트 단계 | 지금까지 도달한 가장 높은 macro milestone |
| 반복 trace | Legal 뒤 proposal이면 뒤로 이동 | Legal 유지 |
| unknown | regex 우연 매칭 가능 | `결과를 준비하고 있습니다.` fallback |
| FAILED/NEEDS_INPUT | 최신 단계 영향 | 도달 단계 유지 + 상태 메시지 |

## SUMMARY METRIC CONTRACT

- 정본 이벤트는 `messageKey === "job.concept-portfolio.summary"`이다.
- `reviewed`, `prepared`, `needsInput` 중 실제 숫자가 있는 값만 사용한다.
- 예: `5개 검토 · 1개 준비 · 2개 추가 확인`.
- summary 이전의 `producedConceptCount=0`, `openInputCount=0`은 metric을 렌더하지 않는다.
- 실행 초기의 `0개 사업안 · 추가 검토 0건` 표현을 제거했다.

## SINGLE CANDIDATE COMPARISON CONTRACT

| concepts 수 | 비교 picker | 카드 checkbox | 비교 진입 |
| --- | --- | --- | --- |
| 0 | 없음 | 없음 | 불가 |
| 1 | 없음 | 없음 | 불가 |
| 2 이상 | 표시 | 표시 | 정확히 2개 선택 시 가능 |

V15/V16의 exact-two 제한은 그대로 유지한다.

## CANDIDATE INPUT UX MATRIX

| 상태 | 기본 화면 | 사용자 action | 편집 화면 |
| --- | --- | --- | --- |
| supported OPEN | 사업안명, 한 줄 설명, 확인 내용, 확인 이유 | 정보 입력해서 검토 계속 | 해당 field만 명확한 흰 surface 편집기 |
| 여러 supported field | 질문 목록 compact 표시 | 한 candidate 편집기 열기 | 허용된 field를 함께 제출 |
| ANSWERED + RETRY_CONTINUATION | 재반영 실패 안내 | 추가 사업정보 반영 다시 시도 | 같은 사실 재입력 없음 |
| unsupported | 입력 형식으로 연속 검토가 어렵다는 안내 | 준비된 다른 사업안 선택/보류 | form 없음 |

Field metadata는 label, 사용자 질문, 도움말, type을 함께 가진다. seller/provider/intermediary/transaction/payment/partner/privacy/physical activity 문구를 실제 사업 역할 중심으로 교체했다.

## UNSUPPORTED INPUT CONTRACT

- `affectedFields`가 지원 field와 연결되지 않으면 textarea, 제출 버튼을 렌더하지 않는다.
- provider raw question, `unknownFacts`, 영어 질문을 primary UI에 표시하지 않는다.
- `Concept의 사업 구조를 보완해야 합니다.` 같은 developer wording을 표시하지 않는다.
- candidate 안에서 `portfolio.start`를 호출하던 `다른 방향 다시 탐색`을 제거했다.
- Core prompt와 continuation API 계약은 변경하지 않았다. 새 field/한국어 provider 질문 생성은 `ENGINE FOLLOW-UP` 대상이다.

## SCROLL RESET MATRIX

| 전환 | 방식 |
| --- | --- |
| 사업안 선택/현재 선택으로 계속 → 기준값 | 서버 selection identity 확인 후 page top + 현재 section focus |
| 현재 값으로 진행 → 법률 결과 | decision stage 증가 감지 후 page top |
| 시장 분석 준비하기 → `?view=validation-prep` | query 변경과 action에서 page top |
| 법률 PDF route | route manager + report mount에서 cover top |
| 시장 분석 시작하기 → market route | route manager + link action에서 page top |
| Project Settings overlay 열기/닫기 | background scroll 보존 |
| reduced motion | `auto` |

AppShell route manager는 pathname/search 전환만 reset하고 `backgroundLocation` overlay 열기와 복귀는 제외한다.

## SCROLL TOP CONTROL

- 공통 `ScrollToTopButton`을 프로젝트 layout에 추가했다.
- `scrollY >= 700`이고 document가 viewport보다 긴 경우만 표시한다.
- `chevronUp` AppIcon, `aria-label="페이지 맨 위로 이동"`을 사용한다.
- desktop 우측/하단 여백과 mobile safe area를 적용한다.
- Work Center보다 낮은 z-index이며 print에서는 숨긴다.

## PDF PRINT REDACTION

| 요소 | screen | print |
| --- | --- | --- |
| 보고서 문서 | 표시 | 표시 |
| app topbar/project header/substeps | 표시 | 숨김 |
| print action bar | 표시 | 숨김 |
| skip-link | 접근성용 표시 | 숨김 |
| Scroll-to-top | 조건부 표시 | 숨김 |
| raw enum/hash | 숨김 | 숨김 |

기존 A4, page break, 공식 법령 링크 계약을 유지했다.

## PDF FILE NAME CONTRACT

- `LegalReportView.generatedAt`을 `ConceptLegalRegulatoryReport.getCreatedAt()`에서 제공한다.
- DB migration은 없다.
- 제안 제목: `{정리된_사업안명}_법률규제_사전검토_보고서_{YYYYMMDD_HHmm}`.
- Windows 금지 문자 `< > : " / \ | ? *`는 공백으로 치환하고 공백은 `_`로 정규화한다.
- 긴 사업안명은 72자로 제한한다.
- print 직전에 `document.title`을 설정하고 `afterprint`에서 원래 제목을 복원한다.
- Browser Save-as-PDF의 **default suggested filename**이며 OS 저장 파일명을 강제로 보장하지 않는다.
- 예: `스마트_식단_관리_서비스_법률규제_사전검토_보고서_20260814_1658`.

## LEGAL EXECUTION GUIDE MATRIX

| 사용자 묶음 | 기존 Legal Report source |
| --- | --- |
| 필요한 조치 | `requiredControls` |
| 고객에게 안내할 내용 | `requiredDisclosures`, `advertisingExpressionCautions.requiredDisclosures` |
| 외부 협의·자격 | `partnerRequirements`, `qualificationRequirements`, `requiredPartnersAndQualifications` |
| 피해야 할 표현 | `prohibitedVariants` |
| 추가 확인사항 | `unknownFacts` |

동일 helper를 화면의 `사업 진행 전 확인할 내용`과 PDF의 3번 `실행 체크사항`에서 사용한다. severity나 우선순위는 생성하지 않았고 새 AI/법률 조언 호출도 추가하지 않았다.

## VALIDATION PREP COPY MATRIX

| 구분 | 이전 | V18 |
| --- | --- | --- |
| heading | 시장 분석 이후에 사용할 운영 정보를 미리 정리하세요 | 사업 검증에 사용할 운영 정보를 준비하세요 |
| 역할 설명 | 시장 분석 이후 사용 | 시장 규모·경쟁 확인 뒤 같은 결과로 사업 모델 캔버스를 구성할 때 사용 |
| optional | section/field 반복 | form 상단에서 모든 항목이 선택 입력임을 한 번 안내 |
| 정확성 | Market direct input처럼 오해 가능 | BM Plan이 Market canonical input이 아님을 반영 |

## BM PLAN SOURCE/PREFILL MATRIX

| field | 저장 authority | 선택 사업안 초안 | 자동 적용 |
| --- | --- | --- | --- |
| customer_relationship | 기존 BmPlan | 없음 | 없음 |
| key_activities | 기존 BmPlan | `operatingModel + transactionFlow` | 없음, `이 내용 사용` 필요 |
| key_resources | 기존 BmPlan | `platformRole + featureSet` | 없음, `이 내용 사용` 필요 |
| key_partners | 기존 BmPlan | `partnerModel + partnerRequirements` | 없음, `이 내용 사용` 필요 |
| budget_krw/months/team | 기존 explicit constraints | 없음 | 추정 금지 |

우선순위는 `사용자 수정값 > 저장된 BmPlan > 사용자가 적용한 concept-derived draft`다. 선택 사업안 초안 적용 후에도 수정할 수 있고, 저장 시 기존 `BmPlanPreparation`이 최종 authority다.

## INPUT VISUAL MATRIX

| 영역 | 읽기 상태 | 편집 상태 |
| --- | --- | --- |
| Candidate | subtle tonal parent | 흰 surface, 강화된 neutral border, brand focus ring |
| BM operation | tinted row + 현재값/초안 surface | 필요한 row만 흰 editor |
| BM resources | 하나의 resource surface | 3개 compact numeric field, mobile 1열 |
| Decision summary | very-light tonal + subtle border | 현재 full workspace와 1.75rem 이상 간격 |

## TEST MATRIX

| 검증 | 결과 |
| --- | --- |
| Work Center `job-123` direct-open detail/focus | PASS |
| 4단계 exact messageKey mapping | PASS |
| Legal → proposal monotonic 유지 | PASS |
| summary 5/1/2 metric | PASS |
| early 0/0 metric 미노출 | PASS |
| single candidate comparison UI 미노출 | PASS |
| supported compact → editor → submit | PASS |
| unsupported raw English/form/start action 미노출 | PASS |
| selection forward top reset | PASS |
| route/query reset + settings overlay 보존 | PASS |
| Scroll-to-top threshold/reduced motion | PASS |
| print skip-link redaction | PASS |
| filename sanitizer/title restore | PASS |
| Legal execution guide source 재사용 | PASS |
| BM 3개 concept draft / customer·numeric 미추론 | PASS |
| concept draft 적용 → 사용자 수정 → 저장 | PASS |
| 프런트 대상 Vitest | 9 files, 81 tests PASS |
| Backend selection service 대상 test | PASS |
| 변경 JS/JSX ESLint | PASS |
| Vite production build | PASS, 281 modules |
| `git diff --check` | 문서 작성 후 최종 재실행 |

Production build의 기존 500kB 초과 chunk 경고는 남아 있으며 V18 범위의 신규 오류는 아니다. 전체 regression, authenticated browser, 실제 OS print dialog/PDF 저장은 완료 정책에 따라 실행하지 않았다.

## 변경 파일

- Backend: `ConceptPortfolioSelectionApiModels.java`, `ConceptPortfolioSelectionService.java`, `ConceptPortfolioSelectionServiceP5Tests.java`
- App shell: `AppShell.jsx`, `ProjectChromeContext.jsx`, `ProjectContextTools.jsx`, `ProjectLayout.jsx`, `project-shell.css`, `ProjectContextTools.test.jsx`
- Shared UI: `ProjectExecutionExperience.jsx`, `ScrollToTopButton.jsx`, `ScrollToTopButton.test.jsx`, `scroll.js`, `index.js`
- Business Proposal: `businessProposalExecution.js`, `businessProposalExecution.test.js`, `businessProposalModel.js`, `legalReportPresentation.js`, `BusinessProposalWorkspace.jsx`, `BusinessProposalWorkspace.test.jsx`
- Legal/Validation components: `BusinessValidationPreparation.jsx`, `BusinessValidationPreparation.test.jsx`, `LegalRegulatoryReportDocument.jsx`, `LegalRegulatoryReportDocument.test.jsx`, `LegalRegulatoryReportPage.jsx`
- Business Proposal CSS: `business-proposal.css`, `business-validation-preparation.css`, `legal-regulatory-report.css`
- BM Plan: `BmPlanForm.jsx`, `BmPlanPhase.test.jsx`, `bmPlan.js`, `bmPlan.test.js`, `market.css`
- 문서: 본 V18 결과 문서와 `V18_BUSINESS_PROPOSAL_EXECUTION_DETAIL_CANDIDATE_CONTINUATION_SCROLL_PDF_VALIDATION_PREP_UX_USER_VERIFICATION.md`

## USER VISUAL REVIEW ITEMS

- 실행 중 4단계 rail이 실제 trace 반복에도 자연스럽게 보이는지
- Work Center Sheet가 상세 job으로 바로 열리고 기존 Quick/Full 동작과 충돌하지 않는지
- 1개 사업안에서 비교 도구가 완전히 사라지는지
- candidate compact card와 열린 textarea의 대비가 충분한지
- rationale, 선택 요약, 기준값, 법률 surface 사이 간격과 대비가 충분한지
- 긴 페이지에서 Scroll-to-top이 Work Center/모바일 safe area와 겹치지 않는지
- 실제 Chrome/Edge Save-as-PDF에서 skip-link가 없고 제안 파일명이 적용되는지
- 사업 검증 준비의 초안/현재값/editor가 1440px, 390px에서 읽기 쉬운지

## 남은 위험과 정확한 연속 지점

- 기능 계약은 자동 검증 기준 COMPLETE다.
- authenticated browser visual 및 실제 OS PDF 결과는 `USER REVIEW PENDING`이다.
- 브라우저가 Save-as-PDF 파일명을 자체 정책으로 바꿀 수 있으므로 앱은 default suggested filename만 제공한다.
- provider가 unsupported field를 계속 생성하는 문제를 줄이려면 후속 `ENGINE FOLLOW-UP`에서 한국어 질문/field 확장 Task 계약을 별도로 설계해야 한다.
- 사용자 시각 피드백이 오면 이번 selector authority를 직접 조정하고 override-only CSS를 추가하지 않는다.
