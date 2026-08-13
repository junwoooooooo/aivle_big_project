import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { useEffect } from 'react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';

import ProjectContextTools from './ProjectContextTools.jsx';
import { ProjectChromeProvider, useProjectChrome } from './ProjectChromeContext.jsx';

vi.mock('../../features/job-center/JobCenter.jsx', () => ({ default: () => <div>프로젝트 작업</div> }));

const model = {
  projectId: '41', currentJourney: { id: 'validation', shortLabel: '사업 검증' },
  currentModule: { shortLabel: '시장 분석', nextAction: { label: '시장 근거 확인' } },
  currentStatus: { label: '진행 중' },
  journeys: [
    { id: 'planning', shortLabel: '사업 기획', href: '/app/projects/41/idea', status: 'COMPLETED' },
    { id: 'validation', label: '2. 사업 검증', shortLabel: '사업 검증', href: '/app/projects/41/market', status: 'IN_PROGRESS' },
    { id: 'launch', shortLabel: '출시 준비', href: '/app/projects/41/tech-ops', status: 'NOT_STARTED' },
  ],
};

function RegisteredTools() {
  const { register } = useProjectChrome();
  useEffect(() => register(model), [register]);
  return <ProjectContextTools />;
}

describe('project context tools', () => {
  it('도움말·단계·작업 중 하나만 열고 Escape 후 trigger로 초점을 돌린다', async () => {
    render(<MemoryRouter initialEntries={['/app/projects/41/market']}><ProjectChromeProvider><RegisteredTools /></ProjectChromeProvider></MemoryRouter>);
    const helper = await screen.findByRole('button', { name: '도움말' });
    const navigator = screen.getByRole('button', { name: '단계' });
    fireEvent.click(helper);
    expect(screen.getByRole('dialog', { name: '현재 업무 도움말' })).toBeInTheDocument();
    fireEvent.click(navigator);
    expect(screen.queryByRole('dialog', { name: '현재 업무 도움말' })).not.toBeInTheDocument();
    expect(screen.getByLabelText('프로젝트 단계 탐색')).toBeInTheDocument();
    fireEvent.keyDown(window, { key: 'Escape' });
    expect(screen.queryByLabelText('프로젝트 단계 탐색')).not.toBeInTheDocument();
    await waitFor(() => expect(navigator).toHaveFocus());
  });

  it('Project Chrome 등록이 없으면 도구를 표시하지 않는다', () => {
    render(<MemoryRouter initialEntries={['/app']}><ProjectChromeProvider><ProjectContextTools /></ProjectChromeProvider></MemoryRouter>);
    expect(screen.queryByRole('button', { name: '도움말' })).not.toBeInTheDocument();
  });
});
