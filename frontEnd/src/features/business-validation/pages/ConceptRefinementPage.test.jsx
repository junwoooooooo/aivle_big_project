import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';

import { ApiClientProvider } from '../../../shared/api/ApiClientProvider.jsx';
import ConceptRefinementPage from './ConceptRefinementPage.jsx';

function renderPage(client) {
  return render(<MemoryRouter initialEntries={['/app/projects/41/concept-refinement']}>
    <ApiClientProvider client={client}><Routes>
      <Route path="/app/projects/:projectId/concept-refinement" element={<ConceptRefinementPage />} />
    </Routes></ApiClientProvider>
  </MemoryRouter>);
}

describe('ConceptRefinementPage', () => {
  it('독립 화면에서 full v3 session lineage의 refinement만 읽는다', async () => {
    const client = { get: vi.fn(async (url) => {
      if (url.endsWith('/business-validation/current')) return { data: {
        businessValidationSessionId: 'session-1', state: 'COMPLETED', stale: false,
      } };
      if (url.endsWith('/business-validation/refinement/current')) return { data: {
        sourceBusinessValidationSessionId: 'session-1', state: 'NOT_STARTED', stale: false,
      } };
      if (url.endsWith('/business-validation/refinement/final')) return { data: {
        sourceBusinessValidationSessionId: 'session-1', state: 'NOT_STARTED', stale: false,
      } };
      throw new Error(`unexpected ${url}`);
    }), post: vi.fn() };

    renderPage(client);

    expect(await screen.findByRole('heading', { name: '검증 결과를 현재 컨셉에 반영하세요' }))
      .toBeInTheDocument();
    expect(screen.getByRole('button', { name: '다듬기 제안 받기' })).toBeInTheDocument();
    await waitFor(() => expect(client.get).toHaveBeenCalledTimes(3));
    expect(client.get.mock.calls.map(([url]) => url).every((url) => url.startsWith('/api/v3/projects/41/')))
      .toBe(true);
  });

  it('다듬기 적용으로 validation이 stale이어도 같은 session의 진행 결과를 보존한다', async () => {
    const client = { get: vi.fn(async (url) => {
      if (url.endsWith('/business-validation/current')) return { data: {
        businessValidationSessionId: 'session-1', state: 'STALE', stale: true,
      } };
      if (url.endsWith('/business-validation/refinement/current')) return { data: {
        sourceBusinessValidationSessionId: 'session-1', state: 'NO_CHANGES', stale: false,
      } };
      return { data: { sourceBusinessValidationSessionId: 'session-1', state: 'NOT_STARTED', stale: false } };
    }), post: vi.fn() };

    renderPage(client);

    expect(await screen.findByText('시장 검증 근거로 지금 바꿀 만한 항목이 나오지 않았습니다.'))
      .toBeInTheDocument();
    expect(screen.queryByText(/시장 분석이 완료되면 사업 모델/)).not.toBeInTheDocument();
  });
});
