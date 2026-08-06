import { money } from '../model/financialModel.js';

export default function FinancialResultSummary({ result, preview = false }) {
  const base = result?.scenarios?.find((item) => item.code === 'BASE') ?? result?.scenarios?.[0];
  if (!base) return preview ? null : <section className="financial-results"><h2>재무 분석 결과</h2><p>아직 저장된 완료 결과가 없습니다.</p></section>;
  const profitable = Number(base.totalOperatingProfit) >= 0;
  return <section className="financial-results" aria-labelledby="financial-result-title">
    <div className="financial-results__heading"><h2 id="financial-result-title">{preview ? '현재 입력 기준 미리보기' : '저장된 재무 분석 결과'}</h2><strong className={`financial-profit-label financial-profit-label--${profitable ? 'positive' : 'negative'}`}>{profitable ? '흑자' : '적자'}</strong></div>
    <div className="financial-metrics">
      <div><span>{preview ? '월 예상 매출' : '총 매출'}</span><strong>{money(base.totalRevenue)}</strong></div>
      <div><span>{preview ? '월 예상 손익' : '총 영업손익'}</span><strong>{money(base.totalOperatingProfit)}</strong></div>
      <div><span>손익분기 월</span><strong>{base.breakEvenMonth ? `${base.breakEvenMonth}개월` : '손익분기 미도달'}</strong></div>
      <div><span>투자 회수 월</span><strong>{base.paybackMonth != null ? `${base.paybackMonth}개월` : '회수 미도달'}</strong></div>
      <div><span>필요 운영자금</span><strong>{money(base.requiredWorkingCapital)}</strong></div>
    </div>
    {result.summary?.headline && <p>{result.summary.headline}</p>}
    {result.summary?.disclaimer && <p>{result.summary.disclaimer}</p>}
  </section>;
}
