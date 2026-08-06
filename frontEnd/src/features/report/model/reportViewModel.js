import { DIMENSION_LABELS, EVIDENCE_TYPE_LABELS, parseJsonList, VERDICT_LABELS } from '../../feasibility/model/feasibilityViewModel.js';
import { LEGAL_CATEGORY_LABELS, parseStringList, RISK_LABELS } from '../../legal-review/model/legalReviewViewModel.js';
import { parseJsonArray } from '../../personas/model/personaViewModel.js';
import { SECTION_LABELS } from '../../structured-plan/model/structuredPlanViewModel.js';
import { formatProjectDate } from '../../projects/model/projectViewModel.js';

const ACTIVE_JOBS = new Set(['QUEUED', 'RUNNING']);
const FAILED_JOBS = new Set(['FAILED', 'CANCELED']);

export const REPORT_STATUS = {
  NOT_STARTED: { label: '미실행', tone: 'neutral' },
  NEEDS_ACTION: { label: '조치 필요', tone: 'warning' },
  QUEUED: { label: '대기 중', tone: 'info' },
  RUNNING: { label: '진행 중', tone: 'info' },
  PARTIAL: { label: '일부 완료', tone: 'warning' },
  COMPLETED: { label: '결과 있음', tone: 'success' },
  FAILED: { label: '실패', tone: 'danger' },
  BLOCKED: { label: '선행 단계 필요', tone: 'neutral' },
};

function formatDate(value) {
  if (!value) return '기록 없음';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '시각 확인 필요';
  return new Intl.DateTimeFormat('ko-KR', {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(date);
}

function jobValue(resource) {
  return resource?.state === 'available' ? resource.data : null;
}

function sectionState(result, job, prerequisiteReady, partial = false) {
  if (result?.state === 'error') return 'FAILED';
  if (result?.state === 'available') return partial ? 'PARTIAL' : 'COMPLETED';
  if (job?.state === 'error') return 'FAILED';
  const currentJob = jobValue(job);
  if (ACTIVE_JOBS.has(currentJob?.status)) return currentJob.status;
  if (FAILED_JOBS.has(currentJob?.status)) return 'FAILED';
  return prerequisiteReady ? 'NOT_STARTED' : 'BLOCKED';
}

function withView(status, values) {
  return { ...values, status, statusView: REPORT_STATUS[status] ?? REPORT_STATUS.NOT_STARTED };
}

function planSection(resource, documentJob) {
  const plan = resource.state === 'available' ? resource.data : null;
  const status = resource.state === 'error'
    ? 'FAILED'
    : plan
      ? (plan.status === 'NEEDS_INPUT' ? 'NEEDS_ACTION' : plan.status === 'CONFIRMED' ? 'COMPLETED' : 'PARTIAL')
      : sectionState(resource, documentJob, true);
  return withView(status, {
    data: plan,
    error: resource.error,
    route: '../structure',
    title: '사업계획 구조화',
    summary: plan
      ? `서버 완성도 ${plan.completionRate}% · ${plan.sections?.length ?? 0}개 섹션`
      : '사업계획서를 등록하고 구조화 결과를 준비해 주세요.',
    sections: (plan?.sections ?? []).map((section) => ({
      ...section,
      displayName: SECTION_LABELS[section.sectionCode] ?? section.displayName ?? section.sectionCode,
      evidence: section.evidence ?? [],
    })),
    filledFields: (plan?.missingFields ?? []).filter((item) => item.status === 'FILLED'),
    waivedFields: (plan?.missingFields ?? []).filter((item) => item.status === 'WAIVED'),
    confirmedAtLabel: formatDate(plan?.confirmedAt),
  });
}

function legalSection(resource, job, plan) {
  const review = resource.state === 'available' ? resource.data : null;
  const ready = plan.data?.status === 'CONFIRMED';
  const status = sectionState(resource, job, ready, review?.status === 'NEEDS_REVIEW');
  const importantFindings = (review?.findings ?? []).filter(
    (item) => ['HIGH', 'CRITICAL'].includes(item.riskLevel) || item.requiresProfessionalReview,
  );
  return withView(status, {
    data: review,
    error: resource.error ?? job.error,
    route: '../legal-review',
    title: '법률·규제 사전검토',
    summary: review?.summary ?? (ready ? '법률·규제 사전검토를 시작할 수 있습니다.' : '확정된 사업계획이 필요합니다.'),
    riskLabel: review ? (RISK_LABELS[review.overallRiskLevel] ?? '확인 필요') : null,
    importantFindings: importantFindings.map((item) => ({
      ...item,
      categoryLabel: LEGAL_CATEGORY_LABELS[item.category] ?? item.title,
      evidence: parseStringList(item.evidenceJson),
    })),
    questions: (review?.questions ?? []).filter((item) => item.status === 'OPEN'),
  });
}

function feasibilitySection(resource, job, legal, plan) {
  const assessment = resource.state === 'available' ? resource.data : null;
  const ready = Boolean(legal.data) && plan.data?.status === 'CONFIRMED';
  const status = sectionState(
    resource,
    job,
    ready,
    assessment?.status === 'NEEDS_VALIDATION',
  );
  return withView(status, {
    data: assessment,
    error: resource.error ?? job.error,
    route: '../feasibility',
    title: '사업 타당성',
    summary: assessment?.summary ?? (ready ? '사업 타당성 분석을 시작할 수 있습니다.' : '법률 사전검토 결과가 필요합니다.'),
    verdictLabel: assessment ? (VERDICT_LABELS[assessment.verdict] ?? assessment.verdict) : null,
    strengths: parseJsonList(assessment?.keyStrengthsJson),
    risks: parseJsonList(assessment?.keyRisksJson),
    dimensions: (assessment?.dimensions ?? []).map((item) => ({
      ...item,
      label: DIMENSION_LABELS[item.code] ?? item.code,
      strengths: parseJsonList(item.strengthsJson),
      risks: parseJsonList(item.risksJson),
      assumptions: parseJsonList(item.assumptionsJson),
      evidence: parseJsonList(item.evidenceJson).map((evidence) => ({
        ...evidence,
        typeLabel: EVIDENCE_TYPE_LABELS[evidence.type] ?? evidence.type,
      })),
    })),
  });
}

function personaSection(resource, job, feasibility) {
  const recommendation = resource.state === 'available' ? resource.data : null;
  const ready = feasibility.data?.status === 'COMPLETED'
    || feasibility.data?.status === 'NEEDS_VALIDATION';
  const status = sectionState(
    resource,
    job,
    ready,
    recommendation?.status === 'NEEDS_VALIDATION',
  );
  return withView(status, {
    data: recommendation,
    error: resource.error ?? job.error,
    route: '../personas',
    title: '페르소나·고객 검증 계획',
    summary: recommendation?.summary ?? (ready ? '페르소나 추천을 시작할 수 있습니다.' : '사업 타당성 결과가 필요합니다.'),
    items: (recommendation?.items ?? []).map((item) => ({
      ...item,
      matchReasons: parseJsonArray(item.matchReasonsJson),
      mismatchRisks: parseJsonArray(item.mismatchRisksJson),
      assumptions: parseJsonArray(item.assumptionsJson),
      evidence: parseJsonArray(item.evidenceJson),
      verificationQuestions: parseJsonArray(item.verificationQuestionsJson),
    })),
    hypotheses: recommendation?.hypotheses ?? [],
    validationPlans: (recommendation?.validationPlans ?? []).map((plan) => ({
      ...plan,
      expectedEvidence: parseJsonArray(plan.expectedEvidenceJson),
      interviewQuestions: parseJsonArray(plan.interviewQuestionsJson),
      surveyQuestions: parseJsonArray(plan.surveyQuestionsJson),
    })),
  });
}

function financialSection(listResource, detailResource, feasibility) {
  const items = listResource?.state === 'available' ? listResource.data : [];
  const summary = items.find((item) => item.status === 'COMPLETED') ?? null;
  const latest = detailResource?.state === 'available' ? detailResource.data : null;
  const status = latest ? 'COMPLETED' : feasibility.data ? 'NOT_STARTED' : 'BLOCKED';
  return withView(status, {
    data: latest,
    error: detailResource?.error ?? listResource?.error,
    route: '../review/financial',
    title: '재무·수익성 분석',
    summary: latest ? '완료된 재무 분석의 기준 시나리오와 손익분기 정보를 보고서에 반영합니다.' : '재무 분석 미완료',
    listSummary: summary,
  });
}

function validationTasks(feasibility, persona) {
  const tasks = [];
  const seen = new Set();
  for (const task of feasibility.data?.validationTasks ?? []) {
    const key = `feasibility:${task.id}:${task.code}`;
    if (seen.has(key)) continue;
    seen.add(key);
    tasks.push({
      key,
      source: '사업 타당성',
      title: task.title,
      priority: task.priority,
      reason: task.reason,
      method: task.validationMethod,
      expectedEvidence: task.expectedEvidence,
      dimension: DIMENSION_LABELS[task.dimensionCode] ?? task.dimensionCode,
    });
  }
  for (const plan of persona.validationPlans ?? []) {
    const key = `persona-plan:${plan.id}`;
    if (seen.has(key)) continue;
    seen.add(key);
    tasks.push({
      key,
      source: '페르소나',
      title: plan.objective,
      priority: plan.priority,
      reason: '실제 고객에게 확인해야 하는 검증 계획입니다.',
      method: plan.method,
      expectedEvidence: plan.expectedEvidence.join(', ') || '팀이 성공 기준과 증거를 확정해야 합니다.',
      personaCode: plan.personaCode,
    });
  }
  return tasks;
}

function nextAction(plan, legal, feasibility, persona) {
  if (!plan.data) {
    return { title: '사업계획서 등록', description: 'DOCX를 등록해 구조화를 시작하세요.', route: '../documents' };
  }
  if (plan.data.status === 'NEEDS_INPUT') {
    return { title: '누락 항목 보완', description: '필수 보완 항목을 해결하세요.', route: '../structure' };
  }
  if (plan.data.status === 'DRAFT') {
    return { title: '사업계획 확정', description: '구조화 결과를 검토하고 확정하세요.', route: '../structure' };
  }
  if (!legal.data) {
    return { title: ACTIVE_JOBS.has(jobValue(legal.job)?.status) ? '법률 검토 진행 확인' : '법률 사전검토 시작', description: legal.summary, route: '../legal-review' };
  }
  if (!feasibility.data) {
    return { title: '사업 타당성 분석', description: feasibility.summary, route: '../feasibility' };
  }
  if (!persona.data) {
    return { title: '페르소나 추천', description: persona.summary, route: '../personas' };
  }
  return { title: '통합 보고서 확인', description: '현재까지 생성된 모든 분석 결과와 검증 과제를 검토하세요.', route: '../report' };
}

function providers(sections) {
  return sections
    .filter((section) => section.data?.provider)
    .map((section) => ({
      section: section.title,
      provider: section.data.provider,
      model: section.data.modelName ?? '기록 없음',
      promptVersion: section.data.promptVersion ?? '기록 없음',
      isMock: String(section.data.provider).toLowerCase() === 'mock',
      completedAt: formatDate(section.data.completedAt ?? section.data.createdAt),
    }));
}

export function toIntegratedReportViewModel(project, resources) {
  const plan = planSection(resources.plan, resources.documentJob);
  const legal = legalSection(resources.legalReview, resources.legalJob, plan);
  legal.job = resources.legalJob;
  const feasibility = feasibilitySection(
    resources.feasibilityAssessment,
    resources.feasibilityJob,
    legal,
    plan,
  );
  const persona = personaSection(resources.personaRecommendation, resources.personaJob, feasibility);
  const financial = financialSection(resources.financialAnalyses, resources.financialAnalysis, feasibility);
  const sections = [plan, legal, feasibility, financial, persona];
  const completed = sections.filter((section) => ['COMPLETED', 'PARTIAL'].includes(section.status)).length;
  const failed = sections.filter((section) => section.status === 'FAILED').length;
  const allResults = sections.every((section) => Boolean(section.data));
  const allCompleted = allResults
    && sections.every((section) => section.status === 'COMPLETED');
  const anyMock = providers(sections).some((provider) => provider.isMock);

  return {
    project,
    generatedAt: new Date().toISOString(),
    generatedAtLabel: formatDate(new Date().toISOString()),
    projectUpdatedAtLabel: formatProjectDate(project.updatedAt),
    reportStatus: allCompleted ? 'COMPLETED' : completed > 0 ? 'PARTIAL' : 'NOT_STARTED',
    reportStatusLabel: allCompleted
      ? '현재 분석 결과 통합 완료'
      : completed > 0
        ? '현재까지의 분석 결과 · 일부 검증 미완료'
        : '분석 준비 중',
    completedCount: completed,
    failedCount: failed,
    sections,
    plan,
    legal,
    feasibility,
    persona,
    financial,
    validationTasks: validationTasks(feasibility, persona),
    nextAction: nextAction(plan, legal, feasibility, persona),
    provenance: providers(sections),
    anyMock,
    sourceDocumentVersionId: plan.data?.sourceDocumentVersionId ?? null,
    structuredPlanVersion: plan.data?.versionNumber ?? null,
    limitations: [
      '이 보고서는 저장된 스냅샷이 아니라 현재 API 결과를 조회해 구성한 화면입니다.',
      'AI 결과는 사전 검토와 검증 계획이며 법률 자문, 투자 추천, 실제 고객 응답이 아닙니다.',
      ...(anyMock ? ['Mock provider 결과가 포함되어 실제 외부 AI 호출 결과가 아닙니다.'] : []),
    ],
  };
}
