import { formatKrwNarrative } from '../model/financeNarrativeFormat.js';

const formatNumber = (value) => new Intl.NumberFormat('ko-KR', { maximumFractionDigits: 2 })
  .format(Number(value ?? 0));

export default function AnalysisReport({ analysis }) {
  const result = analysis?.result;
  if (!result) return null;
  const base = result.calculation?.scenarios?.find((item) => item.code === 'BASE')
    ?? result.calculation?.scenarios?.[0];
  const report = result.report ?? {};
  return <section className="finance-section finance-analysis-report" aria-labelledby="finance-analysis-title">
    <div className="finance-section__heading"><div><p>AI Financial Report</p>
      <h2 id="finance-analysis-title">AI 기반 사업 타당성 및 재무 경제성 분석 최종 보고서</h2></div>
      <span>{analysis.fallback ? '결정론 계산 Fallback' : 'AI 보고서 생성 완료'}</span></div>
    {analysis.fallback && <p className="finance-warning" role="status">AI 설명을 완료하지 못해 계산 결과는 유지하고 기본 설명으로 대체했습니다. {report.safeFailureReason}</p>}
    <h3>{report.headline}</h3>
    <div className="finance-source__grid">
      <Metric label="36개월 누적 매출" value={`${formatNumber(base?.totalRevenue)} KRW`} />
      <Metric label="36개월 누적 영업이익" value={`${formatNumber(base?.totalOperatingProfit)} KRW`} />
      <Metric label="필요 운전자금" value={`${formatNumber(base?.requiredWorkingCapital)} KRW`} />
      <Metric label="손익분기점(BEP)" value={base?.breakEvenMonth ? `${base.breakEvenMonth}개월 차` : '분석 기간 내 미도달'} />
      <Metric label="손실 확률" value={`${result.monteCarlo?.lossProbabilityPercent ?? 0}%`} />
      <Metric label="투자금 회수 확률" value={`${result.monteCarlo?.paybackProbabilityPercent ?? 0}%`} />
    </div>

    <h3>1. 구조화된 3개년 추정 손익계산서</h3>
    <div className="finance-table-wrap"><table><thead><tr><th>구분 (KRW)</th>{(result.annualProjections ?? []).map((row) => <th key={row.year}>{row.year}년 차</th>)}</tr></thead>
      <tbody>{[
        ['매출액', 'revenue'], ['매출원가', 'variableCost'], ['매출총이익', 'grossProfit'],
        ['판매비와 관리비', 'sellingGeneralAdministrative'], ['영업이익', 'operatingProfit'],
        ['영업외손익', 'nonOperatingIncome'], ['법인세', 'corporateTax'], ['당기순이익', 'netIncome'],
      ].map(([label, key]) => <tr key={key}><th>{label}</th>{(result.annualProjections ?? []).map((row) => <td key={row.year}>{formatNumber(row[key])}</td>)}</tr>)}
      <tr><th>영업이익률</th>{(result.annualProjections ?? []).map((row) => <td key={row.year}>{row.operatingMarginPercent}%</td>)}</tr></tbody></table></div>

    <h3>2. 현금 흐름 및 손익분기점</h3>
    <p>기준 시나리오 BEP: <strong>{base?.breakEvenMonth ? `${base.breakEvenMonth}개월 차` : '분석 기간 내 미도달'}</strong> · 필요 운전자금: <strong>{formatNumber(base?.requiredWorkingCapital)} KRW</strong></p>
    <FinancialLineChart title="월별 매출 및 영업이익" subtitle="매출과 영업이익이 0원 기준선을 넘는 시점을 확인합니다."
      series={[{ name: '매출', color: '#245fc0', values: (result.cashFlowChart ?? []).map((row) => row.revenue) },
        { name: '영업이익', color: '#e05a47', values: (result.cashFlowChart ?? []).map((row) => row.operatingProfit) }]} />
    <FinancialLineChart title="누적 현금흐름" subtitle="0원 기준선을 넘으면 초기 투자금 회수 상태입니다."
      series={[{ name: '누적 현금흐름', color: '#16826c', values: (result.cashFlowChart ?? []).map((row) => row.cumulativeCashFlow) }]} />
    <details><summary>월별 매출·영업이익·누적 현금흐름 상세 보기</summary><div className="finance-table-wrap"><table><thead><tr><th>월</th><th>매출</th><th>영업이익</th><th>누적 현금흐름</th></tr></thead><tbody>
      {(result.cashFlowChart ?? []).map((row) => <tr key={row.month}><td>{row.month}</td><td>{formatNumber(row.revenue)}</td><td>{formatNumber(row.operatingProfit)}</td><td>{formatNumber(row.cumulativeCashFlow)}</td></tr>)}</tbody></table></div></details>

    <h3>3. 스트레스 테스트 및 Monte Carlo</h3>
    <p>시뮬레이션 {result.monteCarlo?.simulations?.toLocaleString()}회 · 재현 기준 {result.monteCarlo?.seed} · P10 {formatNumber(result.monteCarlo?.profitP10)} KRW · P50 {formatNumber(result.monteCarlo?.profitP50)} KRW · P90 {formatNumber(result.monteCarlo?.profitP90)} KRW</p>
    <div className="finance-source__grid">{(result.stressScenarios ?? []).map((row) => <article key={row.code}><span>{row.label}</span><strong>BEP {row.breakEvenMonth ?? '미도달'}개월</strong><small>영업이익 {formatNumber(row.totalOperatingProfit)} KRW · 필요자금 {formatNumber(row.requiredWorkingCapital)} KRW</small></article>)}</div>
    <FinancialLineChart title="시나리오별 누적 현금흐름" subtitle="보수·기준·낙관 시나리오의 현금 여력과 회수 시점을 비교합니다."
      series={(result.stressScenarios ?? []).map((row, index) => ({ name: row.label, color: ['#e05a47', '#245fc0', '#16826c'][index % 3], values: (row.monthlyCashFlow ?? []).map((point) => point.cumulativeCashFlow) }))} />

    <h3>4. 보고서 판단과 근거</h3>
    <ReportList title="핵심 발견" values={report.findings} />
    <ReportList title="주의·위험" values={report.cautions} />
    <ReportList title="권장 액션" values={report.recommendedActions} />
    <details><summary>기술 정보</summary><p><strong>보고서 생성 방식:</strong> {report.source} · <strong>AI 서비스 상태:</strong> {report.providerStatus}</p></details>
    <small>{report.disclaimer}</small>
  </section>;
}

function Metric({ label, value }) { return <article><span>{label}</span><strong>{value}</strong></article>; }
function ReportList({ title, values = [] }) { return <><h4>{title}</h4><ul>{values.map((item, index) => <li key={`${title}-${index}`}>{formatKrwNarrative(item)}</li>)}</ul></>; }

function FinancialLineChart({ title, subtitle, series }) {
  const width = 880; const height = 280; const left = 64; const right = 24; const top = 24; const bottom = 42;
  const allValues = series.flatMap((item) => item.values).map((value) => Number(value ?? 0));
  const min = Math.min(0, ...allValues); const max = Math.max(0, ...allValues); const range = max - min || 1;
  const pointCount = Math.max(...series.map((item) => item.values.length), 1);
  const x = (index) => left + (index * (width - left - right)) / Math.max(pointCount - 1, 1);
  const y = (value) => top + ((max - Number(value ?? 0)) * (height - top - bottom)) / range;
  const points = (values) => values.map((value, index) => `${x(index)},${y(value)}`).join(' ');
  return <figure className="finance-line-chart"><figcaption><strong>{title}</strong><span>{subtitle}</span></figcaption>
    <svg viewBox={`0 0 ${width} ${height}`} role="img" aria-label={title}>
      {[max, max - range / 2, min].map((value, index) => <g key={index}><line x1={left} x2={width - right} y1={y(value)} y2={y(value)} className="finance-line-chart__grid" /><text x={left - 8} y={y(value) + 4} textAnchor="end" className="finance-line-chart__axis">{formatCompact(value)}</text></g>)}
      <line x1={left} x2={width - right} y1={y(0)} y2={y(0)} className="finance-line-chart__zero" />
      {series.map((item) => <polyline key={item.name} points={points(item.values)} fill="none" stroke={item.color} strokeWidth="3" strokeLinejoin="round" strokeLinecap="round" />)}
      <text x={left} y={height - 14} className="finance-line-chart__axis">1개월</text><text x={width - right} y={height - 14} textAnchor="end" className="finance-line-chart__axis">{pointCount}개월</text>
    </svg><div className="finance-line-chart__legend">{series.map((item) => <span key={item.name}><i style={{ backgroundColor: item.color }} />{item.name}</span>)}</div>
  </figure>;
}

function formatCompact(value) {
  const number = Number(value ?? 0); const absolute = Math.abs(number);
  if (absolute >= 100000000) return `${(number / 100000000).toFixed(1)}억`;
  if (absolute >= 10000) return `${(number / 10000).toFixed(0)}만`;
  return new Intl.NumberFormat('ko-KR', { maximumFractionDigits: 0 }).format(number);
}
