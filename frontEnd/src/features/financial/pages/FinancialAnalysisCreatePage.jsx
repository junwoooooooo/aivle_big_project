import { useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { Alert, Button, PageHeader } from '../../../shared/ui/index.js';
import { useApiClient } from '../../../shared/api/ApiClientProvider.jsx';
import { getUserErrorMessage } from '../../../shared/api/apiError.js';
import { useServicePolicy } from '../../service-policy/useServicePolicy.js';
import { projectRoutes } from '../../projects/routing/projectRoutes.js';
import { createFinancialApi } from '../api/financialApi.js';
import FinancialAssumptionEditor from '../components/FinancialAssumptionEditor.jsx';
import FinancialScenarioEditor from '../components/FinancialScenarioEditor.jsx';
import { INITIAL_ASSUMPTIONS, SCENARIOS, requestBody } from '../model/financialModel.js';
import '../financial.css';

export default function FinancialAnalysisCreatePage() {
  const { projectId } = useParams();
  const navigate = useNavigate();
  const api = createFinancialApi(useApiClient());
  const { policy } = useServicePolicy();
  const [form, setForm] = useState({
    title: '새 재무·수익성 분석',
    analysisPeriodMonths: 12,
    assumptions: INITIAL_ASSUMPTIONS,
    scenarios: SCENARIOS,
  });
  const [error, setError] = useState(null);
  const [saving, setSaving] = useState(false);
  function setAssumption(key, value) {
    setForm((current) => ({ ...current, assumptions: { ...current.assumptions, [key]: value } }));
  }
  function setScenario(index, key, value) {
    setForm((current) => {
      const scenarios = [...current.scenarios];
      scenarios[index] = { ...scenarios[index], [key]: value };
      return { ...current, scenarios };
    });
  }
  async function create(event) {
    event.preventDefault();
    if (saving || policy.maintenanceMode) return;
    setSaving(true);
    setError(null);
    try {
      const created = await api.create(projectId, requestBody(form));
      navigate(projectRoutes.financialDetail(projectId, created.summary.id));
    } catch (requestError) {
      setError(getUserErrorMessage(requestError));
    } finally {
      setSaving(false);
    }
  }
  return <form className="financial-page" onSubmit={create}>
    <PageHeader eyebrow="Review / Financial" title="새 재무·수익성 분석" description="누락된 값은 0으로 처리하지 않습니다. 확인한 가격·판매량·비용 가정을 입력하세요." />
    {error && <Alert tone="danger" title="분석을 만들지 못했습니다.">{error}</Alert>}
    <label className="financial-title">분석 제목<input value={form.title} disabled={policy.maintenanceMode} onChange={(event) => setForm((current) => ({ ...current, title: event.target.value }))} /></label>
    <FinancialAssumptionEditor assumptions={form.assumptions} analysisPeriodMonths={form.analysisPeriodMonths} onChange={setAssumption} onPeriodChange={(value) => setForm((current) => ({ ...current, analysisPeriodMonths: value }))} disabled={policy.maintenanceMode} />
    <FinancialScenarioEditor scenarios={form.scenarios} onChange={setScenario} disabled={policy.maintenanceMode} />
    <Alert tone="info" title="계산 기준">입력한 값은 사용자 가정으로 저장됩니다. 실제 매출·비용·투자 성과를 보장하지 않습니다.</Alert>
    <Button type="submit" disabled={saving || policy.maintenanceMode}>{saving ? '생성 중…' : '분석 만들기'}</Button>
  </form>;
}
