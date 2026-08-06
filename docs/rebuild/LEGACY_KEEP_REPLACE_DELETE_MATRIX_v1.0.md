# LEGACY KEEP / REPLACE / DELETE MATRIX v1.0

## 1. 원칙

코드는 Git History가 보존하므로 활성 저장소 안에 Legacy 코드 Archive 폴더를 장기간 유지하지 않는다. 새 기능이 대체되는 즉시 기존 Route·Import·Controller·Table 의존을 제거하고 파일을 삭제한다. 문서만 `docs/archive`로 보존한다.

## 2. KEEP

- 인증·사용자·프로젝트 소유권
- 공통 API 응답·예외
- TaskRun·JobEvent·SSE·Replay
- Audit
- File Storage
- 공통 디자인 Token
- Provider Adapter 기반

## 3. REPLACE

- Project Route와 Workflow Model
- 전역 Stage 기반 Navigation
- Conversational Idea Workspace → Idea Brief Form·Question Card
- 3 Concept Workboard → 5 Concept Factory Workboard
- Regulatory Boundary 화면 → 내부 Legal Context
- 기존 Marketing Snapshot → Finalized Planning 기반 Snapshot
- Financial Source Snapshot → 외부 모듈 Handoff

## 4. DELETE AFTER REPLACEMENT

- Legacy Journey UI와 Route
- Persona·Panel·Interview·Market Response UI·Service
- 기존 Financial Workspace
- 기존 Marketing Workspace·A/B·Validation 연결
- Final Report Journey
- 사용하지 않는 Controller·Entity·Migration·Test

## 5. DOCUMENT ARCHIVE

`docs/redesign/**` → `docs/archive/conversational-workspace/**`

## 6. 기준

Legacy가 신규 코드에 import되거나 신규 Route에 노출되거나 신규 FK가 연결되면 Cutover 실패로 판정한다.
