import { Link, useOutletContext, useParams } from 'react-router-dom';
import { projectRoutes } from '../../../app/routing/projectRoutes.js';
import useMarketIntegration from '../hooks/useMarketIntegration.js';
import '../styles/market-integration.css';

const STATUS_LABELS = Object.freeze({
  NOT_CONNECTED: '준비 중', READY: '시작 가능', QUEUED: '대기 중', RUNNING: '분석 중',
  NEEDS_INPUT: '입력 필요', COMPLETED: '분석 완료', FAILED: '확인 필요', STALE: '업데이트 필요',
});

export default function MarketIntegrationPage() {
  const { projectId } = useParams();
  const outlet = useOutletContext() ?? {};
  const market = useMarketIntegration(projectId, outlet.liveRevision);
  if (market.loading) return <section className="market-integration" aria-busy="true"><p>시장분석 상태를 불러오고 있습니다.</p></section>;
  const snapshot = market.marketSeed;
  const latestRun = market.runs[0] ?? null;
  const result = market.result;
  return <main className="market-integration">
    <header><div><p>시장 분석</p><h1>{result ? '시장 분석 결과' : '시장 분석 준비'}</h1>
      <span>{result ? '분석 결과는 확정한 사업안이나 가설을 자동으로 바꾸지 않습니다.' : '확정한 시장 입력을 분석에 사용합니다.'}</span></div>
      <span className="market-integration__status">{STATUS_LABELS[result?.status ?? latestRun?.status] ?? '준비 중'}</span></header>
    {market.error && <section role="alert" className="market-integration__error"><strong>요청을 처리하지 못했습니다.</strong><span>{market.error.message}</span><button type="button" onClick={market.refresh}>다시 시도</button></section>}
    {!result ? <NotConnected market={market} snapshot={snapshot} projectId={projectId} /> : <ResultView result={result} snapshot={snapshot} />}
  </main>;
}

function NotConnected({ market, snapshot, projectId }) {
  return <><section className="market-integration__empty"><strong>준비 중</strong><p>시장 분석 기능을 준비하고 있습니다.</p></section>
    <section className="market-integration__actions"><div><strong>{snapshot ? '저장된 시장 입력으로 분석을 준비할 수 있습니다.' : '먼저 사업안 가설을 결정하고 시장 입력을 저장해 주세요.'}</strong>
      <span>{snapshot ? '저장된 입력이 준비되었습니다.' : '분석에 사용할 입력 없음'}</span></div>
      {snapshot ? <button type="button" onClick={market.prepare} disabled={market.preparing}>{market.preparing ? '분석 준비 중…' : '시장 분석 준비'}</button>
        : <Link to={projectRoutes.conceptCompare(projectId)}>사업안 결정으로 이동</Link>}</section></>;
}

function ResultView({ result, snapshot }) {
  const summary = result.summary ?? {};
  return <>
    {result.stale && <section className="market-integration__stale" role="status"><strong>업데이트가 필요한 결과입니다.</strong><span>현재 저장된 입력과 다른 과거 분석 결과입니다.</span></section>}
    <section className="market-result-section"><h2>시장분석 요약</h2><p>{summary.marketSummary}</p></section>
    <section className="market-result-section"><h2>대상 고객 시사점</h2><BulletList values={summary.targetCustomerImplications} /></section>
    <section className="market-result-section"><h2>가격·채널 시사점</h2><BulletList values={summary.pricingAndChannelImplications} /></section>
    <section className="market-result-section"><h2>경쟁제품</h2><div className="competitor-grid">{result.competitors.map((item) => <CompetitorCard key={`${item.companyName}-${item.productName}`} item={item} />)}</div></section>
    <section className="market-result-section market-result-snapshot"><h2>분석 기준 입력</h2><dl>
      <div><dt>현재 입력과 일치</dt><dd>{result.stale ? '아니요' : '예'}</dd></div>
      <div><dt>분석 완료</dt><dd>{new Date(result.completedAt).toLocaleString('ko-KR')}</dd></div>
    </dl><p>분석 결과는 저장된 입력을 수정하지 않습니다.</p><details><summary>기술 정보</summary><p>분석 입력 ID: {result.inputSnapshotId}</p><p>현재 입력 ID: {snapshot?.snapshotId ?? '없음'}</p><p>결과 참조: {result.resultReference}</p></details></section>
  </>;
}

function BulletList({ values = [] }) { return <ul>{values.map((value) => <li key={value}>{value}</li>)}</ul>; }
function CompetitorCard({ item }) {
  return <article className="competitor-card"><header><div><h3>{item.productName}</h3><span>{item.companyName}</span></div><strong>{item.verificationStatus}</strong></header>
    <p>{item.description}</p><p><strong>대상</strong> {item.targetCustomer}</p><ul>{item.keyFeatures.map((feature) => <li key={feature}>{feature}</li>)}</ul>
    <details><summary>가격 근거와 출처</summary><pre>{JSON.stringify(item.priceEvidence, null, 2)}</pre>{item.sourceReferences.map((source) => <a key={source.url} href={source.url} target="_blank" rel="noreferrer">{source.title}</a>)}</details>
    <a href={item.officialUrl} target="_blank" rel="noreferrer">공식 페이지</a></article>;
}
