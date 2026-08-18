import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useParams } from 'react-router-dom';

import { useApiClient } from '../../shared/api/ApiClientProvider.jsx';
import { getUserErrorMessage } from '../../shared/api/apiError.js';
import { Button, ErrorState, LoadingState, ProjectStageHeader, ProjectWorkspace } from '../../shared/ui/index.js';
import { createFinalReportApi } from './finalReportApi.js';
import './final-report.css';

const SOURCE_LABELS = {
  CURRENT_CONCEPT: '현재 확정 사업안', BUSINESS_VALIDATION_SESSION: '사업성 검증', MARKET: '시장 분석',
  BUSINESS_MODEL: '비즈니스 모델', MARKET_INTERVIEW: '시장 인터뷰',
  MARKETING_STRATEGY: '마케팅 전략', MARKETING: '마케팅 콘텐츠', LAUNCH_TECHNOLOGY: '기술 분석',
  LAUNCH_OPERATIONS: '운영 분석', FINANCE: '재무 분석', FINANCE_REPORT: '재무 분석',
};
const OPTIONAL = ['MARKET_INTERVIEW', 'MARKETING_STRATEGY', 'MARKETING', 'LAUNCH_TECHNOLOGY',
  'LAUNCH_OPERATIONS', 'FINANCE'];
const SOURCE_STATE_LABELS = {
  AVAILABLE: '현재 결과 사용 가능', AVAILABLE_FINAL: '최종 저장본 사용 가능', AVAILABLE_DRAFT: '초안 있음 · 검토 전',
  FAILED: '최근 실행 실패 · 포함할 결과 없음', IN_PROGRESS: '현재 실행 중',
  CURRENT_RESULT_UNAVAILABLE: '현재 결과 없음', NOT_RUN: '실행 안 함',
  UPDATE_REQUIRED: '업데이트 필요',
};
const STATE_VIEW = {
  CURRENT: ['최신 사업기획서', 'success'], STALE: ['업데이트 필요', 'warning'], READY: ['생성 가능', 'success'],
  NOT_READY: ['필수 자료 준비 중', 'neutral'], GENERATING: ['사업기획서 작성 중', 'neutral'],
};

function key() {
  return globalThis.crypto?.randomUUID?.() ?? `final-report-${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

function generationFailure(status) {
  if (status?.lastState !== 'FAILED') return null;
  const reason = status.lastErrorReason;
  if (reason === 'FIELD_CONSTRAINT_VIOLATION') return '포함한 분석 자료의 입력 형식이 맞지 않았습니다. 자료 상태를 새로고침한 뒤 다시 생성해 주세요.';
  if (reason === 'AI_RESULT_INVALID') return '생성된 문서의 형식을 확인하는 단계에서 완료하지 못했습니다. 다시 생성해 주세요.';
  return '최근 사업기획서 생성 작업을 완료하지 못했습니다. 현재 자료로 다시 생성할 수 있습니다.';
}

function displayManifestSources(sources = []) {
  const finance = sources.find((source) => ['FINANCE', 'FINANCE_REPORT'].includes(source.type));
  return [...sources.filter((source) => !['FINANCE', 'FINANCE_REPORT'].includes(source.type)),
    ...(finance ? [{ ...finance, type: 'FINANCE' }] : [])];
}

function Evidence({ refs, details }) {
  if (!refs?.length && !details?.length) return null;
  return <details className="proposal-evidence"><summary>근거 상세 보기</summary>{details?.length
    ? <div className="proposal-evidence__details">{details.map((item) => <article key={item.evidenceKey}><header><strong>{item.label}</strong><span>{SOURCE_LABELS[item.sourceType] ?? item.sourceType}</span></header><p>{item.summary ?? item.value}</p>{item.actualQuote ? <blockquote>“{item.actualQuote}”</blockquote> : null}<dl>{item.asOf ? <div><dt>기준 시점</dt><dd>{item.asOf}</dd></div> : null}<div><dt>출처 위치</dt><dd>{item.sourcePath}</dd></div>{item.respondentIds?.length ? <div><dt>응답자</dt><dd>{item.respondentIds.slice(0, 5).join(' · ')}{item.respondentIds.length > 5 ? ` 외 ${item.respondentIds.length - 5}명` : ''}</dd></div> : null}</dl>{item.limitation ? <small>{item.limitation}</small> : null}</article>)}</div>
    : <ul>{refs.map((ref) => <li key={ref}>{SOURCE_LABELS[ref.split(':')[0]] ?? ref.split(':')[0]}</li>)}</ul>}</details>;
}

function ProposalDocument({ view, review, includeReview }) {
  const report = view.report;
  if (!report) return null;
  const summary = report.executiveDecisionSummary ?? {};
  return <article className="proposal-document">
    <header className="proposal-cover"><p>BUSINESS PROPOSAL</p><h1>{report.cover?.documentName ?? '사업기획서'}</h1><h2>{report.cover?.businessName ?? '사업명'}</h2><dl><div><dt>문서번호</dt><dd>{view.snapshotId}</dd></div><div><dt>버전</dt><dd>{report.cover?.version ?? view.version}</dd></div><div><dt>작성일</dt><dd>{report.cover?.createdOn}</dd></div><div><dt>작성자</dt><dd>{view.generatedByName ?? '—'}</dd></div><div><dt>문서 상태</dt><dd>{report.cover?.documentStatus}</dd></div><div><dt>보안 구분</dt><dd>프로젝트 내부 검토용</dd></div></dl><table className="proposal-approval"><thead><tr><th>구분</th><th>작성</th><th>검토</th><th>승인</th></tr></thead><tbody><tr><th>성명</th><td>{view.generatedByName ?? ''}</td><td></td><td></td></tr><tr><th>서명/날인</th><td></td><td></td><td></td></tr><tr><th>일자</th><td></td><td></td><td></td></tr></tbody></table></header>
    <section className="proposal-document-toc"><h2>목차</h2><ol><li>의사결정 요약</li>{(report.sections ?? []).map((section) => <li key={section.number}>{section.number}. {section.title}</li>)}<li>부록</li>{includeReview && review?.result && <li>부록 · AI 사업기획서 검토 의견</li>}</ol></section>
    <section id="proposal-summary" className="proposal-summary"><p>EXECUTIVE DECISION SUMMARY</p><h2>의사결정 요약</h2><div className="proposal-callout"><strong>사업 한 줄 정의</strong><span>{summary.businessDefinition}</span></div><div className="proposal-kpis"><article><strong>추진 목적</strong><p>{summary.purpose}</p></article><article><strong>핵심 가치</strong><p>{summary.coreValue}</p></article><article><strong>승인 요청사항</strong><p>{summary.approvalRequest}</p></article></div><div className="proposal-summary__lists"><List title="대상 고객" values={summary.targetCustomers} /><List title="주요 시장 근거" values={summary.marketEvidence} /><List title="재무 핵심" values={summary.financialHighlights} /><List title="핵심 위험" values={summary.keyRisks} /></div><Evidence refs={summary.evidenceRefs} details={summary.evidenceDetails} /></section>
    {(report.sections ?? []).map((section) => <section className="proposal-section" id={`proposal-section-${section.number}`} key={section.number}><header><span>{String(section.number).padStart(2, '0')}</span><div><p>BUSINESS PLAN</p><h2>{section.title}</h2></div></header><p className="proposal-section__summary">{section.summary}</p>{section.narratives?.map((item) => <article className="proposal-narrative" key={item.heading}><h3>{item.heading}</h3><p>{item.body}</p></article>)}<List title="주요 확인사항" values={section.keyPoints} />{section.tables?.map((table) => <ProposalTable table={table} key={table.title} />)}<Evidence refs={section.evidenceRefs} details={section.evidenceDetails} /></section>)}
    <section id="proposal-appendix" className="proposal-section"><header><span>A</span><div><p>APPENDIX</p><h2>자료·가정·제외 항목</h2></div></header><List title="가정" values={report.appendix?.assumptions} /><List title="포함하지 않은 분석" values={report.appendix?.omittedAnalyses} /><List title="사용 자료 버전" values={report.appendix?.sourceVersions} /><Evidence refs={report.appendix?.evidenceRefs} details={report.appendix?.evidenceDetails} /></section>
    {includeReview && review?.result && <section className="proposal-section proposal-review-print"><header><span>R</span><div><p>AI REVIEW</p><h2>AI 사업기획서 검토 의견</h2></div></header><ReviewGroups result={review.result} /></section>}
  </article>;
}

function List({ title, values }) {
  if (!values?.length) return null;
  return <div className="proposal-list"><h3>{title}</h3><ul>{values.map((value, index) => <li key={`${value}-${index}`}>{value}</li>)}</ul></div>;
}

function ProposalTable({ table }) {
  return <div className="proposal-table"><h3>{table.title}</h3><div><table><thead><tr>{table.columns?.map((column) => <th key={column}>{column}</th>)}</tr></thead><tbody>{table.rows?.map((row, index) => <tr key={index}>{row.map((cell, cellIndex) => <td key={cellIndex}>{cell}</td>)}</tr>)}</tbody></table></div></div>;
}

function ReviewGroups({ result }) {
  return <div className="proposal-review-groups"><ReviewGroup title="잘 갖춰진 부분" values={result.wellPrepared} /><ReviewGroup title="보완 필요" values={result.needsImprovement} /><ReviewGroup title="결재 전 필수 확인" values={result.requiredBeforeApproval} /><ReviewGroup title="후속 조치" values={result.followUpActions} /></div>;
}
function ReviewGroup({ title, values }) { return <section><h3>{title}</h3>{values?.length ? <ul>{values.map((item, index) => <li key={`${item.rubric}-${index}`}><strong>{item.rubric}</strong><p>{item.finding}</p><Evidence refs={item.evidenceRefs} details={item.evidenceDetails} /></li>)}</ul> : <p>해당 의견이 없습니다.</p>}</section>; }

export default function FinalReportPage() {
  const { projectId } = useParams();
  const client = useApiClient();
  const api = useMemo(() => createFinalReportApi(client), [client]);
  const commandKey = useRef(null);
  const [state, setState] = useState({ loading: true, status: null, view: null, review: null, error: null, downloadFormat: null });
  const [selected, setSelected] = useState([]);
  const [includeReview, setIncludeReview] = useState(true);

  const load = useCallback(async () => {
    setState((value) => ({ ...value, loading: true, error: null }));
    try {
      const status = await api.status(projectId);
      let view = null; let review = null;
      if (['CURRENT', 'STALE'].includes(status.state)) {
        [view, review] = await Promise.all([api.current(projectId), api.currentReview(projectId)]);
      }
      setSelected((current) => current.length ? current : OPTIONAL.filter((type) => status.availableSources?.includes(type)));
      setState((current) => ({ ...current, loading: false, status, view, review, error: null }));
    } catch (error) { setState((value) => ({ ...value, loading: false, error })); }
  }, [api, projectId]);
  useEffect(() => { void load(); }, [load]);
  useEffect(() => {
    if (state.status?.state !== 'GENERATING') return undefined;
    const timer = window.setInterval(() => { void load(); }, 1800);
    return () => window.clearInterval(timer);
  }, [load, state.status?.state]);
  useEffect(() => {
    if (!['QUEUED', 'READY', 'CLAIMED', 'RUNNING'].includes(state.review?.status)) return undefined;
    const timer = window.setInterval(() => {
      void api.currentReview(projectId).then((review) => {
        setState((value) => ({ ...value, review }));
      }).catch(() => {});
    }, 1800);
    return () => window.clearInterval(timer);
  }, [api, projectId, state.review?.status]);

  const generate = async () => {
    commandKey.current ??= key();
    try { await api.generate(projectId, commandKey.current, selected); commandKey.current = null; await load(); }
    catch (error) { setState((value) => ({ ...value, error })); }
  };
  const download = async (format) => {
    if (!state.view?.snapshotId) return;
    setState((value) => ({ ...value, downloadFormat: format, error: null }));
    try {
      const response = await api.download(projectId, state.view.snapshotId, format, includeReview, { timeoutMs: 60000 });
      const url = URL.createObjectURL(response.blob);
      const anchor = document.createElement('a');
      anchor.href = url; anchor.download = `business-proposal-${projectId}.${format}`;
      document.body.appendChild(anchor); anchor.click(); anchor.remove(); URL.revokeObjectURL(url);
      setState((value) => ({ ...value, downloadFormat: null }));
    } catch (error) { setState((value) => ({ ...value, downloadFormat: null, error })); }
  };

  if (state.loading && !state.status) return <LoadingState label="사업기획서 상태를 확인하고 있습니다" />;
  if (state.error && !state.status) return <ErrorState title="사업기획서 상태를 불러오지 못했습니다" description={getUserErrorMessage(state.error)} onRetry={() => void load()} />;
  const status = state.status;
  const statusView = STATE_VIEW[status?.state] ?? STATE_VIEW.NOT_READY;
  const report = state.view?.report?.contract === 'final-business-proposal-result-v1'
    ? state.view.report : null;
  const failedGeneration = generationFailure(status);
  return <ProjectWorkspace mode="document" className="final-report-page">
    <ProjectStageHeader step={6} eyebrow="최종 사업기획서" title="결재·공유 가능한 사업기획서를 만드세요" description="사용자가 선택한 현재 분석 snapshot만 고정해 회사용 사업기획서로 구성합니다." status={<span className="pipeline-status" data-tone={statusView[1]}>{statusView[0]}</span>} actions={<Button type="button" variant="outline" onClick={() => void load()}>새로고침</Button>} />
    {state.error && <p className="final-report-error" role="alert">{getUserErrorMessage(state.error)}</p>}
    {failedGeneration && <p className="final-report-error" role="alert"><strong>사업기획서 생성에 실패했습니다.</strong><br />{failedGeneration}</p>}
    {!report && <section className="proposal-ready"><header><p>BUSINESS PROPOSAL</p><h2>사업기획서 작성</h2><span>현재 프로젝트의 분석 결과를 조합해 결재·공유 가능한 사업기획서를 만듭니다.</span></header><div className="proposal-source-grid"><section><h3>필수 기반</h3><SourceOption label="현재 확정 사업안" ready={status?.availableSources?.includes('CURRENT_CONCEPT')} required /><SourceOption label="사업성 검증" ready={status?.availableSources?.includes('MARKET') && status?.availableSources?.includes('BUSINESS_MODEL')} required /></section><section><h3>선택 포함</h3>{OPTIONAL.map((type) => { const sourceState = status?.sourceStates?.[type] ?? (status?.availableSources?.includes(type) ? 'AVAILABLE' : 'NOT_RUN'); return <SourceOption key={type} label={SOURCE_LABELS[type]} sourceState={sourceState} ready={sourceState.startsWith('AVAILABLE')} checked={selected.includes(type)} onChange={(checked) => setSelected((items) => checked ? [...new Set([...items, type])] : items.filter((item) => item !== type))} />; })}</section></div>{status?.blockingSources?.length > 0 && <p className="proposal-blocking">필수 기반 자료를 먼저 준비해 주세요.</p>}<Button type="button" loading={status?.state === 'GENERATING'} disabled={status?.state === 'NOT_READY' || status?.state === 'GENERATING'} onClick={() => void generate()}>사업기획서 만들기</Button></section>}
    {report && <div className="proposal-workspace">
      <header className="proposal-control-strip"><div><strong>버전 {state.view.version}</strong><span>{state.view.generatedAt ? new Date(state.view.generatedAt).toLocaleString('ko-KR') : ''}</span><span>작성자 {state.view.generatedByName ?? '—'}</span><span>포함 자료 {displayManifestSources(state.view.sourceManifest?.sources).length}개</span></div><div><Button type="button" onClick={() => void generate()}>{status.state === 'STALE' ? '최신 자료로 다시 생성' : '새 버전 생성'}</Button><Button type="button" variant="outline" loading={state.downloadFormat === 'pdf'} onClick={() => void download('pdf')}>PDF 저장</Button><Button type="button" variant="outline" loading={state.downloadFormat === 'docx'} onClick={() => void download('docx')}>DOCX 다운로드</Button></div><details><summary>문서 정보·포함 자료 상세</summary><ul>{displayManifestSources(state.view.sourceManifest?.sources).map((source) => <li key={`${source.type}-${source.id}`}>{SOURCE_LABELS[source.type] ?? source.type}</li>)}</ul></details><label><input type="checkbox" checked={includeReview} onChange={(event) => setIncludeReview(event.target.checked)} /> AI 검토 부록 포함</label>{['QUEUED', 'READY', 'CLAIMED', 'RUNNING'].includes(state.review?.status) && <p aria-live="polite">AI 검토 의견을 정리하고 있습니다.</p>}{state.review?.status === 'FAILED' && <p className="final-report-error">AI 검토를 완료하지 못했지만 사업기획서는 정상적으로 사용할 수 있습니다.</p>}</header>
      <main><ProposalDocument view={state.view} review={state.review} includeReview={includeReview} /></main>
    </div>}
  </ProjectWorkspace>;
}

function SourceOption({ label, ready, sourceState, required = false, checked = false, onChange }) {
  return <label className="proposal-source" data-ready={ready}><input type="checkbox" checked={required ? ready : checked} disabled={required || !ready} onChange={(event) => onChange?.(event.target.checked)} /><span><strong>{label}</strong><small>{required ? (ready ? '필수 포함' : '준비 필요') : SOURCE_STATE_LABELS[sourceState] ?? '현재 결과 없음'}</small></span></label>;
}
