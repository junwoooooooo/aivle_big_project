export const JOB_TASK_LABELS = Object.freeze({
  CONCEPT_PORTFOLIO_V2_RUN: '사업안 검토',
  CONCEPT_PORTFOLIO_V2_CONTINUE: '추가 사업정보 반영',
  CONCEPT_PORTFOLIO_V2_SELECTION_ACTION: '사업안 선택 후 검토',
  MARKET_ANALYSIS: '시장 분석 준비',
  IDEA_BRIEF_DERIVATION: '아이디어 정리',
  MARKETING_CONTENT_GENERATION: '마케팅 콘텐츠 준비',
});
export function jobTaskLabel(taskType) { return JOB_TASK_LABELS[taskType] ?? '프로젝트 작업'; }
