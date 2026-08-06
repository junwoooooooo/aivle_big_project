# MARKETING CONTENT PORTING PLAN v1.0

## 1. 출처

`junwoooooooo/aivle_big_project`의 `AIdev` 브랜치 마케팅 구현을 참고한다.

## 2. 이식 대상

- `ai/app/api/marketing.py`
- `ai/app/services/marketing_task_service.py`
- `ai/prompts/marketing_generation/**`
- `frontEnd/src/features/marketing/components/MarketingCanvas.jsx`
- `MarketingSetupPanel.jsx`
- `MarketingStylePanel.jsx`
- `MarketingCopyEditor.jsx`
- `marketingRenderer.js`
- `useMarketingGeneration.js`
- Backend Content·Version·Types 개념

파일을 단순 복사하지 않고 신규 API·Snapshot·디자인 시스템에 맞게 포팅한다.

## 3. 제외

- marketing_comparison
- A/B 테스트
- Persona 만족도
- Panel Interview
- Market Response 메시지 실험
- 출시 전략 리포트
- 기존 Marketing Workspace Stage

## 4. 신규 Source

FinalizedPlanningSnapshot + Market Insight Summary + Legal Advertising Controls.

## 5. 신규 UI

콘텐츠 목록, 생성 Workspace, 설정, Preview, Copy Editor, 법률 경고, 저장·다운로드.

## 6. 콘텐츠 타입

SOCIAL_POST, AD_COPY, LANDING_PAGE, BLOG, EMAIL, BANNER, POSTER, IMAGE_BRIEF.

## 7. Revision Label

첫 생성안, 톤 수정안, 짧은 문구안, 법률 고지 반영안, 최종 저장본.

## 8. 완료 Gate

- 외부 Persona·Panel·Market Response 없이 생성 가능
- 최종 기획 Snapshot 고정
- 금지 표현·필수 고지 반영
- 비동기 진행·새로고침 복원
- 편집·저장·다운로드
