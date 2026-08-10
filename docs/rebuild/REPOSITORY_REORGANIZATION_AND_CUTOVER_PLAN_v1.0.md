# REPOSITORY REORGANIZATION AND CUTOVER PLAN v1.0

## 1. 분석 기준

- bp_new_3 main: 2b4871e210253309f08b164c6dfddefc4ce5d0bc
- AIdev: 581234abbcd77ab5931744be15fa7f28a272e58e

## 2. 신규 문서 구조

```text
docs/
  rebuild/
    README.md
    NEW_PIPELINE_MASTER_PLAN_v1.0.md
    ...
    contracts/
    decisions/
    progress/
  archive/
    conversational-workspace/
    contracts/internal-ai-v1/
```

## 3. Frontend 신규 구조

```text
frontEnd/src/
  app/project-shell/
  app/routing/
  app/module-status/
  features/idea-intake/
  features/concept-factory/
  features/concept-selection/
  features/market-integration/
  features/planning-revision/
  features/business-persona-integration/
  features/marketing-content/
  features/job-center/
  shared/async-events/
  shared/source-snapshot/
```

## 4. Backend 신규 구조

```text
backend/.../pipeline/
  idea/
  concept/
  legal/
  selection/
  planning/
  integration/
  marketing/
```

기존 `journey`, `validation`, legacy analysis package에 신규 기능을 추가하지 않는다.

## 5. AI 신규 구조

```text
ai/app/tasks/
  idea_brief/
  concept_candidate/
  concept_legal_review/
  concept_redesign/
  marketing_content/
ai/app/providers/
ai/app/contracts/
ai/app/tools/
```

## 6. Cutover 순서

1. docs/rebuild 권위 선언
2. 새 Route·Shell 적용, Legacy Navigation 미노출
3. 새 DB Baseline
4. Idea 대체 후 Conversational UI 삭제
5. Concept Factory 대체 후 기존 Workboard·Boundary UI 삭제
6. Selection·Market Shell 후 legacy validation UI 삭제
7. Marketing 포팅 후 legacy marketing 삭제
8. Dead code·Migration·Test 정리
9. 전체 E2E

## 7. 안전장치

- `git grep`로 legacy route·package 참조 0건 확인
- 신규 Package에서 legacy import 금지 Test
- Frontend Route Snapshot Test
- DB Clean Migration Test
- OpenAPI Endpoint 목록 비교
