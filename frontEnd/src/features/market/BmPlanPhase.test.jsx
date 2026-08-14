import { describe, expect, it, vi, beforeEach } from 'vitest';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import BmCanvasPage from './BmCanvasPage.jsx';

/**
 * BM 앞 단계 — <b>제출 게이트</b>가 실제로 막는지 본다.
 *
 * 「빈 칸이 있으면 확인받는다」는 규칙은 화면에서만 성립한다. 서버·AI 어디에도 그 개념이
 * 없으므로 여기서 안 재면 아무도 안 잰다.
 */
const api = {
  currentBusinessModel: vi.fn(),
  startBusinessModel: vi.fn(),
  currentBmPlan: vi.fn(),
  saveBmPlan: vi.fn(),
  currentMarketResearch: vi.fn(),
  startMarketResearch: vi.fn(),
};

vi.mock('./marketApi.js', () => ({ createMarketApi: () => api }));
vi.mock('../../shared/api/ApiClientProvider.jsx', () => ({ useApiClient: () => ({}) }));

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/app/projects/1/business-model']}>
      <Routes>
        <Route path="/app/projects/:projectId/business-model" element={<BmCanvasPage />} />
      </Routes>
    </MemoryRouter>,
  );
}

function openOperation(label) {
  const row = screen.getByText(label).closest('.bm-plan__row');
  fireEvent.click(within(row).getByRole('button', { name: /직접 입력|수정/ }));
  return within(row).getByLabelText(label);
}

beforeEach(() => {
  vi.clearAllMocks();
  // 아직 캔버스가 없다 — 그래서 계획 국면이 나온다.
  api.currentBusinessModel.mockResolvedValue({ run: null, version: null });
  api.currentBmPlan.mockResolvedValue({ plan: {}, constraints: {}, revision: 0 });
  api.saveBmPlan.mockResolvedValue({ plan: {}, constraints: {}, revision: 1 });
  api.startBusinessModel.mockResolvedValue({ run: { state: 'QUEUED' } });
});

describe('실행 계획 국면', () => {
  it('캔버스가 없으면 먼저 물어본다 — 버튼 하나로 시작하지 않는다', async () => {
    renderPage();
    expect(await screen.findByText('운영 정보 확인')).toBeInTheDocument();
    expect(screen.getByText('고객 관계 유지 방식')).toBeInTheDocument();
    expect(screen.queryByRole('textbox', { name: '고객 관계 유지 방식' })).not.toBeInTheDocument();
  });

  it('앞 단계가 정한 것은 다시 묻지 않는다', async () => {
    const { container } = renderPage();
    await screen.findByText('운영 정보 확인');
    const labels = [...container.querySelectorAll('label')].map((n) => n.textContent).join(' ');
    expect(labels).not.toMatch(/수익\s*모델/);
    expect(labels).not.toMatch(/차별점/);
  });

  it('⭐ 빈 칸이 있으면 확인 없이 실행하지 않는다', async () => {
    renderPage();
    await screen.findByText('운영 정보 확인');

    fireEvent.click(screen.getByRole('button', { name: '저장하고 캔버스 만들기' }));

    expect(await screen.findByText('비어 있는 칸이 있습니다')).toBeInTheDocument();
    expect(api.saveBmPlan).not.toHaveBeenCalled();
    expect(api.startBusinessModel).not.toHaveBeenCalled();
  });

  it('⭐ 「돌아가서 채우기」를 누르면 실행이 안 간다', async () => {
    renderPage();
    await screen.findByText('운영 정보 확인');
    fireEvent.click(screen.getByRole('button', { name: '저장하고 캔버스 만들기' }));
    await screen.findByText('비어 있는 칸이 있습니다');

    fireEvent.click(screen.getByRole('button', { name: '돌아가서 채우기' }));

    await waitFor(() =>
      expect(screen.queryByText('비어 있는 칸이 있습니다')).not.toBeInTheDocument());
    expect(api.startBusinessModel).not.toHaveBeenCalled();
  });

  it('확인 문구가 어느 칸이 빌지 이름으로 말한다', async () => {
    renderPage();
    await screen.findByText('운영 정보 확인');
    fireEvent.change(openOperation('고객 관계 유지 방식'),
      { target: { value: '자동 알림' } });
    fireEvent.click(screen.getByRole('button', { name: '저장하고 캔버스 만들기' }));

    const dialog = await screen.findByRole('dialog');
    expect(dialog.textContent).toContain('핵심 활동');
    expect(dialog.textContent).not.toContain('고객 관계,');
  });

  it('「이대로 진행」이면 저장한 뒤 실행한다 — 저장 없이 돌지 않는다', async () => {
    renderPage();
    await screen.findByText('운영 정보 확인');
    fireEvent.click(screen.getByRole('button', { name: '저장하고 캔버스 만들기' }));
    await screen.findByText('비어 있는 칸이 있습니다');

    fireEvent.click(screen.getByRole('button', { name: '이대로 진행' }));

    await waitFor(() => expect(api.startBusinessModel).toHaveBeenCalled());
    expect(api.saveBmPlan).toHaveBeenCalledBefore(api.startBusinessModel);
  });

  it('전부 채우면 확인 없이 바로 간다', async () => {
    api.currentBmPlan.mockResolvedValue({
      plan: {
        customer_relationship: '자동 알림',
        key_activities: ['예약 통합'],
        key_resources: ['결제 연동'],
        key_partners: ['PG'],
      },
      constraints: { budget_krw: 5000000 },
      revision: 0,
    });
    renderPage();
    await screen.findByText('운영 정보 확인');

    fireEvent.click(screen.getByRole('button', { name: '저장하고 캔버스 만들기' }));

    await waitFor(() => expect(api.startBusinessModel).toHaveBeenCalled());
    expect(screen.queryByText('비어 있는 칸이 있습니다')).not.toBeInTheDocument();
  });

  it('⭐ 저장한 값이 실제로 실려 간다 — 빈 칸은 빠진 채로', async () => {
    renderPage();
    await screen.findByText('운영 정보 확인');
    fireEvent.change(openOperation('필요한 파트너'),
      { target: { value: 'PG\n예약 플랫폼' } });
    fireEvent.change(screen.getByLabelText(/사용 가능한 예산/), { target: { value: '5000000' } });

    fireEvent.click(screen.getByRole('button', { name: '저장하고 캔버스 만들기' }));
    fireEvent.click(await screen.findByRole('button', { name: '이대로 진행' }));

    await waitFor(() => expect(api.saveBmPlan).toHaveBeenCalled());
    const [plan, constraints] = api.saveBmPlan.mock.calls[0];
    expect(plan).toEqual({ key_partners: ['PG', '예약 플랫폼'] });
    expect(constraints).toEqual({ budget_krw: 5000000 });
  });

  it('⭐ 캔버스가 이미 있어도 계획을 고칠 수 있다 — 없으면 빈 칸이 영영 빈다', async () => {
    // 실측: 처음엔 `!result` 로만 갈라서, 한 번 돌린 프로젝트는 계획 화면에 못 들어갔다.
    // 계획을 낼 길이 없으니 계획 칸도 그대로였고 사용자가 그 상태를 봤다.
    api.currentBusinessModel.mockResolvedValue({
      run: { state: 'SUCCEEDED' },
      version: { result: { mode: 'BM', canvas: { cells: [] }, bm: null, evidence: [] } },
    });
    renderPage();

    const edit = await screen.findByRole('button', { name: '운영 정보 수정' });
    fireEvent.click(edit);

    expect(await screen.findByText('운영 정보 확인')).toBeInTheDocument();
    expect(screen.getByText('고객 관계 유지 방식')).toBeInTheDocument();
    // 고치러 들어왔다가 그냥 돌아갈 수도 있어야 한다.
    expect(screen.getByRole('button', { name: '지금 캔버스 보기' })).toBeInTheDocument();
  });

  it('저장분이 폼으로 돌아온다 — 다시 오면 친 것이 남아 있어야 한다', async () => {
    api.currentBmPlan.mockResolvedValue({
      plan: { key_activities: ['예약 통합', '보증금 청구'] },
      constraints: { months: 10 },
      revision: 2,
    });
    renderPage();
    expect(await screen.findByText('운영 정보 준비 완료')).toBeInTheDocument();
    expect(screen.queryByLabelText(/사업 운영에서 반복적으로 해야 하는 일/)).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '준비 정보 보기·수정' }));
    await screen.findByText('운영 정보 확인');

    expect(openOperation('사업 운영에서 반복적으로 해야 하는 일').value)
      .toBe('예약 통합\n보증금 청구');
    expect(screen.getByLabelText(/기간/).value).toBe('10');
  });
});
