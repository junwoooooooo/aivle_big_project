import { useMemo, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { getUserErrorMessage } from '../../../shared/api/apiError.js';
import useFinance from '../hooks/useFinance.js';
import {
  CAC_FIELDS, CONDITIONAL_FIELDS, FIXED_COST_FIELDS, INITIAL_INVESTMENT_FIELDS, TARGET_METRICS,
  createFinancialDraft, financialValuesFromDraft, formatMoney,
} from '../model/financeModel.js';
import '../styles/finance.css';

export default function FinancePage() {
  const { projectId } = useParams();
  const finance = useFinance(projectId);
  if (finance.loading) return <section className="finance-state" aria-busy="true">재무 입력을 불러오고 있습니다.</section>;
  if (!finance.preparation) return <section className="finance-state"><h1>재무 분석 준비</h1>
    <p role="alert">{getUserErrorMessage(finance.error)}</p>
    <Link to={`/app/projects/${projectId}/tech-ops`}>기술·운영 입력 Snapshot 확인</Link></section>;
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

  return <main className="finance-page">
    <header className="finance-heading"><p>7. 재무 분석</p><h1>이미 확인된 값은 이어받고, 부족한 값만 입력합니다</h1>
      <span>재무 계산 알고리즘이 아니라 외부 모듈에 전달할 불변 입력 Snapshot을 준비하는 화면입니다.</span></header>
    {finance.error && <p className="finance-error" role="alert">{getUserErrorMessage(finance.error)}</p>}

    <section className="finance-source" aria-labelledby="finance-source-title"><div><p>기술·운영 단계에서 가져옴</p>
      <h2 id="finance-source-title">TechOps 승계 기준</h2></div>
      <div className="finance-source__grid">
        <Reference label="고정운영비" value={references.fixedOperatingCost?.annualEquivalent ?? references.fixedOperatingCost?.value} />
        <Reference label="초기투자" value={references.initialInvestment?.value} />
        <Reference label="기존 3개년 목표" value={references.threeYearTargets?.value} />
      </div><small>TechOpsInputSnapshot · {preparation.sourceTechOpsSnapshotId}</small></section>

    <FinancialSection eyebrow="고정운영비" title="연간 고정비 세부항목" fields={FIXED_COST_FIELDS}
      draft={draft} change={change} sourceFields={fields} missing={missing} locked={locked} />
    <FinancialSection eyebrow="초기투자" title="초기 투자 세부항목" fields={INITIAL_INVESTMENT_FIELDS}
      draft={draft} change={change} sourceFields={fields} missing={missing} locked={locked} />

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

    <section className="finance-section" aria-labelledby="finance-cac-title"><SectionHeading eyebrow="CAC" title="고객 획득 비용 구성값" />
      <p className="finance-note">CAC를 직접 계산하지 마세요. 비용과 신규 고객 수를 입력하면 시스템이 계산합니다.</p>
      <div className="finance-form-grid">{CAC_FIELDS.map(([key, label]) => <MoneyInput key={key} fieldKey={key} label={label}
        value={draft[key]} onChange={change} field={fields[key]} missing={missing.has(key)} locked={locked} />)}
        <label data-missing={missing.has('newCustomerCount')}><span>신규 고객 수</span><input type="number" min="1"
          disabled={locked || fields.newCustomerCount?.readOnly} value={draft.newCustomerCount}
          onChange={(event) => change('newCustomerCount', event.target.value)} /><SourceNote field={fields.newCustomerCount} /></label>
        <div className="finance-cac-result"><span>시스템 계산 CAC</span><strong>{formatMoney(preparation.calculatedCac)}</strong>
          <small>(총 마케팅비 + 총 영업비) ÷ 신규 고객 수</small></div>
      </div></section>

    <section className="finance-section"><details><summary>조건부 단위원가 입력</summary>
      <p className="finance-note">사업 구조나 외부 모듈 계약에 필요한 항목만 입력하세요. 모든 사업에 강제되지 않습니다.</p>
      <div className="finance-form-grid">{CONDITIONAL_FIELDS.map(([key, label]) => <MoneyInput key={key} fieldKey={key} label={label}
        value={draft[key]} onChange={change} field={fields[key]} locked={locked} />)}</div></details></section>

    <section className="finance-assistance" aria-labelledby="finance-assistance-title"><div><p>설명·예시·AI 추정</p><h2 id="finance-assistance-title">입력 도움말</h2></div>
      <div>{Object.entries(preparation.assistance ?? {}).map(([key, item]) => <article key={key}><strong>{item.explanation}</strong>
        {item.example && <span>{item.example}</span>}<small>{estimateLabel(item)}</small>
        {item.proposalValue != null && fields[key] && <span>{JSON.stringify(item.proposalValue)} · {item.assumptions?.join(' · ')}</span>}
        {fields[key] && key !== 'newCustomerCount' && <EstimateControls fieldKey={key} item={item} field={fields[key]}
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
        : <button type="button" disabled={finance.busy === 'handoff'} onClick={() => void safe(finance.handoff)}>재무 분석 Handoff 준비</button>}
      {finance.run && <small>외부 연결 상태: {finance.run.status}{finance.run.stale ? ' · 입력 갱신 필요' : ''}</small>}
    </section>
  </main>;
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
    <button type="button" disabled={busy} onClick={() => void safe(() => decide(fieldKey, { action: 'REQUEST_ALTERNATIVE', value: null }))}>다른 추천 요청</button></div>;
}

function FinancialSection({ eyebrow, title, fields, draft, change, sourceFields, missing, locked }) {
  return <section className="finance-section"><SectionHeading eyebrow={eyebrow} title={title} />
    <div className="finance-form-grid">{fields.map(([key, label]) => <MoneyInput key={key} fieldKey={key} label={label}
      value={draft[key]} onChange={change} field={sourceFields[key]} missing={missing.has(key)} locked={locked} />)}</div></section>;
}
function SectionHeading({ eyebrow, title }) { return <div className="finance-section__heading"><div><p>{eyebrow}</p><h2>{title}</h2></div><span>KRW 기준</span></div>; }
function MoneyInput({ fieldKey, label, value, onChange, field, missing, locked }) {
  return <label data-missing={Boolean(missing)}><span>{label}</span><input type="number" min="0" disabled={locked || field?.readOnly}
    value={value} onChange={(event) => onChange(fieldKey, event.target.value)} /><SourceNote field={field} /></label>;
}
function SourceNote({ field }) {
  return <small data-source={field?.readOnly ? 'inherited' : 'input'}>{field?.readOnly ? '기술·운영 단계에서 가져옴' : '없을 때만 사용자 입력'}</small>;
}
function Reference({ label, value }) {
  const display = value?.amount != null ? formatMoney(value) : value == null ? '값 없음' : JSON.stringify(value);
  return <article><span>{label}</span><strong>{display}</strong></article>;
}
