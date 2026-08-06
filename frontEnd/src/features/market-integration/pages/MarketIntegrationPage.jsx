import { Link, useParams } from 'react-router-dom';

import { projectRoutes } from '../../../app/routing/projectRoutes.js';
import useMarketIntegration from '../hooks/useMarketIntegration.js';
import '../styles/market-integration.css';

const STATUS_LABELS = Object.freeze({ NOT_CONNECTED: '연결 준비 중', READY: '전달 가능', QUEUED: '대기 중', RUNNING: '분석 중', COMPLETED: '완료', FAILED: '실패', STALE: '이전 선택 기준' });

export default function MarketIntegrationPage() {
  const { projectId } = useParams();
  const market = useMarketIntegration(projectId);
  if (market.loading) return <section className="market-integration" aria-busy="true"><p>시장분석 연결 상태를 불러오고 있습니다.</p></section>;
  const snapshot = market.selection?.snapshot;
  const body = snapshot?.body ?? {};
  const concept = body.concept ?? {};
  const latestRun = market.runs[0] ?? null;
  return <main className="market-integration">
    <header><div><p>외부 Module Integration</p><h1>시장분석 모듈 연결 준비 중</h1><span>외부 시장분석 알고리즘은 아직 연결되지 않았습니다. 선택 Snapshot과 전달 계약만 안전하게 준비합니다.</span></div><span className="market-integration__status">{STATUS_LABELS[latestRun?.status] ?? 'NOT CONNECTED'}</span></header>
    {market.error && <section role="alert" className="market-integration__error"><strong>상태를 처리하지 못했습니다.</strong><span>{market.error.message}</span><button type="button" onClick={market.refresh}>다시 시도</button></section>}
    <section className="market-integration__grid">
      <Info title="선택 컨셉" value={concept.title ?? '선택된 컨셉 없음'} detail={concept.summary ?? '컨셉 비교에서 하나를 선택해 주세요.'} />
      <Info title="입력 Snapshot" value={snapshot?.snapshotId ?? '준비 전'} detail={snapshot ? `${snapshot.snapshotHash} · 선택 이유: ${market.selection.selectionReason}` : '선택 확정 후 불변 Snapshot이 만들어집니다.'} />
      <Info title="전달 준비 상태" value={market.handoff ? 'Handoff 준비 완료' : snapshot ? 'Snapshot 준비 완료' : '선택 필요'} detail={market.handoff?.handoffId ?? '시장분석 전달 Action 전입니다.'} />
      <Info title="외부 Module 상태" value={STATUS_LABELS[latestRun?.status] ?? '연결 준비 중'} detail={latestRun ? `Run ${latestRun.runId}` : '외부 DB나 Entity를 직접 참조하지 않습니다.'} />
    </section>
    <section className="market-integration__manifest"><h2>전달 예정 항목</h2><ul><li>전체 Concept 기획</li><li>법률 Assessment와 필수 통제</li><li>필수 파트너·필수 고지·금지 변형</li><li>공식 Evidence Reference</li><li>Snapshot ID·Hash·선택 시각</li></ul></section>
    <section className="market-integration__actions"><div><strong>페이지는 항상 열려 있습니다.</strong><span>{snapshot ? 'Handoff를 준비해도 실제 외부 분석 완료로 표시되지 않습니다.' : '실행 Action만 Selection 필요 조건을 확인합니다.'}</span></div>
      {snapshot ? <button type="button" onClick={market.prepare} disabled={market.preparing}>{market.preparing ? 'Handoff 준비 중…' : '시장분석 Handoff 준비'}</button> : <Link to={projectRoutes.conceptCompare(projectId)}>컨셉 선택하러 가기</Link>}</section>
    {market.runs.length > 0 && <section className="market-integration__runs"><h2>Module Run 이력</h2><ul>{market.runs.map((run) => <li key={run.runId}><div><strong>{STATUS_LABELS[run.status] ?? run.status}</strong><span>{run.inputSnapshotId}</span></div><small>{run.stale ? '현재 선택과 다른 입력이며 기존 Run은 보존됩니다.' : '현재 선택 Snapshot 기준'}</small></li>)}</ul></section>}
  </main>;
}

function Info({ title, value, detail }) { return <article><span>{title}</span><strong>{value}</strong><p>{detail}</p></article>; }
