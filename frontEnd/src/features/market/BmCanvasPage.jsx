import { useCallback, useMemo } from 'react';
import { useNavigate, useOutletContext, useParams } from 'react-router-dom';
import { useApiClient } from '../../shared/api/ApiClientProvider.jsx';
import { createMarketApi } from './marketApi.js';
import { projectRoutes } from '../../app/routing/projectRoutes.js';
import { Alert, Button, Card, LoadingState, ProjectStageHeader, ProjectWorkspace } from '../../shared/ui';
// 캔버스와 칸 세부는 `BmResultBody` 안에 있다 — 이 파일은 셸만 갖는다.
import { BmResultBody } from './BmResultBody.jsx';
import useMarketLiveState from './useMarketPolling.js';
import './market.css';


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
  const { run, result, error, busy, loading, active, elapsed, trigger } =
    useMarketLiveState(load, start, liveRevision);

  if (loading) return <LoadingState label="BM 캔버스를 불러오는 중" />;


  const bm = result?.bm ?? null;

  return (
    <ProjectWorkspace as="section" mode="analyze" className="market-page">
      {/* ⚠ 판정 배지(「수정 필요」·「확신 중간」)를 뺐다(2026-08-16 사용자 지시).
          그 한 마디는 캔버스가 칸마다 이미 더 정확하게 말한다 — 어느 칸이 근거를 못 얻었는지.
          ⚠ **잃는 것**: 화면에 한눈에 보이는 총평이 없다. 판정 값은 봉투에 그대로 있다. */}
      <ProjectStageHeader step={2} eyebrow="수익 구조" title="사업이 고객에게 가치를 전달하고 수익을 만드는 방식을 확인하세요"
        description="시장조사에서 확인된 근거로 캔버스를 구성하며, 근거가 없는 항목은 비워 둡니다." />

      <div className="market-page__actions">
        <Button variant="ghost" onClick={() => navigate(projectRoutes.market(projectId))}>
          시장조사로
        </Button>
        {/* ⚠ 「실행 계획 고치기」는 **없앴다**(2026-08-16 사용자 지시). 계획은 사업안 확정
            자리에서 한 번 받고, 이 화면은 결과만 본다 — 같은 폼이 두 자리에 있으면
            어느 쪽 값으로 돌았는지가 갈린다.
            ⚠ **잃는 것**: 한 번 돌린 뒤에는 계획을 고칠 길이 화면에 없다. 고치려면
              사업안 단계로 돌아가야 한다. */}
        {/* ⚠ 「다시 생성」을 뺐다(2026-08-16 사용자 지시). 남은 것은 **처음 만들기**뿐이다.
            ⚠ **잃는 것 둘**: ① BM 이 실패해도 화면에서 다시 걸 길이 없다.
              ② 다듬기 첫 라운드는 BM 채택이 걸어 주므로, 다시 돌릴 길이 없으면
                 다듬기도 다시 못 건다. 되살릴 자리는 이 버튼이다. */}
        {!result ? (
          <Button onClick={trigger} disabled={busy || active}>
            {active ? '생성 중…' : '캔버스 만들기'}
          </Button>
        ) : null}
        {/* 세 걸음의 가운데다 — 다음은 기술·운영이 아니라 **컨셉 다듬기**다. */}
        {result ? <Button onClick={() => navigate(projectRoutes.conceptRefinement(projectId))}>다음 — 컨셉 다듬기</Button> : null}
      </div>

      {error ? <Alert tone="danger">{error}</Alert> : null}
      {/* ⚠ 「시장 분석이 바뀌었습니다」 경고는 안 세운다(2026-08-16 사용자 지시).
          ⚠ 잃는 것: 옛 시장조사로 만든 캔버스를 보고 있다는 사실이 화면에서 사라진다. */}
      {active ? <Alert tone="info">수익 구조를 만드는 중입니다. {elapsed}초 경과</Alert> : null}
      {run?.state === 'FAILED' ? (
        <Alert tone="danger">수익 구조 결과를 만들지 못했습니다. 잠시 후 다시 시도해 주세요.</Alert>
      ) : null}

      {!result ? null : (
        <>
          {/* 캔버스 · 누른 칸 세부 · 강점/약점/위험은 저쪽이 정본이다.
              ⚠ <b>판정 카드는 위에 그대로 둔다.</b> `BmResultBody` 가 판정을 안 그리는 것은
                 제 옛 컨테이너가 <b>제목 옆에 배지를 이미 세웠기</b> 때문인데, 이 셸에는
                 그 배지가 없다 — 빼면 판정이 화면에서 통째로 사라진다.
              ⚠ 「판정이 안 왔다」 경고는 저쪽이 갖는다. 여기 else 가지를 지운 이유다 —
                 안 지우면 같은 말이 두 번 뜬다. */}
          <BmResultBody result={result} />

          {/* ⚠ **안 썼으면 카드를 세우지 않는다.** 옛 판은 「사용하지 않음 · UNVERIFIED」를
              띄우고 그 아래에 「법률 위험 — 없음」·「필수 조치 — 없음」을 세웠다. 법을 아예
              안 봤는데 위험이 없다고 말하는 셈이라 **틀린 안심**이다. 한 줄로만 말한다. */}
          {bm?.legal?.used ? <Card title="법률 결과 반영">
            <p>상태: <strong>{bm.legal.status || 'UNVERIFIED'}</strong></p>
            <p>{bm.legal.summary || '법률 요약이 오지 않았어요.'}</p>
            <SwrBox title="법률 위험" items={bm.legal.risks} tone="var(--color-status-danger)" />
            <SwrBox title="필수 조치" items={bm.legal.requiredActions} tone="var(--color-status-warning)" />
          </Card> : bm?.legal ? (
            <p className="market-note">법률 결과는 이번 판정에 <strong>반영되지 않았어요</strong> — 위험이 없다는 뜻이 아니에요.</p>
          ) : null}

          {/* ⚠ 「재무 분석에 사용할 정보」 카드는 **여기서 안 그린다**(2026-08-16 사용자 지시).
              값은 봉투에 그대로 실려 있고 재무 단계가 받아 간다 — 없앤 것은 «두 번째 자리»다. */}
        </>
      )}
    </ProjectWorkspace>
  );
}



function SwrBox({ title, items, tone }) {
  return (
    <div>
      <h4 style={{ color: tone }}>{title}</h4>
      <ul>
        {/* ⚠ **「없음」이라고 쓰지 않는다.** *없다* 와 *적지 못했다* 는 다른 말이고,
            판정이 「수정 필요」인 화면에서 「위험 — 없음」은 틀린 안심이다.
            `BmResultBody` 의 같은 부품과 문구를 맞춘다 — 한 화면에서 두 말을 하면 안 된다. */}
        {items.length > 0
          ? items.map((line) => <li key={line}>{line}</li>)
          : <li className="bm-swr__none">이번 실행은 이 칸을 <b>적지 못했어요</b> — 없다는 뜻이 아니에요</li>}
      </ul>
    </div>
  );
}
