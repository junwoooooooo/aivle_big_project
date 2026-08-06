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
  market: (projectId) => `${projectBase(projectId)}/market`,
  businessPersonaTest: (projectId) => `${projectBase(projectId)}/business-persona-test`,
  marketing: (projectId) => `${projectBase(projectId)}/marketing`,
  settings: (projectId) => `${projectBase(projectId)}/settings`,
});

export function getProjectRoute(projectId, moduleId) {
  const route = projectRoutes[moduleId];
  return typeof route === 'function' ? route(projectId) : projectRoutes.overview(projectId);
}
