import { spawnSync } from 'node:child_process';
import { readFileSync, rmSync } from 'node:fs';
import { dirname, relative, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const projectRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const baselinePath = resolve(projectRoot, 'test-debt-baseline.json');
const reportPath = resolve(projectRoot, '.vitest-baseline-results.json');
const vitestPath = resolve(projectRoot, 'node_modules/vitest/vitest.mjs');

const baseline = JSON.parse(readFileSync(baselinePath, 'utf8'));
const expiration = new Date(`${baseline.expiresOn}T23:59:59Z`);
if (Number.isNaN(expiration.getTime()) || expiration < new Date()) {
  console.error(`Frontend test debt baseline expired on ${baseline.expiresOn}.`);
  process.exit(1);
}

const run = spawnSync(
  process.execPath,
  [vitestPath, 'run', '--reporter=json', `--outputFile=${reportPath}`],
  { cwd: projectRoot, encoding: 'utf8' },
);

if (run.stdout) process.stdout.write(run.stdout);
if (run.stderr) process.stderr.write(run.stderr);

let report;
try {
  report = JSON.parse(readFileSync(reportPath, 'utf8'));
} catch (error) {
  console.error('Vitest did not produce a readable JSON report.');
  console.error(error);
  process.exit(1);
} finally {
  rmSync(reportPath, { force: true });
}

const key = ({ file, name }) => `${file}::${name}`;
const allowed = new Set(baseline.allowedFailures.map(key));
const actualFailures = report.testResults.flatMap((suite) => {
  const file = relative(projectRoot, suite.name).replaceAll('\\', '/');
  return suite.assertionResults
    .filter((assertion) => assertion.status === 'failed')
    .map((assertion) => ({ file, name: assertion.fullName }));
});
const actual = new Set(actualFailures.map(key));
const unexpected = actualFailures.filter((failure) => !allowed.has(key(failure)));
const resolved = baseline.allowedFailures.filter((failure) => !actual.has(key(failure)));

if (unexpected.length > 0) {
  console.error('\nUnexpected frontend test failures:');
  unexpected.forEach((failure) => console.error(`- ${failure.file}: ${failure.name}`));
}
if (resolved.length > 0) {
  console.error('\nResolved failures still present in the allowlist; remove them:');
  resolved.forEach((failure) => console.error(`- ${failure.file}: ${failure.name}`));
}

if (unexpected.length > 0 || resolved.length > 0 || actual.size !== allowed.size) {
  process.exit(1);
}

console.log(
  `Frontend baseline verified: ${report.numPassedTests} passed, `
  + `${actual.size} explicitly allowed failures, 0 unexpected failures.`,
);
