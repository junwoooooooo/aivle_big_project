import { useMemo, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { getUserErrorMessage } from '../../../shared/api/apiError.js';
import { JobTimeline } from '../../../shared/async-events/index.js';
import { DECISION_FIELDS, createFactDraft, decisionComplete, displayValue, factsFromDraft, proposalDraft, proposalValue } from '../model/techOpsModel.js';
import useTechOps from '../hooks/useTechOps.js';
import { FileDropzone, ProjectSplitWorkspace, ProjectStageHeader, ProjectWorkspace } from '../../../shared/ui/index.js';
import '../styles/tech-ops.css';

const EVIDENCE_TYPES = [['QUOTE', '견적서'], ['BOM', 'BOM'], ['SUPPLIER', '공급사 정보'], ['SPECIFICATION', '사양서'], ['PILOT', '파일럿 자료']];

export default function TechOpsPage() {
  const { projectId } = useParams();
  const techOps = useTechOps(projectId);
  if (techOps.loading) return <section className="tech-ops-state" aria-busy="true">기술·운영 입력을 불러오고 있습니다.</section>;
  if (!techOps.preparation) return <section className="tech-ops-state"><h1>기술·운영 분석 준비</h1>
    <p role="alert">{getUserErrorMessage(techOps.error)}</p><Link to={`/app/projects/${projectId}/concepts/compare`}>컨셉 선택과 가설 결정 확인</Link></section>;
  return <TechOpsWorkspace key={`${techOps.preparation.preparationId}:${techOps.preparation.revision}`} techOps={techOps} />;
}

function TechOpsWorkspace({ techOps }) {
  const preparation = techOps.preparation;
  const [facts, setFacts] = useState(() => createFactDraft(preparation.requiredFacts));
  const [decisionDrafts, setDecisionDrafts] = useState(() => Object.fromEntries(DECISION_FIELDS.map(([key]) => [key,
    proposalDraft(key, preparation.proposalDecisions?.[key]?.finalValue ?? preparation.proposalDecisions?.[key]?.proposalValue)])));
  const [evidence, setEvidence] = useState({ evidenceType: 'QUOTE', file: null, description: '', inputKey: 0 });
  const product = preparation.requiredFacts?.productServiceSpecification;
  const locked = Boolean(preparation.inputSnapshotId);
  const missing = useMemo(() => new Set(preparation.missingRequiredInputs ?? []), [preparation.missingRequiredInputs]);
  const hasMissingProposal = DECISION_FIELDS.some(([key]) => preparation.proposalDecisions?.[key]?.proposalValue == null);
  const safe = async (action) => { try { await action(); } catch { /* hook가 안전한 오류 상태를 제공한다. */ } };

  return <ProjectWorkspace as="main" mode="compose" className="tech-ops-page">
    <ProjectStageHeader step={5} eyebrow="출시 준비" title="출시를 위한 기술·운영 조건을 정리하세요"
      description="서비스 운영에 필요한 핵심 사실과 보완 조건을 확인하고, 실제 근거 자료를 연결합니다." />
    {techOps.error && <p className="tech-ops-error" role="alert">{getUserErrorMessage(techOps.error)}</p>}
    {['QUEUED', 'RUNNING'].includes(preparation.proposalGenerationStatus) && <p role="status">
      {Object.values(preparation.proposalDecisions ?? {}).some((item) => item.pendingAlternativeTaskRunId)
        ? '새 제안 생성 중' : 'AI 운영 가설 생성 중'}
    </p>}
    {preparation.proposalGenerationStatus === 'FAILED' && <p role="alert">AI 제안 생성 실패 — 직접 입력하거나 다시 시도할 수 있습니다.
      {hasMissingProposal && <button type="button" disabled={techOps.busy === 'proposal-retry'} onClick={() => void safe(techOps.retryProposals)}>AI 제안 다시 시도</button>}</p>}

    <section className="tech-ops-source" aria-labelledby="tech-source-title"><div><p>선택한 사업안 · 사용자 확인 필요</p><h2 id="tech-source-title">제품·서비스 사양</h2></div>
      <strong>{displayValue(product?.value?.summary)}</strong>
      <span>{displayValue(product?.value?.features)}</span>
      <small>저장 전에는 수정할 수 있습니다.</small></section>

    <ProjectSplitWorkspace className="tech-ops-input-workspace" primary={<>
    <section className="tech-ops-section" aria-labelledby="tech-facts-title"><div className="tech-ops-section__heading"><div><p>프로젝트 정보</p><h2 id="tech-facts-title">분석 전 필수 입력</h2></div><span>{locked ? '입력 저장 완료' : '직접 입력'}</span></div>
      <div className="tech-ops-form-grid project-form-layout">
        <label className="wide"><span>제품·서비스 사양 요약</span><textarea disabled={locked} value={facts.productSummary} onChange={(event) => setFacts({ ...facts, productSummary: event.target.value })} /></label>
        <label className="wide"><span>핵심 기능</span><textarea disabled={locked} value={facts.productFeatures} onChange={(event) => setFacts({ ...facts, productFeatures: event.target.value })} placeholder="한 줄에 하나씩 입력" /></label>
        <label><span>목표 출시일</span><input type="date" disabled={locked} value={facts.targetLaunchDate} onChange={(event) => setFacts({ ...facts, targetLaunchDate: event.target.value })} /></label>
        <label className="wide"><span>보유 인력</span><textarea disabled={locked} value={facts.personnel} onChange={(event) => setFacts({ ...facts, personnel: event.target.value })} placeholder={'역할|인원|비고\n예: 백엔드 개발|2|내부 인력'} /><small>한 줄에 역할|인원|비고 형식으로 입력합니다. 인력이 없으면 현재 전담 인력 없음|0으로 명시합니다.</small></label>
        <label className="wide"><span>보유 자산·설비</span><textarea disabled={locked} value={facts.assets} onChange={(event) => setFacts({ ...facts, assets: event.target.value })} placeholder={'클라우드 계정\n테스트 장비'} /><small>한 줄에 하나씩 입력합니다. 없으면 “현재 보유 자산 없음”으로 명시합니다.</small></label>
        <label><span>월 고정운영비(KRW)</span><input type="number" min="0" disabled={locked} value={facts.fixedOperatingCost} onChange={(event) => setFacts({ ...facts, fixedOperatingCost: event.target.value })} /></label>
        <label><span>초기투자금(KRW)</span><input type="number" min="0" disabled={locked} value={facts.initialInvestment} onChange={(event) => setFacts({ ...facts, initialInvestment: event.target.value })} /></label>
        <label><span>3개년 목표 지표</span><select disabled={locked} value={facts.targetMetric} onChange={(event) => setFacts({ ...facts, targetMetric: event.target.value })}><option value="salesVolume">판매량</option><option value="customerCount">고객 수</option><option value="subscriberCount">구독자 수</option><option value="transactionCount">거래 수</option></select></label>
        <label><span>목표 단위</span><input disabled={locked} value={facts.targetUnit} onChange={(event) => setFacts({ ...facts, targetUnit: event.target.value })} placeholder="명 또는 건" /></label>
        {[1, 2, 3].map((year) => <label key={year}><span>{year}년차 목표</span><input type="number" min="0" disabled={locked} value={facts.targets[year - 1]} onChange={(event) => { const targets = [...facts.targets]; targets[year - 1] = event.target.value; setFacts({ ...facts, targets }); }} /></label>)}
      </div>
      {!locked && <button className="tech-ops-primary" type="button" disabled={techOps.busy === 'facts'} onClick={() => void safe(() => techOps.saveFacts(factsFromDraft(facts)))}>사용자 사실 저장</button>}
    </section>

    </>} secondary={<>
    <section className="tech-ops-section" aria-labelledby="tech-decisions-title"><div className="tech-ops-section__heading"><div><p>제안과 결정</p><h2 id="tech-decisions-title">분석 전 확정할 운영 가설</h2></div><span>사용자 결정 필수</span></div>
      <div className="tech-ops-decisions">{DECISION_FIELDS.map(([key, label]) => {
        const item = preparation.proposalDecisions?.[key] ?? {};
        const pending = Boolean(item.pendingAlternativeTaskRunId);
        return <article key={key} data-missing={missing.has(key)}><div><h3>{label}</h3><span>{decisionComplete(item) ? '확정됨' : item.alternativeRequested ? '다른 제안 요청됨' : '확인 필요'}</span></div>
          <p><b>{item.source === 'CONCEPT_GENERATED' || item.source === 'ANALYSIS_RESULT' ? '상위 단계 제안' : 'AI 제안 영역'}</b> {displayValue(item.proposalValue)}</p>
          <textarea disabled={locked} value={decisionDrafts[key] ?? ''} onChange={(event) => setDecisionDrafts({ ...decisionDrafts, [key]: event.target.value })}
            placeholder={key === 'expectedMonthlyThroughputOrSales' ? '수량|단위 (예: 1000|건)' : '확정할 값을 입력하세요'} />
          {!locked && <div className="tech-ops-actions">{item.proposalValue != null && <button type="button" disabled={pending} onClick={() => void safe(() => techOps.decide(key, { action: 'ACCEPT', value: null }))}>제안 채택</button>}
            <button type="button" onClick={() => void safe(() => techOps.decide(key, { action: 'EDIT_AND_ACCEPT', value: proposalValue(key, decisionDrafts[key] ?? '') }))}>수정 후 확정</button>
            <button type="button" disabled={pending || item.proposalValue == null} onClick={() => void safe(() => techOps.decide(key, { action: 'REJECT_AND_REQUEST_ALTERNATIVE', value: null }))}>다른 제안 요청</button></div>}
        </article>;
      })}</div>
      <p className="tech-ops-note">다른 제안을 요청하면 현재 값은 유지되고 새 제안이 별도로 준비됩니다.</p>
    </section>

    <section className="tech-ops-section" aria-labelledby="tech-evidence-title"><div className="tech-ops-section__heading"><div><p>선택 사항</p><h2 id="tech-evidence-title">실제 근거 자료</h2></div><span>직접 등록한 자료</span></div>
      <p className="tech-ops-note">견적서·부품 목록·공급사·사양서·시험 운영 자료를 등록할 수 있습니다. AI 제안은 근거 자료로 저장하지 않습니다.</p>
      {!locked && <div className="tech-ops-evidence-form tech-ops-evidence-form--dropzone"><select aria-label="자료 유형" value={evidence.evidenceType} onChange={(event) => setEvidence({ ...evidence, evidenceType: event.target.value })}>{EVIDENCE_TYPES.map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select>
        <FileDropzone key={evidence.inputKey} label="근거 파일 선택" description="근거 파일을 끌어 놓거나 선택하세요" acceptLabel="PDF, 표, 문서 또는 이미지 파일" accept=".pdf,.csv,.xlsx,.xls,.docx,.txt,.png,.jpg,.jpeg" aria-label="근거 파일" files={evidence.file ? [evidence.file] : []} onFilesChange={(files) => setEvidence({ ...evidence, file: files[0] ?? null })} />
        <input aria-label="자료 설명" placeholder="선택 사항" value={evidence.description} onChange={(event) => setEvidence({ ...evidence, description: event.target.value })} />
        <button type="button" disabled={!evidence.file || techOps.busy === 'evidence'} onClick={() => void safe(async () => {
          await techOps.uploadEvidence(evidence.file, evidence.evidenceType, evidence.description);
          setEvidence({ evidenceType: 'QUOTE', file: null, description: '', inputKey: evidence.inputKey + 1 });
        })}>파일 업로드 및 근거 추가</button></div>}
      <ul className="tech-ops-evidence-list">{preparation.evidenceReferences.map((item) => <li key={item.evidenceId}><div><strong>{item.originalFilename ?? item.displayName}</strong>
        <span>{item.evidenceType} · {item.mediaType ?? '파일 메타데이터 없음'} · {formatBytes(item.sizeBytes)}</span>
        {item.sha256 && <details><summary>기술 정보</summary><small>{item.sha256}</small></details>}</div>{!locked && <button type="button" onClick={() => void safe(() => techOps.removeEvidence(item.evidenceId))}>삭제</button>}</li>)}</ul>
    </section>

    </>} />

    <section className="tech-ops-finalize" aria-live="polite"><div><p>기술·운영 분석 입력</p><h2>{techOps.snapshot ? '분석 입력을 저장했습니다' : preparation.readyToFinalize ? '입력 내용을 저장할 수 있습니다' : `${preparation.missingRequiredInputs.length}개 입력 또는 결정이 남았습니다`}</h2>
      <span>{techOps.snapshot ? '저장된 입력을 다음 분석에 사용합니다.' : preparation.missingRequiredInputs.join(' · ')}</span>{techOps.snapshot && <details><summary>기술 정보</summary><p>{techOps.snapshot.snapshotId} · {techOps.snapshot.snapshotHash}</p></details>}</div>
      {!techOps.snapshot ? <button type="button" disabled={!preparation.readyToFinalize || techOps.busy === 'finalize'} onClick={() => void safe(techOps.finalize)}>입력 내용 저장</button>
        : <button type="button" disabled={techOps.busy === 'handoff'} onClick={() => void safe(techOps.handoff)}>기술·운영 분석 준비</button>}
      {techOps.run && <small>분석 준비 상태: {({ NOT_CONNECTED: '준비 중', READY: '시작 가능', QUEUED: '대기 중', RUNNING: '분석 중', COMPLETED: '완료', FAILED: '확인 필요' })[techOps.run.status] ?? '상태 확인 필요'}{techOps.run.stale ? ' · 업데이트 필요' : ''}</small>}
    </section>
    {techOps.snapshot && <CommercializationAdvisory techOps={techOps} safe={safe} />}
  </ProjectWorkspace>;
}

function CommercializationAdvisory({ techOps, safe }) {
  const advisory = techOps.advisory;
  const result = advisory?.result;
  const active = ['QUEUED', 'RUNNING'].includes(advisory?.status);
  return <section className="tech-ops-advisory" aria-labelledby="tech-ops-advisory-title">
    <div className="tech-ops-section__heading"><div><p>출시 준비</p><h2 id="tech-ops-advisory-title">기술·운영 자문</h2></div><span>{advisory?.stale ? '업데이트 필요' : ({ QUEUED: '대기 중', RUNNING: '분석 중', COMPLETED: '완료', FAILED: '확인 필요' })[advisory?.status] ?? '시작 전'}</span></div>
    <p className="tech-ops-note">저장한 프로젝트 입력과 최신 시장·수익 구조 결과를 사용합니다. 직접 등록한 자료와 외부 참고 자료는 구분해 표시합니다.</p>
    {advisory?.stale && <p className="tech-ops-error" role="status">앞 단계의 최신 자료가 바뀌었습니다. 새 자문을 실행해 주세요.</p>}
    {advisory?.status === 'FAILED' && <div className="tech-ops-error" role="alert"><p>AI 분석을 완료하지 못했습니다. 잠시 후 다시 시도해 주세요.</p>{advisory.errorCode && <details><summary>기술 정보</summary><p>{advisory.errorCode}</p></details>}</div>}
    {active && <JobTimeline events={techOps.advisoryEvents.events} title="상용화 자문 진행 상황" />}
    <button className="tech-ops-primary" type="button" disabled={active || techOps.busy === 'advisory'} onClick={() => void safe(techOps.startAdvisory)}>{result ? '상용화 자문 다시 실행' : '상용화 자문 실행'}</button>
    {result && <AdvisoryReport result={result} preparation={techOps.preparation} />}
  </section>;
}

function AdvisoryReport({ result, preparation }) {
  return <div className="tech-ops-report"><header><div><p>{result.decision}</p><h3>{result.productName}</h3></div><p>{result.summary}</p></header>
    <ReportGroup title="7개 상용화 조언" items={result.advice} render={(item) => <><b>{item.area} · {item.priority}</b><p>{item.advice}</p><small>검증: {item.validationMethod} · 근거 {item.basisIds.join(', ')}</small></>} />
    <ReportGroup title="파일럿 계획" items={[result.pilotPlan]} render={(item) => <><b>{item.objective}</b><p>범위: {item.scope.join(' · ')}</p><p>지표: {item.metrics.join(' · ')}</p><p>중단: {item.stopConditions.join(' · ')}</p><p>확장: {item.scaleConditions.join(' · ')}</p></>} />
    <ReportGroup title="운영 비용 계측" items={result.operatingCosts} render={(item) => <><b>{item.category} · {item.behavior}</b><p>{item.driver}</p><small>{item.trigger} · {item.measurementUnit} · {item.pilotMeasurement} · 근거 {item.basisIds.join(', ')}</small></>} />
    <ReportGroup title="상용화 준비도" items={result.readiness} render={(item) => <><b>{item.topic} · {item.priority}</b><p>{item.assessment}</p><small>주의: {item.watchouts.join(' · ')}</small><small>통제: {item.controls.join(' · ')}</small><small>검증: {item.validationMethod} · 근거 {item.basisIds.join(', ')}</small></>} />
    <ReportGroup title="출시 게이트" items={result.gates} render={(item) => <><b>{item.title} · {item.status}</b><p>{item.exitCriteria}</p><small>담당 {item.owner} · 근거 {item.basisIds.join(', ')}</small></>} />
    <details><summary>사용된 근거 자료와 기술 정보</summary><p><b>프로젝트에서 확정한 정보</b></p><ul>{result.layer1Facts.map((item) => <li key={item.factId}>{item.factId} · {item.source} · {item.path}: {item.value}</li>)}</ul><p><b>외부 참고 자료</b></p><ul>{result.layer2Evidence.map((item) => <li key={item.evidenceId}>{item.evidenceId} · {item.title} {item.url && <a href={item.url} target="_blank" rel="noreferrer">원문</a>}</li>)}</ul><p><b>직접 등록한 자료</b></p><ul>{preparation.evidenceReferences.map((item) => <li key={item.evidenceId}>{item.originalFilename ?? item.displayName}</li>)}</ul></details>
    <p className="tech-ops-disclaimer">{result.disclaimer}</p><Link className="tech-ops-next" to="../finance">다음 - 6. 재무 분석</Link>
  </div>;
}

function ReportGroup({ title, items = [], render }) {
  return <section><h3>{title}</h3><div className="tech-ops-report__grid">{items.map((item, index) => <article key={`${title}-${index}`}>{render(item)}</article>)}</div></section>;
}

function formatBytes(value) {
  if (!Number.isFinite(value)) return '크기 확인 불가';
  if (value < 1024) return `${value} B`;
  return `${(value / 1024).toFixed(1)} KB`;
}
