export const JOB_TASK_LABELS = Object.freeze({
  IDEA_ATTACHMENT_PARSE: '아이디어 첨부파일 분석',
  IDEA_BRIEF_DERIVATION: '아이디어 정리',
  CONCEPT_PORTFOLIO_V2_RUN: '사업안 만들기',
  CONCEPT_PORTFOLIO_V2_CONTINUE: '추가 사업 정보 반영하기',
  CONCEPT_PORTFOLIO_V2_SELECTION_ACTION: '선택한 사업안 검토하기',
  CONCEPT_FACTORY_RUN: '사업안 만들기',
  CONCEPT_CANDIDATE: '사업안 후보 생성',
  CONCEPT_DISTINCTNESS_JUDGE: '사업안 차별성 검토',
  CONCEPT_LEGAL_REVIEW: '사업안 법률 검토',
  CONCEPT_REDESIGN: '사업안 재설계',
  CONCEPT_HYPOTHESIS_ALTERNATIVE: '사업가설 대안 생성',
  CONCEPT_DELTA_LEGAL_REVIEW: '사업가설 변경 법률 검토',
  TECH_OPS_PROPOSAL: '기술·운영 계획 만들기',
  TECH_OPS_ADVISORY: '기술·운영 자문',
  TWIN_STIMULUS_DRAFT: '가상 인터뷰 질문 준비',
  TWIN_SURVEY: '가상 고객 인터뷰',
  FINANCE_ESTIMATE: '재무 입력값 준비',
  FINANCE_ANALYSIS_REPORT: '재무 분석 보고서',
  MARKETING_CONTENT_GENERATION: '마케팅 콘텐츠 준비',
  MARKETING_VISUAL_GENERATION: '마케팅 이미지 생성',
});

export function jobTaskLabel(taskType, subjectType) {
  if (taskType === 'MARKET_RESEARCH') {
    return subjectType === 'MARKET_RESEARCH_BM' ? '수익 구조 분석' : '시장 분석';
  }
  return JOB_TASK_LABELS[taskType] ?? '프로젝트 작업';
}

export function jobModuleLabel(module) {
  return ({
    IDEA: '사업 기획', CONCEPT_PORTFOLIO: '사업 기획', CONCEPT_FACTORY: '사업 기획',
    CONCEPT_SELECTION: '사업 기획', MARKET: '사업 검증', BUSINESS_MODEL: '사업 검증',
    TECH_OPS: '출시 준비', FINANCE: '출시 준비', TWIN: '가상 인터뷰', MARKETING: '마케팅 전략',
  })[module] ?? '프로젝트';
}
