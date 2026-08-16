# V21.5 Launch Readiness IA·보고서 시각 체계·KRW 가독성 결과

- 기능 상태: **COMPLETE**
- 사용자 화면·인쇄 검토: **USER REVIEW PENDING**
- START SHA: `e0bb9bc9353097c86391b5b37264ce83fca69a05`
- 기준 브랜치: `full`
- 시작 작업 트리: clean
- 실제 fetch 후 `HEAD == origin/full`

## 결과 요약

Launch Readiness의 분석·계산·문서 authority를 변경하지 않고 메인 화면과 보고서의 presentation만 정리했다. 기술·운영·재무는 데스크톱에서 3열 독립 분석 카드로 보이며, 각 카드의 workflow는 세로 흐름으로 바뀌었다. 상단 sticky section navigation은 제거하고 보고서 선택 영역은 분석 grid 아래의 full-width section으로 분리했다.

Professional 보고서는 V21.4의 표지·여백·callout을 유지하면서 입력 근거, 영역별 평가, 위험, Gate, 실행 과제를 정형 표로 바꿨다. Finance는 authoritative 결과를 재계산하지 않고 모든 KRW 금액에 원 숫자와 한국식 읽기 표현을 함께 제공한다. 통합 보고서는 선택 순서와 관계없이 기술 → 운영 → 재무 순서를 사용한다.

## PRODUCT CONTRACT

| 계약 | 결과 |
|---|---|
| Mini Product Authority | 유지 |
| Technology/Operations Professional input authority | 유지 |
| Finance USER_DOCUMENT authority | 유지 |
| AI prompt/schema/score/result | 변경 없음 |
| Finance 계산·TaskRun·idempotency | 변경 없음 |
| JobEvent/SSE·Work Center | 변경 없음 |
| current/stale | 유지 |
| React Report = Screen = Print | 유지 |
| `window.print()` Save-as-PDF | 유지 |
| Backend PDF compatibility | 변경 없음 |

새 AI 호출, 결과 재생성, Finance 재계산, Backend DTO/API 변경은 없다.

## INDEPENDENCE COPY BEFORE / AFTER

| 위치 | 이전 | V21.5 |
|---|---|---|
| Page title | 기술·운영·재무 준비를 한 흐름에서 확인 | 출시 전에 필요한 준비 상태를 분야별로 확인 |
| Page description | 한 흐름으로 오해 가능 | 세 분석은 서로 독립적으로 사용할 수 있고 제출 문서가 기준임을 명시 |
| Status | 사용자 문서 기준 | 선택형 · 독립 문서 분석 |
| Technology | 전문 입력 문서 기준이라는 짧은 설명 | 앞 단계 결과를 자동으로 가져오지 않으며 제출 기술 문서가 1차 기준임을 명시 |
| Operations | 전문 입력 문서 기준이라는 짧은 설명 | 앞 단계 없이 사용할 수 있으며 제출 운영 문서가 기준임을 명시 |
| Finance | upstream 없이 시작 가능 | 시장·사업 모델 결과 없이 사용 가능하고 업로드한 값만 계산 기준임을 명시 |
| 보조 근거 | 카드별 의미가 분산 | 기술·운영의 공개자료는 보조, 재무는 업로드 값만 계산 authority임을 별도 helper로 명시 |

각 카드에는 사용자-facing `독립 사용 가능` badge를 표시한다. 내부 domain 명칭은 노출하지 않는다.

## PAGE IA BEFORE / AFTER

| 항목 | 이전 | V21.5 |
|---|---|---|
| 분석 배치 | Technology → Operations → Finance 세로 적층 | 하나의 `.launch-analysis-grid` 안에 분야별 카드 배치 |
| 데스크톱 | full-width 카드 3개 | 3열 |
| 상단 navigation | sticky 기술·운영·재무·보고서 nav | 제거 |
| 카드 높이 | 독립 section | `align-items:start`, 동일 높이 강제 없음 |
| 카드 내부 결과 | 2열 summary + 3열 action item | 카드 폭에 맞춘 단일 열 |
| 보고서 선택 | 마지막 module과 같은 폭 | 분석 grid 아래 full-width section |

## 3-COLUMN RESPONSIVE MATRIX

| viewport | 분석 grid | 카드 내부 | overflow 계약 |
|---|---|---|---|
| 1200px 이상 | 3열 | 세로 workflow·단일 열 result/action | `minmax(0, 1fr)`, 긴 문장·파일명 wrap |
| 761~1199px | 2열 | 동일 | horizontal overflow 없음 |
| 760px 이하 | 1열 | button·result·picker 1열 | 390px에서도 table과 card가 container 안에서 wrap |

## WORKFLOW BEFORE / AFTER

| 모듈 | 이전 horizontal flow | V21.5 vertical flow |
|---|---|---|
| Technology | 템플릿 다운로드 → DOCX 업로드 → 전문 분석 | 템플릿 받기 → 실제 계획 작성·업로드 → 독립 분석 결과 확인 |
| Operations | 템플릿 다운로드 → DOCX 업로드 → 전문 분석 | 템플릿 받기 → 실제 계획 작성·업로드 → 독립 분석 결과 확인 |
| Finance | 재무 템플릿 작성 → 문서 검증 → 분석 | 재무 템플릿 받기 → 값·산정 근거 작성·업로드 → 손익·현금흐름 독립 분석 |

기능 action과 API는 그대로이며 문구·배치만 변경했다.

## OLD PDF VISUAL DONOR MATRIX

사용자가 지정한 두 이전 PDF 바이너리는 현재 workspace와 첨부 디렉터리에서 찾을 수 없었다. 따라서 pixel comparison은 수행하지 않았고, 요청에 명시된 donor 특성을 presentation 계약으로 적용했다.

| donor 특성 | 적용 |
|---|---|
| 3-column summary strip | AI 평가·판정·독립 검증 3개 summary로 적용 |
| 진한 table header | muted navy `#2e4e73` + white text |
| 얇은 grid border | neutral `#d9dfe8` cell grid |
| 입력 근거 2-column | 26% / 74% table |
| dimensions 4-column | 23% / 11% / 14% / 52% |
| risks 4-column | 위험 / 등급 / 사업 영향 / 대응책 |
| compact office-report density | Gate·Action도 고정 column table |
| 결론 callout·source list | V21.4 구조 유지 |

## REPORT SECTION BEFORE / AFTER

| section | 이전 | V21.5 |
|---|---|---|
| Summary | 2 block | AI 평가·판정·독립 검증 3-column strip |
| 1 경영진 요약 | callout | 유지 |
| 2 입력 근거 | 기본 table | 26/74 donor-style table |
| 3 영역별 평가 | article card rows | 평가 영역·AI 평가·상태·판단 근거 table |
| 4 핵심 위험 | article/dl rows | 위험·등급·영향·대응 table |
| 5 출시 Gate | article/dl rows | 확인 기준·상태·통과 기준·확인 자료 table |
| 6 실행 과제 | list rows | 우선순위·과제·담당·완료 증빙 table |
| 7 사업 적용 결론 | callout | 유지 |
| 8 외부 출처 | clickable list | 유지 |

dimensions, risks, gates, actions, source의 원본 항목 수와 텍스트는 그대로 렌더한다.

## TABLE DESIGN CONTRACT

- 화면과 print가 같은 table DOM과 CSS를 사용한다.
- `table-layout: fixed`, `overflow-wrap:anywhere`, `word-break:break-word`로 긴 한국어와 URL을 수용한다.
- `thead`는 `table-header-group`이며 print page마다 반복 가능하다.
- `tbody tr`은 `break-inside:avoid-page`다.
- 긴 표 전체에 page 고정을 강제하지 않는다.
- Professional과 Finance가 동일 navy header·neutral grid 체계를 사용한다.

## INTEGRATED ORDER CONTRACT

단일 model helper의 canonical 순서는 다음과 같다.

`technology → operations → finance`

| 경계 | 보강 |
|---|---|
| Main `viewReport(modules)` | route 생성 전에 canonicalize |
| Report Page query parsing | `modules` 순서·중복·unknown을 canonicalize |
| Integrated renderer | component에 역순 documents가 전달돼도 다시 canonicalize |
| Integrated cover | canonical 순서 label |
| Integrated sources | Technology → Operations → Finance 순으로 수집 후 URL dedupe |

## KRW FORMAT CONTRACT

Finance authoritative 숫자는 변경하지 않는다. presentation helper는 동일 금액을 raw KRW와 한국식 원 단위로 함께 표시한다.

| 입력 | raw | readable |
|---:|---|---|
| 0 | 0 KRW | 0원 |
| 5,000,000 | 5,000,000 KRW | 5백만 원 |
| 50,000,000 | 50,000,000 KRW | 5천만 원 |
| 120,000,000 | 120,000,000 KRW | 1억 2천만 원 |
| 325,000,000 | 325,000,000 KRW | 3억 2,500만 원 |
| 1,250,000,000 | 1,250,000,000 KRW | 12억 5천만 원 |
| -120,000,000 | -120,000,000 KRW | -1억 2천만 원 |

Launch Page summary는 `raw · readable`, Finance Report는 raw/readable 2줄을 사용한다. Monte Carlo P10/P50/P90은 하나의 긴 문장에서 각각의 metric으로 분리했다.

## TEST MATRIX

| 검증 | 결과 |
|---|---|
| Launch Readiness 전체 집중 suite | PASS · 7 files, 38 tests |
| V21.5 KRW·canonical order model | PASS |
| Professional summary 3개 | PASS |
| dimensions/risks/gates/actions row count 보존 | PASS |
| 역순 documents renderer canonical order | PASS |
| 역순 query canonical order | PASS |
| sticky nav 제거 source contract | PASS |
| vertical workflow·3-column grid source contract | PASS |
| Backend PDF GET 0·`window.print()` contract 회귀 | PASS |
| 변경 파일 ESLint | PASS |
| Frontend production build | PASS |
| `git diff --check` | PASS |

전체 Frontend suite는 106 files / 599 tests 중 98 files / 573 tests가 통과했다. 8 files / 26 tests는 이번 변경 밖의 기존 화면 문구·접근성 selector 기대 불일치로 실패했다. Launch Readiness 집중 suite는 전부 통과했다.

## USER VISUAL REVIEW ITEMS

- 현재 환경의 로컬 URL 브라우저 연결 정책으로 authenticated live 화면과 Print Preview는 직접 확인하지 못했다.
- 이전 PDF donor 바이너리가 없어 page 1~3 render 비교는 수행하지 못했다.
- 다음 항목은 사용자 검토가 필요하다.
  1. 1280px 이상에서 세 분석 카드가 독립 기능으로 한눈에 보이는지
  2. 1024px 2열과 390px 1열에서 긴 filename·summary·button overflow가 없는지
  3. 실제 긴 Professional 표에서 한국어 wrapping과 header 반복이 자연스러운지
  4. Finance raw/readable 2줄이 과밀하지 않은지
  5. Technology → Operations → Finance 통합 순서가 항상 유지되는지
  6. Chrome/Edge Save-as-PDF에서 heading orphan, row split, source URL wrap이 안정적인지

## 변경 범위

- Launch Readiness main page IA·사용자 copy·workflow
- Professional/Finance/Integrated React report presentation
- canonical report order·KRW formatter model
- Launch Readiness CSS와 회귀 테스트
- 결과 문서·사용자 검증 문서

Backend, AI, 계산, TaskRun, JobEvent, Finance import/idempotency 파일은 변경하지 않았다.
