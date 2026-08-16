import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';

import { projectRoutes } from '../../../app/routing/projectRoutes.js';
import { useApiClient } from '../../../shared/api/ApiClientProvider.jsx';
import { getUserErrorMessage } from '../../../shared/api/apiError.js';
import { AppIcon, scrollPageToTop } from '../../../shared/ui/index.js';
import BmPlanForm, { BmPlanReview } from '../../market/BmPlanForm.jsx';
import { buildConceptPlanSuggestions, draftFrom, emptyDraft, toPayload } from '../../market/bmPlan.js';
import { createMarketApi } from '../../market/marketApi.js';
import '../styles/business-validation-preparation.css';

const hasNoPlanValue = (draft) => {
  const payload = toPayload(draft);
  return Object.keys(payload.plan).length === 0 && Object.keys(payload.constraints).length === 0;
};

export default function BusinessValidationPreparation({ projectId, portfolio, onBack }) {
  const client = useApiClient();
  const api = useMemo(() => createMarketApi(client, projectId), [client, projectId]);
  const selectedConcept = portfolio.concepts?.find((concept) => concept.conceptId === portfolio.selection?.conceptId);
  const conceptSuggestions = useMemo(() => buildConceptPlanSuggestions(selectedConcept), [selectedConcept]);
  const [state, setState] = useState({ loading: true, loaded: false, saving: false, revision: 0, draft: emptyDraft(), error: null, confirmEmpty: false });

  const load = useCallback(() => {
    let alive = true;
    setState((current) => ({ ...current, loading: true, loaded: false, error: null }));
    api.currentBmPlan()
      .then((view) => { if (alive) setState((current) => ({ ...current, loading: false, loaded: true, draft: draftFrom(view), revision: view?.revision ?? 0 })); })
      .catch((error) => { if (alive) setState((current) => ({ ...current, loading: false, error })); });
    return () => { alive = false; };
  }, [api]);

  useEffect(() => load(), [load]);

  const change = (key, value) => setState((current) => ({ ...current, draft: { ...current.draft, [key]: value }, confirmEmpty: false }));
  const saveAndContinue = async () => {
    if (state.saving || portfolio.busy) return;
    if (hasNoPlanValue(state.draft) && !state.confirmEmpty) {
      setState((current) => ({ ...current, confirmEmpty: true }));
      return;
    }
    setState((current) => ({ ...current, saving: true, error: null }));
    try {
      const payload = toPayload(state.draft);
      const saved = await api.saveBmPlan(payload.plan, payload.constraints);
      setState((current) => ({ ...current, revision: saved?.revision ?? current.revision, confirmEmpty: false }));
      await portfolio.finalizeMarketSeed();
    } catch (error) {
      setState((current) => ({ ...current, error }));
    } finally {
      setState((current) => ({ ...current, saving: false }));
    }
  };

  if (portfolio.selection.status === 'READY_FOR_MARKET') {
    return <section className="business-validation-prep business-validation-prep--ready" aria-labelledby="business-validation-ready-title">
      <div className="business-validation-prep__complete"><AppIcon name="check" size={18} /><div><h2 id="business-validation-ready-title">사업 검증 준비를 마쳤습니다.</h2><p>선택한 사업안, 분석 기준, 법률·규제 결과와 운영 정보를 저장했습니다.</p></div></div>
      <div className="business-validation-prep__actions"><button type="button" className="bp-button bp-button--tertiary" onClick={onBack}><AppIcon name="chevronLeft" size={16} />법률·규제 결과로 돌아가기</button><Link className="bp-button bp-button--primary" to={projectRoutes.market(projectId)} onClick={() => scrollPageToTop({ smooth: false })}>시장 분석 시작하기<AppIcon name="arrowRight" size={16} /></Link></div>
      {state.loading && <p className="business-validation-prep__status" role="status">저장한 운영 정보를 불러오고 있습니다.</p>}
      {state.error && <div className="business-validation-prep__error" role="alert"><span>{getUserErrorMessage(state.error)}</span><button type="button" className="bp-button bp-button--secondary" onClick={load}>다시 불러오기</button></div>}
      {state.loaded && <BmPlanReview draft={state.draft} />}
    </section>;
  }

  if (portfolio.selection.status === 'MARKET_SEED_FINALIZING') {
    return <section className="business-validation-prep business-validation-prep--progress" aria-live="polite" aria-busy="true">
      <span className="bp-button__spinner" aria-hidden="true" />
      <div><h2>사업 검증에 필요한 정보를 정리하고 있습니다.</h2><p>저장한 준비 내용과 기존 사업안 계약을 연결하고 있습니다.</p></div>
    </section>;
  }

  return <section className="business-validation-prep" aria-labelledby="business-validation-prep-title">
    <header className="business-validation-prep__header">
      <div><p>사업 검증 준비</p><h2 id="business-validation-prep-title">사업 검증에 사용할 운영 정보를 준비하세요</h2><span>시장과 경쟁 환경을 분석한 뒤 사업 모델을 구체화할 때 사용할 운영 정보입니다. 지금 알고 있는 내용만 입력하고, 정하지 않은 항목은 비워 두어도 됩니다.</span></div>
      {state.revision > 0 && <strong>저장된 준비 정보 · 수정 {state.revision}</strong>}
    </header>
    <div className="business-validation-prep__actions"><button type="button" className="bp-button bp-button--tertiary" onClick={onBack}><AppIcon name="chevronLeft" size={16} />법률·규제 결과로 돌아가기</button><button type="submit" form="business-validation-prep-form" className="bp-button bp-button--primary" disabled={!state.loaded || state.saving || portfolio.busy}>{state.saving ? '저장 중...' : '저장하고 계속'}</button></div>
    {state.loading && <p className="business-validation-prep__status" role="status">저장된 준비 정보를 불러오고 있습니다.</p>}
    {state.error && <div className="business-validation-prep__error" role="alert"><span>{getUserErrorMessage(state.error)}</span>{!state.loading && !state.loaded ? <button type="button" className="bp-button bp-button--secondary" onClick={load}>다시 불러오기</button> : null}</div>}
    {state.loaded && <BmPlanForm formId="business-validation-prep-form" showSubmit={false} draft={state.draft} suggestions={conceptSuggestions} onChange={change} onSubmit={saveAndContinue} busy={state.saving || portfolio.busy} submitLabel="저장하고 계속" />}
    {state.confirmEmpty && <div className="business-validation-prep__empty" role="status"><div><strong>입력하지 않은 항목은 비워 둔 채 진행합니다.</strong><span>사업 모델 검토에서 나중에 추가할 수 있습니다.</span></div><button type="button" className="bp-button bp-button--primary" onClick={saveAndContinue}>비운 채 계속</button></div>}
  </section>;
}
