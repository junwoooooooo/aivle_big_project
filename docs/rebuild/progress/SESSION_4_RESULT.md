# Session 4 AIdev Marketing Visual 이식 결과

- 기준 Target: `integration/full-transplant-v1` / 시작 commit `412eca9f0b44c6be1dbccd61361ae62c8c61775e`
- Visual donor: `donor-aidev` / `e6fdeb166c2a8fff1f71f16201600e47bc9cc9d3`
- 판정: **SESSION 4 TRANSPLANT READY**
- Provider LIVE, 실 OpenAI 이미지 생성, MOLEG LIVE, Market/Twin LIVE, 전체 Browser E2E, 사용자 Docker 전체 검증은 수행하지 않았다.

## 1. donor/Target 분류

| 분류 | 내용 |
|---|---|
| KEEP_TARGET | 현재 Marketing Source Snapshot, content type, title/body/CTA/hashtags/imageBrief, revision/editor/finalization, legal claims/disclosures/controls, ownership, TaskRun/Attempt/Result, JobEvent/SSE, Work Center, MinIO/Artifact |
| PORT_DONOR | banner copy model/service, prompt service, OpenAI image generation/edit 의미, Pillow text composition, wrapping/font shrink/contrast, image validation, Noto Sans KR fonts와 OFL |
| MERGE_SEAM | Marketing source/revision 결속, source image Artifact reference, Product API, Worker/Internal AI, completion materialization, module status, canonical REST refresh |
| DEV_ONLY | VirtualMarket mock 상품, timer 진행률, canvas-only fake 결과 |
| DO_NOT_EXPOSE | donor FastAPI banner route, orphan direct backend client, AI local output 경로, legacy Journey route |

Session 시작 시 donor/Target의 `ai/app/tasks/marketing_content/**`, Backend `pipeline/marketing/**`, Frontend `marketing-content/**` 공통 파일을 SHA-256으로 재확인했으며 `DIFFERENT=0`, `DONOR_ONLY=0`이었다. 동일 Marketing 코드는 재복사하지 않았다.

## 2. AI Visual 기능

- donor의 badge/headline/subheadline 역할은 기존 Marketing title/body/CTA를 대체하지 않는 Visual 전용 copy로 유지했다.
- promotion, product context, tone 7종, 형식 3종, 강조 키워드, source image를 prompt에 결속했다.
- 가격·할인·프로모션·효과·혜택을 근거 없이 생성하지 않는 donor guardrail과 Target의 allowed/prohibited claims, required disclosures/controls를 함께 적용했다.
- Provider 설정은 `AI_API_KEY`, `AI_BASE_URL`을 재사용한다. `MARKETING_COPY_MODEL` 기본값은 `gpt-4o-mini`, `MARKETING_IMAGE_MODEL` 기본값은 donor 의미의 `gpt-image-2`이다.
- OpenAI 결과 bytes를 Pillow로 JPEG 배너에 합성한다. 자동 줄바꿈, 폭 기반 font shrink, 명암 overlay/readability, 한글 Noto Sans KR font를 보존했다.
- source image는 PNG/JPG/JPEG/WEBP, 10MB 제한, MIME/확장자/실제 decode 검증을 거친다.
- Noto Sans KR Regular/Bold 및 OFL notice를 `/app/assets` runtime asset으로 복사한다. 폰트는 사용자 Artifact로 노출하지 않는다.

## 3. 현재 Marketing 연결과 UI parity

- Visual 입력은 현재 Marketing Content revision과 그 Marketing Source Snapshot을 immutable input으로 저장한다.
- title → headline, body/imageBrief → supporting copy, CTA → associated CTA로 초기화·결속하되 사용자가 Visual 입력을 수정할 수 있다.
- selected Concept, 대상 고객, 핵심 가치, positioning, features, channels, differentiators와 법률 통제를 source summary에 표시한다.
- 현재 Marketing workspace 안에 Visual section을 추가했다. 기존 route, setup/editor/revision/finalization/action을 제거하지 않았다.
- 결과 화면은 Artifact preview, badge/headline/subheadline/CTA, promotion/source/revision/tone/format/model, associated copy의 필수 고지/통제, 재생성, 다운로드를 표시한다.
- legacy mock product picker의 정보 의도는 authoritative Marketing Source summary로 치환했다. legacy VirtualMarket route와 Persona 의존은 추가하지 않았다.

## 4. 공식 Runtime과 Artifact 흐름

공식 흐름은 다음과 같다.

`Frontend → Backend Product API → MARKETING_VISUAL_GENERATION TaskRun → Worker → Internal AI Execution → image generation/Pillow composition → completion → ProjectEvidenceArtifact/MinIO → MarketingAsset link → JobEvent/Job·Project SSE → canonical REST refresh → Marketing UI`

- Product API는 `/api/v3/projects/{projectId}/marketing-visual-runs` 아래 create/current/result/retry/cancel을 제공한다.
- source image bytes는 Browser에서 AI로 직접 전달하지 않는다. Backend ownership 검증을 거친 기존 evidence artifact reference를 사용한다.
- AI 응답의 base64는 materialization 경계에서 제거한다. canonical TaskResult에는 소유 Artifact metadata/download path와 source TaskRun/content/revision만 남는다.
- Artifact 저장과 TaskRun 성공 채택/MarketingAsset link는 같은 transaction 경계다. 저장 실패 시 Task는 `ARTIFACT_STORAGE_FAILED`로 실패하며 성공으로 가장하지 않는다.
- `ai/outputs`는 사용하지 않는다. MinIO-backed Project Artifact만 결과 정본이다.
- download/preview는 ownership 검증 Backend API를 통하며 MinIO 내부 URL을 노출하지 않는다.
- retryable 실패만 재시도하며 매번 새 TaskRun을 만든다. 실패 history를 revive하지 않는다.
- Marketing module을 새 Journey/module로 분리하지 않고 최신 Visual Task 상태를 기존 Marketing module에 결합했다.

## 5. TaskType, 단계와 안전 실패

- 공식 TaskType은 하나만 추가했다: `MARKETING_VISUAL_GENERATION`.
- Backend enum, AI TASK_TYPES/validation/dispatch, Worker, timeout, Work Center 사람말을 정렬했다.
- 실제 Worker JobEvent 단계: 입력 확인, 배너 문구 준비, 이미지 생성, 텍스트 합성, 결과 저장, 완료/실패. 가짜 percentage는 없다.
- 안전 실패: `INPUT_INVALID`, `SOURCE_IMAGE_INVALID`, `COPY_GENERATION_FAILED`, `IMAGE_GENERATION_FAILED`, `IMAGE_COMPOSITION_FAILED`, `ARTIFACT_STORAGE_FAILED` 및 기존 safety code. raw Provider body, secret, stack trace는 노출하지 않는다.

## 6. Schema와 dependency

- Session 4 migration: **0**.
- 기존 `TaskRun + TaskResult + ProjectEvidenceArtifact + MarketingAsset`로 current/history/ownership/lineage를 표현하므로 별도 Visual run table이나 storage authority를 만들지 않았다.
- AI dependency 최소 증분: `Pillow==11.3.0`, `python-dotenv==1.1.1`. 기존 package version은 변경하지 않았다.

## 7. 검증 결과

- AI Visual/TaskType/기존 Marketing contract: 10 PASS, `compileall app` PASS.
- AI Market/BM/Twin/Finance 대표 회귀: 45 PASS.
- Backend Marketing Visual/Marketing/Artifact/Internal AI 대상: 33 PASS, `compileJava` PASS.
- Backend Market/BM/Twin/Finance/TechOps/module status 대표 회귀: 22 PASS.
- Frontend Marketing/Visual/Job/API 대상: 28 PASS(최종 download 보강 포함).
- Frontend Market/BM/Twin/Finance/TechOps 대표 회귀: 209 PASS.
- Frontend production build: PASS, 요청대로 1회 수행. 265 modules transformed. 500kB 초과 chunk 경고는 기존 성능 경고이며 build 실패가 아니다.
- `git diff --check`: 최종 Gate에서 PASS 확인.

## 8. 금지 영역과 최종 Gate

- CPV2 Core diff 0
- Persona 관련 diff 0
- Market/BM/Twin algorithm diff 0
- Finance calculation diff 0
- TechOps product diff 0
- 기존 migration diff 0, 신규 migration 0
- 새 Journey/storage authority/direct AI Browser path 0

따라서 blocker는 없으며 **SESSION 4 TRANSPLANT READY**다.
