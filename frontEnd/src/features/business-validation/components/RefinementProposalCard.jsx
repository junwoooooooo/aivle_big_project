import { Badge, Card, Checkbox } from '../../../shared/ui';
import { fieldLabel, formatRefinementValue, sourceLabel } from '../model/refinementView.js';

export default function RefinementProposalCard({ proposal, checked, disabled, onChange }) {
  const label = fieldLabel(proposal.fieldKey);
  if (!label) return null;
  const evidenceCount = Array.isArray(proposal.evidenceIds) ? proposal.evidenceIds.length : 0;
  return <Card className="refinement-proposal">
    <div className="refinement-proposal__heading">
      <h3>{label}</h3><Badge tone="info">{sourceLabel(proposal.source)}</Badge>
    </div>
    <div className="refinement-proposal__comparison">
      <div><span>현재 값</span><p>{formatRefinementValue(proposal.currentValue)}</p></div>
      <div><span>제안 값</span><p>{formatRefinementValue(proposal.proposedValue)}</p></div>
    </div>
    {proposal.rationale ? <p className="refinement-proposal__reason">{proposal.rationale}</p> : null}
    <p className="refinement-proposal__evidence">
      {proposal.source === 'LEGAL' && proposal.legalRef
        ? proposal.legalRef
        : evidenceCount > 0 ? `${sourceLabel(proposal.source)} ${evidenceCount}건` : sourceLabel(proposal.source)}
    </p>
    <Checkbox label={`${label} 변경안 반영`} checked={checked} disabled={disabled}
      onChange={(event) => onChange(event.target.checked)} />
  </Card>;
}
