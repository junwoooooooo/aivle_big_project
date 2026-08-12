export const VISUAL_MOODS = Object.freeze([
  '신뢰감 있는', '밝고 친근한', '감성적인', '전문적인', '강렬한', '고급스러운', '미니멀한',
]);
export const VISUAL_FORMATS = Object.freeze([
  ['가로형 배너', '가로형'], ['정사각형 SNS 광고', '정사각형 SNS'], ['세로형 모바일 광고', '세로형 모바일'],
]);
export const VISUAL_ACTIVE = new Set(['QUEUED', 'READY', 'RUNNING']);

export function visualDefaults(source = {}, draft = {}) {
  const safeSource = source ?? {};
  const safeDraft = draft ?? {};
  return {
    promotionName: safeSource.conceptName ?? safeDraft.title ?? '',
    mainBanner: String(safeDraft.title ?? '').slice(0, 80),
    supportingCopy: String(safeDraft.body ?? safeDraft.imageBrief ?? '').replace(/\s+/g, ' ').slice(0, 150),
    mood: '밝고 친근한', bannerFormat: '가로형 배너', emphasisKeywords: '',
  };
}

export function visualRequest(value, contentId, revisionId, artifactId) {
  return {
    contract: 'marketing-visual-generation-input-v1', marketingContentId: contentId,
    marketingRevisionId: revisionId, sourceImageArtifactId: artifactId,
    promotionName: value.promotionName.trim(), mainBanner: value.mainBanner.trim(),
    supportingCopy: value.supportingCopy.trim(), mood: value.mood, bannerFormat: value.bannerFormat,
    emphasisKeywords: [...new Set(value.emphasisKeywords.split(',').map((item) => item.trim()).filter(Boolean))].slice(0, 10),
  };
}

export function validateVisualInput(value, file, contentId, revisionId) {
  if (!contentId || !revisionId) return '먼저 마케팅 콘텐츠와 revision을 선택해 주세요.';
  if (!value.promotionName.trim() || !value.mainBanner.trim() || !value.supportingCopy.trim()) return '프로모션 이름과 배너 문구를 모두 입력해 주세요.';
  if (!file) return '배너에 사용할 상품 이미지를 업로드해 주세요.';
  if (!['image/png', 'image/jpeg', 'image/webp'].includes(file.type)) return 'PNG, JPG, JPEG, WEBP 이미지만 업로드할 수 있습니다.';
  if (file.size <= 0 || file.size > 10 * 1024 * 1024) return '이미지는 10MB 이하의 유효한 파일이어야 합니다.';
  return '';
}

export function visualFailure(code) {
  const messages = {
    INPUT_INVALID: 'Visual 입력값을 확인해 주세요.', SOURCE_IMAGE_INVALID: '업로드한 이미지를 해석할 수 없습니다.',
    COPY_GENERATION_FAILED: '배너 문구를 준비하지 못했습니다. 다시 시도할 수 있습니다.',
    IMAGE_GENERATION_FAILED: '이미지 생성 Provider에 연결하지 못했습니다. 다시 시도할 수 있습니다.',
    IMAGE_COMPOSITION_FAILED: '생성 이미지에 한글 문구를 합성하지 못했습니다.',
    ARTIFACT_STORAGE_FAILED: '완성 이미지를 프로젝트 저장소에 보존하지 못했습니다. 작업은 완료 처리되지 않았습니다.',
    MARKETING_PROHIBITED_CLAIM: 'Marketing Source의 금지 표현 정책으로 결과를 사용할 수 없습니다.',
    AI_CONFIGURATION_INVALID: 'AI 이미지 Provider 설정을 확인해 주세요.', TASK_TIMEOUT: '이미지 생성 시간이 초과되었습니다.',
  };
  return messages[code] ?? '마케팅 이미지 생성을 완료하지 못했습니다.';
}
