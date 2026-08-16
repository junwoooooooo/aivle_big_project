# V21 출시 준비 Mini 제품 권위 통제 이식 결과

## 완료 상태

| 구분 | 판정 | 설명 |
| --- | --- | --- |
| PRODUCT INTENT | **PASS** | Mini의 기술·운영·재무·보고서 단일 사용자 흐름을 Full에 이식했다. |
| PLATFORM HARDENING | **PASS** | Full의 인증·소유권·안전 업로드·불변 입력·TaskRun·JobEvent·SSE·Work Center·current/stale 계약을 사용한다. |
| DATA MIGRATION | **HOLD** | V27 SQL 설계와 애플리케이션 매핑은 완료했으나 현재 호스트에 Docker CLI가 없어 실제 PostgreSQL empty/upgrade 실행은 하지 못했다. |
| AUTOMATED TEST | **PARTIAL** | V21 집중 테스트·lint·production build는 통과했다. 전체 저장소에는 기존 baseline 실패와 장시간 backend suite timeout이 남는다. |
| USER VISUAL REVIEW | **PENDING** | 인증 브라우저 화면 검토는 완료 조건에서 분리했다. 생성 PDF는 자동 렌더로 한글·표·링크를 확인했다. |
| REMAINING GAP | **FOLLOW-UP** | 외부 근거가 독립 reviewer 입력에 직접 포함되지 않고, 결과 항목과 근거 URL 사이 citation/basis ID가 없다. PostgreSQL·Docker 실환경 검증도 필요하다. |

V21 범위 기능 구현은 완료했다. 다만 위 HOLD/PENDING 항목을 포함한 배포 승인까지 완료됐다고 과장하지 않는다.

## START FULL SHA / DONOR MINI SHA

| 항목 | 값 |
| --- | --- |
| branch | `full` |
| 시작 HEAD | `dd99a64f572d5ef1bd19be504914e2d952289ce9` |
| fetched `origin/full` | `dd99a64f572d5ef1bd19be504914e2d952289ce9` |
| fetched `origin/mini` | `5d27fffa0335bcbf3f4e2fe1a789cf8a433c17c2` |
| 시작 작업트리 | clean |

## PRODUCT AUTHORITY STATEMENT

- 사용자가 보는 기능 범위·입력 방식·순서·결과는 Mini를 권위로 삼았다.
- 비동기 실행·보안·영속성·작업 이력·재시도·stale/lineage는 Full을 권위로 삼았다.
- 브랜치 병합, unrelated history merge, blind cherry-pick, 디렉터리 overwrite는 사용하지 않았다.
- 기존 Full TechOps·Finance 코드는 역사 데이터와 Final Report 호환을 위해 유지하되 새 Journey의 사용자 선행 흐름으로 노출하지 않는다.

## MINI INTENT EVIDENCE

| Mini 근거 | 채택한 제품 의도 |
| --- | --- |
| `frontEnd/src/features/launch-readiness/pages/LaunchReadinessPage.jsx` | 기술·운영·재무·보고서를 하나의 출시 준비 화면에서 제공 |
| `frontEnd/src/features/launch-readiness/finance/*` | 재무를 독립 과거 화면이 아닌 출시 준비 흐름에 포함 |
| `ai/app/tasks/launch_readiness/professional/*` | 엄격한 전문 분석 schema, 외부 검색, 독립 검토, 최대 2회 생성 |
| `backend/.../FinancialInputDocumentService.java` | 사용자 DOCX 템플릿을 재무 입력 정본으로 사용 |
| `backend/.../FinancialAnalysisPdfService.java` | 계산 결과·시나리오·Monte Carlo·해석을 PDF로 제공 |
| Mini V27/V28/V29 | 전문 분석 영속화·감사 필드·별도 재무 보고서 테이블 의도를 개별 검토 |

## DONOR FILE MANIFEST

| 영역 | donor 파일 | 판정 |
| --- | --- | --- |
| 화면 | `frontEnd/src/features/launch-readiness/pages/LaunchReadinessPage.jsx` | ACCEPT intent / ADAPT implementation |
| 재무 화면 | `frontEnd/src/features/launch-readiness/finance/api/financeApi.js`, `hooks/useFinance.js`, `styles/finance.css` | ACCEPT intent / ADAPT Full API·UI |
| Professional AI | `ai/app/tasks/launch_readiness/professional/{models.py,service.py}` | ACCEPT schema·review / ADAPT Full execution router |
| Mini sync API | `ai/app/api/launch_readiness.py`, `backend/.../ProfessionalLaunchReadinessController.java` | REJECT transport |
| Finance document | `backend/.../pipeline/finance/application/FinancialInputDocumentService.java` | ACCEPT workflow / ADAPT validation·artifact lineage |
| Finance PDF | `backend/.../finance/service/FinancialAnalysisPdfService.java` | ACCEPT content / ADAPT Full authoritative result |
| Professional PDF | Mini professional service/PDF tests | ACCEPT content intent / ADAPT OpenPDF·Full current result |
| Migration | `V27__professional_launch_readiness_analysis.sql` | ADAPTED into Full V27 |
| Migration | `V28__add_audit_columns_to_professional_launch_readiness_reports.sql` | ADAPTED into Full V27/BaseEntity audit columns |
| Migration | `V29__financial_analysis_reports.sql` | REJECTED: Full runtime consumer가 없어 donor table을 복사하지 않음 |

## ACCEPT / ADAPT / REJECT / COMPATIBILITY MATRIX

| 판정 | 내용 |
| --- | --- |
| ACCEPT | 단일 Launch Readiness 화면, DOCX 중심 입력, 기술·운영 strict 결과, 사용자 문서 재무 authority, 개별/통합 PDF |
| ADAPT | 동기 호출을 TaskRun worker로 교체, 실제 JobEvent/SSE 진행, secure artifact 업로드, current/stale/hash lineage, Full Dialog·Work Center·Journey aggregation |
| REJECT | Mini timer 8/45/92%, backend→AI 장시간 동기 HTTP, overwrite 방식 모듈 집계, PostgreSQL tmpfs, blind migration copy, 미사용 V29 테이블 |
| COMPATIBILITY | 기존 TechOps backend/history/source를 유지하고 Final Report는 legacy `TECH_OPS` 또는 새 기술+운영 결과를 읽는다. 기존 Finance 내부 계산/runtime도 재사용한다. |

## USER FLOW BEFORE/AFTER

| 구분 | 이전 Full | V21 |
| --- | --- | --- |
| 진입 | TechOps와 Finance가 별도 사용자 기능 | `출시 준비` 하나 |
| 기술 | 기존 TechOps workflow 의존 | 기술 템플릿 → DOCX → 비동기 분석 → 결과/PDF |
| 운영 | 별도 Professional 흐름 없음 | 운영 템플릿 → DOCX → 비동기 분석 → 결과/PDF |
| 재무 | Market/BM 기반 초기화·독립 workspace | 사용자 재무 DOCX → 전체 검증 → 계산/분석 → 결과/PDF |
| 보고서 | 모듈별 분산 | 완료 보고서 1개는 개별, 2~3개는 통합 PDF |
| 진행 상세 | 화면별 로그 표현 | 본문 macro 상태 + 단일 Work Center 상세 |

## ROUTE MATRIX

| URL | 최종 화면 | 초기 focus |
| --- | --- | --- |
| `/app/projects/:projectId/launch-readiness` | `LaunchReadinessPage` | 화면 상단 |
| `/technology` | `LaunchReadinessPage` | 기술 |
| `/operations` | `LaunchReadinessPage` | 운영 |
| `/tech-ops` | `LaunchReadinessPage` | 출시 준비 상단 |
| `/finance` | `LaunchReadinessPage` | 재무 |

`TechOpsPage`와 과거 `FinancePage`는 위 사용자 route에서 렌더하지 않는다.

## LEGACY TECHOPS COMPATIBILITY MATRIX

| 소비자 | 처리 |
| --- | --- |
| 새 Journey/route | 새 Launch Readiness만 사용 |
| 기존 DB·historical TaskRun | 삭제하지 않음 |
| Work Center | 기존·신규 작업 이력 모두 유지 |
| Final Report | legacy `TECH_OPS` 유지 + `LAUNCH_TECHNOLOGY`/`LAUNCH_OPERATIONS` adapter 추가 |
| 새 프로젝트 준비 판정 | legacy TechOps 1개 또는 새 기술+운영 current 결과 조합을 수용 |
| 기존 TechOps 입력 선행 요구 | 없음 |

## TECHNOLOGY INPUT CONTRACT

- fieldKey 기반 10개 항목: 시스템·제품 구조, 핵심 기능, 기술 스택, 외부 연동, 데이터·보안, 성능·확장, 개발 인력, 출시 일정, 테스트, 기술 위험.
- 프로젝트 소유권 확인 후 Full artifact 계층이 크기·확장자·OOXML signature·안전 파일명·소유권을 검증한다.
- 원본 artifact ID/hash/name과 parse한 사용자 값을 불변 snapshot에 함께 저장한다.
- 지원 fieldKey가 있는 DOCX만 읽고, 작성 항목이 하나도 없으면 사용자용 validation 오류를 반환한다.

## OPERATIONS INPUT CONTRACT

- fieldKey 기반 10개 항목: 운영 프로세스, 인력·역할, 공급·파트너, 고객 지원, 품질·SLA, 장애·민원, KPI, 파일럿, 확장, 운영 위험.
- BM·TechOps 결과를 사용자 문서보다 우선하는 숨은 입력으로 사용하지 않는다.
- 기술과 동일한 안전 업로드·불변 snapshot·hash·current 교체 계약을 사용한다.

## PROFESSIONAL AI CONTRACT

| 항목 | 계약 |
| --- | --- |
| 요청 | `moduleType` + 사용자 `professionalInput` |
| 결과 | `decision`, `score`, `summary`, `dimensions`, `risks`, `gates`, `actions` |
| strictness | Pydantic `extra=forbid`, enum·길이·개수·점수 범위 검증 + backend exact top-level 검증 |
| 외부 근거 | Tavily가 설정된 경우 생성 입력에 포함하고 결과 `externalEvidence`에 보존 |
| 독립 검토 | 사용자 입력과 생성 분석을 별도 reviewer가 검증 |
| 재생성 | review 실패 feedback을 사용해 최대 2회 |
| 실패 | provider/raw 오류를 사용자에게 노출하지 않고 TaskRun safe code로 변환 |

외부 근거 감사 결과: 생성기는 근거를 보지만 reviewer는 현재 사용자 입력과 분석만 받는다. 개별 risk/gate/action과 evidence URL 사이 명시적 citation ID도 없다. 판단 core를 임의 변경하지 않고 후속 개선 항목으로 남겼다.

## PROFESSIONAL ASYNC EXECUTION

1. 인증 사용자와 프로젝트 소유권을 확인한다.
2. 같은 트랜잭션 안에서 secure upload 후 DOCX를 parse한다. parse 실패 시 artifact 저장도 rollback된다.
3. 이전 current snapshot/result를 supersede하고 새 불변 snapshot을 만든다.
4. `LAUNCH_TECHNOLOGY_READINESS` 또는 `LAUNCH_OPERATIONS_READINESS` TaskRun을 QUEUED로 생성한다.
5. 동일 task type과 명령 키 재전송은 새 업로드 전에 기존 TaskRun을 반환한다.
6. worker가 lease/attempt/timeout 계약으로 실행하며 입력 snapshot이 바뀌면 stale 결과를 채택하지 않는다.
7. JobEvent는 SSE와 Work Center에 연결되고 본문에는 가짜 percentage 대신 macro activity를 표시한다.

## FINANCE USER DOCUMENT AUTHORITY

- `GET /api/v3/projects/{projectId}/finance/preparation/template`
- `POST /api/v3/projects/{projectId}/finance/preparation/import`
- import는 secure artifact 저장 → DOCX parse → 전체 필드 검증 → preparation/snapshot 반영 → Finance TaskRun 시작 순이다.
- `sourceMode=USER_DOCUMENT_INPUT`이며 Market/BM ID는 null일 수 있다.
- Market/BM 결과가 없어도 시작할 수 있고, 존재해도 사용자 문서 값을 덮지 않는다.
- 사용자 화면에서도 내부 `TaskRun`, `Snapshot`, hash 용어를 제거했다.

## FINANCE LINEAGE

| 저장 대상 | 보존 값 |
| --- | --- |
| preparation | source mode, artifact ID, document hash, revision, normalized fields |
| input snapshot | preparation ID/revision, source mode, artifact ID/hash, snapshot hash, finalizedAt, createdBy |
| TaskRun input | snapshot ID/hash + deterministic calculation result |
| final result | authoritative calculation/scenarios/Monte Carlo + AI report 또는 safe fallback |

## FINANCE STALE CONTRACT

- USER_DOCUMENT 모드에서는 Market/BM version null을 stale 이유로 사용하지 않는다.
- source document 변경으로 preparation revision과 snapshot hash가 달라지면 이전 결과는 최신 결과로 표시하지 않는다.
- 문서 전체 validation이 끝나기 전 preparation을 부분 변경하지 않는다.
- 검증: unknown/duplicate field, revenue model enum, 양의 신규 고객 정수, 0~100 이탈률, 0 이상 3개년 목표와 금액, 필수 항목.

## FINANCE PDF

- authoritative Finance result만 문서화하며 PDF renderer가 다시 계산하지 않는다.
- 핵심 결과, 3개년 손익, 월별 매출·영업이익·누적 현금흐름, stress scenario, Monte Carlo, 해석·권장 조치, 사업 적용 결론을 포함한다.
- OpenPDF 1.3.39와 Docker의 `fonts-noto-cjk`를 추가했다. Windows QA에서는 Malgun Gothic, Linux에서는 Noto CJK를 사용하며 글꼴이 없으면 조용히 깨진 PDF를 만들지 않고 실패한다.
- 자동 렌더 결과 한글 glyph·표 폭·페이지 분리를 확인했다.

## INTEGRATED REPORT

- 완료·current·non-stale 보고서만 선택 가능하다.
- 1개 선택은 원본 개별 PDF를 반환한다.
- 2~3개 선택은 통합 표지, 선택 문서, 중복 제거된 외부 출처 페이지를 병합한다.
- 통합 manifest에는 선택 module과 source result/snapshot hash를 저장한다.
- URL 기준 dedupe 후 클릭 가능한 link annotation을 보존하며 테두리는 출력하지 않는다.

## MODULE STATUS AGGREGATION

- backend는 기술·운영 TaskRun을 `TECH_OPS`, 사용자 문서 Finance를 `FINANCE` 내부 상태로 유지한다.
- frontend는 두 internal module을 하나의 `launchReadiness` Journey로 deterministic aggregate한다.
- 우선순위는 `FAILED → NEEDS_INPUT → STALE → RUNNING → QUEUED → READY → COMPLETED` 순이며 required input을 합친다.
- Mini의 `Object.fromEntries` overwrite 문제를 복사하지 않았다.

## MIGRATION DECISION

| Mini migration | V21 판정 | 이유 |
| --- | --- | --- |
| V27 Professional tables | ADAPTED | Full project/artifact/task_run FK, current index, schema/hash/owner 필드를 포함해 Full V27에 반영 |
| V28 audit columns | ADAPTED | Full `BaseEntity`의 created/updated/deleted/version 계약으로 V27 생성 시점부터 포함 |
| V29 financial_analysis_reports | REJECTED | Full Finance는 기존 TaskResult가 authoritative result이며 신규 table consumer가 없음 |

Full V27은 professional input/report, Finance user-document lineage columns/index, integrated manifest를 추가한다. 기존 migration을 수정하거나 PostgreSQL tmpfs를 도입하지 않았다.

## DOCKER DECISION

- backend image에 한국어 PDF용 `fonts-noto-cjk`만 추가했다.
- AI/Backend long-running 구조는 기존 Full container/runtime를 유지한다.
- 현재 호스트에서 `docker` 명령을 찾을 수 없어 backend/AI image build, font runtime, health check, empty PostgreSQL, 기존 Full DB upgrade를 실행하지 못했다.
- 따라서 Docker와 migration 실행 판정은 **HOLD**이며 CI 또는 Docker가 있는 환경에서 반드시 수행해야 한다.

## TEST MATRIX

| 영역 | 결과 | 비고 |
| --- | --- | --- |
| AI V21 집중 | PASS | 8 passed, Professional strict/review/attempt + Finance null upstream + TaskType alignment |
| Backend V21 집중 + context | PASS | 11 passed; snapshot/async/idempotency/stale/PDF/bundle/Finance authority/Final Report 포함 |
| Frontend V21 집중 | PASS | 5 files, 27 tests |
| Changed-file ESLint | PASS | 변경 JS/JSX와 새 feature |
| Frontend production build | PASS | Vite production build; 기존 chunk size warning만 존재 |
| PDF 구조 | PASS | technology 2p, finance 1p, integrated 4p; 통합 URL annotation 1개 |
| PDF visual render | PASS | 한글·표·여백·출처 링크 표시 확인 |
| `git diff --check` | PASS | whitespace 오류 없음 |
| 전체 frontend | PARTIAL | 539 passed, 26 failed/8 files; Auth/App 구문·과거 Finance seed·Marketing label·기존 label 기대 등 V21 외 baseline 실패 |
| 전체 AI | PARTIAL | 최초 701 passed/1 skipped/5 failed; TaskType alignment 1건 수정 후 집중 PASS, 남은 Concept Portfolio input-shape 4건은 origin/full 기존 영역 |
| 전체 backend | HOLD | 6분 이상 장시간 실행 후 timeout; orphan test process 종료, V21 집중 세트는 PASS |
| PostgreSQL migration | HOLD | Docker CLI 없음 |
| Docker images/health | HOLD | Docker CLI 없음 |

## REMAINING GAP

1. Docker가 있는 환경에서 empty PostgreSQL V1→V27과 기존 Full DB V26→V27 upgrade를 검증해야 한다.
2. backend/AI image build, Noto CJK 실제 container PDF, health check가 필요하다.
3. 외부 evidence를 reviewer가 직접 검증하지 않으며 result item별 citation/basis ID가 없다. 이는 AI core 후속 설계로 분리한다.
4. 전체 저장소 baseline 실패·backend 장시간 suite를 별도 안정화해야 한다. V21 집중 테스트는 통과했다.
5. 실제 인증 프로젝트의 1440/1920/390 화면과 modal focus/scroll을 사용자가 검토해야 한다.

## USER VISUAL REVIEW ITEMS

- 출시 준비가 기술·운영·재무·보고서 하나의 기능으로 느껴지는지
- alias route가 과거 TechOps/Finance 화면이 아니라 동일 화면과 올바른 section을 여는지
- DOCX workflow와 입력 오류 문구가 이해 가능한지
- 실행 중 macro 진행과 Work Center 상세 연결이 자연스러운지
- 완료 결과·stale 문구·PDF 미리보기·다운로드가 명확한지
- 2~3개 통합 보고서 표지·본문·출처 페이지와 모바일 overflow가 자연스러운지
