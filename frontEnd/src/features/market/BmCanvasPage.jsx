import { useCallback, useMemo } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useApiClient } from '../../shared/api/ApiClientProvider.jsx';
import { createMarketApi } from './marketApi.js';
import { projectRoutes } from '../../app/routing/projectRoutes.js';
import { Alert, Badge, Button, Card, LoadingState } from '../../shared/ui';
import BmCanvas, { BmCellDetails } from './BmCanvas.jsx';
import useCellFocus from './useCellFocus.js';
import useMarketPolling from './useMarketPolling.js';
import { DECISION_VIEW } from './marketResult.js';
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
  const api = useMemo(() => createMarketApi(client, projectId), [client, projectId]);

  const load = useCallback(() => api.currentBusinessModel(), [api]);
  // ⚠ 여기 실린 conceptId 는 **쓰이지 않는다.** 백엔드가 1단계 결과의 conceptId 를 그대로
  //    이어 쓴다 — 1단계와 다른 컨셉으로 판정하면 「관측은 A, 잣대는 B」가 되기 때문이다.
  const start = useCallback(() => api.startBusinessModel(String(projectId), today()),
    [api, projectId]);
  const { run, result, error, busy, loading, active, elapsed, trigger } = useMarketPolling(load, start);
  const focus = useCellFocus('bm-');

  if (loading) return <LoadingState label="BM 캔버스를 불러오는 중" />;

  const bm = result?.bm ?? null;
  const decision = bm ? DECISION_VIEW[bm.decision] : null;

  return (
    <section className="market-page">
      <div className="pipeline-page-heading">
        <p>5. BM 분석</p>
        <h2>비즈니스 모델 캔버스</h2>
        {!result ? (
          <span>시장조사에서 관측된 근거로만 채운다. 근거가 없는 칸은 비워 두고 사유를 적는다.</span>
        ) : null}
      </div>

      <div className="market-page__actions">
        <Button variant="ghost" onClick={() => navigate(projectRoutes.market(projectId))}>
          시장조사로
        </Button>
        <Button onClick={trigger} disabled={busy || active}>
          {active ? '생성 중…' : result ? '다시 생성' : '캔버스 만들기'}
        </Button>
      </div>

      {error ? <Alert tone="danger">{error}</Alert> : null}
      {active ? <Alert tone="info">캔버스를 만드는 중이다 — {elapsed}초 경과.</Alert> : null}
      {run?.state === 'FAILED' ? (
        <Alert tone="danger">생성이 실패했다{run.errorCode ? ` (${run.errorCode})` : ''}{run.errorReason ? `: ${run.errorReason}` : ''}.</Alert>
      ) : null}

      {!result ? (
        !active ? (
          <Card>
            <p>아직 캔버스가 없다. 「캔버스 만들기」를 눌러라.</p>
            <p className="market-note">시장조사를 먼저 끝내야 한다 — 근거 없이는 만들지 않는다.</p>
          </Card>
        ) : null
      ) : (
        <>
          {bm ? (
            <div className="ui-card bm-verdict">
              <h3>판정</h3>
              {decision ? <Badge tone={decision.tone}>{decision.label}</Badge> : null}
              <Badge tone="neutral">{CONFIDENCE_VIEW[bm.confidence] ?? bm.confidence ?? '신뢰도 미기재'}</Badge>
              <p>{bm.consistencySummary ?? bm.marketFitSummary ?? bm.summary ?? ''}</p>
            </div>
          ) : (
            <Alert tone="warning">
              BM 판정이 오지 않았다 — 시장조사 결과는 유효하다. 다시 생성해 볼 수 있다.
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

          {result.canvas ? <BmCellDetails cells={result.canvas} active={focus.active} /> : null}
        </>
      )}
    </section>
  );
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

function today() {
  return new Date().toISOString().slice(0, 10);
}
