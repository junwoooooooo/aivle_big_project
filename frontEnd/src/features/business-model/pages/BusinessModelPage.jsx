import { useParams } from 'react-router-dom';
import useBusinessModel from '../hooks/useBusinessModel.js';
import '../styles/business-model.css';

const STATUS_LABELS = {
  NOT_CONNECTED: '준비 중', READY: '시작 가능', QUEUED: '대기 중', RUNNING: '분석 중',
  NEEDS_INPUT: '입력 필요', COMPLETED: '완료', FAILED: '확인 필요', STALE: '업데이트 필요',
};

export default function BusinessModelPage() {
  const { projectId } = useParams();
  const state = useBusinessModel(projectId);
  if (state.loading) return <section aria-busy="true">BM 분석 상태를 불러오고 있습니다.</section>;

  const run = state.runs.find((item) => item.module === 'BUSINESS_MODEL');
  return <section className="business-model-shell">
    <header>
      <p>사업 검증</p>
      <h1>수익 구조 분석</h1>
      <span>앞서 확정한 시장 정보를 바탕으로 수익 구조를 검토합니다.</span>
    </header>
    <aside role="note">
      분석 기능을 준비하고 있습니다. 앞서 저장한 입력은 변경되지 않습니다.
    </aside>
    {state.error && <p role="alert">{state.error.message}</p>}
    {!state.marketSeed ? <div className="business-model-shell__empty">
      <strong>시장 분석에 사용할 입력을 먼저 확정해 주세요.</strong>
      <span>사업안 비교·선택 화면에서 필요한 결정을 완료해 주세요.</span>
    </div> : <>
      <div className="business-model-shell__source">
        <strong>확정된 시장 입력</strong><span>시장 분석에서 저장한 내용을 사용합니다.</span>
      </div>
      <article data-status={run?.status ?? 'NOT_CONNECTED'}>
        <header><h2>BM 분석 실행 상태</h2><strong>{STATUS_LABELS[run?.status] ?? STATUS_LABELS.NOT_CONNECTED}</strong></header>
        {run && <details><summary>기술 정보</summary><dl><dt>저장된 입력 ID</dt><dd>{run.inputSnapshotId}</dd><dt>작업 ID</dt><dd>{run.runId}</dd></dl></details>}
        <p>{run?.stale ? '시장 입력이 변경되었습니다. 최신 내용으로 다시 준비해 주세요.' : '결과는 의사결정 참고자료이며 저장된 입력을 자동으로 바꾸지 않습니다.'}</p>
      </article>
      <button type="button" disabled={state.busy} onClick={state.prepare}>수익 구조 분석 준비</button>
    </>}
  </section>;
}
