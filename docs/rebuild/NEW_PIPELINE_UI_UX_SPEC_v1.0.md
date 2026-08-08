# NEW PIPELINE UI/UX SPEC — V2 authoritative contract

파일명은 기존 참조 호환을 위해 `v1.0`을 유지한다.

## 1. 설계 원칙

- 사용자는 내부 enum보다 현재 결과, 출처 의미, 다음 행동을 이해해야 한다.
- Seed 화면은 대화형 Workspace가 아니라 짧은 Form이다.
- 진행 상태는 실제 Event에 기반하며 Query API가 정본이다.
- 모든 모듈 페이지 진입을 허용하고 Action 시 전제조건을 안내한다.
- 법률·분석 정보는 요약 후 펼쳐본다.
- 상태를 색상만으로 표현하지 않는다.
- Desktop, Tablet, Mobile, 키보드, 스크린리더에서 동일 기능을 제공한다.

## 2. 정보구조

| 화면 | 목적 |
|---|---|
| 프로젝트 개요 | 8개 Workflow 영역 상태와 다음 행동 |
| 아이디어 / Market Seed | 필수 3개와 optional LOCKED 입력 |
| AI Interpretation | 입력 의미 확인·수정 |
| 컨셉 생성·법률검토 | 후보 생성, distinctness, legal 진행 |
| 컨셉 비교·선택·가설 확정 | 5개 비교, 선택, 선택 Concept 가설 결정 |
| 시장분석 | Market Seed Snapshot과 외부 Run/Result |
| BM | 실행 직전 Preparation과 외부 Shell |
| 기술·운영 분석 | Preparation, decision, Snapshot, 외부 Shell |
| 재무 분석 | 승계값, missing input, Snapshot, 외부 Shell |
| 마케팅 콘텐츠 | 선택 Concept와 Legal Guard 기반 생성 |
| 작업 센터 | 모든 비동기 Run의 현재 actionability와 이력 |

기존 Persona 기능이 별도 승인 계약으로 존재하면 독립적으로 노출할 수 있으나 V2 Seed/Market 필수 선행으로 합치지 않는다.

## 3. Seed 입력 화면

첫 화면은 다음 세 필드를 명확한 필수값으로 표시한다.

- 아이디어 개요
- 해결하려는 문제
- 예상 사용자

그 아래 “이미 정한 내용이 있다면 입력해 주세요” 선택 Section/Accordion에 지역, 알려진 경쟁자, 수익모델, 가격, 채널, 차별점, 예산·팀·일정·기타 제약을 배치한다. optional 값이 비어 있어도 Primary Action을 사용할 수 있다.

초기 화면에서 플랫폼 역할, 결제 주체, 개인정보 처리, 파트너, 인허가, 물리활동을 필수 질문으로 표시하지 않는다. Safety가 `BLOCK_OR_REFRAME`이면 Concept 진행을 막고 안전한 이유와 재구성 Action만 표시한다.

## 4. AI Interpretation 화면

제목은 “입력하신 아이디어를 이렇게 이해했습니다.”를 사용한다. 문제, 대상 사용자, 사용 맥락, 업종, 조사 범위, 한 줄 정의를 보여주며 Action은 `이대로 진행`, `내용 수정`이다.

AI 해석을 사용자 입력처럼 표시하지 않는다. source enum 문자열 대신 `AI가 해석`, `사용자가 입력` 같은 사용자 언어를 쓴다. 수정 가능한 `REVIEWABLE` 상태를 분명히 한다. 후속 질문은 Concept 탐색이 불가능한 핵심 모호성에만 최소 생성한다.

## 5. Concept Workboard와 진행 문구

Workboard는 적격 수, 검사 후보 수, 재설계·폐기·교체 수와 최대 5개 Slot을 표시한다. Desktop은 3+2 Grid와 Timeline, Mobile은 세로 Card와 sticky action을 사용한다.

안전한 진행 문구 예:

- 후보를 설계하고 있습니다.
- 사업 구조를 확인하고 있습니다.
- 다른 후보와 실질적으로 구별되는지 확인하고 있습니다.
- 법률 근거를 확인하고 있습니다.
- 필요한 통제를 반영하고 있습니다.
- 다른 구조의 후보를 준비하고 있습니다.
- 적격 후보 준비가 완료되었습니다.

Prompt, provider raw error/body, API key, 내부 JSON, stack trace, 폐기 Draft 전문은 표시하지 않는다. 5개 적격 후보가 모두 준비되기 전 상세 Draft를 순차 공개하지 않는다. distinct 5개 확보가 불가능하면 부족한 수를 숨기거나 중복으로 채우지 않고 `INSUFFICIENT_DISTINCT_CONCEPTS`와 안전한 다음 행동을 표시한다.

## 6. Concept 카드와 비교

카드 최소 항목:

- 이름, 정의/소개, 핵심 가치
- 대상 사용자, 업종, 조사 범위
- 수익모델·가격, 채널, 차별점
- 사전 SOM 가설
- 법률 상태와 핵심 조건

값 Badge는 `사용자가 입력`, `AI 제안`, `채택됨`, `확인 필요`처럼 표시한다. 사전 SOM은 실제 시장분석 결과가 아님을 보조 문구로 설명한다. 비교표는 고객, 문제, 해결 방식, 수익·가격, 채널, 플랫폼 역할, 운영·파트너, 법률 조건의 실질 차이를 보여준다. 단일 종합점수로 순위를 강제하지 않는다.

## 7. 선택과 가설 결정

사용자는 provisional hypothesis 상태의 5개를 먼저 비교·선택한다. 선택 전 모든 후보의 가설 결정을 요구하지 않는다.

선택 후 `revenueModel`, `price`, `channels`, `differentiators`, pre-market SOM share/amount를 확인한다. Seed에서 LOCKED인 항목은 “사용자가 입력”과 확정 상태로 읽기 전용 표시한다. AI 제안에는 `채택`, `수정 후 채택`, `다른 제안` Action을 제공한다. 단독 `거절`로 막다른 상태를 만들지 않는다.

legal-sensitive 수정은 “법률 영향 확인 중” 상태와 Delta Legal Review 결과를 표시한다. 실패하면 해당 값을 확정된 것으로 표시하지 않고 대체 제안 Action을 제공한다. non-legal SOM 변경에는 불필요한 legal spinner를 만들지 않는다.

## 8. Market, BM, TechOps, Finance

Market 화면은 선택 Concept, `MarketAnalysisSeedSnapshot` 요약, handoff/run 상태, Market Result를 보여준다. planning change proposal, 채택·일부 채택·거절, “시장분석 반영안”, `FinalizedPlanningSnapshot` UI를 제공하지 않는다.

BM 입력이 추가로 필요하면 실행 직전 화면에서만 받는다.

TechOps 화면은 상위 값 자동 승계, 사용자 필수 사실, AI 제안 결정, optional Evidence, Snapshot 준비 상태를 구분한다. AI 제안과 실제 견적서/BOM/공급사 자료를 같은 Badge로 표시하지 않는다.

TechOps Evidence는 자유 `artifactRef` 텍스트 입력 대신 allowlist가 표시된 실제 file picker를 사용한다. 업로드 성공으로 받은 project artifact ID를 evidence type과 함께 등록하며, 목록에는 원본 파일명, media type, 크기, SHA-256을 표시한다. reference 제거는 파일 artifact 삭제로 표현하지 않는다.

Finance 화면은 TechOps 승계값에 “기술·운영 단계에서 가져옴”을 표시하고 수정 provenance를 제공한다. 없는 값만 요청한다. CAC는 비용과 신규 고객 수를 입력받아 시스템이 계산하며 사용자에게 CAC 자체 계산을 요구하지 않는다. 조건부 원가는 해당 사업/외부 계약에 필요한 경우만 표시한다.

## 9. Marketing

Source 요약은 선택 Concept, 최종 확정 가설, Legal Result다. Market Result 대기 또는 `FinalizedPlanningSnapshot` 필요 상태를 표시하지 않는다.

콘텐츠 설정·Canvas·편집 영역과 함께 허용 주장, 금지 표현, 필수 고지, communication control을 고정 또는 쉽게 확인 가능한 위치에 표시한다. Legal Guard 위반 표현은 생성·저장 전에 차단하거나 안전하게 수정 안내한다.

## 10. 상태, 작업 센터, 접근성

공통 상태는 Initial Loading, Empty, Not Ready, Ready, Queued, Running, Needs Input, Completed, Failed Retryable, Failed Permanent, Stale, Not Connected, Offline이다.

작업 센터는 raw terminal history와 현재 actionable item을 구분한다. 해결된 과거 `NEEDS_INPUT`은 현재 입력 필요로 표시하지 않는다. terminal job을 새 Action에 재사용하지 않는다.

접근성 기준:

- 논리적 Tab 순서와 visible focus
- `aria-live`/`alert`로 진행·오류 전달
- Accordion `aria-expanded`
- Modal focus trap과 복원
- 44px touch target
- reduced motion
- 200% 확대와 긴 한국어 줄바꿈
- 비교표의 Mobile card 대체

검증 viewport는 390×844, 768×1024, 1280px 이상이다.
