export const JOB_TASK_LABELS = Object.freeze({
  IDEA_ATTACHMENT_PARSE: '아이디어 첨부파일 분석',
  IDEA_BRIEF_DERIVATION: '아이디어 정리',
  CONCEPT_PORTFOLIO_V2_RUN: '사업안 검토',
  CONCEPT_PORTFOLIO_V2_CONTINUE: '추가 사업정보 반영',
  CONCEPT_PORTFOLIO_V2_SELECTION_ACTION: '사업안 선택 후 검토',
  CONCEPT_FACTORY_RUN: '사업안 생성',
  CONCEPT_CANDIDATE: '사업안 후보 생성',
  CONCEPT_DISTINCTNESS_JUDGE: '사업안 차별성 검토',
  CONCEPT_LEGAL_REVIEW: '사업안 법률 검토',
  CONCEPT_REDESIGN: '사업안 재설계',
  CONCEPT_HYPOTHESIS_ALTERNATIVE: '사업가설 대안 생성',
  CONCEPT_DELTA_LEGAL_REVIEW: '사업가설 변경 법률 검토',
  TECH_OPS_PROPOSAL: '기술·운영 분석',
  TECH_OPS_ADVISORY: '기술·운영 상용화 자문',
  TWIN_STIMULUS_DRAFT: 'Twin 비교안 초안',
  TWIN_SURVEY: 'Twin 조사',
  FINANCE_ESTIMATE: '재무 입력 AI 추정',
  FINANCE_ANALYSIS_REPORT: '재무 분석 보고서',
  MARKETING_CONTENT_GENERATION: '마케팅 콘텐츠 준비',
  MARKETING_VISUAL_GENERATION: '마케팅 이미지 생성',
});

export function jobTaskLabel(taskType, subjectType) {
  if (taskType === 'MARKET_RESEARCH') {
    return subjectType === 'MARKET_RESEARCH_BM' ? '비즈니스 모델' : '시장 조사';
  }
  return JOB_TASK_LABELS[taskType] ?? '프로젝트 작업';
}
