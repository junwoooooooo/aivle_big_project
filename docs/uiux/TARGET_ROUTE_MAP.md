# Current Route Map

- Status: CURRENT_AS_BUILT
- Baseline date: 2026-08-04
- Scope: 실제 `AppRouter`와 `ProjectLayout`

## 현재 공식 Journey Route

| 화면 | 실제 Route | 제품 구분 |
|---|---|---|
| Idea 입력·해석·Origin | `/app/projects/:projectId`, `/app/projects/:projectId/idea` | 현재 Journey |
| Legal Precheck·Guardrail | `/app/projects/:projectId/legal` | 현재 Journey |
| Concept 생성·검증·적격 3개 | `/app/projects/:projectId/journey/concept` | 현재 Journey 종료점 |

Concept 결과에서 후속 화면으로 자동 이동하지 않는다. `ProjectLayout`은 Concept에서 `현재 재설계 범위 완료`를 표시한다.

## 보존된 기존 MVP Route

| 화면 | 실제 Route | 제품 구분 |
|---|---|---|
| Concept Analysis | `/app/projects/:projectId/journey/concept-analysis` | 보존 MVP 실험 기능 |
| Concept Selection | `/app/projects/:projectId/journey/concept-selection` | 보존 MVP 실험 기능 |
| Persona | `/app/projects/:projectId/journey/persona` | 보존 MVP 실험 기능 |
| Interview | `/app/projects/:projectId/journey/interview` | 보존 MVP 실험 기능 |
| Marketing | `/app/projects/:projectId/journey/marketing` | 보존 MVP 실험 기능 |
| Final Report | `/app/projects/:projectId/journey/final-report` | 보존 MVP 실험 기능 |

이 Route들은 직접 접근 가능하지만 현재 공식 Journey와 자동 연결된 단계가 아니다. Sidebar도 `기존 MVP · 현재 여정과 미연결`로 표시한다.

## Deprecated/Compatibility redirect

`AppRouter`에는 과거 `/projects/:projectId/**`, project 내부 `plan/**`, `review/**`, `validate/**`, `validation/**`, `report` 경로를 현재 화면으로 보내는 frontend redirect가 남아 있다. 이는 보존 compatibility route이며 새 API endpoint가 아니다. 이번 기준선에서 삭제하거나 신규 redirect를 추가하지 않았다.
