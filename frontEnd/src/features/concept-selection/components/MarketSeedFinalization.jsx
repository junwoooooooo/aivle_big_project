import { Link } from 'react-router-dom';
import { projectRoutes } from '../../../app/routing/projectRoutes.js';

export default function MarketSeedFinalization({ projectId, selection, snapshot, finalizing, onFinalize }) {
  if (!selection) return null;
  return <section className="market-seed-finalization" aria-labelledby="market-seed-finalization-title">
    <header>
      <p>시장 분석 준비</p>
      <h2 id="market-seed-finalization-title">시장 분석에 사용할 입력</h2>
      <span>선택한 사업안과 최종 가설, 법률 검토 결과를 다음 분석에 사용할 내용으로 저장합니다.</span>
    </header>
    {snapshot ? <div className="market-seed-finalization__ready" role="status">
      <strong>시장 분석에 사용할 입력을 저장했습니다.</strong>
      <p>저장 시각 {new Date(snapshot.createdAt).toLocaleString('ko-KR')}</p>
      <details><summary>기술 정보</summary><dl><div><dt>저장 ID</dt><dd>{snapshot.snapshotId}</dd></div><div><dt>형식 버전</dt><dd>{snapshot.schemaVersion}</dd></div><div><dt>식별값</dt><dd>{snapshot.snapshotHash}</dd></div></dl></details>
      <Link to={projectRoutes.market(projectId)}>시장분석으로 이동</Link>
    </div> : <div className="market-seed-finalization__action">
      <p>{selection.decisionComplete
        ? '모든 가설 결정과 필수 법률 검토가 완료되었습니다.'
        : '모든 가설을 확정하고 필요한 법률 검토를 완료해야 합니다.'}</p>
      <button type="button" disabled={!selection.decisionComplete || finalizing} onClick={onFinalize}>
        {finalizing ? '입력 저장 중…' : '시장 분석 입력 저장'}
      </button>
    </div>}
  </section>;
}
