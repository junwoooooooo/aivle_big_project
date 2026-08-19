import { useCallback, useEffect, useMemo, useState } from 'react';
import { useOutletContext, useParams } from 'react-router-dom';

import { useApiClient } from '../../../shared/api/ApiClientProvider.jsx';
import { getUserErrorMessage } from '../../../shared/api/apiError.js';
import { Alert, LoadingState, ProjectStageHeader, ProjectWorkspace } from '../../../shared/ui';
import { createBusinessValidationApi } from '../api/businessValidationApi.js';
import ConceptRefinementPanel from '../components/ConceptRefinementPanel.jsx';
import { resolveRefinementCycle } from '../model/refinementView.js';
import '../styles/business-validation.css';

export default function ConceptRefinementPage() {
  const { projectId } = useParams();
  const { liveRevision = 0 } = useOutletContext() ?? {};
  const client = useApiClient();
  const api = useMemo(() => createBusinessValidationApi(client, projectId), [client, projectId]);
  const [validation, setValidation] = useState(null);
  const [refinement, setRefinement] = useState(null);
  const [finalView, setFinalView] = useState(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);

  const refresh = useCallback(async () => {
    const [nextValidation, nextRefinement, nextFinal] = await Promise.allSettled([
      api.current(), api.currentRefinement(), api.currentRefinementFinal(),
    ]);
    if (nextValidation.status === 'fulfilled') setValidation(nextValidation.value);
    if (nextRefinement.status === 'fulfilled') setRefinement(nextRefinement.value);
    else setRefinement((previous) => previous ?? { state: 'UNAVAILABLE', stale: false });
    if (nextFinal.status === 'fulfilled') setFinalView(nextFinal.value);
    const failure = [nextValidation, nextRefinement, nextFinal]
      .find((result) => result.status === 'rejected');
    setError(failure ? getUserErrorMessage(failure.reason) : null);
    setLoading(false);
  }, [api]);

  useEffect(() => {
    const timer = setTimeout(refresh, 0);
    return () => clearTimeout(timer);
  }, [liveRevision, refresh]);

  const act = useCallback(async (action) => {
    setBusy(true);
    setError(null);
    try { await action(); }
    catch (failure) {
      await refresh();
      setError(getUserErrorMessage(failure));
    } finally { setBusy(false); }
  }, [refresh]);

  const applyBody = useCallback((source = refinement) => ({
    expectedRound: source?.round,
    expectedDecisionHash: source?.decision?.decisionHash ?? null,
  }), [refinement]);
  const { effectiveRefinement, effectiveFinal } = resolveRefinementCycle({
    validation, refinement, finalView,
  });
  const refinementStarted = effectiveRefinement?.state
    && !['NOT_STARTED', 'UNAVAILABLE'].includes(effectiveRefinement.state);
  const finalStarted = effectiveFinal?.state && effectiveFinal.state !== 'NOT_STARTED';
  const ready = validation?.state === 'COMPLETED' && !validation?.stale;
  const visible = ready || refinementStarted || finalStarted;

  if (loading) return <LoadingState label="컨셉 다듬기 상태를 불러오는 중" />;

  const start = () => act(async () => setRefinement(await api.startRefinement()));
  const retry = () => act(async () => setRefinement(await api.retryRefinement()));
  const next = () => act(async () => {
    const awaiting = refinement?.state === 'AWAITING_DECISION';
    setRefinement(await api.nextRefinement({
      expectedRound: refinement?.round,
      expectedProposalSetHash: awaiting ? refinement?.proposalSetHash : null,
      expectedDecisionHash: awaiting ? null : refinement?.decision?.decisionHash ?? null,
    }));
  });
  const decideAndApply = (selectedProposalKeys) => act(async () => {
    const decided = await api.decideRefinement({
      expectedRound: refinement.round,
      proposalSetHash: refinement.proposalSetHash,
      selectedProposalKeys,
      keepCurrent: false,
    });
    setRefinement(decided);
    setRefinement(await api.applyRefinement(applyBody(decided)));
  });
  const keepCurrent = () => act(async () => {
    const decided = await api.decideRefinement({
      expectedRound: refinement.round,
      proposalSetHash: refinement.proposalSetHash,
      selectedProposalKeys: [],
      keepCurrent: true,
    });
    setRefinement(decided);
    setFinalView(await api.finalizeRefinement(applyBody(decided)));
  });

  return <ProjectWorkspace as="section" mode="analyze" className="business-validation">
    <ProjectStageHeader step={2} eyebrow="컨셉 다듬기"
      title="검증 결과를 현재 컨셉에 반영하세요"
      description="완료된 시장 분석과 사업 모델의 정확한 버전을 기준으로 변경 제안을 검토합니다." />
    {error ? <Alert tone="danger">{error}</Alert> : null}
    {!visible ? <Alert tone="info">시장 분석이 완료되면 사업 모델이 자동으로 이어지고, 사업 모델이 완료되면 이 화면의 첫 다듬기 제안이 자동으로 준비됩니다.</Alert> : null}
    {visible ? <ConceptRefinementPanel refinement={effectiveRefinement} finalView={effectiveFinal}
      busy={busy} error={error} onStart={start} onRetry={retry} onNext={next}
      onDecideAndApply={decideAndApply} onKeepCurrent={keepCurrent}
      onApply={() => act(async () => setRefinement(await api.applyRefinement(applyBody())))}
      onRetryLegal={() => act(async () => setRefinement(await api.retryRefinementLegal(applyBody())))}
      onRecoverLegalBlocked={() => act(async () => setRefinement(await api.recoverLegalBlocked(applyBody())))}
      onFinalize={() => act(async () => setFinalView(await api.finalizeRefinement(applyBody())))} /> : null}
  </ProjectWorkspace>;
}
