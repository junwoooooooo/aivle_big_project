import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

const root = resolve(import.meta.dirname, '..');
const read = (path) => readFileSync(resolve(root, path), 'utf8');
const assert = (condition, message) => {
  if (!condition) throw new Error(message);
};

const limits = read('backend/src/main/java/com/aivle/backend/pipeline/concept/domain/ConceptFactoryLimits.java');
assert(/SLOT_COUNT\s*=\s*5/.test(limits), 'Concept target must be 5');
assert(/MAX_INSPECTED_CANDIDATES\s*=\s*15/.test(limits), 'Inspected candidate limit must be 15');
assert(/MAX_REPLACEMENT_ROUNDS\s*=\s*2/.test(limits), 'Replacement round limit must be 2');

const env = read('.env.example');
const compose = read('compose.yaml');
const envKeys = new Set([...env.matchAll(/^([A-Z][A-Z0-9_]*)=/gm)].map((match) => match[1]));
for (const key of ['AI_CONCEPT_GENERATION_CONCURRENCY', 'AI_PROVIDER', 'AI_MODEL', 'AI_INTERNAL_SERVICE_TOKEN']) {
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
  'ProjectStage',
  'R2A',
  '개발용 fixture',
  '후속 단계에서',
  '다음 구현 단계',
]) {
  assert(!activeFrontend.includes(forbidden), `Active frontend still contains: ${forbidden}`);
}

console.log('Pipeline cutover configuration is consistent (5 eligible / 15 inspected / 2 replacement rounds).');
