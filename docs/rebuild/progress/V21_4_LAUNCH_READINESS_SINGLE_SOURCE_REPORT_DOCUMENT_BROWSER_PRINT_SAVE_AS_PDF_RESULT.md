# V21.4 Launch Readiness 단일 보고서 문서 및 브라우저 PDF 저장 결과

- 기능 상태: **COMPLETE**
- 사용자 화면·인쇄 검토: **USER REVIEW PENDING**
- START SHA: `a08a9ccac4a5bcee07db1e68b781f9e02414e4ca`
- 기준 브랜치: `full`
- 시작 작업 트리: clean
- 실제 fetch 후 `HEAD == origin/full`

## 결과 요약

Launch Readiness의 사용자-facing 보고서 authority를 React Report Document 하나로 통일했다. 이제 `보고서 보기`는 독립 report route로 이동하고, `PDF로 저장`은 같은 DOM에서 `window.print()`만 실행한다. 일반 UI는 Professional, Finance, Integrated Backend PDF endpoint를 호출하지 않는다.

기존 OpenPDF renderer와 PDF HTTP endpoint는 API compatibility, automated artifact, legacy external integration을 위해 삭제하지 않았다. Backend PDF generator 내용과 계산 로직도 변경하지 않았다.

## REPORT AUTHORITY BEFORE / AFTER

| 항목 | V21.3 | V21.4 |
|---|---|---|
| 화면 보고서 | React Preview Document | React Report Document |
| 미리보기 위치 | Dialog | canonical report route |
| PDF 저장 source | 별도 Backend OpenPDF | 화면과 동일 React DOM + print CSS |
| 사용자 PDF 요청 | 명시적 download에서 PDF GET | PDF GET 0회, `window.print()` |
| 화면/저장 일치 | renderer가 달라 불일치 가능 | 동일 component·동일 DOM·동일 CSS |
| PDF.js | 제거 상태 | 재도입 없음 |

## SINGLE-SOURCE INVARIANT

| 보고서 | 화면 component | print source |
|---|---|---|
| Technology | `LaunchReadinessReportDocument` | 동일 component |
| Operations | `LaunchReadinessReportDocument` | 동일 component |
| Finance | `FinanceReadinessReportDocument` | 동일 component |
| Integrated | `IntegratedLaunchReadinessReportDocument` | 동일 component와 위 개별 component 조합 |

별도 `PreviewDocument`, `PrintDocument`, `ReportPreviewDialog`은 남기지 않았다.

## ROUTE MATRIX

| 보고서 | canonical route |
|---|---|
| Technology | `/app/projects/:projectId/launch-readiness/reports/technology` |
| Operations | `/app/projects/:projectId/launch-readiness/reports/operations` |
| Finance | `/app/projects/:projectId/launch-readiness/reports/finance` |
| Integrated | `/app/projects/:projectId/launch-readiness/reports/integrated?modules=technology&modules=finance` |

Report route는 Project Layout 안의 출시 준비 Journey로 인식된다. 진입 시 page top으로 이동하며 상단에 `출시 준비로 돌아가기`와 `PDF로 저장`을 제공한다. 브라우저 Back도 일반 route navigation으로 동작한다.

## PROFESSIONAL PDF PARITY MATRIX

| Backend `LaunchReadinessPdfService` 의미 | React authority | 결과 |
|---|---|---|
| 표지·분석 기준일·입력 문서 | cover metadata | PASS |
| 종합 준비도·판정 | AI 평가와 판정 summary | PASS |
| 독립 검증 | `quality.passed === true` 조건부 문구 | PASS |
| 1. 경영진 요약 | section 1 | PASS |
| 2. 평가 입력 근거 | section 2 table | PASS |
| 3. 영역별 점수·상태·근거 | section 3 | PASS |
| 4. 위험·영향·대응 | section 4 | PASS |
| 5. Gate·기준·증빙 | section 5 | PASS |
| 6. 과제·담당·완료 증빙 | section 6 | PASS |
| 7. 사업 적용 결론 | section 7 | PASS |
| 8. 외부 참고 출처 | section 8 또는 통합 마지막 section | PASS |
| 문서 한계 | document footer | PASS |

## AI SCORE TRANSPARENCY

- 주 점수는 계속 `analysis.score`만 사용한다.
- `AI 출시 준비도 평가 82점`으로 표시한다.
- `작성한 전문 계획을 바탕으로 AI가 평가한 준비도이며 정해진 산식의 점수가 아닙니다.`를 함께 표시한다.
- Dimension도 `AI 평가 N점`으로 명시한다.
- `quality.passed === true`일 때만 `독립 AI 검증 통과`를 표시한다.
- `reviewScore` 숫자, raw prompt, provider 정보, internal ID/hash는 사용자 문서에 표시하지 않는다.
- timer 기반 가짜 percentage는 추가하지 않았다.

## FINANCE REPORT PARITY MATRIX

| Backend `FinancialAnalysisPdfService` 의미 | React authority | 결과 |
|---|---|---|
| 표지·사용자 문서 기준·기준일 | cover metadata | PASS |
| 1. 핵심 결과 | BEP, 회수, 운전자금, 총 영업이익 | PASS |
| 2. 3개년 추정 손익 | authoritative annual projections table | PASS |
| 3. 월별 매출·영업이익·누적 현금흐름 | print-safe SVG + 전체 월별 table | PASS |
| 4. 스트레스 시나리오 | stored scenario table | PASS |
| 5. Monte Carlo | stored simulations·확률·P10/P50/P90 | PASS |
| 6. AI 해석·핵심 발견·주의·권장 조치 | report 값 그대로 | PASS |
| 7. 사업 적용 결론 | stored calculation summary/report headline | PASS |
| disclaimer | document footer | PASS |

Finance renderer는 business 값을 다시 계산하지 않는다. SVG 좌표 정규화와 숫자 표시는 presentation 작업이며, source는 저장된 `cashFlowChart`다.

## FINANCE COMPLETED TIME

기존 Finance current DTO에 실제 완료 시각이 없었으므로 `AnalysisView.completedAt`을 additive field로 추가했다. DB migration 없이 현재 `TaskRun.finishedAt`을 사용한다. Technology/Operations는 기존 `completedAt`을 그대로 사용한다.

## INTEGRATED REPORT

- 두 개 이상 완료된 current 보고서를 선택하면 integrated route로 이동한다.
- 통합 표지 다음에 선택한 Technology, Operations, Finance의 동일 document component를 선택 순서대로 렌더한다.
- 개별 Professional 문서의 외부 출처 반복은 생략하고 마지막 `통합 외부 참고 출처`에서 URL 기준으로 중복 제거한다.
- 통합 문서도 한 번의 `window.print()`만 사용한다.
- stale 결과는 기본 보고서 authority로 사용하지 않는다.

## PRINT CONTRACT

| 항목 | 계약 |
|---|---|
| 저장 동작 | `window.print()` 정확히 1회 |
| Backend PDF GET | 0회 |
| 용지 | `@page { size: A4; }` |
| 숨김 | app topbar, maintenance banner, project header, journey substeps, status error, skip-link, scroll-to-top, report action bar |
| 문서 source | 화면의 동일 `.launch-report-document` DOM |
| page break | cover, embedded report, heading, summary, row, action, chart에 print break contract 적용 |
| 링크 | 보고서 출처 링크는 문서에 유지 |

## PRINT FILE NAME CONTRACT

| 종류 | suggested `document.title` |
|---|---|
| Technology | `{프로젝트명}_기술_출시준비_보고서_{YYYYMMDD_HHmm}` |
| Operations | `{프로젝트명}_운영_출시준비_보고서_{YYYYMMDD_HHmm}` |
| Finance | `{프로젝트명}_재무_출시준비_보고서_{YYYYMMDD_HHmm}` |
| Integrated | `{프로젝트명}_출시준비_통합보고서_{YYYYMMDD_HHmm}` |

Windows 금지 문자 `< > : " / \ | ? *`를 제거하고 공백을 `_`로 정규화한다. print 직전에 `document.title`을 설정하며 `afterprint`에서 원래 title을 복원한다. 실제 저장 파일명 확정은 브라우저 Save-as-PDF dialog의 authority다.

## BACKEND COMPATIBILITY

- `LaunchReadinessPdfService` 변경 없음.
- `FinancialAnalysisPdfService` 변경 없음.
- integrated OpenPDF bundle service 변경 없음.
- 기존 PDF HTTP endpoint와 `%PDF-`/PdfReader 회귀 테스트를 삭제하지 않았다.
- 일반 사용자 화면만 Backend PDF renderer에서 분리했다.

## 변경 파일

### Backend

- Finance current DTO에 `completedAt` additive field
- `TaskRun.finishedAt` 매핑
- PDF HTTP fixture constructor 갱신

### Frontend

- canonical report route와 route helper
- Project Journey/report route 인식
- Technology/Operations 단일 Report Document
- Finance 단일 Report Document와 print-safe SVG
- Integrated Report Document와 source consolidation
- print filename/`window.print()` helper
- report screen/print stylesheet
- Launch Readiness 본문의 Dialog preview를 report navigation으로 교체
- V21.3 Preview/Dialog component 제거

## TEST MATRIX

| 검증 | 결과 |
|---|---|
| Frontend V21.4·라우트·V21.1 회귀 집중 테스트 | PASS · 7 files, 41 tests |
| Technology/Operations 보고서 보기 PDF GET 0회 | PASS |
| Finance 보고서 보기 PDF GET 0회 | PASS |
| Integrated 보고서 보기 bundle GET 0회 | PASS |
| `window.print()` 1회·title 복원 | PASS |
| Professional 8개 section + disclaimer | PASS |
| Finance 7개 section + SVG | PASS |
| 통합 source consolidation | PASS |
| print exclusion source contract | PASS |
| 변경 범위 ESLint | PASS |
| Frontend production build | PASS |
| Backend PDF compatibility 집중 테스트 | PASS · 4 classes |
| V21.1/V21.2 Finance parser·import·authority 회귀 | PASS · 3 classes, 14 tests |
| Backend production build | PASS |
| `git diff --check` | PASS |

전체 frontend lint는 이번 변경과 무관한 기존 항목 때문에 실패했다.

- `useMarketingVisual.test.jsx`: `global` 미정의 2건
- Market/Twin polling hook: 불필요 dependency warning 2건

변경 파일을 대상으로 한 ESLint는 오류 없이 통과했다.

## BROWSER CHECK

인앱 브라우저에서 `/app/projects/41/launch-readiness/reports/technology` 접근을 확인했으나 인증 세션이 없어 `/auth/login`으로 이동했다. Chrome 확장 브라우저 연결도 사용할 수 없었다. 자격증명을 입력하거나 우회하지 않았으며 실제 current 보고서의 화면·Print Preview는 **USER REVIEW PENDING**으로 남긴다.

## 보호 계약

- Mini Product Authority 유지
- Technology/Operations Professional AI 결과 계약 유지
- Finance USER_DOCUMENT authority·parser·idempotency 유지
- TaskRun·JobEvent·Work Center 유지
- AI score transparency 유지
- current/stale 유지
- Backend 개별·통합 PDF compatibility 유지
- PDF.js 재도입 없음
- 새 AI 호출 없음

## 남은 사용자 검토

1. 인증 프로젝트에서 Technology, Operations, Finance 개별 report route의 실제 긴 데이터 밀도 확인
2. 통합 보고서에서 개별 문서 page break와 외부 출처 통합 확인
3. Chrome/Edge Print Preview에서 app chrome·skip-link·버튼 미출력 확인
4. Save as PDF 후 한글 줄바꿈, SVG 선명도, 표 header 반복, source link 클릭 확인
5. 브라우저가 제안하는 파일명이 `document.title` 계약과 일치하는지 확인

## 정확한 후속 시작점

사용자 인증 화면에서 위 다섯 항목을 검토하고, 시각적인 page break나 표 밀도 문제만 print CSS로 조정한다. 분석·계산·Backend PDF generator 변경으로 확장하지 않는다.
