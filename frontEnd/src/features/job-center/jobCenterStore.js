const STORAGE_KEY = 'new-pipeline-active-jobs';
export const JOB_CENTER_CHANGED = 'job-center-changed';

export function registerConceptJob({ projectId, runId, jobId, status, updatedAt }) {
  if (!projectId || !runId || !jobId) return;
  const current = readJobs().filter((job) => job.jobId !== jobId);
  current.unshift({ projectId: String(projectId), runId, jobId, status, updatedAt, type: 'CONCEPT_FACTORY' });
  globalThis.localStorage?.setItem(STORAGE_KEY, JSON.stringify(current.slice(0, 20)));
  globalThis.dispatchEvent?.(new CustomEvent(JOB_CENTER_CHANGED));
}

export function readJobs() {
  try {
    const values = JSON.parse(globalThis.localStorage?.getItem(STORAGE_KEY) ?? '[]');
    return Array.isArray(values) ? values : [];
  } catch {
    return [];
  }
}
