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
  { id: 'idea', label: '1. 아이디어', shortLabel: '아이디어', routeKey: 'idea', defaultStatus: MODULE_STATUS.NEEDS_INPUT },
  { id: 'concepts', label: '2. 사업안', shortLabel: '사업안', routeKey: 'concepts', defaultStatus: MODULE_STATUS.NOT_READY },
  { id: 'market', label: '3. 시장 분석', shortLabel: '시장 분석', routeKey: 'market', defaultStatus: MODULE_STATUS.NOT_CONNECTED },
  { id: 'businessModel', label: '4. 사업 모델', shortLabel: '사업 모델', routeKey: 'businessModel', defaultStatus: MODULE_STATUS.NOT_CONNECTED },
  { id: 'launchReadiness', label: '5. 출시 준비 분석', shortLabel: '출시 준비', routeKey: 'launchReadiness', defaultStatus: MODULE_STATUS.NOT_READY },
  { id: 'twinSurvey', label: '7. 트윈 패널 조사', shortLabel: '트윈 패널', routeKey: 'twinSurvey', defaultStatus: MODULE_STATUS.NOT_READY },
  { id: 'marketing', label: '8. 마케팅 콘텐츠 제작', shortLabel: '마케팅 콘텐츠', routeKey: 'marketing', defaultStatus: MODULE_STATUS.NOT_READY },
  { id: 'settings', label: '프로젝트 설정', shortLabel: '설정', routeKey: 'settings', defaultStatus: MODULE_STATUS.READY },
]);

const API_MODULE_IDS = Object.freeze({
  IDEA: 'idea', CONCEPT_PORTFOLIO: 'concepts', CONCEPT_FACTORY: 'concepts',
  CONCEPT_SELECTION: 'concepts', MARKET_ANALYSIS: 'market', BUSINESS_MODEL: 'businessModel',
  TWIN_SURVEY: 'twinSurvey',
  TECH_OPS: 'launchReadiness', FINANCE: 'launchReadiness', MARKETING: 'marketing',
});

export function getModuleStatusView(status) { return MODULE_STATUS_VIEW[status] ?? MODULE_STATUS_VIEW.NOT_READY; }
export function normalizeProjectModuleStatuses(items) {
  if (!Array.isArray(items)) return {};
  const normalized = {};
  const priority = [MODULE_STATUS.FAILED, MODULE_STATUS.NEEDS_INPUT, MODULE_STATUS.STALE,
    MODULE_STATUS.RUNNING, MODULE_STATUS.QUEUED, MODULE_STATUS.READY, MODULE_STATUS.COMPLETED,
    MODULE_STATUS.NOT_READY, MODULE_STATUS.NOT_CONNECTED];
  items.forEach((item) => {
    const id = API_MODULE_IDS[item?.module];
    if (!id || !MODULE_STATUS[item?.status]) return;
    const candidate = { ...item, requiredInputs: Array.isArray(item.requiredInputs) ? item.requiredInputs : [] };
    const current = normalized[id];
    if (!current || priority.indexOf(candidate.status) < priority.indexOf(current.status)) normalized[id] = candidate;
    else if (id === 'launchReadiness') normalized[id] = { ...current,
      requiredInputs: [...new Set([...(current.requiredInputs ?? []), ...candidate.requiredInputs])] };
  });
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
  if (/\/(launch-readiness|technology|operations|tech-ops|finance)$/.test(normalized)) return modules.find((item) => item.id === 'launchReadiness');
  return modules.find((module) => module.href === normalized) ?? modules[0];
}
