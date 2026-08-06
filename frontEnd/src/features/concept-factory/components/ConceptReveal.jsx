export default function ConceptReveal({ concepts }) {
  return <section className="concept-reveal" aria-labelledby="concept-reveal-title">
    <h2 id="concept-reveal-title">법률검토를 통과한 5개 컨셉</h2>
    <p>모든 Reveal 조건을 확인했습니다. 다섯 컨셉을 동시에 공개합니다.</p>
    <div className="concept-reveal__grid">{concepts.map((concept) => {
      const candidate = concept.candidate ?? {};
      const legal = concept.legalReview ?? {};
      const assessment = legal.assessment ?? {};
      return <article key={concept.conceptId} className="concept-detail">
        <header><span>컨셉 {concept.slotNumber}</span><h3>{concept.title}</h3><p>{concept.summary}</p></header>
        <section><h4>기본 기획</h4><p>{candidate.valueProposition}</p><p>{candidate.solutionMechanism}</p></section>
        <section><h4>사업 구조</h4><p>{candidate.operatingModel}</p><List title="파트너·자격" values={assessment.requiredPartnersAndQualifications ?? candidate.partnerRequirements} /></section>
        <section><h4>법률 상태</h4><strong>{legal.status ?? concept.legalStatus}</strong><p>{legal.safeSummary}</p>
          <List title="필수 통제" values={assessment.requiredControls} />
          <List title="필수 고지" values={assessment.requiredDisclosures} />
          <List title="금지 변형" values={assessment.prohibitedVariants} />
          <List title="남은 확인 사항" values={assessment.unknownFacts} />
        </section>
        <details><summary>공식 Evidence</summary><ul>{(legal.evidence ?? []).map((item) => <li key={`${item.title}-${item.officialSourceUri}`}><a href={item.officialSourceUri} target="_blank" rel="noreferrer">{item.title}</a></li>)}</ul></details>
      </article>;
    })}</div>
  </section>;
}

function List({ title, values = [] }) {
  if (!Array.isArray(values) || values.length === 0) return null;
  return <div><h5>{title}</h5><ul>{values.map((value) => <li key={value}>{value}</li>)}</ul></div>;
}
