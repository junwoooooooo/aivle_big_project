import {
  getProjectArea,
  getProjectBasePath,
  getProjectNextAction,
  getProjectStatusView,
} from './projectWorkflowModel.js';

const STAGE_LABEL = {
  DOCUMENT: '아이디어', STRUCTURING: '아이디어', LEGAL_REVIEW: '법률 검토',
  FEASIBILITY: '콘셉트 생성', FINANCIAL: '콘셉트 분석',
  PERSONA_CONFIGURATION: '콘셉트 선택', PANEL_SURVEY: '페르소나',
  PANEL_DISCUSSION: '인터뷰', MARKETING: '마케팅', REPORT: '최종 보고서',
  COMPLETED: '최종 보고서',
};

export function toProjectViewModel(project) {
  const viewModel = {
    projectId: String(project.id),
    name: project.title,
    description: project.description ?? '',
    industryCategory: project.industryCategory ?? '',
    status: project.status,
    stage: project.stage,
    createdAt: project.createdAt,
    updatedAt: project.updatedAt ?? project.createdAt,
    version: project.version,
  };
  const statusView = getProjectStatusView(viewModel.status);
  const nextAction = getProjectNextAction(viewModel);
  return {
    ...viewModel,
    statusLabel: statusView.label,
    statusTone: statusView.tone,
    stageLabel: STAGE_LABEL[viewModel.stage] ?? '단계 확인 필요',
    area: getProjectArea(viewModel),
    nextAction,
    nextRoute: nextAction.route.replace(`${getProjectBasePath(viewModel.projectId)}/`, ''),
  };
}

export function formatProjectDate(value) {
  if (!value) return '수정 시각 없음';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '수정 시각 확인 필요';
  return new Intl.DateTimeFormat('ko-KR', {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(date);
}
