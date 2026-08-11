export const JOB_TASK_LABELS = Object.freeze({
  CONCEPT_PORTFOLIO_V2_RUN: '사업안 검토',
  CONCEPT_PORTFOLIO_V2_CONTINUE: '추가 사업정보 반영',
  CONCEPT_PORTFOLIO_V2_SELECTION_ACTION: '사업안 선택 후 검토',
  MARKET_ANALYSIS: '시장 분석 준비',
  MARKET_RESEARCH: '시장조사·BM 분석',
  TWIN_STIMULUS_DRAFT: 'Twin 비교안 초안',
  TWIN_SURVEY: 'Twin 조사',
  FINANCE_ESTIMATE: '재무 입력 AI 추정',
  FINANCE_ANALYSIS_REPORT: '재무 분석 보고서',
  IDEA_BRIEF_DERIVATION: '아이디어 정리',
  MARKETING_CONTENT_GENERATION: '마케팅 콘텐츠 준비',
  MARKETING_VISUAL_GENERATION: '마케팅 이미지 생성',
});
export function jobTaskLabel(taskType) { return JOB_TASK_LABELS[taskType] ?? '프로젝트 작업'; }
