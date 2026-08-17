import { useMemo, useState } from 'react';
import { marketInterviewDashboard } from '../model/marketInterviewDashboard.js';

const GROUP_LABEL = { TARGET: '직접 타겟', COMPARISON: '비교 관점', PROXY: '대리 조건', EXPLORATORY: '탐색 표본' };
const REPRESENTATION_LABEL = {
  REPRESENTABLE_TARGET: '타겟 표현 가능', PARTIAL_PROXY: '부분 대리 조건',
  EXPLORATORY_ONLY: '탐색 표본', TARGET_UNAVAILABLE: '직접 타겟 표현 불가',
};

function EvidenceCount({ theme, total }) {
  return <><strong>{theme.mentionCount ?? theme.participantIds.length} / {total}</strong>
    {theme.targetCount != null && theme.nonTargetCount != null ? <small>직접 타겟 {theme.targetCount} · 그 외 {theme.nonTargetCount}</small> : null}</>;
}

function ThemeRow({ theme, total, selected, onSelect }) {
  const numerator = theme.mentionCount ?? theme.participantIds.length;
  const width = total > 0 ? Math.min(100, Math.max(0, numerator / total * 100)) : 0;
  return <article className="market-interview__theme-row" data-selected={selected || undefined}><button type="button" aria-pressed={selected} onClick={() => onSelect(selected ? null : theme.key)}>
    <span className="market-interview__theme-heading"><span>{theme.title}</span><EvidenceCount theme={theme} total={total} /></span>
    <span className="market-interview__bar" aria-label={`${total}명 중 실제 근거가 연결된 ${numerator}명`}><span style={{ width: `${width}%` }} /></span>
    {theme.description ? <span className="market-interview__theme-description">{theme.description}</span> : null}
    {theme.quote ? <q>{theme.quote}</q> : null}<span className="market-interview__theme-cta">응답자 보기</span>
  </button></article>;
}

function RespondentDetail({ participant, onPrevious, onNext, previousDisabled, nextDisabled }) {
  if (!participant) return <div className="market-interview__empty"><p>조건에 맞는 응답자가 없습니다.</p></div>;
  const questions = Array.isArray(participant.interview?.questions) ? participant.interview.questions : [];
  const answer = (item, index) => <div className="market-interview__answer" key={`${participant.participantId}-${index}`}><strong>{item.question}</strong><p>{item.answer}</p></div>;
  return <article className="market-interview__respondent-detail"><header><div><span>{GROUP_LABEL[participant.group] ?? participant.group}</span><h3>{participant.label ?? participant.participantId}</h3></div>
    <nav aria-label="응답자 이동"><button type="button" onClick={onPrevious} disabled={previousDisabled}>이전</button><button type="button" onClick={onNext} disabled={nextDisabled}>다음</button></nav></header>
    {participant.profile ? <p className="market-interview__profile">{participant.profile}</p> : null}{participant.context ? <p className="market-interview__context">{participant.context}</p> : null}
    <section aria-label="대표 원문 답변">{questions.slice(0, 3).map(answer)}</section>
    {questions.length > 3 ? <details><summary>나머지 답변 {questions.length - 3}개 보기</summary>{questions.slice(3).map((item, index) => answer(item, index + 3))}</details> : null}
    <footer>가상 응답의 해석은 실제 고객에게 동일 질문으로 다시 확인해야 합니다.</footer></article>;
}

function ListSection({ title, items = [] }) {
  if (!items.length) return null;
  return <section className="market-interview__section"><h2>{title}</h2><ul>{items.map((item, index) => <li key={`${title}-${index}`}>{item}</li>)}</ul></section>;
}

export default function MarketInterviewResult({ result }) {
  const dashboard = useMemo(() => marketInterviewDashboard(result), [result]);
  const [selectedTheme, setSelectedTheme] = useState(null);
  const [group, setGroup] = useState('ALL');
  const [selectedRespondent, setSelectedRespondent] = useState(null);
  const selected = dashboard.themes.find((theme) => theme.key === selectedTheme) ?? null;
  const linkedIds = useMemo(() => new Set(selected?.participantIds ?? []), [selected]);
  const respondents = dashboard.participants.filter((participant) => (group === 'ALL' || participant.group === group) && (!selected || linkedIds.has(participant.participantId)));
  if (!result) return null;
  const effectiveRespondent = respondents.some((item) => item.participantId === selectedRespondent)
    ? selectedRespondent : respondents[0]?.participantId ?? null;
  const activeIndex = Math.max(0, respondents.findIndex((item) => item.participantId === effectiveRespondent));
  const activeRespondent = respondents[activeIndex] ?? null;
  const requested = result.targeting?.requestedSampleSize ?? result.targeting?.drawnSampleSize;
  const failed = result.targeting?.failedCount ?? 0;
  const groups = ['TARGET', 'COMPARISON', 'PROXY', 'EXPLORATORY'].filter((value) => dashboard.participants.some((item) => item.group === value));

  return <div className="market-interview__result">
    <section className="market-interview__scope" aria-labelledby="market-interview-summary-title"><div><span>조사 요약</span><h2 id="market-interview-summary-title">가상 인터뷰 근거 범위</h2><strong>{REPRESENTATION_LABEL[result.targeting?.representationStatus] ?? '표현 범위 확인 필요'}</strong></div>
      <dl><div><dt>요청</dt><dd>{requested}명</dd></div><div><dt>코딩 응답</dt><dd>{dashboard.usableCount}명</dd></div><div><dt>직접 타겟</dt><dd>{result.targeting?.targetCount ?? 0}명</dd></div><div><dt>대리·탐색</dt><dd>{(result.targeting?.proxyCount ?? 0) + (result.targeting?.exploratoryCount ?? 0)}명</dd></div></dl>
      <p>{result.targeting?.criteriaText}</p>{failed ? <p>응답 생성 실패 {failed}명은 모든 코딩과 집계에서 제외했습니다.</p> : null}{result.targeting?.targetCoverageWarning ? <p role="status">{result.targeting.targetCoverageWarning}</p> : null}</section>

    {dashboard.headlines.length ? <section className="market-interview__headline" aria-labelledby="interview-headline-title"><div><span>핵심 Insight</span><h2 id="interview-headline-title">반복 패턴을 원문 근거와 함께 확인하세요</h2></div><div>{dashboard.headlines.map((item) => <button type="button" key={item.label} onClick={() => setSelectedTheme(item.key)}><span>{item.label}</span><strong>{item.title}</strong><small>{item.total}명 중 근거 응답 {item.count}명</small></button>)}</div></section>
      : <section className="market-interview__empty"><h2>표시할 반복 인사이트가 없습니다</h2><p>원문 근거가 연결된 theme이 없습니다.</p></section>}

    <div className="market-interview__dashboard"><section className="market-interview__insights" aria-label="테마 상세">
      {dashboard.sections.map((section) => <section key={section.id}><h2>{section.title}</h2>{section.themes.map((theme) => <ThemeRow key={theme.key} theme={theme} total={dashboard.usableCount} selected={selectedTheme === theme.key} onSelect={setSelectedTheme} />)}</section>)}
      {dashboard.crossRelationships.length ? <section><h2>패턴 간 연결</h2><div className="market-interview__relationships">{dashboard.crossRelationships.map((item) => <article key={`${item.suggestionTitle}-${item.relatedTitle}`}><div><small>개선 요구</small><strong>{item.suggestionTitle}</strong></div><span>연결됨</span><div><small>{item.relatedAxis === 'BARRIER' ? '거부 이유' : '우려'}</small><strong>{item.relatedTitle}</strong></div><b>공통 응답자 {item.overlapCount}명</b></article>)}</div></section> : null}
    </section>

      <aside className="market-interview__respondents" aria-labelledby="market-interview-participants"><header><div><span>응답자 근거</span><h2 id="market-interview-participants">Respondent Explorer</h2></div><div role="group" aria-label="응답자 그룹 필터"><button type="button" aria-pressed={group === 'ALL'} onClick={() => setGroup('ALL')}>전체</button>{groups.map((value) => <button type="button" key={value} aria-pressed={group === value} onClick={() => setGroup(value)}>{GROUP_LABEL[value]}</button>)}</div></header>
        {selected ? <p className="market-interview__filter-note"><strong>{selected.title}</strong>의 원문 근거가 있는 응답자만 표시합니다.<button type="button" onClick={() => setSelectedTheme(null)}>필터 해제</button></p> : null}
        <div className="market-interview__respondent-workspace"><nav aria-label="응답자 목록">{respondents.map((participant) => <button type="button" key={participant.participantId} aria-current={participant.participantId === effectiveRespondent ? 'true' : undefined} onClick={() => setSelectedRespondent(participant.participantId)}><strong>{participant.label}</strong><small>{GROUP_LABEL[participant.group]}</small><span>{participant.profile}</span></button>)}</nav>
          <RespondentDetail participant={activeRespondent} previousDisabled={activeIndex <= 0} nextDisabled={activeIndex >= respondents.length - 1} onPrevious={() => setSelectedRespondent(respondents[Math.max(0, activeIndex - 1)]?.participantId)} onNext={() => setSelectedRespondent(respondents[Math.min(respondents.length - 1, activeIndex + 1)]?.participantId)} /></div>
      </aside></div>

    {result.saturation ? <section className="market-interview__section market-interview__diversity"><h2>다양성·한계</h2><dl><div><dt>코딩 응답</dt><dd>{result.saturation.codedParticipantCount}명</dd></div><div><dt>theme</dt><dd>{result.saturation.themeCount}개</dd></div><div><dt>표본 성격</dt><dd>{REPRESENTATION_LABEL[result.targeting?.representationStatus] ?? result.saturation.assessment}</dd></div></dl><p>{result.saturation.limitation}</p></section> : null}
    <ListSection title="실제 고객에게 확인할 질문" items={result.followUpQuestions} /><ListSection title="해석할 때의 한계" items={result.limitations} />
  </div>;
}
