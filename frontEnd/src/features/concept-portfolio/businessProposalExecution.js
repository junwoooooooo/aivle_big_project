export const BUSINESS_PROPOSAL_PHASES = Object.freeze([
  { id: 'DIRECTION', label: '사업 방향 구성' },
  { id: 'GENERATE', label: '사업안 생성' },
  { id: 'LEGAL', label: '법률·규제 검토' },
  { id: 'DISTINCTNESS', label: '차별성 확인' },
  { id: 'READY', label: '비교 준비' },
]);

function eventPhase(event) {
  const value = `${event?.stage ?? ''} ${event?.messageKey ?? ''}`.toLowerCase();
  if (/completed|materializing|ai-completed|summary/.test(value)) return 'READY';
  if (/duplicate|distinct|excluded/.test(value)) return 'DISTINCTNESS';
  if (/legal/.test(value)) return 'LEGAL';
  if (/proposal|generate|draft/.test(value)) return 'GENERATE';
  if (/direction|condition|queued|running/.test(value)) return 'DIRECTION';
  return null;
}

const ACTIVITY = Object.freeze({
  DIRECTION: '확정한 아이디어에서 서로 다른 사업 방향을 구성하고 있습니다.',
  GENERATE: '사업 방향을 실행 가능한 사업안 후보로 구체화하고 있습니다.',
  LEGAL: '각 사업안에서 확인해야 할 법률·규제 요소를 검토하고 있습니다.',
  DISTINCTNESS: '사업안끼리 충분히 다른 방향인지 확인하고 있습니다.',
  READY: '같은 기준으로 비교할 수 있도록 결과를 정리하고 있습니다.',
});

export function businessProposalExecutionPresentation(run, events = []) {
  const latestPhase = eventPhase(events.at(-1));
  const currentPhaseId = [...events].reverse().map(eventPhase).find(Boolean)
    ?? (run?.producedConceptCount > 0 ? 'READY' : 'DIRECTION');
  const state = run?.productStatus === 'FAILED' ? 'FAILED'
    : run?.productStatus === 'NEEDS_INPUT' ? 'NEEDS_INPUT'
      : run?.productStatus === 'COMPLETED' ? 'COMPLETED' : 'RUNNING';
  return {
    phases: BUSINESS_PROPOSAL_PHASES,
    currentPhaseId,
    activity: events.length > 0 && !latestPhase ? '결과를 준비하고 있습니다.' : ACTIVITY[currentPhaseId] ?? '결과를 준비하고 있습니다.',
    state,
  };
}
