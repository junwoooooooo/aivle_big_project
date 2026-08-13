import { render } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

import IdeaIntakeForm from './IdeaIntakeForm.jsx';

const emptyIntake = {
  ideaOverview: '', problem: '', targetUsers: '', targetRegion: '', knownCompetitors: '',
  revenueModel: '', price: '', channels: '', differentiators: '', budgetConstraint: '',
  teamConstraint: '', timelineConstraint: '', otherConstraint: '',
};

describe('IdeaIntakeForm 공통 폼 적용', () => {
  it('필수·선택 입력과 파일 업로드를 공통 구조로 렌더링한다', () => {
    const { container } = render(<IdeaIntakeForm
      draft={{ intake: emptyIntake, referenceFiles: [] }} errors={{}}
      onChange={vi.fn()} onFilesChange={vi.fn()} onSubmit={vi.fn()}
    />);

    expect(container.querySelectorAll('.project-form-row')).toHaveLength(13);
    expect(container.querySelectorAll('.project-form-layout').length).toBeGreaterThanOrEqual(3);
    expect(container.querySelector('.project-file-dropzone input[type="file"]')).toHaveAttribute('multiple');
  });
});
