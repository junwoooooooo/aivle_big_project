export const ACTIVE_JOB_EVENT_KEYS = Object.freeze([
  'job.idea.queued',
  'job.idea.started',
  'job.idea.extracting',
  'job.idea.questions.preparing',
  'job.idea.brief.preparing',
  'job.idea.completed',
  'job.idea.failed',
  'job.concept.run.queued',
  'job.concept.run.started',
  'job.concept.legal-context.started',
  'job.concept.legal-context.completed',
  'job.concept.slot.started',
  'job.concept.slot.generated',
  'job.concept.slot.retrying',
  'job.concept.slot.generation_failed',
  'job.concept.slot.review_failed',
  'job.concept.slot.validating_origin',
  'job.concept.slot.validating_distinctness',
  'job.concept.slot.validating_legal',
  'job.concept.slot.redesigning',
  'job.concept.slot.replacing',
  'job.concept.slot.eligible',
  'job.concept.slot.rejected',
  'job.concept.run.needs_input',
  'job.concept.run.completed',
  'job.concept.run.failed',
  'job.marketing.queued',
  'job.marketing.started',
  'job.marketing.source_prepared',
  'job.marketing.copy_generating',
  'job.marketing.legal_checking',
  'job.marketing.completed',
  'job.marketing.failed',
]);

const messages = Object.freeze({
  'job.idea.queued': '아이디어 정리 작업을 준비하고 있습니다.',
  'job.idea.started': '아이디어 정리를 시작했습니다.',
  'job.idea.extracting': '아이디어를 안전하게 지원할 수 있는지 확인하고 있습니다.',
  'job.idea.questions.preparing': '입력하신 의미를 보존해 AI 해석을 정리하고 있습니다.',
  'job.idea.brief.preparing': 'Market Seed와 AI 해석을 저장하고 있습니다.',
  'job.idea.completed': '안전 확인과 AI 해석이 준비되었습니다.',
  'job.idea.failed': 'Idea Brief 정리를 완료하지 못했습니다.',
  'job.concept.run.queued': '컨셉 생성을 준비하고 있습니다.',
  'job.concept.run.started': '컨셉 생성과 법률 근거 확인을 시작했습니다.',
  'job.concept.legal-context.started': '공식 법률 근거 컨텍스트를 확인하고 있습니다.',
  'job.concept.legal-context.completed': '공식 법률 근거 컨텍스트를 준비했습니다.',
  'job.concept.slot.started': '서로 다른 관점의 컨셉 후보 생성을 시작했습니다.',
  'job.concept.slot.generated': '컨셉 후보 구조를 생성했습니다.',
  'job.concept.slot.retrying': '일시적인 오류로 제한된 재시도를 진행하고 있습니다.',
  'job.concept.slot.generation_failed': '후보 생성 계약을 충족하지 못해 다른 후보를 준비합니다.',
  'job.concept.slot.review_failed': '법률 검토 처리를 완료하지 못했습니다.',
  'job.concept.slot.validating_origin': '확정된 아이디어 조건을 확인하고 있습니다.',
  'job.concept.slot.validating_distinctness': '이름이 아니라 실제 사업 구조가 기존 후보와 다른지 확인하고 있습니다.',
  'job.concept.slot.validating_legal': '규제 경계와 구현 방식을 확인하고 있습니다.',
  'job.concept.slot.redesigning': '규제 조건에 맞도록 역할과 운영 흐름을 다시 설계하고 있습니다.',
  'job.concept.slot.replacing': '조건에 맞지 않는 후보를 다른 방향으로 교체하고 있습니다.',
  'job.concept.slot.eligible': '해당 컨셉 후보의 검증이 완료되었습니다.',
  'job.concept.slot.rejected': '조건에 맞지 않는 내부 후보를 사용자 후보에서 제외했습니다.',
  'job.concept.run.needs_input': '컨셉 생성을 위해 확인할 정보가 남아 있습니다.',
  'job.concept.run.completed': '검증된 컨셉 5개가 준비되었습니다.',
  'job.concept.run.failed': '컨셉 생성을 완료하지 못했습니다.',
  'job.marketing.queued': '마케팅 콘텐츠 생성을 준비하고 있습니다.',
  'job.marketing.started': '마케팅 콘텐츠 생성을 시작했습니다.',
  'job.marketing.source_prepared': '확정된 기획 Source를 준비했습니다.',
  'job.marketing.copy_generating': '채널에 맞는 문구를 생성하고 있습니다.',
  'job.marketing.legal_checking': '금지 표현과 필수 고지를 확인하고 있습니다.',
  'job.marketing.completed': '마케팅 콘텐츠가 준비되었습니다.',
  'job.marketing.failed': '마케팅 콘텐츠 생성을 완료하지 못했습니다.',
});

const activeKeys = new Set(ACTIVE_JOB_EVENT_KEYS);

export function isUserVisibleJobEvent(event) {
  return activeKeys.has(event?.messageKey);
}

export function jobEventMessage(event) {
  const template = messages[event?.messageKey] ?? '작업 상태가 업데이트되었습니다.';
  return template.replace(/\{([a-zA-Z0-9_]+)\}/g, (_, key) => {
    const value = event?.messageParams?.[key];
    return typeof value === 'string' || typeof value === 'number' ? String(value) : '';
  });
}
