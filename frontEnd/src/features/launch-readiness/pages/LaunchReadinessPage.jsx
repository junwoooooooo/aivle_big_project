import { useEffect, useMemo, useRef, useState } from 'react';
import { useParams } from 'react-router-dom';
import { useApiClient } from '../../../shared/api/ApiClientProvider.jsx';
import { getUserErrorMessage } from '../../../shared/api/apiError.js';
import useFinance from '../finance/hooks/useFinance.js';
import { createProfessionalReadinessApi } from '../api/professionalReadinessApi.js';
import '../finance/styles/finance.css';
import '../styles/launch-readiness.css';

const download = (blob, filename) => { const url = URL.createObjectURL(blob); const a = document.createElement('a'); a.href = url; a.download = filename; a.click(); URL.revokeObjectURL(url); };
const info = { technology: ['기술 분석', '기술 구조, 보안, 성능 및 출시 계획을 분석합니다.'], operations: ['운영 분석', '운영 프로세스, 고객 지원 및 확장 계획을 분석합니다.'] };

function ProfessionalConclusion({ module, result }) {
  const analysis = result?.analysis ?? {}; const actions = Array.isArray(analysis.actions) ? analysis.actions.slice(0, 3) : [];
  const decision = analysis.decision === 'READY' ? '출시 준비' : analysis.decision === 'CONDITIONAL' ? '조건부 준비' : '보완 후 재검토';
  return <div className="launch-readiness-conclusion"><p>분석 결론</p><h3>{info[module][0]} 관점의 사업 실행 판단: {decision}</h3><strong>{analysis.summary}</strong><span>입력된 {module === 'technology' ? '기술 구조와 검증 계획' : '운영 체계와 실행 계획'}을 계획대로 적용하면 사업의 실행 안정성과 확장 가능성을 높일 수 있습니다. 다만 아래 우선 과제를 먼저 완료한 뒤 다음 단계로 진행해야 합니다.</span>{actions.length > 0 && <ul>{actions.map((action, index) => <li key={`${action.title}-${index}`}><b>{action.title}</b>{action.completionEvidence ? ` — ${action.completionEvidence}` : ''}</li>)}</ul>}</div>;
}

function FinancialConclusion({ analysis }) {
  const base = analysis?.calculation?.scenarios?.find((scenario) => scenario.code === 'BASE') ?? analysis?.calculation?.scenarios?.[0];
  if (!base) return null;
  const money = (value) => `${Number(value ?? 0).toLocaleString('ko-KR')} KRW`;
  const lossMaking = Number(base.totalOperatingProfit) < 0;
  const headline = lossMaking ? '현재 가정에서는 손실 구조를 먼저 개선해야 합니다.' : '현재 가정에서는 사업 지속 가능성을 확인할 수 있습니다.';
  const explanation = lossMaking
    ? `예상 총매출은 ${money(base.totalRevenue)}이지만 총 영업이익은 ${money(base.totalOperatingProfit)}으로, 분석 기간에 손실이 지속될 가능성이 있습니다. 가격·판매량·변동비를 실제 데이터로 다시 검증한 후 확장 여부를 판단해야 합니다.`
    : `예상 총매출 ${money(base.totalRevenue)}, 총 영업이익 ${money(base.totalOperatingProfit)}으로 수익성이 예상됩니다. 다만 보수적 시나리오 기준의 현금 계획도 함께 관리해야 합니다.`;
  const actions = lossMaking ? ['가격·판매량·변동비 가정을 실제 데이터로 검증하기', '보수적 시나리오 기준으로 현금 계획 수립하기'] : ['실제 판매량과 비용을 정기적으로 비교·보정하기', '보수적 시나리오에서도 필요한 운전자금 확보하기'];
  return <div className="launch-readiness-conclusion"><p>분석 결론</p><h3>재무 가정에 따른 사업 전망</h3><strong>{headline}</strong><span>{explanation}</span><ul>{actions.map((action, index) => <li key={`${action}-${index}`}>{action}</li>)}</ul><small>이 결론은 입력한 가정에 따른 분석 결과이며, 실제 성과를 보장하지 않습니다.</small></div>;
}

function Module({ module, api, projectId, onReady, onPreview }) {
  const [result, setResult] = useState(null); const [busy, setBusy] = useState(false); const [error, setError] = useState(null); const [progress, setProgress] = useState(0); const [fileName, setFileName] = useState('선택된 파일 없음'); const input = useRef(null);
  useEffect(() => {
    let active = true;
    api.current(projectId, module).then((value) => {
      if (!active) return;
      setResult(value);
      onReady(value);
    }).catch(() => { if (active) setResult(null); });
    return () => { active = false; };
  }, [api, module, projectId]);
  useEffect(() => {
    if (!busy) { setProgress(0); return undefined; }
    setProgress(8);
    const timer = setInterval(() => setProgress((value) => Math.min(92, value + (value < 45 ? 9 : 4))), 900);
    return () => clearInterval(timer);
  }, [busy]);
  const template = async () => { try { download(await api.template(projectId, module), `${module}-analysis-input-template.docx`); } catch (e) { setError(e); } };
  const analyze = async (event) => { const file = event.target.files?.[0]; if (!file) return; setFileName(file.name); setBusy(true); setError(null); try { const value = await api.analyze(projectId, module, file); setProgress(100); setResult(value); onReady(value); } catch (e) { setError(e); } finally { event.target.value = ''; setBusy(false); } };
  const preview = () => onPreview(`${info[module][0]} 보고서 미리보기`, () => api.report(projectId, module));
  return <section className="finance-section launch-readiness-section"><div className="launch-readiness-section__heading"><div><p>{info[module][0]}</p><h2>{info[module][0]} 전문 입력 분석</h2><span>{info[module][1]}</span></div></div><div className="finance-minimal-actions"><button className="finance-save" type="button" onClick={() => void template()}>입력 템플릿 다운로드</button><input className="launch-readiness-file-input" ref={input} type="file" accept=".docx" onChange={(e) => void analyze(e)} disabled={busy} /><button className="finance-save" type="button" disabled={busy} onClick={() => input.current?.click()}>{busy ? '분석 중…' : '파일 선택 및 분석'}</button><span className="launch-readiness-file-name">{fileName}</span><button className="finance-save launch-readiness-preview" type="button" disabled={!result || busy} onClick={() => void preview()}>미리보기</button></div>{busy && <div className="launch-readiness-progress"><div><span>분석 진행 중</span><strong>{progress}%</strong></div><i><em style={{ width: `${progress}%` }} /></i><small>입력 항목을 확인하고 평가 근거와 보고서를 생성하고 있습니다. 분석이 끝날 때까지 잠시 기다려 주세요.</small></div>}{error && <p className="finance-error">{getUserErrorMessage(error)}</p>}{result && <ProfessionalConclusion module={module} result={result} />}</section>;
}

function PdfPreview({ preview, onClose }) {
  if (!preview) return null;
  return <div className="launch-readiness-modal" role="dialog" aria-modal="true" aria-label={preview.title}><div className="launch-readiness-modal__panel"><div className="launch-readiness-modal__heading"><h2>{preview.title}</h2><button type="button" onClick={onClose}>닫기</button></div>{preview.loading ? <p>PDF를 불러오는 중입니다…</p> : preview.url ? <iframe title={preview.title} src={preview.url} /> : <p className="finance-error">미리보기를 불러오지 못했습니다.</p>}</div></div>;
}

export default function LaunchReadinessPage() {
  const { projectId } = useParams(); const client = useApiClient(); const api = useMemo(() => createProfessionalReadinessApi(client), [client]); const finance = useFinance(projectId);
  const [technologyDone, setTechnologyDone] = useState(false); const [operationsDone, setOperationsDone] = useState(false); const [financeDone, setFinanceDone] = useState(false); const [financeFileName, setFinanceFileName] = useState('선택된 파일 없음'); const [selected, setSelected] = useState([]); const [downloadOpen, setDownloadOpen] = useState(false); const [error, setError] = useState(null); const [preview, setPreview] = useState(null); const financeInput = useRef(null);
  useEffect(() => () => { if (preview?.url) URL.revokeObjectURL(preview.url); }, [preview]);
  useEffect(() => { if (finance.analysis?.result) setFinanceDone(true); }, [finance.analysis]);
  const available = [{ id: 'technology', label: '기술 분석 보고서', ready: technologyDone }, { id: 'operations', label: '운영 분석 보고서', ready: operationsDone }, { id: 'finance', label: '재무 분석 보고서', ready: financeDone }].filter((item) => item.ready);
  const toggle = (id) => setSelected((values) => values.includes(id) ? values.filter((value) => value !== id) : [...values, id]);
  const openPreview = async (title, load) => { setPreview({ title, loading: true, url: null }); try { const blob = await load(); const url = URL.createObjectURL(blob); setPreview({ title, loading: false, url }); } catch (e) { setError(e); setPreview({ title, loading: false, url: null }); } };
  const closePreview = () => setPreview(null);
  const analyzeFinance = async (event) => { const file = event.target.files?.[0]; if (!file) return; setFinanceFileName(file.name); try { await finance.importAndAnalyze(file); setFinanceDone(true); } catch (e) { setError(e); } finally { event.target.value = ''; } };
  const downloadReports = async () => { try { const query = selected.map((id) => `modules=${encodeURIComponent(id)}`).join('&'); const blob = await client.get(`/api/v3/projects/${projectId}/reports/download?${query}`, { responseType: 'blob', timeoutMs: 60000 }); download(blob, selected.length > 1 ? 'integrated-analysis-report.pdf' : `${selected[0]}-analysis-report.pdf`); } catch (e) { setError(e); } };
  return <main className="finance-page finance-page--minimal launch-readiness-page"><header className="finance-heading"><div><p>5. 출시 준비</p><h1>출시 준비 분석</h1><span>기술·운영·재무 분석을 완료한 뒤, 맨 아래에서 필요한 보고서를 선택해 다운로드하세요.</span></div></header>{error && <p className="finance-error">{getUserErrorMessage(error)}</p>}
    <Module module="technology" api={api} projectId={projectId} onReady={() => setTechnologyDone(true)} onPreview={openPreview} /><Module module="operations" api={api} projectId={projectId} onReady={() => setOperationsDone(true)} onPreview={openPreview} />
    <section className="finance-section launch-readiness-section"><div className="launch-readiness-section__heading"><div><p>재무 분석</p><h2>재무 입력 기반 손익·현금흐름 분석</h2><span>재무 입력 문서를 업로드해 분석합니다.</span></div></div><div className="finance-minimal-actions"><button className="finance-save" type="button" onClick={() => void finance.downloadTemplate()}>재무 템플릿 다운로드</button><input className="launch-readiness-file-input" ref={financeInput} type="file" accept=".docx" onChange={(e) => void analyzeFinance(e)} disabled={finance.busy === 'import-analyze'} /><button className="finance-save" type="button" disabled={finance.busy === 'import-analyze'} onClick={() => financeInput.current?.click()}>{finance.busy === 'import-analyze' ? '분석 요청 중…' : '파일 선택 및 분석'}</button><span className="launch-readiness-file-name">{financeFileName}</span><button className="finance-save launch-readiness-preview" type="button" disabled={!financeDone || finance.busy === 'import-analyze'} onClick={() => void openPreview('재무 분석 보고서 미리보기', () => finance.downloadFinancialAnalysisDocument())}>미리보기</button></div>{['QUEUED', 'RUNNING'].includes(finance.analysis?.status) && <p className="launch-readiness-progress">재무 분석 보고서를 생성하고 있습니다. 완료되면 미리보기와 다운로드가 활성화됩니다.</p>}<FinancialConclusion analysis={finance.analysis?.result} /></section>
    <section className="finance-section launch-readiness-section"><div className="launch-readiness-section__heading"><div><p>보고서 다운로드</p><h2>완료된 분석 보고서 선택</h2><span>한 개는 해당 PDF, 두 개 이상은 선택한 보고서를 합친 통합 PDF로 내려받습니다.</span></div></div><div className="report-download"><button className="finance-save" type="button" onClick={() => setDownloadOpen((value) => !value)}>다운로드</button>{downloadOpen && <div className="report-download__menu">{available.length ? available.map((item) => <button key={item.id} className={`report-download__option ${selected.includes(item.id) ? 'is-selected' : ''}`} type="button" onClick={() => toggle(item.id)}><span>{selected.includes(item.id) ? '✓ 선택됨' : '선택'}</span>{item.label}</button>) : <span>완료된 분석 보고서가 없습니다.</span>}<button className="finance-save" type="button" disabled={!selected.length} onClick={() => void downloadReports()}>{selected.length ? `${selected.length}개 보고서 다운로드` : '보고서를 선택하세요'}</button></div>}</div></section><PdfPreview preview={preview} onClose={closePreview} />
  </main>;
}
