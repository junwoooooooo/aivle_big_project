import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { MarketProgress } from './MarketResearchPage.jsx';
import { marketRunFailureMessage } from './marketRuntime.js';

describe('MarketProgress', () => {
  it('shows the latest safe Market heartbeat on the active page', () => {
    render(<MarketProgress events={[
      { sequence: 1, messageKey: 'job.market.trace', messageParams: { traceDetail: '수집 시작' } },
      { sequence: 2, messageKey: 'job.market.trace',
        messageParams: { traceDetail: '시장 근거 수집을 계속 진행하고 있습니다.' } },
    ]} />);

    expect(screen.getByText('시장 근거 수집을 계속 진행하고 있습니다.')).toBeInTheDocument();
    expect(screen.queryByText('수집 시작')).not.toBeInTheDocument();
  });

  it('does not expose filtered internal trace text', () => {
    const { container } = render(<MarketProgress events={[
      { sequence: 1, messageKey: 'job.market.trace',
        messageParams: { traceDetail: 'Candidate lineage hash checked' } },
    ]} />);
    expect(container).toBeEmptyDOMElement();
  });

  it('keeps timeout, dependency, input, result and execution failures distinct', () => {
    expect(marketRunFailureMessage('DEADLINE_EXCEEDED')).toContain('시간');
    expect(marketRunFailureMessage('DEPENDENCY_UNAVAILABLE')).toContain('연결');
    expect(marketRunFailureMessage('INVALID_REQUEST')).toContain('입력');
    expect(marketRunFailureMessage('RESULT_SCHEMA_INVALID')).toContain('계약');
    expect(marketRunFailureMessage('EXECUTION_FAILED')).toContain('완료하지 못');
  });
});
