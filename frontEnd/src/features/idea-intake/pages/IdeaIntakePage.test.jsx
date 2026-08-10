import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Outlet, Route, Routes } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';

import { IDEA_INTAKE_SCREEN_STATE } from '../model/ideaIntakeModel.js';
import useIdeaIntake from '../hooks/useIdeaIntake.js';
import IdeaIntakePage from './IdeaIntakePage.jsx';

vi.mock('../hooks/useIdeaIntake.js', () => ({ default: vi.fn(), IDEA_FAILURE_KIND: {} }));

describe('Idea confirmation journey', () => {
  it('refreshes module status and offers an explicit proposal CTA without auto navigation', async () => {
    const retry = vi.fn();
    useIdeaIntake.mockReturnValue({ screenState: IDEA_INTAKE_SCREEN_STATE.CONFIRMED });
    render(<MemoryRouter initialEntries={['/app/projects/41/idea']}><Routes>
      <Route element={<Outlet context={{ moduleState: { retry } }} />}>
        <Route path="/app/projects/:projectId/idea" element={<IdeaIntakePage />} />
      </Route>
    </Routes></MemoryRouter>);
    expect(screen.getByText('1단계 · 아이디어 정리')).toBeInTheDocument();
    const link = screen.getByRole('link', { name: '사업안 검토로 이동' });
    expect(link).toHaveAttribute('href', '/app/projects/41/concepts');
    expect(window.location.pathname).not.toBe('/app/projects/41/concepts');
    await waitFor(() => expect(retry).toHaveBeenCalledTimes(1));
  });
});
