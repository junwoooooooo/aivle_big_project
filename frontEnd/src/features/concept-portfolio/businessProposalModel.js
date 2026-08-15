export const HYPOTHESIS_TYPES = Object.freeze([
  'TARGET_REGION', 'REVENUE_MODEL', 'PRICE', 'CHANNELS', 'DIFFERENTIATORS',
  'PRE_MARKET_SOM_SHARE', 'PRE_MARKET_SOM',
]);

export const HYPOTHESIS_LABELS = Object.freeze({
  TARGET_REGION: '목표 지역', REVENUE_MODEL: '수익 모델', PRICE: '가격', CHANNELS: '판매·제공 채널',
  DIFFERENTIATORS: '차별점', PRE_MARKET_SOM_SHARE: '시장 점유 가정', PRE_MARKET_SOM: '초기 확보 시장 규모',
});

export const CANDIDATE_FACT_FIELDS = Object.freeze({
  sellerRole: { label: '실제 판매 주체', type: 'string' },
  providerRole: { label: '실제 서비스 제공 주체', type: 'string' },
  intermediaryRole: { label: '실제 중개 주체', type: 'string' },
  transactionFlow: { label: '거래 흐름', type: 'list' },
  paymentFlow: { label: '결제·수취 흐름', type: 'list' },
  partnerRequirements: { label: '파트너·자격 요건', type: 'list' },
  personalDataUsage: { label: '개인정보 이용', type: 'list' },
  physicalActivities: { label: '실제 물리 활동', type: 'list' },
});

export function normalizePortfolioConcepts(items) {
  if (!Array.isArray(items)) return [];
  return items.filter((item) => item?.conceptId && item.selectable !== false).slice(0, 5);
}

export function toggleComparedConcept(current, conceptId) {
  if (current.includes(conceptId)) return current.filter((id) => id !== conceptId);
  if (current.length >= 3) return current;
  return [...current, conceptId];
}

export function canOpenComparison(ids) { return ids.length >= 2 && ids.length <= 3; }

export function candidateRequests(requests, candidateId) {
  return (requests ?? []).filter((request) => request.scope === 'CANDIDATE'
    && (request.status === 'OPEN' || (request.status === 'ANSWERED' && request.nextAction === 'RETRY_CONTINUATION'))
    && (!candidateId || request.candidateId === candidateId));
}

export function openCandidateRequests(requests, candidateId) {
  return candidateRequests(requests, candidateId).filter((request) => request.status === 'OPEN');
}

export function candidateFieldOptions(request) {
  const affected = Array.isArray(request?.affectedFields) ? request.affectedFields : [];
  const allowed = [...new Set(affected.filter((field) => CANDIDATE_FACT_FIELDS[field]))];
  return allowed;
}

export function candidateDefaultField(request) {
  const affected = Array.isArray(request?.affectedFields) ? request.affectedFields : [];
  const allowed = [...new Set(affected.filter((field) => CANDIDATE_FACT_FIELDS[field]))];
  return affected.length === 1 && allowed.length === 1 ? allowed[0] : '';
}

export function serializeCandidateFact(field, rawValue) {
  const contract = CANDIDATE_FACT_FIELDS[field];
  if (!contract || typeof rawValue !== 'string') return null;
  if (contract.type === 'string') {
    const value = rawValue.trim();
    return value ? { [field]: value } : null;
  }
  const values = rawValue.split(/\r?\n/).map((item) => item.trim()).filter(Boolean);
  return values.length > 0 ? { [field]: values } : null;
}

export function createCandidateDraft(request) {
  return { values: Object.fromEntries(candidateFieldOptions(request).map((field) => [field, ''])) };
}

export function serializeCandidateFacts(request, draft) {
  const fields = candidateFieldOptions(request);
  if (fields.length === 0) return null;
  const values = draft?.values ?? {};
  const entries = fields.map((field) => {
    const serialized = serializeCandidateFact(field, values[field]);
    return serialized ? [field, serialized[field]] : null;
  });
  return entries.some((entry) => entry == null) ? null : Object.fromEntries(entries);
}

export function hypothesisValueText(value) {
  if (typeof value === 'string') return value;
  if (Array.isArray(value)) return value.join(', ');
  if (value == null) return '';
  return String(value);
}

export function parseHypothesisValue(value) {
  try { return JSON.parse(value); } catch { return value; }
}

export function buildHypothesisChanges(hypotheses, edits) {
  const byType = Object.fromEntries((hypotheses ?? []).map((item) => [item.hypothesisType, item]));
  return Object.fromEntries(Object.entries(edits ?? {}).flatMap(([type, rawValue]) => {
    const hypothesis = byType[type];
    if (!hypothesis || hypothesis.locked || !HYPOTHESIS_TYPES.includes(type)) return [];
    const original = hypothesis.finalValue ?? hypothesis.proposedValue;
    const edited = typeof rawValue === 'string' ? parseHypothesisValue(rawValue) : rawValue;
    return JSON.stringify(edited) === JSON.stringify(original) ? [] : [[type, edited]];
  }));
}

export function hypothesisDisplay(type, value) {
  if (value == null) return '';
  if (type === 'PRE_MARKET_SOM_SHARE' && typeof value === 'object') {
    const share = value.targetSharePercent == null ? '미입력' : `${value.targetSharePercent}%`;
    const horizon = value.horizonYears == null ? '' : ` · ${value.horizonYears}년`;
    return `${share}${horizon}`;
  }
  if (type === 'PRE_MARKET_SOM' && typeof value === 'object') {
    const amount = Number(value.amount);
    const formatted = Number.isFinite(amount) ? new Intl.NumberFormat('ko-KR').format(amount) : '미입력';
    return `${formatted} ${value.currency ?? ''}${value.period ? ` · ${value.period}` : ''}`.trim();
  }
  return hypothesisValueText(value);
}

const displayValue = (value) => {
  if (Array.isArray(value)) return value.filter(Boolean).join(' · ');
  if (value && typeof value === 'object') return Object.values(value).filter((item) => typeof item !== 'object' && item != null).join(' · ');
  return value == null || value === '' ? '정보 없음' : String(value);
};

export function comparisonRows(concepts) {
  const definitions = [
    ['사업안 한 줄 정의', (c) => c.conceptDefinition ?? c.summary],
    ['주요 사용자', (c) => c.targetUsers], ['해결 문제', (c) => c.problemScenario],
    ['사용 상황', (c) => c.useContext ?? c.researchScope], ['핵심 가치', (c) => c.coreValue],
    ['제공 방식', (c) => c.solutionMechanism], ['핵심 기능', (c) => c.featureSet],
    ['플랫폼 역할', (c) => c.platformRole], ['서비스 제공 주체', (c) => c.providerRole],
    ['판매·중개 구조', (c) => [c.sellerRole, c.intermediaryRole].filter(Boolean)],
    ['거래 흐름', (c) => c.transactionFlow], ['결제 흐름', (c) => c.paymentFlow],
    ['수익 모델', (c) => c.revenueModel], ['가격·과금 구조', (c) => c.price],
    ['파트너 의존', (c) => c.partnerRequirements], ['운영 방식', (c) => c.operatingModel],
    ['개인정보 이용', (c) => c.personalDataUsage], ['물리 활동', (c) => c.physicalActivities],
    ['선택 전 법률·규제 요약', (_, concept) => concept.legalReview?.safeSummary],
    ['필수 통제', (_, concept) => concept.legalReview?.requiredControls],
    ['필수 파트너·자격', (_, concept) => concept.legalReview?.requiredPartnersAndQualifications],
  ];
  return definitions.map(([label, read]) => ({
    label,
    values: concepts.map((concept) => displayValue(read(concept.candidate?.candidate ?? concept.candidate ?? {}, concept))),
  })).filter((row) => row.values.some((value) => value !== '정보 없음'));
}

export function hypothesisDecisionLabel(hypothesis) {
  if (hypothesis?.locked) return '확정된 사업 조건';
  return ['ACCEPTED', 'USER_EDITED_ACCEPTED'].includes(hypothesis?.decisionStatus) ? '확인됨' : '제안값';
}

export function portfolioRunPresentation(run) {
  const status = run?.productStatus;
  if (status === 'QUEUED') return { title: '사업안 검토를 준비하고 있습니다.' };
  if (status === 'RUNNING') return { title: '사업안을 검토하고 있습니다.' };
  if (status === 'RESULTS_AVAILABLE') return { title: '검토 완료' };
  if (status === 'RESULTS_WITH_OPEN_INPUT') return {
    title: '검토 가능한 사업안이 준비되었습니다.', detail: '추가로 확인할 사업정보가 있습니다.',
  };
  if (status === 'NEEDS_INPUT') return { title: '사업안을 완성하려면 추가 사업정보가 필요합니다.' };
  if (status === 'FAILED' && run?.failureCode === 'NO_ACCEPTED_CONCEPTS') return {
    title: '현재 조건에서 검토 가능한 사업안이 없습니다.', action: '다른 방향으로 다시 탐색', restart: true,
  };
  if (status === 'FAILED') return { title: '사업안 검토를 완료하지 못했습니다.', action: '다시 시도', restart: true };
  if (status === 'STALE') return { title: '아이디어가 변경되어 사업안을 다시 검토해야 합니다.', action: '다시 검토', restart: true };
  return { title: '사업안 상태를 확인하고 있습니다.' };
}

export function selectedConceptId(selection) { return selection?.conceptId ?? null; }
