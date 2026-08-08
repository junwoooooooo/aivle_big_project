export const MIN_COMPARE_COUNT = 2;
export const MAX_COMPARE_COUNT = 5;

export const LEGAL_STATUS_LABELS = Object.freeze({
  IMPLEMENTABLE: '구현 가능',
  IMPLEMENTABLE_WITH_CONTROLS: '통제 조건부 구현 가능',
});

const asList = (value) => Array.isArray(value) ? value.filter((item) => typeof item === 'string' && item.trim()) : [];
const text = (value, fallback = '정보 없음') => typeof value === 'string' && value.trim() ? value.trim() : fallback;
const listText = (value) => asList(value).join(' · ') || '해당 없음';

function operatingDifficulty(candidate) {
  const dependencies = asList(candidate.partnerRequirements).length;
  const physical = asList(candidate.physicalActivities).length;
  const actors = asList(candidate.actorRoles).length;
  const burden = dependencies + physical + Math.max(0, actors - 2);
  if (burden >= 6) return `높음 · 참여자 ${actors}개, 파트너 ${dependencies}개, 현장 활동 ${physical}개`;
  if (burden >= 3) return `보통 · 참여자 ${actors}개, 파트너 ${dependencies}개, 현장 활동 ${physical}개`;
  return `낮음 · 참여자 ${actors}개, 파트너 ${dependencies}개, 현장 활동 ${physical}개`;
}

export const COMPARISON_ROWS = Object.freeze([
  { key: 'targetCustomer', label: '대상 고객', read: (model) => model.targetCustomer },
  { key: 'coreProblem', label: '핵심 문제', read: (model) => model.coreProblem },
  { key: 'coreValue', label: '핵심 가치', read: (model) => model.coreValue },
  { key: 'userFlow', label: '사용 흐름', read: (model) => model.userFlow },
  { key: 'platformRole', label: '플랫폼 역할', read: (model) => model.platformRole },
  { key: 'features', label: '주요 기능', read: (model) => model.features },
  { key: 'revenueModel', label: '수익 모델', read: (model) => model.revenueModel },
  { key: 'operatingDifficulty', label: '운영 난이도', read: (model) => model.operatingDifficulty },
  { key: 'partnerDependency', label: '파트너 의존성', read: (model) => model.partnerDependency },
  { key: 'legalControls', label: '법률 필수 조건', read: (model) => model.requiredControls },
  { key: 'initialScope', label: '초기 실행 범위', read: (model) => model.initialScope },
]);

export function deriveComparisonTags(concept) {
  const candidate = concept?.candidate ?? {};
  const assessment = concept?.legalReview?.assessment ?? {};
  const partners = asList(candidate.partnerRequirements);
  const physical = asList(candidate.physicalActivities);
  const target = text(candidate.targetUsers, '').toLowerCase();
  const revenue = `${text(candidate.revenueModel, '')} ${text(candidate.price, '')}`.toLowerCase();
  const tags = [];

  if (concept?.variationFocus === 'LOW_RISK_FAST_EXECUTION') tags.push('빠른 실행');
  if (partners.length === 0 && physical.length === 0) tags.push('초기 비용 낮음');
  if (partners.length >= 3 || concept?.variationFocus === 'OPERATING_MODEL_AND_PARTNERS') tags.push('파트너 의존 높음');
  if (/(구독|정기|월간|연간|멤버십|subscription|recurring)/i.test(revenue)) tags.push('반복 수익형');
  if (/(b2b|기업|사업자|조직|기관|매장)/i.test(target)) tags.push('B2B 중심');
  if (/(b2c|개인|소비자|일반 고객|가정)/i.test(target)) tags.push('B2C 중심');
  if (concept?.legalStatus === 'IMPLEMENTABLE_WITH_CONTROLS' || asList(assessment.requiredControls).length > 0) tags.push('규제 통제 필요');
  return [...new Set(tags)];
}

export function toComparisonModel(concept) {
  const candidate = concept?.candidate ?? {};
  const legal = concept?.legalReview ?? {};
  const assessment = legal.assessment ?? {};
  return {
    conceptId: concept.conceptId,
    slotNumber: concept.slotNumber,
    title: text(concept.title),
    summary: text(concept.summary),
    differentiator: text(candidate.differentiators),
    targetCustomer: text(candidate.targetUsers),
    coreProblem: text(candidate.problemScenario),
    coreValue: text(candidate.coreValue),
    userFlow: listText(candidate.transactionFlow),
    platformRole: text(candidate.platformRole),
    features: listText(candidate.featureSet),
    operatingModel: text(candidate.operatingModel),
    operatingDifficulty: operatingDifficulty(candidate),
    revenueModel: `${text(candidate.revenueModel)} · ${text(candidate.price)}`,
    partnerDependency: listText(candidate.partnerRequirements),
    requiredControls: listText(assessment.requiredControls),
    risks: listText(candidate.constraintCompliance),
    initialScope: `${text(candidate.solutionMechanism)} · 채널: ${text(candidate.channels)}`,
    legalStatus: concept.legalStatus,
    legalStatusLabel: LEGAL_STATUS_LABELS[concept.legalStatus] ?? text(concept.legalStatus),
    legal,
    candidate,
    tags: deriveComparisonTags(concept),
  };
}

export function createLocalSelectionDraft(projectId, comparedConceptIds, preferredConceptId) {
  const uniqueIds = [...new Set(comparedConceptIds)].slice(0, MAX_COMPARE_COUNT);
  if (uniqueIds.length < MIN_COMPARE_COUNT) throw new Error('비교 대상은 2개 이상이어야 합니다.');
  if (!preferredConceptId || !uniqueIds.includes(preferredConceptId)) throw new Error('비교 대상 중 선택 후보를 표시해야 합니다.');
  return {
    version: 1,
    projectId: String(projectId),
    comparedConceptIds: uniqueIds,
    preferredConceptId,
    savedAt: new Date().toISOString(),
    persistence: 'SESSION_LOCAL_ONLY',
  };
}
