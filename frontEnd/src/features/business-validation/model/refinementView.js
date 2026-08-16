export const FIELD_LABELS = Object.freeze({
  targetRegion: '목표 지역',
  revenueModel: '수익 모델',
  price: '가격',
  channels: '고객 접점·채널',
  differentiators: '차별점',
  preMarketSomShare: '초기 시장 점유 가정',
  preMarketSom: '초기 시장 규모 가정',
  targetUsers: '타깃 고객',
  featureSet: '핵심 기능',
  keyActivities: '핵심 활동',
  keyResources: '핵심 자원',
  keyPartners: '핵심 파트너',
  customerRelationships: '고객 관계',
});

export const BM_FIELD_LABELS = Object.freeze({
  key_activities: '핵심 활동',
  key_resources: '핵심 자원',
  key_partners: '핵심 파트너',
  customer_relationship: '고객 관계',
});

const VALUE_LABELS = Object.freeze({
  amount: '금액', currency: '통화', targetSharePercent: '목표 점유율',
  horizonYears: '기준 기간', value: '값',
});

export const fieldLabel = (field) => FIELD_LABELS[field] ?? null;
export const sourceLabel = (source) => source === 'MARKET'
  ? '시장 검증 근거' : source === 'LEGAL' ? '법률 검토 근거' : '검증 근거';
export const outcomeLabel = (outcome) => ({
  REFINED: '다듬기 완료', KEEP_CURRENT: '현재 사업안 유지', NO_CHANGES: '바꿀 점 없음',
}[outcome] ?? '최종 결과');

export function formatRefinementValue(value) {
  if (value === null || value === undefined || value === '') return '값 없음';
  if (Array.isArray(value)) return value.length ? value.map(formatRefinementValue).join(', ') : '없음';
  if (typeof value === 'object') {
    const parts = Object.entries(value).map(([key, item]) => {
      const formatted = formatRefinementValue(item);
      return VALUE_LABELS[key] ? `${VALUE_LABELS[key]}: ${formatted}` : formatted;
    });
    return parts.length ? parts.join(' · ') : '없음';
  }
  if (typeof value === 'boolean') return value ? '예' : '아니요';
  return String(value);
}

export function validProposals(proposals) {
  return (Array.isArray(proposals) ? proposals : []).filter((proposal) =>
    fieldLabel(proposal?.fieldKey) && typeof proposal?.proposalKey === 'string');
}

const notStartedRefinement = (source) => ({
  state: 'NOT_STARTED', stale: false, round: 0, policy: source?.policy,
  proposals: [], retry: { available: false }, proposalSetHash: null, decision: null,
  sourceBusinessValidationSessionId: null,
});
const notStartedFinal = () => ({
  state: 'NOT_STARTED', outcome: null, stale: false, value: null,
  sourceBusinessValidationSessionId: null,
});

export function resolveRefinementCycle({ validation, refinement, finalView }) {
  const sessionId = validation?.businessValidationSessionId;
  const validSession = typeof sessionId === 'string' && sessionId.length > 0;
  const refinementCurrent = validSession
    && refinement?.sourceBusinessValidationSessionId === sessionId;
  const finalCurrent = validSession
    && finalView?.sourceBusinessValidationSessionId === sessionId;
  return {
    effectiveRefinement: refinement?.state === 'UNAVAILABLE' || refinementCurrent
      ? refinement : notStartedRefinement(refinement),
    effectiveFinal: finalCurrent ? finalView : notStartedFinal(),
  };
}
