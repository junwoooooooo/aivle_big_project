import { describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';

import PairEditorDialog from './PairEditorDialog.jsx';

const pair = () => ({
  pairId: 'P1',
  X: { label: '신선', attrs: { 형태: '신선(냉장)' }, priceKrw: 4500 },
  Y: { label: '냉동', attrs: { 형태: '냉동' }, priceKrw: 4500 },
});

function open(props = {}) {
  return render(<PairEditorDialog open pair={pair()} onSave={() => {}} onClose={() => {}} {...props} />);
}

describe('PairEditorDialog — 한 번에 한 질문만', () => {
  it('닫혀 있으면 아무것도 그리지 않는다', () => {
    render(<PairEditorDialog open={false} pair={pair()} />);
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('축과 양쪽 값을 채운 채로 연다', () => {
    open();
    expect(screen.getByRole('dialog')).toBeInTheDocument();
    expect(screen.getByDisplayValue('형태')).toBeInTheDocument();
    expect(screen.getByLabelText('A안 값')).toHaveValue('신선(냉장)');
    expect(screen.getByLabelText('B안 값')).toHaveValue('냉동');
  });

  /**
   * 이전 편집기는 속성 이름이 고정이라 「형태」 말고 다른 축으로 물을 방법이 아예 없었다.
   * 축을 바꾸면 **양쪽 키가 함께** 바뀌어야 한다 — 갈리면 서버가 «같은 속성 공간이 아니다»로
   * 거절한다(`models.py` 의 `same_attribute_space`).
   */
  it('축 이름을 바꾸면 양쪽 키가 함께 바뀐다', () => {
    const onSave = vi.fn();
    open({ onSave });

    fireEvent.change(screen.getByLabelText('무엇을 비교하나'), { target: { value: '배송' } });
    fireEvent.click(screen.getByRole('button', { name: '저장' }));

    const saved = onSave.mock.calls[0][0];
    expect(Object.keys(saved.X.attrs)).toEqual(['배송']);
    expect(Object.keys(saved.Y.attrs)).toEqual(['배송']);
  });

  it('가격은 손대지 않고 그대로 흘려보낸다', () => {
    const onSave = vi.fn();
    open({ onSave });
    expect(screen.queryByLabelText(/가격/)).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: '저장' }));

    const saved = onSave.mock.calls[0][0];
    expect(saved.X.priceKrw).toBe(4500);
    expect(saved.Y.priceKrw).toBe(4500);
    expect(saved.pairId).toBe('P1');
  });

  it('고치는 동안 유형 판정을 그 자리에서 보여준다', () => {
    open();
    expect(screen.getByRole('status').textContent).toContain('명백한 우열형');

    fireEvent.change(screen.getByLabelText('무엇을 비교하나'), { target: { value: '친환경 인증' } });
    expect(screen.getByRole('status').textContent).toContain('윤리·가치형');
    expect(screen.getByRole('status').textContent).toContain('예측을 제공하지 않는다');
  });

  it('빈 칸이 있으면 저장할 수 없다', () => {
    open();
    fireEvent.change(screen.getByLabelText('A안 값'), { target: { value: '  ' } });
    expect(screen.getByRole('button', { name: '저장' })).toBeDisabled();
  });

  it('취소는 아무것도 올려 보내지 않는다', () => {
    const onSave = vi.fn();
    const onClose = vi.fn();
    open({ onSave, onClose });

    fireEvent.change(screen.getByLabelText('A안 값'), { target: { value: '바꿈' } });
    fireEvent.click(screen.getByRole('button', { name: '취소' }));

    expect(onSave).not.toHaveBeenCalled();
    expect(onClose).toHaveBeenCalled();
  });
});
