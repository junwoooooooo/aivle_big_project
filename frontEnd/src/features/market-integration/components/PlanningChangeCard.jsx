import { useState } from 'react';
import { DECISION_LABELS, parsePartialValue, stringifyPlanningValue } from '../model/marketResultModel.js';

export default function PlanningChangeCard({ proposal, busy = false, onDecision }) {
  const [partialOpen, setPartialOpen] = useState(false);
  const [partialValue, setPartialValue] = useState(stringifyPlanningValue(proposal.modifiedAfter ?? proposal.after));
  const decided = proposal.decisionStatus !== 'PENDING';
  const submitPartial = () => {
    const value = parsePartialValue(partialValue);
    if (value != null) onDecision('PARTIALLY_ADOPT', value);
  };
  return <article className="planning-change-card">
    <header><div><h3>{proposal.meaningfulTitle}</h3><p>{proposal.affectedFields.join(' · ')}</p></div><span>{DECISION_LABELS[proposal.decisionStatus] ?? proposal.decisionStatus}</span></header>
    <div className="planning-change-card__values"><section><strong>현재</strong><pre>{stringifyPlanningValue(proposal.before)}</pre></section><section><strong>제안</strong><pre>{stringifyPlanningValue(proposal.after)}</pre></section></div>
    <p><strong>이유</strong> {proposal.reason}</p>
    <p><strong>영향</strong> {proposal.impactAreas.join(' · ')}</p>
    <details><summary>근거 {proposal.evidenceReferences.length}건</summary><ul>{proposal.evidenceReferences.map((source) => <li key={source.url}><a href={source.url} target="_blank" rel="noreferrer">{source.title}</a></li>)}</ul></details>
    {partialOpen && !decided && <div className="planning-change-card__partial"><label htmlFor={`partial-${proposal.proposalId}`}>수정해 채택할 값</label><textarea id={`partial-${proposal.proposalId}`} value={partialValue} onChange={(event) => setPartialValue(event.target.value)} /><button type="button" disabled={busy || !partialValue.trim()} onClick={submitPartial}>수정값으로 부분 채택</button></div>}
    {!decided && <footer><button type="button" disabled={busy} onClick={() => onDecision('ADOPT')}>채택</button><button type="button" disabled={busy} onClick={() => setPartialOpen((value) => !value)}>부분 채택</button><button type="button" disabled={busy} onClick={() => onDecision('REJECT')}>거절</button></footer>}
    {decided && proposal.modifiedAfter != null && <p className="planning-change-card__chosen"><strong>부분 채택 값</strong> {stringifyPlanningValue(proposal.modifiedAfter)}</p>}
  </article>;
}
