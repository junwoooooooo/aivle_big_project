import { render, screen } from '@testing-library/react';
import { MemoryRouter, Outlet, Route, Routes } from 'react-router-dom';
import { describe, expect, it } from 'vitest';

import { ProjectOverviewPage } from './ProjectModulePages.jsx';

const journeys = [
  ['planning', '사업 기획', 'READY'], ['validation', '사업 검증', 'NOT_STARTED'],
  ['launch', '출시 준비', 'NOT_STARTED'], ['interview', '가상 인터뷰', 'NOT_STARTED'],
  ['marketingStrategy', '마케팅 전략', 'NOT_STARTED'], ['finalReport', '최종 보고서', 'NOT_STARTED'],
].map(([id, shortLabel, status]) => ({ id, shortLabel, label: shortLabel, status, href: `/app/projects/41/${id}` }));

function Host() {
  return <Outlet context={{ journeys }} />;
}

describe('project overview journey map', () => {
  it('6개 업무를 카드 격자가 아닌 하나의 여정 지도와 행동형 CTA로 표시한다', () => {
    render(<MemoryRouter initialEntries={['/']}><Routes><Route element={<Host />}><Route path="/" element={<ProjectOverviewPage />} /></Route></Routes></MemoryRouter>);
    expect(screen.getByRole('list').children).toHaveLength(6);
    expect(screen.getByRole('link', { name: '사업 기획 시작하기' })).toBeInTheDocument();
    expect(screen.queryByText('사업 기획 열기')).not.toBeInTheDocument();
    expect(document.querySelector('.journey-map')).toBeInTheDocument();
    expect(document.querySelector('.pipeline-overview__grid')).not.toBeInTheDocument();
    expect([...screen.getByRole('list').children].map((node) => node.querySelector('.journey-map__step').textContent)).toEqual(['1단계', '2단계', '3단계', '4단계', '5단계', '6단계']);
    expect(screen.getAllByRole('link')).toHaveLength(6);
    expect(screen.getByRole('link', { name: '사업 기획 시작하기' })).toContainElement(document.querySelector('.journey-map__station'));
    expect(document.querySelector('.journey-map__path path')).toHaveAttribute('d', expect.stringContaining('M80 180'));
    expect(screen.getByRole('link', { name: '사업 기획 시작하기' }).querySelector('path')).toHaveAttribute('d', 'M7 17 17 7M9 7h8v8');
  });
});
