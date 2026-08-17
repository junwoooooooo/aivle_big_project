import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { pathToFileURL } from 'node:url';

const root = resolve(import.meta.dirname, '..');
const read = (path) => readFileSync(resolve(root, path), 'utf8');
const assert = (condition, message) => {
  if (!condition) throw new Error(message);
};

const limits = read('backend/src/main/java/com/aivle/backend/pipeline/concept/domain/ConceptFactoryLimits.java');
assert(/SLOT_COUNT\s*=\s*5/.test(limits), 'Concept target must be 5');
assert(/MAX_INSPECTED_CANDIDATES\s*=\s*SLOT_COUNT\s*\*\s*\(1\s*\+\s*MAX_REPLACEMENT_ROUNDS\s*\+\s*MAX_LEGAL_REDESIGNS_PER_SLOT\)/s.test(limits),
  'Inspected candidate limit must derive from slots, replacement rounds, and legal redesigns');
assert(/MAX_REPLACEMENT_ROUNDS\s*=\s*2/.test(limits), 'Replacement round limit must be 2');

const env = read('.env.example');
const compose = read('compose.yaml');
const envKeys = new Set([...env.matchAll(/^([A-Z][A-Z0-9_]*)=/gm)].map((match) => match[1]));
for (const key of ['AI_CONCEPT_GENERATION_CONCURRENCY', 'AI_PROVIDER', 'AI_MODEL', 'AI_INTERNAL_SERVICE_TOKEN',
  'MARKET_INTERVIEW_MODEL', 'MARKET_INTERVIEW_TEMPERATURE', 'MARKET_INTERVIEW_REASONING_EFFORT',
  'MARKET_INTERVIEW_CONCURRENCY']) {
  assert(envKeys.has(key), `.env.example is missing ${key}`);
  assert(compose.includes(`\${${key}`), `compose.yaml is missing ${key}`);
}

const activeFrontend = [
  'frontEnd/Dockerfile',
  'frontEnd/src/app/routing/AppRouter.jsx',
  'frontEnd/src/app/project-shell/ProjectModulePages.jsx',
  'frontEnd/src/features/idea-intake/components/IdeaIntakeForm.jsx',
  'frontEnd/src/features/idea-intake/pages/IdeaIntakePage.jsx',
  'frontEnd/src/features/market-integration/pages/MarketIntegrationPage.jsx',
].map(read).join('\n');
for (const forbidden of [
  'VITE_CONVERSATIONAL_VALIDATION_WORKSPACE',
  'LegacyPipelineSurface',
  'R2A',
  '개발용 fixture',
  '후속 단계에서',
  '다음 구현 단계',
]) {
  assert(!activeFrontend.includes(forbidden), `Active frontend still contains: ${forbidden}`);
}

const dockerE2e = [
  read('scripts/docker-e2e-smoke.ps1'),
  read('scripts/docker-failure-e2e.ps1'),
].join('\n');
for (const forbidden of [
  /\/api\/v1\/projects\/[^\s"']*\/ai-tasks\/(?:artifact-)?smoke/,
  /\/api\/v1\/jobs\//,
  /\/marketing-contents(?:\/|"|')/,
  /\banalysis_jobs\b/,
  /\bai_task_results\b/,
  /\bai_task_artifacts\b/,
  /\bmarketing_content_versions\b/,
  /\[long\]\s*\$\w*(?:Job|TaskRun)Id/,
]) {
  assert(!forbidden.test(dockerE2e), `Docker E2E restored a removed contract: ${forbidden}`);
}
for (const required of [
  '/api/v2/projects/$ProjectId/task-runs/$TaskRunId',
  '/api/v3/projects/$projectId/evidence-artifacts/$storedArtifactId/download',
  '/internal/e2e/projects/$ProjectId/task-runs',
  'task_runs',
  'task_results',
  'project_evidence_artifacts',
]) {
  assert(dockerE2e.includes(required), `Docker E2E is missing current authority: ${required}`);
}

const e2eController = read(
  'backend/src/main/java/com/aivle/backend/taskrun/e2e/E2eTaskRunController.java',
);
const e2eService = read(
  'backend/src/main/java/com/aivle/backend/taskrun/e2e/E2eTaskRunService.java',
);
assert(e2eController.includes('@Profile("e2e")'), 'E2E controller must be profile isolated');
assert(e2eService.includes('@Profile("e2e")'), 'E2E service must be profile isolated');
assert(e2eController.includes('/internal/e2e/'), 'E2E seam must remain internal');
for (const authority of [
  'TaskRunService',
  'TaskResultRepository',
  'ProjectEvidenceArtifactService',
]) {
  assert(e2eService.includes(authority), `E2E seam bypasses current ${authority} authority`);
}

const journeyModule = await import(pathToFileURL(resolve(root,
  'frontEnd/src/app/module-status/projectJourneyModel.js')).href);
const expectedJourneyIds = ['planning', 'validation', 'launch', 'interview', 'marketingStrategy', 'finalReport'];
const expectedJourneyLabels = ['사업 기획', '사업 검증', '출시 준비', '가상 인터뷰', '마케팅 전략', '최종 보고서'];
const expectedJourneyModules = [
  ['idea', 'concepts'], ['market', 'businessModel'], ['launchReadiness'],
  ['marketInterview', 'twinSurvey'], ['marketing'], [],
];
assert(JSON.stringify(journeyModule.PROJECT_JOURNEYS.map(({ id }) => id)) === JSON.stringify(expectedJourneyIds),
  'Top-level Journey IDs must remain the canonical six-stage taxonomy');
assert(JSON.stringify(journeyModule.PROJECT_JOURNEYS.map(({ shortLabel }) => shortLabel)) === JSON.stringify(expectedJourneyLabels),
  'Top-level Journey labels must remain the canonical six-stage taxonomy');
assert(JSON.stringify(journeyModule.PROJECT_JOURNEYS.map(({ moduleIds }) => moduleIds)) === JSON.stringify(expectedJourneyModules),
  'Current modules must remain grouped under the canonical six-stage taxonomy');
assert(journeyModule.getJourneyByPath('/app/projects/41/market-interview').id === 'interview'
  && journeyModule.getJourneyByPath('/app/projects/41/twin-survey').id === 'interview',
  'Market Interview and Twin Survey must share the virtual interview top-level Journey');

const journeyUx = [
  read('frontEnd/src/app/project-shell/ProjectModulePages.jsx'),
  read('frontEnd/src/features/projects/WorkspaceHomePage.jsx'),
  read('frontEnd/src/features/projects/ProjectPages.jsx'),
  read('frontEnd/src/features/landing/components/WorkflowSection.jsx'),
].join('\n');
assert(!journeyUx.includes('8단계 사업 여정') && !journeyUx.includes('여덟 단계'),
  'Active UX must not restore the eight-stage Journey regression');
assert(journeyUx.includes('6단계 사업 여정') && journeyUx.includes('여섯 단계'),
  'Active UX must explain the canonical six-stage Journey');
const landingWorkflow = read('frontEnd/src/features/landing/data/landingData.js');
for (const [number, title] of expectedJourneyLabels.map((title, index) => [String(index + 1).padStart(2, '0'), title])) {
  assert(landingWorkflow.includes(`number: '${number}', title: '${title}'`),
    `Landing workflow is missing ${number} ${title}`);
}
assert(!/number:\s*'0[78]'/.test(landingWorkflow), 'Landing workflow must stop at canonical stage 06');

console.log('Pipeline cutover configuration is consistent (5 eligible / 20 inspected / 2 replacement rounds / 1 legal redesign).');
