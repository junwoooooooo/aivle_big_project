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
  overview: (projectId) => projectBase(projectId),
  getStarted: (projectId) => `${projectBase(projectId)}/get-started`,
  plan: (projectId) => `${projectBase(projectId)}/plan`,
  briefSettings: (projectId) => `${projectBase(projectId)}/settings`,
  documents: projectBase,
  structure: projectBase,
  review: (projectId) => `${projectBase(projectId)}/review`,
  legal: (projectId) => `${projectBase(projectId)}/legal`,
  feasibility: (projectId) => `${projectBase(projectId)}/journey/concept`,
  financial: (projectId) => `${projectBase(projectId)}/journey/concept-analysis`,
  financialNew: (projectId) => `${projectBase(projectId)}/journey/concept-analysis`,
  financialDetail: (projectId) => `${projectBase(projectId)}/journey/concept-analysis`,
  validate: (projectId) => `${projectBase(projectId)}/journey/persona`,
  personas: (projectId) => `${projectBase(projectId)}/journey/persona`,
  interview: (projectId) => `${projectBase(projectId)}/journey/interview`,
  interviewDetail: (projectId) =>
    `${projectBase(projectId)}/journey/interview`,
  marketResponse: (projectId) => `${projectBase(projectId)}/journey/interview`,
  marketResponseDetail: (projectId) => `${projectBase(projectId)}/journey/interview`,
  marketing: (projectId) => `${projectBase(projectId)}/journey/marketing`,
  marketingNew: (projectId) => `${projectBase(projectId)}/journey/marketing`,
  marketingContent: (projectId) => `${projectBase(projectId)}/journey/marketing`,
  report: (projectId) => `${projectBase(projectId)}/journey/final-report`,
  settings: (projectId) => `${projectBase(projectId)}/settings`,
  danger: (projectId) => `${projectBase(projectId)}/settings`,
});
