# V21.6 Launch Readiness 최종 UX 마감 결과

- 기능 상태: **COMPLETE**
- 사용자 시각 검토: **USER REVIEW PENDING**
- START SHA: `4436602311be2c6830eaf55c5d5e1bfba96c78e6`
- 기준 브랜치: `full`
- 시작 작업 트리: clean
- 실제 fetch 후 `HEAD == origin/full`

## 결과 요약

Finance 입력 문서명은 새 저장소나 DB 필드 없이 기존 snapshot → evidence artifact lineage에서 복원한다. 따라서 업로드 직후에는 선택한 로컬 파일명을 임시 표시하고, 재진입·새로고침 이후에는 서버 `current.sourceDocumentName`이 authority가 된다. 같은 값은 개별 Finance 보고서와 통합 보고서의 Finance 문서 표지에도 표시된다.

Launch Readiness 메인은 독립성 설명을 상단 한 문단으로 합쳤다. 카드의 반복 badge·상태·upstream 설명은 제거하고 각 분석이 실제로 다루는 범위만 남겼다. 하단 대형 보고서 module은 삭제하고 상단 compact toolbar로 동일 선택 기능을 옮겼다. 세 workflow는 station과 connector가 분명한 세로 timeline으로 정리했다.

프로젝트 공통 header에는 모든 비-overview Journey에서 프로젝트 개요로 직접 이동하는 icon action을 추가했다. 이 동작은 browser history가 아니라 `projectRoutes.overview(projectId)`를 사용한다.

## 보호 계약

| 계약 | 결과 |
|---|---|
| Mini Product Authority | 유지 |
| Technology/Operations Professional DOCX authority | 유지 |
| Finance USER_DOCUMENT authority | 유지 |
| Professional AI·score/result | 변경 없음 |
| Finance 계산·parser·idempotency | 변경 없음 |
| TaskRun·JobEvent·SSE·Work Center | 변경 없음 |
| current/stale | 유지 |
| V21.5 보고서 table·KRW formatter | 유지 |
| 통합 보고서 canonical order | 기술 → 운영 → 재무 유지 |
| Screen = Print·`window.print()` | 유지 |
| Backend PDF compatibility | 유지 |

## FINANCE DOCUMENT NAME ROOT CAUSE

| 경계 | 이전 | V21.6 |
|---|---|---|
| 업로드 직후 | `FinanceModule.state.filename = file.name` | `optimisticFilename`으로만 임시 표시 |
| route unmount/remount | local state 소멸 | `financeCurrent()` 응답으로 복원 |
| Finance `AnalysisView` | 문서명 필드 없음 | additive `sourceDocumentName` 제공 |
| 보고서 표지 | 고정 문구 `사용자 재무 입력 문서` | 실제 이름, 없을 때만 안전한 고정 문구 |

원인 분류는 persistence 손실이 아니라 **영속 artifact lineage를 current view에서 해석하지 않은 view bug**다.

## FINANCE SOURCE AUTHORITY

```text
FinancialInputSnapshot.sourceDocumentArtifactId
  → ProjectEvidenceArtifactRepository
  → projectId + deletedAt is null 경계 조회
  → ProjectEvidenceArtifact.originalFilename
  → AnalysisView.sourceDocumentName
```

- 새 DB migration/column/table을 추가하지 않았다.
- artifact가 없는 역사 데이터는 Finance current 전체를 실패시키지 않고 `null`을 반환한다.
- main UI 우선순위는 `current.sourceDocumentName → optimisticFilename → 미표시`다.
- 개별/통합 Finance Report는 동일 current 값을 사용한다.

## COPY DEDUPE BEFORE / AFTER

| 위치 | 이전 | V21.6 |
|---|---|---|
| Page description | 독립성·앞 단계·제출 문서 의미를 긴 문장으로 설명 | 필요한 분석만 선택 가능하고 제출 문서 기준임을 한 문단으로 설명 |
| 추가 helper | 공개 자료/Finance authority를 다시 설명 | 제거 |
| Header status | `선택형 · 독립 문서 분석` | 제거 |
| 카드 badge | 세 카드 모두 `독립 사용 가능` | 제거 |
| Technology 카드 | upstream 미사용을 반복 설명 | 기술 구조·보안·성능·테스트·출시 계획 범위 |
| Operations 카드 | upstream 미사용을 반복 설명 | 운영 프로세스·고객 지원·품질·확장 계획 범위 |
| Finance 카드 | upstream 미사용을 반복 설명 | 입력한 비용·매출·성장 계획의 손익·현금흐름 범위 |

실행 독립성 자체는 변경하지 않았다.

## REPORT TOOLBAR BEFORE / AFTER

| 항목 | 이전 | V21.6 |
|---|---|---|
| 위치 | 분석 grid 아래 대형 네 번째 module | `ProjectStageHeader` 바로 아래 |
| 밀도 | heading·description·대형 picker card | 단일 utility row |
| 선택 계약 | 완료/current/not-stale 보고서 | 동일 |
| 1개 선택 | 개별 보고서 | 동일 |
| 2~3개 선택 | 통합 보고서 | 동일 |
| 순서 | canonical helper | 기술 → 운영 → 재무 유지 |
| 준비된 보고서 없음 | 큰 빈 module | 짧은 helper만 표시 |
| mobile | module stack | picker wrap + full-width action, overflow 없음 |

## WORKFLOW VISUAL CONTRACT

각 분석 카드에 3개 station을 유지한다.

| 분석 | 1 | 2 | 3 |
|---|---|---|---|
| 기술 | 템플릿 받기 | 실제 계획 작성·업로드 | 분석 결과 확인 |
| 운영 | 템플릿 받기 | 실제 계획 작성·업로드 | 분석 결과 확인 |
| 재무 | 재무 템플릿 받기 | 재무 값과 산정 근거 작성·업로드 | 손익·현금흐름 분석 결과 확인 |

- station은 circle, 낮은 채도의 tonal surface, 명확한 border를 사용한다.
- station 사이에는 실제 세로 connector가 있다.
- 각 step은 title + 한 줄 helper이며 nested card를 만들지 않는다.
- 카드 grid의 3/2/1 column responsive 계약은 유지한다.

## GLOBAL OVERVIEW RETURN CONTRACT

| 항목 | 계약 |
|---|---|
| 구현 authority | `ProjectLayout`의 공통 `ProjectLocationRow` |
| breadcrumb | `프로젝트 개요 / 현재 Journey` 유지 |
| action 위치 | 같은 location row 우측 |
| destination | `projectRoutes.overview(projectId)` |
| overview Journey | self-return 숨김 |
| 비-overview Journey | 공통 icon link 1개 |
| history dependency | `navigate(-1)`, `history.back()` 사용 없음 |
| icon | 기존 `AppIcon name="chevronLeft"` |
| 접근성 | `aria-label`, `title`, 40×40 target, focus-visible |

Report Page의 `출시 준비로 돌아가기`는 Journey 복귀 동작이므로 공통 프로젝트 개요 action과 함께 유지한다.

## TEST MATRIX

| 검증 | 결과 |
|---|---|
| Finance artifact lineage → actual filename | PASS |
| artifact 없는 역사 snapshot safe null | PASS |
| Finance component unmount/remount current 복원 | PASS |
| Finance 개별 report 실제 문서명 | PASS |
| Finance 통합 report 동일 문서명 | PASS · 동일 document component 사용 |
| 독립성 page copy 1회·반복 badge/status 0 | PASS |
| compact toolbar 1개·하단 대형 module 0 | PASS |
| report selection·canonical order 회귀 | PASS |
| workflow 3 station·connector source contract | PASS |
| overview self-return 0 | PASS |
| 모든 실제 Journey id의 공통 return 1 | PASS |
| overview href·history independence·accessible name | PASS |
| Launch Readiness/ProjectLayout 집중 frontend | PASS · 5 files, 34 tests |
| Finance/Launch Readiness backend 집중 테스트 | PASS |
| 변경 frontend 파일 ESLint | PASS |
| Frontend production build | PASS |
| Backend build | PASS |
| `git diff --check` | PASS |

## USER REVIEW ITEMS

자동 기능 검증과 build는 완료했다. 인증 브라우저 visual acceptance는 별도 사용자 검토가 필요하다.

1. 1280px+에서 상단 report toolbar가 page의 별도 대형 module처럼 보이지 않는지
2. 1024px/768px/390px에서 toolbar picker와 action이 겹치지 않는지
3. 세 카드의 station·connector가 위에서 아래로 자연스럽게 읽히는지
4. 실제 Finance 재진입 후 업로드 문서명이 유지되는지
5. Finance 개별/통합 보고서 표지에 같은 실제 filename이 보이는지
6. breadcrumb 우측 overview icon이 설정 action과 충돌하지 않고 40×40 target으로 보이는지
7. overview 화면에는 self-return icon이 없는지

## LAUNCH READINESS CLOSURE STATUS

V21.6 기능 구현과 자동 회귀 검증은 **COMPLETE**다. 보고서 table, KRW 표현, 통합 순서, Screen = Print와 Mini Product Authority는 변경하지 않았다. 디자인 만족도는 **USER REVIEW PENDING**으로 분리한다.

