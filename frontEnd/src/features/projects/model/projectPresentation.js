import { JOURNEY_STATUS } from '../../../app/module-status/projectJourneyModel.js';

export const PROJECT_PRESENTATION_STATE = Object.freeze({
  NOT_STARTED: 'NOT_STARTED',
  IN_PROGRESS: 'IN_PROGRESS',
  NEEDS_ATTENTION: 'NEEDS_ATTENTION',
  COMPLETED: 'COMPLETED',
});

export const PROJECT_PRESENTATION_VIEW = Object.freeze({
  NOT_STARTED: { label: '시작 전', tone: 'neutral' },
  IN_PROGRESS: { label: '진행 중', tone: 'info' },
  NEEDS_ATTENTION: { label: '확인 필요', tone: 'warning' },
  COMPLETED: { label: '완료', tone: 'success' },
});

const ATTENTION_STATES = new Set([
  JOURNEY_STATUS.NEEDS_INPUT,
  JOURNEY_STATUS.ATTENTION,
  JOURNEY_STATUS.STALE,
]);

function hasJourneyStarted(journey) {
  if (!Array.isArray(journey.children)) return true;
  return journey.children.some((module) =>
    [JOURNEY_STATUS.IN_PROGRESS, JOURNEY_STATUS.COMPLETED, JOURNEY_STATUS.ATTENTION, JOURNEY_STATUS.STALE].includes(module.status)
    || Boolean(module.updatedAt || module.activeRunId || module.activeTaskRunId || module.sourceSnapshotId || module.confirmedSnapshotId),
  );
}

export function getProjectPresentationView(state) {
  return PROJECT_PRESENTATION_VIEW[state] ?? PROJECT_PRESENTATION_VIEW.NOT_STARTED;
}

export function deriveProjectPresentationState(journeys = []) {
  const required = journeys.filter(({ status }) => status !== JOURNEY_STATUS.OPTIONAL);
  if (required.length > 0 && required.every(({ status }) => status === JOURNEY_STATUS.COMPLETED)) {
    return PROJECT_PRESENTATION_STATE.COMPLETED;
  }
  if (required.some((journey) => ATTENTION_STATES.has(journey.status)
    && (journey.status !== JOURNEY_STATUS.NEEDS_INPUT || hasJourneyStarted(journey)))) {
    return PROJECT_PRESENTATION_STATE.NEEDS_ATTENTION;
  }
  if (required.some(({ status }) => [JOURNEY_STATUS.IN_PROGRESS, JOURNEY_STATUS.COMPLETED].includes(status))) {
    return PROJECT_PRESENTATION_STATE.IN_PROGRESS;
  }
  return PROJECT_PRESENTATION_STATE.NOT_STARTED;
}

export function projectNextAction(project) {
  if (project.presentationState === PROJECT_PRESENTATION_STATE.NEEDS_ATTENTION) {
    return { label: project.attentionReason || '확인이 필요한 항목을 살펴보세요.', actionLabel: '확인하기' };
  }
  if (project.presentationState === PROJECT_PRESENTATION_STATE.COMPLETED) {
    return { label: '최종 결과를 확인할 수 있습니다.', actionLabel: '결과 보기' };
  }
  if (project.presentationState === PROJECT_PRESENTATION_STATE.IN_PROGRESS) {
    return { label: `${project.stageLabel}에서 이어서 진행하세요.`, actionLabel: '계속하기' };
  }
  return { label: '사업 아이디어를 정리하며 첫 단계를 시작하세요.', actionLabel: '시작하기' };
}
