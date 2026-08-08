import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

import MissingRequiredFieldsForm from './MissingRequiredFieldsForm.jsx';

describe('MissingRequiredFieldsForm', () => {
  it('renders catalog labels and submits manual field values', () => {
    const onChange = vi.fn();
    const onSubmit = vi.fn((event) => event.preventDefault());
    render(<MissingRequiredFieldsForm
      fieldKeys={['problem', 'targetUsers']}
      catalog={[
        { key: 'problem', label: '해결하려는 문제' },
        { key: 'targetUsers', label: '예상 사용자' },
      ]}
      fields={{ problem: { value: '' }, targetUsers: { value: '' } }}
      errors={{}} onChange={onChange} onSubmit={onSubmit} />);

    fireEvent.change(screen.getByLabelText('해결하려는 문제'), { target: { value: '폐기 문제' } });
    fireEvent.click(screen.getByRole('button', { name: '누락 정보 반영하고 다시 정리하기' }));

    expect(onChange).toHaveBeenCalledWith('problem', '폐기 문제');
    expect(onSubmit).toHaveBeenCalled();
    expect(screen.queryByText('답변을 Brief에 반영하기')).not.toBeInTheDocument();
  });
});
