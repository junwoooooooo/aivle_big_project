# P0 donor behavior transplant 중단 결과

## 판정

`STOP CONDITION 1` — 지정된 donor 어느 계통에도 concept-specific Market series selector가 없다.
이를 구현하면 donor behavior transplant가 아니라 새로운 제품 분류기 설계가 되므로 코드 이식을 시작하지 않았다.

## 기준 SHA

- FULL/HEAD: `6a957b3dece33330866d39f18b4a99ef5d18ab27`
- main: `aab1db2d0924bddbd307893c604426a3b0f7bf44`
- historical: `4ee74359a1b231359dc3131fb8eecb126462d2bf`
- historical: `598209fedfd6ee6e8f7ae98c56340f1bf1c60efe`
- UX reference: `87ba06018cb800484d9fef902452401586d1ebcc`

## 근거

- `4ee7435`의 `ResearchConceptFactory`는 `SERIES = "C"`와 “계열 판별 관문을 만들지 않는다”를 명시하고, `seriesIsPinnedToC` 테스트로 고정한다.
- `598209f`와 `origin/main`의 `MarketResearchInputFactory.donorConcept`도 `_계열.계열 = "C"`를 기록한다.
- `87ba060`의 edge concept 자료는 “계열 판별기는 존재하지 않는다(백로그 36)”를 명시한다.
- 따라서 세 상이한 fixture가 concept 특성에 따라 서로 다른 strategy를 선택해야 한다는 acceptance authority가 없다.

## Market Interview 확인

- shipped profile bank는 존재하며 frame/card 각 8,604행이다.
- main donor는 `resolve_criteria` → `condition_matches`/`matches` → `draw_split` → deterministic stratified sampling → interviews → coding → aggregation 경로를 가진다.
- FULL은 v3/TaskRun 입력 이후 별도 `deep_engine`/`panel_sampling` 재구현을 사용한다.
- Business Validation stop condition 때문에 Market Interview만 부분 이식하지 않았다.

## 실제 실행한 검사

- START GATE fetch/status/SHA 검증
- 네 donor 계통의 series 생성 코드·테스트·edge 자료 정적 추적
- donor/full Market Interview 및 bank loader/matcher 정적 추적
- shipped bank 파일/manifest/행 수 확인
- `git diff --check`

## 의도적으로 생략

- 구현 및 테스트 전체
- provider 호출
- Docker/browser smoke

## 남은 위험과 정확한 재개 조건

concept-specific series를 결정하는 검증된 donor SHA 또는 승인된 A/B/C 분류 계약이 필요하다.
그 authority가 주어지면 동일 START GATE에서 다시 시작해 해당 selector를 thin adapter로 연결한 뒤 나머지 donor subsystem 이식을 진행한다.

