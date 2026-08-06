export const projectFixture = {
  projectId: '10',
  name: '검증 프로젝트',
  description: '설명',
  industryCategory: '교육',
  status: 'ACTIVE',
  statusLabel: '진행 중',
  stage: 'REPORT',
  stageLabel: '보고서',
  createdAt: '2026-07-20T10:00:00',
  updatedAt: '2026-07-24T10:00:00',
  version: 3,
};

const plan = {
  planId: 2,
  projectId: 10,
  sourceDocumentVersionId: 3,
  versionNumber: 1,
  status: 'CONFIRMED',
  completionRate: 100,
  confirmedAt: '2026-07-21T10:00:00',
  provider: 'mock',
  modelName: 'mock-structure',
  promptVersion: 'structure-v1',
  sections: [{
    sectionCode: 'BUSINESS_OVERVIEW',
    status: 'PRESENT',
    extractedContent: '사업 개요 내용',
    evidence: [{ source: 'document' }],
  }],
  missingFields: [
    { fieldId: 1, label: '고객', status: 'FILLED', userValue: '초기 창업팀' },
    { fieldId: 2, label: '재무', status: 'WAIVED', reason: '후속 검증' },
  ],
};

const legalReview = {
  legalReviewId: 4,
  structuredPlanId: 2,
  sourceDocumentVersionId: 3,
  versionNumber: 1,
  status: 'COMPLETED',
  overallRiskLevel: 'HIGH',
  summary: '인허가 확인이 필요합니다.',
  disclaimer: '법률 자문이 아닙니다.',
  provider: 'mock',
  modelName: 'mock-legal',
  promptVersion: 'legal-review-v1',
  completedAt: '2026-07-22T10:00:00',
  findings: [{
    id: 1,
    category: 'LICENSE_AND_PERMIT',
    riskLevel: 'HIGH',
    finding: '허가 여부 확인',
    recommendedAction: '전문가에게 확인',
    requiresProfessionalReview: true,
    evidenceJson: '["사업계획"]',
  }],
  questions: [{ id: 1, status: 'OPEN', question: '허가 대상인가요?' }],
};

const feasibilityAssessment = {
  assessmentId: 5,
  structuredPlanId: 2,
  legalReviewId: 4,
  sourceDocumentVersionId: 3,
  versionNumber: 1,
  status: 'NEEDS_VALIDATION',
  verdict: 'CONDITIONAL',
  overallScore: null,
  confidence: 'MEDIUM',
  summary: '가정 검증이 필요합니다.',
  keyStrengthsJson: '["명확한 문제"]',
  keyRisksJson: '["수요 검증 부족"]',
  disclaimer: '투자 추천이 아닙니다.',
  provider: 'mock',
  modelName: 'mock-feasibility',
  promptVersion: 'feasibility-analysis-v1',
  completedAt: '2026-07-23T10:00:00',
  dimensions: [{
    id: 1,
    code: 'TARGET_CUSTOMER',
    status: 'NEEDS_VALIDATION',
    finding: '고객 가설 검증 필요',
    assumptionsJson: '["지불 의향"]',
    evidenceJson: '[{"type":"USER_ASSUMPTION","description":"문서 가정"}]',
  }],
  validationTasks: [{
    id: 7,
    code: 'CUSTOMER_INTERVIEW',
    dimensionCode: 'TARGET_CUSTOMER',
    title: '고객 인터뷰',
    reason: '수요 확인',
    priority: 'HIGH',
    validationMethod: 'INTERVIEW',
    expectedEvidence: '인터뷰 기록',
  }],
};

const personaRecommendation = {
  recommendationId: 6,
  status: 'NEEDS_VALIDATION',
  confidence: 'LOW',
  summary: '우선 검증 고객군',
  disclaimer: '실제 고객 조사 결과가 아닙니다.',
  provider: 'mock',
  modelName: 'mock-persona',
  promptVersion: 'persona-recommendation-v1',
  catalogVersion: 'persona-catalog-v1',
  completedAt: '2026-07-24T10:00:00',
  items: [{
    id: 1,
    fitScore: 52,
    interpretation: '가설 해석',
    matchReasonsJson: '["디지털 탐색"]',
    mismatchRisksJson: '["대표성 제한"]',
    assumptionsJson: '["가정"]',
    evidenceJson: '["패널 군집"]',
    verificationQuestionsJson: '["실제 사용하는가?"]',
    baselinePersona: { personaCode: 'P01', displayName: '디지털 탐색형' },
  }],
  hypotheses: [{ id: 1, statement: '문제 가설', rationale: '확인 필요' }],
  validationPlans: [{
    id: 8,
    personaCode: 'P01',
    objective: '실제 행동 검증',
    priority: 'HIGH',
    method: 'INTERVIEW',
    expectedEvidenceJson: '["녹취"]',
    interviewQuestionsJson: '["언제 사용하는가?"]',
    surveyQuestionsJson: '["얼마나 자주 사용하는가?"]',
  }],
  linkedFeasibilityTasks: [{
    id: 1,
    feasibilityValidationTaskId: 7,
    taskCode: 'CUSTOMER_INTERVIEW',
  }],
};

const available = (data) => ({ state: 'available', data, error: null });
const missing = () => ({ state: 'missing', data: null, error: null });

export function fullResources() {
  return {
    plan: available(structuredClone(plan)),
    documentJob: missing(),
    legalReview: available(structuredClone(legalReview)),
    legalJob: missing(),
    feasibilityAssessment: available(structuredClone(feasibilityAssessment)),
    feasibilityJob: missing(),
    personaRecommendation: available(structuredClone(personaRecommendation)),
    personaJob: missing(),
    financialAnalyses: available([{ id: 1, title: '재무 분석', status: 'COMPLETED', summaryJson: '{"headline":"완료"}' }]),
    financialAnalysis: available({
      summary: { id: 1, title: '재무 분석', status: 'COMPLETED' },
      summaryJson: '{"headline":"완료","sensitiveAssumptions":["판매량"],"keyRisks":["비용"]}',
      resultJson: '{"scenarios":[{"code":"BASE","totalRevenue":1200000,"totalOperatingProfit":400000,"breakEvenMonth":4,"paybackMonth":6,"requiredWorkingCapital":300000}]}',
    }),
  };
}

export function emptyResources() {
  return {
    plan: missing(),
    documentJob: missing(),
    legalReview: missing(),
    legalJob: missing(),
    feasibilityAssessment: missing(),
    feasibilityJob: missing(),
    personaRecommendation: missing(),
    personaJob: missing(),
    financialAnalyses: missing(),
    financialAnalysis: missing(),
  };
}

export function jobResource(status, message = '') {
  return available({ jobId: 99, status, message, progress: status === 'RUNNING' ? 50 : 0 });
}
