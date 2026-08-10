import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

import { QUESTION_TYPE } from '../model/ideaIntakeModel.js';
import QuestionCard from './QuestionCard.jsx';

describe('QuestionCard', () => {
  it('uses keyboard-accessible native controls and returns the selected answer', () => {
    const onAnswer = vi.fn();
    render(<QuestionCard question={{ id: 'region', type: QUESTION_TYPE.SINGLE_SELECT, title: '서비스 지역은 어디인가요?', options: ['국내', '글로벌'] }} answer="" onAnswer={onAnswer} />);

    const domestic = screen.getByRole('radio', { name: '국내' });
    domestic.focus();
    expect(domestic).toHaveFocus();
    fireEvent.click(domestic);
    expect(onAnswer).toHaveBeenCalledWith('국내');
  });
});
