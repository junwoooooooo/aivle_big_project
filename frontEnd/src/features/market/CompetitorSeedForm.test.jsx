import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import CompetitorSeedForm from './CompetitorSeedForm.jsx';

describe('CompetitorSeedForm', () => {
  it('저장된 씨앗을 불러오고 수정값을 서버에 보낸다', async () => {
    const api = {
      currentCompetitorSeeds: vi.fn().mockResolvedValue({ seeds: [{ name: '공비서', reason: '노쇼 방지', operatorName: '' }] }),
      saveCompetitorSeeds: vi.fn().mockResolvedValue({ seeds: [{ name: '새 경쟁사', reason: '노쇼 방지', operatorName: '' }] }),
    };
    render(<CompetitorSeedForm api={api} />);
    const name = await screen.findByDisplayValue('공비서');
    fireEvent.change(name, { target: { value: '새 경쟁사' } });
    fireEvent.click(screen.getByRole('button', { name: '저장' }));
    await waitFor(() => expect(api.saveCompetitorSeeds).toHaveBeenCalledWith([
      { name: '새 경쟁사', reason: '노쇼 방지', operatorName: '' },
    ]));
    expect(await screen.findByText('저장했다.')).toBeInTheDocument();
  });

  it('서버 경고와 저장 오류를 사용자에게 표시한다', async () => {
    const api = {
      currentCompetitorSeeds: vi.fn().mockResolvedValue({ seeds: [], warning: '씨앗 없이 업종 기준으로 조사한다.' }),
      saveCompetitorSeeds: vi.fn().mockRejectedValue(new Error('중복 이름')),
    };
    render(<CompetitorSeedForm api={api} />);
    expect(await screen.findByText('씨앗 없이 업종 기준으로 조사한다.')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '저장' }));
    expect(await screen.findByText('중복 이름')).toBeInTheDocument();
  });
});
