export const HYPOTHESIS_TYPES = Object.freeze([
  'TARGET_REGION', 'REVENUE_MODEL', 'PRICE', 'CHANNELS', 'DIFFERENTIATORS',
  'PRE_MARKET_SOM_SHARE', 'PRE_MARKET_SOM',
]);

export const BUSINESS_BASIS_TYPES = Object.freeze(HYPOTHESIS_TYPES.slice(0, 5));
export const MARKET_TARGET_TYPES = Object.freeze(HYPOTHESIS_TYPES.slice(5));

export const HYPOTHESIS_LABELS = Object.freeze({
  TARGET_REGION: '사업 대상 지역',
  REVENUE_MODEL: '수익을 만드는 방식',
  PRICE: '가격·과금 방식',
  CHANNELS: '고객에게 제공하는 경로',
  DIFFERENTIATORS: '핵심 차별점',
  PRE_MARKET_SOM_SHARE: '목표 시장 점유율',
  PRE_MARKET_SOM: '초기 목표 시장 규모',
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

const candidateOf = (concept) => concept?.candidate?.candidate ?? concept?.candidate ?? {};
const normalizeComparable = (value) => displayValue(value).replace(/\s+/g, ' ').trim();

export function normalizePortfolioConcepts(items) {
  if (!Array.isArray(items)) return [];
  return items.filter((item) => item?.conceptId && item.selectable !== false).slice(0, 5);
}

export function toggleComparedConcept(current, conceptId) {
  if (current.includes(conceptId)) return current.filter((id) => id !== conceptId);
  if (current.length >= 2) return current;
  return [...current, conceptId];
}

export function canOpenComparison(ids) { return ids.length === 2; }

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
  return [...new Set(affected.filter((field) => CANDIDATE_FACT_FIELDS[field]))];
}

export function candidateDefaultField(request) {
  const allowed = candidateFieldOptions(request);
  const affected = Array.isArray(request?.affectedFields) ? request.affectedFields : [];
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

export function displayValue(value) {
  if (Array.isArray(value)) return value.filter(Boolean).join(' · ');
  if (value && typeof value === 'object') return Object.values(value).filter((item) => typeof item !== 'object' && item != null).join(' · ');
  return value == null || value === '' ? '정보 없음' : String(value);
}

const PREVIEW_FIELDS = Object.freeze([
  ['targetUsers', '주요 고객'],
  ['coreValue', '핵심 가치'],
  ['solutionMechanism', '제공 방식'],
  ['revenueModel', '수익 방식'],
  ['price', '가격·과금'],
  ['operatingModel', '운영 방식'],
  ['featureSet', '핵심 기능'],
  ['partnerRequirements', '파트너 조건'],
]);

export function buildProposalPreview(concept, allConcepts = []) {
  const candidate = candidateOf(concept);
  const candidates = allConcepts.map(candidateOf);
  const different = PREVIEW_FIELDS.filter(([key]) => {
    if (candidate[key] == null || displayValue(candidate[key]) === '정보 없음') return false;
    return new Set(candidates.map((item) => normalizeComparable(item[key])).filter((value) => value !== '정보 없음')).size > 1;
  });
  const chosen = [...different];
  for (const field of PREVIEW_FIELDS) {
    if (chosen.length >= 4) break;
    if (!chosen.some(([key]) => key === field[0]) && displayValue(candidate[field[0]]) !== '정보 없음') chosen.push(field);
  }
  return {
    definition: displayValue(candidate.conceptDefinition ?? concept?.summary),
    highlights: chosen.slice(0, 3).map(([key, label]) => ({ key, label, value: displayValue(candidate[key]) })),
  };
}

const BASIC_COMPARISON = Object.freeze([
  ['주요 고객', 'targetUsers'],
  ['해결하려는 문제', 'problemScenario'],
  ['핵심 가치', 'coreValue'],
  ['제공 방식', 'solutionMechanism'],
  ['수익 방식', 'revenueModel'],
  ['운영상 차이', 'operatingModel'],
]);

export const DETAILED_COMPARISON_GROUPS = Object.freeze([
  { title: '서비스 구성', fields: [['핵심 기능', 'featureSet'], ['사용 상황', 'useContext']] },
  { title: '사업 운영', fields: [['플랫폼 역할', 'platformRole'], ['제공 주체', 'providerRole'], ['판매 주체', 'sellerRole'], ['중개 주체', 'intermediaryRole'], ['운영 방식', 'operatingModel']] },
  { title: '거래와 수익', fields: [['거래 흐름', 'transactionFlow'], ['결제 흐름', 'paymentFlow'], ['수익 모델', 'revenueModel'], ['가격', 'price']] },
  { title: '운영 조건', fields: [['파트너', 'partnerRequirements'], ['개인정보', 'personalDataUsage'], ['물리 활동', 'physicalActivities']] },
]);

const rowsFrom = (concepts, definitions) => definitions.map(([label, key]) => ({
  label,
  values: concepts.map((concept) => displayValue(candidateOf(concept)[key])),
})).filter((row) => row.values.some((value) => value !== '정보 없음'));

export function comparisonRows(concepts) { return rowsFrom(concepts, BASIC_COMPARISON); }
export function detailedComparisonGroups(concepts) {
  return DETAILED_COMPARISON_GROUPS.map((group) => ({ ...group, rows: rowsFrom(concepts, group.fields) }))
    .filter((group) => group.rows.length > 0);
}

export function hypothesisDecisionLabel(hypothesis) {
  if (hypothesis?.locked) return '확정된 값';
  return ['ACCEPTED', 'USER_EDITED_ACCEPTED'].includes(hypothesis?.decisionStatus) ? '확인 완료' : 'AI가 제안한 값';
}

export function businessDecisionStage(selection) {
  if (!selection) return 'PROPOSAL_SELECTION';
  if (['HYPOTHESES_PREPARING', 'PENDING_HYPOTHESIS_CONFIRMATION'].includes(selection.status)) return 'BUSINESS_BASIS';
  if (selection.status === 'READY_FOR_MARKET') return 'MARKET_READY';
  return 'LEGAL_REVIEW';
}

export function canChangeSelection(selection) {
  return Boolean(selection) && selection.status !== 'STALE';
}

const normalizeLawName = (value) => {
  const name = String(value ?? '').trim().replace(/^["'“”‘’]+|["'“”‘’]+$/g, '').trim();
  return name || '기타 법률 근거';
};

export function groupLegalEvidence(evidence) {
  const groups = new Map();
  for (const item of Array.isArray(evidence) ? evidence : []) {
    const lawName = normalizeLawName(item?.lawName ?? item?.title);
    if (!groups.has(lawName)) groups.set(lawName, { lawName, articles: [], keys: new Set() });
    const group = groups.get(lawName);
    const dedupeKey = [lawName, item?.articleReference, item?.officialSourceUri, item?.contentHash, item?.title, item?.boundedProvisionSummary].join('|');
    if (!group.keys.has(dedupeKey)) {
      group.keys.add(dedupeKey);
      group.articles.push({ ...item, lawName });
    }
  }
  return [...groups.values()].map((group) => ({ lawName: group.lawName, articles: group.articles }));
}

export function portfolioRunPresentation(run) {
  const status = run?.productStatus;
  if (status === 'QUEUED') return { title: '사업안 검토를 준비하고 있습니다.' };
  if (status === 'RUNNING') return { title: '사업안을 검토하고 있습니다.' };
  if (status === 'RESULTS_AVAILABLE') return { title: '검토 완료' };
  if (status === 'RESULTS_WITH_OPEN_INPUT') return { title: '검토 가능한 사업안이 준비되었습니다.', detail: '추가로 확인할 사업정보가 있습니다.' };
  if (status === 'NEEDS_INPUT') return { title: '사업안을 완성하려면 추가 사업정보가 필요합니다.' };
  if (status === 'FAILED' && run?.failureCode === 'NO_ACCEPTED_CONCEPTS') return { title: '현재 조건에서 검토 가능한 사업안이 없습니다.', action: '다른 방향으로 다시 탐색', restart: true };
  if (status === 'FAILED') return { title: '사업안 검토를 완료하지 못했습니다.', action: '다시 시도', restart: true };
  if (status === 'STALE') return { title: '아이디어가 변경되어 사업안을 다시 검토해야 합니다.', action: '다시 검토', restart: true };
  return { title: '사업안의 상태를 확인하고 있습니다.' };
}

export function selectedConceptId(selection) { return selection?.conceptId ?? null; }
