# TEST AND ACCEPTANCE PLAN v1.0

## 1. 자동 테스트 층

- Domain unit
- API contract
- Repository PostgreSQL
- Worker lease·retry·recovery
- AI schema·provider adapter
- Frontend model·hook·component
- Cross-service fixture
- Route·legacy absence

## 2. 핵심 E2E

프로젝트 생성 → Idea Brief → 5 Concept Factory → 비교·선택 → Market Stub → 변경안 → Finalized Planning → Marketing.

## 3. Provider Gate

핵심 AI Task마다 synthetic input으로 실제 Provider Schema Smoke를 제공한다. Mock Green만으로 단계 승인하지 않는다.

## 4. UI Acceptance

- Desktop 1280+
- Tablet 768
- Mobile 390×844
- Keyboard
- 200% zoom
- Reduced motion
- aria-live·alert
- offline·retry·stale·not connected

## 5. 데이터·계약

- hash·idempotency
- project isolation
- immutable snapshot
- stale detection
- event ordering·dedupe
- no secret/raw prompt

## 6. Legacy Absence

- 기존 Journey Route 접근 불가 또는 신규 Redirect
- Navigation 미노출
- 신규 Package의 legacy import 0
- OpenAPI legacy controller 제거 확인

## 7. 수동 Gate

R2, R3, R6, R7에서 Docker·브라우저 수동 검증을 수행한다.
