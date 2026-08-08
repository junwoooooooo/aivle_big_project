export default function ConceptReveal({ concepts }) {
  return <section className="concept-reveal" aria-labelledby="concept-reveal-title">
    <h2 id="concept-reveal-title">검증을 마친 서로 다른 컨셉 5개</h2>
    <p>확정한 Seed 조건을 보존하고 중복 및 법률 사전검토를 통과한 후보를 함께 공개합니다.</p>
    <div className="concept-reveal__grid">{concepts.map((concept) => {
      const candidate = concept.candidate ?? {};
      const legal = concept.legalReview ?? {};
      const assessment = legal.assessment ?? {};
      const factPattern = assessment.legalFactPattern ?? {};
      return <article key={concept.conceptId} className="concept-detail">
        <header>
          <span>컨셉 {concept.slotNumber} · {strategyLabel(candidate.generationStrategy)}</span>
          <h3>{concept.title}</h3><p>{candidate.introduction ?? concept.summary}</p>
          {candidate.originalCandidate && <strong>사용자 원안 구조화</strong>}
        </header>
        <section><h4>핵심 기획</h4><p>{candidate.conceptDefinition}</p><p>{candidate.coreValue}</p>
          <dl><Item label="대상 사용자" value={candidate.targetUsers} /><Item label="업종" value={candidate.industryCategory} />
            <Item label="조사 범위" value={candidate.researchScope} /></dl>
        </section>
        <section><h4>시장 가설</h4>
          <Hypothesis label="대상 지역" value={candidate.targetRegion} semantics={semantics(candidate, 'targetRegion')} />
          <Hypothesis label="수익 모델" value={candidate.revenueModel} semantics={semantics(candidate, 'revenueModel')} />
          <Hypothesis label="가격" value={candidate.price} semantics={semantics(candidate, 'price')} />
          <Hypothesis label="채널" value={candidate.channels} semantics={semantics(candidate, 'channels')} />
          <Hypothesis label="차별점" value={candidate.differentiators} semantics={semantics(candidate, 'differentiators')} />
          <Som candidate={candidate} />
        </section>
        <section><h4>해결 방식과 운영 구조</h4><p>{candidate.problemScenario}</p><p>{candidate.solutionMechanism}</p>
          <List title="주요 기능" values={candidate.featureSet} /><p>{candidate.operatingModel}</p><p>{candidate.partnerModel}</p>
          <List title="참여자 역할" values={candidate.actorRoles} /><List title="파트너 요건" values={candidate.partnerRequirements} />
        </section>
        <section><h4>법률 구현 가능성 사전검토</h4><strong>{legal.status ?? concept.legalStatus}</strong><p>{legal.safeSummary}</p>
          <h5>검토에 사용한 사업 구조</h5>
          <dl><Item label="플랫폼 역할" value={factValue(factPattern.platformRole, candidate.platformRole)} />
            <Item label="제공자 역할" value={factValue(factPattern.commercialRoles?.providerRole, candidate.providerRole)} />
            <Item label="판매자 역할" value={factValue(factPattern.commercialRoles?.sellerRole, candidate.sellerRole)} />
            <Item label="중개자 역할" value={factValue(factPattern.commercialRoles?.intermediaryRole, candidate.intermediaryRole)} /></dl>
          <List title="검토 결제 흐름" values={factList(factPattern.paymentFlow, candidate.paymentFlow)} />
          <List title="검토한 사업 활동" values={assessment.reviewedActivities} />
          <List title="필수 통제" values={assessment.requiredControls} />
          <List title="파트너·자격 요건" values={assessment.requiredPartnersAndQualifications ?? candidate.qualificationRequirements} />
          <List title="필수 고지" values={assessment.requiredDisclosures} />
          <List title="금지 변형" values={assessment.prohibitedVariants} />
          <List title="추가 확인 사항" values={assessment.unknownFacts} />
          <p>전문가 검토 권장: {assessment.expertReviewRecommended ? '예' : '아니요'}</p>
          <p className="concept-detail__legal-limit">{assessment.reviewLimitations ?? '이 결과는 공식 근거를 이용한 사전검토이며 법률 자문이 아닙니다.'}</p>
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

function strategyLabel(value) {
  return ({ EXPLORE: '탐색', REFINE: '구체화', AS_IS: '원안 유지' })[value] ?? '컨셉 설계';
}

function semantics(candidate, fieldKey) {
  return (candidate.valueSemantics ?? []).find((item) => item.fieldKey === fieldKey);
}

function Hypothesis({ label, value, semantics: meta }) {
  return <div><h5>{label}</h5><p>{value}</p>{meta && <small>{meta.source} · {meta.authority} · {meta.decision}</small>}</div>;
}

function Som({ candidate }) {
  const share = candidate.preMarketSomShareHypothesis;
  const som = candidate.preMarketSomHypothesis;
  if (!share || !som) return null;
  return <div><h5>시장분석 전 SOM 가설</h5>
    <p>{share.horizonYears}년 내 목표 점유율 {share.targetSharePercent}% · {share.rationale}</p>
    <p>{som.period} {Number(som.amount).toLocaleString('ko-KR')} {som.currency} · 신뢰도 {som.confidence}</p>
    <small>AI_HYPOTHESIS · OPEN · PROPOSED — 실제 시장분석 결과가 아닙니다.</small>
  </div>;
}

function Item({ label, value }) { return value ? <div><dt>{label}</dt><dd>{value}</dd></div> : null; }

function factValue(governed, fallback) { return governed?.value ?? fallback; }

function factList(governed, fallback) { return Array.isArray(governed?.value) ? governed.value : fallback; }

function formatDate(value) {
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? '확인 필요' : new Intl.DateTimeFormat('ko-KR').format(date);
}

function List({ title, values = [] }) {
  if (!Array.isArray(values) || values.length === 0) return null;
  return <div><h5>{title}</h5><ul>{values.map((value) => <li key={value}>{value}</li>)}</ul></div>;
}
