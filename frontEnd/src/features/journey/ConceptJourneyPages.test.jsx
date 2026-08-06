import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';

import { ApiClientProvider } from '../../shared/api/ApiClientProvider.jsx';
import { ConceptGenerationPage } from './ConceptJourneyPages.jsx';

function renderPage(client) {
  return render(
    <MemoryRouter initialEntries={['/app/projects/7/journey/concept']}>
      <ApiClientProvider client={client}>
        <Routes>
          <Route
            path="/app/projects/:projectId/journey/concept"
            element={<ConceptGenerationPage />}
          />
        </Routes>
      </ApiClientProvider>
    </MemoryRouter>,
  );
}

describe('ConceptGenerationPage', () => {
  it('offers one explicit manual restart for AI_RESULT_INVALID without auto retry', async () => {
    let finishPost;
    const postResult = new Promise((resolve) => { finishPost = resolve; });
    const failedBatch = {
      id: 9,
      state: 'FAILED',
      stale: false,
      retryable: false,
      errorCode: 'AI_RESULT_INVALID',
      currentRound: 0,
      inspectedCandidates: 0,
      eligibleCandidates: 0,
      maxInspectedCandidates: 9,
      targetEligibleCount: 3,
    };
    const client = {
      get: vi.fn(async (path) => {
        if (path.endsWith('/legal-prechecks/current')) {
          return { data: { version: { conceptBuilderAllowed: true }, stale: false } };
        }
        if (path.endsWith('/concept-generations/current')) {
          return { data: failedBatch };
        }
        if (path.endsWith('/concepts')) return { data: [] };
        throw new Error(`Unexpected GET ${path}`);
      }),
      post: vi.fn(() => postResult),
    };

    renderPage(client);
    const restart = await screen.findByRole('button', {
      name: 'AI 응답 구조 오류 · 동일 입력으로 새 Batch 실행',
    });
    expect(client.post).not.toHaveBeenCalled();

    fireEvent.click(restart);
    expect(restart).toBeDisabled();
    await waitFor(() => expect(client.post).toHaveBeenCalledOnce());
    expect(client.post.mock.calls[0][0]).toBe('/api/v2/projects/7/concept-generations');

    finishPost({ data: { ...failedBatch, id: 10, state: 'GENERATING' } });
    await waitFor(() => expect(screen.getByText('GENERATING')).toBeInTheDocument());
  });
});
