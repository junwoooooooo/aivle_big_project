import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { readFileSync } from 'node:fs';
import { JourneySubsteps, ProjectLocationRow } from './ProjectLayout.jsx';

describe('V9 project layout', () => {
  it('통합 Journey 안에서 작은 substep 탐색만 제공한다', () => {
    const journey = { shortLabel: '사업 검증', children: [
      { id: 'market', href: '/app/projects/41/market', shortLabel: '시장 분석', status: 'COMPLETED' },
      { id: 'businessModel', href: '/app/projects/41/business-model', shortLabel: '사업 모델', status: 'READY' },
    ] };
    render(<MemoryRouter><JourneySubsteps journey={journey} currentModule={journey.children[1]} /></MemoryRouter>);
    expect(screen.getByRole('navigation', { name: '사업 검증 세부 업무' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /사업 모델/ })).toHaveAttribute('aria-current', 'step');
  });

  it('상시 sidebar, Work Center, floating helper를 렌더링하지 않는다', () => {
    const source = readFileSync('src/app/project-shell/ProjectLayout.jsx', 'utf8');
    expect(source).not.toContain('pipeline-shell__sidebar');
    expect(source).not.toContain('pipeline-shell__work-center');
    expect(source).not.toContain('pipeline-shell__help');
  });

  it('프로젝트 개요에서는 self-return을 숨기고 breadcrumb를 유지한다', () => {
    render(<MemoryRouter><ProjectLocationRow projectId="41"
      currentJourney={{ id: 'overview', shortLabel: '프로젝트 개요' }} /></MemoryRouter>);
    expect(screen.getByRole('link', { name: '프로젝트 개요' })).toHaveAttribute('href', '/app/projects/41/overview');
    expect(screen.queryByRole('link', { name: '프로젝트 개요로 돌아가기' })).not.toBeInTheDocument();
  });

  it.each(['planning', 'validation', 'launch', 'interview', 'marketingStrategy', 'finalReport'])(
    '%s Journey에서 공통 overview return을 제공한다', (id) => {
      render(<MemoryRouter><ProjectLocationRow projectId="41"
        currentJourney={{ id, shortLabel: id }} /></MemoryRouter>);
      const link = screen.getByRole('link', { name: '프로젝트 개요로 돌아가기' });
      expect(link).toHaveAttribute('href', '/app/projects/41/overview');
      expect(link).toHaveAttribute('title', '프로젝트 개요로 돌아가기');
    });

  it('overview return은 browser history가 아니라 projectRoutes 링크만 사용한다', () => {
    const source = readFileSync('src/app/project-shell/ProjectLayout.jsx', 'utf8');
    const css = readFileSync('src/app/project-shell/project-shell.css', 'utf8');
    expect(source).toContain('to={projectRoutes.overview(projectId)}');
    expect(source).not.toMatch(/navigate\(-1\)|history\.back|window\.history\.back/);
    expect(css).toContain('.pipeline-shell__overview-return');
    expect(css).toContain('width:2.5rem; height:2.5rem');
    expect(css).toContain('.pipeline-shell__overview-return:focus-visible');
  });
});
