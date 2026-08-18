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
  sellerRole: { label: '실제로 상품·서비스를 판매하는 주체', type: 'string', question: '누가 고객에게 상품이나 서비스를 판매하나요?', help: '사업자나 매장처럼 실제 계약의 판매자 역할을 적어 주세요.' },
  providerRole: { label: '실제로 서비스를 제공하는 주체', type: 'string', question: '누가 고객에게 실제 서비스를 제공하나요?', help: '직접 제공하는 조직이나 협력 업체의 역할을 적어 주세요.' },
  intermediaryRole: { label: '거래를 연결·중개하는 주체', type: 'string', question: '거래를 연결하거나 중개하는 주체가 있나요?', help: '플랫폼 등 거래 당사자를 연결하는 역할이 있다면 적어 주세요.' },
  transactionFlow: { label: '고객이 신청·구매하고 서비스가 제공되는 과정', type: 'list', question: '고객의 신청부터 서비스 제공까지 어떤 순서로 진행되나요?', help: '주요 단계를 한 줄에 하나씩 적어 주세요.' },
  paymentFlow: { label: '결제와 정산이 이루어지는 흐름', type: 'list', question: '고객 결제와 판매자·파트너 정산은 어떻게 이루어지나요?', help: '돈이 이동하는 주요 단계를 한 줄에 하나씩 적어 주세요.' },
  partnerRequirements: { label: '필요한 외부 파트너·자격', type: 'list', question: '이 사업을 운영하려면 어떤 외부 파트너나 자격이 필요한가요?', help: '회사명이 정해지지 않았다면 역할이나 자격 유형만 적어도 됩니다.' },
  personalDataUsage: { label: '수집·이용할 개인정보', type: 'list', question: '서비스에서 어떤 개인정보를 수집하거나 이용하나요?', help: '이름, 연락처, 위치처럼 실제로 필요한 정보만 한 줄에 하나씩 적어 주세요.' },
  physicalActivities: { label: '오프라인에서 실제로 이루어지는 활동', type: 'list', question: '현장 방문·배송·시술처럼 오프라인에서 이루어지는 활동이 있나요?', help: '사람이나 물품이 실제로 움직이는 활동을 한 줄에 하나씩 적어 주세요.' },
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

const CURRENCY_LABELS = Object.freeze({ KRW: '원', USD: '달러', JPY: '엔', EUR: '유로' });

function formatKoreanNumberGroup(value) {
  const number = Math.trunc(value);
  if (number === 0) return '';
  const units = [[1000, '천'], [100, '백'], [10, '십']];
  let remainder = number;
  let output = '';
  for (const [unit, label] of units) {
    const digit = Math.floor(remainder / unit);
    if (digit > 0) output += `${digit}${label}`;
    remainder %= unit;
  }
  if (remainder > 0) output += String(remainder);
  return output;
}

export function formatKoreanCurrencyAmount(amount, currency = '') {
  const number = Number(amount);
  if (!Number.isFinite(number)) return '';
  const sign = number < 0 ? '-' : '';
  let remainder = Math.floor(Math.abs(number));
  const groups = [[1_000_000_000_000, '조'], [100_000_000, '억'], [10_000, '만']];
  const parts = [];
  for (const [unit, label] of groups) {
    const group = Math.floor(remainder / unit);
    if (group > 0) parts.push(`${group < 100 ? group : formatKoreanNumberGroup(group)}${label}`);
    remainder %= unit;
  }
  if (remainder > 0 || parts.length === 0) parts.push(formatKoreanNumberGroup(remainder) || '0');
  return `${sign}${parts.join(' ')} ${CURRENCY_LABELS[currency] ?? currency}`.trim();
}

const LEGAL_STATUS_LABELS = Object.freeze({
  IMPLEMENTABLE: '현재 조건으로 진행 가능',
  IMPLEMENTABLE_WITH_CONTROLS: '필요한 조치를 반영하면 진행 가능',
  NEEDS_FACTS: '추가 정보 확인 필요',
  REDESIGNABLE: '일부 구조 조정 후 진행 가능',
  REJECTED: '현재 형태로 진행하기 어려움',
  CONDITIONAL: '필요한 조치를 반영하면 진행 가능',
  ACCEPT: '검토 결과 확인 완료',
});

export function legalStatusLabel(status) {
  return LEGAL_STATUS_LABELS[status] ?? '검토 결과 확인 필요';
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
  const source = String(hypothesis?.source ?? '').toUpperCase();
  if (hypothesis?.decisionStatus === 'USER_EDITED_ACCEPTED' || source.includes('EDIT')) return '사용자 수정';
  if (source.includes('USER')) return '사용자 입력';
  return 'AI 제안';
}

export function hypothesisHasValue(value) {
  if (value == null) return false;
  if (typeof value === 'string') return value.trim().length > 0;
  if (Array.isArray(value)) return value.some(hypothesisHasValue);
  if (typeof value === 'object') return Object.values(value).some(hypothesisHasValue);
  return true;
}

export function hypothesisInputCount(hypotheses = [], edits = {}) {
  const byType = Object.fromEntries(hypotheses.map((item) => [item.hypothesisType, item]));
  return HYPOTHESIS_TYPES.filter((type) => {
    const hypothesis = byType[type];
    return hypothesisHasValue(edits[type] ?? hypothesis?.finalValue ?? hypothesis?.proposedValue);
  }).length;
}

export function hypothesisPresentation(hypothesis, edit) {
  const currentValue = edit ?? hypothesis?.finalValue ?? hypothesis?.proposedValue;
  const hasCurrentValue = edit !== undefined
    ? hypothesisHasValue(edit)
    : hypothesis?.hasCurrentValue ?? hypothesisHasValue(currentValue);
  const locallyEdited = edit !== undefined;
  const semanticBlocked = !locallyEdited && hypothesis?.semanticStatus
    && hypothesis.semanticStatus !== 'VALID';
  const legalBlocked = !locallyEdited && hypothesis?.legalReviewStatus === 'FAILED';
  const confirmable = hasCurrentValue && !semanticBlocked && !legalBlocked
    && (locallyEdited || hypothesis?.confirmable !== false);
  const blockingReason = !hasCurrentValue ? '현재 값이 비어 있습니다.'
    : confirmable ? null
      : hypothesis?.blockingReason ?? hypothesis?.semanticReason
        ?? '현재 값은 입력되어 있으나 확정하려면 값을 조금 더 구체화해 주세요.';
  return { currentValue, hasCurrentValue, confirmable, blockingReason };
}

export function hypothesisNeedsConfirmation(hypothesis) {
  return Boolean(hypothesis) && !['ACCEPTED', 'USER_EDITED_ACCEPTED'].includes(hypothesis.decisionStatus);
}

export function hypothesisConfirmationMessage(hypothesis) {
  if (!hypothesisNeedsConfirmation(hypothesis)) return '';
  if (hypothesis.legalReviewStatus === 'FAILED') return '법률 검토를 통과하지 못했습니다. 값을 수정하거나 다른 제안을 선택해 주세요.';
  if (hypothesis.semanticStatus !== 'VALID') return '현재 AI 제안은 그대로 확정할 수 없습니다. 값을 확인해 수정하거나 다른 제안을 선택해 주세요.';
  return '현재 AI 제안을 기준값으로 일괄 확정할 수 있습니다.';
}

export function businessDecisionStage(selection) {
  if (!selection) return 'PROPOSAL_SELECTION';
  if (['HYPOTHESES_PREPARING', 'PENDING_HYPOTHESIS_CONFIRMATION', 'DELTA_LEGAL_PENDING',
    'DELTA_LEGAL_FAILED', 'READY_FOR_LEGAL_REPORT'].includes(selection.status)) return 'BUSINESS_BASIS';
  if (['MARKET_SEED_FINALIZING', 'READY_FOR_MARKET'].includes(selection.status)) return 'VALIDATION_PREP';
  return 'LEGAL_REVIEW';
}

export function businessDecisionReachability({ concepts = [], selection, report, validationPrepReached = false } = {}) {
  const stage = businessDecisionStage(selection);
  return {
    PROPOSAL_SELECTION: concepts.length > 0,
    BUSINESS_BASIS: Boolean(selection),
    LEGAL_REVIEW: Boolean(report) || ['LEGAL_REVIEW', 'VALIDATION_PREP'].includes(stage),
    VALIDATION_PREP: validationPrepReached || ['MARKET_SEED_FINALIZING', 'READY_FOR_MARKET'].includes(selection?.status),
  };
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
