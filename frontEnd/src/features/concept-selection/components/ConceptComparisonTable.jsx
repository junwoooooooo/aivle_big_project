import { COMPARISON_ROWS } from '../model/conceptComparisonModel.js';

export default function ConceptComparisonTable({ models }) {
  return <section className="comparison-panel" aria-labelledby="comparison-title">
    <header><p>총점이나 자동 순위 없이 항목별 차이를 확인합니다.</p><h2 id="comparison-title">선택한 컨셉 비교</h2></header>
    <div className="comparison-table-wrap">
      <table><caption className="sr-only">선택한 컨셉 항목별 비교표</caption><thead><tr><th scope="col">비교 항목</th>{models.map((model) => <th scope="col" key={model.conceptId}>{model.title}</th>)}</tr></thead>
        <tbody>{COMPARISON_ROWS.map((row) => <tr key={row.key}><th scope="row">{row.label}</th>{models.map((model) => <td key={model.conceptId}>{row.read(model)}</td>)}</tr>)}</tbody></table>
    </div>
    <div className="comparison-mobile" aria-label="모바일 컨셉 비교">{pair(models).map((group, index) => <section key={group.map((item) => item.conceptId).join('-')}><h3>비교 묶음 {index + 1}</h3><div>{group.map((model) => <article key={model.conceptId}><h4>{model.title}</h4><dl>{COMPARISON_ROWS.map((row) => <div key={row.key}><dt>{row.label}</dt><dd>{row.read(model)}</dd></div>)}</dl></article>)}</div></section>)}</div>
  </section>;
}

function pair(items) {
  const groups = [];
  for (let index = 0; index < items.length; index += 2) groups.push(items.slice(index, index + 2));
  return groups;
}
