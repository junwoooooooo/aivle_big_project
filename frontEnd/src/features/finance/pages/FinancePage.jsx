import { useRef, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { getUserErrorMessage } from '../../../shared/api/apiError.js';
import useFinance from '../hooks/useFinance.js';
import '../styles/finance.css';

const fmt = (value) => new Intl.NumberFormat('ko-KR').format(Number(value ?? 0));
const statementRows = [
  ['매출', 'revenue'], ['매출원가', 'variableCost'], ['매출총이익', 'grossProfit'],
  ['판매비와관리비', 'sellingGeneralAdministrative'], ['영업이익', 'operatingProfit'],
  ['영업외손익', 'nonOperatingIncome'], ['법인세', 'corporateTax'], ['당기순이익', 'netIncome'],
];

const reportKo = (text) => ({
  'Base scenario remains loss-making over the selected period.': '기준 시나리오에서는 선택한 분석 기간 동안 누적 손실이 지속될 것으로 예측됩니다.',
  'P10/P50/P90 profit should be reviewed before funding decisions.': '자금 조달 전에는 보수·기준·낙관 범위(P10/P50/P90)의 수익 가능성을 함께 검토하세요.',
  'Validate price, volume and variable-cost assumptions with observed data.': '가격·판매량·변동비 가정을 실제 고객·판매 데이터로 검증하세요.',
  'Use the conservative scenario for cash planning.': '현금 계획은 보수 시나리오를 기준으로 수립하세요.',
}[String(text ?? '')] ?? String(text ?? '')
  .replace(/^Total revenue:/, '총매출:')
  .replace(/^Operating profit:/, '영업이익:')
  .replace(/^Required working capital:/, '필요 운전자금:')
  .replace(/^Monte Carlo loss probability:/, '몬테카를로 손실 확률:'));

const compactKrw = (value) => {
  const amount = Math.abs(Number(value ?? 0));
  const sign = Number(value ?? 0) < 0 ? '-' : '';
  if (amount >= 100000000) return `${sign}${(amount / 100000000).toFixed(1)}억`;
  if (amount >= 10000) return `${sign}${Math.round(amount / 10000)}만`;
  return `${sign}${fmt(amount)}`;
};

function FinancialPerformanceChart({ rows = [] }) {
  const revenue = rows.map((row) => Number(row.revenue ?? 0));
  const monthlyCash = rows.map((row) => Number(row.operatingProfit ?? 0));
  const cumulative = rows.map((row) => Number(row.cumulativeCashFlow ?? 0));
  if (!revenue.length) return null;
  const width = 900; const height = 438; const left = 64; const right = 26; const top = 28; const panel = 82; const gap = 42;
  const chartWidth = width - left - right;
  const x = (index) => left + index * chartWidth / Math.max(revenue.length - 1, 1);
  const scale = (values, y) => { const rawMin = Math.min(0, ...values); const rawMax = Math.max(0, ...values); const rawRange = rawMax - rawMin || 1; const min = rawMin - rawRange * .12; const max = rawMax + rawRange * .12; const range = max - min; return { min, max, y: (value) => y + (max - value) * panel / range, zero: y + max * panel / range }; };
  const revenueScale = scale(revenue, top); const cashScale = scale(monthlyCash, top + panel + gap); const cumulativeScale = scale(cumulative, top + (panel + gap) * 2);
  const monthlyBep = monthlyCash.findIndex((value) => value >= 0); const payback = cumulative.findIndex((value) => value >= 0);
  const points = (values, chart) => values.map((value, index) => `${x(index)},${chart.y(value)}`).join(' ');
  const xTicks = [...new Set([0, Math.floor((revenue.length - 1) / 3), Math.floor((revenue.length - 1) * 2 / 3), revenue.length - 1])];
  const grid = (chart, key) => <g key={key}><line x1={left} x2={width - right} y1={chart.y(chart.max)} y2={chart.y(chart.max)} className="finance-performance-chart__grid" /><line x1={left} x2={width - right} y1={chart.zero} y2={chart.zero} className="finance-performance-chart__zero" /><line x1={left} x2={width - right} y1={chart.y(chart.min)} y2={chart.y(chart.min)} className="finance-performance-chart__grid" /><text x={left - 10} y={chart.y(chart.max) + 4} textAnchor="end" className="finance-performance-chart__tick">{compactKrw(chart.max)}</text><text x={left - 10} y={chart.zero + 4} textAnchor="end" className="finance-performance-chart__tick">0</text><text x={left - 10} y={chart.y(chart.min) + 4} textAnchor="end" className="finance-performance-chart__tick">{compactKrw(chart.min)}</text></g>;
  const marker = (index, chart, color, label) => index < 0 ? null : <g><line x1={x(index)} x2={x(index)} y1={chart.y(chart.max)} y2={chart.y(chart.min)} stroke={color} strokeWidth="2" strokeDasharray="5 4" /><text x={Math.min(x(index) + 5, width - right - 80)} y={chart.y(chart.max) + 14} className="finance-performance-chart__event" fill={color}>{label}</text></g>;
  const monthBepStatus = monthlyBep >= 0 ? `${monthlyBep + 1}개월 차` : `${revenue.length}개월 안에 도달하지 못함`;
  const paybackStatus = payback >= 0 ? `${payback + 1}개월 차` : `${revenue.length}개월 안에 회수하지 못함`;
  return <figure className="finance-performance-chart"><figcaption><strong>매출·현금흐름 연결 예측</strong><span>매출, 월 영업이익, 누적 현금흐름을 같은 월 축으로 분리해 보여줍니다. 각 그래프의 숫자 범위는 서로 독립적입니다.</span></figcaption><div className="finance-line-chart__status"><span><b>월 손익분기점</b>{monthBepStatus}</span><span><b>초기 투자금 회수</b>{paybackStatus}</span><span><b>{revenue.length}개월 말 누적 현금흐름</b>{fmt(cumulative.at(-1))} KRW</span></div><div className="finance-performance-chart__legend"><span><i className="finance-performance-chart__revenue" />월별 매출</span><span><i className="finance-performance-chart__profit" />월 영업이익</span><span><i className="finance-performance-chart__cash" />누적 현금흐름</span><em>점선은 달성 시점만 표시됩니다.</em></div><svg viewBox={`0 0 ${width} ${height}`} role="img" aria-label="월별 매출, 월 영업이익, 누적 현금흐름과 손익분기점 예측"><text x={left} y={top - 10} className="finance-performance-chart__label">월별 매출</text><text x={width - right} y={top - 10} textAnchor="end" className="finance-performance-chart__value">마지막 달 {compactKrw(revenue.at(-1))} KRW</text>{grid(revenueScale, 'revenue')}<polyline points={points(revenue, revenueScale)} fill="none" stroke="#245fc0" strokeWidth="3" strokeLinejoin="round" strokeLinecap="round" /><circle cx={x(revenue.length - 1)} cy={revenueScale.y(revenue.at(-1))} r="4" fill="#245fc0" /><text x={left} y={top + panel + gap - 10} className="finance-performance-chart__label">월 영업이익</text><text x={width - right} y={top + panel + gap - 10} textAnchor="end" className="finance-performance-chart__value">마지막 달 {compactKrw(monthlyCash.at(-1))} KRW</text>{grid(cashScale, 'cash')}{monthlyCash.map((value, index) => <line key={index} x1={x(index)} x2={x(index)} y1={cashScale.zero} y2={cashScale.y(value)} stroke={value >= 0 ? '#16826c' : '#d14343'} strokeWidth={Math.max(5, Math.min(12, chartWidth / revenue.length * .58))} strokeLinecap="round" />)}{marker(monthlyBep, cashScale, '#b46100', `손익분기 ${monthlyBep + 1}개월`)}<text x={left} y={top + (panel + gap) * 2 - 10} className="finance-performance-chart__label">누적 현금흐름</text><text x={width - right} y={top + (panel + gap) * 2 - 10} textAnchor="end" className="finance-performance-chart__value">마지막 달 {compactKrw(cumulative.at(-1))} KRW</text>{grid(cumulativeScale, 'cumulative')}<polyline points={points(cumulative, cumulativeScale)} fill="none" stroke="#16826c" strokeWidth="3" strokeLinejoin="round" strokeLinecap="round" /><circle cx={x(cumulative.length - 1)} cy={cumulativeScale.y(cumulative.at(-1))} r="4" fill="#16826c" />{marker(payback, cumulativeScale, '#087f6b', `투자금 회수 ${payback + 1}개월`)}{xTicks.map((index) => <text key={index} x={x(index)} y={height - 20} textAnchor={index === 0 ? 'start' : index === revenue.length - 1 ? 'end' : 'middle'} className="finance-performance-chart__tick">{index + 1}개월</text>)}</svg><small className="finance-performance-chart__guide">빨간 막대는 적자, 초록 막대는 흑자입니다. 세 지표의 단위 범위가 달라 각각의 축으로 추세를 선명하게 보여줍니다.</small></figure>;
}

function LineChart({ title, subtitle, rows = [], field, color }) {
  if (field === 'revenue') return <FinancialPerformanceChart rows={rows} />;
  if (field === 'revenue') {
    const revenue = rows.map((row) => Number(row.revenue ?? 0));
    const monthlyCash = rows.map((row) => Number(row.operatingProfit ?? 0));
    const cumulative = rows.map((row) => Number(row.cumulativeCashFlow ?? 0));
    if (!revenue.length) return null;
    const width = 900; const height = 420; const left = 58; const right = 28; const top = 26; const panel = 94; const gap = 28;
    const x = (index) => left + index * (width - left - right) / Math.max(revenue.length - 1, 1);
    const scale = (values, topY, panelHeight) => { const min = Math.min(0, ...values); const max = Math.max(0, ...values); const range = max - min || 1; return { y: (value) => topY + (max - value) * panelHeight / range, zero: topY + (max * panelHeight / range), max, min }; };
    const revenueScale = scale(revenue, top, panel); const monthlyScale = scale(monthlyCash, top + panel + gap, panel); const cumulativeScale = scale(cumulative, top + (panel + gap) * 2, panel);
    const monthlyBep = monthlyCash.findIndex((value) => value >= 0); const payback = cumulative.findIndex((value) => value >= 0);
    const marker = (index, color, label) => index < 0 ? null : <g key={label}><line x1={x(index)} x2={x(index)} y1={top - 5} y2={top + panel * 3 + gap * 2} stroke={color} strokeWidth="2" strokeDasharray="5 4" /><text x={x(index) + 4} y={top + 12} className="finance-line-chart__axis" fill={color}>{label}</text></g>;
    const points = (values, chart) => values.map((value, index) => `${x(index)},${chart.y(value)}`).join(' ');
    const monthBepStatus = monthlyBep >= 0 ? `${monthlyBep + 1}개월 차` : `${revenue.length}개월 안에 도달하지 못함`;
    const paybackStatus = payback >= 0 ? `${payback + 1}개월 차` : `${revenue.length}개월 안에 회수하지 못함`;
    return <figure className="finance-line-chart"><figcaption><strong>매출·현금흐름 연결 예측</strong><span>위에서 아래 순서로 월별 매출, 그 달의 현금 증감(영업이익), 누적 현금흐름을 같은 월 축에 표시합니다. 매출 증가가 실제 현금 증가로 이어지는 시점을 확인할 수 있습니다.</span></figcaption><div className="finance-line-chart__status"><span><b>월 손익분기점</b>{monthBepStatus}</span><span><b>초기 투자금 회수</b>{paybackStatus}</span><span><b>{revenue.length}개월 말 누적 현금흐름</b>{fmt(cumulative.at(-1))} KRW</span></div><div className="finance-line-chart__legend"><span><i style={{ background: '#245fc0' }} />월별 매출</span><span><i style={{ background: '#16826c' }} />월 현금 증가(흑자)</span><span><i style={{ background: '#d14343' }} />월 현금 감소(적자)</span><span><i style={{ background: '#16826c' }} />누적 현금흐름</span><span><i style={{ background: '#a65300' }} />월 손익분기점</span><span><i style={{ background: '#087f6b' }} />투자금 회수 시점</span></div><svg viewBox={`0 0 ${width} ${height}`} role="img" aria-label="월별 매출, 현금 증감, 누적 현금흐름과 손익분기점 예측"><text x={left} y={top - 8} className="finance-line-chart__axis">월별 매출</text><text x={left} y={top + panel + gap - 8} className="finance-line-chart__axis">월별 현금 증감 (빨강: 감소 / 초록: 증가)</text><text x={left} y={top + (panel + gap) * 2 - 8} className="finance-line-chart__axis">누적 현금흐름</text><line x1={left} x2={width - right} y1={monthlyScale.zero} y2={monthlyScale.zero} className="finance-line-chart__zero" /><line x1={left} x2={width - right} y1={cumulativeScale.zero} y2={cumulativeScale.zero} className="finance-line-chart__zero" /><polyline points={points(revenue, revenueScale)} fill="none" stroke="#245fc0" strokeWidth="3" strokeLinejoin="round" strokeLinecap="round" />{monthlyCash.map((value, index) => <line key={index} x1={x(index)} x2={x(index)} y1={monthlyScale.zero} y2={monthlyScale.y(value)} stroke={value >= 0 ? '#16826c' : '#d14343'} strokeWidth="10" strokeLinecap="round" />)}<polyline points={points(cumulative, cumulativeScale)} fill="none" stroke="#16826c" strokeWidth="3" strokeLinejoin="round" strokeLinecap="round" />{marker(monthlyBep, '#a65300', `월 손익분기점 ${monthlyBep + 1}개월`)}{marker(payback, '#087f6b', `투자금 회수 ${payback + 1}개월`)}<text x={left} y={height - 10} className="finance-line-chart__axis">1개월</text><text x={width - right} y={height - 10} textAnchor="end" className="finance-line-chart__axis">{revenue.length}개월</text></svg><small className="finance-line-chart__guide">빨간 막대는 해당 월의 영업이익이 음수여서 현금이 줄어드는 구간입니다. 초록 막대가 나타나면 그 달부터 현금이 증가합니다. 주황 점선은 월 손익분기점, 진한 초록 점선은 초기 투자금 회수 시점입니다.</small></figure>;
  }
  // 매출은 월간 흐름, 현금흐름은 누적 잔액이라 각각의 축을 사용해 한 번에 비교한다.
  if (field === 'cumulativeCashFlow') return null;
  if (field === 'revenue') {
    const revenue = rows.map((row) => Number(row.revenue ?? 0));
    const cumulative = rows.map((row) => Number(row.cumulativeCashFlow ?? 0));
    if (!revenue.length) return null;
    const width = 900; const height = 270; const left = 58; const right = 58; const top = 22; const bottom = 40;
    const revenueMax = Math.max(1, ...revenue); const cashMin = Math.min(0, ...cumulative); const cashMax = Math.max(0, ...cumulative); const cashRange = cashMax - cashMin || 1;
    const x = (index) => left + index * (width - left - right) / Math.max(revenue.length - 1, 1);
    const revenueY = (value) => top + (revenueMax - value) * (height - top - bottom) / revenueMax;
    const cashY = (value) => top + (cashMax - value) * (height - top - bottom) / cashRange;
    return <figure className="finance-line-chart"><figcaption><strong>월별 매출 및 누적 현금흐름 예측</strong><span>파란선은 월별 매출 예측(왼쪽 축), 초록선은 누적 현금흐름 예측(오른쪽 축)입니다. 업로드한 가정을 바탕으로 계산한 예측값입니다.</span></figcaption><div className="finance-line-chart__legend"><span><i style={{ background: '#245fc0' }} />월별 매출 예측</span><span><i style={{ background: '#16826c' }} />누적 현금흐름 예측</span></div><svg viewBox={`0 0 ${width} ${height}`} role="img" aria-label="월별 매출 및 누적 현금흐름 예측"><line x1={left} x2={width - right} y1={cashY(0)} y2={cashY(0)} className="finance-line-chart__zero" /><polyline points={revenue.map((value, index) => `${x(index)},${revenueY(value)}`).join(' ')} fill="none" stroke="#245fc0" strokeWidth="3" strokeLinejoin="round" strokeLinecap="round" /><polyline points={cumulative.map((value, index) => `${x(index)},${cashY(value)}`).join(' ')} fill="none" stroke="#16826c" strokeWidth="3" strokeLinejoin="round" strokeLinecap="round" /><text x={left} y={top - 4} className="finance-line-chart__axis">매출 {fmt(revenueMax)} KRW</text><text x={width - right} y={top - 4} textAnchor="end" className="finance-line-chart__axis">누적현금흐름 {fmt(cashMax)} KRW</text><text x={left} y={height - 12} className="finance-line-chart__axis">1개월</text><text x={width - right} y={height - 12} textAnchor="end" className="finance-line-chart__axis">{revenue.length}개월</text></svg></figure>;
  }
  const values = rows.map((row) => Number(row[field] ?? 0));
  if (!values.length) return null;
  const width = 900; const height = 270; const left = 58; const right = 18; const top = 22; const bottom = 40;
  const min = Math.min(0, ...values); const max = Math.max(0, ...values); const range = max - min || 1;
  const x = (i) => left + i * (width - left - right) / Math.max(values.length - 1, 1);
  const y = (v) => top + (max - v) * (height - top - bottom) / range;
  return <figure className="finance-line-chart"><figcaption><strong>{title}</strong><span>{subtitle}</span></figcaption><svg viewBox={`0 0 ${width} ${height}`} role="img" aria-label={title}><line x1={left} x2={width - right} y1={y(0)} y2={y(0)} className="finance-line-chart__zero" /><polyline points={values.map((v, i) => `${x(i)},${y(v)}`).join(' ')} fill="none" stroke={color} strokeWidth="3" strokeLinejoin="round" strokeLinecap="round" />{values.map((v, i) => <circle key={i} cx={x(i)} cy={y(v)} r="3" fill={color} />)}<text x={left} y={height - 12} className="finance-line-chart__axis">1개월</text><text x={width - right} y={height - 12} textAnchor="end" className="finance-line-chart__axis">{values.length}개월</text></svg></figure>;
}

function ScenarioComparisonChart({ scenarios = [] }) {
  const colors = ['#e05a47', '#245fc0', '#16826c'];
  const series = scenarios.map((scenario, index) => ({ label: scenario.label, color: colors[index % colors.length], values: (scenario.monthlyCashFlow ?? []).map((row) => Number(row.cumulativeCashFlow ?? 0)) }));
  const allValues = series.flatMap((item) => item.values); if (!allValues.length) return null;
  const width = 900; const height = 270; const left = 58; const right = 18; const top = 22; const bottom = 40;
  const min = Math.min(0, ...allValues); const max = Math.max(0, ...allValues); const range = max - min || 1; const months = Math.max(...series.map((item) => item.values.length));
  const x = (i) => left + i * (width - left - right) / Math.max(months - 1, 1); const y = (v) => top + (max - v) * (height - top - bottom) / range;
  return <figure className="finance-line-chart"><figcaption><strong>시나리오별 누적 현금흐름 예측 비교</strong><span>동일한 기간에 보수·기준·낙관 시나리오의 입력 가정으로 계산한 누적 현금흐름 예측입니다.</span></figcaption><div className="finance-line-chart__legend">{series.map((item) => <span key={item.label}><i style={{ background: item.color }} />{item.label} 시나리오</span>)}</div><svg viewBox={`0 0 ${width} ${height}`} role="img" aria-label="시나리오별 누적 현금흐름 예측 비교"><line x1={left} x2={width - right} y1={y(0)} y2={y(0)} className="finance-line-chart__zero" />{series.map((item) => <polyline key={item.label} points={item.values.map((value, index) => `${x(index)},${y(value)}`).join(' ')} fill="none" stroke={item.color} strokeWidth="3" strokeLinejoin="round" strokeLinecap="round" />)}<text x={left} y={height - 12} className="finance-line-chart__axis">1개월</text><text x={width - right} y={height - 12} textAnchor="end" className="finance-line-chart__axis">{months}개월</text></svg></figure>;
}

function Report({ analysis }) {
  if (!analysis) return null;
  const annual = analysis.annualProjections ?? []; const cash = analysis.cashFlowChart ?? [];
  const base = analysis.calculation?.scenarios?.find((item) => item.code === 'BASE') ?? analysis.calculation?.scenarios?.[0];
  const noBep = '분석 기간 안에 손익분기점에 도달하지 못함';
  return <section className="finance-section finance-analysis-report"><h2>재무 분석 결과</h2><p className="finance-note">아래 수치와 그래프는 업로드한 입력값을 바탕으로 계산한 예측값입니다. 실제 성과와 차이가 날 수 있습니다.</p>
    <div className="finance-source__grid"><article><span>몬테카를로 손실 확률</span><strong>{analysis.monteCarlo?.lossProbabilityPercent ?? '-'}%</strong></article><article><span>P50 영업이익</span><strong>{fmt(analysis.monteCarlo?.profitP50)} KRW</strong></article></div>
    <h3>1. 구조화된 3개년 추정 손익계산서</h3><div className="finance-table-wrap"><table><thead><tr><th>구분 (KRW)</th>{annual.map((row) => <th key={row.year}>{row.year}년차</th>)}</tr></thead><tbody>{statementRows.map(([label, key]) => <tr key={key}><th>{label}</th>{annual.map((row) => <td key={row.year}>{fmt(row[key])}</td>)}</tr>)}<tr><th>영업이익률</th>{annual.map((row) => <td key={row.year}>{row.operatingMarginPercent}%</td>)}</tr></tbody></table></div>
    <h3>2. 현금흐름 및 손익분기점(BEP) 예측</h3><p>예상 손익분기점: <b>{base?.breakEvenMonth ? `출시 후 ${base.breakEvenMonth}개월 차` : noBep}</b> · 예상 필요 운전자금: <b>{fmt(base?.requiredWorkingCapital)} KRW</b></p><LineChart title="월별 매출 예측" subtitle="업로드한 가격·고객 수·이탈률 가정을 바탕으로 계산한 월별 매출 예측입니다." rows={cash} field="revenue" color="#245fc0" /><LineChart title="누적 현금흐름 예측" subtitle="매월 예상 현금흐름을 누적한 예측값입니다. 0을 넘으면 초기 투자금 회수 흐름을 의미합니다." rows={cash} field="cumulativeCashFlow" color="#16826c" />
    <h3>3. 시나리오 스트레스 테스트와 몬테카를로 시뮬레이션</h3><p className="finance-note"><b>몬테카를로 시뮬레이션</b>은 가격·판매량·비용처럼 달라질 수 있는 값을 수천 번 무작위로 바꾸어 계산해, 손실 가능성과 수익 범위를 추정하는 방법입니다. P10은 보수적 결과, P50은 가장 가운데 결과, P90은 낙관적 결과입니다.</p><p>총 {fmt(analysis.monteCarlo?.simulations)}회 계산 · P10 {fmt(analysis.monteCarlo?.profitP10)} KRW · P50 {fmt(analysis.monteCarlo?.profitP50)} KRW · P90 {fmt(analysis.monteCarlo?.profitP90)} KRW</p><div className="finance-source__grid">{(analysis.stressScenarios ?? []).map((row) => <article key={row.code}><span>{row.label} 시나리오</span><strong>손익분기점: {row.breakEvenMonth ? `${row.breakEvenMonth}개월 차` : noBep}</strong><small>예상 영업이익 {fmt(row.totalOperatingProfit)} KRW · 필요자금 {fmt(row.requiredWorkingCapital)} KRW</small></article>)}</div><ScenarioComparisonChart scenarios={analysis.stressScenarios ?? []} />
    <h3>4. AI 기반 사업 타당성 및 재무 경제성 분석 최종 보고서</h3><p>{reportKo(analysis.report?.headline)}</p><ul>{(analysis.report?.findings ?? []).map((item) => <li key={item}>{reportKo(item)}</li>)}</ul><h4>주의사항</h4><ul>{(analysis.report?.cautions ?? []).map((item) => <li key={item}>{reportKo(item)}</li>)}</ul><h4>권장 조치</h4><ul>{(analysis.report?.recommendedActions ?? []).map((item) => <li key={item}>{reportKo(item)}</li>)}</ul>
  </section>;
}

export default function FinancePage() {
  const { projectId } = useParams(); const finance = useFinance(projectId); const input = useRef(null); const [file, setFile] = useState(null);
  const download = async () => { const blob = await finance.downloadTemplate(); const url = URL.createObjectURL(blob); const anchor = document.createElement('a'); anchor.href = url; anchor.download = 'financial-input-template.docx'; anchor.click(); URL.revokeObjectURL(url); };
  if (finance.loading) return <section className="finance-state">재무 입력을 불러오는 중입니다.</section>;
  return <main className="finance-page"><header className="finance-heading"><div><p>6. 재무 분석 (선택)</p><h1>파일 기반 재무 분석</h1><span>업로드한 재무 입력값만 계산에 사용합니다.</span></div></header>{finance.error && <p className="finance-error" role="alert">{getUserErrorMessage(finance.error)}</p>}<section className="finance-section"><h2>1. 입력 템플릿 작성</h2><p>DOCX의 재무 입력값 표에 필요한 값을 직접 작성하세요.</p><button className="finance-save" type="button" onClick={() => void download()}>재무 입력 템플릿 다운로드 (.docx)</button></section><section className="finance-section"><h2>2. 작성한 파일 업로드</h2><input ref={input} type="file" accept=".docx" onChange={(event) => setFile(event.target.files?.[0] ?? null)} />{file && <p>{file.name}</p>}<button type="button" disabled={!file || finance.busy === 'import'} onClick={() => void finance.importDocument(file)}>파일 업로드 및 입력값 반영</button></section><section className="finance-section"><h2>3. 재무 분석 실행</h2><p>{finance.preparation?.readyToFinalize ? '입력값이 준비되었습니다.' : '필수 입력값을 작성한 뒤 업로드해 주세요.'}</p>{!finance.snapshot ? <button type="button" disabled={!finance.preparation?.readyToFinalize || finance.busy === 'finalize'} onClick={() => void finance.finalize()}>입력 스냅샷 확정</button> : <button type="button" disabled={finance.busy === 'analysis'} onClick={() => void finance.analyze()}>재무 그래프 및 보고서 생성</button>}</section><Report analysis={finance.analysis} /><Link className="finance-next-step" to={`/app/projects/${projectId}/panel-survey`}>다음 - 패널 조사</Link></main>;
}
