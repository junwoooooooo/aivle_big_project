# V15 사업안 결정·비교·법률·시장 기준값 UX 결과

## 상태

`PARTIAL / LIVE VISUAL HOLD`

코드 구현, V15 범위 테스트, 변경 파일 lint, production build는 완료했다. 다만 실제 인증 프로젝트 화면을 열 수 없어 1440×900, 1920×1080, 390×844 live visual gate는 통과로 선언하지 않는다.

## START SHA

| 항목 | 결과 |
|---|---|
| 브랜치 | `full` |
| START HEAD | `093299a7805b60e5a8c894bd97a4026029a4e222` |
| 로컬 `origin/full` | `093299a7805b60e5a8c894bd97a4026029a4e222` |
| 시작 worktree | clean |
| `git fetch origin full` | 네트워크 접근 제한으로 실패. 로컬 remote-tracking SHA를 기록했으며 fetch 성공으로 간주하지 않음 |

## READY STATE BEFORE/AFTER

| 구분 | Before | After |
|---|---|---|
| 기본 탐색 | `사업안 목록 / 비교` 탭 | `생성된 사업안을 살펴보세요` gallery |
| 비교 진입 | 2~3개를 탭 mode에서 비교 | 정확히 2개 선택 후 `두 사업안 비교` Focus View |
| 선택 후 | gallery, 기준값, 법률 결과가 계속 누적 | 선택 요약만 유지하고 서버 상태의 현재 단계만 full |
| 완료 후 | generic 다음 분석 안내 | `시장 분석 시작하기` 실제 destination CTA |

V14 pre-generation과 `ProjectExecutionExperience`는 변경하지 않았다.

## PROPOSAL PREVIEW MODEL

`buildProposalPreview(concept, allConcepts)`를 추가했다. AI 재호출 없이 저장된 candidate snapshot만 사용한다.

- 한 줄 정의: `conceptDefinition`, 없으면 기존 `summary`
- 후보 간 다른 값을 우선 탐지
- 최대 4개 highlight
- 차별 값이 부족하면 실제 존재하는 필드를 우선순위대로 보충
- 모든 텍스트는 카드에서 2줄 clamp하여 카드 높이 편차를 제한

## DIFFERENTIATION FIELD MATRIX

| 우선순위 | candidate field | 사용자 label | 차별 판정 |
|---|---|---|---|
| 1 | `targetUsers` | 주요 고객 | portfolio 내 정규화 값이 2종 이상 |
| 2 | `coreValue` | 핵심 가치 | 동일 |
| 3 | `solutionMechanism` | 제공 방식 | 동일 |
| 4 | `revenueModel` | 수익 방식 | 동일 |
| 5 | `price` | 가격·과금 | 동일 |
| 6 | `operatingModel` | 운영 방식 | 동일 |
| 7 | `featureSet` | 핵심 기능 | 동일 |
| 8 | `partnerRequirements` | 파트너 조건 | 동일 |

카드의 상세 법률 요약은 제거하고 `법률·규제 사전 검토 완료` 한 줄 상태만 유지했다.

## COMPARE EXACT-TWO CONTRACT

| 계약 | 결과 |
|---|---|
| 0 → 1 | 추가 |
| 1 → 2 | 추가 |
| 2 → 3 시도 | 기존 2개 유지 |
| 선택 해제 | 정상 제거 |
| `canOpenComparison` | 길이가 정확히 2일 때만 `true` |
| 3개 직접 입력 | `false` |

비교 picker는 document flow에 위치하며 sticky/floating overlay를 사용하지 않는다.

## COMPARISON INFORMATION HIERARCHY

초기 Focus View는 주요 고객, 해결하려는 문제, 핵심 가치, 제공 방식, 수익 방식, 운영상의 차이만 표시한다.

세부 disclosure는 다음 그룹으로 분리했다.

1. 서비스 구성: 핵심 기능, 사용 상황
2. 사업 운영: 플랫폼/제공/판매/중개 주체, 운영 방식
3. 거래와 수익: 거래/결제 흐름, 수익 모델, 가격
4. 운영 조건: 파트너, 개인정보, 물리 활동

법률 상세 비교 row는 제거했다. 데스크톱 matrix는 `9~10rem + 1fr + 1fr`이며 `min-width:max-content`와 horizontal scroll을 사용하지 않는다. 모바일에서는 기준 label 다음 A/B vertical pair로 전환한다.

## SELECTION DECISION FLOW

`businessDecisionStage(selection)`은 저장되지 않는 presentation helper이며 서버 selection status만 사용한다.

| 서버 authority | presentation stage | 펼쳐지는 영역 |
|---|---|---|
| selection 없음 | `PROPOSAL_SELECTION` | gallery |
| `HYPOTHESES_PREPARING`, `PENDING_HYPOTHESIS_CONFIRMATION` | `BUSINESS_BASIS` | 시장 분석 기준값 |
| `DELTA_LEGAL_PENDING`, `DELTA_LEGAL_FAILED`, `READY_FOR_LEGAL_REPORT`, `LEGAL_REPORT_READY`, `MARKET_SEED_FINALIZING` | `LEGAL_REVIEW` | 법률·규제 결과/처리 상태 |
| `READY_FOR_MARKET` | `MARKET_READY` | compact 완료 요약 + 시장 분석 CTA |

선택 직후 기준값 section에 focus/scroll한다. reduced motion에서는 smooth scroll을 사용하지 않는다. 기존 selection endpoint가 새 선택 생성, 이전 selection 및 파생물 stale 처리를 담당하므로 프런트는 별도 reset 상태를 만들지 않는다.

## HYPOTHESIS USER-LANGUAGE MATRIX

| 내부 enum | Before | After |
|---|---|---|
| `TARGET_REGION` | 목표 지역 | 사업 대상 지역 |
| `REVENUE_MODEL` | 수익 모델 | 수익을 만드는 방식 |
| `PRICE` | 가격 | 가격·과금 방식 |
| `CHANNELS` | 판매·제공 채널 | 고객에게 제공하는 경로 |
| `DIFFERENTIATORS` | 차별점 | 핵심 차별점 |
| `PRE_MARKET_SOM_SHARE` | 시장 점유 가정 | 목표 시장 점유율 |
| `PRE_MARKET_SOM` | 초기 확보 시장 규모 | 초기 목표 시장 규모 |
| status | 제안값 / 확인됨 / 확정된 사업 조건 | AI가 제안한 값 / 확인 완료 / 확정된 값 |

사용자 title은 `시장 분석에 사용할 기준값`, action은 `기준값 확인 완료`로 변경했다. 내부 Hypothesis enum/API는 유지했다.

## STRUCTURED INPUT LAYOUT MATRIX

| 화면 | 목표 점유율 | 초기 목표 시장 규모 | overflow 계약 |
|---|---|---|---|
| Desktop | 점유율/기간 2열 | 규모/통화/기간 + full-width 근거 | 모든 control과 grid child `min-width:0`, `width:100%`, `max-width:100%` |
| Tablet | market target panel 1열 | 내부 subfield는 공간에 맞춰 배치 | 바깥 panel 1열 |
| Mobile 390 | 모든 subfield 1열 | 모든 subfield 1열 | horizontal overflow를 유발하는 max-content 없음 |

기존 7-card auto-fit과 강제 structured 2-column selector는 제거했다.

## LEGAL GROUPING ALGORITHM

`groupLegalEvidence(evidence)`를 추가했다.

1. `lawName.trim()` 후 바깥 따옴표만 제거한다.
2. `lawName`이 없으면 `title`, 둘 다 없으면 `기타 법률 근거`를 사용한다.
3. 같은 법률명은 하나의 surface로 그룹화한다.
4. `lawName + articleReference + officialSourceUri + contentHash + title + boundedProvisionSummary`로 동일 evidence를 중복 제거한다.
5. 법률명 유사도 추정이나 자의적 병합은 하지 않는다.

법률 UI는 한눈에 보는 결과 → 특히 확인할 사항 → 사업 구조 역할 → 관련 법률·규제 → 광고/표현 주의 → 상세 근거 → 기술 정보 순이다.

## LEGAL EVIDENCE PRESERVATION MATRIX

| field | 보존 | 표시 위치 |
|---|---|---|
| `lawName` | 예 | 법률 group heading |
| `articleReference` | 예 | group chip + article disclosure |
| `title` | 예 | article disclosure title |
| `boundedProvisionSummary` | 예 | article 본문 |
| `effectiveDate` | 예 | article metadata |
| `officialSourceUri` | 예 | `법령 원문 보기` |
| `contentHash` | 예 | article metadata 및 dedupe key |

## RESPONSIVE MATRIX

| viewport | 예상 composition | 자동 검증 |
|---|---|---|
| 1920×1080 | 92rem bounded gallery/compare, 2개 matrix | CSS contract + build, live HOLD |
| 1440×900 | 2개 matrix, market target 2 panel | CSS contract + build, live HOLD |
| 390×844 | gallery 1열, comparison vertical pair, structured input 1열 | CSS contract test, live HOLD |

## ACCESSIBILITY MATRIX

| 항목 | 구현 |
|---|---|
| 비교 picker | semantic checkbox, disabled third choice |
| Focus View back | button + `aria-label="사업안으로 돌아가기"` |
| disclosure | button `aria-expanded`, `aria-controls`, controlled panel id |
| legal article | 동일 disclosure contract |
| decision progress | ordered list + current item `aria-current="step"` |
| motion | `prefers-reduced-motion`에서 animation/transition 최소화, scroll auto |

## TEST MATRIX

| 검사 | 결과 |
|---|---|
| V15 model/component targeted | PASS, 2 files / 32 tests |
| changed-file ESLint | PASS |
| frontend production build | PASS, Vite 273 modules |
| `git diff --check` | PASS |
| V15 금지 copy/legacy CSS grep | production source 0건, 부정 assertion test만 존재 |
| 전체 frontend test | BASELINE FAIL: 94 files 중 86 pass, 8 fail; 505 tests 중 479 pass, 26 fail. V15 관련 파일은 pass이며 실패는 Auth/App/MarketingVisual/기존 MarketSeed 등 비변경 영역 |
| backend selection contract tests | HOLD: Gradle 9.5.1 distribution 다운로드가 네트워크 제한으로 실패. 백엔드 파일 변경 없음 |

Build warning으로 기존 단일 chunk가 500kB를 넘는다는 Vite 경고가 있으나 V15 오류는 아니다.

## LIVE VISUAL MATRIX

| 화면 | 1440×900 | 1920×1080 | 390×844 | 상태 |
|---|---|---|---|---|
| 사업안 3개 gallery | 미검증 | 미검증 | 미검증 | HOLD |
| exact-two 비교 Focus View | 미검증 | 미검증 | 미검증 | HOLD |
| 선택 후 collapse | 미검증 | 미검증 | 미검증 | HOLD |
| 시장 기준값 | 미검증 | 미검증 | 미검증 | HOLD |
| 법령 grouping | 미검증 | 미검증 | 미검증 | HOLD |

인앱 브라우저로 `http://127.0.0.1:5173` 접속은 성공했으나 인증 세션이 없어 랜딩 화면만 확인했다. 연결된 Chrome 브라우저는 제공되지 않았다. 실제 프로젝트의 business proposal route와 데이터에 접근하지 못했으므로 visual PASS를 기록하지 않는다.

## REMAINING GAP

1. 인증된 실제 프로젝트에서 사업안 3개 이상 준비 상태를 열어 1440×900, 1920×1080, 390×844 캡처와 bounding rect를 확인해야 한다.
2. 비교 Focus View의 `scrollWidth === clientWidth`, 문서 전체 horizontal overflow 0을 live 측정해야 한다.
3. structured market input의 각 control rect가 부모 content box를 넘지 않는지 live 측정해야 한다.
4. 법령 grouping이 실제 저장 report의 lawName 변형과 긴 조항 제목에서도 자연스러운지 확인해야 한다.
5. 기존 전체 프런트 테스트 기준선 26개 실패는 V15 범위 밖에서 별도 정리가 필요하다.
6. 네트워크 가능한 환경에서 backend selection contract test를 재실행해야 한다.

위 항목이 남아 있으므로 V15를 `COMPLETE`로 선언하지 않는다.
