import { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import { useApiClient } from '../../../shared/api/ApiClientProvider.jsx';
import { getUserErrorMessage } from '../../../shared/api/apiError.js';
import { createTechOpsApi } from '../api/techOpsApi.js';
import CommercializationAdvisory from '../components/CommercializationAdvisory.jsx';
import '../styles/tech-ops.css';

const storageKey = (projectId) => `aivle:tech-ops:commercialization-advisory:${projectId}`;

const progressSteps = [
  { progress: 8, title: '시장·BM 결과 연결', detail: '최신 시장분석과 BM 분석 결과를 불러옵니다.' },
  { progress: 24, title: '상용화 사실 정리', detail: '시장·BM 사실을 기술·운영 검증용 근거로 구조화합니다.' },
  { progress: 42, title: '외부 근거 수집', detail: 'KOSIS·웹 출처를 확인하고 검토 가능한 링크만 선별합니다.' },
  { progress: 62, title: '상용화 조언 생성', detail: '제품·기술·운영·파트너·파일럿 관점의 조언을 생성합니다.' },
  { progress: 80, title: '출시 게이트·운영비 계측 설계', detail: '운영 리스크, 출시 조건, 파일럿 계측 항목을 구체화합니다.' },
  { progress: 94, title: '근거·품질 검증 및 저장', detail: '근거 ID와 구체성 기준을 점검한 뒤 보고서를 저장합니다.' },
];

function CommercializationProgress({ elapsedSeconds = 0 }) {
  const progress = Math.min(94, 8 + Math.floor(elapsedSeconds * 2.8));
  const current = progressSteps.findLast((step) => progress >= step.progress) ?? progressSteps[0];
  return <section className="commercialization-progress" aria-live="polite" aria-label="기술 운영 분석 진행 상황">
    <div className="commercialization-progress__headline">
      <div><p>기술·운영 에이전트 분석 진행 중</p><h2>{current.title}</h2><span>{current.detail}</span></div>
      <strong>{progress}%</strong>
    </div>
    <div className="commercialization-progress__track" role="progressbar" aria-valuemin="0" aria-valuemax="100" aria-valuenow={progress}><i style={{ width: `${progress}%` }} /></div>
    <ol>{progressSteps.map((step) => <li key={step.title} className={progress >= step.progress ? 'is-complete' : ''}><b>{progress > step.progress ? '✓' : progress === step.progress ? '•' : '○'}</b><span>{step.title}</span></li>)}</ol>
    <small>외부 출처와 AI 응답 시간에 따라 실제 소요 시간은 달라질 수 있습니다.</small>
  </section>;
}

function loadSavedReport(projectId) {
  try {
    const raw = window.localStorage.getItem(storageKey(projectId));
    const parsed = raw ? JSON.parse(raw) : null;
    return parsed?.result ? parsed : null;
  } catch {
    return null;
  }
}

export default function TechOpsPage() {
  const { projectId } = useParams();
  const client = useApiClient();
  const [report, setReport] = useState(() => loadSavedReport(projectId));
  const [state, setState] = useState({ running: false, error: null });
  const [downloading, setDownloading] = useState(false);
  const [elapsedSeconds, setElapsedSeconds] = useState(0);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      setReport(loadSavedReport(projectId));
      setState({ running: false, error: null });
      setElapsedSeconds(0);
    }, 0);
    return () => window.clearTimeout(timer);
  }, [projectId]);

  useEffect(() => {
    if (!state.running) return undefined;
    const startedAt = Date.now();
    const timer = window.setInterval(() => setElapsedSeconds(Math.floor((Date.now() - startedAt) / 1000)), 500);
    return () => window.clearInterval(timer);
  }, [state.running]);

  const start = async () => {
    setState({ running: true, error: null });
    setElapsedSeconds(0);
    try {
      const response = await createTechOpsApi(client).runCommercializationAdvisory(projectId);
      // Keep the last successful report per project. It survives navigation and refresh;
      // a new run intentionally replaces it with the latest Market/BM-based analysis.
      window.localStorage.setItem(storageKey(projectId), JSON.stringify(response));
      window.dispatchEvent(new CustomEvent('tech-ops-advisory-completed', { detail: { projectId } }));
      setReport(response);
      setState({ running: false, error: null });
    } catch (error) {
      setState({ running: false, error });
    }
  };

  const downloadReport = async () => {
    if (!report?.result) return;
    setDownloading(true);
    try {
      const blob = await createTechOpsApi(client).downloadCommercializationReport(projectId, report.result);
      const url = window.URL.createObjectURL(blob);
      const anchor = document.createElement('a');
      anchor.href = url; anchor.download = 'tech-ops-analysis-report.docx'; anchor.click();
      window.URL.revokeObjectURL(url);
    } catch (error) {
      setState((current) => ({ ...current, error }));
    } finally {
      setDownloading(false);
    }
  };

  return <main className="tech-ops-page commercialization-start-page">
    <header className="commercialization-start-page__hero">
      <div className="pipeline-page-heading">
      <p>5. 기술·운영 분석</p>
      <h1>기술·운영 상용화 검증</h1>
      <span>시장 가설과 BM 가정이 실제 구현, 운영, 파트너, 품질, 파일럿 조건과 충돌하는 지점을 확인합니다.</span>
      </div>
      <div className="market-page__actions commercialization-start-page__actions">
      <button type="button" onClick={() => void start()} disabled={state.running}>
        {state.running ? '기술·운영 분석 생성 중' : report ? '기술·운영 분석 다시 실행' : '기술·운영 분석 시작'}
      </button>
      </div>
    </header>
    {state.error && <section className="tech-ops-error" role="alert"><b>분석을 시작할 수 없습니다.</b><br />{getUserErrorMessage(state.error)}</section>}
    {!report && !state.running && <section className="commercialization-start-page__empty"><h2>분석 시작 전</h2><p>버튼을 누르면 프로젝트의 최신 시장·BM 결과를 읽어 상용화 조언을 생성합니다. 생성된 결과는 이 프로젝트에 고정되어 다른 화면으로 이동하거나 새로고침해도 유지됩니다.</p></section>}
    {state.running && <CommercializationProgress elapsedSeconds={elapsedSeconds} />}
    <CommercializationAdvisory report={report} onDownload={() => void downloadReport()} downloading={downloading} />
  </main>;
}
