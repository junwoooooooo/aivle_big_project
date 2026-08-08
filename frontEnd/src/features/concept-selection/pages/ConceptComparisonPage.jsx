import { useMemo, useState } from 'react';
import { Link, useParams } from 'react-router-dom';

import { projectRoutes } from '../../../app/routing/projectRoutes.js';
import ConceptCard from '../components/ConceptCard.jsx';
import ConceptComparisonTable from '../components/ConceptComparisonTable.jsx';
import LegalDetailDialog from '../components/LegalDetailDialog.jsx';
import HypothesisDecisionPanel from '../components/HypothesisDecisionPanel.jsx';
import MarketSeedFinalization from '../components/MarketSeedFinalization.jsx';
import SelectionConfirmation from '../components/SelectionConfirmation.jsx';
import useConceptSelection from '../hooks/useConceptSelection.js';
import { MAX_COMPARE_COUNT, MIN_COMPARE_COUNT, toComparisonModel } from '../model/conceptComparisonModel.js';
import '../styles/concept-selection.css';

export default function ConceptComparisonPage() {
  const { projectId } = useParams();
  const selection = useConceptSelection(projectId);
  const models = useMemo(() => selection.concepts.map(toComparisonModel), [selection.concepts]);
  const initialIds = selection.draft?.comparedConceptIds ?? [];
  const [comparedIds, setComparedIds] = useState(initialIds);
  const [preferredId, setPreferredId] = useState(selection.draft?.preferredConceptId ?? null);
  const [view, setView] = useState('cards');
  const [detail, setDetail] = useState(null);
  const [draftMessage, setDraftMessage] = useState('');

  if (selection.loading) return <section className="concept-selection" aria-busy="true"><p>비교할 컨셉을 불러오고 있습니다.</p></section>;
  if (selection.error) return <section className="concept-selection" role="alert"><h1>컨셉 비교를 불러오지 못했습니다.</h1><button type="button" onClick={selection.refresh}>다시 시도</button></section>;
  if (selection.run?.status !== 'COMPLETED' || models.length !== 5) return <NotReady projectId={projectId} status={selection.run?.status} />;

  const comparedModels = comparedIds.map((id) => models.find((model) => model.conceptId === id)).filter(Boolean);
  const toggleCompare = (conceptId) => {
    setDraftMessage('');
    setComparedIds((ids) => {
      if (ids.includes(conceptId)) {
        if (preferredId === conceptId) setPreferredId(null);
        return ids.filter((id) => id !== conceptId);
      }
      return ids.length < MAX_COMPARE_COUNT ? [...ids, conceptId] : ids;
    });
  };
  const saveDraft = () => {
    selection.saveDraft(comparedIds, preferredId);
    setDraftMessage('이 브라우저 세션에 선택 준비 초안을 저장했습니다. 서버에는 저장되지 않았습니다.');
  };

  return <main className="concept-selection">
    <header className="concept-selection__heading"><div><p>공식 근거 기반 법률 구현 가능성 검토를 통과한 5개 컨셉</p><h1>컨셉 비교와 선택 준비</h1><span>총점이나 자동 1위 없이 차이를 살펴보고 직접 선택합니다.</span></div>
      <div role="group" aria-label="비교 보기 방식"><button type="button" aria-pressed={view === 'cards'} onClick={() => setView('cards')}>카드 보기</button><button type="button" aria-pressed={view === 'table'} onClick={() => setView('table')}>비교표</button></div></header>
    <section className="selection-guide" aria-live="polite"><strong>비교 대상 {comparedIds.length} / 5</strong><span>2~5개를 고르고, 그중 하나를 선택 후보로 표시하세요.</span></section>
    {view === 'cards' ? <section className="selection-grid" aria-label="컨셉 카드 보기">{models.map((model) => <ConceptCard key={model.conceptId} model={model} compared={comparedIds.includes(model.conceptId)} preferred={preferredId === model.conceptId} compareDisabled={comparedIds.length >= MAX_COMPARE_COUNT} onToggleCompare={toggleCompare} onPrefer={setPreferredId} onDetails={setDetail} />)}</section>
      : comparedModels.length >= MIN_COMPARE_COUNT ? <ConceptComparisonTable models={comparedModels} /> : <section className="comparison-empty"><h2>비교할 컨셉을 먼저 골라주세요.</h2><p>카드 보기에서 2개 이상을 비교 대상으로 선택하면 비교표가 열립니다.</p><button type="button" onClick={() => setView('cards')}>카드에서 선택하기</button></section>}
    <footer className="selection-draft"><div><strong>비교 선택 초안</strong><span>이 초안은 현재 브라우저 세션에만 저장됩니다.</span>{draftMessage && <p role="status">{draftMessage}</p>}</div><button type="button" disabled={comparedIds.length < MIN_COMPARE_COUNT || !preferredId} onClick={saveDraft}>선택 준비 초안 저장</button></footer>
    <SelectionConfirmation preferred={models.find((model) => model.conceptId === preferredId)} currentSelection={selection.currentSelection} onConfirm={selection.confirmSelection} />
    {selection.currentSelection && <HypothesisDecisionPanel selection={selection.currentSelection} onAction={selection.decideHypothesis} />}
    <MarketSeedFinalization projectId={projectId} selection={selection.currentSelection} snapshot={selection.marketSeed}
      finalizing={selection.finalizing} onFinalize={selection.finalizeMarketSeed} />
    {detail && <LegalDetailDialog model={detail} onClose={() => setDetail(null)} />}
  </main>;
}

function NotReady({ projectId, status }) {
  const messages = {
    NEEDS_INPUT: 'Concept Factory에 필요한 입력을 확인해야 합니다.',
    FAILED: 'Concept Factory 작업을 다시 시도해야 합니다.',
    STALE: '최신 Idea Brief로 Concept Factory를 다시 실행해야 합니다.',
  };
  return <section className="concept-selection concept-selection--not-ready"><p>비교 준비 전</p><h1>법률검토를 통과한 5개 컨셉이 필요합니다.</h1><span>{messages[status] ?? 'Concept Factory를 완료하면 5개 컨셉을 비교할 수 있습니다.'}</span><Link to={projectRoutes.concepts(projectId)}>Concept Factory로 이동</Link></section>;
}
