import { useMemo, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { getUserErrorMessage } from '../../../shared/api/apiError.js';
import { DECISION_FIELDS, createFactDraft, decisionComplete, displayValue, factsFromDraft, proposalDraft, proposalValue } from '../model/techOpsModel.js';
import useTechOps from '../hooks/useTechOps.js';
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

  return <main className="tech-ops-page">
    <header className="tech-ops-heading"><p>6. 기술·운영 분석</p><h1>분석에 전달할 입력을 확정합니다</h1>
      <span>상위 단계에서 이미 확정된 값은 다시 입력하지 않습니다. 실제 분석 알고리즘은 외부 모듈이 담당합니다.</span></header>
    {techOps.error && <p className="tech-ops-error" role="alert">{getUserErrorMessage(techOps.error)}</p>}
    {['QUEUED', 'RUNNING'].includes(preparation.proposalGenerationStatus) && <p role="status">
      {Object.values(preparation.proposalDecisions ?? {}).some((item) => item.pendingAlternativeTaskRunId)
        ? '새 제안 생성 중' : 'AI 운영 가설 생성 중'}
    </p>}
    {preparation.proposalGenerationStatus === 'FAILED' && <p role="alert">AI 제안 생성 실패 — 직접 입력하거나 다시 시도할 수 있습니다.
      {hasMissingProposal && <button type="button" disabled={techOps.busy === 'proposal-retry'} onClick={() => void safe(techOps.retryProposals)}>AI 제안 다시 시도</button>}</p>}

    <section className="tech-ops-source" aria-labelledby="tech-source-title"><div><p>Concept 초안 · 사용자 확인 필요</p><h2 id="tech-source-title">제품·서비스 사양</h2></div>
      <strong>{displayValue(product?.value?.summary)}</strong>
      <span>{displayValue(product?.value?.features)}</span>
      <small>확정 전에는 수정할 수 있습니다 · Market Seed Snapshot {preparation.sourceMarketSeedSnapshotId}</small></section>

    <section className="tech-ops-section" aria-labelledby="tech-facts-title"><div className="tech-ops-section__heading"><div><p>사용자 사실</p><h2 id="tech-facts-title">분석 전 필수 입력</h2></div><span>{locked ? 'Snapshot 확정됨' : '직접 입력'}</span></div>
      <div className="tech-ops-form-grid">
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
      <p className="tech-ops-note">다른 제안 요청은 직전 값과 다른 새 proposal version을 생성합니다.</p>
    </section>

    <section className="tech-ops-section" aria-labelledby="tech-evidence-title"><div className="tech-ops-section__heading"><div><p>선택 사항</p><h2 id="tech-evidence-title">실제 근거 자료</h2></div><span>사용자 제공 Evidence</span></div>
      <p className="tech-ops-note">견적서·BOM·공급사·사양서·파일럿 자료만 등록합니다. AI 제안은 Evidence로 저장되지 않습니다.</p>
      {!locked && <div className="tech-ops-evidence-form"><select aria-label="자료 유형" value={evidence.evidenceType} onChange={(event) => setEvidence({ ...evidence, evidenceType: event.target.value })}>{EVIDENCE_TYPES.map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select>
        <input key={evidence.inputKey} type="file" aria-label="근거 파일" accept=".pdf,.csv,.xlsx,.xls,.docx,.txt,.png,.jpg,.jpeg"
          onChange={(event) => setEvidence({ ...evidence, file: event.target.files?.[0] ?? null })} />
        <input aria-label="자료 설명" placeholder="선택 사항" value={evidence.description} onChange={(event) => setEvidence({ ...evidence, description: event.target.value })} />
        <button type="button" disabled={!evidence.file || techOps.busy === 'evidence'} onClick={() => void safe(async () => {
          await techOps.uploadEvidence(evidence.file, evidence.evidenceType, evidence.description);
          setEvidence({ evidenceType: 'QUOTE', file: null, description: '', inputKey: evidence.inputKey + 1 });
        })}>파일 업로드 및 근거 추가</button></div>}
      <ul className="tech-ops-evidence-list">{preparation.evidenceReferences.map((item) => <li key={item.evidenceId}><div><strong>{item.originalFilename ?? item.displayName}</strong>
        <span>{item.evidenceType} · {item.mediaType ?? '파일 메타데이터 없음'} · {formatBytes(item.sizeBytes)}</span>
        {item.sha256 && <small>{item.sha256}</small>}</div>{!locked && <button type="button" onClick={() => void safe(() => techOps.removeEvidence(item.evidenceId))}>삭제</button>}</li>)}</ul>
    </section>

    <section className="tech-ops-finalize" aria-live="polite"><div><p>TechOpsInputSnapshot</p><h2>{techOps.snapshot ? '분석 입력이 확정되었습니다' : preparation.readyToFinalize ? 'Snapshot을 확정할 수 있습니다' : `${preparation.missingRequiredInputs.length}개 입력 또는 결정이 남았습니다`}</h2>
      <span>{techOps.snapshot ? `${techOps.snapshot.snapshotId} · ${techOps.snapshot.snapshotHash}` : preparation.missingRequiredInputs.join(' · ')}</span></div>
      {!techOps.snapshot ? <button type="button" disabled={!preparation.readyToFinalize || techOps.busy === 'finalize'} onClick={() => void safe(techOps.finalize)}>입력 Snapshot 확정</button>
        : <button type="button" disabled={techOps.busy === 'handoff'} onClick={() => void safe(techOps.handoff)}>기술·운영 분석 Handoff 준비</button>}
      {techOps.run && <small>외부 연결 상태: {techOps.run.status}{techOps.run.stale ? ' · 입력 갱신 필요' : ''}</small>}
    </section>
  </main>;
}

function formatBytes(value) {
  if (!Number.isFinite(value)) return '크기 확인 불가';
  if (value < 1024) return `${value} B`;
  return `${(value / 1024).toFixed(1)} KB`;
}
