export const CANONICAL_SECTION_ORDER = [
  'BUSINESS_OVERVIEW',
  'MARKET_SIZE',
  'TARGET_CUSTOMER',
  'COMPETITIVE_ANALYSIS',
  'PRODUCT_SERVICE',
  'BUSINESS_MODEL',
  'COST_PROFITABILITY',
  'SALES_GOALS_FINANCIAL_PROJECTIONS',
  'TECHNOLOGY_PRODUCTION',
  'LEGAL_PERMITS',
  'SCHEDULE_RISK',
  'EVIDENCE_LIST',
];

export const SECTION_LABELS = {
  BUSINESS_OVERVIEW: '사업 개요',
  MARKET_SIZE: '시장 규모',
  TARGET_CUSTOMER: '목표 고객',
  COMPETITIVE_ANALYSIS: '경쟁 분석',
  PRODUCT_SERVICE: '제품·서비스',
  BUSINESS_MODEL: '비즈니스 모델',
  COST_PROFITABILITY: '원가·수익성',
  SALES_GOALS_FINANCIAL_PROJECTIONS: '판매 목표·재무 추정',
  TECHNOLOGY_PRODUCTION: '기술·생산',
  LEGAL_PERMITS: '법률·인허가',
  SCHEDULE_RISK: '일정·리스크',
  EVIDENCE_LIST: '근거 자료 목록',
};

export const STATUS_VIEW = {
  PRESENT: { label: '충족', shortLabel: 'PASS', group: 'complete' },
  MISSING: { label: '누락·보완 필요', shortLabel: '보완 필요', group: 'needs-input' },
  PARTIAL: { label: '일부 확인 필요', shortLabel: '보완 필요', group: 'needs-input' },
  INVALID: { label: '분석 오류·재검증 필요', shortLabel: '재검증', group: 'review' },
  UNKNOWN: { label: '확인 필요', shortLabel: '확인 필요', group: 'review' },
};

export const MISSING_FIELD_STATUS_VIEW = {
  OPEN: { label: '보완 필요', tone: 'warning', isResolved: false },
  FILLED: { label: '입력 완료', tone: 'success', isResolved: true },
  WAIVED: { label: '이번 단계에서 제외', tone: 'neutral', isResolved: true },
};

export const MISSING_FIELD_PRIORITY_VIEW = {
  HIGH: { label: '우선 보완', rank: 0 },
  MEDIUM: { label: '보완 권장', rank: 1 },
  LOW: { label: '선택 확인', rank: 2 },
};

function canonicalSectionRank(sectionCode) {
  const index = CANONICAL_SECTION_ORDER.indexOf(sectionCode);
  return index < 0 ? CANONICAL_SECTION_ORDER.length : index;
}

export function toMissingFieldViewModel(field, planStatus) {
  const statusView = MISSING_FIELD_STATUS_VIEW[field.status]
    ?? MISSING_FIELD_STATUS_VIEW.OPEN;
  const priorityView = MISSING_FIELD_PRIORITY_VIEW[field.priority]
    ?? MISSING_FIELD_PRIORITY_VIEW.LOW;
  return {
    ...field,
    sectionDisplayName: SECTION_LABELS[field.sectionCode] ?? '확인 필요 항목',
    lockVersion: field.version,
    statusView,
    priorityView,
    isResolved: statusView.isResolved,
    isEditable: planStatus !== 'CONFIRMED' && field.version != null,
  };
}

export function toStructuredPlanViewModel(plan) {
  if (!plan) return null;
  const sections = [...plan.sections]
    .sort((a, b) => {
      const left = CANONICAL_SECTION_ORDER.indexOf(a.sectionCode);
      const right = CANONICAL_SECTION_ORDER.indexOf(b.sectionCode);
      return (left < 0 ? 99 : left) - (right < 0 ? 99 : right);
    })
    .map((section, index) => ({
      ...section,
      sequence: index + 1,
      displayName: SECTION_LABELS[section.sectionCode]
        ?? section.displayName
        ?? '확인 필요 항목',
      statusView: STATUS_VIEW[section.status] ?? STATUS_VIEW.UNKNOWN,
      evidence: section.evidence ?? [],
      sourceBlockReferences: section.sourceBlockReferences ?? [],
    }));
  const missingFields = (plan.missingFields ?? [])
    .map((field) => toMissingFieldViewModel(field, plan.status))
    .sort((left, right) => {
      const openRank = Number(left.status !== 'OPEN') - Number(right.status !== 'OPEN');
      if (openRank !== 0) return openRank;
      const priorityRank = left.priorityView.rank - right.priorityView.rank;
      if (priorityRank !== 0) return priorityRank;
      const sectionRank = canonicalSectionRank(left.sectionCode)
        - canonicalSectionRank(right.sectionCode);
      if (sectionRank !== 0) return sectionRank;
      return String(left.fieldCode).localeCompare(String(right.fieldCode));
    });
  const openRequiredCount = missingFields.filter(
    (field) => field.required && field.status === 'OPEN',
  ).length;
  return {
    ...plan,
    sections,
    lockVersion: plan.version,
    missingFields,
    openRequiredCount,
    resolvedMissingFieldCount: missingFields.filter((field) => field.isResolved).length,
    isMock: String(plan.provider ?? '').toLowerCase() === 'mock',
  };
}
