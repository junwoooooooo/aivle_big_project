import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { useProjectContext } from '../projects/ProjectContext.jsx';
import { projectRoutes } from '../projects/routing/projectRoutes.js';
import {
  Alert, Button, Card, Dialog, ErrorState, LoadingState, PageHeader, Progress, StatusBadge,
} from '../../shared/ui/index.js';
import { useLegalReview } from './hooks/useLegalReview.js';
import { useServicePolicy } from '../service-policy/useServicePolicy.js';
import { getWriteRestriction } from '../service-policy/servicePolicyRestrictions.js';
import {
  APPLICABILITY_LABELS, LEGAL_CATEGORY_LABELS, parseStringList, RISK_LABELS,
} from './model/legalReviewViewModel.js';
import './legal-review.css';

function ReadyState({ plan, onStart, restriction, onRefreshPolicy }) {
  const [confirming, setConfirming] = useState(false);
  return (
    <>
      <Card className="legal-start">
        <StatusBadge status="CONFIRMED" />
        <h2>확정된 사업계획으로 사전검토를 시작합니다</h2>
        <dl className="legal-source">
          <div><dt>구조화 계획</dt><dd>#{plan?.planId ?? plan?.structuredPlanId}</dd></div>
          <div><dt>원본 문서 버전</dt><dd>#{plan?.sourceDocumentVersionId}</dd></div>
        </dl>
        <p>
          사업자 등록, 인허가, 개인정보, 계약, 지식재산권 등 10개 범주를
          확정된 계획 스냅샷만으로 점검합니다.
        </p>
        <Alert title="민감정보 입력 주의" tone="warning">
          주민등록번호, 계좌번호, 영업비밀 등 불필요한 민감정보가 계획에 포함되지 않았는지 확인하세요.
        </Alert>
        {restriction.blocked && (
          <Alert tone={restriction.code === 'POLICY_UNAVAILABLE' ? 'danger' : 'warning'} title="새 분석을 시작할 수 없습니다">
            {restriction.message}
            {restriction.code === 'POLICY_UNAVAILABLE' && <Button type="button" variant="outline" size="small" onClick={onRefreshPolicy}>다시 시도</Button>}
          </Alert>
        )}
        <Button disabled={restriction.blocked} onClick={() => setConfirming(true)}>법률·규제 사전검토 시작</Button>
      </Card>
      <Dialog open={confirming} onClose={() => setConfirming(false)} title="사전검토를 시작할까요?">
        <p>
          현재 확정본이 변경 불가능한 입력 스냅샷으로 사용됩니다. 결과는 법률 자문이나
          적법성 판정이 아니며, 실제 의사결정 전 전문가 확인이 필요합니다.
        </p>
        <div className="legal-dialog-actions">
          <Button variant="outline" onClick={() => setConfirming(false)}>취소</Button>
          <Button disabled={restriction.blocked} onClick={() => { setConfirming(false); onStart(); }}>확인하고 시작</Button>
        </div>
      </Dialog>
    </>
  );
}

function ProcessingState({ job }) {
  return (
    <Card aria-live="polite">
      <StatusBadge status={job?.status ?? 'QUEUED'} />
      <h2>법률·규제 사전검토 진행 중</h2>
      <p>페이지를 새로 열거나 다시 로그인해도 최신 Job 상태에서 이어집니다.</p>
      <Progress value={job?.progress ?? 0} label="실제 처리 진행률" />
      {job?.message && <p className="legal-muted">{job.message}</p>}
    </Card>
  );
}

function ResultState({ review }) {
  return (
    <div className="legal-results">
      <Card className="legal-summary">
        <div>
          <p className="legal-kicker">사전검토 요약</p>
          <h2>{RISK_LABELS[review.overallRiskLevel] ?? '확인 필요'} 위험</h2>
        </div>
        <StatusBadge status={review.status} />
        <p>{review.summary}</p>
        <dl className="legal-source">
          <div><dt>입력 계획</dt><dd>#{review.structuredPlanId}</dd></div>
          <div><dt>문서 버전</dt><dd>#{review.sourceDocumentVersionId}</dd></div>
          <div><dt>분석 방식</dt><dd>{review.provider === 'mock' ? 'Mock Legal AI' : review.provider}</dd></div>
        </dl>
      </Card>

      <section aria-labelledby="legal-findings-title">
        <h2 id="legal-findings-title">범주별 확인 사항</h2>
        <div className="legal-finding-grid">
          {review.findings.map((item) => (
            <Card key={item.category} className={`legal-finding legal-risk--${item.riskLevel?.toLowerCase()}`}>
              <div className="legal-finding__heading">
                <h3>{LEGAL_CATEGORY_LABELS[item.category] ?? item.title}</h3>
                <span>{RISK_LABELS[item.riskLevel] ?? item.riskLevel}</span>
              </div>
              <p className="legal-applicability">
                {APPLICABILITY_LABELS[item.applicability] ?? item.applicability}
              </p>
              <p>{item.finding}</p>
              <h4>판단 이유</h4><p>{item.rationale}</p>
              <h4>권장 행동</h4><p>{item.recommendedAction}</p>
              {parseStringList(item.evidenceJson).length > 0 && (
                <>
                  <h4>근거</h4>
                  <ul>{parseStringList(item.evidenceJson).map((evidence) => <li key={evidence}>{evidence}</li>)}</ul>
                </>
              )}
              {item.requiresProfessionalReview && (
                <Alert title="전문가 확인 권장" tone="warning" live={false}>
                  실제 적용 여부와 대응 방법을 자격 있는 전문가에게 확인하세요.
                </Alert>
              )}
            </Card>
          ))}
        </div>
      </section>

      {review.questions.length > 0 && (
        <Card>
          <h2>추가로 확인할 질문</h2>
          <ol className="legal-questions">
            {review.questions.map((item) => (
              <li key={item.id}><strong>{item.question}</strong><p>{item.reason}</p></li>
            ))}
          </ol>
        </Card>
      )}
      <Alert title="AI 사전검토의 한계" tone="warning" live={false}>
        AI 기반 사전점검은 법률 자문이 아니며 모든 법령·규제를 포괄하지 않을 수 있습니다.
        법령은 변경될 수 있으므로 실제 사업 실행 전 전문가와 관할 기관에 확인하세요.
        {' '}{review.disclaimer}
      </Alert>
    </div>
  );
}

export default function LegalReviewPage() {
  const { projectId } = useParams();
  const { project } = useProjectContext();
  const state = useLegalReview(projectId);
  const servicePolicy = useServicePolicy();
  const restriction = getWriteRestriction({ ...servicePolicy, documentProcessing: true });

  return (
    <>
      <PageHeader
        eyebrow={project.stageLabel}
        title="법률·규제 사전검토"
        description="확정된 사업계획에서 확인이 필요한 규제 영역과 다음 행동을 정리합니다."
      />
      {state.status === 'loading' && <LoadingState label="최신 사전검토 상태를 확인하고 있습니다" />}
      {state.status === 'ready' && project.stage === 'LEGAL_REVIEW' && (
        <ReadyState
          plan={state.plan}
          onStart={state.start}
          restriction={restriction}
          onRefreshPolicy={() => void servicePolicy.refresh().catch(() => undefined)}
        />
      )}
      {state.status === 'ready' && project.stage !== 'LEGAL_REVIEW' && (
        <Card><h2>현재 단계에서는 시작할 수 없습니다</h2><p>프로젝트 단계가 법률 검토에 도달하면 시작할 수 있습니다.</p></Card>
      )}
      {(state.status === 'starting' || state.status === 'processing') && <ProcessingState job={state.job} />}
      {state.status === 'result' && <ResultState review={state.review} />}
      {state.status === 'plan-not-confirmed' && (
        <Card>
          <StatusBadge status="NEEDS_INPUT" />
          <h2>사업계획 확정이 먼저 필요합니다</h2>
          <p>필수 항목을 모두 보완하고 구조화 계획을 확정한 뒤 다시 방문하세요.</p>
          <Link className="primary-link" to={projectRoutes.structure(projectId)}>구조화 계획 확인</Link>
        </Card>
      )}
      {state.status === 'failed' && (
        <ErrorState
          title="사전검토를 완료하지 못했습니다"
          description={state.job?.message ?? '입력 상태를 확인한 뒤 다시 시도해 주세요.'}
          onRetry={state.retry}
        />
      )}
      {state.status === 'error' && (
        <ErrorState
          title={state.error?.status === 404 ? '검토 정보를 찾을 수 없습니다' : '사전검토 상태를 불러오지 못했습니다'}
          description={state.error?.message ?? '네트워크 연결을 확인한 뒤 다시 시도해 주세요.'}
          onRetry={state.retry}
        />
      )}
    </>
  );
}
