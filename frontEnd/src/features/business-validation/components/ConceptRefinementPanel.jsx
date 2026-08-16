import { useMemo, useState } from 'react';

import { Alert, Button } from '../../../shared/ui';
import { validProposals } from '../model/refinementView.js';
import RefinedConceptSummary from './RefinedConceptSummary.jsx';
import RefinementProposalCard from './RefinementProposalCard.jsx';

const PROGRESS = {
  PROPOSING: '검증 결과를 읽고 개선 제안을 만들고 있습니다.',
  APPLYING_HYPOTHESES: '선택한 변경이 사업안에 맞는지 확인하고 있습니다.',
  LEGAL_REVIEW_PENDING: '선택한 변경의 법률 영향을 확인하고 있습니다.',
  FINALIZING: '다듬어진 컨셉을 최종 결과로 정리하고 있습니다.',
};

function Action({ children, busy, disabled, variant, onClick }) {
  return <Button variant={variant} onClick={onClick} disabled={busy || disabled} loading={busy}>{children}</Button>;
}

export default function ConceptRefinementPanel({ refinement, finalView, busy = false, error,
  onStart, onRetry, onNext, onDecideAndApply, onKeepCurrent, onApply, onRetryLegal, onFinalize }) {
  const proposals = useMemo(() => validProposals(refinement?.proposals), [refinement?.proposals]);
  const proposalIdentity = `${refinement?.round ?? 0}:${refinement?.proposalSetHash ?? ''}`;
  const [selection, setSelection] = useState(() => ({ identity: proposalIdentity, keys: new Set() }));
  const selected = selection.identity === proposalIdentity ? selection.keys : new Set();
  const state = finalView?.state && finalView.state !== 'NOT_STARTED'
    ? finalView.state : refinement?.state ?? 'NOT_STARTED';
  const policy = refinement?.policy;
  const currentRound = refinement?.round;
  const maxRounds = refinement?.nextRound?.maxRounds ?? policy?.maxRounds;
  const stale = state === 'STALE' || refinement?.stale || finalView?.stale;
  const nextAvailable = !stale && refinement?.nextRound?.available === true;
  const lastRound = Number.isInteger(currentRound) && Number.isInteger(maxRounds)
    && currentRound === maxRounds;

  if (state === 'FINALIZED' && finalView?.value) return <RefinedConceptSummary finalView={finalView} />;

  const toggle = (proposal, checked) => setSelection((current) => {
    const next = new Set(current.identity === proposalIdentity ? current.keys : []);
    if (checked) {
      proposals.filter((item) => item.fieldKey === proposal.fieldKey)
        .forEach((item) => next.delete(item.proposalKey));
      next.add(proposal.proposalKey);
    } else next.delete(proposal.proposalKey);
    return { identity: proposalIdentity, keys: next };
  });

  return <section className="concept-refinement" aria-labelledby="concept-refinement-title">
    <header><span>사업 검증 다음 단계</span><h2 id="concept-refinement-title">검증 결과로 사업안 다듬기</h2></header>
    {Number.isInteger(currentRound) && currentRound > 0 && Number.isInteger(maxRounds)
      ? <div className="concept-refinement__round" aria-label="다듬기 제안 진행">
        <strong>제안 {currentRound} / {maxRounds}</strong>
        {lastRound ? <span>마지막 제안입니다.</span> : null}
      </div> : null}
    {error ? <Alert tone="danger">{error}</Alert> : null}
    {state === 'STALE' || refinement?.stale || finalView?.stale
      ? <Alert tone="warning">사업안이 추가로 변경되어 이 다듬기 결과를 현재 결과로 사용할 수 없습니다. 사업 검증을 다시 진행해 주세요.</Alert> : null}

    {state === 'NOT_STARTED' ? <div className="concept-refinement__intro">
      <p>시장·사업 모델 검증 결과를 바탕으로 바꿔볼 만한 항목을 제안합니다. 제안을 받는 것만으로 사업안이 변경되지는 않습니다.</p>
      {policy ? <p className="concept-refinement__policy">가격은 현재 값에서 최대 ±{policy.priceChangePercent}% 범위, 목록형 항목은 한 번에 {policy.listChangeAllowance}개씩 조정하며 최대 {policy.maxProposals}개 제안을 확인합니다.</p> : null}
      <Action busy={busy} onClick={onStart}>다듬기 제안 받기</Action>
    </div> : null}

    {PROGRESS[state] ? <p className="concept-refinement__status" aria-live="polite">{state === 'PROPOSING' && currentRound > 1
      ? '앞선 선택을 바탕으로 다른 개선안을 만들고 있습니다.' : PROGRESS[state]}</p> : null}

    {state === 'FAILED' ? <div className="concept-refinement__status"><p>다듬기 제안을 만들지 못했습니다.</p>
      {refinement?.retry?.available ? <Action busy={busy} onClick={onRetry}>다시 시도</Action> : null}</div> : null}

    {state === 'AWAITING_DECISION' ? <>
      <p>검증 결과를 바탕으로 이런 변경을 제안합니다. 아직 사업안에는 반영되지 않았습니다.</p>
      <div className="concept-refinement__proposals">{proposals.map((proposal) =>
        <RefinementProposalCard key={proposal.proposalKey} proposal={proposal}
          checked={selected.has(proposal.proposalKey)} disabled={busy}
          onChange={(checked) => toggle(proposal, checked)} />)}</div>
      <div className="concept-refinement__actions">
        <Action busy={busy} disabled={!selected.size || stale}
          onClick={() => onDecideAndApply([...selected])}>선택한 변경 반영</Action>
        <Action busy={busy} disabled={stale} variant="secondary" onClick={onKeepCurrent}>변경 없이 현재 사업안으로 확정</Action>
        {nextAvailable ? <Action busy={busy} disabled={selected.size > 0}
          variant="secondary" onClick={onNext}>다른 제안 받기</Action> : null}
      </div>
      {nextAvailable && selected.size > 0
        ? <p className="concept-refinement__next-help">다른 제안을 받으려면 선택한 변경을 먼저 해제해 주세요.</p> : null}
    </> : null}

    {state === 'DECISION_RECORDED' ? <div className="concept-refinement__status"><p>선택한 변경안이 저장되었습니다.</p>
      <Action busy={busy} disabled={stale} onClick={onApply}>선택한 변경 반영</Action></div> : null}
    {state === 'APPLY_FAILED' ? <div className="concept-refinement__status"><p>선택한 변경을 반영하지 못했습니다.</p>
      <Action busy={busy} disabled={stale} onClick={onApply}>변경 반영 다시 시도</Action></div> : null}
    {state === 'LEGAL_REVIEW_FAILED' ? <div className="concept-refinement__status"><p>법률 영향 확인을 완료하지 못했습니다.</p>
      <Action busy={busy} onClick={onRetryLegal}>법률 검토 다시 시도</Action></div> : null}
    {state === 'LEGAL_BLOCKED' ? <Alert tone="warning">선택한 변경은 법률 검토를 통과하지 못해 현재 컨셉으로 확정할 수 없습니다.</Alert> : null}
    {state === 'APPLIED_PENDING_FINALIZATION' ? <div className="concept-refinement__status"><p>선택한 변경을 반영했습니다. 최종 컨셉으로 확정하면 다음 단계에서 이 내용을 사용합니다.</p>
      {nextAvailable ? <p>지금까지 반영한 변경은 그대로 유지한 채 한 번 더 개선 제안을 받을 수 있습니다.</p> : null}
      <div className="concept-refinement__actions">
        <Action busy={busy} disabled={stale} onClick={onFinalize}>이 컨셉으로 확정하기</Action>
        {nextAvailable ? <Action busy={busy} variant="secondary" onClick={onNext}>다른 제안 더 받기</Action> : null}
      </div></div> : null}
    {state === 'FINALIZATION_FAILED' ? <div className="concept-refinement__status"><p>최종 컨셉을 정리하지 못했습니다.</p>
      <Action busy={busy} onClick={onFinalize}>최종 확정 다시 시도</Action></div> : null}
    {state === 'KEEP_CURRENT' ? <div className="concept-refinement__status"><p>현재 사업안을 유지하는 결정이 저장되었습니다.</p>
      <Action busy={busy} onClick={onFinalize}>현재 사업안으로 확정</Action></div> : null}
    {state === 'NO_CHANGES' ? <div className="concept-refinement__status"><p>시장 검증 근거로 지금 바꿀 만한 항목이 나오지 않았습니다.</p>
      <Action busy={busy} onClick={onFinalize}>현재 사업안으로 확정</Action></div> : null}
    {state === 'FINALIZED' ? <p className="concept-refinement__status" aria-live="polite">최종 결과를 불러오는 중입니다.</p> : null}
  </section>;
}
