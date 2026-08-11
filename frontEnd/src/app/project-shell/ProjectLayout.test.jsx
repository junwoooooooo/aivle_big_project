import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { readFileSync } from 'node:fs';
import { DesktopStepNavigation, ProjectHelpControl, workCenterViewState } from './ProjectLayout.jsx';

describe('Project helper control', () => {
  it('opens useful canonical stage guidance instead of remaining dead', () => {
    render(<ProjectHelpControl current={{ label: '2. 사업안', nextAction: { label: '검증 가정 확인' } }} currentStatus={{ label: '입력 필요' }} />);
    fireEvent.click(screen.getByLabelText('도움말과 가이드 열기'));
    expect(screen.getByRole('dialog', { name: '현재 단계 도움말' })).toBeInTheDocument();
    expect(screen.getByText('현재 상태: 입력 필요')).toBeInTheDocument();
    expect(screen.getByText('다음에 할 일: 검증 가정 확인')).toBeInTheDocument();
    expect(screen.getByText(/오른쪽 작업 센터/)).toBeInTheDocument();
  });
});

describe('desktop project navigation and Work Center state', () => {
  it('keeps the sheet open while switching from list to detail', () => {
    const open = { mounted: true, phase: 'open', view: 'list', focusJobId: null, direction: 'forward' };
    expect(workCenterViewState(open, 'job-1')).toEqual({
      mounted: true, phase: 'open', view: 'detail', focusJobId: 'job-1', direction: 'forward',
    });
    expect(workCenterViewState(workCenterViewState(open, 'job-1'), null)).toMatchObject({
      mounted: true, phase: 'open', view: 'list', focusJobId: null, direction: 'backward',
    });
  });

  it('shows previous and gated next navigation', () => {
    render(<MemoryRouter><DesktopStepNavigation
      previous={{ href: '/app/projects/41/idea', shortLabel: '아이디어' }}
      current={{ shortLabel: '사업안' }}
      next={{ id: 'market', href: '/app/projects/41/market', shortLabel: '시장 분석', status: 'NOT_READY' }}
    /></MemoryRouter>);
    expect(screen.getByRole('link', { name: '← 아이디어' })).toHaveAttribute('href', '/app/projects/41/idea');
    expect(screen.getByText(/현재 단계 · 사업안/)).toBeInTheDocument();
    expect(screen.getByText(/시장 분석 →/)).toHaveAttribute('aria-disabled', 'true');
    expect(screen.getByText(/사업안을 선택하고 검증 가정을 확정한 후/)).toBeInTheDocument();
  });

  it('links the confirmed Idea step to Business Proposal without auto redirect', () => {
    render(<MemoryRouter><DesktopStepNavigation previous={null}
      next={{ id: 'concepts', href: '/app/projects/41/concepts', shortLabel: '사업안', status: 'READY' }}
    /></MemoryRouter>);
    expect(screen.getByRole('link', { name: '사업안 →' }))
      .toHaveAttribute('href', '/app/projects/41/concepts');
  });

  it('places one desktop navigator before the routed main content', () => {
    const source = readFileSync('src/app/project-shell/ProjectLayout.jsx', 'utf8');
    const layout = source.slice(source.indexOf('return <div className="pipeline-shell">'));
    expect(layout.match(/<DesktopStepNavigation/g)).toHaveLength(1);
    expect(layout.indexOf('<DesktopStepNavigation')).toBeLessThan(layout.indexOf('<Outlet'));
  });
});
