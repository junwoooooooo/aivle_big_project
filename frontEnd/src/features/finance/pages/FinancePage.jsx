import { createContext, useContext, useMemo, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { getUserErrorMessage } from '../../../shared/api/apiError.js';
import useFinance from '../hooks/useFinance.js';
import {
  CAC_FIELDS, CONDITIONAL_FIELDS, FIXED_COST_FIELDS, INITIAL_INVESTMENT_FIELDS, REVENUE_MODELS, REVENUE_MONEY_FIELDS, TARGET_METRICS,
  createFinancialDraft, financialValuesFromDraft, formatMoney,
} from '../model/financeModel.js';
import '../styles/finance.css';

const FinanceRefreshContext = createContext(null);
const EMPTY_ESTIMATE = Object.freeze({ estimateStatus: 'NONE', proposalValue: null });

export default function FinancePage() {
  const { projectId } = useParams();
  const finance = useFinance(projectId);
  if (finance.loading) return <section className="finance-state" aria-busy="true">재무 입력을 불러오고 있습니다.</section>;
  if (!finance.preparation) return <section className="finance-state"><h1>재무 분석 준비</h1>
    <p role="alert">{getUserErrorMessage(finance.error)}</p>
    <Link to={`/app/projects/${projectId}/business-model`}>BM 분석을 완료하고 재무 분석 시작</Link></section>;
  return <FinanceWorkspace key={`${finance.preparation.preparationId}:${finance.preparation.revision}`} projectId={projectId} finance={finance} />;
}

// Retained for future demo-mode routing; production FinancePage does not render it.
// eslint-disable-next-line no-unused-vars
function FinanceDemo({ projectId, finance }) {
  const analysis = finance.analysis;
  return <section className="finance-state"><h1>재무 분석 테스트</h1>
    <p role="alert">{finance.error ? getUserErrorMessage(finance.error) : '기술·운영 Snapshot이 없습니다.'}</p>
    <p>가짜 기술·운영 및 재무값만 사용하며 프로젝트 DB와 상위 단계는 변경하지 않습니다.</p>
    <button type="button" disabled={finance.busy === 'demo'} onClick={() => void finance.demo()}>
      {finance.busy === 'demo' ? '테스트 분석 실행 중...' : '가짜 데이터로 재무 분석 테스트'}
    </button>
    <Link to={`/app/projects/${projectId}/tech-ops`}>기술·운영 입력으로 이동</Link>
    {analysis && <AnalysisReport analysis={analysis} />}
  </section>;
}

function AnalysisReport({ analysis }) {
  const base = analysis.calculation?.scenarios?.find((item) => item.code === 'BASE') ?? analysis.calculation?.scenarios?.[0];
  return <section className="finance-section finance-analysis-report" aria-labelledby="finance-test-result-title">
    <SectionHeading eyebrow="AI Financial Report" title="AI 기반 사업 타당성 및 재무 경제성 분석 최종 보고서" />
    <h2 id="finance-test-result-title">{analysis.report?.headline}</h2>
    <div className="finance-source__grid">
      <article><span>누적 매출</span><strong>{formatNumber(base?.totalRevenue)} KRW</strong></article>
      <article><span>누적 영업이익</span><strong>{formatNumber(base?.totalOperatingProfit)} KRW</strong></article>
      <article><span>손실 확률</span><strong>{analysis.monteCarlo?.lossProbabilityPercent}%</strong></article>
      <article><span>P50 영업이익</span><strong>{formatNumber(analysis.monteCarlo?.profitP50)} KRW</strong></article>
    </div>
    <h3>1. 구조화된 3개년 추정 손익계산서</h3>
    <div className="finance-table-wrap"><table><thead><tr><th>구분 (KRW)</th>{(analysis.annualProjections ?? []).map((row) => <th key={row.year}>{row.year}년 차</th>)}</tr></thead>
      <tbody>{[
        ['매출액', 'revenue'], ['매출원가', 'variableCost'], ['매출총이익', 'grossProfit'], ['판매비와관리비', 'sellingGeneralAdministrative'],
        ['영업이익', 'operatingProfit'], ['영업외손익', 'nonOperatingIncome'], ['법인세', 'corporateTax'], ['당기순이익', 'netIncome'],
      ].map(([label, key]) => <tr key={key}><th>{label}</th>{(analysis.annualProjections ?? []).map((row) => <td key={row.year}>{formatNumber(row[key])}</td>)}</tr>)}
      <tr><th>영업이익률</th>{(analysis.annualProjections ?? []).map((row) => <td key={row.year}>{row.operatingMarginPercent}%</td>)}</tr></tbody></table></div>
    <h3>2. 현금 흐름 및 손익분기점(BEP)</h3>
    <p>기준 시나리오 BEP: <strong>{base?.breakEvenMonth ? `출시 후 ${base.breakEvenMonth}개월 차` : '분석 기간 내 미도달'}</strong> · 필요 운전자금: <strong>{formatNumber(base?.requiredWorkingCapital)} KRW</strong></p>
    <FinancialLineChart title="월별 손익 및 손익분기점" subtitle="매출과 영업이익이 0원 기준선을 넘는 시점을 확인합니다."
      series={[{ name: '매출', color: '#245fc0', values: (analysis.cashFlowChart ?? []).map((row) => row.revenue) },
        { name: '영업이익', color: '#e05a47', values: (analysis.cashFlowChart ?? []).map((row) => row.operatingProfit) }]} />
    <FinancialLineChart title="누적 현금흐름" subtitle="0원 기준선을 넘으면 초기 투자금을 회수한 상태입니다."
      series={[{ name: '누적 현금흐름', color: '#16826c', values: (analysis.cashFlowChart ?? []).map((row) => row.cumulativeCashFlow) }]} />
    <details><summary>월별 매출·영업이익·누적 현금흐름 보기</summary><div className="finance-table-wrap"><table><thead><tr><th>월</th><th>매출</th><th>영업이익</th><th>누적 현금흐름</th></tr></thead><tbody>
      {(analysis.cashFlowChart ?? []).map((row) => <tr key={row.month}><td>{row.month}</td><td>{formatNumber(row.revenue)}</td><td>{formatNumber(row.operatingProfit)}</td><td>{formatNumber(row.cumulativeCashFlow)}</td></tr>)}</tbody></table></div></details>
    <h3>3. AI 시나리오 스트레스 테스트 및 몬테카를로</h3>
    <p>시뮬레이션 {analysis.monteCarlo?.simulations?.toLocaleString()}회 · P10 {formatNumber(analysis.monteCarlo?.profitP10)} KRW · P50 {formatNumber(analysis.monteCarlo?.profitP50)} KRW · P90 {formatNumber(analysis.monteCarlo?.profitP90)} KRW · 투자금 회수 확률 {analysis.monteCarlo?.paybackProbabilityPercent}%</p>
    <div className="finance-source__grid">{(analysis.stressScenarios ?? []).map((row) => <article key={row.code}><span>{row.label}</span><strong>BEP {row.breakEvenMonth ?? '미도달'}개월</strong><small>영업이익 {formatNumber(row.totalOperatingProfit)} KRW · 필요자금 {formatNumber(row.requiredWorkingCapital)} KRW</small></article>)}</div>
    <FinancialLineChart title="시나리오별 누적 현금흐름" subtitle="보수·기준·낙관 시나리오의 현금 여력과 회수 시점을 비교합니다."
      series={(analysis.stressScenarios ?? []).map((row, index) => ({ name: row.label, color: ['#e05a47', '#245fc0', '#16826c'][index % 3], values: (row.monthlyCashFlow ?? []).map((point) => point.cumulativeCashFlow) }))} />
    <h3>4. AI 핵심 제언</h3>
    <ul>{(analysis.report?.findings ?? []).map((item) => <li key={`finding-${item}`}>{item}</li>)}</ul>
    <h4>주의할 리스크</h4><ul>{(analysis.report?.cautions ?? []).map((item) => <li key={`caution-${item}`}>{item}</li>)}</ul>
    <h4>성공 확률 극대화 전략</h4><ul>{(analysis.report?.recommendedActions ?? []).map((item) => <li key={`action-${item}`}>{item}</li>)}</ul>
    <small>{analysis.report?.disclaimer}</small>
  </section>;
}

function formatNumber(value) { return new Intl.NumberFormat('ko-KR').format(Number(value ?? 0)); }

function calculateDraftCac(draft) {
  const marketing = Number(draft.totalMarketingCost);
  const sales = Number(draft.totalSalesCost);
  const customers = Number(draft.newCustomerCount);
  if (![marketing, sales, customers].every(Number.isFinite) || customers <= 0 || marketing < 0 || sales < 0) return null;
  return { amount: Math.round(((marketing + sales) / customers) * 100) / 100, currency: 'KRW' };
}

function FinancialLineChart({ title, subtitle, series }) {
  const width = 880; const height = 280; const left = 64; const right = 24; const top = 24; const bottom = 42;
  const allValues = series.flatMap((item) => item.values).map((value) => Number(value ?? 0));
  const min = Math.min(0, ...allValues); const max = Math.max(0, ...allValues); const range = max - min || 1;
  const pointCount = Math.max(...series.map((item) => item.values.length), 1);
  const x = (index) => left + (index * (width - left - right)) / Math.max(pointCount - 1, 1);
  const y = (value) => top + ((max - Number(value ?? 0)) * (height - top - bottom)) / range;
  const zeroY = y(0);
  const points = (values) => values.map((value, index) => `${x(index)},${y(value)}`).join(' ');
  const tickValues = [max, max - range / 2, min];
  return <figure className="finance-line-chart"><figcaption><strong>{title}</strong><span>{subtitle}</span></figcaption>
    <svg viewBox={`0 0 ${width} ${height}`} role="img" aria-label={title}>
      {tickValues.map((value, index) => <g key={index}><line x1={left} x2={width - right} y1={y(value)} y2={y(value)} className="finance-line-chart__grid" />
        <text x={left - 8} y={y(value) + 4} textAnchor="end" className="finance-line-chart__axis">{formatCompact(value)}</text></g>)}
      <line x1={left} x2={width - right} y1={zeroY} y2={zeroY} className="finance-line-chart__zero" />
      {series.map((item) => <polyline key={item.name} points={points(item.values)} fill="none" stroke={item.color} strokeWidth="3" strokeLinejoin="round" strokeLinecap="round" />)}
      <text x={left} y={height - 14} className="finance-line-chart__axis">1개월</text>
      <text x={width - right} y={height - 14} textAnchor="end" className="finance-line-chart__axis">{pointCount}개월</text>
    </svg>
    <div className="finance-line-chart__legend">{series.map((item) => <span key={item.name}><i style={{ backgroundColor: item.color }} />{item.name}</span>)}</div>
  </figure>;
}

function formatCompact(value) {
  const number = Number(value ?? 0); const absolute = Math.abs(number);
  if (absolute >= 100000000) return `${(number / 100000000).toFixed(1)}억`;
  if (absolute >= 10000) return `${(number / 10000).toFixed(0)}만`;
  return new Intl.NumberFormat('ko-KR', { maximumFractionDigits: 0 }).format(number);
}

function FinanceWorkspace({ projectId, finance }) {
  const preparation = finance.preparation;
  const fields = useMemo(() => preparation.financialFields ?? {}, [preparation.financialFields]);
  const [draftState, setDraft] = useState(() => applyAiProposals(
    createFinancialDraft(fields), fields, preparation.assistance,
  ));
  const draft = useMemo(() => applyAiProposals(draftState, fields, preparation.assistance),
    [draftState, fields, preparation.assistance]);
  const locked = Boolean(preparation.inputSnapshotId);
  const missing = useMemo(() => new Set(preparation.missingRequiredInputs ?? []), [preparation.missingRequiredInputs]);
  const safe = async (action) => { try { await action(); } catch { /* hook이 사용자용 오류 상태를 제공한다. */ } };
  const change = (key, value) => setDraft((current) => ({ ...current, [key]: value }));
  const references = preparation.upstreamReferences ?? {};
  const editedValues = () => financialValuesFromDraft(draft, fields);
  const liveCac = calculateDraftCac(draft);
  const revenuePriceFields = draft.revenueModel === 'ONE_TIME' ? REVENUE_MONEY_FIELDS.filter(([key]) => key === 'unitPrice')
    : draft.revenueModel === 'SUBSCRIPTION' ? REVENUE_MONEY_FIELDS.filter(([key]) => key === 'monthlySubscriptionPrice')
      : REVENUE_MONEY_FIELDS;

  const refreshContainer = () => void finance.refresh({ preserveView: true });
  return <FinanceRefreshContext.Provider value={refreshContainer}><main className="finance-page">
    <header className="finance-heading"><div><p>7. 재무 분석</p><h1>{locked ? '재무 분석 입력값이 확정되었습니다.' : '재무 분석 입력값을 준비하세요.'}</h1>
      <span>기술·운영 분석에서 전달된 값을 확인하고, 부족한 항목만 입력해 재무 분석 Snapshot을 만듭니다.</span></div>
      <strong className="finance-heading__status">{locked ? '입력 확정' : preparation.readyToFinalize ? '확정 준비' : `${preparation.missingRequiredInputs.length}개 입력 필요`}</strong></header>
    {finance.error && <p className="finance-error" role="alert">{getUserErrorMessage(finance.error)}</p>}

    <section className="finance-source" aria-labelledby="finance-source-title"><div><p>시장·BM 분석에서 가져옴</p>
      <h2 id="finance-source-title">시장 규모·성장률·가격 가설 및 BM 근거</h2></div>
      <RefreshButton />
      <div className="finance-source__grid">
        <Reference label="TAM" value={references.marketAnalysis?.tam} />
        <Reference label="SAM" value={references.marketAnalysis?.sam} />
        <Reference label="시장 성장률" value={references.marketAnalysis?.growth} />
        <Reference label="시장 가격 가설" value={references.marketAnalysis?.price} />
      </div><p className="finance-source__ai-note">AI는 시장 규모·성장률·가격 가설과 BM 결과를 참고해 비용 또는 3개년 목표의 초안을 제안합니다. 추천값은 자동 저장되지 않으며, 검토 후 저장해야 합니다.</p>
      <small>BM Run · {preparation.sourceMarketResearchRunId ?? '연결 대기'} · 가격 가설과 가정은 사용자 확인 전까지 확정값이 아닙니다.</small></section>

    <FinancialSection eyebrow="고정운영비" title="연간 고정비 세부항목" fields={FIXED_COST_FIELDS}
      draft={draft} change={change} sourceFields={fields} missing={missing} locked={locked}
      assistance={preparation.assistance}
      finance={finance} safe={safe} editedValues={editedValues} />
    <FinancialSection eyebrow="초기투자" title="초기 투자 세부항목" fields={INITIAL_INVESTMENT_FIELDS}
      draft={draft} change={change} sourceFields={fields} missing={missing} locked={locked}
      assistance={preparation.assistance}
      finance={finance} safe={safe} editedValues={editedValues} />

    <section className="finance-section" aria-labelledby="finance-targets-title"><SectionHeading eyebrow="3개년 목표" title="사업 유형에 맞는 목표 지표" />
      <div className="finance-form-grid finance-targets">
        <label><span>목표 지표</span><select disabled={locked || fields.threeYearTargets?.readOnly} value={draft.targetMetric}
          onChange={(event) => change('targetMetric', event.target.value)}>{TARGET_METRICS.map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label>
        <label><span>단위</span><input disabled={locked || fields.threeYearTargets?.readOnly} value={draft.targetUnit}
          onChange={(event) => change('targetUnit', event.target.value)} placeholder="명, 건, 개" /></label>
        {[1, 2, 3].map((year) => <label key={year} data-missing={missing.has('threeYearTargets')}><span>{year}년차 목표</span>
          <input type="number" min="0" disabled={locked || fields.threeYearTargets?.readOnly} value={draft.targetYears[year - 1]}
            onChange={(event) => { const values = [...draft.targetYears]; values[year - 1] = event.target.value; change('targetYears', values); }} /></label>)}
      </div><SourceNote field={fields.threeYearTargets} />
      {canShowInlineEstimate(fields.threeYearTargets, preparation.assistance?.threeYearTargets ?? EMPTY_ESTIMATE, draft.targetYears.join('')) && <EstimateControls fieldKey="threeYearTargets" item={preparation.assistance?.threeYearTargets ?? EMPTY_ESTIMATE} field={fields.threeYearTargets}
        locked={locked} busy={finance.busy === 'estimate:threeYearTargets'} generate={finance.generateEstimate}
        decide={finance.decideEstimate} editedValue={editedValues().threeYearTargets} safe={safe} />}</section>

    <section className="finance-section"><SectionHeading eyebrow="수익 모델" title="가격 및 반복 매출 가정" />
      <div className="finance-form-grid">
        <label data-missing={missing.has('revenueModel')}><span>수익 모델</span><select disabled={locked || fields.revenueModel?.readOnly}
          value={draft.revenueModel} onChange={(event) => change('revenueModel', event.target.value)}>
          {REVENUE_MODELS.map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select><SourceNote field={fields.revenueModel} /></label>
        {revenuePriceFields.map(([key, label]) => <MoneyInput key={key} fieldKey={key} label={label} value={draft[key]}
          onChange={change} field={fields[key]} missing={missing.has(key)} locked={locked} assistance={preparation.assistance?.[key]}
          finance={finance} safe={safe} editedValue={editedValues()[key]} />)}
        <label data-missing={missing.has('monthlyChurnRate')}><span>월 이탈률 (%)</span><input type="number" min="0" max="100"
          disabled={locked || fields.monthlyChurnRate?.readOnly} value={draft.monthlyChurnRate}
          onChange={(event) => change('monthlyChurnRate', event.target.value)} /><SourceNote field={fields.monthlyChurnRate} />
          {canShowInlineEstimate(fields.monthlyChurnRate, preparation.assistance?.monthlyChurnRate, draft.monthlyChurnRate) && <EstimateControls
            fieldKey="monthlyChurnRate" item={preparation.assistance?.monthlyChurnRate} field={fields.monthlyChurnRate}
            locked={locked} busy={finance.busy === 'estimate:monthlyChurnRate'} generate={finance.generateEstimate}
            decide={finance.decideEstimate} editedValue={editedValues().monthlyChurnRate} safe={safe} />}</label>
      </div>
    </section>

    <section className="finance-section" aria-labelledby="finance-cac-title"><SectionHeading eyebrow="CAC" title="고객 획득 비용 구성값" />
      <p className="finance-note">CAC를 직접 계산하지 마세요. 비용과 신규 고객 수를 입력하면 시스템이 계산합니다.</p>
      <div className="finance-form-grid">{CAC_FIELDS.map(([key, label]) => <MoneyInput key={key} fieldKey={key} label={label}
        value={draft[key]} onChange={change} field={fields[key]} missing={missing.has(key)} locked={locked} assistance={preparation.assistance?.[key]}
        finance={finance} safe={safe} editedValue={editedValues()[key]} />)}
        <label data-missing={missing.has('newCustomerCount')}><span>신규 고객 수</span><input type="number" min="1"
          disabled={locked || fields.newCustomerCount?.readOnly} value={draft.newCustomerCount}
          onChange={(event) => change('newCustomerCount', event.target.value)} /><SourceNote field={fields.newCustomerCount} />
          {canShowInlineEstimate(fields.newCustomerCount, preparation.assistance?.newCustomerCount ?? { estimateStatus: 'NONE' }, draft.newCustomerCount) && <EstimateControls
            fieldKey="newCustomerCount" item={preparation.assistance?.newCustomerCount ?? { estimateStatus: 'NONE' }} field={fields.newCustomerCount}
            locked={locked} busy={finance.busy === 'estimate:newCustomerCount'} generate={finance.generateEstimate}
            decide={finance.decideEstimate} editedValue={editedValues().newCustomerCount} safe={safe} />}</label>
        <div className="finance-cac-result"><span>시스템 계산 CAC</span><strong>{formatMoney(liveCac ?? preparation.calculatedCac)}</strong>
          <small>(총 마케팅비 + 총 영업비) ÷ 신규 고객 수</small></div>
      </div></section>

    <section className="finance-section"><details><summary>조건부 단위원가 입력</summary>
      <p className="finance-note">사업 구조나 외부 모듈 계약에 필요한 항목만 입력하세요. 모든 사업에 강제되지 않습니다.</p>
      <div className="finance-form-grid">{CONDITIONAL_FIELDS.map(([key, label]) => <MoneyInput key={key} fieldKey={key} label={label}
        value={draft[key]} onChange={change} field={fields[key]} locked={locked} assistance={preparation.assistance?.[key]}
        finance={finance} safe={safe} editedValue={editedValues()[key]} />)}</div></details></section>

    <section className="finance-assistance" aria-labelledby="finance-assistance-title"><div><p>설명·예시·AI 추정</p><h2 id="finance-assistance-title">입력 도움말</h2>
      <p className="finance-ai-guide">AI는 시장 규모·성장률·가격 가설과 BM 결과를 참고해 비용 또는 3개년 목표의 초안을 제안합니다. 추천값은 자동 저장되지 않으며, 검토 후 채택해야 합니다.</p></div>
      <div className="finance-ai-scope"><strong>AI 추천 대상</strong><span>연간 고정비(인건비·임차관리비·인프라비), 초기투자(개발·설비·특허), 마케팅·영업비, 조건부 단위원가, 가격·3개년 목표</span><small>신규 고객 수는 CAC 계산의 기준값이므로 사용자가 직접 입력합니다.</small></div>
      <div>{Object.entries(preparation.assistance ?? {}).map(([key, item]) => <article key={key}><strong>{fieldLabel(key)}</strong><span>{item.explanation}</span>
        {item.example && <span>{item.example}</span>}<small>{estimateLabel(item)}</small>
        {item.proposalValue != null && fields[key] && <Recommendation item={item} />}
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
      <h2>{finance.analysis?.report?.headline ?? '확정된 입력값으로 재무 분석을 실행할 수 있습니다.'}</h2>
      {finance.analysis?.monteCarlo && <span>몬테카를로 손실 확률: {finance.analysis.monteCarlo.lossProbabilityPercent}%</span>}</div>
      <button type="button" disabled={finance.busy === 'analysis'} onClick={() => void safe(finance.analyze)}>
        {finance.busy === 'analysis' ? '재무 분석 실행 중...' : '재무 분석 및 보고서 생성'}
      </button>
    </section>}
    {finance.analysis && <><AnalysisReport analysis={finance.analysis} />
      <section className="finance-next-step" aria-label="다음 단계"><div><p>8. 패널 조사</p>
        <h2>재무 가정을 패널 조사로 검증하세요.</h2><span>가격 수용도와 고객 반응을 확인해 재무 분석의 가정을 보완할 수 있습니다.</span></div>
        <Link to={`/app/projects/${projectId}/panel-survey`}>다음 - 패널조사</Link></section></>}
  </main></FinanceRefreshContext.Provider>;
}

function estimateLabel(item) {
  if (['QUEUED', 'RUNNING'].includes(item?.estimateStatus)) return '추천 생성 중';
  if (item?.estimateStatus === 'FAILED') return `추천 생성 실패${item?.safeError ? ` · ${item.safeError}` : ''} — 다시 요청할 수 있습니다.`;
  if (item?.estimateStatus === 'ACCEPTED' || ['ACCEPTED', 'USER_EDITED_ACCEPTED'].includes(item?.decision)) return '채택됨';
  if (item?.proposalValue != null && item?.estimateStatus === 'SUCCEEDED') return 'AI 추천';
  return '추천 없음';
}

function EstimateControls({ fieldKey, item, field, locked, busy, generate, decide, editedValue, safe }) {
  void editedValue;
  if (locked || field?.readOnly || item?.estimateStatus === 'ACCEPTED') return null;
  const pending = ['QUEUED', 'RUNNING'].includes(item?.estimateStatus);
  const proposed = item?.proposalValue != null && item?.estimateStatus === 'SUCCEEDED';
  if (!proposed) return <span className="finance-estimate-controls"><button type="button" disabled={busy || pending}
    onClick={() => void safe(() => generate(fieldKey))}>AI 추천 받기</button></span>;
  return <span className="finance-estimate-controls"><button type="button" disabled={busy}
    onClick={() => void safe(() => decide(fieldKey, { action: 'REQUEST_ALTERNATIVE', value: null }))}>다른 추천 요청</button></span>;
}

function FinancialSection({ eyebrow, title, fields, draft, change, sourceFields, missing, locked, assistance, finance, safe, editedValues }) {
  return <section className="finance-section"><SectionHeading eyebrow={eyebrow} title={title} />
    <div className="finance-form-grid">{fields.map(([key, label]) => <MoneyInput key={key} fieldKey={key} label={label}
      value={draft[key]} onChange={change} field={sourceFields[key]} missing={missing.has(key)} locked={locked} assistance={assistance?.[key]}
      finance={finance} safe={safe} editedValue={editedValues()[key]} />)}</div></section>;
}
function canShowInlineEstimate(field, assistance, draftValue) {
  void draftValue;
  if (field?.readOnly || assistance?.estimateStatus === 'ACCEPTED') return false;
  if (assistance?.estimateStatus === 'SUCCEEDED') return true;
  return !['QUEUED', 'RUNNING'].includes(assistance?.estimateStatus);
}
function RefreshButton() {
  const refresh = useContext(FinanceRefreshContext);
  if (!refresh) return null;
  return <button className="finance-container-refresh" type="button" onClick={refresh}>새로고침</button>;
}
function SectionHeading({ eyebrow, title }) { return <div className="finance-section__heading"><div><p>{eyebrow}</p><h2>{title}</h2></div><div><span>KRW 기준</span><RefreshButton /></div></div>; }
function MoneyInput({ fieldKey, label, value, onChange, field, missing, locked, assistance = EMPTY_ESTIMATE, finance, safe, editedValue }) {
  const proposedAmount = assistance?.proposalValue?.amount;
  const displayedValue = isEmptyOrZeroDraft(value) && proposedAmount != null ? String(proposedAmount) : value;
  return <label data-missing={Boolean(missing)}><span>{label}</span><input type="number" min="0" disabled={locked || field?.readOnly}
    value={displayedValue} onChange={(event) => onChange(fieldKey, event.target.value)} /><SourceNote field={field} />
    {assistance && canShowInlineEstimate(field, assistance, value) && <EstimateControls fieldKey={fieldKey} item={assistance} field={field} locked={locked}
      busy={finance?.busy === `estimate:${fieldKey}`} generate={finance?.generateEstimate}
      decide={finance?.decideEstimate} editedValue={editedValue} safe={safe} />}</label>;
}
function fieldLabel(key) {
  return ({ annualFixedLaborCost: '연간 고정 인건비', annualFixedRentAndManagementCost: '연간 임차·관리비',
    annualFixedInfrastructureCost: '연간 인프라비', initialDevelopmentAndRnDCost: '초기 개발·R&D 비용',
    initialEquipmentAndInfrastructureCost: '초기 설비·인프라 비용', initialPatentAndLicensingCost: '초기 특허·라이선스 비용',
    totalMarketingCost: '총 마케팅비', totalSalesCost: '총 영업비', threeYearTargets: '3개년 목표',
    unitVariableCost: '단위 변동비', paymentFee: '결제 수수료', partnerPayout: '파트너 지급액', shippingCost: '배송비',
    customerIncrementalInfraCost: '고객 증가분 인프라비', unitPrice: '제품 단가', monthlySubscriptionPrice: '월 구독 가격',
    monthlyChurnRate: '월 이탈률', revenueModel: '수익 모델' })[key] ?? key;
}
function applyAiProposals(draft, fields, assistance = {}) {
  let changed = false;
  const next = { ...draft };
  for (const [key, item] of Object.entries(assistance)) {
    if (item?.proposalValue == null) continue;
    const proposal = item.proposalValue;
    if (proposal.amount != null && canApplyProposal(next[key], fields[key]?.value?.amount)) {
      next[key] = String(proposal.amount); changed = true;
    }
    if (key === 'monthlyChurnRate' && proposal.percent != null && canApplyProposal(next.monthlyChurnRate, fields.monthlyChurnRate?.value)) {
      next.monthlyChurnRate = String(proposal.percent); changed = true;
    }
    if (key === 'newCustomerCount' && proposal.count != null && canApplyProposal(next.newCustomerCount, fields.newCustomerCount?.value)) {
      next.newCustomerCount = String(proposal.count); changed = true;
    }
    if (key === 'threeYearTargets' && proposal.years && next.targetYears.every((value) => String(value ?? '').trim() === '')) {
      next.targetMetric = proposal.metric; next.targetUnit = proposal.unit;
      next.targetYears = [1, 2, 3].map((year) => String(proposal.years.find((item) => item.year === year)?.value ?? ''));
      changed = true;
    }
  }
  return changed ? next : draft;
}
function canApplyProposal(draftValue, storedValue) {
  return isEmptyOrZeroDraft(draftValue) || String(draftValue ?? '') === String(storedValue ?? '');
}
function isEmptyOrZeroDraft(value) {
  return String(value ?? '').trim() === '' || Number(value) === 0;
}
function SourceNote({ field }) {
  if (field?.source === 'MARKET_ANALYSIS_ASSUMPTION') return <small data-source="inherited">시장 분석 가설 · 확인 후 저장 필요</small>;
  return <small data-source={field?.readOnly ? 'inherited' : 'input'}>{field?.readOnly ? '기술·운영 단계에서 가져옴' : '없을 때만 사용자 입력'}</small>;
}
function Recommendation({ item }) {
  const value = item.proposalValue?.amount != null
    ? `${new Intl.NumberFormat('ko-KR').format(item.proposalValue.amount)} ${item.proposalValue.currency ?? 'KRW'}`
    : item.proposalValue?.percent != null
      ? `${item.proposalValue.percent}%`
    : item.proposalValue?.count != null
      ? `${new Intl.NumberFormat('ko-KR').format(item.proposalValue.count)}명`
    : item.proposalValue?.years
      ? item.proposalValue.years.map((year) => `${year.year}년차 ${new Intl.NumberFormat('ko-KR').format(year.value)}`).join(' · ')
      : '추천값 확인 필요';
  return <div className="finance-recommendation"><strong>추천값: {value}</strong>
    {item.explanation && <span>산정 근거: {item.explanation}</span>}
    {item.assumptions?.length ? <small>가정: {item.assumptions.join(' · ')}</small> : null}
    {item.confidence && <small>신뢰도: {item.confidence}</small>}
  </div>;
}
function Reference({ label, value }) {
  const display = value?.amount != null ? formatMoney(value)
    : value?.value != null ? `${new Intl.NumberFormat('ko-KR').format(value.value)} ${value.unit ?? ''}`
      : value?.base != null ? `${new Intl.NumberFormat('ko-KR').format(value.base)} ${value.currency ?? 'KRW'}`
        : value == null ? '값 없음' : '근거·가정 보기';
  return <article><span>{label}</span><strong>{display}</strong></article>;
}
