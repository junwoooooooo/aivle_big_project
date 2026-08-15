import { useCallback, useEffect, useState } from 'react';
import { Alert, Button, TextInput } from '../../shared/ui';

const EMPTY_ROW = { name: '', reason: '', operatorName: '' };
const MAX_ROWS = 8;

export default function CompetitorSeedForm({ api, disabled }) {
  const [rows, setRows] = useState([EMPTY_ROW]);
  const [warning, setWarning] = useState(null);
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);
  const [saved, setSaved] = useState(false);

  const apply = useCallback((view) => {
    const next = (view?.seeds ?? []).map((seed) => ({
      name: seed.name ?? '', reason: seed.reason ?? '', operatorName: seed.operatorName ?? '',
    }));
    setRows(next.length ? next : [EMPTY_ROW]);
    setWarning(view?.warning ?? null);
  }, []);

  useEffect(() => {
    let alive = true;
    api.currentCompetitorSeeds().then((view) => { if (alive) apply(view); }).catch(() => {});
    return () => { alive = false; };
  }, [api, apply]);

  const set = (index, field) => (event) => {
    const value = event.target.value;
    setSaved(false);
    setRows((current) => current.map((row, i) => (i === index ? { ...row, [field]: value } : row)));
  };

  const submit = async (event) => {
    event.preventDefault();
    setBusy(true);
    setError(null);
    try {
      apply(await api.saveCompetitorSeeds(rows));
      setSaved(true);
    } catch (failure) {
      setError(failure?.message ?? '경쟁 씨앗을 저장하지 못했다.');
    } finally {
      setBusy(false);
    }
  };

  const locked = disabled || busy;
  return (
    <form className="competitor-seeds" onSubmit={submit}>
      <p className="competitor-seeds__why">
        이미 이 문제를 풀고 있는 서비스를 아는 만큼 적어라. 조사가 <strong>경쟁을 찾는 출발점</strong>으로
        쓴다. <strong>씨앗이지 조사 결과가 아니다</strong> — 조사가 찾은 것과 합쳐 같은 잣대로 검증한다.
      </p>
      {rows.map((row, index) => (
        <div key={index} className="competitor-seeds__row project-form-layout">
          <TextInput label="이름" value={row.name} onChange={set(index, 'name')} disabled={locked} placeholder="예: 공비서" />
          <TextInput label="왜 경쟁인가" value={row.reason} onChange={set(index, 'reason')} disabled={locked}
            placeholder="예: 노쇼 방지 방식이 우리 차별점과 겹친다" />
          <TextInput label="운영사 (선택)" value={row.operatorName} onChange={set(index, 'operatorName')} disabled={locked}
            placeholder="법인명. 모르면 비워라" />
        </div>
      ))}
      <p className="competitor-seeds__hint">
        운영사는 법인명이다. 공시 자료 조회가 법인명으로만 되므로, 모르면 비우는 편이 안전하다.
      </p>
      <div className="competitor-seeds__actions">
        <Button type="button" variant="secondary" disabled={locked || rows.length >= MAX_ROWS}
          onClick={() => setRows((current) => [...current, EMPTY_ROW])}>줄 추가</Button>
        <Button type="submit" disabled={locked}>{busy ? '저장 중…' : '저장'}</Button>
      </div>
      {error ? <Alert tone="danger">{error}</Alert> : null}
      {saved && !error ? <Alert tone="success">저장했다.</Alert> : null}
      {warning ? <Alert tone="warning">{warning}</Alert> : null}
    </form>
  );
}
