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

function CellList({ values = [] }) {
  return values.length ? values.map((value) => <div key={value}>{value}</div>) : '—';
}

export default function MarketingStrategyReportPage() {
  const { projectId } = useParams();
  const strategy = useMarketingStrategy(projectId);
  const result = strategy.view?.result;
  if (strategy.loading) return <LoadingState label="마케팅 전략 보고서를 불러오고 있습니다" />;
  if (strategy.error && !result) return <ErrorState title="전략 보고서를 불러오지 못했습니다" description={strategy.error.message} onRetry={() => void strategy.refresh()} />;
  if (!result) return <EmptyState title="표시할 최신 전략이 없습니다" description="마케팅 전략을 먼저 생성해 주세요." action={<Link to={projectRoutes.marketing(projectId)}>마케팅 전략으로 돌아가기</Link>} />;

  const evidence = [...new Set((result.evidenceRefs ?? []).map((ref) => SOURCE_LABELS[String(ref).split(':', 1)[0]] ?? '사용 분석 자료'))];
  const generatedAt = strategy.view?.generatedAt ? new Date(strategy.view.generatedAt) : null;
  const writtenDate = generatedAt?.toLocaleDateString('ko-KR') ?? '현재 기준';
  const projectName = strategy.view?.projectName ?? `프로젝트 ${projectId}`;
  return <ProjectWorkspace mode="document" className="mk-strategy-report">
    <header className="mk-strategy-report__toolbar"><Link className="mk-button-link" to={projectRoutes.marketing(projectId)}>마케팅 전략으로 돌아가기</Link><Button type="button" variant="outline" onClick={() => window.print()}>PDF로 저장</Button></header>
    {strategy.error && <p className="mk-alert mk-alert--danger" role="alert">{strategy.error.message}</p>}
    <article className="mk-strategy-report__paper">
      <header className="mk-strategy-report__cover"><p>MARKETING STRATEGY REPORT</p><h1>마케팅 전략 보고서</h1><dl><div><dt>사업명</dt><dd>{projectName}</dd></div><div><dt>작성일</dt><dd>{writtenDate}</dd></div><div><dt>버전</dt><dd>현재 전략본</dd></div><div><dt>작성 기준</dt><dd>현재 사업안 및 생성 시점 가용 분석 자료</dd></div></dl></header>
      <section className="mk-strategy-report__document-info"><h2>문서 정보</h2><table><tbody><tr><th>문서명</th><td>마케팅 전략 보고서</td><th>사업명</th><td>{projectName}</td></tr><tr><th>작성일</th><td>{writtenDate}</td><th>자료 기준일</th><td>{writtenDate}</td></tr></tbody></table><table className="mk-strategy-report__approval"><thead><tr><th>구분</th><th>작성</th><th>검토</th><th>승인</th></tr></thead><tbody><tr><th>담당</th><td></td><td></td><td></td></tr><tr><th>서명/날인</th><td></td><td></td><td></td></tr><tr><th>일자</th><td></td><td></td><td></td></tr></tbody></table></section>
      <section className="mk-strategy-report__toc"><h2>목차</h2><ol>{['전략 요약', '타깃 고객', '포지셔닝', '핵심 메시지', '채널 전략', '캠페인 로드맵', '예산·KPI 운영', '위험 및 주의사항', '사용 근거'].map((label, index) => <li key={label}><span>{index + 1}</span>{label}</li>)}</ol></section>
      <section><h2>1. 전략 요약</h2><div className="mk-strategy-report__callout">{result.executiveSummary}</div><h3>콘텐츠 운영 주제</h3><List values={result.contentPillars} /></section>
      <section><h2>2. 타깃 고객</h2><div className="mk-strategy-report__grid">{(result.targetCustomers ?? []).map((item) => <article key={item}>{item}</article>)}</div></section>
      <section><h2>3. 포지셔닝</h2><div className="mk-strategy-report__callout">{result.positioning}</div></section>
      <section><h2>4. 핵심 메시지</h2><List values={result.coreMessages} /></section>
      <section><h2>5. 채널 전략</h2><div className="mk-strategy-report__table-wrap"><table><thead><tr><th>채널</th><th>목표</th><th>대상</th><th>주요 실행</th><th>KPI</th></tr></thead><tbody>{(result.channelStrategies ?? []).map((channel) => <tr key={channel.channel}><th>{channel.channel}</th><td>{channel.objective}</td><td>{channel.audience}</td><td><CellList values={channel.actions} /></td><td><CellList values={channel.kpis} /></td></tr>)}</tbody></table></div></section>
      <section><h2>6. 캠페인 로드맵</h2><div className="mk-strategy-report__table-wrap"><table><thead><tr><th>단계</th><th>목표</th><th>주요 실행</th><th>KPI</th></tr></thead><tbody>{(result.campaignRoadmap ?? []).map((phase) => <tr key={phase.phase}><th>{phase.phase}</th><td>{phase.objective}</td><td><CellList values={phase.actions} /></td><td><CellList values={phase.kpis} /></td></tr>)}</tbody></table></div></section>
      <section><h2>7. 예산·KPI</h2><List values={result.budgetGuidelines} /></section>
      <section><h2>8. 위험 및 주의사항</h2><table><thead><tr><th>위험·주의사항</th></tr></thead><tbody>{(result.risks ?? []).map((risk) => <tr key={risk}><td>{risk}</td></tr>)}</tbody></table></section>
      <section><h2>9. 근거 요약</h2><List values={evidence} /></section>
    </article>
  </ProjectWorkspace>;
}
