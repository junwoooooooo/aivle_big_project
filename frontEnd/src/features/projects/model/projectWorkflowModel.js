import { projectRoutes } from '../routing/projectRoutes.js';

export const PROJECT_AREAS = Object.freeze({
  OVERVIEW: 'OVERVIEW',
  PLAN: 'PLAN',
  REVIEW: 'REVIEW',
  VALIDATE: 'VALIDATE',
  REPORT: 'REPORT',
});

export const PROJECT_STATUS_VIEW = Object.freeze({
  DRAFT: { label: '초안', tone: 'neutral' },
  ACTIVE: { label: '진행 중', tone: 'info' },
  PAUSED: { label: '일시 중지', tone: 'warning' },
  COMPLETED: { label: '완료', tone: 'success' },
  ARCHIVED: { label: '보관됨', tone: 'neutral' },
});

export const TASK_STATUS_VIEW = Object.freeze({
  UNKNOWN: { label: '상태 확인 필요', tone: 'neutral' },
  NOT_STARTED: { label: '시작 전', tone: 'neutral' },
  NEEDS_INPUT: { label: '입력 필요', tone: 'warning' },
  READY: { label: '실행 가능', tone: 'info' },
  QUEUED: { label: '대기 중', tone: 'neutral' },
  RUNNING: { label: '분석 중', tone: 'info' },
  COMPLETED: { label: '완료', tone: 'success' },
  FAILED: { label: '실패', tone: 'danger' },
  BLOCKED: { label: '선행 단계 필요', tone: 'warning' },
  CANCELLED: { label: '취소됨', tone: 'neutral' },
});

export const PROJECT_AREA_DEFINITIONS = Object.freeze([
  { id: PROJECT_AREAS.OVERVIEW, label: 'Overview', path: '' },
  { id: PROJECT_AREAS.PLAN, label: 'Plan', path: 'plan' },
  { id: PROJECT_AREAS.REVIEW, label: 'Review', path: 'review' },
  { id: PROJECT_AREAS.VALIDATE, label: 'Validate', path: 'validate' },
  { id: PROJECT_AREAS.REPORT, label: 'Report', path: 'report' },
]);

export const STAGE_AREA = Object.freeze({
  DOCUMENT: PROJECT_AREAS.PLAN,
  STRUCTURING: PROJECT_AREAS.PLAN,
  LEGAL_REVIEW: PROJECT_AREAS.REVIEW,
  FEASIBILITY: PROJECT_AREAS.REVIEW,
  FINANCIAL: PROJECT_AREAS.REVIEW,
  PERSONA_CONFIGURATION: PROJECT_AREAS.VALIDATE,
  PANEL_SURVEY: PROJECT_AREAS.VALIDATE,
  PANEL_DISCUSSION: PROJECT_AREAS.VALIDATE,
  REPORT: PROJECT_AREAS.REPORT,
  COMPLETED: PROJECT_AREAS.REPORT,
});

export const STAGE_VIEW = Object.freeze({
  DOCUMENT: { label: '아이디어 입력', route: '' },
  STRUCTURING: { label: '아이디어 해석', route: '' },
  LEGAL_REVIEW: { label: '법률·규제 검토', route: 'legal' },
  FEASIBILITY: { label: '콘셉트 생성', route: 'journey/concept' },
  FINANCIAL: { label: '콘셉트 분석', route: 'journey/concept-analysis' },
  PERSONA_CONFIGURATION: { label: '콘셉트 선택', route: 'journey/concept-selection' },
  PANEL_SURVEY: { label: '페르소나', route: 'journey/persona' },
  PANEL_DISCUSSION: { label: '인터뷰', route: 'journey/interview' },
  MARKETING: { label: '마케팅', route: 'journey/marketing' },
  REPORT: { label: '최종 보고서', route: 'journey/final-report' },
  COMPLETED: { label: '최종 보고서', route: 'journey/final-report' },
});

const JOURNEY_STAGE_INDEX = Object.freeze({
  DOCUMENT: 0, STRUCTURING: 0, LEGAL_REVIEW: 1, FEASIBILITY: 2, FINANCIAL: 3,
  PERSONA_CONFIGURATION: 4, PANEL_SURVEY: 5, PANEL_DISCUSSION: 6,
  MARKETING: 7, REPORT: 8, COMPLETED: 9,
});

export function getProjectBasePath(projectId) {
  return projectRoutes.base(projectId);
}

export function getProjectArea(project) {
  return STAGE_AREA[project?.stage] ?? PROJECT_AREAS.PLAN;
}

export function getProjectStatusView(status) {
  return PROJECT_STATUS_VIEW[status] ?? { label: '상태 확인 필요', tone: 'neutral' };
}

export function getTaskStatusForProject(project) {
  if (project?.status === 'COMPLETED') return 'COMPLETED';
  if (project?.status === 'ARCHIVED') return 'CANCELLED';
  return 'UNKNOWN';
}

export function getProjectNextAction(project) {
  if (project?.status === 'COMPLETED') {
    return {
      type: 'COMPLETED',
      label: '통합 보고서를 검토하세요',
      description: '검증 결과와 근거를 보고서에서 다시 확인할 수 있습니다.',
      route: `${getProjectBasePath(project.projectId)}/journey/final-report`,
      priority: 'NORMAL',
    };
  }
  const stage = STAGE_VIEW[project?.stage] ?? STAGE_VIEW.DOCUMENT;
  return {
    type: getTaskStatusForProject(project),
    label: `${stage.label}을(를) 계속하세요`,
    description: '현재 프로젝트의 입력과 결과를 확인한 뒤 다음 검증 단계로 이어갈 수 있습니다.',
    route: stage.route ? `${getProjectBasePath(project.projectId)}/${stage.route}` : getProjectBasePath(project.projectId),
    priority: 'NORMAL',
  };
}

export function getProjectProgress(project) {
  const index = JOURNEY_STAGE_INDEX[project?.stage] ?? 0;
  return Math.max(0, Math.min(100, Math.round((index / 9) * 100)));
}

export function getAreaSummary(project) {
  const currentArea = getProjectArea(project);
  const currentIndex = PROJECT_AREA_DEFINITIONS.findIndex((definition) => definition.id === currentArea);
  return PROJECT_AREA_DEFINITIONS
    .filter(({ id }) => id !== PROJECT_AREAS.OVERVIEW)
    .map((definition, index) => ({
      ...definition,
      taskStatus: project?.status === 'COMPLETED' || index < currentIndex - 1
        ? 'COMPLETED'
        : index === currentIndex - 1
          ? getTaskStatusForProject(project)
          : 'BLOCKED',
    }));
}
