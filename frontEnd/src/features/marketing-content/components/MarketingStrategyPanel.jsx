import { Link } from 'react-router-dom';

const SOURCE_LABELS = Object.freeze({
  PROJECT: '프로젝트', CURRENT_CONCEPT: '사업안·법률', MARKET: '시장 분석',
  BUSINESS_MODEL: 'BM 분석', LAUNCH_TECHNOLOGY: '기술 분석',
  LAUNCH_OPERATIONS: '운영 분석', FINANCE: '재무 분석',
  MARKET_INTERVIEW: '시장 인터뷰',
});
const SOURCE_ORDER = Object.keys(SOURCE_LABELS);
const EVIDENCE_GROUP = {
  MARKET: '시장 분석 근거', BUSINESS_MODEL: 'BM 분석 근거', FINANCE: '재무 분석 근거',
  FINANCE_REPORT: '재무 분석 근거', MARKET_INTERVIEW: '인터뷰 근거',
  CURRENT_CONCEPT: '사업안·법률 근거', LAUNCH_TECHNOLOGY: '기술 분석 근거',
  LAUNCH_OPERATIONS: '운영 분석 근거', PROJECT: '프로젝트 근거',
};
const NAV = [['strategy-summary', '전략 요약'], ['strategy-target', '타깃·포지셔닝'],
  ['strategy-channel', '채널'], ['strategy-campaign', '캠페인'],
  ['strategy-budget', '예산·KPI'], ['strategy-risk', '위험·근거']];

function List({ values = [], empty = '표시할 항목이 없습니다.' }) {
  return values.length ? <ul>{values.map((value, index) => <li key={`${value}-${index}`}>{value}</li>)}</ul>
    : <p className="mk-strategy__muted">{empty}</p>;
}

function SourceStatus({ view }) {
  const available = new Set((view?.sourceManifest ?? []).map((item) => item.type));
  return <section className="mk-strategy__sources"><header><div><p>전략 입력 자료</p><h3>이번 전략에 사용한 현재 자료</h3></div></header><ul>{SOURCE_ORDER.map((type) => {
    const connected = type === 'FINANCE'
      ? available.has('FINANCE') || available.has('FINANCE_REPORT') : available.has(type);
    return <li key={type} data-ready={connected}><span>{connected ? '✓' : '–'}</span><strong>{SOURCE_LABELS[type]}</strong><small>{connected ? '포함됨' : '이번 전략에 포함되지 않음'}</small></li>;
  })}</ul></section>;
}

export default function MarketingStrategyPanel({ strategy, onNext }) {
  const view = strategy.view;
  const result = view?.result;
  const evidenceGroups = (result?.evidenceRefs ?? []).reduce((groups, ref) => {
    const type = String(ref).split(':', 1)[0];
    const label = EVIDENCE_GROUP[type] ?? '기타 근거';
    groups[label] = [...(groups[label] ?? []), ref];
    return groups;
  }, {});
  const latestStage = strategy.jobEvents?.events?.at(-1)?.stage ?? view?.status;
  const activeStep = latestStage === 'ANALYZING' || latestStage === 'RUNNING' ? 2
    : latestStage === 'COMPLETED' || latestStage === 'SUCCEEDED' ? 3 : 1;

  return <div className="mk-strategy">
    <SourceStatus view={view} />
    {!view?.ready && <div className="mk-alert mk-alert--danger" role="alert"><strong>현재 확정 사업안이 필요합니다.</strong><p>사업안 선택과 기준 확정을 먼저 완료해 주세요.</p></div>}
    {strategy.error && <div className="mk-alert mk-alert--danger" role="alert">{strategy.error.message}</div>}
    {strategy.active && <section className="mk-strategy-progress" aria-live="polite"><header><span>최신 전략 생성 중…</span><strong>{result ? '최신 자료로 전략을 다시 작성하고 있습니다.' : '현재 사업안과 사용 가능한 분석 자료로 전략을 작성하고 있습니다.'}</strong><p>{result && '기존 전략은 새 결과가 준비될 때까지 아래에 유지됩니다.'}</p></header><ol>{['입력 자료 확인', '전략 작성', '결과 정리'].map((label, index) => { const step = index + 1; return <li key={label} data-state={step < activeStep ? 'complete' : step === activeStep ? 'active' : 'pending'}><span>{step < activeStep ? '✓' : step}</span><strong>{label}</strong></li>; })}</ol></section>}
    {!result && !strategy.active && <section className="mk-strategy__empty"><h3>현재 사업안으로 마케팅 전략을 만드세요</h3><p>시장·BM·재무·인터뷰 결과는 존재하는 경우에만 활용하며, 없는 분석 때문에 전략 생성을 막지 않습니다.</p><button className="mk-primary" type="button" disabled={!view?.ready} onClick={() => void Promise.resolve(strategy.generate()).catch(() => {})}>마케팅 전략 생성</button></section>}

    {result && <section className="mk-strategy__result" data-generating={strategy.active || undefined}>
      {view.stale && <div className="mk-alert mk-alert--warning" role="alert">이전 사업안 기준 전략입니다. 현재 사업안으로 다시 생성해 주세요.</div>}
      <nav className="mk-strategy__nav" aria-label="마케팅 전략 바로가기">{NAV.map(([id, label]) => <a key={id} href={`#${id}`}>{label}</a>)}</nav>
      <section id="strategy-summary" className="mk-strategy__hero"><p>마케팅 전략 · 현재 사업안 기준</p><h2>{result.executiveSummary}</h2><div className="mk-strategy__pillars"><List values={result.contentPillars} /></div></section>

      <section id="strategy-target" className="mk-strategy__section"><header><p>WHO & WHY</p><h3>타깃·포지셔닝</h3></header><div className="mk-strategy__targets">{(result.targetCustomers ?? []).map((customer) => <article key={customer}>{customer}</article>)}</div><article className="mk-strategy__positioning"><span>포지셔닝</span><p>{result.positioning}</p></article><div className="mk-strategy__messages">{(result.coreMessages ?? []).map((message) => <article key={message}><span>핵심 메시지</span><strong>{message}</strong></article>)}</div></section>

      <section id="strategy-channel" className="mk-strategy__section"><header><p>CHANNEL PLAN</p><h3>채널 전략</h3></header><div className="mk-strategy__cards">{(result.channelStrategies ?? []).map((channel) => <article key={channel.channel}><header><strong>{channel.channel}</strong><span>{channel.objective}</span></header><p><b>대상</b>{channel.audience}</p><details><summary>실행 항목·KPI 보기</summary><h4>선정 근거</h4><p>{channel.rationale}</p><h4>실행 항목</h4><List values={channel.actions} /><h4>KPI</h4><List values={channel.kpis} /></details></article>)}</div></section>

      <section id="strategy-campaign" className="mk-strategy__section"><header><p>ROADMAP</p><h3>캠페인 로드맵</h3></header><ol className="mk-strategy__roadmap">{(result.campaignRoadmap ?? []).map((phase, index) => <li key={`${phase.phase}-${index}`}><span>{index + 1}</span><article><small>{phase.phase}</small><h4>{phase.objective}</h4><details><summary>실행과 KPI 보기</summary><h5>실행</h5><List values={phase.actions} /><h5>KPI</h5><List values={phase.kpis} /></details></article></li>)}</ol></section>

      <section id="strategy-budget" className="mk-strategy__section"><header><p>OPERATING RULES</p><h3>예산·KPI 운영 기준</h3></header><div className="mk-strategy__budget"><List values={result.budgetGuidelines} empty="별도 금액이나 예산 운영 기준이 없습니다." /></div><div className="mk-strategy__kpi-grid">{(result.channelStrategies ?? []).map((channel) => <article key={channel.channel}><strong>{channel.channel}</strong><List values={channel.kpis} /></article>)}</div></section>

      <section id="strategy-risk" className="mk-strategy__section"><header><p>GUARDRAILS</p><h3>위험·근거</h3></header><div className="mk-strategy__risk-grid"><article><h4>위험 및 주의사항</h4><List values={result.risks} /></article><article><h4>근거 연결</h4>{Object.entries(evidenceGroups).map(([label, refs]) => <details key={label}><summary>{label} · {refs.length}건</summary><ul>{refs.map((ref) => <li key={ref}><code>{ref}</code></li>)}</ul></details>)}</article></div></section>

      <footer className="mk-strategy__actions"><Link className="mk-button-link" to="report">보고서 보기</Link><button type="button" disabled={strategy.active} onClick={() => void Promise.resolve(strategy.generate()).catch(() => {})}>{strategy.active ? '최신 전략 생성 중…' : '최신 자료로 다시 생성'}</button><button className="mk-primary" type="button" disabled={!strategy.current} onClick={onNext}>이 전략으로 콘텐츠 만들기</button></footer>
    </section>}
  </div>;
}
