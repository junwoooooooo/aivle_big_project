import { fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';

import SelectionConfirmation from './SelectionConfirmation.jsx';

describe('SelectionConfirmation', () => {
  it('requires a reason and sends exactly one preferred concept for hypothesis decisions', async () => {
    const onConfirm = vi.fn().mockResolvedValue({ selectionId: 1 });
    render(<MemoryRouter><SelectionConfirmation preferred={{ conceptId: 'c-1', title: '첫 컨셉' }} currentSelection={null} marketHref="/market" onConfirm={onConfirm} /></MemoryRouter>);
    const button = screen.getByRole('button', { name: '이 컨셉 선택 확정' });
    expect(button).toBeDisabled();
    fireEvent.change(screen.getByLabelText('선택 이유'), { target: { value: '초기 실행 범위가 명확합니다.' } });
    fireEvent.click(button);
    expect(onConfirm).toHaveBeenCalledWith('c-1', '초기 실행 범위가 명확합니다.');
  });
});
