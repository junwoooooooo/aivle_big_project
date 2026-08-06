import { Link, useParams } from 'react-router-dom';
import { projectRoutes } from '../../../app/routing/projectRoutes.js';
import PlanningRevisionPanel from '../../planning-revision/components/PlanningRevisionPanel.jsx';
import useMarketIntegration from '../hooks/useMarketIntegration.js';
import '../styles/market-integration.css';

const STATUS_LABELS = Object.freeze({ NOT_CONNECTED: '연결 준비 중', READY: '전달 가능', QUEUED: '대기 중', RUNNING: '분석 중', NEEDS_INPUT: '추가 입력 필요', COMPLETED: '분석 완료', FAILED: '실패', STALE: '이전 Snapshot 기준' });

export default function MarketIntegrationPage() {
  const { projectId } = useParams();
  const market = useMarketIntegration(projectId);
  if (market.loading) return <section className="market-integration" aria-busy="true"><p>시장분석 결과를 불러오고 있습니다.</p></section>;
  const snapshot = market.selection?.snapshot;
  const latestRun = market.runs[0] ?? null;
  const result = market.result;
  return <main className="market-integration">
    <header><div><p>외부 시장분석</p><h1>{result ? '시장분석 결과와 기획 변경 제안' : '시장분석 모듈 연결 준비'}</h1><span>{result ? '근거가 확인된 경쟁상품과 제안을 검토하고 기획에 반영할 범위를 결정하세요.' : '외부 모듈이 연결되기 전에는 분석이 완료된 것처럼 표시하지 않습니다.'}</span></div><span className="market-integration__status">{STATUS_LABELS[result?.status ?? latestRun?.status] ?? '연결 준비 중'}</span></header>
    {market.error && <section role="alert" className="market-integration__error"><strong>요청을 처리하지 못했습니다.</strong><span>{market.error.message}</span><button type="button" onClick={market.refresh}>다시 시도</button></section>}
    {!result ? <NotConnected market={market} snapshot={snapshot} projectId={projectId} /> : <ResultView result={result} snapshot={snapshot} projectId={projectId} />}
  </main>;
}

function NotConnected({ market, snapshot, projectId }) {
  return <><section className="market-integration__empty"><strong>Not Connected</strong><p>현재는 안전한 Handoff 계약과 개발용 fixture 수신 경로만 준비되어 있습니다.</p></section><section className="market-integration__actions"><div><strong>{snapshot ? '선택한 컨셉 Snapshot을 전달할 수 있습니다.' : '먼저 컨셉을 선택해 주세요.'}</strong><span>{snapshot?.snapshotId ?? '분석 기준 Snapshot 없음'}</span></div>{snapshot ? <button type="button" onClick={market.prepare} disabled={market.preparing}>{market.preparing ? 'Handoff 준비 중…' : '시장분석 Handoff 준비'}</button> : <Link to={projectRoutes.conceptCompare(projectId)}>컨셉 선택하기</Link>}</section></>;
}

function ResultView({ result, snapshot, projectId }) {
  const summary = result.summary ?? {};
  return <>
    {result.stale && <section className="market-integration__stale" role="status"><strong>이 결과는 과거 Snapshot 기준입니다.</strong><span>현재 선택을 변경했으므로 참고용으로만 확인하세요.</span></section>}
    <section className="market-result-section"><h2>시장분석 요약</h2><p>{summary.marketSummary}</p></section>
    <section className="market-result-section"><h2>타깃 고객 시사점</h2><BulletList values={summary.targetCustomerImplications} /></section>
    <section className="market-result-section"><h2>가격·채널 시사점</h2><BulletList values={summary.pricingAndChannelImplications} /></section>
    <section className="market-result-section"><h2>경쟁상품</h2><div className="competitor-grid">{result.competitors.map((item) => <CompetitorCard key={`${item.companyName}-${item.productName}`} item={item} />)}</div></section>
    <PlanningRevisionPanel projectId={projectId} />
    <section className="market-result-section market-result-snapshot"><h2>분석 기준 Snapshot</h2><dl><div><dt>Snapshot ID</dt><dd>{result.inputSnapshotId}</dd></div><div><dt>현재 선택과 일치</dt><dd>{result.stale ? '아니요' : '예'}</dd></div><div><dt>분석 완료</dt><dd>{new Date(result.completedAt).toLocaleString('ko-KR')}</dd></div><div><dt>결과 참조</dt><dd>{result.resultReference}</dd></div></dl><small>화면에는 v1, v2 같은 버전 번호 대신 의미와 분석 기준을 표시합니다. 현재 Snapshot: {snapshot?.snapshotId ?? '없음'}</small></section>
  </>;
}

function BulletList({ values = [] }) { return <ul>{values.map((value) => <li key={value}>{value}</li>)}</ul>; }
function CompetitorCard({ item }) { return <article className="competitor-card"><header><div><h3>{item.productName}</h3><span>{item.companyName}</span></div><strong>{item.verificationStatus}</strong></header><p>{item.description}</p><p><strong>타깃</strong> {item.targetCustomer}</p><ul>{item.keyFeatures.map((feature) => <li key={feature}>{feature}</li>)}</ul><details><summary>가격 근거와 출처</summary><pre>{JSON.stringify(item.priceEvidence, null, 2)}</pre>{item.sourceReferences.map((source) => <a key={source.url} href={source.url} target="_blank" rel="noreferrer">{source.title}</a>)}</details><a href={item.officialUrl} target="_blank" rel="noreferrer">공식 페이지</a></article>; }
