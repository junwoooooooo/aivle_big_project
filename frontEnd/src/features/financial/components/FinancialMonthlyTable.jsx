import { money } from '../model/financialModel.js';

export default function FinancialMonthlyTable({ result }) {
  const base = result?.scenarios?.find((item) => item.code === 'BASE');
  if (!base) return null;
  return <div className="financial-table-wrap" role="region" aria-label="월별 결과 표, 가로로 스크롤할 수 있습니다." tabIndex="0">
    <table><caption>기준 시나리오 월별 매출·비용·손익과 누적 현금흐름</caption><thead><tr><th>월</th><th>판매량·활성 고객</th><th>매출</th><th>변동비</th><th>공헌이익</th><th>고정비</th><th>영업손익</th><th>누적 현금흐름</th></tr></thead>
      <tbody>{base.months.map((month) => <tr key={month.month}><td>{month.month}</td><td>{Number(month.salesVolume).toLocaleString('ko-KR')}</td><td>{money(month.revenue)}</td><td>{money(month.variableCost)}</td><td>{money(month.contributionMargin)}</td><td>{money(month.fixedCost)}</td><td>{money(month.operatingProfit)}</td><td>{money(month.cumulativeCashFlow)}</td></tr>)}</tbody>
    </table>
  </div>;
}
