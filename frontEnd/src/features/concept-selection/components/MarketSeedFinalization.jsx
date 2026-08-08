import { Link } from 'react-router-dom';
import { projectRoutes } from '../../../app/routing/projectRoutes.js';

export default function MarketSeedFinalization({ projectId, selection, snapshot, finalizing, onFinalize }) {
  if (!selection) return null;
  return <section className="market-seed-finalization" aria-labelledby="market-seed-finalization-title">
    <header>
      <p>시장분석 공식 입력</p>
      <h2 id="market-seed-finalization-title">Market Analysis Seed Snapshot</h2>
      <span>확정하면 원본 Seed, AI 해석, 선택 Concept, 최종 가설과 법률 결과를 변경 불가능한 입력으로 묶습니다.</span>
    </header>
    {snapshot ? <div className="market-seed-finalization__ready" role="status">
      <strong>시장분석 입력 Snapshot이 확정되었습니다.</strong>
      <dl>
        <div><dt>Snapshot ID</dt><dd>{snapshot.snapshotId}</dd></div>
        <div><dt>Schema</dt><dd>{snapshot.schemaVersion}</dd></div>
        <div><dt>Hash</dt><dd>{snapshot.snapshotHash}</dd></div>
        <div><dt>생성 시각</dt><dd>{new Date(snapshot.createdAt).toLocaleString('ko-KR')}</dd></div>
      </dl>
      <Link to={projectRoutes.market(projectId)}>시장분석으로 이동</Link>
    </div> : <div className="market-seed-finalization__action">
      <p>{selection.decisionComplete
        ? '모든 가설 결정과 필수 법률 검토가 완료되었습니다.'
        : '모든 가설을 확정하고 필요한 Delta Legal Review를 통과해야 합니다.'}</p>
      <button type="button" disabled={!selection.decisionComplete || finalizing} onClick={onFinalize}>
        {finalizing ? 'Snapshot 확정 중…' : '시장분석 Seed 확정'}
      </button>
    </div>}
  </section>;
}
