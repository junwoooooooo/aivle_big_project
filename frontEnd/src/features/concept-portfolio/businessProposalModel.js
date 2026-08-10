export const HYPOTHESIS_TYPES = Object.freeze([
  'TARGET_REGION', 'REVENUE_MODEL', 'PRICE', 'CHANNELS', 'DIFFERENTIATORS',
  'PRE_MARKET_SOM_SHARE', 'PRE_MARKET_SOM',
]);

export const HYPOTHESIS_LABELS = Object.freeze({
  TARGET_REGION: '목표 지역', REVENUE_MODEL: '수익 모델', PRICE: '가격', CHANNELS: '판매·제공 채널',
  DIFFERENTIATORS: '차별점', PRE_MARKET_SOM_SHARE: '시장 점유 가정', PRE_MARKET_SOM: '초기 확보 시장 규모',
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

export function canOpenComparison(ids) {
  return ids.length >= 2 && ids.length <= 3;
}

export function openCandidateRequests(requests, candidateId) {
  return (requests ?? []).filter((request) => request.status === 'OPEN'
    && request.scope === 'CANDIDATE' && (!candidateId || request.candidateId === candidateId));
}

export function selectedConceptId(selection) {
  return selection?.conceptId ?? null;
}
