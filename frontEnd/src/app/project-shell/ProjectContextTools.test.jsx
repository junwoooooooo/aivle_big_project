import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { useEffect } from 'react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';

import ProjectContextTools from './ProjectContextTools.jsx';
import { ProjectChromeProvider, useProjectChrome } from './ProjectChromeContext.jsx';

vi.mock('../../features/job-center/JobCenter.jsx', async () => {
  const { Link } = await import('react-router-dom');
  return { default: ({ quickOpen, sheet, onOpenList, onCloseSheet, onNavigate }) => <>
    {quickOpen && <div className="project-work-popover"><span>프로젝트 작업</span><button type="button" onClick={onOpenList}>전체 작업 보기</button></div>}
    {sheet?.mounted && <div role="presentation" data-testid="work-backdrop" onMouseDown={(event) => { if (event.target === event.currentTarget) onCloseSheet(); }}><section role="dialog" aria-label="전체 작업"><button type="button" onClick={onCloseSheet}>작업 센터 닫기</button><Link to="/app/projects/41/idea" onClick={onNavigate}>업무 화면 열기</Link></section></div>}
  </> };
});

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

function RegisteredTools({ value = model }) {
  const { register } = useProjectChrome();
  useEffect(() => register(value), [register, value]);
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

  it('개요를 포함한 7개 탐색 순서를 사용하고 마지막 단계 문구를 노출하지 않는다', async () => {
    const overviewModel = { ...model, currentJourney: { id: 'overview', shortLabel: '프로젝트 개요' }, journeys: [
      ...model.journeys,
      { id: 'interview', shortLabel: '가상 인터뷰', href: '/app/projects/41/twin-survey', status: 'NOT_STARTED' },
      { id: 'marketingStrategy', shortLabel: '마케팅 전략', href: '/app/projects/41/marketing', status: 'NOT_STARTED' },
      { id: 'finalReport', shortLabel: '최종 보고서', href: '/app/projects/41/final-report', status: 'NOT_STARTED' },
    ] };
    render(<MemoryRouter initialEntries={['/app/projects/41/overview']}><ProjectChromeProvider><RegisteredTools value={overviewModel} /></ProjectChromeProvider></MemoryRouter>);
    fireEvent.click(await screen.findByRole('button', { name: '단계' }));
    expect(screen.getByText('1 / 7')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '이전 단계 없음' })).toBeDisabled();
    expect(screen.getByRole('link', { name: '다음 단계: 사업 기획' })).toHaveAttribute('href', '/app/projects/41/idea');
    expect(screen.queryByText('마지막 단계')).not.toBeInTheDocument();
  });

  it('Quick은 스크롤을 잠그지 않고 Full의 닫기·배경·Escape·경로 이동·unmount는 잠금을 복원한다', async () => {
    const view = render(<MemoryRouter initialEntries={['/app/projects/41/market']}><ProjectChromeProvider><RegisteredTools /></ProjectChromeProvider></MemoryRouter>);
    fireEvent.click(await screen.findByRole('button', { name: '작업' }));
    expect(document.body.style.overflow).not.toBe('hidden');
    fireEvent.click(screen.getByRole('button', { name: '전체 작업 보기' }));
    await waitFor(() => expect(document.body.style.overflow).toBe('hidden'));
    expect(document.querySelectorAll('.project-work-popover')).toHaveLength(0);
    fireEvent.click(screen.getByRole('button', { name: '작업 센터 닫기' }));
    await waitFor(() => expect(document.body.style.overflow).not.toBe('hidden'));

    fireEvent.click(screen.getByRole('button', { name: '작업' }));
    fireEvent.click(screen.getByRole('button', { name: '전체 작업 보기' }));
    fireEvent.mouseDown(screen.getByTestId('work-backdrop'));
    await waitFor(() => expect(document.body.style.overflow).not.toBe('hidden'));

    fireEvent.click(screen.getByRole('button', { name: '작업' }));
    fireEvent.click(screen.getByRole('button', { name: '전체 작업 보기' }));
    fireEvent.keyDown(window, { key: 'Escape' });
    await waitFor(() => expect(document.body.style.overflow).not.toBe('hidden'));

    fireEvent.click(screen.getByRole('button', { name: '작업' }));
    fireEvent.click(screen.getByRole('button', { name: '전체 작업 보기' }));
    fireEvent.click(screen.getByRole('link', { name: '업무 화면 열기' }));
    await waitFor(() => expect(document.body.style.overflow).not.toBe('hidden'));

    fireEvent.click(screen.getByRole('button', { name: '작업' }));
    fireEvent.click(screen.getByRole('button', { name: '전체 작업 보기' }));
    view.unmount();
    expect(document.body.style.overflow).not.toBe('hidden');
  });
});
