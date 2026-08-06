import { displayValue } from '../model/marketingContentModel.js';

const ITEMS = [['conceptName','최종 확정 기획명'],['targetSegment','대상 고객'],['valueProposition','핵심 가치'],['positioning','포지셔닝'],['keyFeatures','주요 기능'],['channels','채널'],['competitorDifferentiators','경쟁상품 차별점'],['allowedClaims','허용 주장'],['prohibitedClaims','금지 표현'],['requiredDisclosures','필수 고지']];
export default function MarketingSourceSummary({ source }) {
  return <section className="mk-panel mk-source" aria-labelledby="mk-source-title">
    <div className="mk-panel__heading"><div><p>Source 요약</p><h2 id="mk-source-title">최종 확정 기획 기준</h2></div></div>
    <dl>{ITEMS.map(([key,label])=><div key={key} data-sensitive={key==='prohibitedClaims'||key==='requiredDisclosures'}><dt>{label}</dt><dd>{displayValue(source[key])}</dd></div>)}</dl>
    <p className="mk-source__date">Snapshot 기준일 · {source.capturedAt ? new Date(source.capturedAt).toLocaleString('ko-KR') : '서버 기준일 미제공'}</p>
  </section>;
}
