import { describe, expect, it } from 'vitest';

import { businessProposalExecutionPresentation, businessProposalSummaryMetric } from './businessProposalExecution.js';

describe('사업안 실행 macro progress', () => {
  it.each([
    ['job.concept-portfolio.trace.conditions', 'CONDITION'],
    ['job.concept-portfolio.trace.directions', 'EXPLORE'],
    ['job.concept-portfolio.trace.proposals', 'EXPLORE'],
    ['job.concept-portfolio.trace.excluded-duplicate', 'EXPLORE'],
    ['job.concept-portfolio.trace.legal-started', 'LEGAL'],
    ['job.concept-portfolio.materializing', 'FINALIZE'],
  ])('%s를 %s 단계로 표시한다', (messageKey, expected) => {
    expect(businessProposalExecutionPresentation({ productStatus: 'RUNNING' }, [{ messageKey }]).currentPhaseId).toBe(expected);
  });

  it('법률 단계 뒤 proposal trace가 와도 탐색 단계로 후퇴하지 않는다', () => {
    const events = ['directions', 'proposal-generated', 'legal-started', 'proposals']
      .map((name) => ({ messageKey: `job.concept-portfolio.trace.${name}` }));
    expect(businessProposalExecutionPresentation({ productStatus: 'RUNNING' }, events).currentPhaseId).toBe('LEGAL');
  });

  it('unknown stage와 terminal 상태를 사실대로 표시한다', () => {
    const unknown = businessProposalExecutionPresentation({ productStatus: 'RUNNING' }, [{ messageKey: 'job.concept-portfolio.future' }]);
    expect(unknown.activity).toBe('결과를 준비하고 있습니다.');
    expect(businessProposalExecutionPresentation({ productStatus: 'FAILED' }, []).state).toBe('FAILED');
    expect(businessProposalExecutionPresentation({ productStatus: 'NEEDS_INPUT' }, []).state).toBe('NEEDS_INPUT');
  });

  it('summary messageKey의 실제 수만 metric으로 표시한다', () => {
    expect(businessProposalSummaryMetric({}, [{ messageKey: 'job.concept-portfolio.summary', messageParams: { reviewed: 5, prepared: 1, needsInput: 2 } }]))
      .toBe('5개 검토 · 1개 준비 · 2개 추가 확인');
  });

  it('초기 0개·0건은 metric으로 노출하지 않는다', () => {
    expect(businessProposalSummaryMetric({ productStatus: 'RUNNING', producedConceptCount: 0, openInputCount: 0 }, [])).toBeNull();
  });
});
