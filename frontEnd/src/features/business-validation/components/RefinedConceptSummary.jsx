import { Alert, Badge, Card } from '../../../shared/ui';
import { BM_FIELD_LABELS, FIELD_LABELS, fieldLabel, formatRefinementValue, outcomeLabel, sourceLabel } from '../model/refinementView.js';

const HYPOTHESIS_FIELDS = ['targetRegion', 'revenueModel', 'price', 'channels',
  'differentiators', 'preMarketSomShare', 'preMarketSom'];

function ValueRows({ rows, empty = '표시할 값이 없습니다.' }) {
  const visible = rows.filter(([, value]) => value !== null && value !== undefined);
  if (!visible.length) return <p className="refined-concept__empty">{empty}</p>;
  return <dl className="refined-concept__values">{visible.map(([label, value]) =>
    <div key={label}><dt>{label}</dt><dd>{formatRefinementValue(value)}</dd></div>)}</dl>;
}

export default function RefinedConceptSummary({ finalView }) {
  const value = finalView?.value ?? {};
  const concept = value.selectedConcept ?? {};
  const identity = concept.identity ?? {};
  const solution = concept.solution ?? {};
  const operation = concept.operation ?? {};
  const hypotheses = value.finalHypotheses ?? {};
  const bm = value.businessModelPlan ?? {};
  const changes = Array.isArray(value.selectedChanges) ? value.selectedChanges : [];
  const outcome = finalView?.outcome ?? value.outcome;
  const conceptRows = [
    ['컨셉명', identity.conceptName], ['컨셉 정의', identity.conceptDefinition],
    ['타깃 고객', identity.targetUsers], ['핵심 가치', identity.coreValue],
    ['핵심 기능', solution.featureSet], ['운영 방식', operation.operatingModel],
  ];

  return <section className="refined-concept" aria-labelledby="refined-concept-title">
    {finalView?.stale ? <Alert tone="warning">이후 사업안이 변경되어 이 다듬기 결과는 현재 기준이 아닙니다.</Alert> : null}
    <header><div><span>검증 완료</span><small>다듬어진 컨셉</small><h2 id="refined-concept-title">{identity.conceptName ?? '현재 확정 사업안'}</h2><p>{identity.conceptDefinition ?? identity.coreValue ?? '시장과 비즈니스 모델 검증 결과를 반영한 최종 사업안입니다.'}</p></div>
      <Badge tone={finalView?.stale ? 'warning' : 'success'}>{outcomeLabel(outcome)}</Badge></header>

    <div className="refined-concept__snapshots"><Card><h3>시장 결과 기준</h3><p>완료된 시장 검증 snapshot을 기준으로 확정했습니다.</p></Card><Card><h3>사업 모델 기준</h3><p>완료된 사업 모델 판정과 실행 계획을 반영했습니다.</p></Card><Card><h3>변경 여부</h3><strong>{changes.length ? `${changes.length}건 반영` : '변경 없이 유지'}</strong></Card></div>
    <details className="refined-concept__detail" open><summary>확정 사업안 상세</summary><Card><h3>핵심 컨셉</h3><ValueRows rows={conceptRows} /></Card></details>
    <details className="refined-concept__detail"><summary>검증 가설·사업 모델 보기</summary><Card><h3>확정된 검증 가설</h3><ValueRows rows={HYPOTHESIS_FIELDS.map((field) =>
      [FIELD_LABELS[field], hypotheses[field]?.value])} /></Card><Card><h3>사업 모델 실행 계획</h3><ValueRows rows={Object.entries(BM_FIELD_LABELS).map(([field, label]) =>
      [label, bm[field]])} /></Card></details>
    <details className="refined-concept__detail"><summary>반영한 변경 보기</summary><Card><h3>이번에 반영한 변경</h3>{changes.length ? <ul className="refined-concept__changes">
      {changes.map((change, index) => {
        const label = fieldLabel(change.fieldKey);
        if (!label) return null;
        return <li key={`${change.fieldKey}-${index}`}><strong>{label}</strong>
          <span>{formatRefinementValue(change.currentValue)} → {formatRefinementValue(change.proposedValue)}</span>
          {change.rationale ? <small>{change.rationale}</small> : null}
          <small>{sourceLabel(change.source)}</small></li>;
      })}
    </ul> : <p className="refined-concept__empty">적용한 변경 없음</p>}</Card></details>
  </section>;
}
