import { useCallback, useEffect, useMemo, useState } from 'react';
import { useParams } from 'react-router-dom';

import { useApiClient } from '../../shared/api/ApiClientProvider.jsx';
import { getUserErrorMessage } from '../../shared/api/apiError.js';
import { Button, ErrorState, LoadingState, ProjectStageHeader, ProjectWorkspace } from '../../shared/ui/index.js';
import { createFinalReportApi } from './finalReportApi.js';
import './final-report.css';

const STATE_VIEW = {
  CURRENT: { label: '최신 보고서', tone: 'success' },
  STALE: { label: '업데이트 필요', tone: 'warning' },
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
  return ({ PROJECT: '프로젝트 정보', IDEA_BRIEF: '사업 아이디어', CONCEPT_SELECTION: '선정 사업안',
    MARKET_RESEARCH: '시장 분석', BUSINESS_MODEL: '수익 구조 분석', TECH_OPS: '기술·운영 계획',
    FINANCE: '재무 분석', TWIN_SURVEY: '가상 인터뷰', MARKETING: '마케팅 콘텐츠' })[type] ?? '프로젝트 자료';
}

function ReportValue({ value, depth = 0 }) {
  if (value == null || value === '') return <span className="final-report__missing">자료 없음</span>;
  if (typeof value === 'string' || typeof value === 'number' || typeof value === 'boolean') return <span>{String(value)}</span>;
  if (Array.isArray(value)) {
    if (value.length === 0) return <span className="final-report__missing">자료 없음</span>;
    return <ul>{value.map((item, index) => <li key={typeof item === 'object' ? index : String(item)}><ReportValue value={item} depth={depth + 1} /></li>)}</ul>;
  }
  return <dl className={depth > 1 ? 'is-nested' : ''}>{Object.entries(value).map(([key, item]) => <div key={key}><dt>{fieldLabel(key)}</dt><dd><ReportValue value={item} depth={depth + 1} /></dd></div>)}</dl>;
}

function ReportDocument({ view }) {
  const report = view.report ?? {};
  const metadata = report.metadata ?? {};
  return <article className="final-report-document">
    <header className="final-report-document__cover"><p>VENTURE VERIFY</p><h1>{report.title ?? '사업 타당성 검토 보고서'}</h1><dl><div><dt>프로젝트명</dt><dd>{metadata.projectName ?? '자료 없음'}</dd></div><div><dt>사업 분야</dt><dd>{metadata.industryCategory ?? '자료 없음'}</dd></div><div><dt>작성일</dt><dd>{metadata.generatedAt ? new Date(metadata.generatedAt).toLocaleDateString('ko-KR') : '초안'}</dd></div><div><dt>분석 기준일</dt><dd>{metadata.analysisBaseAt ? new Date(metadata.analysisBaseAt).toLocaleDateString('ko-KR') : '현재 자료 기준'}</dd></div><div><dt>보고서 버전</dt><dd>{view.version ?? '초안'}</dd></div></dl></header>
    {(report.sections ?? []).map((section) => <section key={section.number} className="final-report-section"><h2>{section.number}. {section.title}</h2>{section.sources?.map((source) => <div key={`${section.number}-${source.type}`} className="final-report-source"><h3>{sourceTypeLabel(source.type)}</h3>{source.status === 'MISSING' ? <p className="final-report__missing">자료 없음 · 미완료</p> : <ReportValue value={source.data} />}<footer>사용한 자료: {sourceTypeLabel(source.type)}</footer></div>)}</section>)}
    <footer className="final-report-document__appendix"><h2>부록 · 사용된 자료와 버전</h2><p>{report.caveat}</p><details><summary>기술 정보</summary><table><thead><tr><th>자료</th><th>ID</th><th>버전 / 수정 이력</th><th>결과 식별값</th></tr></thead><tbody>{(view.sourceManifest ?? []).map((source) => <tr key={`${source.type}-${source.id}`}><td>{sourceTypeLabel(source.type)}</td><td>{source.id}</td><td>{source.version ?? source.revision ?? '-'}</td><td>{source.resultHash ?? '-'}</td></tr>)}</tbody></table></details></footer>
  </article>;
}

export default function FinalReportPage() {
  const { projectId } = useParams();
  const client = useApiClient();
  const api = useMemo(() => createFinalReportApi(client), [client]);
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
    try {
      const view = await api.generate(projectId);
      setState({ status: 'success', view, error: null, generating: false });
    } catch (error) {
      setState((current) => ({ ...current, generating: false, error }));
    }
  };

  if (state.status === 'loading') return <LoadingState label="최종 보고서를 불러오고 있습니다" />;
  if (state.status === 'error') return <ErrorState title="최종 보고서를 불러오지 못했습니다" description={getUserErrorMessage(state.error)} onRetry={load} />;
  const view = state.view;
  const statusView = STATE_VIEW[view.state] ?? STATE_VIEW.NOT_READY;
  return <ProjectWorkspace mode="document" className="final-report-page">
    <ProjectStageHeader step={9} eyebrow="최종 보고서" title="사업의 전체 검토 결과를 한 문서에서 확인하세요"
      description="확정된 프로젝트 자료만 사용하며, 준비되지 않은 내용은 추정하지 않습니다."
      status={<span className="pipeline-status" data-tone={statusView.tone}>{statusView.label}</span>}
      actions={<>{view.state === 'CURRENT' && <Button type="button" variant="outline" onClick={() => window.print()}>PDF로 저장</Button>}<Button type="button" loading={state.generating} onClick={generate}>{view.state === 'STALE' ? '보고서 업데이트' : view.state === 'CURRENT' ? '새 버전 만들기' : '최종 보고서 만들기'}</Button></>} />
    {state.error && <p className="final-report-error" role="alert">{getUserErrorMessage(state.error)}</p>}
    {view.state === 'NOT_READY' && <section className="final-report-readiness" aria-labelledby="report-readiness-title"><h2 id="report-readiness-title">보고서 준비 상태</h2><p>현재 저장된 자료로 초안을 표시합니다. 없는 자료는 추정하지 않습니다.</p><ul>{view.readiness.map((item) => <li key={item.journeyId}><span>{item.label}</span><strong>{({ COMPLETED: '완료', IN_PROGRESS: '진행 중', READY: '시작 가능', NEEDS_INPUT: '입력 필요', ATTENTION: '확인 필요', STALE: '업데이트 필요', NOT_STARTED: '시작 전' })[item.status] ?? '상태 확인 필요'}</strong></li>)}</ul>{view.missingSources.length > 0 && <p>아직 준비되지 않은 자료가 있습니다.</p>}</section>}
    {view.state === 'STALE' && <p className="final-report-stale" role="status">보고서를 만든 뒤 프로젝트 자료가 변경되었습니다. 최신 내용으로 업데이트해 주세요.</p>}
    <ReportDocument view={view} />
  </ProjectWorkspace>;
}
