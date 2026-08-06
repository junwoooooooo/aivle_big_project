import { createDocumentApi } from '../../documents/api/documentApi.js';
import { createFeasibilityApi } from '../../feasibility/api/feasibilityApi.js';
import { createLegalReviewApi } from '../../legal-review/api/legalReviewApi.js';
import { createPersonaApi } from '../../personas/api/personaApi.js';
import { createStructuredPlanApi } from '../../structured-plan/api/structuredPlanApi.js';
import { createFinancialApi } from '../../financial/api/financialApi.js';

function normalize(result) {
  if (result.status === 'fulfilled') {
    return { state: 'available', data: result.value, error: null };
  }
  if (result.reason?.status === 404) {
    return { state: 'missing', data: null, error: null };
  }
  return { state: 'error', data: null, error: result.reason };
}

export function createReportApi(client) {
  const documents = createDocumentApi(client);
  const plans = createStructuredPlanApi(client);
  const legal = createLegalReviewApi(client);
  const feasibility = createFeasibilityApi(client);
  const personas = createPersonaApi(client);
  const financial = createFinancialApi(client);

  return {
    async load(projectId, options) {
      const settled = await Promise.allSettled([
        plans.getLatest(projectId, options),
        documents.getLatestJob(projectId, options),
        legal.latest(projectId, options),
        legal.latestJob(projectId, options),
        feasibility.latest(projectId, options),
        feasibility.latestJob(projectId, options),
        personas.latest(projectId, options),
        personas.latestJob(projectId, options),
        financial.list(projectId, options),
      ]);
      const [
        plan,
        documentJob,
        legalReview,
        legalJob,
        feasibilityAssessment,
        feasibilityJob,
        personaRecommendation,
        personaJob,
        financialAnalyses,
      ] = settled.map(normalize);
      let financialAnalysis = { state: 'missing', data: null, error: null };
      if (financialAnalyses.state === 'available' && Array.isArray(financialAnalyses.data)) {
        const latestCompleted = financialAnalyses.data.find((item) => item.status === 'COMPLETED');
        if (latestCompleted) {
          financialAnalysis = normalize(await Promise.resolve(
            financial.detail(projectId, latestCompleted.id, options),
          ).then(
            (value) => ({ status: 'fulfilled', value }),
            (reason) => ({ status: 'rejected', reason }),
          ));
        }
      }
      return {
        plan,
        documentJob,
        legalReview,
        legalJob,
        feasibilityAssessment,
        feasibilityJob,
        personaRecommendation,
        personaJob,
        financialAnalyses,
        financialAnalysis,
      };
    },
  };
}
