import { useState } from 'react';

export default function SelectionConfirmation({ preferred, currentSelection, onConfirm }) {
  const [reason, setReason] = useState('');
  const [status, setStatus] = useState('idle');
  const [error, setError] = useState('');
  const confirm = async () => {
    if (!preferred || !reason.trim()) return;
    setStatus('saving');
    setError('');
    try {
      await onConfirm(preferred.conceptId, reason.trim());
      setStatus('saved');
    } catch (failure) {
      setStatus('idle');
      setError(failure?.message ?? '선택을 확정하지 못했습니다.');
    }
  };
  const selectedTitle = currentSelection && preferred?.conceptId === currentSelection.conceptId ? preferred.title : null;
  return <section className="selection-confirmation" aria-labelledby="selection-confirmation-title">
    <div><p>컨셉 선택</p><h2 id="selection-confirmation-title">{preferred ? `${preferred.title}을(를) 선택 후보로 표시했습니다.` : '비교 대상 중 선택 후보를 표시하세요.'}</h2>
      <span>선택 후에는 이 컨셉의 AI 시장 가설만 확인합니다. 시장분석 Snapshot은 모든 결정이 끝난 다음 단계에서 만듭니다.</span></div>
    <label><span>선택 이유</span><textarea rows="3" maxLength="2000" value={reason} onChange={(event) => setReason(event.target.value)} placeholder="이 컨셉을 선택한 판단 근거를 적어주세요." /></label>
    <div className="selection-confirmation__actions"><button type="button" disabled={!preferred || !reason.trim() || status === 'saving'} onClick={confirm}>{status === 'saving' ? '선택 저장 중…' : '이 컨셉 선택 확정'}</button></div>
    {selectedTitle && <p role="status">현재 선택: {selectedTitle} · 아래 가설을 확인해 주세요.</p>}
    {status === 'saved' && !selectedTitle && <p role="status">컨셉을 선택했습니다. 가설 확인 단계로 이동합니다.</p>}
    {error && <p role="alert">{error}</p>}
  </section>;
}
