import { useCallback, useEffect, useMemo, useState } from 'react';
import { useOutletContext, useParams } from 'react-router-dom';

import { useApiClient } from '../../../shared/api/ApiClientProvider.jsx';
import { getUserErrorMessage } from '../../../shared/api/apiError.js';
import { useJobEvents } from '../../../shared/async-events/index.js';
import { Alert, Button, LoadingState, ProjectStageHeader, ProjectWorkspace } from '../../../shared/ui/index.js';
import { createMarketInterviewApi } from '../api/marketInterviewApi.js';
import MarketInterviewResult from '../components/MarketInterviewResult.jsx';
import { marketInterviewView } from '../model/marketInterviewView.js';
import '../styles/market-interview.css';

const SAMPLE_OPTIONS = [
  { size: 20, title: '빠른 탐색', detail: '핵심 반응과 인터뷰 질문을 빠르게 살핍니다.' },
  { size: 40, title: '패턴 비교', detail: '더 다양한 응답에서 반복 패턴을 비교합니다.' },
  { size: 80, title: '더 넓은 정성 탐색', detail: '정성적 관점의 폭을 넓힙니다. 통계적 대표성을 뜻하지 않습니다.' },
];
const PURPOSES = ['이해도', '매력 요소', '우려·거부 이유', '기존 대안', '사용 상황', '개선 요구'];
const STAGES = [
  ['MI_INPUT_VALIDATED', '사업안 기준 확인'], ['MI_TARGETING', '타겟 조건 해석'],
  ['MI_BANK_READY', '패널 후보 탐색'], ['MI_PANEL_READY', '패널 구성'],
  ['MI_INTERVIEWING', '가상 인터뷰 진행'], ['MI_CODING', '응답 코딩'],
  ['MI_PATTERNS', '반복 패턴 정리'], ['MI_RESULT_READY', '결과 구성'],
];
const REPRESENTATION = { ORGANIZATION: '직접 타겟 표현 불가 · 탐색 표본', PERSON: '개인 프로필 조건으로 표현 가능 여부 확인', TRANSACTION: '거래 단위 · 탐색 표본', UNKNOWN: '탐색 표본' };
const WORKFLOW = ['표집', '가상 인터뷰', '응답 코딩', '반복 패턴', '실제 고객 질문'];
const STAGE_COPY = {
  MI_INPUT_VALIDATED: '사업안과 현재 검증 기준을 확인했습니다.', MI_TARGETING: '관찰 가능한 타겟 조건을 해석하고 있습니다.',
  MI_BANK_READY: '조건에 맞는 패널 후보를 탐색하고 있습니다.', MI_PANEL_READY: '가상 인터뷰 패널을 구성하고 있습니다.',
  MI_INTERVIEWING: '가상 인터뷰 응답을 생성하고 있습니다.', MI_CODING: '응답별 원문 근거를 코딩하고 있습니다.',
  MI_PATTERNS: '반복되는 응답 패턴을 정리하고 있습니다.', MI_RESULT_READY: '인사이트와 원문 응답을 연결하고 있습니다.',
};

function ConceptCard({ concept, representation }) {
  const identity = concept?.identity ?? {};
  const solution = concept?.solution ?? {};
  const operation = concept?.operation ?? {};
  const rows = [
    ['타겟 고객', identity.targetUsers], ['해결 문제', solution.problemScenario],
    ['제공 방식', solution.solutionMechanism], ['운영 주체', operation.actorRoles ?? operation.providerRole],
  ].filter(([, value]) => value && (typeof value === 'string' || Array.isArray(value)));
  return <section className="market-interview__concept-card"><header><span>RESEARCH MISSION · 현재 인터뷰 대상</span><div><small>타겟 표현</small><strong>{representation}</strong></div></header>
    <h2>{identity.conceptName ?? concept?.conceptName ?? '현재 선택 사업안'}</h2>
    <p>{identity.conceptDefinition ?? identity.coreValue ?? concept?.summary ?? '현재 확정된 사업안 기준으로 진행합니다.'}</p>
    {rows.length ? <details><summary>사업안 기준 자세히 보기</summary><dl>{rows.map(([label, value]) => <div key={label}><dt>{label}</dt><dd>{Array.isArray(value) ? value.join(' · ') : String(value)}</dd></div>)}</dl></details> : null}
  </section>;
}

function InterviewProgress({ events, concept }) {
  const latest = events.events?.at(-1);
  const stage = String(latest?.stage ?? 'INTERVIEWING').replace(/^TRACE_/, '');
  const index = Math.max(0, STAGES.findIndex(([key]) => key === stage));
  const params = latest?.messageParams ?? {};
  const identity = concept?.identity ?? {};
  return <section className="market-interview__progress" aria-live="polite" aria-busy="true">
    <header><span>시장 인터뷰 진행 중 · {index + 1}/{STAGES.length}</span><h2>{params.traceDetail ?? STAGE_COPY[stage] ?? '실제 실행 상태를 기다리고 있습니다.'}</h2><p>사업안 기준: {identity.conceptName ?? concept?.conceptName ?? '현재 선택 사업안'}</p></header>
    <ol>{STAGES.map(([key, label], stageIndex) => <li key={key} data-status={stageIndex < index ? 'complete' : stageIndex === index ? 'current' : 'pending'}><b>{stageIndex < index ? '✓' : stageIndex + 1}</b><span>{label}</span></li>)}</ol>
    {(params.completedCount != null || params.candidateCount != null) ? <dl>
      {params.candidateCount != null ? <div><dt>패널 후보</dt><dd>{Number(params.candidateCount).toLocaleString('ko-KR')}명 profile bank 탐색</dd></div> : null}
      {params.completedCount != null ? <div><dt>현재 처리</dt><dd>{params.completedCount} / {params.totalCount ?? '?'} 완료</dd></div> : null}
    </dl> : null}<p>실제 고객에게 연락하거나 조사하는 과정은 아닙니다.</p><div className="market-interview__progress-skeleton" aria-hidden="true"><i /><i /><i /></div>
  </section>;
}

export default function MarketInterviewPage() {
  const { projectId } = useParams();
  const client = useApiClient();
  const { liveRevision = 0 } = useOutletContext() ?? {};
  const api = useMemo(() => createMarketInterviewApi(client, projectId), [client, projectId]);
  const [current, setCurrent] = useState(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [sampleSize, setSampleSize] = useState(20);
  const [error, setError] = useState(null);
  const view = current ?? marketInterviewView(null);
  const job = useJobEvents(view.active ? view.taskRunId : null);

  const refresh = useCallback(async () => {
    try { setCurrent(marketInterviewView(await api.current())); setError(null); }
    catch (failure) { setError(getUserErrorMessage(failure)); }
    finally { setLoading(false); }
  }, [api]);
  useEffect(() => { const timer = setTimeout(() => void refresh(), 0); return () => clearTimeout(timer); }, [refresh, liveRevision]);
  useEffect(() => {
    if (!job.terminal) return undefined;
    const timer = setTimeout(() => void refresh(), 0);
    return () => clearTimeout(timer);
  }, [job.terminal, refresh]);

  const command = useCallback(async (action) => {
    setBusy(true); setError(null);
    try { setCurrent(marketInterviewView(await action())); }
    catch (failure) { try { setCurrent(marketInterviewView(await api.current())); } catch { setError(getUserErrorMessage(failure)); } }
    finally { setBusy(false); }
  }, [api]);

  if (loading) return <LoadingState label="시장 인터뷰 상태를 불러오는 중" />;
  return <ProjectWorkspace as="section" mode="analyze" className="market-interview">
    <ProjectStageHeader step={4} eyebrow="정성적 고객 탐색" title="시장 인터뷰" description="현재 사업안을 실측 profile bank 기반 가상 관점으로 탐색하고, 원문 근거와 실제 고객 확인 질문까지 이어서 살펴봅니다." />
    {error ? <Alert tone="danger">{error}</Alert> : null}
    {view.stale || view.state === 'STALE' ? <Alert tone="warning" title="이전 사업안 기준 결과입니다">사업안이 변경되어 이전 결과를 current로 표시하지 않습니다. 현재 사업안으로 다시 인터뷰해 주세요.</Alert> : null}
    {view.state === 'FAILED' ? <Alert tone="danger" title="시장 인터뷰를 완료하지 못했습니다">{view.failureCode === 'RESULT_SCHEMA_INVALID'
      ? '응답 코딩 근거를 확인하는 단계에서 실패한 코딩 묶음을 자동으로 다시 생성했지만 계약을 충족하지 못했습니다. 새 실행으로 다시 시도해 주세요.'
      : view.failure ?? '실패한 단계를 확인한 뒤 다시 시도해 주세요.'}</Alert> : null}

    {(view.state === 'NOT_STARTED' || view.state === 'STALE') ? <div className="market-interview__before">
      <Alert tone="info" title="AI 가상 고객 인터뷰">실측 profile bank에서 파생한 가상 관점을 탐색합니다. 실제 고객에게 조사한 결과는 아닙니다.</Alert>
      <ConceptCard concept={view.concept} representation={REPRESENTATION[view.targetingPreview?.customerUnit] ?? '시작 전에 고객 단위를 확인합니다.'} />
      <section className="market-interview__purpose"><span>이번 인터뷰에서 확인할 것</span><ul>{PURPOSES.map((item) => <li key={item}>{item}</li>)}</ul></section>
      <fieldset className="market-interview__sample"><legend>패널 준비</legend><p>표본 수는 정성 탐색의 폭을 조절하며 시장 대표성이나 구매 확률을 만들지 않습니다.</p><div>{SAMPLE_OPTIONS.map((option) => <label key={option.size} data-selected={sampleSize === option.size}><input type="radio" name="market-interview-sample" value={option.size} checked={sampleSize === option.size} onChange={() => setSampleSize(option.size)} /><strong>{option.size}명</strong><span>{option.title}</span><small>{option.detail}</small></label>)}</div>
        <p className="market-interview__representation"><b>타겟 표현</b>{REPRESENTATION[view.targetingPreview?.customerUnit] ?? '시작 전에 현재 사업안의 고객 단위를 확인합니다.'}</p></fieldset>
      <section className="market-interview__start"><div><span>진행 방식</span><h2>가상 고객 인터뷰를 시작합니다</h2><p>각 단계는 실제 TaskRun 상태에 따라 진행되며, 새로운 통계나 구매 확률을 만들지 않습니다.</p></div><ol className="market-interview__workflow">{WORKFLOW.map((item, index) => <li key={item}><b>{index + 1}</b><span>{item}</span></li>)}</ol>
        <Button disabled={busy} loading={busy} onClick={() => void command(() => api.start(sampleSize))}>{view.state === 'STALE' ? '현재 사업안으로 다시 인터뷰' : '가상 고객 인터뷰 시작'}</Button></section>
    </div> : null}
    {view.active ? <InterviewProgress events={job} concept={view.concept} /> : null}
    {view.canRetry ? <div className="market-interview__actions"><Button disabled={busy} loading={busy} onClick={() => void command(api.retry)}>실패한 실행 다시 시도</Button></div> : null}
    {view.result && !view.stale ? <><Alert tone="warning">아래 내용은 가상 정성 탐색이며 시장 근거나 통계로 인용하지 말고 실제 고객 확인에 사용하세요. 모든 theme은 연결된 응답 원문으로 확인해야 합니다.</Alert><MarketInterviewResult result={view.result} /></> : null}
  </ProjectWorkspace>;
}
