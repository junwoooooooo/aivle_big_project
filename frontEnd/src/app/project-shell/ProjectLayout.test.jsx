import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { readFileSync } from 'node:fs';
import { JourneySubsteps } from './ProjectLayout.jsx';

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
});
