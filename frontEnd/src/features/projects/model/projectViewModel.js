import { projectRoutes } from '../../../app/routing/projectRoutes.js';
import { getProjectPresentationView, projectNextAction } from './projectPresentation.js';

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
  const presentationState = project.presentationState ?? (project.status === 'COMPLETED' ? 'COMPLETED' : 'NOT_STARTED');
  const statusView = getProjectPresentationView(presentationState);
  const result = {
    ...viewModel,
    presentationState,
    attentionCount: project.attentionCount ?? 0,
    attentionReason: project.attentionReason ?? null,
    statusLabel: statusView.label,
    statusTone: statusView.tone,
    stageLabel: project.currentJourneyLabel ?? (project.status === 'COMPLETED' ? '최종 보고서' : '사업 기획'),
    journeyCompleted: project.completedJourneyCount ?? (project.status === 'COMPLETED' ? 6 : 0),
    journeyTotal: 6,
    area: 'PIPELINE',
    nextAction: null,
    nextRoute: 'overview',
  };
  return { ...result, nextAction: { ...projectNextAction(result), route: projectRoutes.overview(viewModel.projectId) } };
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
