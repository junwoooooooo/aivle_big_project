import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import { ApiClientProvider } from '../../../shared/api/ApiClientProvider.jsx';
import LaunchReadinessPage from './LaunchReadinessPage.jsx';

describe('Launch Readiness single-purpose surface', () => {
  it('DOCX 출시 준비 흐름만 표시하고 TechOps/Finance 업무를 노출하지 않는다', async () => {
    const client = { get: vi.fn().mockResolvedValue({ data: { moduleType: 'LAUNCH', status: 'NOT_STARTED' } }), post: vi.fn(), download: vi.fn() };
    render(<MemoryRouter initialEntries={['/app/projects/41/launch-readiness']}><ApiClientProvider client={client}><Routes>
      <Route path="/app/projects/:projectId/launch-readiness" element={<LaunchReadinessPage />} />
    </Routes></ApiClientProvider></MemoryRouter>);
    expect(await screen.findByRole('heading', { name: '출시 결정을 위한 준비 상태를 확인하세요' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /입력 템플릿 다운로드/ })).toBeInTheDocument();
    expect(screen.getByLabelText('출시 준비 DOCX 업로드')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '작성한 DOCX로 분석 시작' })).toBeInTheDocument();
    expect(screen.queryByText('재무 분석')).not.toBeInTheDocument();
    expect(screen.queryByText('기술 분석')).not.toBeInTheDocument();
    expect(screen.queryByText('운영 분석')).not.toBeInTheDocument();
    expect(screen.queryByText(/module completion/i)).not.toBeInTheDocument();
    expect(client.get).toHaveBeenCalledWith(expect.stringContaining('/launch-readiness/launch/current'));
  });
});
