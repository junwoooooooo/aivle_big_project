import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { DiffBanner } from './DiffBanner.jsx';

describe('DiffBanner', () => {
  it('diff 요약과 재실행/승계 범주를 표시한다', () => {
    render(
      <DiffBanner
        diff={{ resolved: 1, added: 0, maintained: 5 }}
        rerunCategories={['ADVERTISING_AND_MARKETING']}
        carriedCategories={[
          'BUSINESS_REGISTRATION', 'LICENSE_AND_PERMIT', 'PRIVACY_AND_DATA',
          'TERMS_AND_CONTRACT', 'INTELLECTUAL_PROPERTY', 'CONSUMER_PROTECTION',
          'LABOR_AND_EMPLOYMENT', 'INDUSTRY_SPECIFIC', 'TAX_AND_FINANCIAL',
        ]}
      />,
    );
    expect(screen.getByText('해결 1 · 신규 0 · 유지 5')).toBeInTheDocument();
    expect(screen.getByText('광고·마케팅 재검토 · 9개 범주는 이전 결과 유지')).toBeInTheDocument();
  });

  it('diff가 없으면 아무것도 그리지 않는다', () => {
    const { container } = render(<DiffBanner diff={null} />);
    expect(container).toBeEmptyDOMElement();
  });
});
