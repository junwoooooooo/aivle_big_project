const messages = Object.freeze({
  'job.idea.queued': '아이디어 정리 작업을 준비하고 있습니다.',
  'job.idea.started': '아이디어 정리를 시작했습니다.',
  'job.idea.extracting': '아이디어의 핵심 조건을 확인하고 있습니다.',
  'job.idea.questions.preparing': '추가로 확인할 내용을 정리하고 있습니다.',
  'job.idea.brief.preparing': '아이디어 정본을 준비하고 있습니다.',
  'job.idea.completed': '아이디어 정리가 완료되었습니다.',
  'job.idea.failed': '아이디어 정리를 완료하지 못했습니다.',
  'job.concept-portfolio.queued': '사업안 검토를 준비하고 있습니다.',
  'job.concept-portfolio.running': '사업 방향을 탐색하고 있습니다.',
  'job.concept-portfolio.ai-executing': '사업안을 구체화하고 관련 법률·규제 조건을 확인하고 있습니다.',
  'job.concept-portfolio.materializing': '검토 결과를 정리하고 있습니다.',
  'job.concept-portfolio.needs-input': '추가로 확인할 사업정보가 있습니다.',
  'job.concept-portfolio.completed': '검토 가능한 사업안이 준비되었습니다.',
  'job.concept-portfolio.failed': '사업안 검토를 완료하지 못했습니다.',
  'job.concept-portfolio.trace.conditions': '확정한 아이디어 조건을 확인하고 있습니다.',
  'job.concept-portfolio.trace.directions': '서로 다른 사업 방향을 탐색하고 있습니다.',
  'job.concept-portfolio.trace.proposals': '사업안 후보를 구체화하고 있습니다.',
  'job.concept-portfolio.trace.legal': '관련 법률·규제 조건을 확인하고 있습니다.',
  'job.concept-portfolio.trace.legal-reviewed': '사업안의 법률·규제 검토 결과를 반영했습니다.',
  'job.concept-portfolio.trace.excluded': '검토 기준에 맞지 않는 방향을 결과에서 제외했습니다.',
  'job.concept-portfolio.trace.excluded-duplicate': '기존 사업안과 실질적으로 차이가 작아 제외했습니다.',
  'job.concept-portfolio.trace.excluded-scope': '확정한 아이디어 범위를 벗어나 이번 결과에서 제외했습니다.',
  'job.concept-portfolio.trace.excluded-legal': '현재 조건에서는 법률·규제 요구를 충족하기 어려워 제외했습니다.',
  'job.concept-portfolio.trace.ai-completed': 'AI 검토를 마치고 결과를 정리하고 있습니다.',
  'job.concept-portfolio.summary': '{reviewed}개의 사업안을 검토해 {prepared}개가 준비되었고, {needsInput}개는 실제 사업정보 확인이 필요합니다.',
  'job.concept-portfolio.continuation.queued': '추가 사업정보 반영을 준비하고 있습니다.',
  'job.concept-portfolio.continuation.running': '추가 사업정보를 반영하고 있습니다.',
  'job.concept-portfolio.continuation.ai-executing': '해당 사업안의 법률·규제 조건을 다시 확인하고 있습니다.',
  'job.concept-portfolio.continuation.materializing': '추가 검토 결과를 정리하고 있습니다.',
  'job.concept-portfolio.continuation.needs-input': '추가로 확인할 사업정보가 있습니다.',
  'job.concept-portfolio.continuation.completed': '추가 사업안 검토가 완료되었습니다.',
  'job.concept-portfolio.continuation.failed': '추가 사업정보를 반영하지 못했습니다.',
  'job.concept-portfolio.selection.queued': '사업안 선택 후 검토를 준비하고 있습니다.',
  'job.concept-portfolio.selection.running': '선택한 사업안의 검증 가정을 확인하고 있습니다.',
  'job.concept-portfolio.selection.materializing': '선택 후 검토 결과를 정리하고 있습니다.',
  'job.concept-portfolio.selection.completed': '사업안 선택 후 검토가 완료되었습니다.',
  'job.concept-portfolio.selection.failed': '사업안 선택 후 검토를 완료하지 못했습니다.',
  'job.concept.run.queued': '이전 사업안 검토를 준비하고 있습니다.',
  'job.concept.run.started': '이전 사업안 검토를 시작했습니다.',
  'job.concept.run.needs_input': '추가로 확인할 사업정보가 있습니다.',
  'job.concept.run.completed': '이전 방식의 사업안 검토가 완료되었습니다.',
  'job.concept.run.failed': '이전 방식의 사업안 검토를 완료하지 못했습니다.',
  'job.marketing.queued': '마케팅 콘텐츠 작업을 준비하고 있습니다.',
  'job.marketing.started': '마케팅 콘텐츠 작업을 시작했습니다.',
  'job.marketing.source_prepared': '확정된 기획 자료를 준비했습니다.',
  'job.marketing.copy_generating': '채널에 맞는 문구를 작성하고 있습니다.',
  'job.marketing.legal_checking': '금지 표현과 필수 고지를 확인하고 있습니다.',
  'job.marketing.completed': '마케팅 콘텐츠가 준비되었습니다.',
  'job.marketing.failed': '마케팅 콘텐츠 작업을 완료하지 못했습니다.',
});

export const ACTIVE_JOB_EVENT_KEYS = Object.freeze(Object.keys(messages));
const activeKeys = new Set(ACTIVE_JOB_EVENT_KEYS);
export function isUserVisibleJobEvent(event) { return activeKeys.has(event?.messageKey); }
export function jobEventMessage(event) {
  const template = messages[event?.messageKey] ?? '작업 상태가 업데이트되었습니다.';
  return template.replace(/\{([a-zA-Z0-9_]+)\}/g, (_, key) => {
    const value = event?.messageParams?.[key];
    return typeof value === 'string' || typeof value === 'number' ? String(value) : '';
  });
}
