import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

import IdeaIntakeForm from './IdeaIntakeForm.jsx';

const emptyIntake = {
  ideaOverview: '', problem: '', targetUsers: '', targetRegion: '', knownCompetitors: '',
  revenueModel: '', price: '', channels: '', differentiators: '', budgetConstraint: '',
  teamConstraint: '', timelineConstraint: '', otherConstraint: '',
};

describe('IdeaIntakeForm 공통 폼 적용', () => {
  it('필수·선택 입력과 파일 업로드를 공통 구조로 렌더링한다', () => {
    const onChange = vi.fn();
    const { container } = render(<IdeaIntakeForm
      draft={{ intake: emptyIntake, referenceFiles: [] }} errors={{}}
      onChange={onChange} onFilesChange={vi.fn()} onSubmit={vi.fn()}
    />);

    expect(container.querySelector('.project-split-workspace')).toBeInTheDocument();
    expect(container.querySelectorAll('.project-form-row')).toHaveLength(3);
    expect(screen.getAllByRole('button', { expanded: false })).toHaveLength(10);
    expect(screen.getByText('0 / 10 입력')).toBeInTheDocument();
    expect(container.querySelector('details')).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: /대상 지역/ }));
    expect(screen.getByRole('button', { name: /대상 지역/ })).toHaveAttribute('aria-expanded', 'true');
    fireEvent.change(screen.getByLabelText('대상 지역'), { target: { value: '서울' } });
    expect(onChange).toHaveBeenCalledWith('targetRegion', '서울');
    expect(container.querySelector('.project-file-dropzone input[type="file"]')).toHaveAttribute('multiple');
    expect(screen.getByRole('button', { name: '입력 내용으로 사업안 만들기' })).toBeInTheDocument();
  });
});
