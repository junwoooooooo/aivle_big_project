export const BUSINESS_PROPOSAL_PHASES = Object.freeze([
  { id: 'CONDITION', label: '아이디어 조건 확인' },
  { id: 'EXPLORE', label: '사업안 탐색·구체화' },
  { id: 'LEGAL', label: '법률·규제 검토' },
  { id: 'FINALIZE', label: '결과 정리' },
]);

const MESSAGE_PHASE = Object.freeze({
  'job.concept-portfolio.queued': 'CONDITION',
  'job.concept-portfolio.running': 'CONDITION',
  'job.concept-portfolio.trace.conditions': 'CONDITION',
  'job.concept-portfolio.trace.conditions-analyzed': 'CONDITION',
  'job.concept-portfolio.ai-executing': 'EXPLORE',
  'job.concept-portfolio.trace.directions': 'EXPLORE',
  'job.concept-portfolio.trace.drafts-generated': 'EXPLORE',
  'job.concept-portfolio.trace.direction-validating': 'EXPLORE',
  'job.concept-portfolio.trace.proposals': 'EXPLORE',
  'job.concept-portfolio.trace.proposal-generated': 'EXPLORE',
  'job.concept-portfolio.trace.proposal-validated': 'EXPLORE',
  'job.concept-portfolio.trace.recovery': 'EXPLORE',
  'job.concept-portfolio.trace.needs-input': 'EXPLORE',
  'job.concept-portfolio.trace.excluded': 'EXPLORE',
  'job.concept-portfolio.trace.excluded-duplicate': 'EXPLORE',
  'job.concept-portfolio.trace.excluded-scope': 'EXPLORE',
  'job.concept-portfolio.trace.excluded-legal': 'EXPLORE',
  'job.concept-portfolio.trace.legal': 'LEGAL',
  'job.concept-portfolio.trace.legal-started': 'LEGAL',
  'job.concept-portfolio.trace.legal-reviewed': 'LEGAL',
  'job.concept-portfolio.trace.ai-completed': 'FINALIZE',
  'job.concept-portfolio.materializing': 'FINALIZE',
  'job.concept-portfolio.summary': 'FINALIZE',
  'job.concept-portfolio.completed': 'FINALIZE',
});

const ACTIVITY = Object.freeze({
  CONDITION: '확정한 아이디어와 사업 조건을 확인하고 있습니다.',
  EXPLORE: '서로 다른 사업 방향을 탐색하고 실행 가능한 사업안으로 구체화하고 있습니다.',
  LEGAL: '사업안에 적용될 수 있는 법률·규제 조건을 검토하고 있습니다.',
  FINALIZE: '검토한 사업안을 비교하고 선택할 수 있도록 결과를 정리하고 있습니다.',
});

const PHASE_INDEX = Object.freeze(Object.fromEntries(BUSINESS_PROPOSAL_PHASES.map((phase, index) => [phase.id, index])));

export function businessProposalEventPhase(event) {
  return MESSAGE_PHASE[event?.messageKey] ?? null;
}

export function businessProposalExecutionPresentation(run, events = []) {
  const reached = events.map(businessProposalEventPhase).filter(Boolean);
  const highest = reached.reduce((current, phase) => PHASE_INDEX[phase] > PHASE_INDEX[current] ? phase : current, 'CONDITION');
  const currentPhaseId = run?.productStatus === 'COMPLETED' ? 'FINALIZE'
    : reached.length > 0 ? highest
      : run?.producedConceptCount > 0 ? 'FINALIZE' : 'CONDITION';
  const state = run?.productStatus === 'FAILED' ? 'FAILED'
    : run?.productStatus === 'NEEDS_INPUT' ? 'NEEDS_INPUT'
      : run?.productStatus === 'COMPLETED' ? 'COMPLETED' : 'RUNNING';
  const latestKnown = businessProposalEventPhase(events.at(-1));
  return {
    phases: BUSINESS_PROPOSAL_PHASES,
    currentPhaseId,
    activity: events.length > 0 && !latestKnown ? '결과를 준비하고 있습니다.' : ACTIVITY[currentPhaseId],
    state,
  };
}

const metricNumber = (value) => {
  const number = Number(value);
  return Number.isFinite(number) && number >= 0 ? number : null;
};

export function businessProposalSummaryMetric(run, events = []) {
  const summary = [...events].reverse().find((event) => event?.messageKey === 'job.concept-portfolio.summary')?.messageParams;
  if (summary) {
    const values = [
      ['검토', metricNumber(summary.reviewed)],
      ['준비', metricNumber(summary.prepared)],
      ['추가 확인', metricNumber(summary.needsInput)],
    ].filter(([, value]) => value != null);
    return values.length ? values.map(([label, value]) => `${value}개 ${label}`).join(' · ') : null;
  }
  const prepared = metricNumber(run?.producedConceptCount);
  const needsInput = metricNumber(run?.openInputCount);
  const values = [];
  if (prepared > 0) values.push(`${prepared}개 준비`);
  if (needsInput > 0) values.push(`${needsInput}개 추가 확인`);
  return values.length ? values.join(' · ') : null;
}
