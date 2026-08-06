import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { useProjectContext } from '../projects/ProjectContext.jsx';
import { projectRoutes } from '../projects/routing/projectRoutes.js';
import {
  Alert, Button, Card, Dialog, ErrorState, LoadingState, PageHeader, Progress, StatusBadge,
} from '../../shared/ui/index.js';
import { usePersonas } from './hooks/usePersonas.js';
import useAvailablePersonas from './hooks/useAvailablePersonas.js';
import AvailablePersonaSection from './AvailablePersonaSection.jsx';
import { useServicePolicy } from '../service-policy/useServicePolicy.js';
import { getWriteRestriction } from '../service-policy/servicePolicyRestrictions.js';
import {
  CONFIDENCE_LABELS, LEVEL_LABELS, listItemText, parseJsonArray,
} from './model/personaViewModel.js';
import './personas.css';

function Ready({ feasibility, onStart, restriction, onRefreshPolicy }) {
  const [confirming, setConfirming] = useState(false);
  return (
    <>
      <Card className="persona-ready">
        <StatusBadge status="NEEDS_VALIDATION" />
        <h2>확정 계획과 타당성 분석으로 고객 가설을 만듭니다</h2>
        <p>추천은 실제 고객 조사 결과가 아닙니다. 기준 군집과 프로젝트 근거의 적합성을 비교해 우선 검증할 페르소나, 가설, 질문과 조사 계획을 제안합니다.</p>
        <dl className="persona-source">
          <div><dt>타당성 분석</dt><dd>#{feasibility?.assessmentId}</dd></div>
          <div><dt>확정 계획</dt><dd>#{feasibility?.structuredPlanId}</dd></div>
          <div><dt>검증 과제</dt><dd>{feasibility?.validationTasks?.length ?? 0}개</dd></div>
        </dl>
        {restriction.blocked && (
          <Alert tone={restriction.code === 'POLICY_UNAVAILABLE' ? 'danger' : 'warning'} title="새 추천 작업을 시작할 수 없습니다">
            {restriction.message}
            {restriction.code === 'POLICY_UNAVAILABLE' && <Button type="button" variant="outline" size="small" onClick={onRefreshPolicy}>다시 시도</Button>}
          </Alert>
        )}
        <Button disabled={restriction.blocked} onClick={() => setConfirming(true)}>페르소나 추천 시작</Button>
      </Card>
      <Dialog open={confirming} onClose={() => setConfirming(false)} title="고객 가설 추천을 시작할까요?">
        <p>결과는 AI 추론과 통계 군집의 비교이며 구매 의향, 시장 규모, 실제 인터뷰 응답을 의미하지 않습니다. 조사 전에는 가설로만 사용해야 합니다.</p>
        <div className="persona-actions">
          <Button variant="outline" onClick={() => setConfirming(false)}>취소</Button>
          <Button disabled={restriction.blocked} onClick={() => { setConfirming(false); onStart(); }}>확인하고 시작</Button>
        </div>
      </Dialog>
    </>
  );
}

function RecommendationItem({ item }) {
  const persona = item.baselinePersona;
  return (
    <Card className={`persona-result-card persona-rank-${item.rank}`}>
      <div className="persona-card-heading">
        <div><p className="persona-kicker">#{item.rank} · {LEVEL_LABELS[item.recommendationLevel]}</p>
          <h3>{persona.displayName}</h3></div>
        <div className="persona-score" aria-label={`적합도 ${item.fitScore ?? '정보 부족'}`}>{item.fitScore ?? '—'}</div>
      </div>
      <p>{item.interpretation}</p>
      <p className="persona-confidence">신뢰도 {CONFIDENCE_LABELS[item.confidence] ?? item.confidence}</p>
      <div className="persona-comparison">
        <div><h4>맞는 근거</h4><ul>{parseJsonArray(item.matchReasonsJson).map((reason) => <li key={reason}>{reason}</li>)}</ul></div>
        <div><h4>불일치·위험</h4><ul>{parseJsonArray(item.mismatchRisksJson).map((risk) => <li key={risk}>{risk}</li>)}</ul></div>
      </div>
      <details><summary>가정과 확인 질문</summary>
        <h4>검증 전 가정</h4><ul>{parseJsonArray(item.assumptionsJson).map((value) => <li key={value}>{value}</li>)}</ul>
        <h4>확인 질문</h4><ul>{parseJsonArray(item.verificationQuestionsJson).map((value) => <li key={value}>{value}</li>)}</ul>
      </details>
    </Card>
  );
}

function Result({ value }) {
  return (
    <div className="persona-results">
      <Card className="persona-summary">
        <div className="persona-card-heading"><div><p className="persona-kicker">고객 검증 우선순위</p>
          <h2>{value.summary}</h2></div><StatusBadge status={value.status} /></div>
        <dl className="persona-source">
          <div><dt>신뢰도</dt><dd>{CONFIDENCE_LABELS[value.confidence]}</dd></div>
          <div><dt>기준 카탈로그</dt><dd>{value.catalogVersion}</dd></div>
          <div><dt>분석 방식</dt><dd>{value.provider === 'mock' ? 'Mock AI' : value.provider}</dd></div>
        </dl>
      </Card>
      <section aria-labelledby="persona-recommendations-title"><h2 id="persona-recommendations-title">추천 비교</h2>
        <div className="persona-recommendations">{value.items.map((item) => <RecommendationItem key={item.id} item={item} />)}</div>
      </section>
      <section aria-labelledby="persona-hypotheses-title"><h2 id="persona-hypotheses-title">검증할 고객 가설</h2>
        <div className="persona-hypotheses">{value.hypotheses.map((item) => (
          <Card key={item.id}><StatusBadge status={item.priority} /><h3>{item.statement}</h3>
            <p>{item.rationale}</p><small>{item.hypothesisType} · {item.sourceType}</small></Card>
        ))}</div>
      </section>
      <section aria-labelledby="persona-plans-title"><h2 id="persona-plans-title">고객 검증 계획</h2>
        <div className="persona-plans">{value.validationPlans.map((plan) => (
          <Card key={plan.id}><div className="persona-card-heading"><h3>{plan.method}</h3><StatusBadge status={plan.priority} /></div>
            <p>{plan.objective}</p><dl>
              <div><dt>대상</dt><dd>{plan.targetParticipantDescription}</dd></div>
              <div><dt>모집 채널</dt><dd>{plan.recruitmentChannel}</dd></div>
              <div><dt>표본 수</dt><dd>{plan.suggestedSampleSize ?? '팀 결정 필요'}</dd></div>
            </dl>
            <h4>인터뷰 질문</h4><ol>{parseJsonArray(plan.interviewQuestionsJson).map((q, index) => (
              <li key={`${index}-${listItemText(q)}`}>{listItemText(q)}</li>
            ))}</ol>
            <h4>설문 질문</h4><ol>{parseJsonArray(plan.surveyQuestionsJson).map((q, index) => (
              <li key={`${index}-${listItemText(q)}`}>{listItemText(q)}</li>
            ))}</ol>
          </Card>
        ))}</div>
      </section>
      <Alert title="해석 및 사용 한계" tone="warning" live={false}>{value.disclaimer}</Alert>
    </div>
  );
}

export default function PersonaPage() {
  const { projectId } = useParams();
  const { project } = useProjectContext();
  const state = usePersonas(projectId);
  const servicePolicy = useServicePolicy();
  const restriction = getWriteRestriction({ ...servicePolicy, documentProcessing: true });
  const clusterEnabled =
    !servicePolicy.loading
    && !servicePolicy.error
    && servicePolicy.policy.clusterPersonaEnabled;
  const availablePersonas = useAvailablePersonas(projectId, clusterEnabled);
  return (
    <>
      <PageHeader eyebrow={project.stageLabel} title="데이터 기반 페르소나·고객 검증"
        description="통계 군집을 프로젝트 근거와 비교해 고객 가설과 실제 검증 계획으로 연결합니다." />
      {state.refreshError && <Alert tone="warning" title="기준 세그먼트 정보를 새로고침하지 못했습니다.">기존 정보를 표시하고 있습니다. <Button variant="outline" size="small" onClick={state.retry}>다시 시도</Button></Alert>}
      {state.status === 'loading' && <LoadingState label="페르소나 기준선과 최신 결과를 확인하고 있습니다" />}
      {state.status === 'ready' && (
        <Ready
          feasibility={state.feasibility}
          onStart={state.start}
          restriction={restriction}
          onRefreshPolicy={() => void servicePolicy.refresh().catch(() => undefined)}
        />
      )}
      {(state.status === 'starting' || state.status === 'processing') && (
        <Card aria-live="polite"><StatusBadge status={state.job?.status ?? 'QUEUED'} />
          <h2>페르소나 추천과 고객 검증 계획을 구성하고 있습니다</h2>
          <Progress value={state.job?.progress ?? 0} label="실제 서버 작업 진행률" /></Card>
      )}
      {state.status === 'result' && <Result value={state.recommendation} />}
      {state.status === 'not-ready' && (
        <Card><StatusBadge status="NEEDS_INPUT" /><h2>완료된 사업 타당성 분석이 필요합니다</h2>
          <p>확정 계획에 연결된 최신 타당성 분석과 검증 과제를 먼저 준비해 주세요.</p>
          <Link className="primary-link" to={projectRoutes.feasibility(projectId)}>사업 타당성 분석으로 이동</Link></Card>
      )}
      {state.status === 'failed' && <ErrorState title="페르소나 추천을 완료하지 못했습니다"
        description={state.job?.message ?? '작업 상태를 확인한 뒤 다시 시도해 주세요.'} onRetry={state.retry} />}
      {state.status === 'error' && <ErrorState title="페르소나 상태를 불러오지 못했습니다"
        description={state.error?.message ?? '연결을 확인한 뒤 다시 시도해 주세요.'} onRetry={state.retry} />}
      {servicePolicy.error && (
        <Alert tone="warning" title="추가 페르소나 운영 상태를 확인하지 못했습니다.">
          기존 추천 결과는 계속 확인할 수 있지만 추가 페르소나는 선택할 수 없습니다.
          <Button size="small" variant="outline" onClick={() => void servicePolicy.refresh().catch(() => undefined)}>다시 시도</Button>
        </Alert>
      )}
      {clusterEnabled && (
        <AvailablePersonaSection
          state={availablePersonas}
          blocked={restriction.blocked}
          blockedReason={restriction.message}
        />
      )}
    </>
  );
}
