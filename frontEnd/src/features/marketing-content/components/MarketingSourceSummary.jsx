import { displayValue } from '../model/marketingContentModel.js';

const ITEMS = [
  ['conceptName', '선택 Concept'], ['targetSegment', '대상 고객'], ['valueProposition', '핵심 가치'],
  ['positioning', '포지셔닝'], ['keyFeatures', '주요 기능'], ['channels', '채널'],
  ['competitorDifferentiators', '차별점'], ['allowedClaims', '허용 주장'],
  ['prohibitedClaims', '금지 표현'], ['requiredDisclosures', '필수 고지'],
  ['communicationRequiredControls', '커뮤니케이션 통제조건'],
];

export default function MarketingSourceSummary({ source }) {
  return <section className="mk-panel mk-source" aria-labelledby="mk-source-title">
    <div className="mk-panel__heading"><div><p>Source 요약</p><h2 id="mk-source-title">Marketing Source Snapshot</h2></div></div>
    <dl>{ITEMS.map(([key, label]) => <div key={key}
      data-sensitive={['prohibitedClaims', 'requiredDisclosures', 'communicationRequiredControls'].includes(key)}>
      <dt>{label}</dt><dd>{displayValue(source[key])}</dd></div>)}</dl>
    <p className="mk-source__date">Snapshot 기준 · {source.capturedAt
      ? new Date(source.capturedAt).toLocaleString('ko-KR') : '아직 확정되지 않음'}</p>
  </section>;
}
