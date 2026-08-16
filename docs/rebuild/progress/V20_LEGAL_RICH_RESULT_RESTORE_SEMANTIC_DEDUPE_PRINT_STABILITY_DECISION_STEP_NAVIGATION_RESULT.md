# V20 Legal 풍부한 결과 복원·의미 중복 제거·PDF 안정화·Decision 단계 탐색 결과

## 완료 상태

- V20 자동 기능 검증: **COMPLETE**
- Production build: **PASS**
- 실제 Chrome/Edge PDF 및 사용자 시각 검토: **USER REVIEW PENDING**
- AI Legal 판단, Concept AI, Hypothesis, Market Seed, BM 계산/판정 core: 변경 없음
- Backend product code/API/DB: 변경 없음

## START SHA

| 항목 | 값 |
| --- | --- |
| branch | `full` |
| 시작 HEAD | `821b3bbdc0ff51ec68c70689c80a3d8c572ce24f` |
| fetched `origin/full` | `821b3bbdc0ff51ec68c70689c80a3d8c572ce24f` |
| 시작 작업트리 | clean |

## V17/V19 LEGAL DIFF

V17(`bfe786b5371e9db8bd494a3c8da737fcbd2a5d97`)의 `LegalReport`와 V19를 직접 대조했다. V19은 중복 block을 제거하고 source별 exact dedupe를 추가했지만, 거래·결제·개인정보·물리 활동이 하단 `상세 근거` disclosure 안에 남아 기본 화면에서 사업 구조 맥락이 약해졌다. 전체 V17 blob을 되돌리지 않고 Legal presentation 하위 구조만 선별 복원했다.

| 영역 | V17 | V19 | V20 |
| --- | --- | --- | --- |
| 결과 요약 | 기본 노출 | 유지 | 유지 |
| 특히 확인할 사항 | 4개 그룹 | exact dedupe + 빈 그룹 축소 | semantic dedupe + 같은 계층 유지 |
| 사업 구조 | 역할 4개 기본, 흐름은 상세 | 동일 | 역할·거래·결제·개인정보·물리 활동을 `사업 구조 검토`로 통합 |
| 법률 근거 | 법률별 그룹 | V15 grouping 유지 | grouping·조항·시행일·공식 링크 유지 |
| 광고 | 허용/고지/금지 | 일반 고지 cross-dedupe | 모든 소유 source와 보수적 cross-dedupe |
| 상세 | 흐름 + delta | 흐름 + delta | 기본 계층과 겹치지 않는 delta 이력만 |

## LEGAL CONTENT RESTORE MATRIX

| 기본 순서 | 표시 데이터 | 빈 상태 |
| --- | --- | --- |
| 한눈에 보는 검토 결과 | 한국어 status, `safeSummary`, source partial 경고, 기준일 | 기본 안내 유지 |
| 특히 확인할 사항 | required controls/disclosures, partner·qualification, unknown facts | 일반 빈 그룹 숨김, unknown 없음은 한 번만 표시 |
| 사업 구조 검토 | 플랫폼/판매/제공/중개 역할, 거래/결제, 개인정보, 물리 활동 | 전체가 없을 때만 단일 empty |
| 관련 법률·규제 | 법률별 조항, 요약, 시행일, 공식 원문 | 단일 empty |
| 광고·표현 주의사항 | 사용 가능 표현, 광고 고유 고지, 피해야 할 표현 | 빈 하위 그룹 숨김 |
| 상세 검토 내용 | 기본 section과 겹치지 않는 delta 재검토 이력 | 단일 안내 |

## SEMANTIC DEDUPE RULE

`legalPresentationKey()`는 presentation 전용 deterministic key다.

1. 문자열 trim 및 내부 whitespace 한 칸 정규화
2. 문장 끝의 `. ! ? 。！？`만 제거
3. 문장 끝의 제한된 의무 표현만 제거: `이/가 필요함`, `필요함/필요합니다`, `해야 함/해야 합니다`, `요구됨`
4. 정규화한 핵심 문장이 완전히 같은 경우만 병합
5. 처음 등장한 원본 item을 그대로 표시

형태소 분석, fuzzy similarity, Levenshtein, 새 AI 호출은 사용하지 않았다. `공급업체와의 계약`과 `공급업체와의 계약이 필요함.`은 하나가 되지만, `개인정보 수집 동의`와 `개인정보 제3자 제공 동의`는 별도로 유지한다.

## SOURCE OWNERSHIP MATRIX

| source | 사용자 화면 소유 section |
| --- | --- |
| `requiredControls` | 특히 확인할 사항 / 반드시 해야 할 조치 |
| `requiredDisclosures` | 특히 확인할 사항 / 필수 고지 |
| partner·qualification 3개 source | 특히 확인할 사항 / 파트너·자격·인허가 |
| `unknownFacts` | 특히 확인할 사항 / 아직 확인되지 않은 사항 |
| roles·flows·privacy·physical | 사업 구조 검토 |
| `officialEvidenceReferences` | 관련 법률·규제 |
| `allowedClaims` | 광고 / 사용 가능한 표현 |
| advertising 고유 disclosure | 광고 / 함께 표시할 내용 |
| `prohibitedVariants` | 광고 / 피해야 할 표현 |
| `deltaLegalHistory` | 상세 검토 내용 |

특히 확인할 사항 → 광고 → 상세 검토 순으로 이미 소유된 semantic key를 제외한다. Backend JSON과 evidence는 변경하지 않는다.

## PDF BEFORE/AFTER

| 구분 | 이전 | V20 |
| --- | --- | --- |
| 3번 요약 DOM | 4개 metric `dl` grid | semantic `table` (`thead`/`tbody`) |
| A4 폭 | `repeat(4, minmax(0, 1fr))` | 2열 `항목 / 건수` |
| 줄바꿈 | metric cell 폭에 의존 | `table-layout: fixed`, `overflow-wrap:anywhere` |
| page break | 개별 card 중심 | table 전체 `break-inside/page-break-inside: avoid` |
| count | exact dedupe | 화면과 같은 semantic dedupe |
| 상세 문장 | exact dedupe | semantic dedupe + source ownership |

## PRINT TABLE CONTRACT

- `.legal-document__execution table`: `width:100%`, `border-collapse:collapse`, `table-layout:fixed`
- `th/td`: 명시 padding, bottom border, left alignment, 안전한 줄바꿈
- 4-column grid selector와 mobile 2-column override 제거
- 기존 A4 `@page`, skip-link/topbar/button redaction, 공식 법령 링크, generatedAt 기반 제안 파일명 유지

## DECISION STEP REACHABILITY MATRIX

| 단계 | 도달 조건 | 도달 후 동작 |
| --- | --- | --- |
| 1 사업안 선택 | `concepts.length > 0` | 후보 gallery 조회, 현재 선택 강조 |
| 2 분석 기준 확정 | selection 존재 | 확정 기준 전체 read mode |
| 3 법률·규제 확인 | report 존재 또는 legal stage 도달 | 풍부한 Legal 결과 조회 |
| 4 사업 검증 준비 | validation prep query 진입, `MARKET_SEED_FINALIZING`, `READY_FOR_MARKET` | 저장 BM Plan/자원 또는 진행 상태 조회 |

미도달 단계는 disabled button과 `aria-disabled=true`다. 도달 단계는 실제 button이고, 현재 단계는 `aria-current=step`, 완료 단계는 check로 표시한다. mobile은 세로 rail로 바뀌며 horizontal scroll을 만들지 않는다.

## READ-ONLY REVIEW CONTRACT

- 단계 클릭은 local `decisionView`와 query presentation만 바꾼다.
- 1단계 클릭은 자동 선택 변경 모드가 아니다. 카드의 선택 API action과 candidate continuation editor를 숨기고 현재 선택만 강조한다.
- 실제 변경은 별도 `선택 변경`을 눌렀을 때만 시작한다. Backend의 기존 selection/stale 계약을 그대로 사용한다.
- 2단계는 일반 조건 5개와 시장 목표의 점유율·기간·근거·금액·통화·계산 기준을 read mode로 표시한다.
- 3단계는 full Legal result를 표시한다.
- 4단계 READY 상태는 compact 완료 문구만 보여주지 않고 저장한 고객 관계·활동·자원·파트너·예산·기간·인원을 다시 표시한다.

## API ZERO-NAVIGATION TEST

READY_FOR_MARKET 상태에서 `4 → 3 → 2 → 1 → 3`을 클릭했다. `select`, hypothesis `confirm`, legal `finalizeReport`, market `finalizeMarketSeed` 호출이 모두 0회임을 component test로 고정했다. 저장 BM Plan 조회는 4단계 내용을 복원하기 위한 read-only GET이며 단계 클릭이 mutation을 만들지 않는다.

## TEST MATRIX

| 검증 | 결과 |
| --- | --- |
| semantic ending normalization | PASS |
| 다른 법률 요구 비병합 | PASS |
| 화면/PDF partner count 동일 | PASS |
| Legal 풍부한 기본 계층 | PASS |
| 광고/상세 cross-dedupe | PASS |
| 공식 근거 grouping/link/date 보존 | PASS |
| PDF semantic table/source contract | PASS |
| step reachability 1 / 1·2 / 1·2·3 / 1·2·3·4 | PASS |
| READY 4→3→2→1→3 mutation API 0 | PASS |
| step1 read-only + 별도 선택 변경 | PASS |
| step2 full basis read mode | PASS |
| step4 저장 BM Plan/constraints read mode | PASS |
| V20 표적 Vitest | 5 files, 65 tests PASS |
| V19 Idea 재제출 보호 Vitest | 3 files, 18 tests PASS |
| 변경 JS/JSX ESLint | PASS, warning 0 |
| production `pnpm build` | PASS, 281 modules |
| `git diff --check` | PASS |

전체 Frontend suite는 98 files 중 90 files, 554 tests 중 528 tests가 통과했다. 나머지 8 files/26 tests는 V20 변경 파일 밖의 기존 영문/과거 접근성·문구 기대값(Auth/App/Finance/Marketing 등) 불일치다. 전체 lint도 V20 밖의 `global` 미정의 2건과 polling dependency warning 2건으로 실패했으며, 변경 파일 lint는 통과했다.

## USER PDF REVIEW ITEMS

- Chrome/Edge `Save as PDF`에서 3번 summary table의 label/count가 겹치지 않는지
- 한글 줄바꿈과 A4 page break가 자연스러운지
- 공식 법령 링크가 PDF에서 클릭되는지
- skip-link/topbar/button이 출력되지 않는지
- 제안 파일명이 사업안명과 생성시각을 포함하는지

## 변경 파일 범위

- Business Proposal 상태/화면/model/tests/CSS
- Legal presentation helper/tests
- Legal 전용 PDF document/tests/print CSS
- Validation Prep 및 BM Plan read-only review/tests/CSS
- 본 RESULT와 USER VERIFICATION

## 남은 항목

- 실제 authenticated 화면과 Chrome/Edge PDF 저장 결과는 **USER REVIEW PENDING**이다.
- 큰 bundle warning(500kB 초과)은 기존 구조의 성능 후속 항목이며 V20 기능 실패는 아니다.
- 비관련 전체 suite/lint의 기존 실패는 별도 정리 대상이다.
