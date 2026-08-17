function ListSection({ title, items = [] }) {
  if (!items.length) return null;
  return <section className="market-interview__section"><h2>{title}</h2><ul>{items.map((item, index) =>
    <li key={`${title}-${index}`}>{item}</li>)}</ul></section>;
}

export default function MarketInterviewResult({ result }) {
  if (!result) return null;
  const requested = result.targeting?.requestedSampleSize ?? result.targeting?.drawnSampleSize;
  const usable = result.targeting?.usableCount ?? result.targeting?.drawnSampleSize;
  const failed = result.targeting?.failedCount ?? 0;
  return <div className="market-interview__result">
    {result.targeting ? <section className="market-interview__section"><h2>표집 기준과 범위</h2>
      <p>{result.targeting.criteriaText}</p>
      <p>요청 {requested}명 · 유효 응답 {usable}명{failed ? ` · 응답 생성 실패 ${failed}명` : ''}</p>
      <p>유효 응답 중 타겟 조건 일치 {result.targeting.targetCount}명 · 비교 관점 {result.targeting.nonTargetCount}명</p>
      {result.targeting.targetCoverageWarning ? <p>{result.targeting.targetCoverageWarning}</p> : null}
    </section> : null}
    <section className="market-interview__section" aria-labelledby="market-interview-participants">
      <h2 id="market-interview-participants">가상 참여자</h2>
      <div className="market-interview__participants">{result.participants?.map((participant) => {
        const interview = result.interviews?.find((item) => item.participantId === participant.participantId);
        return <article className="market-interview__participant" key={participant.participantId}>
          <p className="market-interview__tag">{participant.group === 'COMPARISON' ? '비교 관점 가상 응답자' : '타겟 가상 응답자'}</p>
          <h3>{participant.label}</h3><p>{participant.profile}</p><p>{participant.context}</p>
          {participant.needs?.length ? <><h4>주요 니즈</h4><ul>{participant.needs.map((item) => <li key={item}>{item}</li>)}</ul></> : null}
          {interview?.questions?.length ? <><h4>핵심 답변과 관점</h4>{interview.questions.slice(0, 3).map((item, index) =>
            <div className="market-interview__answer" key={`${participant.participantId}-${index}`}><strong>{item.question}</strong><p>{item.answer}</p><small>아직 확인할 점: {item.uncertainty}</small></div>)}</> : null}
        </article>;
      })}</div>
    </section>
    {result.themes?.length ? <section className="market-interview__section"><h2>주요 반응과 반복된 관점</h2>
      <div className="market-interview__themes">{result.themes.map((theme) => <article key={theme.title}><h3>{theme.title}</h3><p>{theme.description}</p>
        {Number.isInteger(theme.mentionCount) ? <p>가상 응답자 언급 {theme.mentionCount}명</p> : null}
        {Number.isInteger(theme.targetCount) && Number.isInteger(theme.nonTargetCount)
          ? <p>타겟 조건 일치 {theme.targetCount}명 · 비교 관점 {theme.nonTargetCount}명</p> : null}
        {theme.quote ? <q>{theme.quote}</q> : null}</article>)}</div>
    </section> : null}
    {result.crossRelationships?.length ? <section className="market-interview__section"><h2>우려와 개선 제안의 연결</h2><ul>
      {result.crossRelationships.slice(0, 8).map((item) => <li key={`${item.suggestionTitle}-${item.relatedTitle}`}>
        {item.suggestionTitle} ↔ {item.relatedTitle} · 같은 가상 응답자 {item.overlapCount}명
      </li>)}</ul></section> : null}
    {result.saturation ? <section className="market-interview__section"><h2>응답 다양성 진단</h2>
      <p>코딩 완료 {result.saturation.codedParticipantCount}명 · 주제 {result.saturation.themeCount}개</p>
      <p>{result.saturation.limitation}</p>
      {result.saturation.saturatedThemes?.length ? <p>응답이 지나치게 한 방향으로 모였을 수 있는 주제: {result.saturation.saturatedThemes.join(' · ')}</p> : null}
    </section> : null}
    <ListSection title="반복적으로 나온 우려" items={result.objections} />
    <ListSection title="구매·사용을 결정할 요인" items={result.purchaseTriggers} />
    <ListSection title="충족되지 않은 요구" items={result.unmetNeeds} />
    <ListSection title="실제 고객에게 확인할 질문" items={result.followUpQuestions} />
    <ListSection title="해석할 때의 한계" items={result.limitations} />
  </div>;
}
