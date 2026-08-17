import { createContext, useContext, useMemo, useState } from 'react';
import { Link, useOutletContext, useParams } from 'react-router-dom';
import { getUserErrorMessage } from '../../../shared/api/apiError.js';
import { ProjectSplitWorkspace, ProjectStageHeader, ProjectWorkspace } from '../../../shared/ui/index.js';
import useFinance from '../hooks/useFinance.js';
import {
  CAC_FIELDS, CONDITIONAL_FIELDS, FIXED_COST_FIELDS, INITIAL_INVESTMENT_FIELDS, REVENUE_MODELS,
  REVENUE_MONEY_FIELDS, TARGET_METRICS,
  createFinancialDraft, financialValuesFromDraft, formatMoney,
} from '../model/financeModel.js';
import '../styles/finance.css';
import AnalysisReport from './AnalysisReport.jsx';

const FinanceRefreshContext = createContext(null);

export default function FinancePage() {
  const { projectId } = useParams();
  const { liveRevision = 0 } = useOutletContext() ?? {};
  const finance = useFinance(projectId, liveRevision);
  if (finance.loading) return <section className="finance-state" aria-busy="true">재무 입력을 불러오고 있습니다.</section>;
  if (!finance.preparation) return <section className="finance-state"><h1>재무 분석 준비</h1>
    <p role="alert">{getUserErrorMessage(finance.error)}</p>
    <p>시장 분석이나 BM 완료 여부와 관계없이 재무 입력을 직접 작성할 수 있습니다.</p></section>;
  return <FinanceWorkspace key={`${finance.preparation.preparationId}:${finance.preparation.revision}`}
    projectId={projectId} finance={finance} />;
}

function FinanceWorkspace({ projectId, finance }) {
  const preparation = finance.preparation;
  const fields = useMemo(() => preparation.financialFields ?? {}, [preparation.financialFields]);
  const [draft, setDraft] = useState(() => createFinancialDraft(fields));
  const locked = Boolean(preparation.inputSnapshotId);
  const missing = useMemo(() => new Set(preparation.missingRequiredInputs ?? []), [preparation.missingRequiredInputs]);
  const safe = async (action) => { try { await action(); } catch { /* hook이 사용자용 오류 상태를 제공한다. */ } };
  const change = (key, value) => setDraft((current) => ({ ...current, [key]: value }));
  const references = preparation.upstreamReferences ?? {};
  const hasOptionalContext = Boolean(preparation.sourceMarketResearchVersionId
    || preparation.sourceBusinessModelVersionId || Object.keys(references).length);
  const editedValues = () => financialValuesFromDraft(draft, fields);
  const targetView = proposalTargets(draft, fields.threeYearTargets, preparation.assistance?.threeYearTargets);
  const customerCountView = proposalPrimitive(draft.newCustomerCount, fields.newCustomerCount,
    preparation.assistance?.newCustomerCount, 'count');
  const churnView = proposalPrimitive(draft.monthlyChurnRate, fields.monthlyChurnRate,
    preparation.assistance?.monthlyChurnRate, 'percent');
  const liveCac = calculateDraftCac({ ...draft, newCustomerCount: customerCountView });
  const revenueFields = draft.revenueModel === 'ONE_TIME'
    ? REVENUE_MONEY_FIELDS.filter(([key]) => key === 'unitPrice')
    : draft.revenueModel === 'SUBSCRIPTION'
      ? REVENUE_MONEY_FIELDS.filter(([key]) => key === 'monthlySubscriptionPrice')
      : REVENUE_MONEY_FIELDS;
  const groupEstimateKeys = useMemo(() => Object.keys(preparation.assistance ?? {}).filter((key) => {
    const item = preparation.assistance?.[key];
    return key !== 'revenueModel' && fields[key] && !fields[key].readOnly
      && !['QUEUED', 'RUNNING', 'SUCCEEDED', 'ACCEPTED'].includes(item?.estimateStatus)
      && !['ACCEPTED', 'USER_EDITED_ACCEPTED'].includes(item?.decision);
  }), [fields, preparation.assistance]);
  const refreshContainer = () => void finance.refresh({ preserveView: true });

  return <FinanceRefreshContext.Provider value={refreshContainer}><ProjectWorkspace as="main" mode="compose" className="finance-page">
    <ProjectStageHeader step={3} eyebrow="재무 계획" title={locked ? '재무 가정이 확정되었습니다' : '사업에 필요한 비용과 수익을 입력하세요'}
      description="재무 입력만으로 독립 실행할 수 있으며, 시장 분석과 수익 구조가 있으면 참고 문맥으로 함께 사용합니다."
      status={<div className="finance-statuses" aria-label="재무 상태">
        <strong className="finance-heading__status">준비 · {locked ? '확정' : preparation.readyToFinalize ? '완료' : '입력 필요'}</strong>
        <strong className="finance-heading__status">입력 · {finance.snapshot ? '저장 완료' : '입력 중'}</strong>
        <strong className="finance-heading__status">분석 · {analysisStatus(finance.analysis)}</strong>
        {finance.error && <strong className="finance-heading__status" data-error="true">오류</strong>}
      </div>} />
    {finance.error && <p className="finance-error" role="alert">{getUserErrorMessage(finance.error)}</p>}

    <section className="finance-source" aria-labelledby="finance-source-title"><div><p>{hasOptionalContext ? '선택 참고 문맥 연결됨' : '재무 고유 입력'}</p>
      <h2 id="finance-source-title">재무 가정의 원본과 근거</h2></div>
      <RefreshButton />
      {!hasOptionalContext && <p>연결된 시장 분석이나 BM 없이 사용자 입력을 기준으로 준비합니다.</p>}
      <div className="finance-source__grid">
        <Reference label="TAM" value={references.marketAnalysis?.tam} />
        <Reference label="SAM" value={references.marketAnalysis?.sam} />
        <Reference label="시장 성장률" value={references.marketAnalysis?.growth} />
        <Reference label="시장 가격 가정" value={references.marketAnalysis?.price} />
        <Reference label="Concept 가설" value={references.conceptHypotheses?.values ?? references.conceptHypotheses} />
        <Reference label="수익 구조의 재무 정보" value={references.businessModel?.financialHandoff} />
      </div><p className="finance-source__ai-note">AI 추정은 Market·BM 근거를 참고한 초안이며 자동 저장되지 않습니다. 근거와 가정을 확인한 뒤 채택하거나 수정하세요.</p>
      <details><summary>시장·수익 구조의 근거, 가정과 주의사항 전체 보기</summary>
        <pre className="finance-source-detail">{JSON.stringify({ marketAnalysis: references.marketAnalysis,
          businessModel: references.businessModel, conceptHypotheses: references.conceptHypotheses }, null, 2)}</pre></details>
      {hasOptionalContext && <small>Market Version {preparation.sourceMarketResearchVersionId ?? '없음'} · BM Version {preparation.sourceBusinessModelVersionId ?? '없음'}</small>}</section>

    <ProjectSplitWorkspace className="finance-input-workspace" primary={<>
    <FinancialSection eyebrow="고정운영비" title="연간 고정비 세부항목" fields={FIXED_COST_FIELDS}
      draft={draft} change={change} sourceFields={fields} missing={missing} locked={locked}
      assistance={preparation.assistance} finance={finance} safe={safe} editedValues={editedValues} />
    <FinancialSection eyebrow="초기투자" title="초기 투자 세부항목" fields={INITIAL_INVESTMENT_FIELDS}
      draft={draft} change={change} sourceFields={fields} missing={missing} locked={locked}
      assistance={preparation.assistance} finance={finance} safe={safe} editedValues={editedValues} />

    <section className="finance-section" aria-labelledby="finance-targets-title"><SectionHeading eyebrow="3개년 목표" title="사업 유형에 맞는 목표 지표" />
      <div className="finance-form-grid finance-targets project-form-layout">
        <label><span>목표 지표</span><select disabled={locked || fields.threeYearTargets?.readOnly} value={targetView.targetMetric}
          data-proposal-preview={targetView !== draft || undefined}
          onChange={(event) => change('targetMetric', event.target.value)}>{TARGET_METRICS.map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label>
        <label><span>단위</span><input disabled={locked || fields.threeYearTargets?.readOnly} value={targetView.targetUnit}
          data-proposal-preview={targetView !== draft || undefined}
          onChange={(event) => change('targetUnit', event.target.value)} placeholder="명, 건, 개" /></label>
        {[1, 2, 3].map((year) => <label key={year} data-missing={missing.has('threeYearTargets')}><span>{year}년차 목표</span>
          <input type="number" min="0" disabled={locked || fields.threeYearTargets?.readOnly} value={targetView.targetYears[year - 1]}
            data-proposal-preview={targetView !== draft || undefined}
            onChange={(event) => { const values = [...draft.targetYears]; values[year - 1] = event.target.value; change('targetYears', values); }} /></label>)}
      </div><SourceNote field={fields.threeYearTargets} /></section>

    <section className="finance-section" aria-labelledby="finance-revenue-title"><SectionHeading eyebrow="수익 모델" title="가격 및 반복 매출 가정" />
      <div className="finance-form-grid project-form-layout">
        <label data-missing={missing.has('revenueModel')}><span>수익 모델</span><select disabled={locked || fields.revenueModel?.readOnly}
          value={draft.revenueModel} onChange={(event) => change('revenueModel', event.target.value)}>
          {REVENUE_MODELS.map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select><SourceNote field={fields.revenueModel} /></label>
        {revenueFields.map(([key, label]) => <MoneyInput key={key} fieldKey={key} label={label} value={draft[key]}
          onChange={change} field={fields[key]} missing={missing.has(key)} locked={locked} assistance={preparation.assistance?.[key]}
          finance={finance} safe={safe} editedValue={editedValues()[key]} />)}
        {(draft.revenueModel === 'SUBSCRIPTION' || draft.revenueModel === 'HYBRID') && <label data-missing={missing.has('monthlyChurnRate')}><span>월 이탈률 (%)</span><input type="number" min="0" max="100"
          disabled={locked || fields.monthlyChurnRate?.readOnly} value={churnView}
          data-proposal-preview={churnView !== draft.monthlyChurnRate || undefined}
          onChange={(event) => change('monthlyChurnRate', event.target.value)} /><SourceNote field={fields.monthlyChurnRate} /></label>}
      </div></section>

    </>} secondary={<>
    <section className="finance-section" aria-labelledby="finance-cac-title"><SectionHeading eyebrow="CAC" title="고객 획득 비용 구성값" />
      <p className="finance-note">CAC를 직접 계산하지 마세요. 비용과 신규 고객 수를 입력하면 시스템이 계산합니다.</p>
      <div className="finance-form-grid project-form-layout">{CAC_FIELDS.map(([key, label]) => <MoneyInput key={key} fieldKey={key} label={label}
        value={draft[key]} onChange={change} field={fields[key]} missing={missing.has(key)} locked={locked}
        assistance={preparation.assistance?.[key]} finance={finance} safe={safe} editedValue={editedValues()[key]} />)}
        <label data-missing={missing.has('newCustomerCount')}><span>신규 고객 수</span><input type="number" min="1"
          disabled={locked || fields.newCustomerCount?.readOnly} value={customerCountView}
          data-proposal-preview={customerCountView !== draft.newCustomerCount || undefined}
          onChange={(event) => change('newCustomerCount', event.target.value)} /><SourceNote field={fields.newCustomerCount} /></label>
        <div className="finance-cac-result"><span>시스템 계산 CAC</span><strong>{formatMoney(liveCac ?? preparation.calculatedCac)}</strong>
          <small>(총 마케팅비 + 총 영업비) ÷ 신규 고객 수</small></div>
      </div></section>

    <section className="finance-section"><details><summary>조건부 단위원가 입력</summary>
      <p className="finance-note">사업 구조나 외부 모듈 계약에 필요한 항목만 입력하세요. 모든 사업에 강제되지 않습니다.</p>
      <div className="finance-form-grid project-form-layout">{CONDITIONAL_FIELDS.map(([key, label]) => <MoneyInput key={key} fieldKey={key} label={label}
        value={draft[key]} onChange={change} field={fields[key]} locked={locked} assistance={preparation.assistance?.[key]}
        finance={finance} safe={safe} editedValue={editedValues()[key]} />)}</div></details></section>

    <section className="finance-assistance" aria-labelledby="finance-assistance-title"><div><p>설명·예시·AI 추정</p><h2 id="finance-assistance-title">입력 도움말</h2></div>
      <p className="finance-ai-guide">비용·가격·3개년 목표 추천은 Market·BM과 선택적 Tavily 근거를 참고합니다. 외부 근거가 없어도 Finance 입력은 계속할 수 있으며, 추천은 검토 전 확정값이 아닙니다.</p>
      <div className="finance-ai-scope"><strong>AI 추천 대상</strong><span>연간 고정비, 초기투자, 마케팅·영업비, 조건부 단위원가, 가격, 이탈률, 신규 고객 수, 정확한 1·2·3년 목표</span>
        <small>추천은 input에 미리보기만 하며 ACCEPT 또는 EDIT_AND_ACCEPT 전에는 사용자 결정으로 저장되지 않습니다.</small>
        <button className="finance-group-recommendation" type="button"
          disabled={locked || finance.busy === 'estimate:group' || groupEstimateKeys.length === 0}
          onClick={() => void safe(() => finance.generateEstimates(groupEstimateKeys))}>미확정 항목 그룹 추천</button></div>
      <div>{Object.entries(preparation.assistance ?? {}).map(([key, item]) => <article key={key}><strong>{fieldLabel(key)}</strong><span>{item.explanation}</span>
        {item.example && <span>{item.example}</span>}<small>{estimateLabel(item)}</small>
        {item.proposalValue != null && fields[key] && <Recommendation item={item} />}
        {fields[key] && key !== 'revenueModel' && <EstimateControls fieldKey={key} item={item} field={fields[key]}
          locked={locked} busy={finance.busy === `estimate:${key}`} generate={finance.generateEstimate}
          decide={finance.decideEstimate} editedValue={editedValues()[key]} safe={safe} />}
      </article>)}</div></section>

    </>} />

    {!locked && <button className="finance-save" type="button" disabled={finance.busy === 'save'}
      onClick={() => void safe(() => finance.save(financialValuesFromDraft(draft, fields)))}>재무 입력 저장</button>}

    <section className="finance-finalize" aria-live="polite"><div><p>재무 분석 입력</p>
      <h2>{finance.snapshot ? '재무 분석 입력을 저장했습니다' : preparation.readyToFinalize ? '입력 내용을 저장할 수 있습니다' : `${preparation.missingRequiredInputs.length}개 필수 입력이 남았습니다`}</h2>
      <span>{finance.snapshot ? '저장된 입력을 재무 분석에 사용합니다.' : preparation.missingRequiredInputs.join(' · ')}</span>{finance.snapshot && <details><summary>기술 정보</summary><p>{finance.snapshot.snapshotId} · {finance.snapshot.snapshotHash}</p></details>}</div>
      {!finance.snapshot ? <button type="button" disabled={!preparation.readyToFinalize || finance.busy === 'finalize'}
        onClick={() => void safe(finance.finalize)}>입력 내용 저장</button>
        : <><button type="button" disabled={finance.busy === 'reopen'} onClick={() => void safe(finance.reopen)}>입력 수정</button>
          <button type="button" disabled={finance.busy === 'handoff'} onClick={() => void safe(finance.handoff)}>재무 분석 준비</button></>}
      {finance.run && <small>분석 준비 상태: {({ NOT_CONNECTED: '준비 중', READY: '시작 가능', QUEUED: '대기 중', RUNNING: '분석 중', COMPLETED: '완료', FAILED: '확인 필요' })[finance.run.status] ?? '상태 확인 필요'}{finance.run.stale ? ' · 업데이트 필요' : ''}</small>}
    </section>
    {finance.snapshot && <section className="finance-finalize" aria-live="polite"><div><p>재무 분석</p>
      <h2>{finance.analysis?.result?.report?.headline ?? '확정된 입력값으로 재무 분석과 보고서를 생성할 수 있습니다.'}</h2>
      <span>{analysisStatus(finance.analysis)}</span></div>
      <button type="button" disabled={finance.busy === 'analysis' || ['QUEUED', 'RUNNING'].includes(finance.analysis?.status)}
        onClick={() => void safe(finance.analyze)}>{['QUEUED', 'RUNNING'].includes(finance.analysis?.status) ? '재무 분석 실행 중…' : '재무 분석 및 보고서 생성'}</button>
    </section>}
    {finance.analysis?.stale && <p className="finance-warning" role="status">앞 단계의 입력이 바뀌어 재무 결과를 업데이트해야 합니다. 입력을 다시 저장해 주세요.</p>}
    {finance.analysis?.safeErrorCode && !finance.analysis?.result && <div className="finance-error" role="alert"><p>재무 보고서를 만들지 못했습니다.{finance.analysis.retryable ? ' 잠시 후 다시 시도해 주세요.' : ' 입력 내용을 확인해 주세요.'}</p><details><summary>기술 정보</summary><p>{finance.analysis.safeErrorCode}</p></details></div>}
    <AnalysisReport analysis={finance.analysis} />
    {finance.analysis?.result && <section className="finance-next-step" aria-label="다음 단계"><div><p>7. 트윈 패널 조사</p>
      <h2>재무 판단 다음으로 고객 선택 방향을 패널에서 확인하세요.</h2><span>확정 Concept의 비교안을 만들고 Twin 표본으로 방향과 측정 가능성을 확인합니다.</span></div>
      <Link to={`/app/projects/${projectId}/market-interview`}>다음 - 가상 시장 인터뷰</Link></section>}
  </ProjectWorkspace></FinanceRefreshContext.Provider>;
}

function analysisStatus(analysis) {
  if (!analysis || analysis.status === 'NOT_STARTED') return '아직 분석을 실행하지 않았습니다.';
  if (['QUEUED', 'RUNNING'].includes(analysis.status)) return '재무 분석을 진행하고 있습니다.';
  if (analysis.fallback) return '계산 완료 · AI 설명을 완료하지 못해 기본 보고서를 사용했습니다.';
  return ({ COMPLETED: '재무 분석을 완료했습니다.', FAILED: '재무 분석을 완료하지 못했습니다.' })[analysis.status] ?? '재무 분석 상태를 확인해 주세요.';
}

function calculateDraftCac(draft) {
  const values = [draft.totalMarketingCost, draft.totalSalesCost, draft.newCustomerCount].map(Number);
  if (!values.every(Number.isFinite) || values[2] <= 0 || values[0] < 0 || values[1] < 0) return null;
  return { amount: Math.round(((values[0] + values[1]) / values[2]) * 100) / 100, currency: 'KRW' };
}

function estimateLabel(item) {
  if (['QUEUED', 'RUNNING'].includes(item?.estimateStatus)) return '추천 생성 중';
  if (item?.estimateStatus === 'FAILED') return '추천을 만들지 못했습니다. 다시 요청할 수 있습니다.';
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
  return <div className="finance-estimate-controls"><button type="button" disabled={busy} onClick={() => void safe(() => decide(fieldKey, { action: 'ACCEPT', value: null }))}>AI 추천 채택</button>
    <button type="button" disabled={busy || editedValue == null} onClick={() => void safe(() => decide(fieldKey, { action: 'EDIT_AND_ACCEPT', value: editedValue }))}>입력값으로 수정 후 채택</button>
    <button type="button" disabled={busy} onClick={() => void safe(() => decide(fieldKey, { action: 'REJECT', value: null }))}>AI 추천 거절</button>
    <button type="button" disabled={busy} onClick={() => void safe(() => decide(fieldKey, { action: 'REQUEST_ALTERNATIVE', value: null }))}>다른 추천 요청</button></div>;
}

function FinancialSection({ eyebrow, title, fields, draft, change, sourceFields, missing, locked,
  assistance, finance, safe, editedValues }) {
  return <section className="finance-section"><SectionHeading eyebrow={eyebrow} title={title} />
    <div className="finance-form-grid project-form-layout">{fields.map(([key, label]) => <MoneyInput key={key} fieldKey={key} label={label}
      value={draft[key]} onChange={change} field={sourceFields[key]} missing={missing.has(key)} locked={locked}
      assistance={assistance?.[key]} finance={finance} safe={safe} editedValue={editedValues()[key]} />)}</div></section>;
}
function RefreshButton() {
  const refresh = useContext(FinanceRefreshContext);
  return refresh ? <button className="finance-container-refresh" type="button" onClick={refresh}>새로고침</button> : null;
}
function SectionHeading({ eyebrow, title }) { return <div className="finance-section__heading"><div><p>{eyebrow}</p><h2>{title}</h2></div>
  <div className="finance-section__actions"><span>KRW 기준</span><RefreshButton /></div></div>; }
function MoneyInput({ fieldKey, label, value, onChange, field, missing, locked, assistance, finance, safe, editedValue }) {
  const displayedValue = proposalPrimitive(value, field, assistance, 'amount');
  return <label data-missing={Boolean(missing)}><span>{label}</span><input type="number" min="0" disabled={locked || field?.readOnly}
    value={displayedValue} data-proposal-preview={displayedValue !== value || undefined}
    onChange={(event) => onChange(fieldKey, event.target.value)} /><SourceNote field={field} />
    {assistance && <EstimateControls fieldKey={fieldKey} item={assistance} field={field} locked={locked}
      busy={finance?.busy === `estimate:${fieldKey}`} generate={finance?.generateEstimate}
      decide={finance?.decideEstimate} editedValue={editedValue} safe={safe} />}</label>;
}

function proposalPrimitive(draftValue, field, assistance, key) {
  if (field?.readOnly || String(draftValue ?? '').trim() !== ''
      || assistance?.estimateStatus !== 'SUCCEEDED') return draftValue;
  const proposed = assistance?.proposalValue?.[key];
  return proposed == null ? draftValue : String(proposed);
}

function proposalTargets(draft, field, assistance) {
  const proposal = assistance?.proposalValue;
  if (field?.readOnly || assistance?.estimateStatus !== 'SUCCEEDED' || !Array.isArray(proposal?.years)
      || !draft.targetYears.every((value) => String(value ?? '').trim() === '')) return draft;
  return { ...draft, targetMetric: proposal.metric ?? draft.targetMetric,
    targetUnit: proposal.unit ?? draft.targetUnit,
    targetYears: [1, 2, 3].map((year) => String(proposal.years.find((item) => item.year === year)?.value ?? '')) };
}
function SourceNote({ field }) {
  if (field?.source === 'MARKET_ANALYSIS_ASSUMPTION') return <small data-source="inherited">시장 분석 가정 · 확인 후 저장 필요</small>;
  if (field?.source === 'BUSINESS_MODEL_HANDOFF') return <small data-source="inherited">수익 구조 분석에서 가져온 값</small>;
  if (field?.source === 'BUSINESS_MODEL_ASSUMPTION') return <small data-source="inherited">수익 구조의 가정 · 확인 후 저장 필요</small>;
  if (field?.source === 'CONCEPT_HYPOTHESIS') return <small data-source="inherited">사업안의 확정 가정 · 확인 후 저장 필요</small>;
  return <small data-source={field?.readOnly ? 'inherited' : 'input'}>{field?.readOnly ? '앞 단계에서 저장한 값' : '값이 없을 때 직접 입력'}</small>;
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
