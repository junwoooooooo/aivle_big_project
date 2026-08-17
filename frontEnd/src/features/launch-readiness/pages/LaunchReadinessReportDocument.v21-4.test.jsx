import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { FinanceReadinessReportDocument } from '../components/FinanceReadinessReportDocument.jsx';
import { IntegratedLaunchReadinessReportDocument } from '../components/IntegratedLaunchReadinessReportDocument.jsx';
import { LaunchReadinessReportDocument } from '../components/LaunchReadinessReportDocument.jsx';
import { launchReadinessReportTitle, printLaunchReadinessReport, reportModulesFromQuery } from '../model/reportDocumentPresentation.js';
import { FinanceModule, ProfessionalModule, ReportToolbar } from './LaunchReadinessPage.jsx';
import { LaunchReadinessReportPageView } from './LaunchReadinessReportPage.jsx';

vi.mock('../../../shared/async-events/index.js', () => ({ useJobEvents: () => ({ events: [], terminal: false }) }));

const professionalCurrent = (moduleType = 'TECHNOLOGY', passed = true) => ({
  moduleType, status: 'SUCCEEDED', sourceDocumentName: `${moduleType.toLowerCase()}.docx`,
  completedAt: '2026-08-16T09:30:00Z', current: true, stale: false,
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
});

const financeCurrent = {
  status: 'SUCCEEDED', stale: false, fallback: false, completedAt: '2026-08-16T10:30:00',
  sourceDocumentName: 'my-finance-plan.docx',
  result: {
    calculation: { scenarios: [{ code: 'BASE', totalRevenue: 36000000, totalOperatingProfit: 9000000,
      requiredWorkingCapital: 4000000, breakEvenMonth: 8, paybackMonth: 14 }], summary: { headline: '기준 시나리오의 사업 지속 가능성을 확인했습니다.' } },
    annualProjections: [{ year: 1, revenue: 100, variableCost: 20, grossProfit: 80,
      sellingGeneralAdministrative: 30, operatingProfit: 50, nonOperatingIncome: 0,
      corporateTax: 10, netIncome: 40, operatingMarginPercent: 50 }],
    cashFlowChart: [{ month: 1, revenue: 100, operatingProfit: -10, cumulativeCashFlow: -30 },
      { month: 2, revenue: 130, operatingProfit: 10, cumulativeCashFlow: -20 }],
    stressScenarios: [{ code: 'CONSERVATIVE', label: '보수', breakEvenMonth: null,
      totalOperatingProfit: -100, requiredWorkingCapital: 500 }],
    monteCarlo: { simulations: 1000, profitP10: -10, profitP50: 50, profitP90: 120,
      lossProbabilityPercent: 18, paybackProbabilityPercent: 64 },
    report: { headline: '기준 시나리오는 타당성을 보입니다.', findings: ['매출 근거 확인'],
      cautions: ['손실 확률 주의'], recommendedActions: ['가격 검증'], disclaimer: '추정치입니다.' },
  },
};

describe('V21.4 단일 보고서 문서', () => {
  beforeEach(() => vi.restoreAllMocks());

  it.each([['technology', 'TECHNOLOGY'], ['operations', 'OPERATIONS']])('%s 보고서 보기는 PDF endpoint 없이 route command만 전달한다', async (module, type) => {
    const api = { professionalCurrent: vi.fn().mockResolvedValue(professionalCurrent(type)), downloadProfessionalReport: vi.fn() };
    const onViewReport = vi.fn();
    render(<ProfessionalModule module={module} api={api} projectId="7" onReady={vi.fn()} onDetail={vi.fn()} onViewReport={onViewReport} />);
    fireEvent.click(await screen.findByRole('button', { name: '보고서 보기' }));
    expect(api.downloadProfessionalReport).not.toHaveBeenCalled();
    expect(onViewReport).toHaveBeenCalledWith([module]);
  });

  it('입력 문서가 교체된 과거 결과를 열람 가능하게 유지하고 새 분석 CTA를 제공한다', async () => {
    const current = { ...professionalCurrent(), stale: true, current: false,
      status: 'STALE', staleReason: 'DOCUMENT_SUPERSEDED' };
    const api = { professionalCurrent: vi.fn().mockResolvedValue(current) };
    render(<ProfessionalModule module="technology" api={api} projectId="7" onReady={vi.fn()} />);
    expect(await screen.findByText('새 입력 문서가 있어 이 결과는 이전 입력 기준입니다.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '새 DOCX로 다시 분석' })).toBeInTheDocument();
    expect(screen.getByText(current.analysis.summary)).toBeInTheDocument();
  });

  it('FAILED만 명시적으로 retry하고 응답 모호성은 POST 재전송 없이 current GET 한 번으로 회복한다', async () => {
    const failed = { ...professionalCurrent(), analysis: null, status: 'FAILED', retryAvailable: true };
    const api = {
      professionalCurrent: vi.fn().mockResolvedValueOnce(failed).mockResolvedValue({ ...failed, status: 'QUEUED' }),
      retryProfessional: vi.fn().mockRejectedValue(new Error('network ambiguity')),
    };
    render(<ProfessionalModule module="operations" api={api} projectId="7" onReady={vi.fn()} />);
    fireEvent.click(await screen.findByRole('button', { name: '다시 시도' }));
    await waitFor(() => expect(api.professionalCurrent).toHaveBeenCalledTimes(2));
    expect(api.retryProfessional).toHaveBeenCalledTimes(1);
  });

  it('Finance 보고서 보기도 binary fetch 없이 route command만 전달한다', async () => {
    const api = { financeCurrent: vi.fn().mockResolvedValue(financeCurrent), downloadFinanceReport: vi.fn() };
    const onViewReport = vi.fn();
    render(<FinanceModule api={api} projectId="7" onReady={vi.fn()} onDetail={vi.fn()} onViewReport={onViewReport} />);
    fireEvent.click(await screen.findByRole('button', { name: '보고서 보기' }));
    expect(api.downloadFinanceReport).not.toHaveBeenCalled();
    expect(onViewReport).toHaveBeenCalledWith(['finance']);
  });

  it('Finance는 로컬 업로드 상태 없이 current 재조회만으로 입력 문서명을 복원한다', async () => {
    const api = { financeCurrent: vi.fn().mockResolvedValue(financeCurrent) };
    const first = render(<FinanceModule api={api} projectId="7" onReady={vi.fn()} />);
    expect(await screen.findByText('my-finance-plan.docx')).toBeInTheDocument();
    first.unmount();
    render(<FinanceModule api={api} projectId="7" onReady={vi.fn()} />);
    expect(await screen.findByText('my-finance-plan.docx')).toBeInTheDocument();
    expect(api.financeCurrent).toHaveBeenCalledTimes(2);
  });

  it('통합 보고서 선택은 Backend bundle 요청 없이 선택 module route command를 만든다', () => {
    const onViewReport = vi.fn();
    render(<ReportToolbar reports={{ technology: professionalCurrent(), finance: financeCurrent }} onViewReport={onViewReport} />);
    fireEvent.click(screen.getByText('기술 분석 보고서'));
    fireEvent.click(screen.getByText('재무 분석 보고서'));
    fireEvent.click(screen.getByRole('button', { name: '2개 통합 보고서 보기' }));
    expect(onViewReport).toHaveBeenCalledWith(['technology', 'finance']);
  });

  it('Technology 문서는 3개 summary와 8개 의미 section, 점수 투명성·disclaimer를 보존한다', () => {
    const { container } = render(<LaunchReadinessReportDocument module="technology" current={professionalCurrent()} projectName="테스트 프로젝트" />);
    ['1. 경영진 요약', '2. 평가에 사용한 입력 근거', '3. 영역별 준비도와 판단 근거', '4. 핵심 위험',
      '5. 출시 전 확인 기준', '6. 우선 실행 과제', '7. 사업 적용 결론', '8. 외부 참고 출처']
      .forEach((title) => expect(screen.getByRole('heading', { name: title })).toBeInTheDocument());
    expect(screen.getByText('AI 출시 준비도 평가')).toBeInTheDocument();
    expect(screen.getByText(/정해진 산식의 점수가 아닙니다/)).toBeInTheDocument();
    expect(screen.getByText('독립 AI 검증 통과')).toBeInTheDocument();
    expect(screen.queryByText('94')).not.toBeInTheDocument();
    expect(screen.getByText(/인증 또는 성과를 보장하지 않습니다/)).toBeInTheDocument();
    expect(container.querySelectorAll('.launch-report-document__summary > div')).toHaveLength(3);
    expect(container.querySelectorAll('.launch-report-table--dimensions tbody tr')).toHaveLength(1);
    expect(container.querySelectorAll('.launch-report-table--risks tbody tr')).toHaveLength(1);
    expect(container.querySelectorAll('.launch-report-table--gates tbody tr')).toHaveLength(1);
    expect(container.querySelectorAll('.launch-report-table--actions tbody tr')).toHaveLength(1);
  });

  it('quality 미통과 결과를 독립 AI 검증 통과로 표시하지 않는다', () => {
    render(<LaunchReadinessReportDocument module="operations" current={professionalCurrent('OPERATIONS', false)} projectName="테스트" />);
    expect(screen.queryByText('독립 AI 검증 통과')).not.toBeInTheDocument();
    expect(screen.getByText('독립 검증 결과 확인 필요')).toBeInTheDocument();
  });

  it('Finance 문서는 계산값·3개년·월별 SVG·스트레스·Monte Carlo·AI·사업 결론을 모두 표시한다', () => {
    render(<FinanceReadinessReportDocument current={financeCurrent} projectName="테스트 프로젝트" />);
    ['1. 핵심 결과', '2. 3개년 추정 손익', '3. 월별 매출·영업이익·누적 현금흐름', '4. 스트레스 시나리오',
      '5. Monte Carlo 위험 분포', '6. AI 해석과 권장 조치', '7. 사업 적용 결론']
      .forEach((title) => expect(screen.getByRole('heading', { name: title })).toBeInTheDocument());
    expect(screen.getByRole('img', { name: '월별 매출, 영업이익, 누적 현금흐름 추이' })).toBeInTheDocument();
    expect(screen.getByText('매출 근거 확인')).toBeInTheDocument();
    expect(screen.getByText('기준 시나리오의 사업 지속 가능성을 확인했습니다.')).toBeInTheDocument();
    expect(screen.getByText('my-finance-plan.docx')).toBeInTheDocument();
  });

  it('통합 문서는 같은 개별 component와 URL 기준 통합 source를 사용한다', () => {
    render(<IntegratedLaunchReadinessReportDocument projectName="테스트" completedAt="2026-08-16T10:30:00Z"
      documents={[{ module: 'technology', current: professionalCurrent() }, { module: 'finance', current: financeCurrent }]} />);
    expect(screen.getByRole('heading', { name: '출시 준비 통합 보고서' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: '기술 출시 준비도 보고서' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: '재무 출시 준비 보고서' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: '통합 외부 참고 출처' })).toBeInTheDocument();
    expect(screen.getAllByText('OWASP ASVS')).toHaveLength(1);
  });

  it('통합 문서는 전달 순서와 무관하게 기술→운영→재무 순서를 사용한다', () => {
    const { container } = render(<IntegratedLaunchReadinessReportDocument projectName="테스트" completedAt="2026-08-16T10:30:00Z"
      documents={[{ module: 'finance', current: financeCurrent }, { module: 'operations', current: professionalCurrent('OPERATIONS') }, { module: 'technology', current: professionalCurrent() }]} />);
    const modules = [...container.querySelectorAll('.launch-integrated-report > [data-report-document]')]
      .map((element) => element.dataset.reportDocument).filter((module) => module !== 'integrated');
    expect(modules).toEqual(['technology', 'operations', 'finance']);
    expect(screen.getByText('기술 분석 보고서 · 운영 분석 보고서 · 재무 분석 보고서')).toBeInTheDocument();
  });

  it('통합 report query도 클릭 순서를 무시하고 canonical order로 정렬한다', () => {
    const params = new URLSearchParams();
    params.append('modules', 'finance');
    params.append('modules', 'technology');
    params.append('modules', 'operations');
    expect(reportModulesFromQuery('integrated', params)).toEqual(['technology', 'operations', 'finance']);
  });

  it('Report Page의 PDF 저장은 같은 DOM에서 window.print만 1회 호출하고 title을 복원한다', () => {
    const originalTitle = document.title;
    const print = vi.spyOn(window, 'print').mockImplementation(() => {});
    render(<MemoryRouter><LaunchReadinessReportPageView reportType="technology" project={{ projectId: 7, name: '스마트:서비스' }}
      documents={[{ module: 'technology', current: professionalCurrent() }]}
      onPrint={(completedAt) => printLaunchReadinessReport('스마트:서비스', 'technology', completedAt)} /></MemoryRouter>);
    fireEvent.click(screen.getByRole('button', { name: 'PDF로 저장' }));
    expect(print).toHaveBeenCalledTimes(1);
    expect(document.title).toBe('스마트_서비스_기술_출시준비_보고서_20260816_0930');
    window.dispatchEvent(new Event('afterprint'));
    expect(document.title).toBe(originalTitle);
  });

  it('파일명은 Windows 금지 문자를 제거하고 통합 보고서 이름을 만든다', () => {
    expect(launchReadinessReportTitle('A/B:* 서비스', 'integrated', '2026-08-16T10:30:00'))
      .toBe('A_B_서비스_출시준비_통합보고서_20260816_1030');
  });
});
