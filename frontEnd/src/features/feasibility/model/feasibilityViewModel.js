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
