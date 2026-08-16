const projectBase = (projectId) => `/app/projects/${encodeURIComponent(projectId)}`;

export const appRoutes = Object.freeze({
  home: '/app',
  projects: '/app/projects',
  newProject: '/app/projects/new',
  profileSettings: '/app/settings/profile',
  securitySettings: '/app/settings/security',
});

export const projectRoutes = Object.freeze({
  base: projectBase,
  overview: (projectId) => `${projectBase(projectId)}/overview`,
  idea: (projectId) => `${projectBase(projectId)}/idea`,
  concepts: (projectId) => `${projectBase(projectId)}/concepts`,
  conceptCompare: (projectId) => `${projectBase(projectId)}/concepts/compare`,
  legalReport: (projectId) => `${projectBase(projectId)}/concepts/legal-report`,
  market: (projectId) => `${projectBase(projectId)}/market`,
  businessModel: (projectId) => `${projectBase(projectId)}/business-model`,
  twinSurvey: (projectId) => `${projectBase(projectId)}/twin-survey`,
  launchReadiness: (projectId) => `${projectBase(projectId)}/launch-readiness`,
  launchReadinessReport: (projectId, reportType, modules = []) => {
    const route = `${projectBase(projectId)}/launch-readiness/reports/${encodeURIComponent(reportType)}`;
    if (reportType !== 'integrated' || modules.length === 0) return route;
    return `${route}?${modules.map((module) => `modules=${encodeURIComponent(module)}`).join('&')}`;
  },
  technology: (projectId) => `${projectBase(projectId)}/technology`,
  operations: (projectId) => `${projectBase(projectId)}/operations`,
  techOps: (projectId) => `${projectBase(projectId)}/launch-readiness`,
  finance: (projectId) => `${projectBase(projectId)}/launch-readiness`,
  marketing: (projectId) => `${projectBase(projectId)}/marketing`,
  finalReport: (projectId) => `${projectBase(projectId)}/final-report`,
  settings: (projectId) => `${projectBase(projectId)}/settings`,
});

export function getProjectRoute(projectId, moduleId) {
  const route = projectRoutes[moduleId];
  return typeof route === 'function' ? route(projectId) : projectRoutes.overview(projectId);
}
