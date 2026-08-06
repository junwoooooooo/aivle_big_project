import { money } from '../model/financialModel.js';

const VARIABLE_LABEL = { VOLUME: '판매량', PRICE: '가격', VARIABLE_COST: '변동비', FIXED_COST: '고정비' };

export default function FinancialSensitivitySection({ result }) {
  if (!result?.sensitivity?.length) return null;
  return <section aria-labelledby="financial-sensitivity-title"><h2 id="financial-sensitivity-title">민감도 분석</h2><p>한 번에 하나의 가정만 바꿔 기준 결과와 비교합니다.</p>
    <div className="financial-sensitivity">{result.sensitivity.map((item, index) => <div key={`${item.variable}-${item.adjustment}-${index}`}><strong>{VARIABLE_LABEL[item.variable] ?? item.label} {item.adjustment > 0 ? '+' : ''}{item.adjustment}%</strong><span>영업손익 {money(item.totalOperatingProfit)}</span><span>손익분기 {item.breakEvenMonth ? `${item.breakEvenMonth}개월` : '미도달'}</span><span>운영자금 {money(item.requiredWorkingCapital)}</span></div>)}</div>
  </section>;
}
