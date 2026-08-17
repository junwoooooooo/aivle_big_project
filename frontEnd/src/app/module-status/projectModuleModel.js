import { projectRoutes } from '../routing/projectRoutes.js';

export const MODULE_STATUS = Object.freeze({
  NOT_READY: 'NOT_READY', READY: 'READY', QUEUED: 'QUEUED', RUNNING: 'RUNNING',
  NEEDS_INPUT: 'NEEDS_INPUT', COMPLETED: 'COMPLETED', FAILED: 'FAILED', STALE: 'STALE',
  NOT_CONNECTED: 'NOT_CONNECTED',
});

export const MODULE_STATUS_VIEW = Object.freeze({
  NOT_READY: { label: '시작 전', tone: 'neutral' }, READY: { label: '시작 가능', tone: 'info' },
  QUEUED: { label: '대기 중', tone: 'neutral' }, RUNNING: { label: '진행 중', tone: 'info' },
  NEEDS_INPUT: { label: '입력 필요', tone: 'warning' }, COMPLETED: { label: '완료', tone: 'success' },
  FAILED: { label: '확인 필요', tone: 'danger' }, STALE: { label: '업데이트 필요', tone: 'warning' },
  NOT_CONNECTED: { label: '준비 중', tone: 'neutral' },
});

export const PROJECT_MODULES = Object.freeze([
  { id: 'overview', label: '프로젝트 개요', shortLabel: '개요', routeKey: 'overview', defaultStatus: MODULE_STATUS.READY },
  { id: 'idea', label: '아이디어', shortLabel: '아이디어', routeKey: 'idea', defaultStatus: MODULE_STATUS.NEEDS_INPUT },
  { id: 'concepts', label: '사업안', shortLabel: '사업안', routeKey: 'concepts', defaultStatus: MODULE_STATUS.NOT_READY },
  { id: 'market', label: '시장 분석', shortLabel: '시장 분석', routeKey: 'businessValidation', defaultStatus: MODULE_STATUS.NOT_CONNECTED },
  { id: 'businessModel', label: '사업 모델', shortLabel: '사업 모델', routeKey: 'businessValidation', defaultStatus: MODULE_STATUS.NOT_CONNECTED },
  { id: 'conceptRefinement', label: '컨셉 다듬기', shortLabel: '컨셉 다듬기', routeKey: 'businessValidation', defaultStatus: MODULE_STATUS.NOT_READY },
  { id: 'techOps', label: '기술·운영 분석', shortLabel: '기술·운영', routeKey: 'techOps', defaultStatus: MODULE_STATUS.READY },
  { id: 'finance', label: '재무 분석', shortLabel: '재무', routeKey: 'finance', defaultStatus: MODULE_STATUS.READY },
  { id: 'launchReadiness', label: '출시 준비', shortLabel: '출시 준비', routeKey: 'launchReadiness', defaultStatus: MODULE_STATUS.NOT_READY },
  { id: 'marketInterview', label: '시장 인터뷰', shortLabel: '시장 인터뷰', routeKey: 'marketInterview', defaultStatus: MODULE_STATUS.NOT_READY },
  { id: 'marketing', label: '마케팅 실행', shortLabel: '마케팅 실행', routeKey: 'marketing', defaultStatus: MODULE_STATUS.NOT_READY },
  { id: 'settings', label: '프로젝트 설정', shortLabel: '설정', routeKey: 'settings', defaultStatus: MODULE_STATUS.READY },
]);

const API_MODULE_IDS = Object.freeze({
  IDEA: 'idea', CONCEPT_PORTFOLIO: 'concepts', CONCEPT_FACTORY: 'concepts',
  CONCEPT_SELECTION: 'concepts', MARKET_ANALYSIS: 'market', BUSINESS_MODEL: 'businessModel',
  CONCEPT_REFINEMENT: 'conceptRefinement',
  MARKET_INTERVIEW: 'marketInterview', TWIN_SURVEY: 'marketInterview',
  TECH_OPS: 'techOps', FINANCE: 'finance', LAUNCH_READINESS: 'launchReadiness', MARKETING: 'marketing',
});

export function getModuleStatusView(status) { return MODULE_STATUS_VIEW[status] ?? MODULE_STATUS_VIEW.NOT_READY; }

function hasInterviewExecution(item) {
  return Boolean(item?.activeRunId || item?.activeTaskRunId || item?.activeJobId || item?.updatedAt);
}

function canonicalInterviewStatus(items) {
  const marketInterview = items.find((item) => item?.module === 'MARKET_INTERVIEW');
  const legacyTwinSurvey = items.find((item) => item?.module === 'TWIN_SURVEY');
  if (hasInterviewExecution(marketInterview)) return marketInterview;
  if (hasInterviewExecution(legacyTwinSurvey)) return legacyTwinSurvey;
  return marketInterview ?? legacyTwinSurvey;
}

export function normalizeProjectModuleStatuses(items) {
  if (!Array.isArray(items)) return {};
  const normalized = {};
  const priority = [MODULE_STATUS.FAILED, MODULE_STATUS.NEEDS_INPUT, MODULE_STATUS.STALE,
    MODULE_STATUS.RUNNING, MODULE_STATUS.QUEUED, MODULE_STATUS.READY, MODULE_STATUS.COMPLETED,
    MODULE_STATUS.NOT_READY, MODULE_STATUS.NOT_CONNECTED];
  const canonicalInterview = canonicalInterviewStatus(items);
  items.filter((item) => !['MARKET_INTERVIEW', 'TWIN_SURVEY'].includes(item?.module)).forEach((item) => {
    const id = API_MODULE_IDS[item?.module];
    if (!id || !MODULE_STATUS[item?.status]) return;
    const candidate = { ...item, requiredInputs: Array.isArray(item.requiredInputs) ? item.requiredInputs : [] };
    const current = normalized[id];
    if (!current || priority.indexOf(candidate.status) < priority.indexOf(current.status)) normalized[id] = candidate;
  });
  if (canonicalInterview && MODULE_STATUS[canonicalInterview.status]) {
    normalized.marketInterview = {
      ...canonicalInterview,
      requiredInputs: Array.isArray(canonicalInterview.requiredInputs) ? canonicalInterview.requiredInputs : [],
    };
  }
  return normalized;
}
export function getProjectModules(projectId, statuses = {}) {
  return PROJECT_MODULES.map((module) => {
    const state = statuses[module.id];
    return { ...module, ...(state && typeof state === 'object' ? state : {}), href: projectRoutes[module.routeKey](projectId), status: typeof state === 'string' ? state : state?.status ?? module.defaultStatus };
  });
}
export function getProjectModuleByPath(projectId, pathname, statuses = {}) {
  const modules = getProjectModules(projectId, statuses);
  const normalized = pathname.replace(/\/+$/, '');
  if ([projectRoutes.conceptCompare(projectId), projectRoutes.legalReport(projectId)].includes(normalized)) return modules.find((item) => item.id === 'concepts');
  if (/\/(business-validation|market|business-model|concept-refinement)$/.test(normalized)) return modules.find((item) => item.id === 'market');
  if (/\/(launch-readiness|technology|operations)$/.test(normalized)) return modules.find((item) => item.id === 'launchReadiness');
  if (/\/tech-ops$/.test(normalized)) return modules.find((item) => item.id === 'techOps');
  if (/\/finance$/.test(normalized)) return modules.find((item) => item.id === 'finance');
  return modules.find((module) => module.href === normalized) ?? modules[0];
}
