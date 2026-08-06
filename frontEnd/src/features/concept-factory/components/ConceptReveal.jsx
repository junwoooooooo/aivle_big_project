export default function ConceptReveal({ concepts }) {
  return <section className="concept-reveal" aria-labelledby="concept-reveal-title">
    <h2 id="concept-reveal-title">공식 근거 기반 법률 구현 가능성 사전검토를 마친 5개 컨셉</h2>
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
          <List title="검토한 사업 활동" values={assessment.reviewedActivities} />
          <List title="필수 통제" values={assessment.requiredControls} />
          <List title="필수 고지" values={assessment.requiredDisclosures} />
          <List title="금지 변형" values={assessment.prohibitedVariants} />
          <List title="남은 확인 사항" values={assessment.unknownFacts} />
          <p>전문가 검토 권장: {assessment.expertReviewRecommended ? '예' : '아니오'}</p>
          <p className="concept-detail__legal-limit">{assessment.reviewLimitations ?? '이 결과는 제한된 공식 근거를 이용한 사전검토이며 법률 자문이 아닙니다.'}</p>
        </section>
        <details><summary>공식 법령 근거</summary><ul>{(legal.evidence ?? []).map((item) => <li key={`${item.lawName}-${item.articleReference}`}>
          <a href={item.officialSourceUri} target="_blank" rel="noreferrer">{item.lawName} {item.articleReference}</a>
          {item.title && <span> · {item.title}</span>}
          <small>시행 기준 {item.effectiveDate || '추가 확인 필요'} · 조회 {formatDate(item.retrievedAt)}</small>
        </li>)}</ul></details>
      </article>;
    })}</div>
  </section>;
}

function formatDate(value) {
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? '확인 필요' : new Intl.DateTimeFormat('ko-KR').format(date);
}

function List({ title, values = [] }) {
  if (!Array.isArray(values) || values.length === 0) return null;
  return <div><h5>{title}</h5><ul>{values.map((value) => <li key={value}>{value}</li>)}</ul></div>;
}
