import { useState } from 'react';

const LABELS = Object.freeze({
  REVENUE_MODEL: '수익 모델', PRICE: '가격', CHANNELS: '판매·확장 채널',
  DIFFERENTIATORS: '차별점', PRE_MARKET_SOM_SHARE: '시장분석 전 목표 점유율 가설',
  PRE_MARKET_SOM: '시장분석 전 SOM 금액 가설',
});

export default function HypothesisDecisionPanel({ selection, onAction }) {
  const hypotheses = selection?.hypotheses ?? [];
  return <section className="hypothesis-decisions" aria-labelledby="hypothesis-decisions-title">
    <header><p>선택한 컨셉만 확인</p><h2 id="hypothesis-decisions-title">AI 시장 가설 최종 결정</h2>
      <span>사용자가 이미 입력한 값은 읽기 전용입니다. AI 제안은 채택하거나 수정하거나 다른 제안을 받을 수 있습니다.</span></header>
    <div aria-live="polite">{selection.decisionComplete ? '모든 필수 가설 결정이 완료되었습니다.' : '확인하지 않은 가설이 남아 있습니다.'}</div>
    {hypotheses.map((hypothesis) => <HypothesisRow key={hypothesis.decisionId} hypothesis={hypothesis} onAction={onAction} />)}
  </section>;
}

function HypothesisRow({ hypothesis, onAction }) {
  const [editing, setEditing] = useState(false);
  const [draft, setDraft] = useState(formatValue(hypothesis.proposedValue, true));
  const [status, setStatus] = useState('idle');
  const [error, setError] = useState('');
  const run = async (action, value) => {
    setStatus(action === 'REQUEST_ALTERNATIVE' ? 'alternative' : 'checking');
    setError('');
    try {
      await onAction(hypothesis.hypothesisType, action, hypothesis.proposalVersion, value);
      setEditing(false);
      setStatus('idle');
    } catch (failure) {
      setStatus('idle');
      setError(failure?.message ?? '가설 결정을 저장하지 못했습니다.');
    }
  };
  const acceptEdit = () => {
    try { run('EDIT_AND_ACCEPT', parseValue(hypothesis.proposedValue, draft)); }
    catch { setError('구조화된 가설 값은 올바른 JSON 형식으로 입력해 주세요.'); }
  };
  const accepted = ['ACCEPTED', 'USER_EDITED_ACCEPTED'].includes(hypothesis.decisionStatus);
  return <article className="hypothesis-decision">
    <header><h3>{LABELS[hypothesis.hypothesisType]}</h3><span>{badge(hypothesis)}</span></header>
    <pre>{formatValue(hypothesis.finalValue ?? hypothesis.proposedValue, true)}</pre>
    {hypothesis.legalReviewStatus === 'FAILED' && <p role="alert">변경한 값은 법률 적격 검토를 통과하지 못해 확정되지 않았습니다. 다른 제안을 선택할 수 있습니다.</p>}
    {!hypothesis.locked && !accepted && <div className="hypothesis-decision__actions">
      <button type="button" disabled={status !== 'idle'} onClick={() => run('ACCEPT')}>
        {status === 'checking' && hypothesis.legalImpact === 'LEGAL_SENSITIVE' && hypothesis.proposalVersion > 1 ? '법률 영향 확인 중…' : '채택'}
      </button>
      <button type="button" disabled={status !== 'idle'} onClick={() => setEditing((value) => !value)}>수정 후 채택</button>
      <button type="button" disabled={status !== 'idle'} onClick={() => run('REQUEST_ALTERNATIVE')}>{status === 'alternative' ? '다른 제안 생성 중…' : '다른 제안'}</button>
    </div>}
    {editing && <div><label><span>수정값</span><textarea rows="5" value={draft} onChange={(event) => setDraft(event.target.value)} /></label>
      <button type="button" disabled={status !== 'idle'} onClick={acceptEdit}>{status === 'checking' && hypothesis.legalImpact === 'LEGAL_SENSITIVE' ? '법률 영향 확인 중…' : '수정값 채택'}</button></div>}
    {error && <p role="alert">{error}</p>}
  </article>;
}

function badge(value) {
  if (value.locked) return '사용자가 입력 · 확정됨';
  if (value.legalReviewStatus === 'FAILED') return '법률 검토 미통과 · 대안 필요';
  if (value.decisionStatus === 'USER_EDITED_ACCEPTED') return '사용자가 수정해 확정';
  if (value.decisionStatus === 'ACCEPTED') return '확정됨';
  if (value.decisionStatus === 'ALTERNATIVE_PROPOSED') return '새 AI 제안 · 확인 필요';
  return 'AI 제안 · 확인 필요';
}

function formatValue(value, pretty = false) {
  return typeof value === 'string' ? value : JSON.stringify(value, null, pretty ? 2 : 0);
}

function parseValue(original, value) { return typeof original === 'string' ? value.trim() : JSON.parse(value); }
