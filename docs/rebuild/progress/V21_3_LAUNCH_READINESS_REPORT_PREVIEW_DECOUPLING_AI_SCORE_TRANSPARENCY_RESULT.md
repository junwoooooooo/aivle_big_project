# V21.3 Launch Readiness Report Preview Decoupling 및 AI Score Transparency 결과

- 기능 상태: **COMPLETE**
- 사용자 인증 화면 검토: **USER REVIEW PENDING**
- START SHA: `e2adb7bd10878d10cdedc5d14388ed7efbe9bc43`
- 기준 브랜치: `full`
- 시작 작업트리: clean
- Mini Product Authority, Finance USER_DOCUMENT authority, TaskRun/JobEvent/Work Center 계약 변경 없음

## LIVE 증거 판정

| 사용자 로그 | 판정 |
|---|---|
| Technology PDF HTTP 200, 219,714 bytes | 서버 PDF 생성 정상 |
| Finance PDF HTTP 200, 195,010 bytes | 서버 PDF 생성 정상 |
| IDM 저장 파일이 정상 PDF | PDF generator failure 아님 |
| Frontend `INVALID_PDF_DOCUMENT` | preview transport/client validation 문제 |

이번 결함을 PDF 생성기 문제로 분류하지 않았다. Professional·Finance·Integrated PDF renderer의 계산·내용·병합 로직은 변경하지 않았다.

## PREVIEW TRANSPORT BEFORE / AFTER

| 항목 | V21.2 | V21.3 |
|---|---|---|
| 미리보기 입력 | PDF endpoint의 binary Blob | 이미 로드된 current JSON result |
| 미리보기 클릭 네트워크 | `application/pdf` GET | PDF GET 0회 |
| IDM 개입 가능성 | raw PDF request를 가로챌 수 있음 | raw PDF request 자체 없음 |
| 렌더러 | PDF.js canvas | React report document |
| Dialog 표시 | Blob fetch/검증 후 READY | 클릭 즉시 current data 표시 |
| 다운로드 | preview Blob 재사용 | 명시적 `PDF 다운로드`에서만 endpoint 호출 |

## REPORT PREVIEW DOCUMENT

### Technology / Operations

`LaunchReadinessReportPreviewDocument`가 다음 current data만 사용한다.

- module type
- 사용자 작성 전문 입력과 원본 문서명
- current `analysis`
- `quality.passed`
- 외부 참고자료
- current/stale 상태

표시 계층:

1. 기술/운영 출시 준비도 보고서
2. 종합 평가와 AI 출시 준비도 점수
3. 입력 기준
4. 영역별 평가
5. 핵심 위험
6. 출시 Gate
7. 우선 실행 과제
8. 외부 참고자료

Backend current DTO에 사용자 작성 `professionalInput`을 additive field로 제공했다. 화면은 TaskRun ID, snapshot/result ID, hash, raw prompt, storage key, provider 내부 정보를 렌더하지 않는다.

### Finance

`FinanceReportPreviewDocument`는 current Finance result를 다시 계산하지 않고 다음 authoritative 값을 표시한다.

1. 핵심 결과
2. 3개년 추정
3. 월별 주요 지표
4. 스트레스 시나리오와 Monte Carlo
5. AI 해석과 권장 조치

기준 시나리오 선택과 숫자 포맷만 presentation에서 수행한다. 누적값, 확률, 시나리오, AI 해석은 저장된 result 값을 그대로 사용한다.

### Integrated

두 개 이상 선택하면 Technology → Operations → Finance 선택 순서대로 current document를 한 Dialog에 표시한다. 통합 PDF bytes는 미리보기에서 요청하지 않는다.

## DOWNLOAD CONTRACT

| 행동 | PDF endpoint | Blob validation | 저장 동작 |
|---|---:|---:|---:|
| 보고서 미리보기 | 0회 | 없음 | 0회 |
| Dialog의 `PDF 다운로드` | 정확히 1회 | size + `%PDF-` | anchor click 1회 |

API 이름도 다운로드 전용 의미로 분리했다.

- `downloadProfessionalReport`
- `downloadFinanceReport`
- `downloadReports`

Professional, Finance, Integrated PDF endpoint는 다운로드 전용이므로 `Content-Disposition: attachment`를 사용한다. DOCX template의 attachment 계약도 유지한다.

## MIME ADVISORY CONTRACT

V21.2의 `blob.type === application/pdf` 절대 조건을 제거했다.

| 검증 요소 | authority |
|---|---|
| Blob 존재와 최소 크기 | 필수 |
| 첫 bytes `%PDF-` | 필수 |
| MIME | advisory, `application/octet-stream`이어도 signature가 유효하면 허용 |
| HTML/JSON bytes | MIME과 무관하게 `%PDF-`가 아니므로 거부 |
| PDF parser viability | Backend HTTP 회귀 테스트에서 실제 `PdfReader` open으로 검증 |

Primary preview는 binary를 받지 않으므로 `INVALID_PDF_DOCUMENT`가 미리보기를 차단하는 경로 자체가 사라졌다.

## PDF.JS DECISION

전수 검색 결과 Launch Readiness 외 `pdfjs-dist` consumer가 없었다. 따라서 다음을 제거했다.

- `PdfCanvasViewer`
- `usePdfPreview`
- PDF viewer/failure 상태 테스트
- `pdfjs-dist` dependency와 lock entries

Production build에서 기존 약 427 kB PDF viewer chunk와 약 1.26 MB worker asset이 더 이상 생성되지 않는다.

## AI SCORE TRANSPARENCY

| 항목 | V21.2 | V21.3 |
|---|---|---|
| 기본 표현 | `78점` | `AI 출시 준비도 평가 78점` |
| 의미 설명 | 없음 | 작성한 기술·운영 계획을 AI가 평가한 준비도이며 정해진 재무 산식 점수가 아님을 설명 |
| 주 점수 | `analysis.score` | 동일 |
| reviewer 점수 | 일반 UI 혼동 가능 | `reviewScore` 숫자 미노출 |
| 독립 검증 | 통과/검토 필요 텍스트 | `quality.passed === true`일 때만 `독립 AI 검증 통과` |

AI prompt·reviewer·score 생성 알고리즘은 변경하지 않았다. 가짜 timer percentage도 추가하지 않았다.

## CURRENT / STALE

- Preview는 모듈이 보유한 current 응답만 사용한다.
- `stale=true`인 결과에는 `이전 입력 기준 결과입니다.`를 표시한다.
- 미리보기를 위해 과거 result나 내부 artifact를 별도 조회하지 않는다.

## 변경 파일

### Backend

- Launch Readiness current DTO/service: 사용자 작성 professional input 제공
- Professional/Finance/Integrated PDF controller: 다운로드 전용 attachment
- 관련 current/PDF/통합 회귀 테스트

### Frontend

- JSON 기반 Professional·Finance·통합 preview document
- Preview Dialog의 explicit download 처리
- 모듈 current 결과를 보고서 선택 영역으로 전달
- PDF Blob MIME advisory validation
- PDF.js viewer/hook/dependency 제거
- A4-like document surface와 responsive CSS
- V21.3 preview/no-fetch/score/download 테스트

## TEST MATRIX

| 영역 | 결과 |
|---|---|
| Technology preview PDF GET 0회 | PASS |
| Operations preview PDF GET 0회 | PASS |
| Finance preview PDF GET 0회 | PASS |
| 3개 integrated preview bundle GET 0회 | PASS |
| 명시적 download에서 loader/anchor 각 1회 | PASS |
| AI 점수 설명·reviewScore 미노출 | PASS |
| `quality.passed` 조건부 문구 | PASS |
| stale 문구 | PASS |
| internal ID/hash DOM 미노출 | PASS |
| octet-stream MIME + `%PDF-` 허용 | PASS |
| HTML bytes 거부 | PASS |
| Frontend 집중 테스트 | 5 files, 17 tests PASS |
| Backend Launch Readiness 집중 테스트 | 4 classes, 10 tests PASS |
| V21.2 Finance 회귀 | 3 classes, 14 tests PASS |
| Backend PDF `%PDF-` + PdfReader | PASS |
| 변경 frontend lint | PASS |
| Frontend production build | PASS |
| Backend production build | PASS |
| `git diff --check` | PASS |

전체 frontend lint는 기존 `useMarketingVisual.test.jsx`의 `global` 미정의 2건 때문에 실패한다. V21.3 변경 파일 lint는 오류 없이 통과했다. 기존 Market/Twin hook warning 2건도 이번 범위 밖이다.

## BROWSER CHECK

로컬 `http://127.0.0.1:3000`은 정상 응답했으나 `/app/projects/41/launch-readiness` 접근 시 로그인 화면으로 이동했다. 인증 정보를 입력하거나 전송하지 않았으며 실제 current 결과 Dialog의 시각 검증은 수행하지 않았다.

따라서 기능 자동 검증은 COMPLETE지만 실제 인증 데이터의 문서 밀도·긴 표·모바일 Dialog는 **USER REVIEW PENDING**이다.

## REMAINING RISK / CONTINUATION

1. 인증 프로젝트에서 Technology·Operations·Finance·통합 미리보기의 실제 데이터 길이를 시각 검토한다.
2. IDM 환경에서 미리보기 클릭 시 PDF 요청과 IDM popup이 0회인지 Network 탭으로 확인한다.
3. 명시적 다운로드에서만 IDM 또는 브라우저 저장 동작이 시작되는지 확인한다.
4. 향후 pixel-identical PDF preview가 다시 필요하면 raw `application/pdf` fetch가 아닌 별도 JSON/base64 preview 계약으로 설계한다.

