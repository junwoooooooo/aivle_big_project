import { useMemo, useState } from 'react';
import { Link, useOutletContext, useParams } from 'react-router-dom';
import { getUserErrorMessage } from '../../../shared/api/apiError.js';
import useFinance from '../hooks/useFinance.js';
import {
  CAC_FIELDS, CONDITIONAL_FIELDS, FIXED_COST_FIELDS, INITIAL_INVESTMENT_FIELDS, REVENUE_MODELS,
  REVENUE_MONEY_FIELDS, TARGET_METRICS,
  createFinancialDraft, financialValuesFromDraft, formatMoney,
} from '../model/financeModel.js';
import '../styles/finance.css';
import AnalysisReport from './AnalysisReport.jsx';

export default function FinancePage() {
  const { projectId } = useParams();
  const { liveRevision = 0 } = useOutletContext() ?? {};
  const finance = useFinance(projectId, liveRevision);
  if (finance.loading) return <section className="finance-state" aria-busy="true">재무 입력을 불러오고 있습니다.</section>;
  if (!finance.preparation) return <section className="finance-state"><h1>재무 분석 준비</h1>
    <p role="alert">{getUserErrorMessage(finance.error)}</p>
    <Link to={`/app/projects/${projectId}/business-model`}>시장조사와 BM을 완료한 뒤 재무 분석 시작</Link></section>;
  return <FinanceWorkspace key={`${finance.preparation.preparationId}:${finance.preparation.revision}`} finance={finance} />;
}

function FinanceWorkspace({ finance }) {
  const preparation = finance.preparation;
  const fields = preparation.financialFields ?? {};
  const [draft, setDraft] = useState(() => createFinancialDraft(fields));
  const locked = Boolean(preparation.inputSnapshotId);
  const missing = useMemo(() => new Set(preparation.missingRequiredInputs ?? []), [preparation.missingRequiredInputs]);
  const safe = async (action) => { try { await action(); } catch { /* hook이 사용자용 오류 상태를 제공한다. */ } };
  const change = (key, value) => setDraft((current) => ({ ...current, [key]: value }));
  const references = preparation.upstreamReferences ?? {};
  const editedValues = () => financialValuesFromDraft(draft, fields);
  const liveCac = calculateDraftCac(draft);
  const revenueFields = draft.revenueModel === 'ONE_TIME'
    ? REVENUE_MONEY_FIELDS.filter(([key]) => key === 'unitPrice')
    : draft.revenueModel === 'SUBSCRIPTION'
      ? REVENUE_MONEY_FIELDS.filter(([key]) => key === 'monthlySubscriptionPrice')
      : REVENUE_MONEY_FIELDS;

  return <main className="finance-page">
    <header className="finance-heading"><div><p>7. 재무 분석</p><h1>{locked ? '재무 분석 입력값이 확정되었습니다' : '재무 분석 입력값을 준비하세요'}</h1>
      <span>current 시장 분석과 BM 결과의 근거를 이어받고, 부족한 값만 입력해 불변 Snapshot을 만듭니다.</span></div>
      <strong className="finance-heading__status">{locked ? '입력 확정' : preparation.readyToFinalize ? '확정 준비' : `${preparation.missingRequiredInputs.length}개 입력 필요`}</strong></header>
    {finance.error && <p className="finance-error" role="alert">{getUserErrorMessage(finance.error)}</p>}

    <section className="finance-source" aria-labelledby="finance-source-title"><div><p>시장 분석·BM에서 가져옴</p>
      <h2 id="finance-source-title">재무 가정의 원본과 근거</h2></div>
      <div className="finance-source__grid">
        <Reference label="TAM" value={references.marketAnalysis?.tam} />
        <Reference label="SAM" value={references.marketAnalysis?.sam} />
        <Reference label="시장 성장률" value={references.marketAnalysis?.growth} />
        <Reference label="시장 가격 가정" value={references.marketAnalysis?.price} />
        <Reference label="고정운영비" value={references.fixedOperatingCost?.annualEquivalent ?? references.fixedOperatingCost?.value} />
        <Reference label="초기투자" value={references.initialInvestment?.value} />
        <Reference label="기존 3개년 목표" value={references.threeYearTargets?.value} />
      </div><p className="finance-source__ai-note">AI 추정은 Market·BM 근거를 참고한 초안이며 자동 저장되지 않습니다. 근거와 가정을 확인한 뒤 채택하거나 수정하세요.</p>
      <details><summary>Market/BM 근거·가정·Evidence·Caveat 전체 보기</summary>
        <pre className="finance-source-detail">{JSON.stringify({ marketAnalysis: references.marketAnalysis,
          businessModel: references.businessModel, conceptHypotheses: references.conceptHypotheses }, null, 2)}</pre></details>
      <small>Market Version {preparation.sourceMarketResearchVersionId} · BM Version {preparation.sourceBusinessModelVersionId}</small></section>

    <FinancialSection eyebrow="고정운영비" title="연간 고정비 세부항목" fields={FIXED_COST_FIELDS}
      draft={draft} change={change} sourceFields={fields} missing={missing} locked={locked}
      assistance={preparation.assistance} finance={finance} safe={safe} editedValues={editedValues} />
    <FinancialSection eyebrow="초기투자" title="초기 투자 세부항목" fields={INITIAL_INVESTMENT_FIELDS}
      draft={draft} change={change} sourceFields={fields} missing={missing} locked={locked}
      assistance={preparation.assistance} finance={finance} safe={safe} editedValues={editedValues} />

    <section className="finance-section" aria-labelledby="finance-targets-title"><SectionHeading eyebrow="3개년 목표" title="사업 유형에 맞는 목표 지표" />
      <div className="finance-form-grid finance-targets">
        <label><span>목표 지표</span><select disabled={locked || fields.threeYearTargets?.readOnly} value={draft.targetMetric}
          onChange={(event) => change('targetMetric', event.target.value)}>{TARGET_METRICS.map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label>
        <label><span>단위</span><input disabled={locked || fields.threeYearTargets?.readOnly} value={draft.targetUnit}
          onChange={(event) => change('targetUnit', event.target.value)} placeholder="명, 건, 개" /></label>
        {[1, 2, 3].map((year) => <label key={year} data-missing={missing.has('threeYearTargets')}><span>{year}년차 목표</span>
          <input type="number" min="0" disabled={locked || fields.threeYearTargets?.readOnly} value={draft.targetYears[year - 1]}
            onChange={(event) => { const values = [...draft.targetYears]; values[year - 1] = event.target.value; change('targetYears', values); }} /></label>)}
      </div><SourceNote field={fields.threeYearTargets} /></section>

    <section className="finance-section" aria-labelledby="finance-revenue-title"><SectionHeading eyebrow="수익 모델" title="가격 및 반복 매출 가정" />
      <div className="finance-form-grid">
        <label data-missing={missing.has('revenueModel')}><span>수익 모델</span><select disabled={locked || fields.revenueModel?.readOnly}
          value={draft.revenueModel} onChange={(event) => change('revenueModel', event.target.value)}>
          {REVENUE_MODELS.map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select><SourceNote field={fields.revenueModel} /></label>
        {revenueFields.map(([key, label]) => <MoneyInput key={key} fieldKey={key} label={label} value={draft[key]}
          onChange={change} field={fields[key]} missing={missing.has(key)} locked={locked} assistance={preparation.assistance?.[key]}
          finance={finance} safe={safe} editedValue={editedValues()[key]} />)}
        {(draft.revenueModel === 'SUBSCRIPTION' || draft.revenueModel === 'HYBRID') && <label data-missing={missing.has('monthlyChurnRate')}><span>월 이탈률 (%)</span><input type="number" min="0" max="100"
          disabled={locked || fields.monthlyChurnRate?.readOnly} value={draft.monthlyChurnRate}
          onChange={(event) => change('monthlyChurnRate', event.target.value)} /><SourceNote field={fields.monthlyChurnRate} /></label>}
      </div></section>

    <section className="finance-section" aria-labelledby="finance-cac-title"><SectionHeading eyebrow="CAC" title="고객 획득 비용 구성값" />
      <p className="finance-note">CAC를 직접 계산하지 마세요. 비용과 신규 고객 수를 입력하면 시스템이 계산합니다.</p>
      <div className="finance-form-grid">{CAC_FIELDS.map(([key, label]) => <MoneyInput key={key} fieldKey={key} label={label}
        value={draft[key]} onChange={change} field={fields[key]} missing={missing.has(key)} locked={locked}
        assistance={preparation.assistance?.[key]} finance={finance} safe={safe} editedValue={editedValues()[key]} />)}
        <label data-missing={missing.has('newCustomerCount')}><span>신규 고객 수</span><input type="number" min="1"
          disabled={locked || fields.newCustomerCount?.readOnly} value={draft.newCustomerCount}
          onChange={(event) => change('newCustomerCount', event.target.value)} /><SourceNote field={fields.newCustomerCount} /></label>
        <div className="finance-cac-result"><span>시스템 계산 CAC</span><strong>{formatMoney(liveCac ?? preparation.calculatedCac)}</strong>
          <small>(총 마케팅비 + 총 영업비) ÷ 신규 고객 수</small></div>
      </div></section>

    <section className="finance-section"><details><summary>조건부 단위원가 입력</summary>
      <p className="finance-note">사업 구조나 외부 모듈 계약에 필요한 항목만 입력하세요. 모든 사업에 강제되지 않습니다.</p>
      <div className="finance-form-grid">{CONDITIONAL_FIELDS.map(([key, label]) => <MoneyInput key={key} fieldKey={key} label={label}
        value={draft[key]} onChange={change} field={fields[key]} locked={locked} assistance={preparation.assistance?.[key]}
        finance={finance} safe={safe} editedValue={editedValues()[key]} />)}</div></details></section>

    <section className="finance-assistance" aria-labelledby="finance-assistance-title"><div><p>설명·예시·AI 추정</p><h2 id="finance-assistance-title">입력 도움말</h2></div>
      <p className="finance-ai-guide">비용·가격·3개년 목표 추천은 Market·BM과 선택적 Tavily 근거를 참고합니다. 외부 근거가 없어도 Finance 입력은 계속할 수 있으며, 추천은 검토 전 확정값이 아닙니다.</p>
      <div>{Object.entries(preparation.assistance ?? {}).map(([key, item]) => <article key={key}><strong>{fieldLabel(key)}</strong><span>{item.explanation}</span>
        {item.example && <span>{item.example}</span>}<small>{estimateLabel(item)}</small>
        {item.proposalValue != null && fields[key] && <Recommendation item={item} />}
        {fields[key] && key !== 'revenueModel' && <EstimateControls fieldKey={key} item={item} field={fields[key]}
          locked={locked} busy={finance.busy === `estimate:${key}`} generate={finance.generateEstimate}
          decide={finance.decideEstimate} editedValue={editedValues()[key]} safe={safe} />}
      </article>)}</div></section>

    {!locked && <button className="finance-save" type="button" disabled={finance.busy === 'save'}
      onClick={() => void safe(() => finance.save(financialValuesFromDraft(draft, fields)))}>재무 입력 저장</button>}

    <section className="finance-finalize" aria-live="polite"><div><p>FinancialInputSnapshot</p>
      <h2>{finance.snapshot ? '재무 분석 입력이 확정되었습니다' : preparation.readyToFinalize ? 'Snapshot을 확정할 수 있습니다' : `${preparation.missingRequiredInputs.length}개 필수 입력이 남았습니다`}</h2>
      <span>{finance.snapshot ? `${finance.snapshot.snapshotId} · ${finance.snapshot.snapshotHash}` : preparation.missingRequiredInputs.join(' · ')}</span></div>
      {!finance.snapshot ? <button type="button" disabled={!preparation.readyToFinalize || finance.busy === 'finalize'}
        onClick={() => void safe(finance.finalize)}>입력 Snapshot 확정</button>
        : <><button type="button" disabled={finance.busy === 'reopen'} onClick={() => void safe(finance.reopen)}>입력 수정</button>
          <button type="button" disabled={finance.busy === 'handoff'} onClick={() => void safe(finance.handoff)}>재무 분석 Handoff 준비</button></>}
      {finance.run && <small>외부 연결 상태: {finance.run.status}{finance.run.stale ? ' · 입력 갱신 필요' : ''}</small>}
    </section>
    {finance.snapshot && <section className="finance-finalize" aria-live="polite"><div><p>Financial analysis module</p>
      <h2>{finance.analysis?.result?.report?.headline ?? '확정된 입력값으로 재무 분석과 보고서를 생성할 수 있습니다.'}</h2>
      <span>{analysisStatus(finance.analysis)}</span></div>
      <button type="button" disabled={finance.busy === 'analysis' || ['QUEUED', 'RUNNING'].includes(finance.analysis?.status)}
        onClick={() => void safe(finance.analyze)}>{['QUEUED', 'RUNNING'].includes(finance.analysis?.status) ? '재무 분석 실행 중…' : '재무 분석 및 보고서 생성'}</button>
    </section>}
    {finance.analysis?.stale && <p className="finance-warning" role="status">상위 current 입력이 바뀌어 이 재무 결과는 stale 상태입니다. 입력을 다시 확정해 주세요.</p>}
    {finance.analysis?.safeErrorCode && !finance.analysis?.result && <p className="finance-error" role="alert">재무 보고서 생성 실패: {finance.analysis.safeErrorCode}{finance.analysis.retryable ? ' · 재시도할 수 있습니다.' : ''}</p>}
    <AnalysisReport analysis={finance.analysis} />
  </main>;
}

function analysisStatus(analysis) {
  if (!analysis || analysis.status === 'NOT_STARTED') return '아직 분석을 실행하지 않았습니다.';
  if (['QUEUED', 'RUNNING'].includes(analysis.status)) return `TaskRun ${analysis.taskRunId} · ${analysis.status}`;
  if (analysis.fallback) return '결정론 계산 완료 · AI 설명 Provider 실패로 Fallback 보고서 사용';
  return `TaskRun ${analysis.taskRunId} · ${analysis.status}`;
}

function calculateDraftCac(draft) {
  const values = [draft.totalMarketingCost, draft.totalSalesCost, draft.newCustomerCount].map(Number);
  if (!values.every(Number.isFinite) || values[2] <= 0 || values[0] < 0 || values[1] < 0) return null;
  return { amount: Math.round(((values[0] + values[1]) / values[2]) * 100) / 100, currency: 'KRW' };
}

function estimateLabel(item) {
  if (['QUEUED', 'RUNNING'].includes(item?.estimateStatus)) return '추천 생성 중';
  if (item?.estimateStatus === 'FAILED') return '추천 생성 실패';
  if (item?.estimateStatus === 'ACCEPTED' || ['ACCEPTED', 'USER_EDITED_ACCEPTED'].includes(item?.decision)) return '채택됨';
  if (item?.proposalValue != null && item?.estimateStatus === 'SUCCEEDED') return 'AI 추천';
  return '추천 없음';
}

function EstimateControls({ fieldKey, item, field, locked, busy, generate, decide, editedValue, safe }) {
  if (locked || field?.readOnly || item?.estimateStatus === 'ACCEPTED') return null;
  const pending = ['QUEUED', 'RUNNING'].includes(item?.estimateStatus);
  const proposed = item?.proposalValue != null && item?.estimateStatus === 'SUCCEEDED';
  if (!proposed) return <button type="button" disabled={busy || pending}
    onClick={() => void safe(() => generate(fieldKey))}>AI 추천 받기</button>;
  return <div><button type="button" disabled={busy} onClick={() => void safe(() => decide(fieldKey, { action: 'ACCEPT', value: null }))}>AI 추천 채택</button>
    <button type="button" disabled={busy} onClick={() => void safe(() => decide(fieldKey, { action: 'EDIT_AND_ACCEPT', value: editedValue }))}>입력값으로 수정 후 채택</button>
    <button type="button" disabled={busy} onClick={() => void safe(() => decide(fieldKey, { action: 'REJECT', value: null }))}>AI 추천 거절</button>
    <button type="button" disabled={busy} onClick={() => void safe(() => decide(fieldKey, { action: 'REQUEST_ALTERNATIVE', value: null }))}>다른 추천 요청</button></div>;
}

function FinancialSection({ eyebrow, title, fields, draft, change, sourceFields, missing, locked,
  assistance, finance, safe, editedValues }) {
  return <section className="finance-section"><SectionHeading eyebrow={eyebrow} title={title} />
    <div className="finance-form-grid">{fields.map(([key, label]) => <MoneyInput key={key} fieldKey={key} label={label}
      value={draft[key]} onChange={change} field={sourceFields[key]} missing={missing.has(key)} locked={locked}
      assistance={assistance?.[key]} finance={finance} safe={safe} editedValue={editedValues()[key]} />)}</div></section>;
}
function SectionHeading({ eyebrow, title }) { return <div className="finance-section__heading"><div><p>{eyebrow}</p><h2>{title}</h2></div><span>KRW 기준</span></div>; }
function MoneyInput({ fieldKey, label, value, onChange, field, missing, locked, assistance, finance, safe, editedValue }) {
  return <label data-missing={Boolean(missing)}><span>{label}</span><input type="number" min="0" disabled={locked || field?.readOnly}
    value={value} onChange={(event) => onChange(fieldKey, event.target.value)} /><SourceNote field={field} />
    {assistance && <EstimateControls fieldKey={fieldKey} item={assistance} field={field} locked={locked}
      busy={finance?.busy === `estimate:${fieldKey}`} generate={finance?.generateEstimate}
      decide={finance?.decideEstimate} editedValue={editedValue} safe={safe} />}</label>;
}
function SourceNote({ field }) {
  if (field?.source === 'MARKET_ANALYSIS_ASSUMPTION') return <small data-source="inherited">시장 분석 가정 · 확인 후 저장 필요</small>;
  if (field?.source === 'BUSINESS_MODEL_HANDOFF') return <small data-source="inherited">BM financial handoff 근거</small>;
  if (field?.source === 'BUSINESS_MODEL_ASSUMPTION') return <small data-source="inherited">BM 분석 가정 · 확인 후 저장 필요</small>;
  if (field?.source === 'CONCEPT_HYPOTHESIS') return <small data-source="inherited">컨셉 확정 가정 · 확인 후 저장 필요</small>;
  return <small data-source={field?.readOnly ? 'inherited' : 'input'}>{field?.readOnly ? '기존 상위 Snapshot에서 가져옴' : '없을 때만 사용자 입력'}</small>;
}
function Reference({ label, value }) {
  const display = value?.amount != null ? formatMoney(value)
    : value?.value != null ? `${new Intl.NumberFormat('ko-KR').format(value.value)} ${value.unit ?? ''}`
      : value?.base != null ? `${new Intl.NumberFormat('ko-KR').format(value.base)} ${value.currency ?? 'KRW'}`
        : value == null ? '값 없음' : '근거·가정 상세 포함';
  return <article><span>{label}</span><strong>{display}</strong></article>;
}

function fieldLabel(key) {
  return ({ annualFixedLaborCost: '연간 고정 인건비', annualFixedRentAndManagementCost: '연간 임차·관리비',
    annualFixedInfrastructureCost: '연간 인프라비', initialDevelopmentAndRnDCost: '초기 개발·R&D 비용',
    initialEquipmentAndInfrastructureCost: '초기 설비·인프라 비용', initialPatentAndLicensingCost: '초기 특허·라이선스 비용',
    totalMarketingCost: '총 마케팅비', totalSalesCost: '총 영업비', threeYearTargets: '3개년 목표',
    unitVariableCost: '단위 변동비', paymentFee: '결제 수수료', partnerPayout: '파트너 지급액', shippingCost: '배송비',
    customerIncrementalInfraCost: '고객 증가분 인프라비', unitPrice: '제품 단가', monthlySubscriptionPrice: '월 구독 가격',
    monthlyChurnRate: '월 이탈률', revenueModel: '수익 모델', newCustomerCount: '신규 고객 수' })[key] ?? key;
}

function Recommendation({ item }) {
  const proposal = item.proposalValue;
  const value = proposal?.amount != null ? `${formatNumber(proposal.amount)} ${proposal.currency ?? 'KRW'}`
    : proposal?.percent != null ? `${proposal.percent}%`
      : proposal?.count != null ? `${formatNumber(proposal.count)}명`
        : proposal?.years ? proposal.years.map((year) => `${year.year}년차 ${formatNumber(year.value)}`).join(' · ')
          : '추천값 확인 필요';
  return <div className="finance-recommendation"><strong>추천값: {value}</strong>
    {item.explanation && <span>선정 근거: {item.explanation}</span>}
    {item.assumptions?.length ? <small>가정: {item.assumptions.join(' · ')}</small> : null}
    {item.confidence && <small>신뢰도: {item.confidence}</small>}</div>;
}

function formatNumber(value) { return new Intl.NumberFormat('ko-KR').format(Number(value ?? 0)); }
