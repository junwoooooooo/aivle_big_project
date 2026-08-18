export const ACTIVE_MARKET_INTERVIEW_STATES = new Set(['RUNNING']);

export function marketInterviewView(value) {
  const state = value?.state ?? 'NOT_STARTED';
  return {
    ...value,
    state,
    active: ACTIVE_MARKET_INTERVIEW_STATES.has(state),
    canStart: state === 'NOT_STARTED' || state === 'STALE',
    canRetry: value?.retryAllowed === true,
    canRestart: value?.restartAllowed === true,
    result: value?.result?.synthetic === true ? value.result : null,
  };
}
