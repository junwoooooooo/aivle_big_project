import { projectRoutes } from '../routing/projectRoutes.js';
import { MODULE_STATUS } from './projectModuleModel.js';

export const JOURNEY_STATUS = Object.freeze({
  NOT_STARTED: 'NOT_STARTED',
  READY: 'READY',
  IN_PROGRESS: 'IN_PROGRESS',
  NEEDS_INPUT: 'NEEDS_INPUT',
  ATTENTION: 'ATTENTION',
  STALE: 'STALE',
  COMPLETED: 'COMPLETED',
  OPTIONAL: 'OPTIONAL',
});

export const JOURNEY_STATUS_VIEW = Object.freeze({
  NOT_STARTED: { label: '시작 전', tone: 'neutral' },
  READY: { label: '시작 가능', tone: 'info' },
  IN_PROGRESS: { label: '진행 중', tone: 'info' },
  NEEDS_INPUT: { label: '입력 필요', tone: 'warning' },
  ATTENTION: { label: '확인 필요', tone: 'danger' },
  STALE: { label: '업데이트 필요', tone: 'warning' },
  COMPLETED: { label: '완료', tone: 'success' },
  OPTIONAL: { label: '선택 기능', tone: 'neutral' },
});

export const PROJECT_JOURNEYS = Object.freeze([
  { id: 'planning', label: '1. 사업 기획', shortLabel: '사업 기획', moduleIds: ['idea', 'concepts'] },
  { id: 'validation', label: '2. 사업 검증', shortLabel: '사업 검증', moduleIds: ['market', 'businessModel', 'conceptRefinement'] },
  { id: 'launch', label: '3. 출시 준비', shortLabel: '출시 준비', moduleIds: [], optional: true },
  { id: 'interview', label: '4. 가상 인터뷰', shortLabel: '가상 인터뷰', moduleIds: ['marketInterview'] },
  { id: 'marketingStrategy', label: '5. 마케팅 전략', shortLabel: '마케팅 전략', moduleIds: ['marketing'] },
  { id: 'finalReport', label: '6. 최종 보고서', shortLabel: '최종 보고서', moduleIds: [], optional: true },
]);

const PATH_TO_JOURNEY = Object.freeze({
  overview: 'overview', idea: 'planning', concepts: 'planning', 'business-validation': 'validation', market: 'validation',
  'business-model': 'validation', 'concept-refinement': 'validation', 'launch-readiness': 'launch', technology: 'launch', operations: 'launch', 'tech-ops': 'launch', finance: 'launch',
  'market-interview': 'interview', 'virtual-interview': 'interview', 'twin-survey': 'interview', marketing: 'marketingStrategy', 'final-report': 'finalReport',
});

export function getJourneyStatusView(status) {
  return JOURNEY_STATUS_VIEW[status] ?? JOURNEY_STATUS_VIEW.NOT_STARTED;
}

export function getJourneyActionView(status) {
  return ({
    [JOURNEY_STATUS.NOT_STARTED]: '시작하기',
    [JOURNEY_STATUS.READY]: '시작하기',
    [JOURNEY_STATUS.IN_PROGRESS]: '계속하기',
    [JOURNEY_STATUS.NEEDS_INPUT]: '입력하기',
    [JOURNEY_STATUS.ATTENTION]: '확인하기',
    [JOURNEY_STATUS.STALE]: '업데이트하기',
    [JOURNEY_STATUS.COMPLETED]: '결과 보기',
    [JOURNEY_STATUS.OPTIONAL]: '열기',
  })[status] ?? '확인하기';
}

export function aggregateJourneyStatus(moduleStatuses = []) {
  const statuses = moduleStatuses.map((item) => typeof item === 'string' ? item : item?.status).filter(Boolean);
  if (statuses.length === 0) return JOURNEY_STATUS.NOT_STARTED;
  if (statuses.includes(MODULE_STATUS.NEEDS_INPUT)) return JOURNEY_STATUS.NEEDS_INPUT;
  if (statuses.includes(MODULE_STATUS.FAILED)) return JOURNEY_STATUS.ATTENTION;
  if (statuses.includes(MODULE_STATUS.STALE)) return JOURNEY_STATUS.STALE;
  if (statuses.some((status) => [MODULE_STATUS.RUNNING, MODULE_STATUS.QUEUED].includes(status))) return JOURNEY_STATUS.IN_PROGRESS;
  if (statuses.every((status) => status === MODULE_STATUS.COMPLETED)) return JOURNEY_STATUS.COMPLETED;
  if (statuses.some((status) => status === MODULE_STATUS.COMPLETED)) return JOURNEY_STATUS.IN_PROGRESS;
  if (statuses.every((status) => status === MODULE_STATUS.READY)) return JOURNEY_STATUS.READY;
  if (statuses.some((status) => status === MODULE_STATUS.READY)) return JOURNEY_STATUS.READY;
  return JOURNEY_STATUS.NOT_STARTED;
}

export function getJourneyByPath(pathname) {
  const segments = pathname.replace(/\/+$/, '').split('/');
  const routeSegment = segments.includes('launch-readiness') ? 'launch-readiness'
    : ['compare', 'legal-report'].includes(segments.at(-1)) ? 'concepts' : segments.at(-1);
  const id = PATH_TO_JOURNEY[routeSegment] ?? 'overview';
  return id === 'overview' ? { id: 'overview', label: '프로젝트 개요', shortLabel: '프로젝트 개요', moduleIds: [] }
    : PROJECT_JOURNEYS.find((journey) => journey.id === id);
}

export function getProjectJourneys(projectId, modules = []) {
  return PROJECT_JOURNEYS.map((journey) => {
    const sourceChildren = (journey.statusModuleIds ?? journey.moduleIds)
      .map((id) => modules.find((module) => module.id === id)).filter(Boolean);
    const status = journey.optional ? JOURNEY_STATUS.OPTIONAL : aggregateJourneyStatus(sourceChildren);
    const children = journey.id === 'validation' ? [{
      id: 'businessValidation', label: '사업성 검증', shortLabel: '사업성 검증',
      href: projectRoutes.businessValidation(projectId), status,
    }] : journey.id === 'launch' ? [] : sourceChildren;
    return {
      ...journey,
      children,
      status,
      href: journey.id === 'finalReport' ? projectRoutes.finalReport(projectId) : getJourneyEntryRoute(projectId, journey, children),
    };
  });
}

export function getJourneyEntryRoute(projectId, journey, children = []) {
  if (journey.id === 'finalReport') return projectRoutes.finalReport(projectId);
  if (journey.id === 'validation') return projectRoutes.businessValidation(projectId);
  if (journey.id === 'launch') return projectRoutes.launchReadiness(projectId);
  const next = children.find((module) => module.status !== MODULE_STATUS.COMPLETED) ?? children.at(-1);
  return next?.href ?? projectRoutes.overview(projectId);
}

export function getJourneyProgress(journeys = []) {
  const required = journeys.filter((journey) => journey.status !== JOURNEY_STATUS.OPTIONAL);
  return { completed: required.filter((journey) => journey.status === JOURNEY_STATUS.COMPLETED).length, total: required.length };
}
