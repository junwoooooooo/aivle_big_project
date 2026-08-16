function ListSection({ title, items = [] }) {
  if (!items.length) return null;
  return <section className="market-interview__section"><h2>{title}</h2><ul>{items.map((item, index) =>
    <li key={`${title}-${index}`}>{item}</li>)}</ul></section>;
}

export default function MarketInterviewResult({ result }) {
  if (!result) return null;
  return <div className="market-interview__result">
    <section className="market-interview__section" aria-labelledby="market-interview-participants">
      <h2 id="market-interview-participants">가상 참여자</h2>
      <div className="market-interview__participants">{result.participants?.map((participant) => {
        const interview = result.interviews?.find((item) => item.participantId === participant.participantId);
        return <article className="market-interview__participant" key={participant.participantId}>
          <p className="market-interview__tag">가상 고객 유형</p>
          <h3>{participant.label}</h3><p>{participant.profile}</p><p>{participant.context}</p>
          {participant.needs?.length ? <><h4>주요 니즈</h4><ul>{participant.needs.map((item) => <li key={item}>{item}</li>)}</ul></> : null}
          {interview?.questions?.length ? <><h4>핵심 답변과 관점</h4>{interview.questions.slice(0, 3).map((item, index) =>
            <div className="market-interview__answer" key={`${participant.participantId}-${index}`}><strong>{item.question}</strong><p>{item.answer}</p><small>아직 확인할 점: {item.uncertainty}</small></div>)}</> : null}
        </article>;
      })}</div>
    </section>
    {result.themes?.length ? <section className="market-interview__section"><h2>주요 반응과 반복된 관점</h2>
      <div className="market-interview__themes">{result.themes.map((theme) => <article key={theme.title}><h3>{theme.title}</h3><p>{theme.description}</p></article>)}</div>
    </section> : null}
    <ListSection title="반복적으로 나온 우려" items={result.objections} />
    <ListSection title="구매·사용을 결정할 요인" items={result.purchaseTriggers} />
    <ListSection title="충족되지 않은 요구" items={result.unmetNeeds} />
    <ListSection title="실제 고객에게 확인할 질문" items={result.followUpQuestions} />
    <ListSection title="해석할 때의 한계" items={result.limitations} />
  </div>;
}
