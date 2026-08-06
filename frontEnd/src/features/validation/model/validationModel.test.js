import { describe, expect, it } from 'vitest';

import { DEFAULT_QUESTIONS, groupAnswersByPersona, scoreLabel } from './validationModel.js';

describe('validationModel', () => {
  it('provides three default questions per interview purpose', () => {
    Object.values(DEFAULT_QUESTIONS).forEach((questions) => expect(questions).toHaveLength(3));
  });

  it('maps relative scores to textual labels', () => {
    expect(scoreLabel(40)).toBe('낮음');
    expect(scoreLabel(50)).toBe('보통');
    expect(scoreLabel(70)).toBe('높음');
    expect(scoreLabel(85)).toBe('매우 높음');
  });

  it('groups expected answers without losing their order', () => {
    const grouped = groupAnswersByPersona([
      { personaName: 'A', questionOrder: 1 },
      { personaName: 'B', questionOrder: 1 },
      { personaName: 'A', questionOrder: 2 },
    ]);
    expect(grouped.A.map((item) => item.questionOrder)).toEqual([1, 2]);
  });
});
