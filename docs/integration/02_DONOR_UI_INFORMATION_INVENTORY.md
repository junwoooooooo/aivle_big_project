# Donor UI Information Inventory

아래 행은 레이아웃 지시가 아니라 정보 보존 계약이다. Target 디자인 시스템으로 재배치할 수 있지만 행의 제목, 값, 근거, 경계, 상태, action, empty/failure 의미를 삭제·통합·축약하면 안 된다.

`PRESERVE_REQUIRED`는 이번 이식에서 전부 `YES`다. `TARGET_DESTINATION`은 Target Project Shell 안의 기능 화면/상세 drawer를 뜻한다.

## 1. Market

| USER_VISIBLE_ITEM | SOURCE_FILE | SOURCE_SECTION | TARGET_DESTINATION | PRESERVE_REQUIRED |
|---|---|---|---|---|
| 제목 `시장조사 결과`, 단계 표시 | `donor-market/frontEnd/src/features/market/MarketResearchPage.jsx` | page header | Market workspace header | YES |
| 시장조사 실행/다시 조사/조사 중/경과시간 | 같은 파일 | header actions | Market run action + Work Center 상태 | YES |
| 견본 Concept 현재값과 다시 고르기 | 같은 파일 | `ConceptPicker`, sample card | 개발 fixture 표시 영역; production selected Concept로 대체 | YES |
| 로딩 상태 | 같은 파일 | `LoadingState` | canonical current loading | YES |
| API 오류 alert | 같은 파일 | top-level error | Market failure banner | YES |
| 실행 실패 code, retryable이면 재시도 가능/아니면 입력 확인 | 같은 파일 | failed run alert | Work Center + Market failure state | YES |
| 미실행 empty state와 실행 안내 | 같은 파일 | empty Card | Market empty state | YES |
| KPI: TAM, SAM, 연 성장률, 가격 범위 | 같은 파일 | `Kpis` | Market summary KPI row | YES |
| KPI의 원값/축약값/등급 | 같은 파일 | `Kpis`, `GradeBadge` | 각 KPI와 같은 카드 | YES |
| 등급: 확정/실무 신뢰/추정/근거 없음/등급 표기 없음 | `marketResult.js` | `GRADE_VIEW` | 모든 수치/근거 badge | YES |
| 가정 원장 제목별 항·값·판정·근거 | `AssumptionLedger.jsx` | `FigureLedger` | Market assumption ledger | YES |
| 가정 factor의 출처 domain 수와 건수 | 같은 파일 | factor source summary | ledger factor detail | YES |
| 가정을 읽는 경계/caveat 문장 | 같은 파일 | ledger boundary copy | ledger 하단, 접지 않음 | YES |
| 1. 시장 크기 | `MarketResearchPage.jsx` | `MARKET_SIZE` section | Market section 1 | YES |
| 모집단 관측 없음 empty | 같은 파일 | Market size empty | section inline empty | YES |
| 2. 성장률, 연 성장률 값/식 | 같은 파일 | `GrowthBody` | Market section 2 | YES |
| 성장률 미산출, assumptions/caveats | 같은 파일 | `GrowthBody` | 값 옆 failure/boundary | YES |
| 3. 경쟁사별 지표/값/출처 | 같은 파일 | `CompetitorBody` | Market section 3 table/cards | YES |
| 경쟁사별 못 찾은 metric | 같은 파일 | `CompetitorBody` | 각 경쟁사 카드 inline | YES |
| 경쟁사 관측 없음 | 같은 파일 | competitor empty | section inline empty | YES |
| 기능·차별점 비교가 조사 항목에 없다는 caveat | 같은 파일 | competitor note | 경쟁사 section note | YES |
| 4. 가격 최소/기준(중앙)/최대 | 같은 파일 | `PriceBody` | Market section 4 | YES |
| 가격 관측 없음 | 같은 파일 | price empty | section inline empty | YES |
| 같은 domain 다건은 독립 다중 확인이 아니라는 warning | 같은 파일 | source-host warning | 가격 근거 아래 | YES |
| 5. 수요 근거와 응답/문서 quote | 같은 파일 | `DEMAND`, `EvidenceTable quote` | Market section 5 | YES |
| 수요 관측 없음 | 같은 파일 | demand empty | section inline empty | YES |
| Evidence 표: 값/항목/기간/등급/출처 | 같은 파일 | `EvidenceTable` | 공통 evidence table | YES |
| Evidence별 quote, caveat, source link | 같은 파일 | `EvidenceTable`, `SourceLink` | 같은 evidence row | YES |
| 핵심 요약 문장, cell 이름, card ids | 같은 파일 | summary Card | Market summary | YES |
| 6. 시장 규모 계산 | 같은 파일 | `CalcBody` | Market section 6 | YES |
| 계산 카드: metric/value/unit/formula | 같은 파일 | `CalcBody` | calculation card | YES |
| 계산 입력명/값 표 | 같은 파일 | `CalcBody` input table | calculation detail | YES |
| 계산 재료 id 및 assumptions | 같은 파일 | calculation note | calculation evidence detail | YES |
| 관측 재료 없음—전부 가정 계산 warning | 같은 파일 | calculation warning badge | calculation card | YES |
| 계산 카드 없음 | 같은 파일 | calculation empty | section inline empty | YES |
| 7. 못 찾은 것 | 같은 파일 | `NotFoundBody` | Market section 7 | YES |
| 아직 못 채움/가정으로 메움/찾아도 없음/규격 미달/값 갈림 5갈래 | `marketResult.js` | `NOT_FOUND_GROUP` | Not-found grouped panels | YES |
| 각 갈래 note, 항목명, 건수, 상세 목록 | `MarketResearchPage.jsx` | `NotFoundBody` | grouped panel body | YES |
| not-found 기록 없음 empty | 같은 파일 | not-found empty | section inline empty | YES |
| 7과목 score state/detail | 같은 파일 + `marketResult.js` | `Section`, `SCORE_STATE_VIEW` | 각 section header | YES |
| 다음—BM 분석 action | 같은 파일 | bottom action | Market→BM handoff action | YES |

## 2. BM

| USER_VISIBLE_ITEM | SOURCE_FILE | SOURCE_SECTION | TARGET_DESTINATION | PRESERVE_REQUIRED |
|---|---|---|---|---|
| 제목 `실행 계획 확인`, “이것만 더 필요” 설명 | `donor-market/frontEnd/src/features/market/BmCanvasPage.jsx` | `PlanPhase` | BM preparation header | YES |
| 이미 확정한 수익모델·채널·차별점·가격은 다시 묻지 않는다는 설명 | 같은 파일 | PlanPhase copy | BM preparation note | YES |
| 고객 관계 질문/예시 | `BmPlanForm.jsx`, `bmPlan.js` | `PLAN_FIELDS` | BM plan form | YES |
| 핵심 활동 질문/여러 줄 예시 | 같은 파일 | `PLAN_FIELDS` | BM plan form | YES |
| 핵심 자원 질문/여러 줄 예시 | 같은 파일 | `PLAN_FIELDS` | BM plan form | YES |
| 핵심 파트너 질문/“계약 상대가 아닌 필요한 유형” warning | 같은 파일 | `PLAN_FIELDS` | BM plan form | YES |
| 비용 구조 입력: 예산(원)/기간(개월)/인원(명) | `bmPlan.js` | `CONSTRAINT_FIELDS` | BM plan form | YES |
| 저장 중/저장하고 캔버스 만들기 | `BmPlanForm.jsx` | submit action | BM plan action | YES |
| 계획 저장 실패 | `BmCanvasPage.jsx` | plan failure | BM plan error | YES |
| 빈 칸 확인 dialog, 비워서 진행했을 때의 의미 | 같은 파일 | pending empty dialog | BM confirmation dialog | YES |
| 지금까지 채운 캔버스 preview | `BmPlanPreview.jsx` | preview header | BM preparation preview | YES |
| 관측 4칸은 실행 후 시장 근거가 채운다는 note | 같은 파일 | `OBSERVED_NOTE` | preview observed cells | YES |
| 제목 `비즈니스 모델 캔버스`와 근거 없는 칸은 비우고 사유를 적는 설명 | `BmCanvasPage.jsx` | result header | BM workspace header | YES |
| 다시 계획하기/캔버스 만들기/다시 생성/생성 중 | 같은 파일 | result actions | BM actions + Work Center | YES |
| 생성 API 오류/실패 code | 같은 파일 | alerts | BM failure state | YES |
| 판정 decision/confidence/summary | 같은 파일 | result decision Card | BM decision summary | YES |
| BM 판정 없음—시장조사는 유효하며 재생성 가능 | 같은 파일 | missing BM alert | partial-result state | YES |
| market fit 상태/요약 | 같은 파일 + `marketResult.js` | BM result | BM decision detail | YES |
| consistency 상태/요약 | 같은 파일 + `marketResult.js` | BM result | BM decision detail | YES |
| 강점/약점/위험 목록과 없음 상태 | 같은 파일 | `SwrBox` | BM SWR cards | YES |
| BMC 9칸: 고객 세그먼트/가치 제안/채널/고객 관계/핵심 활동/자원/파트너/수익원/비용 구조 | `marketResult.js`, `BmCanvas.jsx` | `CANVAS_BANDS` | BM canvas | YES |
| 칸 종류: 관측/계획과 근원 설명 | `marketResult.js` | `CELL_KIND` | 각 canvas cell | YES |
| 칸 상태: 확인됨/일부 확인/미확인/계획(근거 없음)/진행 불가 | 같은 파일 | `CELL_STATUS_VIEW` | 각 canvas cell | YES |
| 관측 칸 근거 확보 수 tally | `BmCanvas.jsx` | `Tally` | BM canvas summary | YES |
| 칸별 전체 내용 | 같은 파일 | `CellDetail` | BM cell detail | YES |
| 빈 칸 사유: 컨셉 서술 없음/조사 근거 못 찾음 | 같은 파일 | `emptyReason` | BM cell detail | YES |
| 칸별 model reason/source labels | 같은 파일 | `CellDetail` | BM cell detail | YES |
| 칸별 evidence 목록/출처/grade | 같은 파일 | `CellDetail` evidence | BM cell evidence | YES |
| 칸별 caveats | 같은 파일 | caveat render | 같은 cell, 숨기지 않음 | YES |
| 칸별 missing evidence `못 찾은 것` | 같은 파일 | missing evidence | 같은 cell | YES |
| legal used/status/summary/risks/required actions | `ai/app/research/serialize.py` | `bm()` result contract | BM legal detail section | YES |
| financial handoff status/누락 재무 입력 | `ai/app/research/bm/handoff.py` | `BMFinancialHandoff` | BM→Finance handoff detail | YES |

## 3. Twin Survey

| USER_VISIBLE_ITEM | SOURCE_FILE | SOURCE_SECTION | TARGET_DESTINATION | PRESERVE_REQUIRED |
|---|---|---|---|---|
| 진행 단계(비교안 준비/표본 선택/패널 응답/결과)와 완료/경과시간 | `donor-market/frontEnd/src/features/twin-survey/TwinSurveyPage.jsx` | `TwinSteps` | Twin workspace progress | YES |
| `무엇을 비교할까` | 같은 파일 | setup Card | Twin setup | YES |
| 확정 Concept에서 비교 두 안 생성 설명/초안 만들기 | 같은 파일 | draft-start | Twin stimulus draft action | YES |
| sample Concept 선택과 직접 입력 action | 같은 파일 | sample/manual controls | 개발 fixture/direct edit controls | YES |
| 초안 생성 중/실패 reason/재시도 | 같은 파일 + `draftFailureText.js` | draft state | Twin draft failure state | YES |
| 초안 후보별 axis/rationale/X/Y 값, 선택 | `StimulusDraftPicker.jsx` | draft cards | Twin draft picker | YES |
| 조사 상황 문장 편집 | `StimulusEditor.jsx` | situation field | Twin stimulus editor | YES |
| 자극쌍 카드 axis/X/Y, serviceable 상태/reason | 같은 파일 | pair cards/gate | Twin pair editor list | YES |
| 막힌 질문 수와 수정 안내 | 같은 파일 | blocked gate alert | Twin setup warning | YES |
| 자극쌍 없음 empty | 같은 파일 | editor empty | Twin setup empty | YES |
| 비교안 편집: 비교축, A/B 이름과 값, 저장 | `PairEditorDialog.jsx` | dialog | Twin pair editor dialog | YES |
| 가격 칸을 임의 편집하지 않는 의미 | `StimulusEditor.jsx` | pair editing contract | UI 행동으로 보존 | YES |
| 표본 카드 | `TwinSurveyPage.jsx` | sample Card | Twin sample setup | YES |
| 가상 표본 수 50/100/300 slider와 현재 명수 | `SampleSizePicker.jsx` | picker | Twin sample selector | YES |
| 예상 응답 회수/시간 | 같은 파일 | cost line | Twin sample detail | YES |
| 표본별 측정 한계 warning | 같은 파일 + `sampleSize.js` | plan warning | Twin sample warning | YES |
| 응답 수 계산식과 양방향 제시 이유 | 같은 파일 | sample note | Twin sample note | YES |
| 조사 실행/다시 조사/조사 중/경과시간 | `TwinSurveyPage.jsx` | action area | Twin run action + Work Center | YES |
| API 오류/실행 실패와 code별 사람말 | 같은 파일 | alerts/`failureText` | Twin failure state | YES |
| caveat 누락 쌍 warning과 “그대로 인용하지 마라” | 같은 파일 | result warning | Twin result top warning | YES |
| pair 제목 X↔Y, 조사 상황, 완료 표본 수 | 같은 파일 | `PairPanel` header | Twin pair result | YES |
| X/Y profile 문장 | 같은 파일 | comparison profiles | Twin pair result | YES |
| 우세 또는 `판정 불가—못 잼` | 같은 파일 | verdict headline | Twin pair result | YES |
| 선호 구성 X/Y/미결정 수와 비율 bar | 같은 파일 | composition bar | Twin pair result | YES |
| decision reason | 같은 파일 | verdict reason | Twin pair result | YES |
| 대표 응답자 인터뷰: profile 요약/선택 badge/quote | 같은 파일 | interview cards | Twin pair interviews | YES |
| 인터뷰 quote 없음 empty | 같은 파일 | interview empty | Twin pair interviews | YES |
| 측정치 상세: Δ, CI, MDE, 위치성분, content share, 확정/전체 응답자 | 같은 파일 | measurement details | Twin pair expandable detail | YES |
| 응답자 class별 명수 | 같은 파일 | class list | Twin pair measurement detail | YES |
| short cells/실효표본 warning | 같은 파일 | short-cell note | Twin pair measurement detail | YES |
| pair caveats와 caveat missing fallback | 같은 파일 + `twinSurveyResult.js` | caveats | 같은 pair 또는 페이지 각주 | YES |
| KISDI 실측 프로파일 기반 synthetic response 일반 한계 | 같은 파일 | `TwinFootnote` | Twin page footnote | YES |
| 쌍별 caveat 중복 제거 집합 | 같은 파일 | `TwinFootnote` | Twin page footnote | YES |

## 4. Finance

| USER_VISIBLE_ITEM | SOURCE_FILE | SOURCE_SECTION | TARGET_DESTINATION | PRESERVE_REQUIRED |
|---|---|---|---|---|
| 재무 분석 준비/BM 완료 필요/error | `donor-mini/frontEnd/src/features/finance/pages/FinancePage.jsx` | no preparation state | Finance empty/input-needed state | YES |
| 제목/단계/입력 확정·확정 준비·N개 입력 필요 | 같은 파일 | finance header | Finance workspace header | YES |
| 기술·운영 전달값 확인/부족 항목 입력 설명 | 같은 파일 | header copy | Finance header | YES |
| 시장 규모·성장률·가격 가설 및 BM 근거 | 같은 파일 | source section | Finance upstream snapshot section | YES |
| BM Run id/연결 대기/가정 미확정 note | 같은 파일 | source footer | Finance upstream detail | YES |
| 연간 고정비 세부항목 | 같은 파일 + `financeModel.js` | fixed cost | Finance input section | YES |
| 초기 투자 세부항목 | 같은 파일 + `financeModel.js` | initial investment | Finance input section | YES |
| 3개년 목표 metric/unit/1~3년 값 | 같은 파일 | targets | Finance input section | YES |
| 수익모델 one-time/subscription/mixed 및 가격/반복매출 가정 | 같은 파일 | revenue model | Finance input section | YES |
| 월 이탈률 등 조건부 필드 | 같은 파일 | conditional fields | Finance input section | YES |
| CAC 비용 구성과 신규 고객 수 | 같은 파일 | CAC | Finance input section | YES |
| CAC 직접 입력 금지/시스템 계산 CAC | 같은 파일 | CAC note/result | Finance input section | YES |
| 필드 source: BM/시장 가설, TechOps 상속, 사용자 입력 | 같은 파일 | source labels | 각 input field | YES |
| 값/단위/source/가정/evidence detail | 같은 파일 | field details | 각 input field detail | YES |
| 입력 도움말/예시/AI 추정 범위 | 같은 파일 | assistance | Finance assistance panel | YES |
| AI 추천 대상과 신규 고객 수 제외 설명 | 같은 파일 | AI scope | Finance assistance panel | YES |
| 추천 생성 중/실패/AI 추천/추천 없음 | 같은 파일 | estimate status | 각 assisted field | YES |
| AI 추천값, 산정 근거, 가정, confidence | 같은 파일 | recommendation | 각 assisted field | YES |
| AI 추천 받기/채택/거절/다른 추천 요청 | 같은 파일 | estimate controls | 각 assisted field | YES |
| 재무 입력 저장 | 같은 파일 | save action | Finance actions | YES |
| Snapshot 확정 가능/필수 입력 N개 남음 | 같은 파일 | finalize status | Finance snapshot panel | YES |
| 입력 Snapshot 확정/입력 수정/Handoff 준비 | 같은 파일 | snapshot actions | Finance snapshot actions | YES |
| 외부 연결 상태와 stale 표시 | 같은 파일 | run status | Finance Work Center inline | YES |
| 분석 실행 및 보고서 생성/실행 중 | 같은 파일 | analysis action | Finance run action | YES |
| 최종 보고서 headline | 같은 파일 | `FinanceAnalysisResult` | Finance result header | YES |
| 누적 매출/영업이익/필요 운전자금/BEP KPI | 같은 파일 | result metrics | Finance result KPI | YES |
| 3개년 손익표: 매출/원가/매출총이익/판관비/영업이익/영업외/세금/순이익/영업이익률 | 같은 파일 | section 1 | Finance result table | YES |
| 현금흐름 및 BEP 설명 | 같은 파일 | section 2 | Finance result | YES |
| 월별 매출·영업이익 line chart | 같은 파일 | section 2 chart | Finance result chart | YES |
| 누적 현금흐름 chart | 같은 파일 | section 2 chart | Finance result chart | YES |
| 월별 상세 표 | 같은 파일 | details table | Finance result expandable table | YES |
| 시나리오 스트레스/Monte Carlo | 같은 파일 | section 3 | Finance risk section | YES |
| P10/P50/P90, 손실/회수 확률, simulation/seed | 같은 파일 + response contract | Monte Carlo metrics | Finance risk section | YES |
| 보수/기준/낙관 누적현금흐름 chart 및 scenario별 BEP/필요자금 | 같은 파일 | scenario chart | Finance risk section | YES |
| AI 핵심 제언/findings | 같은 파일 | section 4 | Finance recommendations | YES |
| 주의할 리스크 | 같은 파일 | cautions | Finance recommendations | YES |
| 성공 확률 극대화 전략 | 같은 파일 | recommended actions | Finance recommendations | YES |
| disclaimer/외부 데이터 및 참조 출처 부재 설명 | `FinancialModulePage.jsx` | report section 5 | Finance report footer/source section | YES |
| sandbox 제목/입력/금액단위/최종 분석/오류/결과 | `FinancialModulePage.jsx` | full page | 별도 dev surface로 보존 여부 후속 결정 | YES |
| demo 가짜 데이터 실행 action과 오류 | `FinancePage.jsx` | `FinanceDemoFallback` | dev-only 상태로 분리 | YES |

## 5. AIdev / Marketing Content / Visual

| USER_VISIBLE_ITEM | SOURCE_FILE | SOURCE_SECTION | TARGET_DESTINATION | PRESERVE_REQUIRED |
|---|---|---|---|---|
| 제목 `확정된 Concept를 실제 콘텐츠로`와 source 설명 | `donor-aidev/frontEnd/src/features/marketing-content/pages/MarketingContentPage.jsx` | page header | Marketing Content header | YES |
| 새로고침 | 같은 파일 | header action | Marketing actions | YES |
| Marketing Source 없음 empty와 선행 조건 설명 | 같은 파일 | empty state | Marketing empty state | YES |
| 생성 중/확인 필요/Preview와 최신 JobEvent 메시지 | 같은 파일 | generation status | Marketing run status | YES |
| API/Task/provider 오류의 안전한 사람말 | 같은 파일 + `marketingContentModel.js` | failure mapping | Marketing failure state | YES |
| Marketing Source Snapshot 제목/기준 시각 | `MarketingSourceSummary.jsx` | source panel | Marketing source panel | YES |
| 선택 Concept/대상 고객/핵심 가치/positioning/features/channels/differentiators | 같은 파일 + model | source fields | Marketing source panel | YES |
| allowed/prohibited claims, required disclosures/controls | 같은 파일 + model | legal source fields | Marketing source/legal panel | YES |
| 콘텐츠 유형: social/ad/landing/blog/email/banner/poster/image brief | `marketingContentModel.js` | content types | Marketing setup | YES |
| 채널/목적/톤/길이/CTA/키워드 입력 | `MarketingSetupPanel.jsx` | setup fields | Marketing setup | YES |
| 콘텐츠 생성/요청 중 | 같은 파일 | setup action | Marketing run action | YES |
| Preview style: 테마/정렬/강조색/글자 크기 | `MarketingStylePanel.jsx` | style controls | Marketing preview controls | YES |
| 생성 결과 title/body/CTA/hashtags/image brief | `MarketingCanvas.jsx` | preview | Marketing preview | YES |
| 생성 결과 없음 empty | 같은 파일 | empty canvas | Marketing preview empty | YES |
| Copy Editor: headline/body/CTA/hashtags/image 설명/필수 고지 | `MarketingCopyEditor.jsx` | editor | Marketing editor | YES |
| 짧게 다듬기/필수 고지 적용 | 같은 파일 | editor tools | Marketing editor actions | YES |
| 법률 표현 확인: 저장 차단/검토 경고/차단 없음 | `MarketingContentPage.jsx` | legal section | Marketing legal guard | YES |
| 저장된 콘텐츠 수/empty/list | `MarketingContentList.jsx` | list | Marketing content list | YES |
| status: 대기/생성 중/편집 가능/실패/최종 저장/stale | 같은 파일 | status labels | Marketing content cards | YES |
| source id/최종 저장 여부/최근 수정/stale source | 같은 파일 | content card detail | Marketing content cards | YES |
| Revision type/origin(AI/사용자/시스템) | `MarketingRevisionList.jsx` | revision list | Marketing history | YES |
| 복사/다운로드/편집본 저장/새 초안/최종 저장 | `MarketingContentPage.jsx` | footer actions | Marketing actions | YES |
| Visual 제목 `AI 광고 배너 생성`과 설명 | `donor-aidev/frontEnd/src/page/VirtualMarket.jsx` | `MarketingCreateSection` | Marketing Visual workspace | YES |
| 프로모션 이름 | 같은 파일 | banner form | Visual setup | YES |
| 메인 배너 문구/보조 문구 | 같은 파일 | banner form | Visual setup | YES |
| 광고 분위기 7종 | 같은 파일 | tone select | Visual setup | YES |
| 배너 형식: 가로/정사각 SNS/세로 모바일 | 같은 파일 | size select | Visual setup | YES |
| 강조 키워드 | 같은 파일 | keywords | Visual setup | YES |
| PNG/JPG, 10MB 제한, 업로드/preview/파일명/제거 | 같은 파일 | `MarketingImageUpload` | Visual asset input | YES |
| 등록 상품 수/상품 카드/카테고리/요약/features/선택 상태 | 같은 파일 | product picker | Visual product source | YES |
| 필수값 validation alert | 같은 파일 | `handleGenerateBanner` | Visual input-needed state | YES |
| 광고 배너 생성 | 같은 파일 | form action | Visual TaskRun action | YES |
| 생성 진행 3단계와 취소 | 같은 파일 | generation screen | Visual Work Center/progress | YES |
| 생성된 배너 preview: tone/promotion/product/headline/subtext/CTA | 같은 파일 | result preview | Visual result | YES |
| 선택 상품/상품 target/광고 분위기 결과 요약 | 같은 파일 | result facts | Visual result detail | YES |
| 다시 만들기/광고 배너 저장 | 같은 파일 | result actions | Visual actions | YES |

## 6. 보존 검증 규칙

- 화면별 구현 완료 검토 시 이 표의 행을 체크리스트로 사용한다.
- caveat/evidence/warning/not measurable/partial result는 값과 같은 화면 문맥에 둔다.
- 단순 카드 합치기로 제목이나 상태를 없애지 않는다.
- donor에 없는 새 Journey, Persona surface는 이 행렬에 추가하지 않는다.

## 7. Session 2 Market/BM/Twin 이식 상태

아래 표는 위 원본 행을 삭제하거나 축약하지 않고, 각 `PRESERVE_REQUIRED=YES` 행의 이식 상태를 1:1로 기록한 companion matrix다.

### 7.1 Market

| USER_VISIBLE_ITEM | STATUS | TARGET 구현 |
|---|---|---|
| 제목 `시장조사 결과`, 단계 표시 | PORTED | `MarketResearchPage.jsx` header |
| 실행/다시 조사/조사 중/경과시간 | PORTED | TaskRun 상태 + 로컬 경과시간 |
| 공식 current selected Concept authority 표시 | PORTED | 조사 기준 카드의 Concept/selection revision/Seed |
| 개발용 sample Concept picker | PORTED | DEV + `VITE_MARKET_FIXTURE_MODE`에서만 표시 |
| API 오류 alert | PORTED | top-level danger alert |
| 실패 code, retryable/입력 확인 | PORTED | failure alert |
| 미실행 empty state/실행 안내 | PORTED | empty Card |
| KPI TAM/SAM/성장률/가격 범위 | PORTED | KPI row |
| KPI grade/근거 상태 | PORTED | Grade badge와 section jump |
| 가정 원장 제목/항/값/판정/근거 | PORTED | `AssumptionLedger.jsx` |
| 1. 시장 크기 | PORTED | section 1 |
| 모집단 관측 없음 | PORTED | inline empty |
| 2. 성장률 값/식 | PORTED | section 2 |
| 3. 경쟁사 지표/값/출처 | PORTED | section 3 |
| 경쟁사 관측 gap | PORTED | competitor gaps |
| 4. 가격 최소/기준/최대 | PORTED | section 4 |
| 가격 base kind/note/grade/evidence/caveat | PORTED | price body |
| 5. 수요 근거/quote | PORTED | section 5 evidence table |
| evidence 값/항목/기간/grade/source link | PORTED | `EvidenceTable`/`SourceLink` |
| evidence caveat | PORTED | 값과 같은 table cell |
| 핵심 요약/cell/card ids | PORTED | summary Card |
| 6. 시장 규모 계산 | PORTED | section 6 |
| calculation formula/input/result/assumption/caveat | PORTED | calculation cards |
| 7. 못 찾은 것 | PORTED | section 7 |
| not-found 5분류/note/건수/목록 | PORTED | grouped not-found panels |
| 7개 section score state/detail | PORTED | section header badge/detail |
| partial/missing 의미 | PORTED | score state와 empty/caveat |
| Market→BM action | PORTED | bottom action |

### 7.2 Business Model

| USER_VISIBLE_ITEM | STATUS | TARGET 구현 |
|---|---|---|
| `실행 계획 확인`과 설명 | PORTED | BM preparation header |
| 상위 확정 수익모델/채널/차별점/가격 재질문 없음 안내 | PORTED | PlanPhase copy |
| 고객 관계 질문/예시 | PORTED | `BmPlanForm` |
| 핵심 활동 질문/여러 줄 예시 | PORTED | `BmPlanForm` |
| 핵심 자원 질문/여러 줄 예시 | PORTED | `BmPlanForm` |
| 핵심 파트너 질문/warning | PORTED | `BmPlanForm` |
| 예산/기간/인원 constraint | PORTED | constraint inputs |
| 저장 중/저장하고 캔버스 만들기 | PORTED | submit action |
| 계획 저장 실패 | PORTED | plan failure alert |
| 빈 칸 confirm dialog/의미 | PORTED | pending-empty dialog |
| 현재 preview | PORTED | `BmPlanPreview` |
| observed cell 안내 | PORTED | preview 설명 |
| `비즈니스 모델 캔버스` 제목/근거 없는 칸 설명 | PORTED | result header |
| 다시 계획/만들기/재생성/생성 중 | PORTED | BM actions + TaskRun state |
| API 오류/실패 code | PORTED | alerts |
| decision/confidence/summary | PORTED | verdict Card |
| BM 판정 없음 partial-result | PORTED | warning alert |
| market fit 상태/요약 | PORTED | verdict detail |
| consistency 상태/요약 | PORTED | verdict detail |
| strength/weakness/risk와 없음 상태 | PORTED | SWR cards |
| BMC 9 cells | PORTED | `BmCanvas` |
| observed/planned status/kind | PORTED | cell status/kind |
| 관측 칸 근거 tally | PORTED | canvas tally |
| 칸별 전체 내용 | PORTED | `CellDetail` |
| 빈 칸 사유 | PORTED | `emptyReason` |
| model reason/source labels | PORTED | cell detail |
| evidence/source/grade | PORTED | cell evidence |
| marketEvidenceIds/missingEvidence/caveats | PORTED | cell detail, caveat 유지 |
| legal used/status/summary/risks/actions | PORTED | 법률 결과 반영 Card |
| financial handoff 전체 값/상태/누락 입력 | PORTED | 재무 전달정보 Card |

### 7.3 Twin Survey

| USER_VISIBLE_ITEM | STATUS | TARGET 구현 |
|---|---|---|
| 진행 단계/완료/경과시간 | PORTED | `TwinSteps` |
| `무엇을 비교할까` | PORTED | setup Card |
| current selected Concept 표시/초안 만들기 | PORTED | source 문구 + async action |
| 개발용 sample Concept 표시 | PORTED | DEV + `VITE_TWIN_FIXTURE_MODE` 한정 |
| 초안 생성 중/실패 reason/재시도 | PORTED | draft TaskRun state/failure |
| 후보 axis/rationale/X/Y/선택 | PORTED | `StimulusDraftPicker` |
| 상황 문장 편집 | PORTED | `StimulusEditor` |
| pair axis/X/Y/serviceable/reason | PORTED | editor cards/gate |
| 막힌 질문 수/수정 안내 | PORTED | blocked gate alert |
| 자극쌍 없음 empty | PORTED | setup empty |
| pair editor axis/A/B/값/저장 | PORTED | `PairEditorDialog` |
| 표본 카드 | PORTED | sample Card |
| 표본 50/100/300/current | PORTED | `SampleSizePicker` |
| 예상 응답/시간 | PORTED | sample cost line |
| 표본별 MDE warning | PORTED | sample warning |
| 응답 계산식/양방향 이유 | PORTED | sample note |
| 실행/다시 조사/조사 중/경과시간 | PORTED | survey action + TaskRun |
| API 오류/실패 code 사람말 | PORTED | alerts/failureText |
| caveat 누락/인용 금지 warning | PORTED | result warning |
| pair X↔Y/상황/완료 표본 | PORTED | pair header |
| X/Y profile 문장 | PORTED | profiles |
| 우세 또는 판정 불가—못 잼 | PORTED | verdict headline |
| X/Y/미결정 수·비율 bar | PORTED | composition bar |
| decision reason | PORTED | verdict reason |
| 대표 인터뷰 profile/choice/quote | PORTED | interview cards |
| 인터뷰 quote 없음 | PORTED | interview empty |
| Δ/CI/MDE/위치성분/content share/응답자 | PORTED | measurement detail |
| respondent class별 명수 | PORTED | class list |
| short cells/실효표본 warning | PORTED | short-cell note |
| KISDI synthetic limitation | PORTED | footnote |
| 쌍별 caveat 중복 제거 집합 | PORTED | `TwinFootnote` |

Session 2 범위의 `NOT_PORTED` 행은 없다.

### 7.4 Finance (Session 3)

기존 4절의 공식 Finance 행을 삭제·축약하지 않고 구현 상태를 대응시켰다.

| USER_VISIBLE_ITEM 묶음 | STATUS | TARGET 구현 |
|---|---|---|
| 준비 실패·BM 완료 필요·필수 입력·Snapshot 상태 | PORTED | `FinancePage.jsx` empty/header/finalize state |
| Market 규모·성장률·가격, BM financial handoff, TechOps Snapshot과 정확한 version/id | PORTED | upstream source card와 전체 Evidence/Caveat 상세 |
| 고정비·초기투자·3개년 목표·수익모델·가격·이탈률·CAC·조건부 원가 | PORTED | Finance preparation 입력 sections |
| source/provenance, 사용자 입력, Market/BM 가정, AI estimate 구분 | PORTED | field source labels, recommendation detail, upstream detail |
| AI 추천 상태·값·근거·가정·confidence·받기·채택·수정채택·거절·대안 요청 | PORTED | `EstimateControls`, `Recommendation` |
| 저장·확정·입력 수정(reopen)·handoff·stale | PORTED | Snapshot/action panels와 stale warning |
| 분석 실행·TaskRun 진행·실패·retryable·AI report fallback | PORTED | analysis action/status/error/fallback warning |
| 누적 매출·영업이익·필요 운전자금·BEP KPI | PORTED | `AnalysisReport.jsx` KPI grid |
| 3개년 P&L 전체 행과 영업이익률 | PORTED | annual projection table |
| 월별 매출·영업이익·누적 현금흐름 차트와 상세 표 | PORTED | SVG line charts와 expandable table |
| 보수·기준·낙관 stress, scenario BEP/필요자금/누적현금 | PORTED | stress cards와 scenario chart |
| Monte Carlo P10/P50/P90·손실확률·회수확률·횟수·seed | PORTED | risk section |
| findings·cautions·recommended actions·disclaimer·report source/provider status | PORTED | report judgment/footer |

공식 Finance의 `PRESERVE_REQUIRED=YES` 행 중 `NOT_PORTED`는 없다.

#### Sandbox/demo 분리 판정

| DONOR SURFACE | STATUS | 판정 |
|---|---|---|
| `FinancialModulePage.jsx` sandbox 입력·미리보기 API | NOT_PORTED + reason | 개발용 중복 API이며 공식 `/api/v3/projects/{projectId}/finance/...`에 노출하지 않았다. donor 파일은 삭제·변경하지 않았다. |
| `FinanceDemoFallback` 가짜 데이터 실행 | NOT_OFFICIAL | sample/demo upstream 금지에 따라 공식 Project Finance route에서 노출하지 않았다. donor 구현은 read-only donor에 보존되어 있다. |

### 7.5 AIdev Marketing Visual (Session 4)

기존 5절의 Marketing Content 행은 전부 `KEEP_TARGET`이며 삭제·축약하지 않았다. Visual donor 정보는 현재 Marketing workspace 안에서 다음과 같이 대응했다.

| USER_VISIBLE_ITEM / DONOR SURFACE | STATUS | TARGET 구현 또는 판정 |
|---|---|---|
| 기존 Marketing source/setup/editor/revision/legal/finalization/copy/download | KEEP_TARGET | 기존 `marketing-content/**`를 유지하고 Visual section만 추가 |
| `AI 광고 배너 생성` 제목과 설명 | PORTED | `MarketingVisualSection.jsx` header |
| 프로모션 이름, 메인/보조 문구 | PORTED | 현재 Marketing title/body에서 초기화되는 수정 가능 입력 |
| 광고 분위기 7종 | PORTED | donor 7종 선택을 그대로 보존 |
| 가로/정사각 SNS/세로 모바일 형식 | PORTED | donor 3종 형식 의도와 Provider 크기 매핑 보존 |
| 강조 키워드 | PORTED | 쉼표 기반 최대 10개 입력과 AI contract 결속 |
| PNG/JPG/JPEG/WEBP, 10MB, preview/파일명/제거 | PORTED | Backend 소유 Artifact 업로드 후 TaskRun에는 artifact reference만 저장 |
| 등록 상품 수/상품 카드/카테고리/요약/features/선택 | REPLACED_SEAM | legacy mock 상품 대신 authoritative Marketing Source의 Concept·대상·가치·features·revision 요약으로 보존 |
| 필수값 validation | PORTED | Browser 선검증 + Backend/AI strict validation |
| 광고 배너 생성 action | PORTED | `MARKETING_VISUAL_GENERATION` Product TaskRun |
| 생성 진행/취소 | REPLACED_SEAM | timer/가짜 percentage 대신 실제 JobEvent 단계와 TaskRun cancel |
| 결과 preview, badge/headline/subheadline/CTA | PORTED | canonical Artifact REST refresh 뒤 인증 download blob preview |
| tone/promotion/product/source/revision/model/format 결과 요약 | PORTED | Visual 결과 detail과 source lineage |
| 필수 고지/통제 | PORTED | associated copy에 결속하고 결과 화면에 명시적으로 표시 |
| 다시 만들기/저장 | PORTED | 새 TaskRun 생성, Project Artifact 영속화, 인증 download |
| donor banner copy/prompt/image generation/Pillow composition | PORTED | Internal AI Execution 내부 구현 |
| donor `POST /api/v1/marketing/banners/generate` | DO_NOT_EXPOSE | Browser 공식 API로 노출하지 않음 |
| `AiServerMarketingClient` direct-call 경로 | DO_NOT_EXPOSE | Target Worker → Internal AI Execution 경계 사용 |
| `ai/outputs/banner_<id>.jpg` | REPLACED_SEAM | local output authority 제거, MinIO-backed Project Artifact가 정본 |
| VirtualMarket legacy route | NOT_PORTED — reason | 새 Journey 금지. 현재 Marketing workspace 내부 section으로 결합 |
| `setTimeout`/canvas fake generation | DEV_ONLY / NOT_PORTED | Product 실행에 포함하지 않음 |

Session 4 범위의 `PRESERVE_REQUIRED=YES` 정보 항목 중 누락된 항목은 없다.
