import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import {
  FinanceModule,
  ProfessionalModule,
  ReportDownload,
  ReportPreviewDialog,
} from './LaunchReadinessPage.jsx';

vi.mock('../../../shared/async-events/index.js', () => ({
  useJobEvents: () => ({ events: [], terminal: false }),
}));

const professionalCurrent = (moduleType = 'TECHNOLOGY', passed = true) => ({
  moduleType,
  status: 'SUCCEEDED',
  sourceDocumentName: `${moduleType.toLowerCase()}.docx`,
  taskRunId: 'internal-task-run', inputSnapshotHash: 'sha256:internal-input', resultId: 'internal-result',
  professionalInput: moduleType === 'TECHNOLOGY'
    ? { systemArchitecture: '웹·API·DB 3계층', testPlan: '부하·복구 테스트' }
    : { operatingProcess: '주문부터 정산까지 표준 운영', customerSupport: '채팅 상담' },
  analysis: {
    decision: 'CONDITIONAL', score: 82, summary: '출시 전 확인 조건을 보완하면 진행할 수 있습니다.',
    dimensions: [{ name: '구조', score: 82, status: 'READY', finding: '핵심 구조가 정의되어 있습니다.' }],
    risks: [{ title: '복구 기준', severity: 'MEDIUM', impact: '복구 지연', mitigation: '복구 훈련' }],
    gates: [{ title: '출시 기준', status: 'OPEN', criterion: '검증 완료', evidenceNeeded: '검증 결과' }],
    actions: [{ priority: 'P0', title: '검증 결과 확인', owner: '담당자', completionEvidence: '검증 문서' }],
  },
  quality: { passed, reviewScore: 94 },
  externalEvidence: [{ title: 'OWASP ASVS', url: 'https://owasp.org/' }],
  current: true,
  stale: false,
});

const financeCurrent = {
  status: 'SUCCEEDED', stale: false, fallback: false,
  result: {
    calculation: { scenarios: [{ code: 'BASE', totalRevenue: 36000000, totalOperatingProfit: 9000000,
      requiredWorkingCapital: 4000000, breakEvenMonth: 8 }] },
    annualProjections: [{ year: 1, revenue: 100, variableCost: 20, grossProfit: 80,
      sellingGeneralAdministrative: 30, operatingProfit: 50, nonOperatingIncome: 0,
      corporateTax: 10, netIncome: 40, operatingMarginPercent: 50 }],
    cashFlowChart: [{ month: 1, revenue: 100, operatingProfit: -10, cumulativeCashFlow: -30 }],
    stressScenarios: [{ code: 'CONSERVATIVE', label: '보수', breakEvenMonth: null,
      totalOperatingProfit: -100, requiredWorkingCapital: 500 }],
    monteCarlo: { simulations: 1000, profitP10: -10, profitP50: 50, profitP90: 120,
      lossProbabilityPercent: 18, paybackProbabilityPercent: 64 },
    report: { headline: '기준 시나리오는 타당성을 보입니다.', findings: ['매출 근거 확인'],
      cautions: ['손실 확률 주의'], recommendedActions: ['가격 검증'], disclaimer: '추정치입니다.' },
  },
};

describe('V21.3 JSON 보고서 미리보기', () => {
  beforeEach(() => vi.restoreAllMocks());

  it('Technology 미리보기는 PDF endpoint를 호출하지 않고 현재 결과를 즉시 연다', async () => {
    const api = { professionalCurrent: vi.fn().mockResolvedValue(professionalCurrent()), downloadProfessionalReport: vi.fn() };
    const onPreview = vi.fn();
    render(<ProfessionalModule module="technology" api={api} projectId="7" onReady={vi.fn()}
      onDetail={vi.fn()} onPreview={onPreview} />);
    fireEvent.click(await screen.findByRole('button', { name: '보고서 미리보기' }));
    expect(api.downloadProfessionalReport).not.toHaveBeenCalled();
    expect(onPreview).toHaveBeenCalledTimes(1);
    expect(onPreview.mock.calls[0][0].documents[0].current.analysis.score).toBe(82);
  });

  it('Operations 미리보기 역시 PDF endpoint를 호출하지 않는다', async () => {
    const api = { professionalCurrent: vi.fn().mockResolvedValue(professionalCurrent('OPERATIONS')), downloadProfessionalReport: vi.fn() };
    const onPreview = vi.fn();
    render(<ProfessionalModule module="operations" api={api} projectId="7" onReady={vi.fn()}
      onDetail={vi.fn()} onPreview={onPreview} />);
    fireEvent.click(await screen.findByRole('button', { name: '보고서 미리보기' }));
    expect(api.downloadProfessionalReport).not.toHaveBeenCalled();
    expect(onPreview.mock.calls[0][0].documents[0].module).toBe('operations');
  });

  it('Finance 미리보기는 PDF endpoint 없이 authoritative result를 전달한다', async () => {
    const api = { financeCurrent: vi.fn().mockResolvedValue(financeCurrent), downloadFinanceReport: vi.fn() };
    const onPreview = vi.fn();
    render(<FinanceModule api={api} projectId="7" onReady={vi.fn()} onDetail={vi.fn()} onPreview={onPreview} />);
    fireEvent.click(await screen.findByRole('button', { name: '보고서 미리보기' }));
    expect(api.downloadFinanceReport).not.toHaveBeenCalled();
    expect(onPreview.mock.calls[0][0].documents[0].current.result.report.headline)
      .toBe('기준 시나리오는 타당성을 보입니다.');
  });

  it('통합 미리보기는 세 current document를 순서대로 열고 bundle endpoint를 호출하지 않는다', () => {
    const api = { downloadReports: vi.fn() }; const onPreview = vi.fn();
    const reports = { technology: professionalCurrent(), operations: professionalCurrent('OPERATIONS'), finance: financeCurrent };
    render(<ReportDownload api={api} projectId="7" reports={reports} onPreview={onPreview} />);
    fireEvent.click(screen.getByText('기술 분석 보고서'));
    fireEvent.click(screen.getByText('운영 분석 보고서'));
    fireEvent.click(screen.getByText('재무 분석 보고서'));
    fireEvent.click(screen.getByRole('button', { name: '3개 통합 보고서 미리보기' }));
    expect(api.downloadReports).not.toHaveBeenCalled();
    expect(onPreview.mock.calls[0][0].documents.map((item) => item.module))
      .toEqual(['technology', 'operations', 'finance']);
  });

  it('Dialog는 AI 점수 의미를 설명하고 PDF 요청은 다운로드 버튼에서만 1회 수행한다', async () => {
    const loadPdf = vi.fn().mockResolvedValue(new Blob(['%PDF-', 'x'.repeat(80)], { type: 'application/octet-stream' }));
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});
    vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:report');
    vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => {});
    render(<ReportPreviewDialog preview={{ title: '기술 출시 준비도 보고서', filename: 'technology.pdf',
      documents: [{ module: 'technology', current: professionalCurrent() }], loadPdf }} onClose={vi.fn()} />);
    expect(screen.getByText('AI 출시 준비도 평가 82점')).toBeInTheDocument();
    expect(screen.getByText(/정해진 재무 산식처럼 계산된 점수는 아닙니다/)).toBeInTheDocument();
    expect(screen.getByText('독립 AI 검증 통과')).toBeInTheDocument();
    expect(screen.queryByText('94')).not.toBeInTheDocument();
    expect(screen.queryByText(/internal-task-run|internal-input|internal-result/)).not.toBeInTheDocument();
    expect(loadPdf).not.toHaveBeenCalled();
    expect(click).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole('button', { name: 'PDF 다운로드' }));
    await waitFor(() => expect(loadPdf).toHaveBeenCalledTimes(1));
    await waitFor(() => expect(click).toHaveBeenCalledTimes(1));
  });

  it('품질 검증이 통과하지 않은 결과에는 독립 AI 검증 통과를 표시하지 않는다', () => {
    render(<ReportPreviewDialog preview={{ title: '운영 보고서', filename: 'operations.pdf',
      documents: [{ module: 'operations', current: { ...professionalCurrent('OPERATIONS', false), stale: true } }], loadPdf: vi.fn() }} onClose={vi.fn()} />);
    expect(screen.queryByText('독립 AI 검증 통과')).not.toBeInTheDocument();
    expect(screen.getByText('이전 입력 기준 결과입니다.')).toBeInTheDocument();
  });

  it('Finance preview는 계산 결과·3개년·월별·스트레스·Monte Carlo·AI 해석을 표시한다', () => {
    render(<ReportPreviewDialog preview={{ title: '재무 분석 보고서', filename: 'finance.pdf',
      documents: [{ module: 'finance', current: financeCurrent }], loadPdf: vi.fn() }} onClose={vi.fn()} />);
    expect(screen.getByText('핵심 결과')).toBeInTheDocument();
    expect(screen.getByText('3개년 추정')).toBeInTheDocument();
    expect(screen.getByText('월별 주요 지표')).toBeInTheDocument();
    expect(screen.getByText('스트레스 시나리오와 Monte Carlo')).toBeInTheDocument();
    expect(screen.getByText('AI 해석과 권장 조치')).toBeInTheDocument();
  });
});
