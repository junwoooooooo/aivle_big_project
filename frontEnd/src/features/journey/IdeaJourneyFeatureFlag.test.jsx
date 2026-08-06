import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { ApiClientProvider } from '../../shared/api/ApiClientProvider.jsx';

describe('Idea Journey feature flag', () => {
  afterEach(() => { vi.unstubAllEnvs(); vi.resetModules(); });

  it('keeps the legacy Idea Journey when the conversational workspace flag is off', async () => {
    vi.stubEnv('VITE_CONVERSATIONAL_VALIDATION_WORKSPACE_ENABLED', 'false');
    const { IdeaJourneyPage } = await import('./JourneyPages.jsx');
    const client = { get: vi.fn(async () => ({ data: null })) };
    render(<MemoryRouter initialEntries={['/app/projects/7/journey/idea']}>
      <ApiClientProvider client={client}><Routes>
        <Route path="/app/projects/:projectId/journey/idea" element={<IdeaJourneyPage />} />
      </Routes></ApiClientProvider>
    </MemoryRouter>);
    expect(await screen.findByRole('heading', { name: '아이디어를 명확한 검토 입력으로 만드세요' })).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: '대화로 사업 기회를 구체화하세요' })).not.toBeInTheDocument();
  });
});
