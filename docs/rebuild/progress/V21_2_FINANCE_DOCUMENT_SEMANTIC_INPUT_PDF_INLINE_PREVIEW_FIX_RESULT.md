# V21.2 Finance Document Semantic Input 및 PDF Inline Preview 수정 결과

- 기능 상태: **COMPLETE**
- 사용자 시각 확인: **USER REVIEW PENDING**
- START SHA: `25712a84f800689d8167f11025099b4f5d494c7f`
- 기준 브랜치: `full`
- Mini Product Authority, Finance USER_DOCUMENT authority, TaskRun/JobEvent/Work Center 계약 변경 없음

## 완료 요약

실제 사용자 작성 DOCX를 그대로 회귀 fixture로 고정하고, 기존 3행 문서의 `주 값 (산정 근거)` 형식에서 주 값과 괄호 근거를 분리하도록 수정했다. 계산에는 주 값만 사용하며 근거는 `inputNotes`와 각 Finance 필드 provenance의 `userNote`로 보존한다.

Professional, Finance, Integrated PDF 응답은 모두 `application/pdf`와 `Content-Disposition: inline`을 사용한다. 미리보기는 인증 fetch로 받은 검증된 PDF bytes를 PDF.js canvas로 렌더하며, 명시적인 `PDF 다운로드` 버튼만 파일 저장 동작을 수행한다.

## 실제 사용자 DOCX 회귀 Fixture

| 항목 | 값 |
|---|---|
| 원본 | 사용자 Downloads의 `finance-readiness-input.docx` |
| 저장 위치 | `backend/src/test/resources/fixtures/finance/user-finance-readiness-input.docx.b64` |
| SHA-256 | `A66124E0302673F113682370FD2D558F9A8568A376A5B36CA1D885C81D7A02B8` |
| 호환 형식 | V21 기존 3행 테이블 |
| 회귀 결과 | 원본 bytes 복원 후 `service.parse()` 및 import/start orchestration PASS |

바이너리 fixture는 저장소의 텍스트 호환성과 원본 동일성 검증을 위해 Base64로 보관한다. 테스트에서 디코딩한 bytes의 SHA-256을 먼저 확인한 뒤 실제 parser를 실행한다.

## Finance 의미 입력 계약

| 입력 형태 | 주 값 | 보존 근거 | 결과 |
|---|---:|---|---|
| `160000000 (개발자 2명, 3~4인)` | `160000000` | `개발자 2명, 3~4인` | PASS |
| `24,000,000원 (산정 근거)` | `24000000` | `산정 근거` | PASS |
| `5000 (산정 근거)` | `5000` | `산정 근거` | PASS |
| `4.5 (산정 근거)` | `4.5` | `산정 근거` | PASS |
| `HYBRID (수익구조 설명)` | `HYBRID` | `수익구조 설명` | PASS |
| `100,300,900 (성장 근거)` | `100 / 300 / 900` | `성장 근거` | PASS |
| `1,000 / 2,000 / 4,000 (성장 근거)` | `1000 / 2000 / 4000` | `성장 근거` | PASS |

괄호 안 숫자는 계산 문자열에 연결하지 않는다. 특히 `160000000 (개발자 2명, 3~4인)`은 `160000000234`가 될 수 없도록 회귀 assertion을 추가했다.

## 사용자 Fixture 기대값

| 필드 | 파싱 결과 |
|---|---:|
| annualFixedLaborCost | 160000000 |
| annualFixedRentAndManagementCost | 24000000 |
| annualFixedInfrastructureCost | 15000000 |
| initialDevelopmentAndRnDCost | 90000000 |
| initialEquipmentAndInfrastructureCost | 20000000 |
| initialPatentAndLicensingCost | 6000000 |
| threeYearTargets | 100 / 300 / 900 |
| totalMarketingCost | 30000000 |
| totalSalesCost | 18000000 |
| newCustomerCount | 5000 |
| revenueModel | HYBRID |
| unitPrice | 500 |
| monthlySubscriptionPrice | 2000000 |
| monthlyChurnRate | 4.5 |
| unitVariableCost | 350 |
| paymentFee | 50 |
| partnerPayout | 0 |
| shippingCost | 0 |
| customerIncrementalInfraCost | 100 |

19개 값 모두 실제 fixture에서 정확히 일치했다. 19개 산정 근거도 별도 `inputNotes`로 보존된다.

## 새 템플릿과 기존 문서 호환

새 Finance 템플릿은 각 필드를 다음 구조로 생성한다.

1. fieldKey
2. 입력값 · 필수/선택
3. 입력값 셀
4. 산정 근거 · 선택
5. 산정 근거 셀

Parser는 5행 문서에서는 두 셀을 직접 분리하고, 기존 V21 3행 문서에서는 마지막 괄호만 보수적으로 분리한다. 수익 모델의 `정기 구독 (SUBSCRIPTION)`처럼 괄호가 canonical enum 표시인 경우에는 근거로 오인하지 않는다.

## 산정 근거 보존 계약

| 위치 | 저장 내용 |
|---|---|
| 파싱 결과 | 최상위 `inputNotes[fieldKey]` |
| Finance field provenance | `fields[fieldKey].userNote` |
| USER_DOCUMENT lineage | `upstreamReferences.userDocument.normalizedValues.inputNotes` |
| 계산 authority | 기존 `fields[fieldKey].value`만 사용 |

Finance 계산·판정 알고리즘과 canonical 값 구조는 변경하지 않았다.

## PDF Inline 응답 계약

| Endpoint 종류 | Content-Type | Content-Disposition | 결과 |
|---|---|---|---|
| Professional Technology/Operations | `application/pdf` | `inline; filename="..."` | PASS |
| Finance | `application/pdf` | `inline; filename="finance-readiness-report.pdf"` | PASS |
| Integrated | `application/pdf` | `inline; filename="..."` | PASS |
| Professional DOCX template | DOCX MIME | `attachment` | PASS |
| Finance DOCX template | DOCX MIME | `attachment` | PASS |

PDF endpoint의 attachment 응답을 제거했으며 DOCX 템플릿 다운로드 계약은 유지했다.

## PDF 미리보기와 다운로드 분리

| 사용자 행동 | 네트워크/브라우저 행동 |
|---|---|
| PDF 미리보기 클릭 | Dialog 즉시 열림, 인증 fetch 수행, download anchor 0회 |
| 유효 bytes 수신 | MIME/size/`%PDF-` 검증 후 PDF.js canvas 렌더 |
| PDF.js 렌더 성공 | 최소 1개 page canvas 표시 |
| PDF 다운로드 클릭 | 검증된 Blob에 한해 anchor 생성·click·제거 |

iframe, `window.location`, preview 경로의 `anchor.click()`을 사용하지 않는다. PDF.js 구조는 그대로 유지했다.

## 오류 구분

| 실패 지점 | 사용자 문구 | 다운로드 버튼 |
|---|---|---|
| PDF fetch 실패 | 보고서를 불러오지 못했습니다. | 없음 |
| MIME/size/magic 불일치 | 생성된 보고서 형식을 확인할 수 없습니다. | 없음 |
| PDF.js 렌더 실패 | 보고서는 생성되었지만 미리보기를 표시하지 못했습니다. | 유지 |

렌더 실패는 bytes 검증까지 통과한 상태이므로 정상 Blob을 내려받아 확인할 수 있다. fetch 실패와 invalid bytes는 다운로드 대상이 없다.

## 회귀 보호

| 계약 | 결과 |
|---|---|
| Technology 독립 분석 | PASS |
| Operations 독립 분석 | PASS |
| Finance USER_DOCUMENT authority | PASS |
| Market/BM 없는 Finance 실행 | PASS |
| Finance command idempotency | PASS |
| TaskRun/JobEvent/Work Center | 변경 없음 |
| 개별/통합 PDF | PASS |

## 테스트 매트릭스

| 영역 | 검사 | 결과 |
|---|---|---|
| 실제 사용자 DOCX | 원본 SHA + 19개 값 + 19개 note + import/start 전달 | PASS |
| Legacy/New template | 3행/5행 parser | PASS |
| Finance authority | note provenance + Market/BM 독립성 | PASS |
| Finance import/idempotency | 기존 V21.1 회귀 | PASS |
| Professional 독립성 | Technology/Operations | PASS |
| PDF HTTP | Professional/Finance/Integrated inline | PASS |
| PDF bytes | `%PDF-` + 실제 PdfReader open | PASS |
| PDF.js | 1페이지 canvas 렌더 | PASS |
| Preview | 즉시 loading, fetch/invalid/render 오류 구분 | PASS |
| Download separation | preview anchor 0회, explicit download만 click | PASS |
| 프런트 집중 테스트 | 5 files, 15 tests | PASS |
| 백엔드 집중 테스트 | 5 classes, 22 tests | PASS |
| 변경 프런트 파일 lint | ESLint | PASS |
| 프런트 production build | Vite | PASS |
| 백엔드 production build | Gradle | PASS |
| diff whitespace | `git diff --check` | PASS |

전체 저장소 ESLint는 이번 변경과 무관한 기존 `useMarketingVisual.test.jsx`의 `global` 미정의 2건 때문에 실패한다. V21.2 변경 파일 lint는 오류 없이 통과했다.

## USER REVIEW PENDING

자동 기능 검증은 완료했다. 실제 Chrome/Edge 인증 화면에서 다음 시각 항목은 사용자 확인 대상으로 남긴다.

1. 새 Finance DOCX의 입력값/산정 근거 셀 구분과 Word 편집성
2. PDF.js 페이지 선명도, 스크롤, 긴 통합 보고서 체감
3. fetch 실패·invalid bytes·render 실패 문구의 실제 배치
4. 명시적 다운로드 파일의 운영체제 PDF reader 열기
