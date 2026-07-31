import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { useProjectContext } from '../projects/ProjectContext.jsx';
import { projectRoutes } from '../projects/routing/projectRoutes.js';
import {
  Alert, Button, Card, Dialog, ErrorState, LoadingState, PageHeader, Progress, StatusBadge,
  Tabs,
} from '../../shared/ui/index.js';
import { useApiClient } from '../../shared/api/ApiClientProvider.jsx';
import { createLegalReviewApi } from './api/legalReviewApi.js';
import { useLegalReview } from './hooks/useLegalReview.js';
import { useReviewCycle } from './hooks/useReviewCycle.js';
import {
  collectActions, LEGAL_CATEGORY_LABELS, RISK_LABELS, riskDistribution, splitRevisionRequests,
} from './model/legalReviewViewModel.js';
import { useLegalChecklist } from './hooks/useLegalChecklist.js';
import { LegalFormalReport } from './components/LegalFormalReport.jsx';
import { DiffBanner } from './components/DiffBanner.jsx';
import { RevisionRequestCard, ResolvedRevisionCard } from './components/RevisionRequestCard.jsx';
import { QuestionAnswerForm } from './components/QuestionAnswerForm.jsx';
import { ConvergedBanner } from './components/ConvergedBanner.jsx';
import { VersionHistoryDropdown } from './components/VersionHistoryDropdown.jsx';
import { PublicationSummary } from './components/PublicationSummary.jsx';
import { OverallVerdictCard } from './components/OverallVerdictCard.jsx';
import './legal-review.css';

function ReadyState({ plan, onStart }) {
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
        <Button onClick={() => setConfirming(true)}>법률·규제 사전검토 시작</Button>
      </Card>
      <Dialog open={confirming} onClose={() => setConfirming(false)} title="사전검토를 시작할까요?">
        <p>
          현재 확정본이 변경 불가능한 입력 스냅샷으로 사용됩니다. 결과는 법률 자문이나
          적법성 판정이 아니며, 실제 의사결정 전 전문가 확인이 필요합니다.
        </p>
        <div className="legal-dialog-actions">
          <Button variant="outline" onClick={() => setConfirming(false)}>취소</Button>
          <Button onClick={() => { setConfirming(false); onStart(); }}>확인하고 시작</Button>
        </div>
      </Dialog>
    </>
  );
}

function ProcessingState({ job, startedMode }) {
  return (
    <Card aria-live="polite">
      <StatusBadge status={job?.status ?? 'QUEUED'} />
      <h2>법률·규제 사전검토 진행 중</h2>
      <p>페이지를 새로 열거나 다시 로그인해도 최신 Job 상태에서 이어집니다.</p>
      {startedMode === 'INCREMENTAL' && (
        <p className="legal-rerun-line">
          증분 재검토 중 — 변경된 섹션에 걸린 범주만 재실행하고, 나머지 범주는 이전 결과를 유지합니다.
        </p>
      )}
      <Progress value={job?.progress ?? 0} label="실제 처리 진행률" />
      {job?.message && <p className="legal-muted">{job.message}</p>}
    </Card>
  );
}

function CategoryChips({ categories }) {
  return (
    <span className="legal-checklist__meta">
      근거 범주:
      {categories.map((code) => (
        <span key={code} className="legal-category-chip">
          {LEGAL_CATEGORY_LABELS[code] ?? code}
        </span>
      ))}
    </span>
  );
}

/** 할 일의 "근거 범주" 칩이 #legal-cat-… 으로 점프하면 그 범주를 펼쳐 준다. */
function useAnchorHash() {
  const [hash, setHash] = useState(() => window.location.hash.slice(1));
  useEffect(() => {
    const onHashChange = () => setHash(window.location.hash.slice(1));
    window.addEventListener('hashchange', onHashChange);
    return () => window.removeEventListener('hashchange', onHashChange);
  }, []);
  return hash;
}

function ReviewOverview({ review, projectId, checklist, feedback }) {
  const openAnchor = useAnchorHash();
  const actions = collectActions(review.findings);
  const ownChecklist = useLegalChecklist(projectId, review.versionNumber, null);
  const list = checklist ?? ownChecklist;
  const doneCount = actions.now.filter((item) => list.isChecked(item.action)).length;
  const revisions = splitRevisionRequests(review.revisionRequests);
  const pendingRevisions = revisions.pending.filter(
    (request) => !feedback?.actedRequestIds?.includes(request.id),
  );
  return (
    <div className="legal-results">
      {(pendingRevisions.length > 0 || revisions.resolved.length > 0) && (
        <section aria-labelledby="legal-revisions-title">
          <h2 id="legal-revisions-title">수정 요청 — 기획서 문장을 고쳐야 하는 항목</h2>
          <p className="legal-muted">
            수정안을 적용하면 기획서 새 버전이 만들어집니다. 해결된 요청은 삭제되지 않고 이력으로 남습니다.
          </p>
          {pendingRevisions.map((request) => (
            <RevisionRequestCard
              key={request.id}
              request={request}
              onAccept={feedback?.onAccept}
              onDismiss={feedback?.onDismiss}
              busy={feedback?.busy}
            />
          ))}
          {revisions.resolved.map((request) => (
            <ResolvedRevisionCard key={request.id} request={request} />
          ))}
        </section>
      )}

      {actions.now.length > 0 && (
        <section aria-labelledby="legal-actions-title">
          <h2 id="legal-actions-title">
            판매 전 할 일
            <span className="legal-muted"> — {doneCount}/{actions.now.length} 완료</span>
          </h2>
          <p className="legal-muted">
            같은 할 일이 여러 법 범주에 걸치면 하나로 묶었습니다. 체크 상태는 이 브라우저에만 저장됩니다.
          </p>
          <Card>
            <ul className="legal-checklist">
              {actions.now.map((item) => (
                <li key={item.action}>
                  <label>
                    <input
                      type="checkbox"
                      checked={list.isChecked(item.action)}
                      onChange={() => list.toggle(item.action)}
                    />
                    <span className="legal-checklist__action">{item.action}</span>
                  </label>
                  <span className="legal-checklist__detail">
                    {item.timing && <span className="legal-timing-badge">{item.timing}</span>}
                    <span className={`legal-risk-chip legal-risk--${item.maxRiskLevel?.toLowerCase()}`}>
                      {RISK_LABELS[item.maxRiskLevel] ?? item.maxRiskLevel}
                    </span>
                    <CategoryChips categories={item.categories} />
                  </span>
                </li>
              ))}
            </ul>
          </Card>
        </section>
      )}

      {actions.conditional.length > 0 && (
        <section aria-labelledby="legal-conditional-title">
          <h2 id="legal-conditional-title">조건부 주의 — 계획이 실행되면 의무 발생</h2>
          <Card>
            <ul className="legal-conditional-list">
              {actions.conditional.map((item) => (
                <li key={item.action}>
                  <span className="legal-timing-badge">계획 실행 시</span>
                  <span className="legal-checklist__action">{item.action}</span>
                  <CategoryChips categories={item.categories} />
                </li>
              ))}
            </ul>
          </Card>
        </section>
      )}

      {review.questions.length > 0 && (
        <section aria-labelledby="legal-questions-title">
          <h2 id="legal-questions-title">추가 질문 — 답이 확정되면 재검토가 필요합니다</h2>
          <Card>
            <ol className="legal-questions">
              {review.questions.map((item) => (
                feedback?.onAnswer && !feedback?.answeredQuestionIds?.includes(item.id)
                  ? (
                    <QuestionAnswerForm
                      key={item.id}
                      question={item}
                      onAnswer={feedback.onAnswer}
                      busy={feedback.busy}
                    />
                  )
                  : <li key={item.id}><strong>{item.question}</strong><p>{item.reason}</p></li>
              ))}
            </ol>
          </Card>
        </section>
      )}

      <OverallVerdictCard findings={review.findings} openAnchor={openAnchor} />
    </div>
  );
}

function ResultState({ review, projectId, projectTitle, cycleState, onRereview }) {
  const client = useApiClient();
  const [tab, setTab] = useState('overview');
  const [busy, setBusy] = useState(false);
  const [toast, setToast] = useState(null);
  const [actedRequestIds, setActedRequestIds] = useState([]);
  const [answeredQuestionIds, setAnsweredQuestionIds] = useState([]);
  const cycle = cycleState?.cycle ?? review.cycle;
  const checklist = useLegalChecklist(projectId, review.versionNumber, cycle?.cycleId);
  const actions = collectActions(review.findings);
  const pendingTodoCount = actions.now
    .filter((item) => !checklist.isChecked(item.action)).length;
  const distribution = riskDistribution(review.findings);
  const completedAt = review.completedAt
    ? new Date(review.completedAt).toLocaleString('ko-KR')
    : null;

  const api = createLegalReviewApi(client);
  const versionToast = (newVersionNumber) => setToast({
    text: `기획서가 v${newVersionNumber}(으)로 변경되었습니다 — 재검토를 실행하면 결과에 반영됩니다.`,
    canRereview: true,
  });
  const run = async (task) => {
    setBusy(true);
    try {
      await task();
      await cycleState?.refresh?.();
    } catch (error) {
      setToast({ text: error?.message ?? '요청을 처리하지 못했습니다.', canRereview: false });
    } finally {
      setBusy(false);
    }
  };
  const feedback = {
    busy,
    actedRequestIds,
    answeredQuestionIds,
    onAccept: (requestId, suggestionId) => run(async () => {
      const created = await api.acceptSuggestion(projectId, requestId, suggestionId);
      setActedRequestIds((current) => [...current, requestId]);
      versionToast(created.newVersionNumber);
    }),
    onDismiss: (requestId) => run(async () => {
      await api.dismissRequest(projectId, requestId);
      setActedRequestIds((current) => [...current, requestId]);
      setToast({ text: '수정 요청을 무시했습니다.', canRereview: false });
    }),
    onAnswer: (questionId, body) => run(async () => {
      const created = await api.answerQuestion(projectId, questionId, body);
      setAnsweredQuestionIds((current) => [...current, questionId]);
      versionToast(created.newVersionNumber);
    }),
  };
  const publish = () => run(async () => {
    await api.publish(projectId, cycle.cycleId, checklist.checkedActions);
    setToast({ text: '정식 보고서가 발행되었습니다. 프로젝트가 타당성 분석 단계로 넘어갑니다.', canRereview: false });
  });

  return (
    <div className="legal-results">
      {toast && (
        <div className="legal-toast" role="status">
          <span>{toast.text}</span>
          {/* 재검토는 자동 실행하지 않는다 — 사용자가 눌러야만 실행된다 (§9) */}
          {toast.canRereview && (
            <Button onClick={() => onRereview?.('INCREMENTAL')}>재검토 실행</Button>
          )}
          <Button variant="outline" onClick={() => setToast(null)}>닫기</Button>
        </div>
      )}
      <DiffBanner
        diff={review.diff}
        rerunCategories={review.rerunCategories}
        carriedCategories={review.carriedCategories}
      />
      <ConvergedBanner
        cycle={cycle}
        pendingTodoCount={pendingTodoCount}
        onPublish={publish}
        busy={busy}
      />
      <Card className="legal-summary">
        <div>
          <p className="legal-kicker">법률·규제 사전검토 보고서</p>
          <h2>{RISK_LABELS[review.overallRiskLevel] ?? '확인 필요'} 위험</h2>
        </div>
        <StatusBadge status={review.status} />
        <dl className="legal-source">
          {completedAt && <div><dt>검토 일시</dt><dd>{completedAt}</dd></div>}
          <div><dt>검토 버전</dt><dd>v{review.versionNumber}</dd></div>
          <div><dt>입력 계획</dt><dd>#{review.structuredPlanId}</dd></div>
          <div><dt>문서 버전</dt><dd>#{review.sourceDocumentVersionId}</dd></div>
          <div><dt>분석 방식</dt><dd>{review.provider === 'mock' ? 'Mock Legal AI' : review.provider}</dd></div>
          {review.modelName && <div><dt>모델</dt><dd>{review.modelName}</dd></div>}
        </dl>
        <p>{review.summary}</p>
        <div className="legal-risk-chips" aria-label="위험도 분포">
          {distribution.map((entry) => (
            <span
              key={entry.riskLevel}
              className={`legal-risk-chip legal-risk--${entry.riskLevel.toLowerCase()}`}
            >
              {entry.label} {entry.count}
            </span>
          ))}
        </div>
      </Card>

      <Tabs
        label="검토 결과 보기 방식"
        value={tab}
        onChange={setTab}
        items={[
          {
            value: 'overview',
            label: '검토 결과',
            content: (
              <ReviewOverview
                review={review}
                projectId={projectId}
                checklist={checklist}
                feedback={feedback}
              />
            ),
          },
          {
            value: 'report',
            label: '정식 보고서',
            content: (
              <LegalFormalReport
                review={review}
                projectId={projectId}
                projectTitle={projectTitle}
              />
            ),
          },
        ]}
      />

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
  const cycleState = useReviewCycle(projectId);

  return (
    <>
      <PageHeader
        eyebrow={project.stageLabel}
        title="법률·규제 사전검토"
        description="확정된 사업계획에서 확인이 필요한 규제 영역과 다음 행동을 정리합니다."
      />
      <VersionHistoryDropdown
        versions={cycleState.versions}
        currentVersionNumber={cycleState.cycle?.currentVersionNumber}
      />
      {cycleState.publication && <PublicationSummary publication={cycleState.publication} />}
      {state.status === 'loading' && <LoadingState label="최신 사전검토 상태를 확인하고 있습니다" />}
      {state.status === 'ready' && project.stage === 'LEGAL_REVIEW' && (
        <ReadyState plan={state.plan} onStart={state.start} />
      )}
      {state.status === 'ready' && project.stage !== 'LEGAL_REVIEW' && (
        <Card><h2>현재 단계에서는 시작할 수 없습니다</h2><p>프로젝트 단계가 법률 검토에 도달하면 시작할 수 있습니다.</p></Card>
      )}
      {(state.status === 'starting' || state.status === 'processing') && (
        <ProcessingState job={state.job} startedMode={state.startedMode} />
      )}
      {state.status === 'result' && (
        <ResultState
          review={state.review}
          projectId={projectId}
          projectTitle={project?.title}
          cycleState={cycleState}
          onRereview={state.start}
        />
      )}
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
          onRetry={state.start}
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
