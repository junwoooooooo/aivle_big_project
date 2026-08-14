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
    expect(screen.getByRole('button', { name: '입력 내용으로 아이디어 정리하기' })).toBeInTheDocument();
  });

  it('정리 요청 실패를 입력 화면에서 알리고 수정값을 유지한다', () => {
    render(<IdeaIntakeForm draft={{ intake: { ...emptyIntake, ideaOverview: '수정 아이디어' }, referenceFiles: [] }} errors={{}}
      submissionError="요청을 시작하지 못했습니다." onChange={vi.fn()} onFilesChange={vi.fn()} onSubmit={vi.fn()} />);
    expect(screen.getByRole('alert')).toHaveTextContent('요청을 시작하지 못했습니다.');
    expect(screen.getByRole('textbox', { name: /아이디어 개요/ })).toHaveValue('수정 아이디어');
  });

  it('정리 요청 중에는 submit을 잠그고 진행 문구를 표시한다', () => {
    render(<IdeaIntakeForm draft={{ intake: emptyIntake, referenceFiles: [] }} errors={{}} organizing
      onChange={vi.fn()} onFilesChange={vi.fn()} onSubmit={vi.fn()} />);
    const button = screen.getByRole('button', { name: /아이디어를 정리하고 있습니다/ });
    expect(button).toBeDisabled();
    expect(button).toHaveAttribute('aria-busy', 'true');
  });
});
