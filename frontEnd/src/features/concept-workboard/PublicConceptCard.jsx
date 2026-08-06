import { useId } from 'react';

export function PublicConceptCard({ concept, index }) {
  const detailsId = useId();
  const controlled = concept.legalState === 'IMPLEMENTABLE_WITH_CONTROLS';
  return <article className="concept-public-card" tabIndex="0" aria-labelledby={`${detailsId}-title`}>
    <header>
      <span>Concept {index + 1}</span>
      <h3 id={`${detailsId}-title`}>{concept.conceptName}</h3>
      <p>{concept.oneLineSummary}</p>
      <strong className={`concept-legal-state concept-legal-state--${controlled ? 'controlled' : 'ready'}`}>
        {controlled ? '필수 통제 적용 시 구현 가능' : '현재 구조로 구현 가능'}
      </strong>
    </header>
    <dl className="concept-public-card__summary">
      <Item label="대상 고객" value={concept.targetSegment} />
      <Item label="문제 상황" value={concept.problemScenario} />
      <Item label="가치 제안" value={concept.valueProposition} />
      <Item label="해결 방식" value={concept.solutionMechanism} />
      <Item label="파트너 조건" value={concept.partnerRequirements} />
      <Item label="운영 모델" value={concept.operatingModel} />
      <Item label="수익 모델" value={concept.revenueModelHypothesis} />
    </dl>
    <details id={detailsId} className="concept-public-card__details">
      <summary>구현 구조와 조건 자세히 보기</summary>
      <DetailSection title="Actor Roles" value={concept.actorRoles} />
      <DetailSection title="Transaction Flow" value={concept.transactionFlow} />
      <DetailSection title="Data Flow" value={concept.dataFlow} />
      <DetailSection title="Physical Activities" value={concept.physicalActivities} />
      <DetailSection title="Feature Set" value={concept.featureSet} />
      <DetailSection title="Channel Hypothesis" value={concept.channelHypothesis} />
      <DetailSection title="Pricing Hypothesis" value={concept.pricingHypothesis} />
      <DetailSection title="Risks" value={concept.risks} />
      <DetailSection title="필수 통제" value={concept.requiredControls} />
      <DetailSection title="파트너·자격" value={concept.requiredPartnersOrLicenses} />
      <DetailSection title="필수 고지" value={concept.requiredDisclosures} />
      <DetailSection title="금지 변형" value={concept.prohibitedVariants} />
      <DetailSection title="미해결 가정" value={concept.unresolvedAssumptions} />
    </details>
    <small>Assessment Version {concept.assessmentVersion}</small>
  </article>;
}

function Item({ label, value }) {
  return <div><dt>{label}</dt><dd>{display(value)}</dd></div>;
}

function DetailSection({ title, value }) {
  const values = toItems(value);
  if (!values.length) return null;
  return <section><h4>{title}</h4><ul>{values.map((item, index) => <li key={`${title}-${index}`}>{display(item)}</li>)}</ul></section>;
}

function toItems(value) {
  if (value == null || value === '') return [];
  return Array.isArray(value) ? value : [value];
}

function display(value) {
  if (value == null || value === '') return '확인된 내용 없음';
  if (typeof value === 'string' || typeof value === 'number') return String(value);
  if (Array.isArray(value)) return value.map(display).join(' · ');
  return Object.entries(value).map(([key, item]) => `${key}: ${display(item)}`).join(' · ');
}
