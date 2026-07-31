import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { useProjectContext } from '../projects/ProjectContext.jsx';
import { projectRoutes } from '../projects/routing/projectRoutes.js';
import {
  Alert, Button, Card, Dialog, ErrorState, LoadingState, PageHeader, Progress, StatusBadge,
} from '../../shared/ui/index.js';
import { useFeasibility } from './hooks/useFeasibility.js';
import {
  CONFIDENCE_LABELS, VERDICT_LABELS, groupDimensions, parseJsonList,
} from './model/feasibilityViewModel.js';
import { AnalysisGroupCard } from './components/AnalysisGroupCard.jsx';
import './feasibility.css';

function ReadyState({ plan, legalReview, onStart }) {
  const [confirming, setConfirming] = useState(false);
  return (
    <>
      <Card className="feasibility-start">
        <StatusBadge status="CONFIRMED" />
        <h2>확정된 입력으로 사업 타당성 사전분석을 시작합니다</h2>
        <dl className="feasibility-source">
          <div><dt>구조화 계획</dt><dd>#{plan?.planId ?? plan?.structuredPlanId}</dd></div>
          <div><dt>문서 버전</dt><dd>#{plan?.sourceDocumentVersionId}</dd></div>
          <div><dt>법률 사전검토</dt><dd>#{legalReview?.legalReviewId}</dd></div>
        </dl>
        <p>
          시장, 비즈니스 모델, 기술·운영 세 갈래로 나눠 확인하고
          검증 과제와 위험을 우선 정리합니다. 이어서 재무 분석으로 넘어갑니다.
        </p>
        <Alert title="분석 범위와 데이터 주의" tone="warning">
          시장 규모와 재무 수치는 자동 생성하지 않습니다. 문서의 가정은 외부 출처와
          실제 운영 자료로 검증해야 하며, 불필요한 민감정보를 추가하지 마세요.
        </Alert>
        <Button onClick={() => setConfirming(true)}>사업 타당성 사전분석 시작</Button>
      </Card>
      <Dialog open={confirming} onClose={() => setConfirming(false)} title="사전분석을 시작할까요?">
        <p>
          현재 확정 계획과 최신 법률 사전검토가 변경 불가능한 입력으로 사용됩니다.
          결과는 성공 확률, 투자 판단 또는 시장조사를 대체하지 않습니다.
        </p>
        <div className="feasibility-dialog-actions">
          <Button variant="outline" onClick={() => setConfirming(false)}>취소</Button>
          <Button onClick={() => { setConfirming(false); onStart(); }}>확인하고 시작</Button>
        </div>
      </Dialog>
    </>
  );
}

function ProcessingState({ job }) {
  return (
    <Card aria-live="polite">
      <StatusBadge status={job?.status ?? 'QUEUED'} />
      <h2>사업 타당성 사전분석 진행 중</h2>
      <p>새로고침하거나 다시 로그인해도 서버의 최신 Job 상태에서 이어집니다.</p>
      <Progress value={job?.progress ?? 0} label="실제 처리 진행률" />
      {job?.message && <p className="feasibility-muted">{job.message}</p>}
    </Card>
  );
}

function ResultState({ assessment }) {
  const strengths = parseJsonList(assessment.keyStrengthsJson);
  const risks = parseJsonList(assessment.keyRisksJson);
  return (
    <div className="feasibility-results">
      <Card className="feasibility-summary">
        <div>
          <p className="feasibility-kicker">사전분석 요약</p>
          <h2>{VERDICT_LABELS[assessment.verdict] ?? assessment.verdict}</h2>
        </div>
        <StatusBadge status={assessment.status} />
        <p>{assessment.summary}</p>
        <dl className="feasibility-source">
          <div><dt>종합 점수</dt><dd>{assessment.overallScore ?? '정보 부족'}</dd></div>
          <div><dt>신뢰도</dt><dd>{CONFIDENCE_LABELS[assessment.confidence]}</dd></div>
          <div><dt>입력 계획</dt><dd>#{assessment.structuredPlanId}</dd></div>
          <div><dt>법률 검토</dt><dd>#{assessment.legalReviewId}</dd></div>
          <div><dt>분석 방식</dt><dd>{assessment.provider === 'mock' ? 'Mock AI' : assessment.provider}</dd></div>
        </dl>
        <div className="feasibility-summary-grid">
          <div><h3>확인된 강점</h3><ul>{strengths.map((item) => <li key={item}>{item}</li>)}</ul></div>
          <div><h3>주요 위험</h3><ul>{risks.map((item) => <li key={item}>{item}</li>)}</ul></div>
        </div>
      </Card>

      <section aria-labelledby="feasibility-groups-title">
        <h2 id="feasibility-groups-title">시장 · 비즈니스 모델 · 기술 운영</h2>
        <p className="feasibility-muted">
          세 갈래로 나눠 보되 하나의 분석입니다. 각 묶음의 결론이 먼저 오고,
          그 판단을 구성한 항목은 펼쳐서 확인할 수 있습니다.
        </p>
        <div className="feasibility-group-list">
          {groupDimensions(assessment).map((group) => (
            <AnalysisGroupCard key={group.analysisType} group={group} />
          ))}
        </div>
      </section>

      <Card>
        <h2>우선 검증 과제</h2>
        {assessment.validationTasks.length === 0 ? <p>현재 열린 검증 과제가 없습니다.</p> : (
          <ol className="feasibility-tasks">
            {assessment.validationTasks.map((task) => (
              <li key={task.code}>
                <div><StatusBadge status={task.priority} /><h3>{task.title}</h3></div>
                <p>{task.description}</p>
                <dl>
                  <div><dt>필요한 이유</dt><dd>{task.reason}</dd></div>
                  <div><dt>검증 방법</dt><dd>{task.validationMethod}</dd></div>
                  <div><dt>기대 근거</dt><dd>{task.expectedEvidence}</dd></div>
                </dl>
              </li>
            ))}
          </ol>
        )}
      </Card>
      <Card className="feasibility-next">
        <div>
          <h2>다음: 재무 분석</h2>
          <p className="feasibility-muted">
            위 세 묶음의 결과와 계획서의 원가·매출 항목을 바탕으로 예상 매출, 손익,
            손익분기를 계산합니다. 같은 단계 안에서 이어지는 후속 분석입니다.
          </p>
        </div>
        <Button disabled aria-disabled="true">재무 분석 실행 (준비 중)</Button>
      </Card>
      <Alert title="재무 분석 경계" tone="warning" live={false}>
        검증된 가격·판매량·원가·고정비 가정이 없어 예상 매출, 손익, 수익성,
        손익분기 수치를 계산하지 않았습니다. 부족한 정보는 0으로 처리하지 않습니다.
      </Alert>
      <Alert title="AI 사전분석의 한계" tone="warning" live={false}>
        {assessment.disclaimer}
      </Alert>
    </div>
  );
}

export default function FeasibilityPage() {
  const { projectId } = useParams();
  const { project } = useProjectContext();
  const state = useFeasibility(projectId);
  return (
    <>
      <PageHeader
        eyebrow={project.stageLabel}
        title="사업 타당성 사전분석"
        description="확정된 계획과 법률 사전검토를 근거로 위험과 검증 과제를 우선 정리합니다."
      />
      {state.status === 'loading' && <LoadingState label="최신 사업 타당성 상태를 확인하고 있습니다" />}
      {state.status === 'ready' && project.stage === 'FEASIBILITY' && (
        <ReadyState plan={state.plan} legalReview={state.legalReview} onStart={state.start} />
      )}
      {state.status === 'ready' && project.stage !== 'FEASIBILITY' && (
        <Card><h2>현재 단계에서는 시작할 수 없습니다</h2><p>프로젝트가 사업성 분석 단계에 도달하면 시작할 수 있습니다.</p></Card>
      )}
      {(state.status === 'starting' || state.status === 'processing') && <ProcessingState job={state.job} />}
      {state.status === 'result' && <ResultState assessment={state.assessment} />}
      {state.status === 'not-ready' && (
        <Card>
          <StatusBadge status="NEEDS_INPUT" />
          <h2>확정 계획과 법률 사전검토가 필요합니다</h2>
          <p>구조화 계획을 확정하고 법률·규제 사전검토를 완료한 뒤 다시 방문하세요.</p>
          <Link className="primary-link" to={projectRoutes.legal(projectId)}>법률·규제 사전검토 확인</Link>
        </Card>
      )}
      {state.status === 'failed' && (
        <ErrorState title="사업 타당성 사전분석을 완료하지 못했습니다"
          description={state.job?.message ?? '입력 상태를 확인한 뒤 다시 시도해 주세요.'}
          onRetry={state.retry} />
      )}
      {state.status === 'error' && (
        <ErrorState title="사업 타당성 상태를 불러오지 못했습니다"
          description={state.error?.message ?? '네트워크 연결을 확인한 뒤 다시 시도해 주세요.'}
          onRetry={state.retry} />
      )}
    </>
  );
}
