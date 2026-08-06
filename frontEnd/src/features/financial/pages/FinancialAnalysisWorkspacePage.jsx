import { useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { Alert, Button, ErrorState, LoadingState, PageHeader } from '../../../shared/ui/index.js';
import { getUserErrorMessage } from '../../../shared/api/apiError.js';
import { useServicePolicy } from '../../service-policy/useServicePolicy.js';
import { projectRoutes } from '../../projects/routing/projectRoutes.js';
import FinancialAssumptionEditor from '../components/FinancialAssumptionEditor.jsx';
import FinancialScenarioEditor from '../components/FinancialScenarioEditor.jsx';
import FinancialResultSummary from '../components/FinancialResultSummary.jsx';
import FinancialMonthlyTable from '../components/FinancialMonthlyTable.jsx';
import FinancialSensitivitySection from '../components/FinancialSensitivitySection.jsx';
import useFinancialAnalysis from '../hooks/useFinancialAnalysis.js';
import { buildFinancialPreview, INITIAL_ASSUMPTIONS, SCENARIOS, requestBody } from '../model/financialModel.js';
import '../financial.css';

export default function FinancialAnalysisWorkspacePage() {
  const { projectId, analysisId } = useParams();
  const navigate = useNavigate();
  const { data, loading, error, refresh, api } = useFinancialAnalysis(projectId, analysisId);
  const { policy } = useServicePolicy();
  const [editing, setEditing] = useState(null);
  const [saving, setSaving] = useState(false);
  const [submitError, setSubmitError] = useState(null);
  const form = useMemo(() => editing ?? (data ? {
    title: data.summary.title,
    analysisPeriodMonths: data.summary.analysisPeriodMonths,
    assumptions: data.assumptions ?? INITIAL_ASSUMPTIONS,
    scenarios: data.scenarios ?? SCENARIOS,
  } : null), [editing, data]);

  if (loading && !data) return <LoadingState label="재무 분석을 불러오는 중입니다." />;
  if (error && !data) return <ErrorState description={getUserErrorMessage(error)} onRetry={refresh} />;

  const completed = data.summary.status === 'COMPLETED';
  const blocked = policy.maintenanceMode || completed;
  function updateForm(change) { setEditing((current) => ({ ...(current ?? form), ...change })); }
  function setAssumption(key, value) {
    const current = editing ?? form;
    setEditing({ ...current, assumptions: { ...current.assumptions, [key]: value } });
  }
  function setScenario(index, key, value) {
    const current = editing ?? form;
    const scenarios = [...current.scenarios];
    scenarios[index] = { ...scenarios[index], [key]: value };
    setEditing({ ...current, scenarios });
  }
  async function save(run = false) {
    if (saving || blocked) return;
    setSaving(true);
    setSubmitError(null);
    try {
      await api.update(projectId, analysisId, requestBody(form));
      if (run) await api.run(projectId, analysisId);
      setEditing(null);
      await refresh();
    } catch (requestError) {
      setSubmitError(getUserErrorMessage(requestError));
    } finally {
      setSaving(false);
    }
  }
  async function duplicate() {
    try {
      const copy = await api.duplicate(projectId, analysisId);
      navigate(projectRoutes.financialDetail(projectId, copy.summary.id));
    } catch (requestError) {
      setSubmitError(getUserErrorMessage(requestError));
    }
  }
  let result;
  try { result = data.resultJson ? JSON.parse(data.resultJson) : null; } catch { result = null; }

  return (
    <div className="financial-page">
      <PageHeader
        eyebrow="Review / Financial"
        title={data.summary.title}
        description="가정 입력 → 시나리오 → 결과 확인"
        actions={<><Button variant="secondary" onClick={duplicate} disabled={policy.maintenanceMode}>복제</Button>{!completed && <Button onClick={() => save(true)} disabled={saving || policy.maintenanceMode}>계산 실행</Button>}</>}
      />
      {submitError && <Alert tone="danger" title="저장하지 못했습니다.">{submitError}</Alert>}
      {policy.maintenanceMode && <Alert tone="warning" title="서비스 점검 중">기존 분석은 조회할 수 있지만 저장과 계산은 잠시 사용할 수 없습니다.</Alert>}
      <div className="financial-steps" aria-label="재무 분석 단계"><span>1. 분석 근거</span><span>2. 수익 모델</span><span>3. 비용 구조</span><span>4. 시나리오</span><span>5. 결과 확인</span></div>
      <section className="financial-source"><h2>분석 근거</h2><p>사업 타당성 분석 ID: {data.feasibilityAssessmentId}</p><p>완료된 분석의 근거 스냅샷은 이후 원본이 변경되어도 자동으로 바뀌지 않습니다.</p></section>
      <label className="financial-title">분석 제목<input value={form.title} disabled={blocked} onChange={(event) => updateForm({ title: event.target.value })} /></label>
      <FinancialAssumptionEditor assumptions={form.assumptions} analysisPeriodMonths={form.analysisPeriodMonths} onChange={setAssumption} onPeriodChange={(value) => updateForm({ analysisPeriodMonths: value })} disabled={blocked} />
      <FinancialScenarioEditor scenarios={form.scenarios} onChange={setScenario} disabled={blocked} />
      {!completed && <Button onClick={() => save(false)} disabled={saving || policy.maintenanceMode}>{saving ? '저장 중…' : '가정 저장'}</Button>}
      {!completed && <FinancialResultSummary result={buildFinancialPreview(form.assumptions)} preview />}
      <FinancialResultSummary result={result} />
      <FinancialMonthlyTable result={result} />
      <FinancialSensitivitySection result={result} />
      <Alert tone="info" title="면책 안내">이 결과는 입력 가정에 따른 예상값이며 회계·세무·투자 자문을 대체하지 않습니다.</Alert>
    </div>
  );
}
