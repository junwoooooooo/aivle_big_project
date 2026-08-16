import { useCallback, useEffect, useMemo, useState } from 'react';
import { useNavigate, useOutletContext, useParams } from 'react-router-dom';
import { useApiClient } from '../../shared/api/ApiClientProvider.jsx';
import { createMarketApi } from './marketApi.js';
import { projectRoutes } from '../../app/routing/projectRoutes.js';
import { Alert, Badge, Button, Card, Dialog, LoadingState, ProjectStageHeader, ProjectWorkspace } from '../../shared/ui';
import BmCanvas, { BmCellDetails } from './BmCanvas.jsx';
import BmPlanForm from './BmPlanForm.jsx';
import BmPlanPreview from './BmPlanPreview.jsx';
import useCellFocus from './useCellFocus.js';
import useMarketLiveState from './useMarketPolling.js';
import { DECISION_VIEW } from './marketResult.js';
import { draftFrom, emptyCellNames, emptyDraft, toPayload } from './bmPlan.js';
import './market.css';

/** 신뢰도 코드 → 사람이 읽는 말. 모르는 코드는 원문 그대로 통과시킨다. */
const CONFIDENCE_VIEW = { HIGH: '확신 높음', MEDIUM: '확신 중간', LOW: '확신 낮음' };

/**
 * 2단계 — BM 캔버스. 1단계 결과를 근거로 채운다.
 *
 * <p>읽는 순서: 판정 → 집계 → 9칸 요약 → 강점·약점·위험 → 칸별 세부.
 * 근거는 <b>그것을 쓴 칸 옆</b>에 붙는다 — 하단에 근거를 몰아 두면 칸의 문장과 근거가
 * 화면 두 곳으로 갈라져, 어느 문장이 무엇에 기대는지가 사라진다.
 */
export default function BmCanvasPage() {
  const { projectId } = useParams();
  const navigate = useNavigate();
  const client = useApiClient();
  const { liveRevision = 0 } = useOutletContext() ?? {};
  const api = useMemo(() => createMarketApi(client, projectId), [client, projectId]);

  const load = useCallback(() => api.currentBusinessModel(), [api]);
  // ⚠ 여기 실린 conceptId 는 **쓰이지 않는다.** 백엔드가 1단계 결과의 conceptId 를 그대로
  //    이어 쓴다 — 1단계와 다른 컨셉으로 판정하면 「관측은 A, 잣대는 B」가 되기 때문이다.
  const start = useCallback(() => api.startBusinessModel(), [api]);
  const { run, result, stale, error, busy, loading, active, elapsed, trigger } =
    useMarketLiveState(load, start, liveRevision);
  const focus = useCellFocus('bm-');
  const [editingPlan, setEditingPlan] = useState(false);
  const plan = useBmPlan(api, trigger, () => setEditingPlan(false));

  if (loading || plan.loading) return <LoadingState label="사업 모델을 불러오는 중" />;

  // 결과가 없고 아직 돌지도 않았으면 **먼저 물어본다.** 「캔버스 만들기」 버튼 하나로
  // 시작하면 계획 칸이 빈 채로 나오고, 그 빈 칸이 조사 실패처럼 읽힌다.
  //
  // ⚠ **이미 캔버스가 있어도 들어올 수 있어야 한다.** 처음엔 `!result` 로만 갈랐는데,
  //    그러면 한 번 돌린 프로젝트는 계획 화면에 **영영 못 들어간다** — 계획을 고칠 길이
  //    없으니 빈 칸도 영영 빈 채다(실측: 사용자가 그 상태를 봤다).
  if (editingPlan || (!result && !active && plan.revision === 0)) {
    return (
      <PlanPhase projectId={projectId} navigate={navigate} plan={plan}
        error={error} run={run}
        onBack={result ? () => setEditingPlan(false) : null} />
    );
  }

  if (!result && !active && plan.revision > 0) {
    return <PreparedPlanPhase projectId={projectId} navigate={navigate} plan={plan} error={error}
      onEdit={() => setEditingPlan(true)} onCreate={trigger} busy={busy} />;
  }

  const bm = result?.bm ?? null;
  const decision = bm ? DECISION_VIEW[bm.decision] : null;

  return (
    <ProjectWorkspace as="section" mode="analyze" className="market-page">
      <ProjectStageHeader step={4} eyebrow="수익 구조" title="사업이 고객에게 가치를 전달하고 수익을 만드는 방식을 확인하세요"
        description="시장조사에서 확인된 근거로 캔버스를 구성하며, 근거가 없는 항목은 비워 둡니다." />

      <div className="market-page__actions">
        <Button variant="ghost" onClick={() => navigate(projectRoutes.market(projectId))}>
          시장조사로
        </Button>
        {/* 계획 칸이 비었으면 고칠 길이 있어야 한다. 없으면 그 칸은 영영 빈 채다. */}
        <Button variant="outline" onClick={() => setEditingPlan(true)} disabled={busy || active}>
          운영 정보 수정
        </Button>
        <Button onClick={trigger} disabled={busy || active}>
          {active ? '생성 중…' : result ? '다시 생성' : '캔버스 만들기'}
        </Button>
        {result ? <Button onClick={() => navigate(projectRoutes.techOps(projectId))}>다음 - 기술·운영 분석</Button> : null}
      </div>

      {error ? <Alert tone="danger">{error}</Alert> : null}
      {stale ? <Alert tone="warning">시장 분석이 바뀌었습니다. 최신 내용으로 다시 만들어 주세요.</Alert> : null}
      {active ? <Alert tone="info">수익 구조를 만드는 중입니다. {elapsed}초 경과</Alert> : null}
      {run?.state === 'FAILED' ? (
        <Alert tone="danger">수익 구조 결과를 만들지 못했습니다. 잠시 후 다시 시도해 주세요.</Alert>
      ) : null}

      {!result ? null : (
        <>
          {bm ? (
            <div className="ui-card bm-verdict">
              <h3>판정</h3>
              {decision ? <Badge tone={decision.tone}>{decision.label}</Badge> : null}
              <Badge tone="neutral">{CONFIDENCE_VIEW[bm.confidence] ?? bm.confidence ?? '신뢰도 미기재'}</Badge>
              <p>{bm.summary ?? ''}</p>
              <dl className="bm-verdict__details">
                <div><dt>시장 적합성</dt><dd>{bm.marketFitStatus || '미기재'} · {bm.marketFitSummary || '요약 없음'}</dd></div>
                <div><dt>내부 일관성</dt><dd>{bm.consistencyStatus || '미기재'} · {bm.consistencySummary || '요약 없음'}</dd></div>
              </dl>
            </div>
          ) : (
            <Alert tone="warning">
              수익 구조 판정을 받지 못했습니다. 시장 분석 결과는 유지되며 다시 만들 수 있습니다.
            </Alert>
          )}

          {result.canvas ? <BmCanvas cells={result.canvas} onJump={focus.jump} /> : null}

          {bm ? (
            <div className="bm-swr">
              <SwrBox title="강점" items={bm.strengths} tone="var(--color-status-success)" />
              <SwrBox title="약점" items={bm.weaknesses} tone="var(--color-status-warning)" />
              <SwrBox title="위험" items={bm.risks} tone="var(--color-status-danger)" />
            </div>
          ) : null}

          {bm?.legal ? <Card title="법률 결과 반영">
            <p>사용 여부: <strong>{bm.legal.used ? '사용함' : '사용하지 않음'}</strong></p>
            <p>상태: <strong>{bm.legal.status || 'UNVERIFIED'}</strong></p>
            <p>{bm.legal.summary || '법률 요약 없음'}</p>
            <SwrBox title="법률 위험" items={bm.legal.risks} tone="var(--color-status-danger)" />
            <SwrBox title="필수 조치" items={bm.legal.requiredActions} tone="var(--color-status-warning)" />
          </Card> : null}

          {bm?.financialHandoff ? <FinancialHandoff value={bm.financialHandoff} /> : null}

          {result.canvas ? <BmCellDetails cells={result.canvas} active={focus.active} /> : null}
        </>
      )}
    </ProjectWorkspace>
  );
}

function FinancialHandoff({ value }) {
  const numbers = [
    ['기준 가격', value.priceBase], ['가격 하한', value.priceMin], ['가격 상한', value.priceMax],
    ['TAM', value.tam], ['SAM', value.sam], ['SOM', value.som],
    ['시장 성장률', value.marketGrowthRate], ['예상 매출', value.expectedRevenue], ['단위 원가', value.unitCost],
  ];
  return <Card title="재무 분석에 사용할 정보">
    <p>다음 단계 준비: <strong>{value.handoffStatus ? '준비됨' : '정보 없음'}</strong></p>
    <p>수익 모델: {value.revenueModel || '미입력'}</p>
    <dl className="bm-verdict__details">{numbers.map(([label, number]) => (
      <div key={label}><dt>{label}</dt><dd>{number ?? '미측정'}</dd></div>
    ))}</dl>
    <p>고정비 항목: {(value.fixedCostItems || []).length
      ? JSON.stringify(value.fixedCostItems) : '없음'}</p>
    <p>변동비 항목: {(value.variableCostItems || []).length
      ? JSON.stringify(value.variableCostItems) : '없음'}</p>
    <p>누락된 재무 입력: {(value.missingFinancialInputs || []).join(', ') || '없음'}</p>
  </Card>;
}

/**
 * 실행 계획 초안 — 불러오기 · 저장 · 「비었는데 진행할까」 확인.
 *
 * <p>초안을 실행 요청 바디에 실지 않고 **따로 저장**한다. 그래야 새로고침에 안 사라지고,
 * 「어느 계획으로 돌렸나」가 감사 기록에 남는다.
 */
function useBmPlan(api, trigger, onStarted) {
  const [draft, setDraft] = useState(emptyDraft);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [revision, setRevision] = useState(0);
  const [pendingEmpty, setPendingEmpty] = useState(null);
  const [failure, setFailure] = useState(null);

  useEffect(() => {
    let alive = true;
    api.currentBmPlan()
      .then((payload) => { if (alive) { setDraft(draftFrom(payload)); setRevision(payload?.revision ?? 0); } })
      // 초안을 못 읽는 것은 실행을 막을 일이 아니다 — 빈 폼으로 연다.
      .catch(() => {})
      .finally(() => { if (alive) setLoading(false); });
    return () => { alive = false; };
  }, [api]);

  const change = useCallback((key, value) => {
    setDraft((prev) => ({ ...prev, [key]: value }));
  }, []);

  const run = useCallback(async () => {
    setSaving(true);
    setFailure(null);
    try {
      const { plan, constraints } = toPayload(draft);
      const saved = await api.saveBmPlan(plan, constraints);
      setRevision((current) => saved?.revision ?? current);
      await trigger();
      onStarted?.();
    } catch (problem) {
      setFailure(problem?.message ?? '계획을 저장하지 못했다.');
    } finally {
      setSaving(false);
      setPendingEmpty(null);
    }
  }, [api, draft, trigger, onStarted]);

  const submit = useCallback(() => {
    const empty = emptyCellNames(draft);
    // 빈 칸이 있으면 **무엇이 빌지 이름으로** 알리고 확인을 받는다.
    if (empty.length > 0) { setPendingEmpty(empty); return; }
    run();
  }, [draft, run]);

  return {
    draft, revision, loading, saving, failure, pendingEmpty,
    change, submit, confirm: run, cancel: () => setPendingEmpty(null),
  };
}

function PlanPhase({ projectId, navigate, plan, error, run, onBack }) {
  return (
    <section className="market-page">
      <div className="pipeline-page-heading">
        <p>4. 사업 모델 검토</p>
        <h2>운영 정보 확인</h2>
        <span>사업 모델을 검토할 때 사용할 운영 정보를 정리합니다. 모든 항목은 선택 입력이며 정확히 정해지지 않았다면 비워 두어도 됩니다.</span>
      </div>

      <div className="market-page__actions">
        <Button variant="ghost" onClick={() => navigate(projectRoutes.market(projectId))}>
          시장조사로
        </Button>
        {onBack ? (
          <Button variant="ghost" onClick={onBack}>지금 캔버스 보기</Button>
        ) : null}
      </div>

      {error ? <Alert tone="danger">{error}</Alert> : null}
      {plan.failure ? <Alert tone="danger">{plan.failure}</Alert> : null}
      {run?.state === 'FAILED' ? (
        <Alert tone="danger">생성이 실패했다{run.errorCode ? ` (${run.errorCode})` : ''}.</Alert>
      ) : null}

      <div className="bm-plan__split">
        <Card title="사업 운영 정보">
          <BmPlanForm draft={plan.draft} onChange={plan.change}
            onSubmit={plan.submit} busy={plan.saving} />
        </Card>
        <BmPlanPreview draft={plan.draft} />
      </div>

      <Dialog
        open={plan.pendingEmpty !== null}
        onClose={plan.cancel}
        title="비어 있는 칸이 있습니다"
      >
        <p>
          <strong>{(plan.pendingEmpty ?? []).join(', ')}</strong> 칸이 비어 있습니다.
        </p>
        <p className="market-note">
          입력하지 않은 항목은 빈 채로 진행합니다. 사업 모델 검토 중 나중에 다시 추가할 수 있습니다.
        </p>
        <div className="mr-actions">
          <Button variant="ghost" onClick={plan.cancel}>돌아가서 채우기</Button>
          <Button onClick={plan.confirm} disabled={plan.saving}>이대로 진행</Button>
        </div>
      </Dialog>
    </section>
  );
}

function PreparedPlanPhase({ projectId, navigate, plan, error, onEdit, onCreate, busy }) {
  return <ProjectWorkspace as="section" mode="analyze" className="market-page">
    <ProjectStageHeader step={4} eyebrow="사업 모델 검토" title="저장한 운영 정보로 사업 모델을 검토할 수 있습니다"
      description="사업 검증 준비에서 저장한 운영 정보를 사용합니다. 필요한 경우 시작 전에 수정할 수 있습니다." />
    <div className="market-page__actions"><Button variant="ghost" onClick={() => navigate(projectRoutes.market(projectId))}>시장조사로</Button><Button variant="outline" onClick={onEdit}>준비 정보 보기·수정</Button><Button onClick={onCreate} disabled={busy}>{busy ? '준비 중…' : '캔버스 만들기'}</Button></div>
    {error ? <Alert tone="danger">{error}</Alert> : null}
    {plan.failure ? <Alert tone="danger">{plan.failure}</Alert> : null}
    <section className="bm-plan-prepared"><strong>운영 정보 준비 완료</strong><span>저장 수정 {plan.revision} · 사업 모델을 만들 때 이 정보를 사용합니다.</span></section>
  </ProjectWorkspace>;
}

function SwrBox({ title, items, tone }) {
  return (
    <div>
      <h4 style={{ color: tone }}>{title}</h4>
      <ul>
        {items.length > 0
          ? items.map((line) => <li key={line}>{line}</li>)
          : <li className="bm-swr__none">없음</li>}
      </ul>
    </div>
  );
}
