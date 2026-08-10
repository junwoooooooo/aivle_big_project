import { fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import BusinessProposalWorkspace, { LegalReport } from './BusinessProposalWorkspace.jsx';
import { useConceptPortfolio } from '../hooks/useConceptPortfolio.js';

vi.mock('../hooks/useConceptPortfolio.js', () => ({ useConceptPortfolio: vi.fn() }));

describe('BusinessProposalWorkspace', () => {
  it('lets the user directly select a single reviewed proposal', () => {
    const select = vi.fn();
    useConceptPortfolio.mockReturnValue({ loading: false, error: null, busy: false,
      run: { runId: 'run', producedConceptCount: 1, openInputCount: 0 },
      concepts: [{ conceptId: 'c1', candidateId: 'candidate', conceptName: '지역 서비스', summary: '요약', selectable: true }],
      inputRequests: [], selection: null, hypotheses: [], report: null, marketSeed: null,
      select, refresh: vi.fn(), start: vi.fn(), respond: vi.fn() });
    render(<MemoryRouter initialEntries={['/app/projects/41/concepts']}><Routes><Route path="/app/projects/:projectId/concepts" element={<BusinessProposalWorkspace />} /></Routes></MemoryRouter>);
    fireEvent.click(screen.getByText('이 사업안 선택'));
    expect(select).toHaveBeenCalledWith('c1');
    expect(screen.getByText('검토 완료')).toBeInTheDocument();
  });

  it('renders the server final legal report sections without creating new judgments', () => {
    render(<LegalReport report={{ basisDate: '2026-08-11', report: { finalConclusion: '조건부 가능', officialEvidence: ['공식 근거'] } }} />);
    expect(screen.getByText('최종 법률·규제 보고서')).toBeInTheDocument();
    expect(screen.getByText('최종 결론')).toBeInTheDocument();
    expect(screen.getByText('거래/결제 흐름')).toBeInTheDocument();
    expect(screen.getByText('Delta 변경 이력')).toBeInTheDocument();
    expect(screen.getByText('조건부 가능')).toBeInTheDocument();
  });
});
