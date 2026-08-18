import { Link, useParams } from 'react-router-dom';
import { Button, EmptyState, ErrorState, LoadingState, ProjectWorkspace } from '../../../shared/ui/index.js';
import useMarketingStrategy from '../hooks/useMarketingStrategy.js';
import { projectRoutes } from '../../../app/routing/projectRoutes.js';
import '../styles/marketing-content.css';

const SOURCE_LABELS = {
  PROJECT: '프로젝트 정보', CURRENT_CONCEPT: '현재 사업안·법률 조건', MARKET: '시장 분석',
  BUSINESS_MODEL: '비즈니스 모델 분석', MARKET_INTERVIEW: '시장 인터뷰',
  LAUNCH_TECHNOLOGY: '기술 분석', LAUNCH_OPERATIONS: '운영 분석',
  FINANCE: '재무 분석', FINANCE_REPORT: '재무 분석',
};

function List({ values = [] }) {
  if (!values.length) return <p className="mk-strategy__muted">표시할 항목이 없습니다.</p>;
  return <ul>{values.map((value, index) => <li key={`${value}-${index}`}>{value}</li>)}</ul>;
}

export default function MarketingStrategyReportPage() {
  const { projectId } = useParams();
  const strategy = useMarketingStrategy(projectId);
  const result = strategy.view?.result;
  if (strategy.loading) return <LoadingState label="마케팅 전략 보고서를 불러오고 있습니다" />;
  if (strategy.error && !result) return <ErrorState title="전략 보고서를 불러오지 못했습니다" description={strategy.error.message} onRetry={() => void strategy.refresh()} />;
  if (!result) return <EmptyState title="표시할 최신 전략이 없습니다" description="마케팅 전략을 먼저 생성해 주세요." action={<Link to={projectRoutes.marketing(projectId)}>마케팅 전략으로 돌아가기</Link>} />;

  const evidence = [...new Set((result.evidenceRefs ?? []).map((ref) => SOURCE_LABELS[String(ref).split(':', 1)[0]] ?? '사용 분석 자료'))];
  return <ProjectWorkspace mode="document" className="mk-strategy-report">
    <header className="mk-strategy-report__toolbar"><Link to={projectRoutes.marketing(projectId)}>마케팅 전략으로 돌아가기</Link><div><Button type="button" variant="outline" onClick={() => window.print()}>PDF로 저장</Button><Button type="button" loading={strategy.downloading} onClick={() => void strategy.download()}>PDF 다운로드</Button></div></header>
    {strategy.error && <p className="mk-alert mk-alert--danger" role="alert">{strategy.error.message}</p>}
    <article className="mk-strategy-report__paper">
      <header className="mk-strategy-report__cover"><p>MARKETING STRATEGY</p><h1>마케팅 전략 보고서</h1><h2>{result.executiveSummary}</h2><span>{strategy.view?.generatedAt ? new Date(strategy.view.generatedAt).toLocaleString('ko-KR') : '현재 사업안 기준'}</span></header>
      <section><h2>1. 전략 요약</h2><div className="mk-strategy-report__callout">{result.executiveSummary}</div><List values={result.contentPillars} /></section>
      <section><h2>2. 타깃 고객</h2><div className="mk-strategy-report__grid">{(result.targetCustomers ?? []).map((item) => <article key={item}>{item}</article>)}</div></section>
      <section><h2>3. 포지셔닝</h2><div className="mk-strategy-report__callout">{result.positioning}</div></section>
      <section><h2>4. 핵심 메시지</h2><List values={result.coreMessages} /></section>
      <section><h2>5. 채널 전략</h2><div className="mk-strategy-report__grid">{(result.channelStrategies ?? []).map((channel) => <article key={channel.channel}><h3>{channel.channel}</h3><p><strong>목표</strong> {channel.objective}</p><p><strong>대상</strong> {channel.audience}</p><p>{channel.rationale}</p><h4>실행 항목</h4><List values={channel.actions} /><h4>KPI</h4><List values={channel.kpis} /></article>)}</div></section>
      <section><h2>6. 캠페인 로드맵</h2><ol className="mk-strategy-report__timeline">{(result.campaignRoadmap ?? []).map((phase) => <li key={phase.phase}><h3>{phase.phase} · {phase.objective}</h3><List values={phase.actions} /><h4>KPI</h4><List values={phase.kpis} /></li>)}</ol></section>
      <section><h2>7. 예산·KPI</h2><List values={result.budgetGuidelines} /></section>
      <section><h2>8. 위험 및 주의사항</h2><List values={result.risks} /></section>
      <section><h2>9. 근거 요약</h2><List values={evidence} /></section>
    </article>
  </ProjectWorkspace>;
}
