import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { formatKrwNarrative } from '../model/financeNarrativeFormat.js';
import AnalysisReport from './AnalysisReport.jsx';

const point = (month, revenue, operatingProfit, cumulativeCashFlow) => ({ month, revenue, operatingProfit, cumulativeCashFlow });
const result = {
  calculation: { scenarios: [{ code: 'BASE', totalRevenue: 36000000, totalOperatingProfit: 9000000,
    requiredWorkingCapital: 4000000, breakEvenMonth: 8 }] },
  annualProjections: [{ year: 1, revenue: 100, variableCost: 20, grossProfit: 80,
    sellingGeneralAdministrative: 30, operatingProfit: 50, nonOperatingIncome: 0,
    corporateTax: 10, netIncome: 40, operatingMarginPercent: 50 }],
  cashFlowChart: [point(1, 100, -10, -30), point(2, 120, 20, 10)],
  stressScenarios: [{ code: 'CONSERVATIVE', label: '보수', breakEvenMonth: null,
    totalOperatingProfit: -100, requiredWorkingCapital: 500, monthlyCashFlow: [point(1, 80, -20, -40)] }],
  monteCarlo: { simulations: 1000, seed: 20260810, profitP10: -10, profitP50: 50,
    profitP90: 120, lossProbabilityPercent: 18, paybackProbabilityPercent: 64 },
  report: { headline: '기준 시나리오는 타당성을 보입니다.', findings: ['매출 근거 확인'],
    cautions: ['손실 확률 주의'], recommendedActions: ['가격 검증'], disclaimer: '추정치입니다.',
    source: 'AI_GENERATED_REPORT', providerStatus: 'SUCCEEDED', safeFailureReason: null },
};

describe('AnalysisReport', () => {
  it.each([
    ['0원', '0원'],
    ['1000원', '1,000원'],
    ['10000원', '10,000원 (1만원)'],
    ['100000000원', '100,000,000원 (1억원)'],
    ['153200000원', '153,200,000원 (1억 5,320만원)'],
    ['1234567890 KRW', '1,234,567,890원 (12억 3,456만 7,890원)'],
    ['-153200000 KRW', '-153,200,000원 (-1억 5,320만원)'],
    ['매출 10000원, 비용 1,000 KRW', '매출 10,000원 (1만원), 비용 1,000원'],
    ['153200000원이며 손실 확률은 18%', '153,200,000원 (1억 5,320만원)이며 손실 확률은 18%'],
    ['2026-08-17, 12개월, 80명, ID 20260810, 비율 0.18', '2026-08-17, 12개월, 80명, ID 20260810, 비율 0.18'],
  ])('명시적인 원/KRW 금액만 표시 형식으로 변환한다: %s', (source, expected) => {
    expect(formatKrwNarrative(source)).toBe(expected);
  });

  it('findings, cautions, recommendedActions의 금액을 모두 presentation에서 변환한다', () => {
    const narrativeResult = { ...result, report: { ...result.report,
      findings: ['매출 153200000원'],
      cautions: ['비용 -100000000 KRW와 손실 확률 18%'],
      recommendedActions: ['예산 1234567890 KRW를 검토'] } };
    render(<AnalysisReport analysis={{ result: narrativeResult, fallback: false }} />);
    expect(screen.getByText('매출 153,200,000원 (1억 5,320만원)')).toBeInTheDocument();
    expect(screen.getByText('비용 -100,000,000원 (-1억원)와 손실 확률 18%')).toBeInTheDocument();
    expect(screen.getByText('예산 1,234,567,890원 (12억 3,456만 7,890원)를 검토')).toBeInTheDocument();
  });

  it('donor의 계산·표·차트·위험·근거·주의·액션 정보를 보존한다', () => {
    render(<AnalysisReport analysis={{ result, fallback: false }} />);
    expect(screen.getByText('36개월 누적 매출')).toBeInTheDocument();
    expect(screen.getByText('필요 운전자금')).toBeInTheDocument();
    expect(screen.getAllByRole('table').length).toBeGreaterThanOrEqual(1);
    expect(screen.getByRole('img', { name: '월별 매출 및 영업이익' })).toBeInTheDocument();
    expect(screen.getByText(/재현 기준 20260810/)).toBeInTheDocument();
    expect(screen.getByText('손실 확률 주의')).toBeInTheDocument();
    expect(screen.getByText('가격 검증')).toBeInTheDocument();
    expect(screen.getByText(/AI_GENERATED_REPORT/)).toBeInTheDocument();
  });

  it('Provider 실패를 성공으로 가장하지 않고 fallback 출처와 안전 사유를 표시한다', () => {
    const fallback = { ...result, report: { ...result.report, source: 'SYSTEM_CALCULATION_FALLBACK',
      providerStatus: 'FAILED', safeFailureReason: 'AI_SERVICE_UNAVAILABLE' } };
    render(<AnalysisReport analysis={{ result: fallback, fallback: true }} />);
    expect(screen.getByText(/결정론 계산 Fallback/)).toBeInTheDocument();
    expect(screen.getByText(/AI_SERVICE_UNAVAILABLE/)).toBeInTheDocument();
  });
});
