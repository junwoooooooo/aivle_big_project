# V17 사업 기획 마무리·법률 보고서·사업 검증 준비 결과

## 완료 상태

- 기능 구현: **COMPLETE**
- 자동 검증: **PASS**
- 디자인 및 실제 PDF 수용성: **USER REVIEW PENDING**
- AI core, Concept 생성, Legal 판단, Market Research canonical input, TaskRun 및 Market Seed hash 계약은 변경하지 않았다.

## START SHA

| 항목 | 값 |
| --- | --- |
| branch | `full` |
| 시작 HEAD | `c3e5b0e62f1911c48e538fef7347ec4e2581273a` |
| fetched `origin/full` | `c3e5b0e62f1911c48e538fef7347ec4e2581273a` |
| 작업트리 | 시작 시 clean |

V16의 exact-two 비교, 후보별 preview, 재선택 identity, Decision Flow, 법령 grouping, bounded layout, tonal control, Hypothesis canonical contract를 보호했다.

## CURRENT SELECTION RETURN FIX

| 흐름 | 이전 | V17 |
| --- | --- | --- |
| A 선택 후 `선택 변경` | A 카드가 `선택됨` disabled | `현재 선택으로 계속` action |
| A로 복귀 | 방법 없음 | gallery 접기, BROWSE 복귀, 현재 Decision section 포커스 |
| selection API | 같은 A도 action을 제공하려면 재호출 필요 | 호출 0회 |
| selection authority | 변경 위험 | 기존 A selection·revision 그대로 유지 |
| 다른 B 선택 | V16 서버 refresh identity 계약 | 그대로 유지 |

현재 선택 label과 하단의 중복 `선택됨` 문구를 제거하고, 카드 상단 상태와 하단 행동을 구분했다.

## DECISION SPACING MATRIX

| 구분 | 간격/구조 |
| --- | --- |
| 선택 요약 → 현재 단계 | `.business-decision-stack`, `1.75rem` |
| 기준값 요약 → 법률 workspace | `.business-decision__current`, `1.75rem` |
| 내부 section | 약 `.75~1.25rem` |
| 구현 방식 | 공통 stack authority 사용, 개별 margin hack 반복 없음 |

## BASIS CONFIRM FLOW BEFORE/AFTER

| 단계 | 이전 | V17 |
| --- | --- | --- |
| 편집 완료 action | 기준값 확인 완료 | 기준값 확정 |
| backend action | confirm hypotheses | 동일 |
| 처리 중 | 일반 busy | `기준값을 확인하고 있습니다...` |
| confirm 완료 | 곧바로 법률 단계처럼 보임 | 7/7 read mode 유지 |
| 다음 action | 불명확한 연속 흐름 | `현재 값으로 진행` |
| 보고서 생성 | 법률 화면의 완료 버튼 | 기준값 단계의 두 번째 action에서 `finalizeReport` |
| editor | 내부 state가 남을 수 있음 | decision/finalValue identity 변경 시 editor만 닫고 draft state는 보존 |

`READY_FOR_LEGAL_REPORT`는 이제 `BUSINESS_BASIS` presentation에 남는다. 사용자가 7개 확정값을 읽은 뒤 별도 action으로 최종 법률 보고서를 준비한다.

## DELTA LEGAL FLOW

| 서버 상태 | 화면 |
| --- | --- |
| `DELTA_LEGAL_PENDING` | 기준값 화면 유지, `기준값은 확정됨`, 변경 기준 영향 확인 안내 |
| `DELTA_LEGAL_FAILED` | 기존 `법률·규제 재검토 다시 시도` 유지 |
| `READY_FOR_LEGAL_REPORT` | `현재 값으로 진행` 활성 |
| `LEGAL_REPORT_READY` | 법률 보고서 즉시 표시 |

Delta 실행·retry·stale/current 의미는 변경하지 않았다.

## LEGAL STATUS LANGUAGE MATRIX

| raw enum | 사용자 표현 |
| --- | --- |
| `IMPLEMENTABLE` | 현재 조건으로 진행 가능 |
| `IMPLEMENTABLE_WITH_CONTROLS` | 필요한 조치를 반영하면 진행 가능 |
| `NEEDS_FACTS` | 추가 정보 확인 필요 |
| `REDESIGNABLE` | 일부 구조 조정 후 진행 가능 |
| `REJECTED` | 현재 형태로 진행하기 어려움 |

`CONDITIONAL` 호환값도 통제 반영 표현으로 매핑한다. 모르는 값은 raw enum 대신 `검토 결과 확인 필요`로 표시한다. 법적 보장·합법·승인 표현은 추가하지 않았다.

## LEGAL UI REDACTION MATRIX

| 데이터 | 일반 UI | 전용 문서 | backend JSON |
| --- | --- | --- | --- |
| `contentHash` | 숨김 | 숨김 | 유지 |
| `sourceHashes` | 기술정보 disclosure 제거 | 제외 | 유지 |
| TaskRun/internal enum | 제외 | 제외 | 유지 |
| bounded provision summary | `주요 내용` | `주요 내용` | 유지 |
| `officialSourceUri` | 원문 link | 원문 link | 유지 |
| safe summary/limitations | 표시 | 표시 | 유지 |

법령 원문을 새로 fetch하거나 전체 조문이라고 오인시키지 않는다.

## LEGAL PDF DOCUMENT MATRIX

| 항목 | 구현 |
| --- | --- |
| 방식 | 전용 React document + `window.print()` + Browser Save-as-PDF |
| route | `/app/projects/:projectId/concepts/legal-report` |
| dependency | 신규 PDF engine 없음 |
| 용지 | `@page size: A4`, `18mm 16mm 20mm` |
| 표지 | 프로젝트명, 선택 사업안, 사업 분야, 기준일, report ID |
| 본문 | 요구된 14개 numbered section |
| 법령 | 법률명 grouping, 조항별 제목·요약·시행일·공식 link |
| page break | 법률/조항 `break-inside: avoid-page` |
| print 제외 | app topbar, project header, substeps, button toolbar, 기술값 |
| 데이터 authority | 현재 finalized Legal Report JSON만 사용 |

Korean font embedding을 요구하는 binary generator는 추가하지 않았다. 사용자 UI의 `법률·규제 보고서 PDF`는 전용 문서를 열고 브라우저의 PDF 저장을 사용한다.

## CURRENCY DISPLAY MATRIX

| canonical | 한국어 읽기 |
| --- | --- |
| `50,000,000 KRW` | `5천만 원` |
| `50,000,000 USD` | `5천만 달러` |
| `50,000,000 JPY` | `5천만 엔` |
| `50,000,000 EUR` | `5천만 유로` |
| `125,000,000 USD` | `1억 2천5백만 달러` |

formatter는 만·억·조를 deterministic하게 지원한다. 숫자나 통화를 환산하지 않으며 canonical value는 그대로 함께 표시한다.

## FLOW RATIONALE COPY MATRIX

| 위치 | 설명 |
| --- | --- |
| Decision Progress 아래 | 선택 사업안을 일관된 기준으로 분석하는 이유와 조건 변경이 법률에 미치는 영향 |
| 분석 기준 heading | 시장 규모·경쟁 환경을 같은 기준으로 보기 위해 지역·가격·수익 방식·목표를 확인 |
| 법률 heading | 확정한 가격·제공 방식·지역의 법률·규제 영향을 마지막으로 확인 |
| 사업 검증 준비 | 시장 분석 input과 BM 운영 정보의 역할이 다름을 명시 |

Decision label은 `사업안 선택 → 분석 기준 확정 → 법률·규제 확인 → 사업 검증 준비`로 정리했다.

## BM PLAN SOURCE MATRIX

| field | canonical source | 자동 prefill | AI 제안 |
| --- | --- | --- | --- |
| customer_relationship | 기존 `BmPlanPreparation.plan` | revision이 있으면 그대로 | FOLLOW_UP |
| key_activities | 기존 `BmPlanPreparation.plan` | revision이 있으면 그대로 | FOLLOW_UP |
| key_resources | 기존 `BmPlanPreparation.plan` | revision이 있으면 그대로 | FOLLOW_UP |
| key_partners | 기존 `BmPlanPreparation.plan` | revision이 있으면 그대로 | FOLLOW_UP |
| budget_krw | 기존 `BmPlanPreparation.constraints` | 명시적으로 저장된 값만 | 금지 |
| months | 기존 `BmPlanPreparation.constraints` | 명시적으로 저장된 값만 | 금지 |
| team | 기존 `BmPlanPreparation.constraints` | 명시적으로 저장된 값만 | 금지 |

선택 사업안/Idea에서 동일 의미의 직접 canonical field를 안전하게 대응하는 현행 seam은 없었다. 새 LLM Task나 숫자 parser를 추가하지 않았다.

## BM PLAN PREFILL POLICY

- GET `/api/v3/projects/{projectId}/business-model/plan`을 재사용한다.
- `revision > 0`이면 저장된 plan/constraints를 그대로 prefill하고 `기존 정보에서 가져옴`의 근거로 삼는다.
- revision 0이면 빈 optional form을 제공한다.
- AI 제안처럼 거짓 label을 붙이지 않는다.
- 저장 시에만 PATCH 결과가 BM Plan authority가 된다.
- 숫자는 사용자가 입력하거나 이미 저장한 정수만 사용한다.

## BUSINESS VALIDATION PREP FLOW

1. 법률 보고서가 준비된 상태에서 `시장 분석 준비하기`를 누른다.
2. 동일 사업안 route의 안정적인 `?view=validation-prep` subview로 이동한다.
3. 기존 BM Plan을 불러온다.
4. 좌측 사업 운영 4개, 우측 사용 가능 자원 3개를 모두 선택 입력으로 제공한다.
5. `저장하고 계속`은 먼저 BM Plan PATCH를 실행한다.
6. 저장 성공 후에만 기존 `finalizeMarketSeed()`를 호출한다.
7. 저장 실패 시 handoff를 호출하지 않고 입력과 오류를 유지한다.
8. 완전한 빈 plan도 확인 후 `{ plan:{}, constraints:{} }`로 저장하고 진행할 수 있다.
9. `MARKET_SEED_FINALIZING`에서는 raw event 대신 compact 진행 안내를 표시한다.
10. `READY_FOR_MARKET`에서는 네 단계 완료와 `시장 분석 시작하기`를 표시한다.

## MARKET SEED CONTRACT PRESERVATION

- 기존 `finalizeMarketSeed`/`BUILD_HANDOFF` 흐름을 그대로 사용한다.
- BmPlan을 `MarketAnalysisSeedSnapshot`에 삽입하지 않았다.
- Market Research canonical input과 source hash를 변경하지 않았다.
- TaskRun/SSE/current/stale 계약을 변경하지 않았다.
- BM Plan은 별도 durable preparation으로 유지된다.

## BM DUPLICATE INPUT REMOVAL

| BM 진입 상태 | V17 동작 |
| --- | --- |
| plan revision 0, result 없음 | 기존 optional 운영 정보 form 접근 |
| plan revision > 0, result 없음 | compact 저장 요약 + `준비 정보 보기·수정` + `캔버스 만들기` |
| result 존재 | 캔버스 기본 표시, `운영 정보 수정` 허용 |

사업 검증 준비에서 저장한 운영 정보를 BM 단계에서 자동으로 다시 묻지 않는다. 수정 권한은 유지한다. 사용자-facing `BM 분석` CTA는 `사업 모델 검토`로 바꿨다.

## 변경 파일

- 사업안 모델/workspace/test/CSS
- 사업 검증 준비 component/test/CSS
- 법률 전용 document/page/test/print CSS
- project route·Journey/module route mapping과 테스트
- 기존 BM Plan form/model/page/CSS와 테스트
- 시장 분석의 다음 단계 문구
- 본 결과 문서 및 사용자 검증 문서

백엔드 파일과 데이터베이스는 변경하지 않았다.

## TEST MATRIX

| 검증 | 결과 |
| --- | --- |
| 현재 선택으로 복귀 시 select API 0회 | PASS |
| V16 다른 사업안 재선택 identity | PASS |
| 기준값 확정 → 7/7 read mode → 현재 값으로 진행 | PASS |
| editor identity 변경 자동 close | PASS |
| Delta pending/failed/ready flow | PASS |
| Legal report ready 즉시 표시 | PASS |
| 5개 Legal enum 사용자 매핑 | PASS |
| 일반 Legal UI hash/기술정보 제거 | PASS |
| 전용 문서 14 section·grouping·official link | PASS |
| A4/print chrome hidden source contract | PASS |
| 만·억·조 통화 formatter | PASS |
| BM revision prefill·중복 form 제거 | PASS |
| BM save 실패 시 handoff 0회 | PASS |
| 빈 optional plan 진행 | PASS |
| route/Journey/module mapping | PASS |
| 대상 Vitest | 9 files, 85 tests PASS |
| 변경 JS/JSX ESLint | PASS |
| Vite production build | PASS, 278 modules |
| `git diff --check` | PASS |

전체 regression suite, authenticated browser, 실제 OS print dialog/PDF 저장은 지침과 V17 정책에 따라 실행하지 않았다. 빌드의 기존 500kB 초과 chunk 경고는 남아 있다.

## USER VISUAL REVIEW ITEMS

- 1440×900/1920×1080에서 선택 요약과 현재 단계 사이 호흡이 충분한지
- 편집 surface가 부모 tonal surface와 명확히 구분되는지
- 시장 목표의 목표·기간·근거·계산 기준을 편집하지 않고 읽을 수 있는지
- 법률 header의 PDF/시장 준비 action이 본문보다 앞에서 발견되는지
- 390×844에서 structured read/editor와 prep 60/40 layout이 1열로 자연스럽게 전환되는지
- 실제 브라우저 Save-as-PDF에서 A4 여백, 한글 font, page break, clickable official link가 정상인지
- 긴 실제 법률 근거와 BM 운영 문장에서 밀도가 과하지 않은지

## 남은 위험과 정확한 연속 지점

- 기능 계약은 완료됐다. 시각 및 실제 PDF 출력 수용성은 사용자 검토 대기다.
- PDF는 binary download가 아니라 Browser Save-as-PDF다.
- 정성 BM field를 위한 안전한 AI proposal seam은 없으므로 `FOLLOW_UP`으로 남겼다.
- 실제 print 결과에서 브라우저별 한글 줄바꿈 차이가 발견되면 `legal-regulatory-report.css`의 print 규칙만 조정하면 된다.
- 다음 작업은 사용자 시각 검토 피드백 또는 별도 승인된 AI suggestion seam 설계에서 시작한다.
