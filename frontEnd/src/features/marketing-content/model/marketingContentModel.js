export const CONTENT_TYPES = Object.freeze([
  ['SOCIAL_POST', 'SNS 게시물'], ['AD_COPY', '광고 문구'], ['LANDING_PAGE', '랜딩 페이지'],
  ['BLOG_INTRO', '블로그 도입부'], ['EMAIL', '이메일'], ['BANNER', '배너'],
  ['POSTER', '포스터'], ['IMAGE_BRIEF', '이미지 설명서'],
]);

export const LENGTHS = Object.freeze([['SHORT', '짧게'], ['MEDIUM', '보통'], ['LONG', '길게']]);

export const REVISION_LABELS = Object.freeze({
  GENERATED: '첫 생성안', TONE_EDITED: '친근한 톤 수정안', SHORTENED: '짧은 SNS 문구안',
  LEGAL_NOTICE_APPLIED: '법률 고지 반영안', USER_EDITED: '사용자 편집안', FINALIZED: '최종 저장본',
});

export const ASYNC_MESSAGES = Object.freeze({
  QUEUED: '선택한 컨셉과 확정 가설을 불러오고 있습니다.',
  STARTED: '핵심 메시지를 구성하고 있습니다.',
  SOURCE_PREPARED: '핵심 메시지를 구성하고 있습니다.',
  COPY_GENERATING: '채널 문구와 이미지를 생성하고 있습니다.',
  LEGAL_CHECKING: '법률상 주의 표현을 확인하고 있습니다.',
  RUNNING: '마케팅 초안을 만들고 있습니다.',
  COMPLETED: '마케팅 초안이 준비되었습니다.',
  FAILED: '마케팅 초안을 만들지 못했습니다.',
  STALE: '이전 컨셉을 기준으로 만든 결과입니다.',
});

export function marketingFailureMessage(error, technicalCode) {
  const code = technicalCode || error?.code;
  if (code === 'AI_CONFIGURATION_INVALID') return '현재 생성 기능을 사용할 수 없습니다. 잠시 후 다시 시도해 주세요.';
  if (code === 'TASK_TIMEOUT' || code === 'DEADLINE_EXCEEDED') return '생성 시간이 초과되었습니다. 다시 시도할 수 있습니다.';
  if (code === 'RATE_LIMITED' || code === 'AI_SERVICE_UNAVAILABLE') return 'AI 서비스가 혼잡합니다. 잠시 후 다시 시도해 주세요.';
  if (code === 'AI_RESULT_INVALID' || code === 'RESULT_SCHEMA_INVALID') return '생성 결과 형식을 확인하지 못했습니다. 다시 생성해 주세요.';
  if (code === 'MARKETING_PROHIBITED_CLAIM' || code === 'SAFETY_POLICY_BLOCKED') return '법률 결과에서 금지한 표현이 포함되어 결과를 사용할 수 없습니다.';
  if (code === 'MODULE_INPUT_STALE') return '사업안이 변경되었습니다. 현재 컨셉으로 새 초안을 만들어 주세요.';
  return '마케팅 초안을 만들지 못했습니다. 잠시 후 다시 시도해 주세요.';
}

export function createSetupModel(marketingSourceSnapshotId = '') {
  return {
    marketingSourceSnapshotId, marketingStrategyReportId: '', contentType: 'SOCIAL_POST', channel: '', purpose: '', tone: '명확하고 친근하게',
    length: 'MEDIUM', callToAction: '', requiredPhrases: '', excludedPhrases: '', additionalInstruction: '', referenceImage: null,
  };
}

export function parsePhrases(value) {
  return [...new Set(String(value || '').split(/[\n,]/).map((item) => item.trim()).filter(Boolean))].slice(0, 20);
}

export function toCreateRequest(setup, referenceArtifactId = null) {
  const required = parsePhrases(setup.requiredPhrases);
  if (setup.callToAction?.trim()) required.unshift(setup.callToAction.trim());
  const instruction = [setup.additionalInstruction?.trim(), setup.callToAction?.trim() && `CTA는 '${setup.callToAction.trim()}'로 작성합니다.`]
    .filter(Boolean).join('\n');
  return {
    contract: 'marketing-content-request-v1', marketingSourceSnapshotId: setup.marketingSourceSnapshotId,
    contentType: setup.contentType, channel: setup.channel.trim(), purpose: setup.purpose.trim(),
    tone: setup.tone.trim(), length: setup.length, requiredPhrases: [...new Set(required)].slice(0, 20),
    excludedPhrases: parsePhrases(setup.excludedPhrases), additionalInstruction: instruction || null,
    referenceArtifactId, marketingStrategyReportId: setup.marketingStrategyReportId || null,
  };
}

export function setupIsValid(setup) {
  return Boolean(setup.marketingSourceSnapshotId
    && setup.channel?.trim() && setup.purpose?.trim() && setup.tone?.trim());
}

export function emptyResult(contentType = 'SOCIAL_POST') {
  return { contract: 'marketing-content-result-v1', contentType, title: '', body: '', callToAction: null,
    hashtags: [], imageBrief: null, legalReview: { compliant: true, warnings: [], requiredDisclosuresApplied: [] }, artifactRefs: [] };
}

export function editableFromResult(value, contentType = 'SOCIAL_POST') {
  const result = value ?? emptyResult(contentType);
  return { ...emptyResult(contentType), ...result,
    hashtags: Array.isArray(result.hashtags) ? result.hashtags : [],
    legalReview: { ...emptyResult(contentType).legalReview, ...result.legalReview,
      warnings: result.legalReview?.warnings ?? [], requiredDisclosuresApplied: result.legalReview?.requiredDisclosuresApplied ?? [] },
    artifactRefs: result.artifactRefs ?? [] };
}

export function latestRevision(detail) {
  return detail?.revisions?.at(-1) ?? null;
}

export function revisionLabel(type) { return REVISION_LABELS[type] ?? '편집안'; }

export function sourceSummary(source = {}) {
  return {
    conceptName: source.conceptName ?? '선택 Concept',
    targetSegment: source.targetSegment,
    valueProposition: source.valueProposition,
    positioning: source.positioning,
    keyFeatures: source.keyFeatures,
    channels: source.channels,
    competitorDifferentiators: source.competitorDifferentiators,
    allowedClaims: source.allowedClaims,
    prohibitedClaims: source.prohibitedClaims,
    requiredDisclosures: source.requiredDisclosures,
    requiredControls: source.requiredControls,
    communicationRequiredControls: source.communicationRequiredControls,
    capturedAt: source.createdAt ?? null,
  };
}

export function displayValue(value) {
  if (value == null || value === '') return 'Source에 별도 값이 없습니다.';
  if (Array.isArray(value)) return value.length ? value.join(' · ') : 'Source에 별도 값이 없습니다.';
  if (typeof value === 'object') return Object.values(value).flat().filter(Boolean).join(' · ') || 'Source에 별도 값이 없습니다.';
  return String(value);
}

export function legalSignals(result, source = {}) {
  const text = [result?.title, result?.body, result?.callToAction].filter(Boolean).join(' ');
  const prohibited = Array.isArray(source.prohibitedClaims) ? source.prohibitedClaims : [];
  const blocking = prohibited.filter((phrase) => phrase && text.includes(phrase));
  if (result?.legalReview?.compliant === false) blocking.unshift('AI 법률 점검에서 사용 불가로 분류했습니다.');
  const applied = result?.legalReview?.requiredDisclosuresApplied ?? [];
  const required = Array.isArray(source.requiredDisclosures) ? source.requiredDisclosures : [];
  const missing = required.filter((notice) => !applied.includes(notice) && !text.includes(notice));
  return { blocking, warnings: [...(result?.legalReview?.warnings ?? []), ...missing.map((notice) => `필수 고지 확인 필요: ${notice}`)] };
}

export function applyEditorAction(result, action, source = {}) {
  const next = editableFromResult(result, result?.contentType);
  if (action === 'SHORTEN') {
    return { result: { ...next, title: next.title.slice(0, 45), body: next.body.slice(0, 240) }, revisionType: 'SHORTENED' };
  }
  if (action === 'LEGAL') {
    const notices = Array.isArray(source.requiredDisclosures) ? source.requiredDisclosures : [];
    const missing = notices.filter((notice) => !next.body.includes(notice));
    return { result: { ...next, body: [next.body, ...missing].filter(Boolean).join('\n'),
      legalReview: { ...next.legalReview, requiredDisclosuresApplied: [...new Set([...next.legalReview.requiredDisclosuresApplied, ...notices])] } }, revisionType: 'LEGAL_NOTICE_APPLIED' };
  }
  return { result: next, revisionType: 'USER_EDITED' };
}
