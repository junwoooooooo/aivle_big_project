# Session 4 사용자 검증 안내

## 검증 전제

- branch: `integration/full-transplant-v1`
- 코드 기준: Session 4 commit `transplant: marketing visual generation`
- `AI_API_KEY`, `AI_BASE_URL`, `MARKETING_COPY_MODEL`, `MARKETING_IMAGE_MODEL`은 배포 환경에서 설정한다.
- Noto Sans KR와 OFL은 AI container runtime asset이며 별도 사용자 Artifact로 배포하지 않는다.

## 1. Marketing 기존 기능 회귀

1. 현재 Marketing route에서 source snapshot, content type 전체, title/body/CTA/hashtags/imageBrief를 확인한다.
2. 기존 생성, editor, revision, copy/download, finalization, stale와 legal guard가 Session 전과 동일하게 동작하는지 확인한다.
3. Visual 생성이 기존 text revision을 overwrite하지 않고 source revision reference만 남기는지 확인한다.

## 2. Visual 입력과 source 검증

1. 확정된 Marketing Content에서 Visual section을 연다.
2. Concept/대상 고객/핵심 가치/features/content/revision 및 allowed/prohibited claims, required disclosures/controls가 현재 source와 일치하는지 확인한다.
3. title/body/CTA가 Visual 초기값과 결과 연계 정보에 반영되며 promotion/main/support/tone/format/keywords를 수정할 수 있는지 확인한다.
4. PNG, JPG/JPEG, WEBP를 각각 업로드해 preview/파일명/제거를 확인한다.
5. 10MB 초과, 확장자 위장, 손상된 이미지가 안전하게 거부되는지 확인한다.

## 3. Provider LIVE 소규모 검증

1. 허가된 낮은 비용의 테스트 프로젝트에서 한 번만 `광고 배너 만들기`를 실행한다.
2. Browser가 AI FastAPI를 직접 호출하지 않고 Product API가 202/TaskRun ID를 반환하는지 확인한다.
3. Work Center에서 `마케팅 이미지 생성`과 입력 확인→문구 준비→이미지 생성→텍스트 합성→결과 저장 단계가 보이는지 확인한다.
4. Provider 기본 모델이 운영 설정의 `gpt-image-2`인지, source image가 generation/edit 경계에 전달되는지 운영 로그의 safe metadata로 확인한다.
5. 한글 badge/headline/subheadline이 Noto Sans KR로 잘리지 않고 줄바꿈·font shrink·명암 처리가 적용되는지 확인한다.

## 4. Artifact와 canonical 결과

1. Task 완료 후 Project Evidence Artifact가 생성되고 object storage가 MinIO인지 확인한다.
2. TaskResult에 image base64나 MinIO internal URL이 없고 artifact ID, filename, MIME, size, download path, source TaskRun/content/revision이 있는지 확인한다.
3. SSE 완료 직후 canonical REST refresh 뒤 preview가 나타나는지 확인한다.
4. preview/open/download가 로그인한 프로젝트 owner에게만 허용되고 foreign project 사용자는 거부되는지 확인한다.
5. 다운로드한 JPEG와 화면의 badge/headline/subheadline/CTA, promotion/source/revision/tone/format/model, 필수 고지/통제가 일치하는지 확인한다.

## 5. 실패·retry·history

1. Provider key 오류 또는 허용된 장애 주입으로 `IMAGE_GENERATION_FAILED`가 raw Provider body 없이 안전하게 표시되는지 확인한다.
2. object storage 장애에서 Artifact가 없고 Task가 성공 처리되지 않으며 `ARTIFACT_STORAGE_FAILED`인지 확인한다.
3. retryable 실패에서 `다시 시도`가 새 TaskRun을 만들고 기존 실패 TaskRun/history를 보존하는지 확인한다.
4. non-retryable input/source/safety 실패에는 재시도 action이 노출되지 않는지 확인한다.
5. 실행 중 취소가 TaskRun terminal 상태와 Work Center에 반영되는지 확인한다.

## 6. 사용자 수행 범위

Codex는 실 image Provider/OpenAI image generation, MOLEG LIVE, Market/Twin LIVE, 전체 Browser E2E, 사용자 Docker 전체 검증을 실행하지 않았다. 위 검증 중 실패하면 project ID, Marketing Content/revision ID, TaskRun ID, Artifact ID, JobEvent sequence와 safe error code를 함께 기록한다. secret, raw Provider body, source image 원본은 이슈 본문에 첨부하지 않는다.
