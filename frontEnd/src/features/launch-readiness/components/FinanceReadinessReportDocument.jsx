import { formatKrwAmount, formatReportDate } from '../model/reportDocumentPresentation.js';

const formatNumber = (value) => new Intl.NumberFormat('ko-KR', { maximumFractionDigits: 2 }).format(Number(value ?? 0));
const valuesOf = (value) => Array.isArray(value) ? value : [];

export function KrwAmount({ value }) {
  const { raw, readable } = formatKrwAmount(value);
  return <span className="launch-money"><span className="launch-money__raw">{raw}</span><small className="launch-money__readable">{readable}</small></span>;
}

function ReportSection({ number, title, children, className = '' }) {
  return <section className={`launch-report-document__section ${className}`}><h2>{number}. {title}</h2>{children}</section>;
}

function ReportList({ title, values }) {
  return valuesOf(values).length > 0 && <section><h3>{title}</h3><ul>{values.map((item, index) => <li key={`${title}-${index}`}>{item}</li>)}</ul></section>;
}

function chartPoints(rows, key, width, height, padding, min, max) {
  const range = max - min || 1;
  return rows.map((row, index) => {
    const x = padding + (index / Math.max(rows.length - 1, 1)) * (width - padding * 2);
    const y = height - padding - ((Number(row[key] ?? 0) - min) / range) * (height - padding * 2);
    return `${x.toFixed(1)},${y.toFixed(1)}`;
  }).join(' ');
}

export function FinanceTrendChart({ rows = [] }) {
  if (!rows.length) return <p>표시할 월별 추이 데이터가 없습니다.</p>;
  const width = 720; const height = 250; const padding = 30;
  const values = rows.flatMap((row) => [row.revenue, row.operatingProfit, row.cumulativeCashFlow]).map(Number).filter(Number.isFinite);
  const min = Math.min(0, ...values); const max = Math.max(0, ...values);
  return <figure className="launch-finance-chart"><svg viewBox={`0 0 ${width} ${height}`} role="img" aria-label="월별 매출, 영업이익, 누적 현금흐름 추이">
    <line x1={padding} y1={height - padding} x2={width - padding} y2={height - padding} className="chart-axis" />
    <polyline points={chartPoints(rows, 'revenue', width, height, padding, min, max)} className="chart-revenue" />
    <polyline points={chartPoints(rows, 'operatingProfit', width, height, padding, min, max)} className="chart-profit" />
    <polyline points={chartPoints(rows, 'cumulativeCashFlow', width, height, padding, min, max)} className="chart-cash" />
  </svg><figcaption><span className="is-revenue">매출</span><span className="is-profit">영업이익</span><span className="is-cash">누적 현금흐름</span></figcaption></figure>;
}

export function FinanceReadinessReportDocument({ current, projectName, embedded = false }) {
  const result = current?.result;
  if (!result) return <article className="launch-report-document"><p>표시할 재무 분석 결과가 없습니다.</p></article>;
  const base = result.calculation?.scenarios?.find((item) => item.code === 'BASE') ?? result.calculation?.scenarios?.[0];
  const report = result.report ?? {};
  const monteCarlo = result.monteCarlo;
  const conclusion = result.calculation?.summary?.headline ?? report.headline ?? '현재 재무 입력을 기준으로 사업 계획을 검토했습니다.';

  return <article className={`launch-report-document${embedded ? ' is-embedded' : ''}`} data-report-document="finance">
    <header className="launch-report-document__cover">
      <p>VENTURE VERIFY · LAUNCH READINESS</p><h1>재무 출시 준비 보고서</h1>
      <span>사용자 재무 입력 문서의 authoritative 계산 결과와 AI 해석을 그대로 정리했습니다.</span>
      <dl><div><dt>프로젝트명</dt><dd>{projectName || '자료 없음'}</dd></div><div><dt>분석 기준일</dt><dd>{formatReportDate(current.completedAt)}</dd></div><div><dt>입력 문서</dt><dd>{current.sourceDocumentName || '사용자 재무 입력 문서'}</dd></div><div><dt>금액 단위</dt><dd>KRW</dd></div></dl>
    </header>
    {current.fallback && <p className="launch-report-document__notice">AI 해석을 완료하지 못해 계산 결과와 기본 설명을 표시합니다.</p>}

    <ReportSection number="1" title="핵심 결과">
      {report.headline && <p className="launch-report-document__callout">{report.headline}</p>}
      <dl className="launch-report-document__metrics"><div><dt>손익분기점</dt><dd>{base?.breakEvenMonth ? `${base.breakEvenMonth}개월 차` : '분석 기간 내 미도달'}</dd></div><div><dt>초기 투자금 회수</dt><dd>{base?.paybackMonth ? `${base.paybackMonth}개월 차` : '분석 기간 내 미회수'}</dd></div><div><dt>필요 운전자금</dt><dd><KrwAmount value={base?.requiredWorkingCapital} /></dd></div><div><dt>총 영업이익</dt><dd><KrwAmount value={base?.totalOperatingProfit} /></dd></div></dl>
    </ReportSection>

    <ReportSection number="2" title="3개년 추정 손익">
      <table className="launch-report-table launch-report-table--finance"><thead><tr><th>구분</th>{valuesOf(result.annualProjections).map((row) => <th key={row.year}>{row.year}년 차</th>)}</tr></thead><tbody>{[
        ['매출', 'revenue', true], ['매출원가', 'variableCost', true], ['매출총이익', 'grossProfit', true],
        ['판매비와 관리비', 'sellingGeneralAdministrative', true], ['영업이익', 'operatingProfit', true],
        ['영업외수익', 'nonOperatingIncome', true], ['법인세', 'corporateTax', true], ['당기순이익', 'netIncome', true],
        ['영업이익률', 'operatingMarginPercent', false],
      ].map(([label, key, currency]) => <tr key={key}><th>{label}</th>{valuesOf(result.annualProjections).map((row) => <td key={row.year}>{currency ? <KrwAmount value={row[key]} /> : `${formatNumber(row[key])}%`}</td>)}</tr>)}</tbody></table>
    </ReportSection>

    <ReportSection number="3" title="월별 매출·영업이익·누적 현금흐름" className="launch-report-document__monthly">
      <FinanceTrendChart rows={result.cashFlowChart} />
      <table className="launch-report-table launch-report-table--finance"><thead><tr><th>개월</th><th>매출</th><th>영업이익</th><th>누적 현금흐름</th></tr></thead><tbody>{valuesOf(result.cashFlowChart).map((row) => <tr key={row.month}><td>{row.month}</td><td><KrwAmount value={row.revenue} /></td><td><KrwAmount value={row.operatingProfit} /></td><td><KrwAmount value={row.cumulativeCashFlow} /></td></tr>)}</tbody></table>
    </ReportSection>

    <ReportSection number="4" title="스트레스 시나리오">
      <table className="launch-report-table launch-report-table--finance"><thead><tr><th>시나리오</th><th>손익분기점</th><th>총 영업이익</th><th>필요 운전자금</th></tr></thead><tbody>{valuesOf(result.stressScenarios).map((row) => <tr key={row.code}><th>{row.label}</th><td>{row.breakEvenMonth ? `${row.breakEvenMonth}개월 차` : '기간 내 미도달'}</td><td><KrwAmount value={row.totalOperatingProfit} /></td><td><KrwAmount value={row.requiredWorkingCapital} /></td></tr>)}</tbody></table>
    </ReportSection>

    <ReportSection number="5" title="Monte Carlo 위험 분포">
      {monteCarlo ? <dl className="launch-report-document__metrics launch-report-document__metrics--monte-carlo"><div><dt>시뮬레이션</dt><dd>{formatNumber(monteCarlo.simulations)}회</dd></div><div><dt>손실 확률</dt><dd>{formatNumber(monteCarlo.lossProbabilityPercent)}%</dd></div><div><dt>투자금 회수 확률</dt><dd>{formatNumber(monteCarlo.paybackProbabilityPercent)}%</dd></div><div><dt>P10</dt><dd><KrwAmount value={monteCarlo.profitP10} /></dd></div><div><dt>P50</dt><dd><KrwAmount value={monteCarlo.profitP50} /></dd></div><div><dt>P90</dt><dd><KrwAmount value={monteCarlo.profitP90} /></dd></div></dl> : <p>표시할 Monte Carlo 결과가 없습니다.</p>}
    </ReportSection>

    <ReportSection number="6" title="AI 해석과 권장 조치"><div className="launch-report-document__list-grid"><ReportList title="핵심 발견" values={report.findings} /><ReportList title="주의 사항" values={report.cautions} /><ReportList title="권장 조치" values={report.recommendedActions} /></div></ReportSection>
    <ReportSection number="7" title="사업 적용 결론"><p className="launch-report-document__callout">{conclusion}</p></ReportSection>
    <footer className="launch-report-document__disclaimer">{report.disclaimer || '본 보고서는 입력 가정에 따른 사업 계획 시뮬레이션이며 실제 성과를 보장하지 않습니다.'}</footer>
  </article>;
}
