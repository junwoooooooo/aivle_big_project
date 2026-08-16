import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { ApiError } from '../../../shared/api/apiError.js';
import { FinanceInputError } from './LaunchReadinessPage.jsx';

describe('V21.1 Finance 문서 오류 UI', () => {
  it('안전한 제목과 필드별 한국어 오류를 함께 표시한다', () => {
    render(<FinanceInputError error={new ApiError({
      status: 422,
      code: 'FINANCIAL_INPUT_INVALID',
      fieldErrors: [
        { field: 'threeYearTargets', message: '1·2·3년차 값을 확인해 주세요.' },
        { field: 'shippingCost', message: '숫자를 입력하거나 비워 주세요.' },
      ],
    })} />);
    expect(screen.getByText('재무 입력 문서를 확인해 주세요.')).toBeInTheDocument();
    expect(screen.getByText('3개년 성장 목표')).toBeInTheDocument();
    expect(screen.getByText('1·2·3년차 값을 확인해 주세요.')).toBeInTheDocument();
    expect(screen.getByText('배송비')).toBeInTheDocument();
  });
});
