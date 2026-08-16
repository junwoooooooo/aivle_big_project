import { describe, expect, it } from 'vitest';
import { canonicalizeReportModules, formatKrwAmount } from './reportDocumentPresentation.js';

describe('V21.5 보고서 표시 model', () => {
  it.each([
    [0, '0 KRW', '0원'],
    [5_000_000, '5,000,000 KRW', '5백만 원'],
    [50_000_000, '50,000,000 KRW', '5천만 원'],
    [120_000_000, '120,000,000 KRW', '1억 2천만 원'],
    [325_000_000, '325,000,000 KRW', '3억 2,500만 원'],
    [1_250_000_000, '1,250,000,000 KRW', '12억 5천만 원'],
    [-120_000_000, '-120,000,000 KRW', '-1억 2천만 원'],
  ])('%s원을 raw와 한국식 단위로 함께 표시한다', (value, raw, readable) => {
    expect(formatKrwAmount(value)).toEqual({ raw, readable });
  });

  it.each([
    [['finance', 'technology'], ['technology', 'finance']],
    [['operations', 'finance', 'technology'], ['technology', 'operations', 'finance']],
    [['finance', 'finance', 'unknown', 'technology'], ['technology', 'finance']],
  ])('통합 보고서 순서를 canonical order로 고정한다', (input, output) => {
    expect(canonicalizeReportModules(input)).toEqual(output);
  });
});
