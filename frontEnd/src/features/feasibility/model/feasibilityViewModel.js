export const DIMENSION_LABELS = {
  PROBLEM_AND_NEED: '문제와 필요',
  TARGET_CUSTOMER: '목표 고객',
  MARKET_ATTRACTIVENESS: '시장 매력도',
  COMPETITIVE_POSITION: '경쟁 포지션',
  PRODUCT_SOLUTION_FIT: '제품·해결책 적합성',
  BUSINESS_MODEL: '비즈니스 모델',
  GO_TO_MARKET: '시장 진입 전략',
  FINANCIAL_VIABILITY: '재무 실행 가능성',
  EXECUTION_CAPABILITY: '실행 역량',
  LEGAL_AND_REGULATORY: '법률·규제',
};

export const VERDICT_LABELS = {
  PROMISING: '유망',
  CONDITIONAL: '조건부',
  HIGH_RISK: '고위험',
  INSUFFICIENT_INFORMATION: '정보 부족',
};

export const CONFIDENCE_LABELS = { LOW: '낮음', MEDIUM: '보통', HIGH: '높음' };

export const EVIDENCE_TYPE_LABELS = {
  DOCUMENT_FACT: '문서 사실',
  USER_ASSUMPTION: '사용자 가정',
  AI_INFERENCE: 'AI 추론',
  LEGAL_REVIEW: '법률 사전검토',
  EXTERNAL_VERIFICATION_REQUIRED: '외부 검증 필요',
};

export const GROUP_LABELS = {
  MARKET: '시장 분석',
  BUSINESS_MODEL: '비즈니스 모델 분석',
  TECHNOLOGY_OPERATION: '기술·운영 분석',
};

/** 각 묶음이 무엇을 보는지 — 화면에서 역할 경계를 드러낸다. */
export const GROUP_DESCRIPTIONS = {
  MARKET: '해결하려는 문제와 고객이 실재하는지, 그 시장이 매력적인지 봅니다.',
  BUSINESS_MODEL: '제품이 문제에 연결되는지, 수익 구조와 재무 가정이 검증 가능한지 봅니다.',
  TECHNOLOGY_OPERATION: '만들고 운영할 역량이 있는지, 규제가 실행에 어떤 제약을 주는지 봅니다.',
};

// 백엔드 FeasibilityDimensionCatalog 와 짝을 이룬다. 한쪽만 바꾸면 묶음에서 차원이 사라진다.
const DIMENSION_GROUP = {
  PROBLEM_AND_NEED: 'MARKET',
  TARGET_CUSTOMER: 'MARKET',
  MARKET_ATTRACTIVENESS: 'MARKET',
  COMPETITIVE_POSITION: 'MARKET',
  PRODUCT_SOLUTION_FIT: 'BUSINESS_MODEL',
  BUSINESS_MODEL: 'BUSINESS_MODEL',
  GO_TO_MARKET: 'BUSINESS_MODEL',
  FINANCIAL_VIABILITY: 'BUSINESS_MODEL',
  EXECUTION_CAPABILITY: 'TECHNOLOGY_OPERATION',
  LEGAL_AND_REGULATORY: 'TECHNOLOGY_OPERATION',
};

const GROUP_ORDER = ['MARKET', 'BUSINESS_MODEL', 'TECHNOLOGY_OPERATION'];

/**
 * 묶음 결과에 소속 차원을 붙여 화면이 쓸 형태로 만든다. 순수 함수.
 * 묶음 결과가 없는 구 assessment는 차원만으로 묶음을 구성한다(점수·서술은 비어 있음).
 */
export function groupDimensions(assessment) {
  const dimensions = assessment?.dimensions ?? [];
  const byType = new Map((assessment?.groups ?? []).map((item) => [item.analysisType, item]));
  return GROUP_ORDER.map((analysisType) => {
    const group = byType.get(analysisType);
    return {
      analysisType,
      label: GROUP_LABELS[analysisType] ?? analysisType,
      description: GROUP_DESCRIPTIONS[analysisType] ?? '',
      score: group?.score ?? null,
      verdict: group?.verdict ?? null,
      headline: group?.headline ?? null,
      summary: group?.summary ?? null,
      nextFocus: group?.nextFocus ?? null,
      strengths: parseJsonList(group?.strengthsJson),
      risks: parseJsonList(group?.risksJson),
      dimensions: dimensions.filter((item) => DIMENSION_GROUP[item.code] === analysisType),
    };
  }).filter((group) => group.dimensions.length > 0 || group.headline);
}

export function parseJsonList(value) {
  if (Array.isArray(value)) return value;
  if (typeof value !== 'string' || value.trim() === '') return [];
  try {
    const parsed = JSON.parse(value);
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
}
