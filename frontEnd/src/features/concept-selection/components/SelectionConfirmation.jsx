import { useState } from 'react';
import { Link } from 'react-router-dom';

export default function SelectionConfirmation({ preferred, currentSelection, marketHref, onConfirm }) {
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
    <div><p>R4B 선택 확정</p><h2 id="selection-confirmation-title">{preferred ? `${preferred.title}을(를) 선택 후보로 표시했습니다.` : '비교 대상 중 선택 후보를 표시하세요.'}</h2>
      <span>확정할 때 전체 기획과 법률 Assessment를 불변 Snapshot으로 저장합니다. 시장분석 시작 전에는 새 선택으로 변경할 수 있습니다.</span></div>
    <label><span>선택 이유</span><textarea rows="3" maxLength="2000" value={reason} onChange={(event) => setReason(event.target.value)} placeholder="이 컨셉을 선택한 판단 근거를 적어주세요." /></label>
    <div className="selection-confirmation__actions"><button type="button" disabled={!preferred || !reason.trim() || status === 'saving'} onClick={confirm}>{status === 'saving' ? 'Snapshot 저장 중…' : '이 컨셉 선택 확정'}</button>
      {currentSelection && <Link to={marketHref}>시장분석 전달 준비 보기</Link>}</div>
    {selectedTitle && <p role="status">현재 선택: {selectedTitle} · Snapshot이 저장되었습니다.</p>}
    {status === 'saved' && !selectedTitle && <p role="status">선택 Snapshot을 저장했습니다.</p>}
    {error && <p role="alert">{error}</p>}
  </section>;
}
