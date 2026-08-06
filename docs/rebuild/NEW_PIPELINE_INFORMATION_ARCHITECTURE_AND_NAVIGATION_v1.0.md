# NEW PIPELINE INFORMATION ARCHITECTURE AND NAVIGATION v1.0

## 1. Route Map

```text
/app/projects/{id}/overview
/app/projects/{id}/idea
/app/projects/{id}/concepts
/app/projects/{id}/concepts/compare
/app/projects/{id}/market
/app/projects/{id}/business-persona-test
/app/projects/{id}/marketing
/app/projects/{id}/settings
```

## 2. 제거 Route

- `/legal`
- `/journey/concept`
- `/journey/concept-analysis`
- `/journey/concept-selection`
- `/journey/persona`
- `/journey/interview`
- `/journey/marketing`
- `/journey/final-report`
- 구형 `/plan`, `/review`, `/validate`, `/report` Area 의존

기존 URL은 장기간 Alias로 유지하지 않는다. Cutover 직후 Project Overview 또는 대응 신규 Route로 단기 Redirect하고, 한 Release 뒤 제거한다.

## 3. 모듈 상태

모든 Navigation은 모듈 상태를 독립 조회한다. `project.stage`는 UI 진입 조건·진행률·라우팅 정본으로 사용하지 않는다.

## 4. Soft Dependency

페이지는 열고, 필요한 입력이 없으면 설명·필요 조건·이동 Action을 제공한다. 버튼 실행 시 Domain Error와 nextAction을 반환한다.

## 5. 전역 작업 센터

Header에서 프로젝트에 관계없이 진행 중 작업을 확인한다. 프로젝트 전환 후에도 Run 상태를 유지한다.

## 6. 진행률

단계 수 기반 퍼센트는 제거한다. 대신 `6개 모듈 중 완료 2`, `법률검토 통과 3/5`처럼 실체 있는 완료 수를 표시한다.
