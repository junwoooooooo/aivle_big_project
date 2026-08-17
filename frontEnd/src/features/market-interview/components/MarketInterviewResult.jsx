import { useMemo, useState } from 'react';
import { marketInterviewDashboard } from '../model/marketInterviewDashboard.js';

const GROUP_LABEL = { TARGET: 'Target', COMPARISON: 'Comparison' };

function EvidenceCount({ theme, total }) {
  return <><strong>{theme.mentionCount ?? theme.participantIds.length} / {total}</strong>
    {theme.targetCount != null && theme.nonTargetCount != null
      ? <small>Target {theme.targetCount} · Comparison {theme.nonTargetCount}</small> : null}</>;
}

function ThemeRow({ theme, total, selected, onSelect }) {
  const numerator = theme.mentionCount ?? theme.participantIds.length;
  const width = total > 0 ? Math.min(100, Math.max(0, numerator / total * 100)) : 0;
  return <button type="button" className="market-interview__theme-row" aria-pressed={selected}
    onClick={() => onSelect(selected ? null : theme.key)}>
    <span className="market-interview__theme-heading"><span>{theme.title}</span><EvidenceCount theme={theme} total={total} /></span>
    <span className="market-interview__bar" aria-label={`${theme.title} ${numerator}명 중 ${total}명`}>
      <span style={{ width: `${width}%` }} /></span>
    {theme.description ? <span className="market-interview__theme-description">{theme.description}</span> : null}
    {theme.quote ? <q>{theme.quote}</q> : null}
  </button>;
}

function ParticipantCard({ participant, highlighted }) {
  const interview = participant.interview;
  const questions = Array.isArray(interview?.questions) ? interview.questions : [];
  return <article className="market-interview__respondent" data-highlighted={highlighted || undefined}>
    <header><span>{GROUP_LABEL[participant.group]}</span><h3>{participant.label ?? participant.participantId}</h3></header>
    {participant.profile ? <p className="market-interview__profile">{participant.profile}</p> : null}
    {participant.context ? <p>{participant.context}</p> : null}
    {participant.needs.length ? <div><strong>명시된 니즈</strong><ul>{participant.needs.map((need) => <li key={need}>{need}</li>)}</ul></div> : null}
    {questions[0] ? <div className="market-interview__answer"><strong>{questions[0].question}</strong><p>{questions[0].answer}</p>
      {questions[0].uncertainty ? <small>아직 확인할 점: {questions[0].uncertainty}</small> : null}</div> : null}
    {questions.length > 1 ? <details><summary>나머지 답변 보기 ({questions.length - 1})</summary>
      {questions.slice(1).map((item, index) => <div className="market-interview__answer" key={`${participant.participantId}-${index}`}>
        <strong>{item.question}</strong><p>{item.answer}</p>{item.uncertainty ? <small>아직 확인할 점: {item.uncertainty}</small> : null}
      </div>)}</details> : null}
  </article>;
}

function ListSection({ title, items = [] }) {
  if (!items.length) return null;
  return <section className="market-interview__section"><h2>{title}</h2><ul>{items.map((item, index) =>
    <li key={`${title}-${index}`}>{item}</li>)}</ul></section>;
}

export default function MarketInterviewResult({ result }) {
  const dashboard = useMemo(() => marketInterviewDashboard(result), [result]);
  const [selectedTheme, setSelectedTheme] = useState(null);
  const [group, setGroup] = useState('ALL');
  if (!result) return null;
  const selected = dashboard.themes.find((theme) => theme.key === selectedTheme) ?? null;
  const linkedIds = new Set(selected?.participantIds ?? []);
  const respondents = dashboard.participants.filter((participant) => (group === 'ALL' || participant.group === group)
    && (!selected || linkedIds.has(participant.participantId)));
  const requested = result.targeting?.requestedSampleSize ?? result.targeting?.drawnSampleSize;
  const failed = result.targeting?.failedCount ?? 0;
  return <div className="market-interview__result">
    {result.targeting ? <section className="market-interview__scope"><div><span>가상 인터뷰 범위</span><strong>{result.targeting.criteriaText}</strong></div>
      <p>요청 {requested}명 · 유효 응답 {dashboard.usableCount}명{failed ? ` · 응답 생성 실패 ${failed}명` : ''}</p>
      <p>유효 응답 중 타겟 조건 일치 {result.targeting.targetCount}명 · 비교 관점 {result.targeting.nonTargetCount}명</p>
      {result.targeting.targetCoverageWarning ? <p role="status">{result.targeting.targetCoverageWarning}</p> : null}</section> : null}

    {dashboard.headlines.length ? <section className="market-interview__headline" aria-labelledby="interview-headline-title">
      <div><span>조사 핵심 인사이트</span><h2 id="interview-headline-title">반복된 응답을 원문 근거와 함께 확인하세요</h2></div>
      <div>{dashboard.headlines.map((item) => <button type="button" key={item.label} onClick={() => setSelectedTheme(item.key)}>
        <span>{item.label}</span><strong>{item.title}</strong><small>{item.total}명 중 {item.count}명</small></button>)}</div>
    </section> : <section className="market-interview__empty"><h2>표시할 반복 인사이트가 없습니다</h2><p>완료 결과에 theme 또는 coding trace가 포함되지 않았습니다.</p></section>}

    <div className="market-interview__dashboard">
      <section className="market-interview__insights" aria-label="인터뷰 인사이트">
        {dashboard.sections.length ? dashboard.sections.map((section) => <section key={section.id}>
          <h2>{section.title}</h2>{section.themes.map((theme) => <ThemeRow key={theme.key} theme={theme}
            total={dashboard.usableCount} selected={selectedTheme === theme.key} onSelect={setSelectedTheme} />)}
        </section>) : <div className="market-interview__empty"><p>분류된 theme이 없습니다.</p></div>}
        {dashboard.crossRelationships.length ? <section><h2>우려와 개선 제안의 연결</h2><ul className="market-interview__relationships">
          {dashboard.crossRelationships.map((item) => <li key={`${item.suggestionTitle}-${item.relatedTitle}`}>
            <strong>{item.suggestionTitle}</strong><span>↔ {item.relatedTitle}</span><small>동일 응답자 {item.overlapCount}명</small></li>)}</ul></section> : null}
      </section>

      <aside className="market-interview__respondents" aria-labelledby="market-interview-participants">
        <header><div><span>응답 근거</span><h2 id="market-interview-participants">가상 응답자</h2></div>
          <div role="group" aria-label="응답자 그룹 필터">{['ALL', 'TARGET', 'COMPARISON'].map((value) => <button type="button"
            key={value} aria-pressed={group === value} onClick={() => setGroup(value)}>{value === 'ALL' ? '전체' : GROUP_LABEL[value]}</button>)}</div></header>
        {selected ? <p className="market-interview__filter-note"><strong>{selected.title}</strong>을 언급한 응답자 원문입니다.
          <button type="button" onClick={() => setSelectedTheme(null)}>필터 해제</button></p> : null}
        <div className="market-interview__respondent-list">{respondents.map((participant) => <ParticipantCard key={participant.participantId}
          participant={participant} highlighted={linkedIds.has(participant.participantId)} />)}</div>
        {!respondents.length ? <div className="market-interview__empty"><p>{selected
          ? '이 theme의 전체 ID는 보존되어 있지만 상세 카드로 제공된 대표 응답자는 없습니다.'
          : '표시할 대표 응답자가 없습니다.'}</p></div> : null}
      </aside>
    </div>

    {result.saturation ? <section className="market-interview__section"><h2>응답 다양성 진단</h2>
      <p>코딩 완료 {result.saturation.codedParticipantCount}명 · 주제 {result.saturation.themeCount}개</p><p>{result.saturation.limitation}</p></section> : null}
    <ListSection title="반복적으로 나온 우려" items={result.objections} />
    <ListSection title="구매·사용을 결정할 요인" items={result.purchaseTriggers} />
    <ListSection title="충족되지 않은 요구" items={result.unmetNeeds} />
    <ListSection title="실제 고객에게 확인할 질문" items={result.followUpQuestions} />
    <ListSection title="해석할 때의 한계" items={result.limitations} />
  </div>;
}
