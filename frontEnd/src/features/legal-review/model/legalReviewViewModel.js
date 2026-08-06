export const LEGAL_CATEGORY_LABELS = {
  BUSINESS_REGISTRATION: '사업자 등록',
  LICENSE_AND_PERMIT: '인허가',
  PRIVACY_AND_DATA: '개인정보·데이터',
  TERMS_AND_CONTRACT: '약관·계약',
  INTELLECTUAL_PROPERTY: '지식재산권',
  CONSUMER_PROTECTION: '소비자 보호',
  ADVERTISING_AND_MARKETING: '광고·마케팅',
  LABOR_AND_EMPLOYMENT: '노무·고용',
  INDUSTRY_SPECIFIC: '산업별 규제',
  TAX_AND_FINANCIAL: '세무·재무 규제',
};

export const RISK_LABELS = {
  LOW: '낮음', MEDIUM: '보통', HIGH: '높음', CRITICAL: '매우 높음', UNKNOWN: '확인 필요',
};

export const APPLICABILITY_LABELS = {
  APPLICABLE: '적용 가능성 높음',
  POSSIBLY_APPLICABLE: '적용 가능성 있음',
  NOT_APPLICABLE: '현재 정보상 비적용',
  INSUFFICIENT_INFORMATION: '정보 부족',
};

export function parseStringList(value) {
  if (Array.isArray(value)) return value;
  if (!value) return [];
  try {
    const parsed = JSON.parse(value);
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
}
