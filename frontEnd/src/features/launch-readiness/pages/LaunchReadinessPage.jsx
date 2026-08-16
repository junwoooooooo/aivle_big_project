import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate, useOutletContext, useParams } from 'react-router-dom';
import { projectRoutes } from '../../../app/routing/projectRoutes.js';
import { useApiClient } from '../../../shared/api/ApiClientProvider.jsx';
import { getUserErrorMessage } from '../../../shared/api/apiError.js';
import { useJobEvents } from '../../../shared/async-events/index.js';
import { AppIcon, ProjectStageHeader, ProjectWorkspace } from '../../../shared/ui/index.js';
import { createLaunchReadinessApi } from '../api/launchReadinessApi.js';
import { canonicalizeReportModules, formatKrwAmount } from '../model/reportDocumentPresentation.js';
import '../styles/launch-readiness.css';

const MODULES = {
  technology: {
    eyebrow: '기술 분석',
    title: '기술 출시 준비도를 확인하세요',
    description: '앞 단계의 분석 결과를 자동으로 가져오지 않습니다. 템플릿에 실제 기술 계획을 작성해 업로드하면 제출한 문서를 1차 기준으로 독립 분석합니다.',
  },
  operations: {
    eyebrow: '운영 분석',
    title: '운영 실행 준비도를 확인하세요',
    description: '앞 단계 결과 없이 사용할 수 있습니다. 실제 운영 계획을 템플릿에 작성해 업로드하면 해당 문서를 기준으로 운영 준비도를 분석합니다.',
  },
};
const REPORTS = [
  { id: 'technology', label: '기술 분석 보고서' },
  { id: 'operations', label: '운영 분석 보고서' },
  { id: 'finance', label: '재무 분석 보고서' },
];
const ACTIVE = new Set(['QUEUED', 'READY', 'RUNNING']);

function formatKrwInline(value) {
  const { raw, readable } = formatKrwAmount(value);
  return `${raw} · ${readable}`;
}

function downloadDocumentBlob(blob, filename) {
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = filename;
  anchor.hidden = true;
  document.body.append(anchor);
  try { anchor.click(); } finally {
    anchor.remove();
    setTimeout(() => URL.revokeObjectURL(url), 30_000);
  }
}

const FINANCE_FIELD_LABELS = {
  annualFixedLaborCost: '연간 고정 인건비',
  annualFixedRentAndManagementCost: '연간 임차료 및 관리비',
  annualFixedInfrastructureCost: '연간 인프라 운영비',
  initialDevelopmentAndRnDCost: '초기 개발 및 R&D 비용',
  initialEquipmentAndInfrastructureCost: '초기 장비 및 인프라 비용',
  initialPatentAndLicensingCost: '초기 특허 및 라이선스 비용',
  totalMarketingCost: '연간 마케팅비', totalSalesCost: '연간 영업비',
  newCustomerCount: '연간 신규 고객 수', threeYearTargets: '3개년 성장 목표',
  revenueModel: '매출 모델', unitPrice: '건당 판매 가격',
  monthlySubscriptionPrice: '월 구독 가격', monthlyChurnRate: '월 이탈률',
  unitVariableCost: '건당 변동비', paymentFee: '결제 수수료',
  partnerPayout: '파트너 지급액', shippingCost: '배송비',
  customerIncrementalInfraCost: '고객 증가 인프라비', file: '재무 입력 문서',
  document: '문서 입력 항목',
};

export function FinanceInputError({ error }) {
  const fields = error?.fieldErrors ?? [];
  return <div className="launch-error launch-error--fields" role="alert">
    <strong>{getUserErrorMessage(error)}</strong>
    {fields.length > 0 && <ul>{fields.map((field, index) => <li key={`${field.field}-${index}`}>
      <b>{FINANCE_FIELD_LABELS[field.field] ?? field.field}</b><span>{field.message}</span>
    </li>)}</ul>}
  </div>;
}

function ExecutionStatus({ jobId, events, onDetail }) {
  const latest = events.events?.at(-1);
  const activity = ({
    DOCUMENT_ACCEPTED: '입력 문서를 확인했습니다.',
    QUEUED: '분석 작업을 준비하고 있습니다.',
    ANALYZING: '외부 참고자료와 입력 내용을 바탕으로 출시 준비도를 분석하고 있습니다.',
    COMPLETED: '분석 결과를 정리했습니다.',
    FAILED: '분석을 완료하지 못했습니다.',
  })[latest?.stage] ?? '분석 작업을 준비하고 있습니다.';
  return <div className="launch-execution" role="status">
    <div className="launch-execution__rail" aria-label="분석 진행 단계">
      {['입력 확인', '전문 분석', '결과 검토', '결과 정리'].map((label, index) => <span key={label}
        className={latest?.stage === 'COMPLETED' || index === 0 || (latest?.stage === 'ANALYZING' && index <= 1) ? 'is-reached' : ''}>{label}</span>)}
    </div>
    <p><AppIcon name="sparkles" size={16} />{activity}</p>
    {jobId && <button className="launch-link-button" type="button" onClick={() => onDetail?.(jobId)}>작업센터에서 상세 기록 보기</button>}
  </div>;
}

function ResultSummary({ module, current }) {
  const result = current?.analysis;
  if (!result) return null;
  const decision = ({ READY: '출시 준비', CONDITIONAL: '조건부 준비', REVISE: '보완 후 재검토' })[result.decision] ?? '검토 필요';
  return <div className="launch-result">
    <div>
      <span>분석 결론</span><strong>{decision}</strong>
      <small>AI 출시 준비도 평가 {result.score}점</small>
      {current.quality?.passed === true && <small className="launch-quality-pass"><AppIcon name="check" size={13} />독립 AI 검증 통과</small>}
    </div>
    <div className="launch-result__summary"><p>{result.summary}</p><small>작성한 계획을 바탕으로 AI가 평가한 결과이며, 정해진 재무 산식처럼 계산된 점수는 아닙니다.</small></div>
    <ul>{(result.actions ?? []).slice(0, 3).map((action) => <li key={`${action.priority}-${action.title}`}><b>{action.priority}</b><span>{action.title}</span></li>)}</ul>
    {current.stale && <p className="launch-warning">새 입력 문서가 있어 이 결과는 이전 입력 기준입니다.</p>}
    <small>{module === 'technology' ? '기술' : '운영'} 입력 문서를 기준으로 만든 결과입니다.</small>
  </div>;
}

export function ProfessionalModule({ module, api, projectId, onReady, onDetail, onViewReport }) {
  const meta = MODULES[module];
  const input = useRef(null);
  const [state, setState] = useState({ current: null, busy: false, error: null });
  const activeJobId = ACTIVE.has(state.current?.status) ? state.current?.taskRunId : null;
  const job = useJobEvents(activeJobId);

  const refresh = useCallback(async () => {
    try {
      const current = await api.professionalCurrent(projectId, module);
      setState((value) => ({ ...value, current, busy: false, error: null }));
      onReady?.(module, current);
    } catch (error) {
      setState((value) => ({ ...value, busy: false, error }));
    }
  }, [api, module, onReady, projectId]);
  useEffect(() => { void refresh(); }, [refresh]);
  useEffect(() => { if (job.terminal) void refresh(); }, [job.terminal, refresh]);

  const start = async (event) => {
    const file = event.target.files?.[0];
    if (!file) return;
    setState((value) => ({ ...value, busy: true, error: null }));
    try {
      const action = await api.startProfessional(projectId, module, file);
      setState((value) => ({ ...value, busy: false, current: {
        ...value.current, ...action, status: action.status, sourceDocumentName: file.name,
      }, error: null }));
    } catch (error) {
      setState((value) => ({ ...value, busy: false, error }));
    } finally { event.target.value = ''; }
  };

  return <section id={`launch-${module}`} className="launch-module" aria-labelledby={`launch-${module}-title`}>
    <div className="launch-module__heading"><div><p>{meta.eyebrow}</p><h2 id={`launch-${module}-title`}>{meta.title}</h2><span>{meta.description}</span></div><div className="launch-module__badges"><span className="launch-independence-badge">독립 사용 가능</span>{state.current?.analysis && !state.current.stale && <span className="launch-status is-complete"><AppIcon name="check" size={14} />완료</span>}</div></div>
    <ol className="launch-workflow launch-workflow--vertical"><li><b>1</b><span>템플릿 받기<small>작성 항목을 확인합니다.</small></span></li><li><b>2</b><span>실제 계획 작성·업로드<small>현재 계획을 직접 작성합니다.</small></span></li><li><b>3</b><span>독립 분석 결과 확인<small>제출한 문서를 1차 근거로 평가합니다.</small></span></li></ol>
    <div className="launch-actions">
      <button type="button" className="launch-button is-secondary" onClick={async () => downloadDocumentBlob(await api.professionalTemplate(projectId, module), `${module}-readiness-input.docx`)}><AppIcon name="download" size={16} />입력 템플릿 다운로드</button>
      <input ref={input} type="file" accept=".docx" onChange={start} disabled={state.busy} />
      <button type="button" className="launch-button is-primary" disabled={state.busy} onClick={() => input.current?.click()}>{state.busy ? '문서를 확인하고 있습니다…' : '작성한 DOCX로 분석 시작'}</button>
      {state.current?.analysis && !state.current.stale && <button type="button" className="launch-button is-tertiary" onClick={() => onViewReport([module])}>보고서 보기</button>}
    </div>
    {state.current?.sourceDocumentName && <p className="launch-document"><AppIcon name="file" size={15} />{state.current.sourceDocumentName}</p>}
    {ACTIVE.has(state.current?.status) && <ExecutionStatus jobId={state.current?.taskRunId} events={job} onDetail={onDetail} />}
    {state.error && <p className="launch-error" role="alert">{getUserErrorMessage(state.error)}</p>}
    <ResultSummary module={module} current={state.current} />
  </section>;
}

export function FinanceModule({ api, projectId, onReady, onDetail, onViewReport }) {
  const input = useRef(null);
  const [state, setState] = useState({ current: null, busy: false, error: null, filename: null });
  const activeJobId = ACTIVE.has(state.current?.status) ? state.current?.taskRunId : null;
  const job = useJobEvents(activeJobId);

  const refresh = useCallback(async () => {
    try {
      const current = await api.financeCurrent(projectId);
      setState((value) => ({ ...value, current, busy: false, error: null }));
      onReady?.('finance', current);
    } catch (error) {
      if (![404, 409, 422].includes(error?.status)) setState((value) => ({ ...value, busy: false, error }));
      else setState((value) => ({ ...value, busy: false }));
    }
  }, [api, onReady, projectId]);
  useEffect(() => { void refresh(); }, [refresh]);
  useEffect(() => { if (job.terminal) void refresh(); }, [job.terminal, refresh]);

  const start = async (event) => {
    const file = event.target.files?.[0];
    if (!file) return;
    setState((value) => ({ ...value, busy: true, error: null, filename: file.name }));
    try {
      const response = await api.startFinance(projectId, file);
      setState((value) => ({ ...value, busy: false, current: response.analysis, error: null }));
    } catch (error) {
      setState((value) => ({ ...value, busy: false, error }));
    } finally { event.target.value = ''; }
  };

  const base = state.current?.result?.calculation?.scenarios?.find((item) => item.code === 'BASE')
    ?? state.current?.result?.calculation?.scenarios?.[0];

  return <section id="launch-finance" className="launch-module" aria-labelledby="launch-finance-title">
    <div className="launch-module__heading"><div><p>재무 분석</p><h2 id="launch-finance-title">사용자 재무 문서로 사업 지속 가능성을 확인하세요</h2><span>시장 분석이나 사업 모델 결과 없이도 사용할 수 있습니다. 업로드한 재무 값만 계산 기준으로 사용합니다.</span></div><div className="launch-module__badges"><span className="launch-independence-badge">독립 사용 가능</span>{state.current?.result && !state.current.stale && <span className="launch-status is-complete"><AppIcon name="check" size={14} />완료</span>}</div></div>
    <ol className="launch-workflow launch-workflow--vertical"><li><b>1</b><span>재무 템플릿 받기<small>작성할 재무 항목을 확인합니다.</small></span></li><li><b>2</b><span>재무 값과 산정 근거 작성·업로드<small>실제 계획의 값과 근거를 직접 작성합니다.</small></span></li><li><b>3</b><span>손익·현금흐름 독립 분석<small>업로드한 재무 값만 계산 기준으로 사용합니다.</small></span></li></ol>
    <div className="launch-actions">
      <button type="button" className="launch-button is-secondary" onClick={async () => downloadDocumentBlob(await api.financeTemplate(projectId), 'finance-readiness-input.docx')}><AppIcon name="download" size={16} />재무 템플릿 다운로드</button>
      <input ref={input} type="file" accept=".docx" onChange={start} disabled={state.busy} />
      <button type="button" className="launch-button is-primary" disabled={state.busy} onClick={() => input.current?.click()}>{state.busy ? '문서를 검증하고 있습니다…' : '작성한 DOCX로 재무 분석 시작'}</button>
      {state.current?.result && !state.current.stale && <button type="button" className="launch-button is-tertiary" onClick={() => onViewReport(['finance'])}>보고서 보기</button>}
    </div>
    {state.filename && <p className="launch-document"><AppIcon name="file" size={15} />{state.filename}</p>}
    {ACTIVE.has(state.current?.status) && <ExecutionStatus jobId={state.current?.taskRunId} events={job} onDetail={onDetail} />}
    {state.error && <FinanceInputError error={state.error} />}
    {state.current?.result && <div className="launch-result"><div><span>재무 분석 결론</span><strong>{base && Number(base.totalOperatingProfit) >= 0 ? '사업 지속 가능성 확인' : '손실 구조 개선 필요'}</strong><small>업로드한 재무 문서를 기준으로 계산한 최신 결과입니다.</small></div><p>{state.current.result.report?.headline}</p><ul><li><b>매출</b><span>{formatKrwInline(base?.totalRevenue)}</span></li><li><b>영업이익</b><span>{formatKrwInline(base?.totalOperatingProfit)}</span></li><li><b>운전자금</b><span>{formatKrwInline(base?.requiredWorkingCapital)}</span></li></ul>{state.current.stale && <p className="launch-warning">이전 입력 기준 결과입니다.</p>}</div>}
  </section>;
}

function reportReady(module, current) {
  if (current?.stale) return false;
  return module === 'finance' ? Boolean(current?.result) : Boolean(current?.analysis);
}

export function ReportDownload({ reports, onViewReport }) {
  const [selected, setSelected] = useState([]);
  const available = REPORTS.filter((item) => reportReady(item.id, reports[item.id]));
  const toggle = (id) => setSelected((value) => value.includes(id)
    ? value.filter((item) => item !== id) : [...value, id]);
  return <section id="launch-reports" className="launch-module launch-reports">
    <div className="launch-module__heading"><div><p>보고서 확인</p><h2>완료된 분석을 선택해 개별 또는 통합 보고서로 확인하세요</h2><span>화면에서 확인한 동일한 문서를 브라우저 인쇄 기능으로 PDF에 저장할 수 있습니다.</span></div></div>
    <div className="launch-report-picker">{available.length ? available.map((item) => <label key={item.id} className={selected.includes(item.id) ? 'is-selected' : ''}><input type="checkbox" checked={selected.includes(item.id)} onChange={() => toggle(item.id)} /><AppIcon name={selected.includes(item.id) ? 'check' : 'file'} size={16} /><span>{item.label}</span></label>) : <p>완료된 분석 보고서가 없습니다.</p>}</div>
    <button className="launch-button is-primary" type="button" disabled={!selected.length} onClick={() => onViewReport(selected)}>{selected.length > 1 ? `${selected.length}개 통합 보고서 보기` : '선택한 보고서 보기'}</button>
  </section>;
}

export default function LaunchReadinessPage({ initialFocus }) {
  const { projectId } = useParams();
  const navigate = useNavigate();
  const outlet = useOutletContext();
  const client = useApiClient();
  const api = useMemo(() => createLaunchReadinessApi(client), [client]);
  const [reports, setReports] = useState({});
  const onReady = useCallback((module, current) => setReports((value) => value[module] === current
    ? value : { ...value, [module]: current }), []);
  useEffect(() => {
    if (!initialFocus) return;
    requestAnimationFrame(() => document.getElementById(`launch-${initialFocus}`)?.scrollIntoView({ block: 'start' }));
  }, [initialFocus]);
  const viewReport = useCallback((modules) => {
    const orderedModules = canonicalizeReportModules(modules);
    const reportType = orderedModules.length > 1 ? 'integrated' : orderedModules[0];
    navigate(projectRoutes.launchReadinessReport(projectId, reportType, orderedModules));
  }, [navigate, projectId]);

  return <ProjectWorkspace as="div" mode="data" className="launch-readiness-page">
    <ProjectStageHeader step={5} eyebrow="출시 준비" title="출시 전에 필요한 준비 상태를 분야별로 확인하세요" description="기술·운영·재무 분석은 서로 독립적으로 사용할 수 있습니다. 필요한 분석만 선택해 템플릿을 내려받고 실제 계획을 작성해 업로드하면, 앞 단계의 분석 결과를 자동으로 가져오지 않고 사용자가 제출한 문서를 기준으로 분석합니다." status="선택형 · 독립 문서 분석" />
    <p className="launch-independence-note">기술·운영은 공개 참고자료를 보조적으로 확인할 수 있으며, 재무는 업로드한 재무 입력값만 계산 기준으로 사용합니다.</p>
    <div className="launch-analysis-grid">
      <ProfessionalModule module="technology" api={api} projectId={projectId} onReady={onReady} onDetail={outlet?.openWorkCenterJob} onViewReport={viewReport} />
      <ProfessionalModule module="operations" api={api} projectId={projectId} onReady={onReady} onDetail={outlet?.openWorkCenterJob} onViewReport={viewReport} />
      <FinanceModule api={api} projectId={projectId} onReady={onReady} onDetail={outlet?.openWorkCenterJob} onViewReport={viewReport} />
    </div>
    <ReportDownload reports={reports} onViewReport={viewReport} />
  </ProjectWorkspace>;
}
