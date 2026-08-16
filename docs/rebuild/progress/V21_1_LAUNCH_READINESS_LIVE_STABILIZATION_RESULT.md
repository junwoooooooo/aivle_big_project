# V21.1 Launch Readiness Live Stabilization 결과

- 작업 상태: **FUNCTIONAL COMPLETE**
- 사용자 시각 검토: **USER REVIEW PENDING**
- 데이터 마이그레이션: **HOLD — Docker CLI 없음**
- START SHA: `6fa10aa52b9b58c7e315550faecebd8c2370600f`
- 기준 브랜치: `full`
- V27 migration 변경: 없음

## LIVE DEFECT MATRIX

| 실제 결함 | 원인 | 조치 | 상태 |
|---|---|---|---|
| 제공 Finance DOCX import 422 | 3개년 목표의 쉼표가 숫자 천 단위 구분자와 값 구분자로 동시에 사용됨 | 슬래시 형식을 권장하고 기존 쉼표 형식도 보수적으로 호환 | PASS |
| 실제 필드 오류가 generic 문구로 표시 | `FINANCIAL_INPUT_INVALID` frontend mapping 및 field error UI 부재 | 안전한 상단 문구와 필드별 오류 목록 추가 | PASS |
| PDF fetch 완료 후 Dialog 표시 | `openPreview`가 `await loader()` 이후에만 상태 생성 | 클릭 즉시 `LOADING` Dialog를 열고 비동기로 `READY/ERROR` 전환 | PASS |
| iframe blank/자동 다운로드 | browser-native PDF viewer가 브라우저 설정에 종속 | iframe 제거, PDF.js canvas viewer로 전환 | PASS |
| 유효하지 않은 PDF 다운로드 가능 | MIME/magic/크기 검증 없이 Blob 저장 | 다운로드 전 동일 PDF byte contract 검증 | PASS |
| Finance import replay 전 입력 상태 생성 | import가 artifact→preparation→snapshot→TaskRun 순으로 수행됨 | 프로젝트 잠금 및 command/document hash replay 확인을 mutation보다 먼저 수행 | PASS |

## FINANCE 422 ROOT CAUSE

기존 parser는 `threeYearTargets`를 `raw.split(",")`로 분리했다. 따라서 `1,000, 2,000, 4,000`은 세 값이 아니라 여섯 token으로 해석되었다. 또한 문서 전체의 `IOException`/`NumberFormatException` 경계에서 `FINANCIAL_INPUT_INVALID`를 만들었기 때문에 실패 필드와 이유가 사라졌다.

V21.1은 각 필드 변환을 독립 경계로 분리하고 `field`, 사용자 label, 안전한 message, 32자 이내 sanitized raw summary를 보존한다. 응답에는 원문 전체가 아닌 `field`와 안전한 message만 전달한다.

## PARSER BEFORE/AFTER

| 항목 | 이전 | V21.1 |
|---|---|---|
| 3개년 목표 | 쉼표 단순 분리 | `1,000 / 2,000 / 4,000` 권장, `100,200,400` 호환 |
| 금액 | 제한적 숫자 | `12000000`, `12,000,000`, `12 000 000`, `12,000,000원` |
| 비율 | 숫자 중심 | `3.5`, `3.5%` |
| 선택 항목 | 빈 값 중심 | 빈 값, 공백, 해당 없음, 없음, 미정, N/A, NA, `-`를 absent 처리 |
| 필수 항목의 빈 표현 | 포괄 오류 | 해당 field의 필수 오류 |
| revenue model | canonical enum 중심 | enum과 일회성/일회성 판매/구독/구독형/정기 구독/혼합/혼합형 alias |
| 오류 원문 | 전체 예외에서 소실 | bounded sanitized summary만 내부 보존 |

## REALISTIC DOCX MATRIX

| 검증 | 결과 |
|---|---|
| `service.template()`이 만든 실제 DOCX 재사용 | PASS |
| Word 사용과 같은 table input cell 수정 후 `service.parse()` | PASS |
| `[필수]`/`[선택]`, 작성 안내, 섹션, 예시 노출 | PASS |
| 쉼표 금액·공백 금액·원 suffix·percent suffix | PASS |
| 선택 항목 blank/해당 없음/N/A | PASS |
| 슬래시 3개년 목표 | PASS |
| legacy 쉼표 3개년 목표 | PASS |
| invalid/missing/duplicate/unknown field의 필드별 오류 | PASS |

## ERROR UX BEFORE/AFTER

| 상태 | 이전 | V21.1 |
|---|---|---|
| 상단 메시지 | 요청을 완료하지 못했습니다 | 재무 입력 문서를 확인해 주세요 |
| 필드 오류 | 미표시 | 한국어 label + 안전한 오류 목록 |
| 예시 | 없음 | `3개년 성장 목표: 1·2·3년차 값을 확인해 주세요.` |
| requestId | 화면 중심 노출 가능성 | 일반 사용자 UI에는 강제 노출하지 않음 |
| 관련 안전 오류 | 일부 generic fallback | preparation/snapshot/idempotency 오류도 사용자 문구 mapping |

Backend `BusinessException`과 `GlobalExceptionHandler`는 기존 API envelope의 `fieldErrors`를 보존한다.

## PDF PREVIEW ROOT CAUSE

기존 `openPreview`는 `await loader()`가 끝난 뒤 `{ title, filename, blob }`을 설정했다. 따라서 네트워크와 PDF 생성이 모두 끝날 때까지 Dialog가 보이지 않았다. 이후에도 Blob URL을 iframe에 전달했기 때문에 브라우저 PDF 다운로드 설정에 따라 빈 화면 또는 다운로드처럼 보일 수 있었다.

V21.1의 명시 상태는 다음과 같다.

1. 클릭 즉시 `LOADING` Dialog를 연다.
2. 인증 download 요청에는 `AbortSignal`을 전달한다.
3. 응답 Blob을 MIME/크기/magic으로 검증한다.
4. 성공 시 `READY`, 실패 시 `ERROR`로 전환한다.
5. Dialog를 닫으면 요청을 취소하고 token guard로 늦은 응답의 재오픈을 막는다.

## PDF VIEWER DECISION

- browser-native iframe을 preview authority에서 제거했다.
- `pdfjs-dist 6.2.108`(Apache-2.0)을 lazy import하여 페이지별 canvas로 렌더한다.
- PDF worker는 별도 asset으로 분리된다.
- production build 기준 PDF viewer chunk는 약 427.53 kB, gzip 약 127.42 kB이며 worker는 약 1.26 MB다.
- viewer가 렌더하지 못해도 byte 검증을 통과한 Blob만 명시적 다운로드 fallback으로 제공한다.
- `npm audit --omit=dev`에서 PDF.js 관련 production 취약점은 보고되지 않았다. 기존 lock에 있던 React Router 7.18.1 관련 high advisory 2건은 별도 dependency follow-up이다.

## PDF BYTE CONTRACT

READY 또는 download 이전에 모두 확인한다.

| 조건 | 계약 |
|---|---|
| Blob | 실제 `Blob` instance |
| size | 64 bytes 이상 |
| MIME | `application/pdf` |
| magic | 첫 5 bytes가 `%PDF-` |
| 실패 | iframe/canvas/download에 전달하지 않고 오류 UI |

## PDF HTTP CONTRACT

실제 renderer와 controller를 함께 사용한 MockMvc/HTTP 검증 결과다.

| 보고서 | HTTP 200 | application/pdf | `%PDF-` | PdfReader |
|---|---:|---:|---:|---:|
| Technology | PASS | PASS | PASS | PASS |
| Operations | PASS | PASS | PASS | PASS |
| Finance runtime result fixture | PASS | PASS | PASS | PASS |
| Technology + Operations | PASS | PASS | PASS | PASS |
| Technology + Finance | PASS | PASS | PASS | PASS |
| Technology + Operations + Finance | PASS | PASS | PASS | PASS |

생성 불가 상태는 non-2xx JSON이며 `application/pdf 200` 안에 JSON/HTML을 넣지 않는다.

## DOWNLOAD CONTRACT

- 미리보기 클릭은 다운로드 함수를 호출하지 않는다.
- PDF 다운로드 버튼은 READY Blob 또는 PDF.js 표시 실패 후에도 byte 검증을 통과한 Blob에만 표시한다.
- anchor를 DOM에 append한 뒤 click하고 즉시 제거한다.
- object URL은 30초 후 revoke하여 저장 시작 전에 URL이 해제되는 위험을 줄였다.
- DOCX 템플릿 다운로드와 PDF 다운로드는 서로 다른 함수/검증 계약을 사용한다.

## FINANCE IDEMPOTENCY

| 명령 | mutation 전 판정 | 결과 |
|---|---|---|
| 새 key | 프로젝트 lock 후 replay 없음 | artifact→parse→preparation/snapshot→TaskRun을 한 transaction에서 수행 |
| 같은 key + 같은 document hash | 기존 TaskRun과 원 snapshot 확인 | 기존 preparation/snapshot/action 반환, 새 artifact 없음 |
| 같은 key + 다른 document hash | 기존 TaskRun과 hash 불일치 | `IDEMPOTENCY_CONFLICT`, 새 입력 상태 없음 |
| 동시 요청 | project row lock | 두 번째 요청이 첫 명령 commit 후 replay를 재확인 |
| parse/start 실패 | outer transaction rollback | preparation/snapshot 부분 적용 없음, object storage rollback cleanup 유지 |

USER_DOCUMENT TaskRun의 idempotency scope는 snapshot UUID 대신 안정적인 `USER_DOCUMENT_INPUT` subject를 사용한다. `current()`는 TaskRun input의 snapshot ID를 현재 snapshot과 다시 대조하므로 과거 결과가 새 문서를 덮지 않는다.

## INDEPENDENCE MATRIX

| 시나리오 | Market 필요 | BM 필요 | 결과 |
|---|---:|---:|---|
| Project + Finance DOCX import/start | 아니오 | 아니오 | PASS |
| Project + Technology DOCX start | 아니오 | 아니오 | PASS |
| Project + Operations DOCX start | 아니오 | 아니오 | PASS |

Mini Product Authority, LaunchReadiness 단일 UX, Work Center, current/stale, USER_DOCUMENT authority를 변경하지 않았다.

## TEST MATRIX

| 영역 | 명령/테스트 | 결과 |
|---|---|---|
| Finance parser | `FinancialInputDocumentV21Tests` | PASS |
| Finance import/error/idempotency | `FinancialDocumentImportV21_1Tests` | PASS |
| Finance independence/authority | `FinancialUserDocumentAuthorityV21Tests` | PASS |
| Technology/Operations independence | `LaunchReadinessAsyncV21Tests` | PASS |
| PDF HTTP/reader/combinations/failure | `LaunchReadinessPdfHttpV21_1Tests` | PASS |
| Frontend error UI | `LaunchReadinessPage.v21-1.test.jsx`, `apiError.v21-1.test.js` | PASS |
| Preview state/cancel | `usePdfPreview.test.jsx` | PASS |
| PDF byte/download | `pdfBlob.test.js` | PASS |
| Binary API signal | `launchReadinessApi.test.js` | PASS |
| Frontend focused tests | 5 files, 11 tests | PASS |
| Changed frontend lint | ESLint | PASS |
| Frontend production build | Vite | PASS (기존 main chunk size warning 존재) |
| Backend build | `gradlew build -x test` | PASS |
| diff whitespace | `git diff --check` | PASS |

## REMAINING HOLD

1. 실제 Chrome/Edge에서 PDF canvas의 한글 줄바꿈·페이지 간격·모바일 Dialog와 명시적 다운로드 파일 열기를 사용자 검토해야 한다.
2. 실제 인증 데이터의 Technology/Operations/Finance/통합 PDF 시각 만족도는 **USER REVIEW PENDING**이다.
3. 현재 호스트에는 Docker CLI가 없어 empty PostgreSQL V1→V27 및 기존 V26→V27 upgrade 검증은 V21의 **DATA MIGRATION HOLD**를 유지한다.
4. production dependency audit에서 확인된 React Router advisory는 PDF.js 도입에서 발생한 항목이 아니며 별도 보안 dependency 업데이트로 처리해야 한다.

