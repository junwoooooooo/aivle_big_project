import { useParams } from 'react-router-dom';
import useBusinessModel from '../hooks/useBusinessModel.js';
import '../styles/business-model.css';

const STATUS_LABELS = {
  NOT_CONNECTED: '연결 준비 중', READY: '시작 가능', QUEUED: '대기 중', RUNNING: '분석 중',
  NEEDS_INPUT: '입력 필요', COMPLETED: '완료', FAILED: '실패', STALE: '갱신 필요',
};

export default function BusinessModelPage() {
  const { projectId } = useParams();
  const state = useBusinessModel(projectId);
  if (state.loading) return <section aria-busy="true">BM 분석 상태를 불러오고 있습니다.</section>;

  const run = state.runs.find((item) => item.module === 'BUSINESS_MODEL');
  return <section className="business-model-shell">
    <header>
      <p>외부 분석 모듈</p>
      <h1>BM 분석</h1>
      <span>확정된 Market Seed 스냅샷을 불변 입력으로 전달합니다.</span>
    </header>
    <aside role="note">
      외부 BM 분석 알고리즘은 아직 연결되지 않았습니다. 준비 작업은 입력 스냅샷을 변경하지 않습니다.
    </aside>
    {state.error && <p role="alert">{state.error.message}</p>}
    {!state.marketSeed ? <div className="business-model-shell__empty">
      <strong>Market Seed 확정이 필요합니다.</strong>
      <span>콘셉트 비교·선택 단계에서 가설 결정을 완료해 주세요.</span>
    </div> : <>
      <div className="business-model-shell__source">
        <strong>Market Seed</strong><span>입력 Snapshot: {state.marketSeed.snapshotId}</span>
      </div>
      <article data-status={run?.status ?? 'NOT_CONNECTED'}>
        <header><h2>BM 분석 실행 상태</h2><strong>{STATUS_LABELS[run?.status] ?? STATUS_LABELS.NOT_CONNECTED}</strong></header>
        {run && <dl><dt>입력 Snapshot</dt><dd>{run.inputSnapshotId}</dd><dt>Run</dt><dd>{run.runId}</dd></dl>}
        <p>{run?.stale ? '현재 Market Seed와 다른 입력이므로 다시 준비해야 합니다.' : '결과는 의사결정 참고자료이며 원본 입력을 자동 변경하지 않습니다.'}</p>
      </article>
      <button type="button" disabled={state.busy} onClick={state.prepare}>BM Handoff 준비</button>
    </>}
  </section>;
}
