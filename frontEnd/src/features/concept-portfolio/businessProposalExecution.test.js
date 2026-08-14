import { describe, expect, it } from 'vitest';

import { businessProposalExecutionPresentation } from './businessProposalExecution.js';

describe('business proposal execution presentation', () => {
  it.each([
    ['job.concept-portfolio.trace.directions', 'DIRECTION'],
    ['job.concept-portfolio.trace.proposals', 'GENERATE'],
    ['job.concept-portfolio.trace.legal-started', 'LEGAL'],
    ['job.concept-portfolio.trace.excluded-duplicate', 'DISTINCTNESS'],
    ['job.concept-portfolio.materializing', 'READY'],
  ])('%s를 %s 단계로 표시한다', (messageKey, expected) => {
    expect(businessProposalExecutionPresentation({ productStatus: 'RUNNING' }, [{ messageKey }]).currentPhaseId).toBe(expected);
  });

  it('unknown stage와 terminal 상태를 사실대로 표시한다', () => {
    const unknown = businessProposalExecutionPresentation({ productStatus: 'RUNNING' }, [{ messageKey: 'job.concept-portfolio.future' }]);
    expect(unknown.activity).toBe('결과를 준비하고 있습니다.');
    expect(businessProposalExecutionPresentation({ productStatus: 'FAILED' }, []).state).toBe('FAILED');
    expect(businessProposalExecutionPresentation({ productStatus: 'NEEDS_INPUT' }, []).state).toBe('NEEDS_INPUT');
  });
});
