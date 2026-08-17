import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useParams } from 'react-router-dom';

import { useApiClient } from '../../shared/api/ApiClientProvider.jsx';
import { getUserErrorMessage } from '../../shared/api/apiError.js';
import { Button, ErrorState, LoadingState, ProjectStageHeader, ProjectWorkspace } from '../../shared/ui/index.js';
import { createFinalReportApi } from './finalReportApi.js';
import './final-report.css';

const STATE_VIEW = {
  CURRENT: { label: '최신 보고서', tone: 'success' },
  STALE: { label: '업데이트 필요', tone: 'warning' },
  READY: { label: '생성 가능', tone: 'success' },
  NOT_READY: { label: '준비 중', tone: 'neutral' },
};

const FIELD_LABELS = {
  overview: '문제와 사업 개요', summary: '핵심 요약', interpretation: '확정 해석', name: '명칭',
  description: '설명', industryCategory: '사업 분야', status: '상태', candidate: '선정 사업안',
  decision: '판정', confidence: '신뢰 수준', caveats: '한계와 주의사항', evidence: '근거',
  assumptions: '가정', recommendations: '권고사항', risks: '위험', artifacts: '산출물',
};

function fieldLabel(key) {
  if (FIELD_LABELS[key]) return FIELD_LABELS[key];
  return key.replace(/([a-z0-9])([A-Z])/g, '$1 $2').replaceAll('_', ' ');
}

function sourceTypeLabel(type) {
  return ({ PROJECT: '프로젝트 정보', CURRENT_CONCEPT: '현재 확정 사업안',
    BUSINESS_VALIDATION_SESSION: '사업성 검증', MARKET: '시장 분석', BUSINESS_MODEL: '비즈니스 모델',
    MARKET_INTERVIEW: '시장 인터뷰', TWIN_SURVEY: '트윈 패널 조사', MARKETING: '마케팅 콘텐츠',
    MARKETING_ASSETS: '마케팅 소재', LAUNCH_TECHNOLOGY: '기술 준비',
    LAUNCH_OPERATIONS: '운영 준비', FINANCE: '재무 분석', FINANCE_REPORT: '재무 분석 보고서' })[type] ?? '프로젝트 자료';
}

function ReportValue({ value, depth = 0 }) {
  if (value == null || value === '') return <span className="final-report__missing">자료 없음</span>;
  if (typeof value === 'string' || typeof value === 'number' || typeof value === 'boolean') return <span>{String(value)}</span>;
  if (Array.isArray(value)) {
    if (value.length === 0) return <span className="final-report__missing">자료 없음</span>;
    return <ul>{value.map((item, index) => <li key={typeof item === 'object' ? index : String(item)}><ReportValue value={item} depth={depth + 1} /></li>)}</ul>;
  }
  const visibleEntries = Object.entries(value).filter(([key]) => !/(^|_)(taskRun|sourceBinding|schemaVersion)|(^|_)([a-z]*Id|[a-z]*Hash|[a-z]*Revision)$/i.test(key));
  return <dl className={depth > 1 ? 'is-nested' : ''}>{visibleEntries.map(([key, item]) => <div key={key}><dt>{fieldLabel(key)}</dt><dd><ReportValue value={item} depth={depth + 1} /></dd></div>)}</dl>;
}

function ReportDocument({ view }) {
  const report = view.report ?? {};
  const metadata = report.metadata ?? {};
  return <article className="final-report-document">
    <header className="final-report-document__cover"><p>VENTURE VERIFY</p><h1>{report.title ?? '사업 타당성 검토 보고서'}</h1><dl><div><dt>프로젝트명</dt><dd>{metadata.projectName ?? '자료 없음'}</dd></div><div><dt>사업 분야</dt><dd>{metadata.industryCategory ?? '자료 없음'}</dd></div><div><dt>작성일</dt><dd>{metadata.generatedAt ? new Date(metadata.generatedAt).toLocaleDateString('ko-KR') : '초안'}</dd></div><div><dt>분석 기준일</dt><dd>{metadata.analysisBaseAt ? new Date(metadata.analysisBaseAt).toLocaleDateString('ko-KR') : '현재 자료 기준'}</dd></div><div><dt>보고서 버전</dt><dd>{view.version ?? '초안'}</dd></div></dl></header>
    {(report.sections ?? []).map((section) => <section key={section.number} className="final-report-section"><h2>{section.number}. {section.title}</h2>{section.sources?.map((source) => <div key={`${section.number}-${source.type}`} className="final-report-source"><h3>{sourceTypeLabel(source.type)}</h3>{source.status === 'MISSING' ? <p className="final-report__missing">{source.label ?? '현재 유효한 결과가 없습니다.'}</p> : <ReportValue value={source.data} />}<footer>사용한 자료: {sourceTypeLabel(source.type)}</footer></div>)}</section>)}
    <footer className="final-report-document__appendix"><h2>부록 · 사용된 자료</h2><p>{report.caveat}</p><ul>{((view.sourceManifest?.sources ?? view.sourceManifest) || []).map((source) => <li key={`${source.type}-${source.generatedAt ?? source.version ?? ''}`}><strong>{sourceTypeLabel(source.type)}</strong>{source.generatedAt ? ` · ${new Date(source.generatedAt).toLocaleDateString('ko-KR')} 생성` : ' · 현재 결과'}</li>)}</ul></footer>
  </article>;
}

export default function FinalReportPage() {
  const { projectId } = useParams();
  const client = useApiClient();
  const api = useMemo(() => createFinalReportApi(client), [client]);
  const pendingCommandKey = useRef(null);
  const [state, setState] = useState({ status: 'loading', view: null, error: null, generating: false });
  const load = useCallback(() => {
    const controller = new AbortController();
    setState((current) => ({ ...current, status: 'loading', error: null }));
    api.current(projectId, { signal: controller.signal })
      .then((view) => { if (!controller.signal.aborted) setState({ status: 'success', view, error: null, generating: false }); })
      .catch((error) => { if (!controller.signal.aborted) setState({ status: 'error', view: null, error, generating: false }); });
    return controller;
  }, [api, projectId]);
  useEffect(() => { const controller = load(); return () => controller.abort(); }, [load]);
  const generate = async () => {
    if (state.generating) return;
    setState((current) => ({ ...current, generating: true, error: null }));
    const before = state.view;
    const commandKey = pendingCommandKey.current ?? globalThis.crypto?.randomUUID?.()
      ?? `final-report-${Date.now()}-${Math.random().toString(16).slice(2)}`;
    pendingCommandKey.current = commandKey;
    try {
      const view = await api.generate(projectId, commandKey);
      pendingCommandKey.current = null;
      setState({ status: 'success', view, error: null, generating: false });
    } catch (error) {
      try {
        const recovered = await api.current(projectId);
        const changed = recovered.snapshotId && (recovered.snapshotId !== before?.snapshotId || recovered.version !== before?.version);
        if (changed) {
          pendingCommandKey.current = null;
          setState({ status: 'success', view: recovered, error: null, generating: false });
          return;
        }
      } catch { /* The original safe user error remains authoritative. */ }
      setState((current) => ({ ...current, generating: false, error }));
    }
  };

  if (state.status === 'loading') return <LoadingState label="최종 보고서를 불러오고 있습니다" />;
  if (state.status === 'error') return <ErrorState title="최종 보고서를 불러오지 못했습니다" description={getUserErrorMessage(state.error)} onRetry={load} />;
  const view = state.view;
  const statusView = STATE_VIEW[view.state] ?? STATE_VIEW.NOT_READY;
  return <ProjectWorkspace mode="document" className="final-report-page">
    <ProjectStageHeader step={6} eyebrow="최종 보고서" title="사업의 전체 검토 결과를 한 문서에서 확인하세요"
      description="현재 확정 사업안과 유효한 분석 결과만 사용하며, 실행하지 않은 내용은 추정하지 않습니다."
      status={<span className="pipeline-status" data-tone={statusView.tone}>{statusView.label}</span>}
      actions={<>{view.state === 'CURRENT' && <Button type="button" variant="outline" onClick={() => window.print()}>PDF로 저장</Button>}<Button type="button" loading={state.generating} onClick={generate}>{view.state === 'STALE' ? '보고서 업데이트' : view.state === 'CURRENT' ? '새 버전 만들기' : '최종 보고서 만들기'}</Button></>} />
    {state.error && <p className="final-report-error" role="alert">{getUserErrorMessage(state.error)}</p>}
    {view.state === 'NOT_READY' && <section className="final-report-readiness" aria-labelledby="report-readiness-title"><h2 id="report-readiness-title">보고서 준비 상태</h2><p>현재 확정된 사업안과 사업성 검증 결과를 먼저 준비해 주세요.</p>{view.blockingSources?.length > 0 && <p>핵심 자료가 준비되면 결과 보고서를 만들 수 있습니다.</p>}</section>}
    {view.omittedSources?.length > 0 && <section className="final-report-readiness"><h2>아직 실행하지 않은 단계</h2><ul>{view.omittedSources.map((type) => <li key={type}>{sourceTypeLabel(type)} · 미실행</li>)}</ul></section>}
    {view.state === 'STALE' && <p className="final-report-stale" role="status">보고서를 만든 뒤 사업안 또는 포함된 분석 결과가 변경되었습니다. 최신 결과로 새 보고서를 만들어 주세요.</p>}
    <ReportDocument view={view} />
  </ProjectWorkspace>;
}
