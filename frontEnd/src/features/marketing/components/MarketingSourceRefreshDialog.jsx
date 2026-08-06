import { useEffect, useState } from 'react';

import { Alert, Button, Dialog, Select } from '../../../shared/ui/index.js';
import { useApiClient } from '../../../shared/api/ApiClientProvider.jsx';
import { createValidationApi } from '../../validation/api/validationApi.js';

export default function MarketingSourceRefreshDialog({
  open,
  current,
  projectId,
  loading,
  onClose,
  onSubmit,
}) {
  const client = useApiClient();
  const [options, setOptions] = useState({
    interviews: [],
    markets: [],
    loading: true,
    error: null,
  });
  const [panelInterviewId, setPanelInterviewId] = useState(current?.panelInterviewId ?? '');
  const [marketResponseId, setMarketResponseId] = useState(current?.marketResponseId ?? '');
  const [generateDraft, setGenerateDraft] = useState(false);

  useEffect(() => {
    const api = createValidationApi(client);
    let active = true;
    Promise.all([api.interviews(projectId), api.marketResponses(projectId)])
      .then(([interviews, markets]) => {
        if (active) setOptions({
          interviews: interviews.filter((item) => item.status === 'COMPLETED'),
          markets: markets.filter((item) => item.status === 'COMPLETED'),
          loading: false,
          error: null,
        });
      })
      .catch((error) => {
        if (active) setOptions({
          interviews: [],
          markets: [],
          loading: false,
          error,
        });
      });
    return () => { active = false; };
  }, [client, projectId]);

  return (
    <Dialog open={open} onClose={onClose} title="검증 결과 다시 불러오기">
      <p>
        현재 Snapshot 생성 시각: {current?.capturedAt
          ? new Date(current.capturedAt).toLocaleString('ko-KR')
          : '확인 불가'}
      </p>
      <Alert tone="info">
        Source만 갱신하면 현재 카피는 유지됩니다. 새 초안을 선택하면 현재 카피를 보존한 새 버전을 만듭니다.
      </Alert>
      {options.loading && <p role="status">사용 가능한 검증 결과를 불러오고 있습니다.</p>}
      {options.error && <Alert tone="danger">검증 결과 목록을 불러오지 못했습니다.</Alert>}
      <Select
        label="패널 인터뷰"
        value={panelInterviewId}
        onChange={(event) => setPanelInterviewId(event.target.value)}
      >
        <option value="">반영하지 않음</option>
        {options.interviews.map((item) => (
          <option key={item.id} value={item.id}>{item.title}</option>
        ))}
      </Select>
      <Select
        label="시장 반응 예측"
        value={marketResponseId}
        onChange={(event) => setMarketResponseId(event.target.value)}
      >
        <option value="">반영하지 않음</option>
        {options.markets.map((item) => (
          <option key={item.id} value={item.id}>{item.title}</option>
        ))}
      </Select>
      <fieldset className="marketing-source-refresh__mode">
        <legend>갱신 방식</legend>
        <label>
          <input
            type="radio"
            checked={!generateDraft}
            onChange={() => setGenerateDraft(false)}
          />
          Source만 갱신
        </label>
        <label>
          <input
            type="radio"
            checked={generateDraft}
            onChange={() => setGenerateDraft(true)}
          />
          Source 갱신 후 새 카피 초안 만들기
        </label>
      </fieldset>
      <div className="marketing-dialog-actions">
        <Button variant="ghost" disabled={loading} onClick={onClose}>취소</Button>
        <Button
          loading={loading}
          disabled={options.loading || Boolean(options.error)}
          onClick={() => onSubmit({
            panelInterviewId: panelInterviewId ? Number(panelInterviewId) : null,
            marketResponseId: marketResponseId ? Number(marketResponseId) : null,
            generateDraft,
          })}
        >
          불러오기
        </Button>
      </div>
    </Dialog>
  );
}
