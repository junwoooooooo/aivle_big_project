import { AppIcon } from '../../../shared/ui/index.js';

const formatNumber = (value) => new Intl.NumberFormat('ko-KR', { maximumFractionDigits: 2 })
  .format(Number(value ?? 0));

function ReportList({ title, values = [] }) {
  if (!values.length) return null;
  return <div><h4>{title}</h4><ul className="launch-preview-list">{values.map((item, index) => <li key={`${title}-${index}`}>{item}</li>)}</ul></div>;
}

export function FinanceReportPreviewDocument({ current }) {
  const result = current?.result;
  if (!result) return <article className="launch-preview-document"><p>표시할 재무 분석 결과가 없습니다.</p></article>;
  const base = result.calculation?.scenarios?.find((item) => item.code === 'BASE')
    ?? result.calculation?.scenarios?.[0];
  const report = result.report ?? {};
  const monteCarlo = result.monteCarlo ?? {};
  return <article className="launch-preview-document" data-module="finance">
    <header className="launch-preview-document__cover">
      <p>재무 분석 보고서</p>
      <h2>업로드한 재무 문서의 분석 결과를 확인하세요</h2>
      <span>현재 Finance 결과의 계산값과 AI 해석을 그대로 표시합니다. 미리보기에서 값을 다시 계산하지 않습니다.</span>
    </header>
    {current.stale && <p className="launch-preview-stale"><AppIcon name="alert" size={16} />이전 입력 기준 결과입니다.</p>}
    {current.fallback && <p className="launch-preview-stale">AI 해석을 완료하지 못해 계산 결과와 기본 설명을 표시합니다.</p>}

    <section className="launch-preview-section" aria-labelledby="finance-preview-core">
      <p className="launch-preview-kicker">핵심 결과</p>
      <h3 id="finance-preview-core">{report.headline || '재무 분석 결과'}</h3>
      <div className="launch-preview-metrics">
        <article><span>36개월 누적 매출</span><strong>{formatNumber(base?.totalRevenue)} KRW</strong></article>
        <article><span>36개월 누적 영업이익</span><strong>{formatNumber(base?.totalOperatingProfit)} KRW</strong></article>
        <article><span>필요 운전자금</span><strong>{formatNumber(base?.requiredWorkingCapital)} KRW</strong></article>
        <article><span>손익분기점</span><strong>{base?.breakEvenMonth ? `${base.breakEvenMonth}개월 차` : '분석 기간 내 미도달'}</strong></article>
      </div>
    </section>

    <section className="launch-preview-section" aria-labelledby="finance-preview-years">
      <p className="launch-preview-kicker">3개년 추정</p>
      <h3 id="finance-preview-years">연도별 손익 추정</h3>
      <div className="launch-preview-table-wrap"><table><thead><tr><th>구분</th>{(result.annualProjections ?? []).map((row) => <th key={row.year}>{row.year}년 차</th>)}</tr></thead>
        <tbody>{[
          ['매출액', 'revenue'], ['매출총이익', 'grossProfit'], ['영업이익', 'operatingProfit'],
          ['당기순이익', 'netIncome'], ['영업이익률', 'operatingMarginPercent'],
        ].map(([label, key]) => <tr key={key}><th>{label}</th>{(result.annualProjections ?? []).map((row) => <td key={row.year}>{formatNumber(row[key])}{key === 'operatingMarginPercent' ? '%' : ''}</td>)}</tr>)}</tbody></table></div>
    </section>

    <section className="launch-preview-section" aria-labelledby="finance-preview-monthly">
      <p className="launch-preview-kicker">월별 주요 지표</p>
      <h3 id="finance-preview-monthly">매출·영업이익·누적 현금흐름</h3>
      <details><summary>월별 값 확인하기</summary><div className="launch-preview-table-wrap"><table><thead><tr><th>월</th><th>매출</th><th>영업이익</th><th>누적 현금흐름</th></tr></thead>
        <tbody>{(result.cashFlowChart ?? []).map((row) => <tr key={row.month}><td>{row.month}</td><td>{formatNumber(row.revenue)}</td><td>{formatNumber(row.operatingProfit)}</td><td>{formatNumber(row.cumulativeCashFlow)}</td></tr>)}</tbody></table></div></details>
    </section>

    <section className="launch-preview-section" aria-labelledby="finance-preview-risk">
      <p className="launch-preview-kicker">스트레스 시나리오와 Monte Carlo</p>
      <h3 id="finance-preview-risk">변동 가능성과 자금 위험</h3>
      <div className="launch-preview-metrics launch-preview-metrics--risk">
        <article><span>손실 확률</span><strong>{formatNumber(monteCarlo.lossProbabilityPercent)}%</strong></article>
        <article><span>투자금 회수 확률</span><strong>{formatNumber(monteCarlo.paybackProbabilityPercent)}%</strong></article>
        <article><span>P10 / P50 / P90</span><strong>{formatNumber(monteCarlo.profitP10)} / {formatNumber(monteCarlo.profitP50)} / {formatNumber(monteCarlo.profitP90)}</strong></article>
        <article><span>시뮬레이션</span><strong>{formatNumber(monteCarlo.simulations)}회</strong></article>
      </div>
      <div className="launch-preview-card-grid">{(result.stressScenarios ?? []).map((row) => <article key={row.code}><div><strong>{row.label}</strong><span>BEP {row.breakEvenMonth ? `${row.breakEvenMonth}개월` : '미도달'}</span></div><p>영업이익 {formatNumber(row.totalOperatingProfit)} KRW</p><p>필요자금 {formatNumber(row.requiredWorkingCapital)} KRW</p></article>)}</div>
    </section>

    <section className="launch-preview-section" aria-labelledby="finance-preview-ai">
      <p className="launch-preview-kicker">AI 해석과 권장 조치</p>
      <h3 id="finance-preview-ai">계산 결과를 사업 계획에 반영할 때 확인할 내용</h3>
      <div className="launch-preview-report-lists"><ReportList title="핵심 발견" values={report.findings} /><ReportList title="주의 사항" values={report.cautions} /><ReportList title="권장 조치" values={report.recommendedActions} /></div>
      {report.disclaimer && <small className="launch-preview-disclaimer">{report.disclaimer}</small>}
    </section>
  </article>;
}
