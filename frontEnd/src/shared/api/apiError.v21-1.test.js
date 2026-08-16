import { describe, expect, it } from 'vitest';
import { ApiError, getUserErrorMessage } from './apiError.js';

describe('V21.1 launch readiness 사용자 오류', () => {
  it('FINANCIAL_INPUT_INVALID를 generic 문구로 가리지 않는다', () => {
    const error = new ApiError({
      status: 422,
      code: 'FINANCIAL_INPUT_INVALID',
      message: '문서의 숫자와 입력 형식을 확인해 주세요.',
      fieldErrors: [{ field: 'threeYearTargets', message: '1·2·3년차 값을 확인해 주세요.' }],
    });
    expect(getUserErrorMessage(error)).toBe('재무 입력 문서를 확인해 주세요.');
    expect(error.fieldErrors).toHaveLength(1);
  });
});
