import { readFileSync } from 'node:fs';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import BusinessValidationPreparation from './BusinessValidationPreparation.jsx';

const api = { currentBmPlan: vi.fn(), saveBmPlan: vi.fn() };
vi.mock('../../market/marketApi.js', () => ({ createMarketApi: () => api }));
vi.mock('../../../shared/api/ApiClientProvider.jsx', () => ({ useApiClient: () => ({}) }));

const portfolio = (overrides = {}) => ({ busy: false, selection: { status: 'LEGAL_REPORT_READY' }, finalizeMarketSeed: vi.fn(() => Promise.resolve()), ...overrides });
const renderPrep = (value = portfolio()) => render(<MemoryRouter><BusinessValidationPreparation projectId="41" portfolio={value} onBack={vi.fn()} /></MemoryRouter>);

beforeEach(() => {
  vi.clearAllMocks();
  api.currentBmPlan.mockResolvedValue({ plan: {}, constraints: {}, revision: 0 });
  api.saveBmPlan.mockResolvedValue({ plan: {}, constraints: {}, revision: 1 });
});

describe('사업 검증 준비', () => {
  it('기존 BM plan revision과 값을 불러와 저장한 뒤 handoff를 시작한다', async () => {
    api.currentBmPlan.mockResolvedValue({ plan: { customer_relationship: '예약 알림' }, constraints: { team: 3 }, revision: 2 });
    const value = portfolio();
    renderPrep(value);
    expect(await screen.findByDisplayValue('예약 알림')).toBeInTheDocument();
    expect(screen.getByText('저장된 준비 정보 · 수정 2')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '저장하고 계속' }));
    await waitFor(() => expect(api.saveBmPlan).toHaveBeenCalledWith({ customer_relationship: '예약 알림' }, { team: 3 }));
    expect(api.saveBmPlan).toHaveBeenCalledBefore(value.finalizeMarketSeed);
  });

  it('저장 실패 시 handoff를 호출하지 않고 입력을 유지한다', async () => {
    api.saveBmPlan.mockRejectedValue(new Error('save failed'));
    const value = portfolio();
    renderPrep(value);
    const input = await screen.findByLabelText('고객 관계 유지 방식');
    fireEvent.change(input, { target: { value: '고객 지원' } });
    fireEvent.click(screen.getByRole('button', { name: '저장하고 계속' }));
    await waitFor(() => expect(screen.getByRole('alert')).toBeInTheDocument());
    expect(value.finalizeMarketSeed).not.toHaveBeenCalled();
    expect(input).toHaveValue('고객 지원');
  });

  it('모든 선택 입력이 비어 있어도 확인 후 빈 plan으로 진행한다', async () => {
    const value = portfolio();
    renderPrep(value);
    await screen.findByRole('button', { name: '저장하고 계속' });
    fireEvent.click(screen.getByRole('button', { name: '저장하고 계속' }));
    expect(screen.getByText('입력하지 않은 항목은 비워 둔 채 진행합니다.')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '비운 채 계속' }));
    await waitFor(() => expect(api.saveBmPlan).toHaveBeenCalledWith({}, {}));
    expect(value.finalizeMarketSeed).toHaveBeenCalled();
  });

  it('desktop split과 tablet 단일 열 overflow 계약을 고정한다', () => {
    const css = readFileSync('src/features/concept-portfolio/styles/business-validation-preparation.css', 'utf8');
    expect(css).toContain('grid-template-columns: minmax(0, 1.6fr) minmax(18rem, .9fr)');
    expect(css).toContain('@media (max-width: 62rem)');
    expect(css).toContain('grid-template-columns: 1fr');
    expect(css).toContain('min-width: 0');
  });
});
