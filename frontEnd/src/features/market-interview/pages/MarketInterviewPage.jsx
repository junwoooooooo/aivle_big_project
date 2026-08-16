import { useCallback, useEffect, useMemo, useState } from 'react';
import { useOutletContext, useParams } from 'react-router-dom';

import { useApiClient } from '../../../shared/api/ApiClientProvider.jsx';
import { getUserErrorMessage } from '../../../shared/api/apiError.js';
import { Alert, Button, LoadingState, ProjectStageHeader, ProjectWorkspace } from '../../../shared/ui/index.js';
import { createMarketInterviewApi } from '../api/marketInterviewApi.js';
import MarketInterviewResult from '../components/MarketInterviewResult.jsx';
import { marketInterviewView } from '../model/marketInterviewView.js';
import '../styles/market-interview.css';

export default function MarketInterviewPage() {
  const { projectId } = useParams();
  const client = useApiClient();
  const { liveRevision = 0 } = useOutletContext() ?? {};
  const api = useMemo(() => createMarketInterviewApi(client, projectId), [client, projectId]);
  const [current, setCurrent] = useState(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);

  const refresh = useCallback(async () => {
    try { setCurrent(marketInterviewView(await api.current())); setError(null); }
    catch (failure) { setError(getUserErrorMessage(failure)); }
    finally { setLoading(false); }
  }, [api]);

  useEffect(() => { void refresh(); }, [refresh, liveRevision]);

  const command = useCallback(async (action) => {
    setBusy(true); setError(null);
    try { setCurrent(marketInterviewView(await action())); }
    catch (failure) {
      try { setCurrent(marketInterviewView(await api.current())); }
      catch { setError(getUserErrorMessage(failure)); }
    } finally { setBusy(false); }
  }, [api]);

  if (loading) return <LoadingState label="시장 인터뷰 상태를 불러오는 중" />;
  const view = current ?? marketInterviewView(null);
  return <ProjectWorkspace as="section" mode="analyze" className="market-interview">
    <ProjectStageHeader step={6} eyebrow="정성적 고객 탐색" title="시장 인터뷰"
      description="현재 확정된 사업안을 여러 가상 고객 관점에서 살펴보고, 실제 고객에게 확인할 질문을 준비합니다." />
    <Alert tone="info" title="AI 가상 고객 인터뷰">
      AI가 현재 사업안과 검증 결과를 바탕으로 가상의 고객 관점에서 인터뷰를 시뮬레이션합니다. 실제 고객에게 조사한 결과는 아닙니다.
    </Alert>
    {error ? <Alert tone="danger">{error}</Alert> : null}
    {view.stale || view.state === 'STALE' ? <Alert tone="warning" title="이전 사업안 기준 결과입니다">
      사업안이 변경되어 이 인터뷰는 이전 버전 기준입니다. 현재 사업안으로 다시 인터뷰해 주세요.
    </Alert> : null}
    {view.active ? <Alert tone="info" title="시장 인터뷰 진행 중">
      가상 고객 관점에서 사업안을 검토하고 있습니다. 실제 고객에게 연락하거나 조사하는 과정은 아닙니다.
    </Alert> : null}
    {view.state === 'FAILED' ? <Alert tone="danger" title="시장 인터뷰를 완료하지 못했습니다">
      {view.failure ?? '잠시 후 다시 시도해 주세요.'}
    </Alert> : null}

    {view.state === 'NOT_STARTED' ? <section className="market-interview__start">
      <h2>현재 사업안을 기준으로 시작합니다</h2><p>확정된 고객, 문제, 해결 방식, 가격·채널·가치제안 맥락만 사용하며 사업안 자체는 변경하지 않습니다.</p>
      <Button disabled={busy} loading={busy} onClick={() => void command(api.start)}>시장 인터뷰 시작</Button>
    </section> : null}
    {view.state === 'STALE' ? <div className="market-interview__actions">
      <Button disabled={busy} loading={busy} onClick={() => void command(api.start)}>현재 사업안으로 다시 인터뷰</Button>
    </div> : null}
    {view.canRetry ? <div className="market-interview__actions">
      <Button disabled={busy} loading={busy} onClick={() => void command(api.retry)}>다시 시도</Button>
    </div> : null}
    {view.result ? <><Alert tone="warning">아래 내용은 가상의 정성적 관점입니다. 시장 근거나 통계로 인용하지 말고 실제 고객 인터뷰 준비에 사용해 주세요.</Alert>
      <MarketInterviewResult result={view.result} /></> : null}
  </ProjectWorkspace>;
}
