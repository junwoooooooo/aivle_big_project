export const IDEA_EXECUTION_PHASES = Object.freeze([
  { id: 'INPUT_REVIEW', label: '입력 내용 확인' },
  { id: 'ELIGIBILITY', label: '진행 가능 여부 확인' },
  { id: 'INTERPRETATION', label: '아이디어 정리' },
  { id: 'PREPARE_REVIEW', label: '확인할 내용 준비' },
]);

const PHASE_BY_KEY = Object.freeze({
  'job.idea.queued': 'INPUT_REVIEW',
  'job.idea.started': 'INPUT_REVIEW',
  'job.idea.extracting': 'ELIGIBILITY',
  'job.idea.questions.preparing': 'INTERPRETATION',
  'job.idea.brief.preparing': 'PREPARE_REVIEW',
  'job.idea.completed': 'PREPARE_REVIEW',
});

const PHASE_BY_STAGE = Object.freeze({
  QUEUED: 'INPUT_REVIEW',
  CLAIMED: 'INPUT_REVIEW',
  SAFETY_REVIEW: 'ELIGIBILITY',
  IDEA_INTERPRETATION: 'INTERPRETATION',
  INTERPRETATION_COMMIT: 'PREPARE_REVIEW',
  COMPLETED: 'PREPARE_REVIEW',
});

const eventPhase = (event) => PHASE_BY_STAGE[event?.stage] ?? PHASE_BY_KEY[event?.messageKey];

const ACTIVITY_BY_PHASE = Object.freeze({
  INPUT_REVIEW: '입력한 내용과 참고 자료를 확인하고 있습니다.',
  ELIGIBILITY: '다음 단계로 진행할 수 있는 아이디어인지 확인하고 있습니다.',
  INTERPRETATION: '입력 내용을 바탕으로 사업 아이디어의 핵심을 정리하고 있습니다.',
  PREPARE_REVIEW: '직접 확인하고 수정할 내용을 준비하고 있습니다.',
});

export function ideaExecutionPresentation(events = []) {
  const latest = events.at(-1);
  const explicit = [...events].reverse().map(eventPhase).find(Boolean);
  const eligibilitySeen = events.some((event) => /safety|eligib|policy/i.test(`${event.stage ?? ''} ${event.messageKey ?? ''}`));
  const currentPhaseId = explicit ?? (eligibilitySeen ? 'ELIGIBILITY' : 'INPUT_REVIEW');
  return {
    phases: IDEA_EXECUTION_PHASES,
    currentPhaseId,
    activity: latest && !eventPhase(latest) && !eligibilitySeen
      ? '결과를 준비하고 있습니다.' : ACTIVITY_BY_PHASE[currentPhaseId] ?? '결과를 준비하고 있습니다.',
    state: latest?.status === 'FAILED' ? 'FAILED' : latest?.status === 'NEEDS_INPUT' ? 'NEEDS_INPUT' : 'RUNNING',
  };
}
