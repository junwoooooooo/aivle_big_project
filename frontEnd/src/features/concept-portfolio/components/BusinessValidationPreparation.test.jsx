import { readFileSync } from 'node:fs';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import BusinessValidationPreparation from './BusinessValidationPreparation.jsx';

const api = { currentBmPlan: vi.fn(), saveBmPlan: vi.fn() };
vi.mock('../../market/marketApi.js', () => ({ createMarketApi: () => api }));
vi.mock('../../../shared/api/ApiClientProvider.jsx', () => ({ useApiClient: () => ({}) }));

const portfolio = (overrides = {}) => ({ busy: false, selection: { status: 'LEGAL_REPORT_READY' }, finalizeMarketSeed: vi.fn(() => Promise.resolve()), ...overrides });
const renderPrep = (value = portfolio(), onBack = vi.fn()) => render(<MemoryRouter><BusinessValidationPreparation projectId="41" portfolio={value} onBack={onBack} /></MemoryRouter>);

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
    expect(await screen.findByText('예약 알림')).toBeInTheDocument();
    expect(screen.getByText('저장된 준비 정보 · 수정 2')).toBeInTheDocument();
    expect(screen.getAllByRole('button', { name: '저장하고 계속' })).toHaveLength(1);
    fireEvent.click(screen.getByRole('button', { name: '저장하고 계속' }));
    await waitFor(() => expect(api.saveBmPlan).toHaveBeenCalledWith({ customer_relationship: '예약 알림' }, { team: 3 }));
    expect(api.saveBmPlan).toHaveBeenCalledBefore(value.finalizeMarketSeed);
  });

  it('상단 action bar에서 법률 결과로 돌아가고 form id submit을 제공한다', async () => {
    const onBack = vi.fn();
    const view = renderPrep(portfolio(), onBack);
    await screen.findByRole('button', { name: '저장하고 계속' });
    const submit = screen.getByRole('button', { name: '저장하고 계속' });
    expect(submit).toHaveAttribute('form', 'business-validation-prep-form');
    expect(view.container.querySelectorAll('.mr-actions')).toHaveLength(0);
    fireEvent.click(screen.getByRole('button', { name: /법률·규제 결과로 돌아가기/ }));
    expect(onBack).toHaveBeenCalledTimes(1);
  });

  it('저장 실패 시 handoff를 호출하지 않고 입력을 유지한다', async () => {
    api.saveBmPlan.mockRejectedValue(new Error('save failed'));
    const value = portfolio();
    renderPrep(value);
    await screen.findByRole('button', { name: '저장하고 계속' });
    fireEvent.click(screen.getAllByRole('button', { name: '직접 입력' })[0]);
    const input = screen.getByLabelText('고객 관계 유지 방식');
    fireEvent.change(input, { target: { value: '고객 지원' } });
    fireEvent.click(screen.getByRole('button', { name: '저장하고 계속' }));
    await waitFor(() => expect(screen.getByRole('alert')).toBeInTheDocument());
    expect(value.finalizeMarketSeed).not.toHaveBeenCalled();
    expect(input).toHaveValue('고객 지원');
  });

  it('선택 사업안 초안을 사용한 뒤 사용자 수정값을 최종 저장 authority로 보낸다', async () => {
    const value = portfolio({ concepts: [{ conceptId: 'c1', candidate: { operatingModel: '예약 운영', transactionFlow: ['고객 신청'] } }], selection: { status: 'LEGAL_REPORT_READY', conceptId: 'c1' } });
    renderPrep(value);
    expect(await screen.findByText('선택한 사업안에서 가져온 초안')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '이 내용 사용' }));
    fireEvent.click(screen.getByRole('button', { name: '수정' }));
    const editor = screen.getByLabelText('사업 운영에서 반복적으로 해야 하는 일');
    fireEvent.change(editor, { target: { value: '사용자가 수정한 운영' } });
    fireEvent.click(screen.getByRole('button', { name: '저장하고 계속' }));
    await waitFor(() => expect(api.saveBmPlan).toHaveBeenCalledWith({ key_activities: ['사용자가 수정한 운영'] }, {}));
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

  it('compact 자원 layout과 tablet/mobile overflow 계약을 고정한다', () => {
    const css = readFileSync('src/features/concept-portfolio/styles/business-validation-preparation.css', 'utf8');
    expect(css).toContain('grid-template-columns: repeat(3, minmax(0, 1fr))');
    expect(css).toContain('@media (max-width: 56.25rem)');
    expect(css).toContain('grid-template-columns: repeat(2, minmax(0, 1fr))');
    expect(css).toContain('@media (max-width: 48rem)');
    expect(css).toContain('min-width: 0');
    expect(css).toContain('.bm-plan__suggestion');
    expect(css).toContain('.bm-plan__editor .ui-input');
    expect(css).toContain('background: #fff');
  });

  it('시장 준비 완료 후에도 저장한 BM Plan과 자원 제약을 read mode로 보여준다', async () => {
    api.currentBmPlan.mockResolvedValue({
      plan: { customer_relationship: '정기 안내', key_activities: ['예약 운영'], key_resources: ['운영 시스템'], key_partners: ['결제 대행사'] },
      constraints: { budget_krw: 50000000, months: 6, team: 4 },
      revision: 3,
    });
    renderPrep(portfolio({ selection: { status: 'READY_FOR_MARKET', conceptId: 'c1' } }));
    expect(await screen.findByRole('heading', { name: '저장한 운영 정보' })).toBeInTheDocument();
    for (const text of ['정기 안내', '예약 운영', '운영 시스템', '결제 대행사', '50000000 원', '6 개월', '4 명']) expect(screen.getByText(text)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /시장 분석 시작하기/ })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '저장하고 계속' })).not.toBeInTheDocument();
  });
});
