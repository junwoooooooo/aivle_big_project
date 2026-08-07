import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

import MissingRequiredFieldsForm from './MissingRequiredFieldsForm.jsx';

describe('MissingRequiredFieldsForm', () => {
  it('renders catalog labels and submits manual field values', () => {
    const onChange = vi.fn();
    const onSubmit = vi.fn((event) => event.preventDefault());
    render(<MissingRequiredFieldsForm
      fieldKeys={['physicalActivity', 'personalData']}
      catalog={[
        { key: 'physicalActivity', label: '물리 활동' },
        { key: 'personalData', label: '개인정보' },
      ]}
      fields={{ physicalActivity: { value: '' }, personalData: { value: '' } }}
      errors={{}} onChange={onChange} onSubmit={onSubmit} />);

    fireEvent.change(screen.getByLabelText('물리 활동'), { target: { value: '활동 없음' } });
    fireEvent.click(screen.getByRole('button', { name: '누락 정보 반영하고 다시 정리하기' }));

    expect(onChange).toHaveBeenCalledWith('physicalActivity', '활동 없음');
    expect(onSubmit).toHaveBeenCalled();
    expect(screen.queryByText('답변을 Brief에 반영하기')).not.toBeInTheDocument();
  });
});
