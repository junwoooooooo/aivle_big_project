import { getProjectStatusView } from '../../../app/module-status/projectModuleModel.js';
import { projectRoutes } from '../../../app/routing/projectRoutes.js';

export function toProjectViewModel(project) {
  const viewModel = {
    projectId: String(project.id),
    name: project.title,
    description: project.description ?? '',
    industryCategory: project.industryCategory ?? '',
    status: project.status,
    createdAt: project.createdAt,
    updatedAt: project.updatedAt ?? project.createdAt,
    version: project.version,
  };
  const statusView = getProjectStatusView(viewModel.status);
  const nextAction = {
    type: 'READY',
    label: '8단계 모듈 확인',
    description: '프로젝트 개요에서 각 모듈의 독립 상태와 필요한 입력을 확인할 수 있습니다.',
    route: projectRoutes.overview(viewModel.projectId),
    priority: 'NORMAL',
  };
  return {
    ...viewModel,
    statusLabel: statusView.label,
    statusTone: statusView.tone,
    stageLabel: '8단계 모듈',
    area: 'PIPELINE',
    nextAction,
    nextRoute: 'overview',
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
