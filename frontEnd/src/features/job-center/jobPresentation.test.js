import { describe, expect, it } from 'vitest';
import { jobTaskLabel } from './jobPresentation.js';

describe('Work Center task labels', () => {
  it('never exposes raw V2 TaskType names', () => {
    expect(jobTaskLabel('CONCEPT_PORTFOLIO_V2_RUN')).toBe('사업안 검토');
    expect(jobTaskLabel('CONCEPT_PORTFOLIO_V2_CONTINUE')).toBe('추가 사업정보 반영');
    expect(jobTaskLabel('CONCEPT_PORTFOLIO_V2_SELECTION_ACTION')).toBe('사업안 선택 후 검토');
  });
});
