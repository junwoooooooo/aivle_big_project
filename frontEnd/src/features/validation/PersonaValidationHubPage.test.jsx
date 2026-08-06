import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import useMarketingContents from '../marketing/hooks/useMarketingContents.js';
import useValidationData from './hooks/useValidationData.js';
import PersonaValidationHubPage from './PersonaValidationHubPage.jsx';

vi.mock('../marketing/hooks/useMarketingContents.js', () => ({ default: vi.fn() }));
vi.mock('./hooks/useValidationData.js', () => ({ default: vi.fn() }));

describe('PersonaValidationHubPage', () => {
  beforeEach(() => {
    useMarketingContents.mockReturnValue({ loading: false, error: null, items: [] });
    useValidationData.mockImplementation((_projectId, type) => type === 'interview'
      ? {
          loading: false,
          error: null,
          items: [{
            id: 11,
            status: 'COMPLETED',
            questionCount: 3,
            updatedAt: '2026-07-29T10:00:00',
          }],
        }
      : {
          loading: false,
          error: null,
          items: [{
            id: 12,
            status: 'DRAFT',
            messageCount: 2,
            updatedAt: '2026-07-29T11:00:00',
          }],
        });
  });

  it('shows actual latest panel and market states without fake progress', () => {
    render(
      <MemoryRouter initialEntries={['/app/projects/7/validate']}>
        <Routes>
          <Route path="/app/projects/:projectId/validate" element={<PersonaValidationHubPage />} />
        </Routes>
      </MemoryRouter>,
    );
    expect(screen.getByText('최근 완료 · 3개')).toBeInTheDocument();
    expect(screen.getByText('초안 작성 중 · 2개')).toBeInTheDocument();
    expect(screen.queryByText(/%/)).not.toBeInTheDocument();
    expect(screen.getByRole('link', { name: '결과 보기' }))
      .toHaveAttribute('href', '/app/projects/7/journey/interview');
  });
});
