import { useCallback, useMemo, useState } from 'react';
import { useNavigate, useOutletContext, useParams } from 'react-router-dom';
import { useApiClient } from '../../shared/api/ApiClientProvider.jsx';
import { createMarketApi } from './marketApi.js';
import { marketRunFailureMessage } from './marketRuntime.js';
import { projectRoutes } from '../../app/routing/projectRoutes.js';
import { traceDetailForDisplay, useJobEvents } from '../../shared/async-events/index.js';
import { Accordion, Alert, Button, Card, LoadingState, ProjectStageHeader, ProjectWorkspace } from '../../shared/ui';
import useMarketLiveState from './useMarketPolling.js';
import useCellFocus from './useCellFocus.js';
import ResearchBasisCard from './ResearchBasisCard.jsx';
// 결과 렌더는 이 파일이 갖지 않는다 — 10과목 + 2·8·9절을 그리는 정본은 저쪽이다.
// 이 파일이 갖는 것은 셸뿐이다: 제목 · 실행 버튼 · recollect · stale · 진행 표시.
import { MarketResultBody } from './MarketResultBody.jsx';
import './market.css';

/**
 * 견본 컨셉 — <b>임시 다리다</b>.
 *
 * <p>제품에서 컨셉은 DB 에 있고 콘셉트 생성 단계가 만든다. 그때 이 버튼은 없어진다.
 * 지금은 AI 쪽 `pipeline.CONCEPTS` 의 이름표를 그대로 보내고,
 * 그 표가 (컨셉 파일, 원장) 을 정한다.
 */
const SAMPLE_CONCEPTS = [
  ['beauty-noshow', '미용실 노쇼 관리'],
  ['household-ledger', '가계부 앱'],
  ['pet-treat', '반려동물 수제 간식'],
];
const DEMO_MODE = import.meta.env.DEV && import.meta.env.VITE_MARKET_FIXTURE_MODE === 'true';

/**
 * 1단계 — 시장조사.
 *
 * <p><b>성적표 7과목이 곧 목차다.</b> 성적표를 맨 아래 접어 두면 「무엇을 쟀나」와
 * 「무엇이 나왔나」가 따로 놀아, 읽는 사람이 빠진 과목을 못 본다. 그래서 과목을 섹션으로
 * 세우고 그 과목의 상태·내용을 섹션 머리에 건다.
 */
export default function MarketResearchPage() {
  const { projectId } = useParams();
  const navigate = useNavigate();
  const client = useApiClient();
  const { liveRevision = 0 } = useOutletContext() ?? {};
  const api = useMemo(() => createMarketApi(client, projectId), [client, projectId]);
  const [conceptKey, setConceptKey] = useState(SAMPLE_CONCEPTS[0][0]);
  const [recollectSlots, setRecollectSlots] = useState('');
  const [recollectFrom, setRecollectFrom] = useState('a4');
  const [slotsFrom, setSlotsFrom] = useState('source');

  const load = useCallback(() => api.currentMarketResearch(), [api]);
  const start = useCallback(() => api.startMarketResearch(today()), [api]);
  const { run, result, version, source, stale, error, busy, loading, active, elapsed,
    trigger, triggerAction } =
    useMarketLiveState(load, start, liveRevision);
  const jobEvents = useJobEvents(run?.taskRunId);
  // KPI → 과목 섹션 착지. 포커스·rAF 함정은 훅 주석에 있다.
  const focus = useCellFocus('sec-');

  if (loading) return <LoadingState label="시장조사 결과를 불러오는 중" />;

  return (
    <ProjectWorkspace as="section" mode="analyze" className="market-page">
      <ProjectStageHeader step={1} eyebrow="사업 검증" title="시장 상황과 경쟁 환경을 확인하세요"
        description="공개 통계, 공시, 언론에서 확인된 근거를 시장 규모·경쟁·고객 관점으로 정리합니다." />

      <div className="market-page__actions">
        <Button onClick={trigger} disabled={busy || active}>
          {active ? '조사 중…' : result ? '다시 조사' : '시장조사 실행'}
        </Button>
      </div>

      {/* 임시 다리 — 컨셉이 DB 에서 오게 되면 이 블록은 통째로 사라진다.
          결과가 있으면 접는다. 첫 화면을 임시 다리가 먹지 않게. */}
      {DEMO_MODE && (result ? (
        <Accordion title="견본 컨셉 다시 고르기">
          <ConceptPicker conceptKey={conceptKey} setConceptKey={setConceptKey} disabled={busy || active} />
        </Accordion>
      ) : (
        <Card title="견본 컨셉">
          <ConceptPicker conceptKey={conceptKey} setConceptKey={setConceptKey} disabled={busy || active} />
        </Card>
      ))}
      {/* 무엇으로 조사하는지를 **값으로** 보인다 — 한 줄 설명만으로는 자기가 고른 값이
          실렸는지 확인할 길이 없다. */}
      {!DEMO_MODE ? <ResearchBasisCard client={client} api={api} projectId={projectId}
        conceptName={source?.conceptName} /> : null}
      {/* ⚠ 「경쟁·현재 대안 씨앗」은 **여기서 뺐다**(2026-08-16 사용자 지시).
          `CompetitorSeedForm` 과 그 API 는 지우지 않았다 — 되살릴 자리가 생기면 한 줄이다. */}
      {result && version && !stale ? <Accordion title="기존 원장에서 근거 다시 수집">
        <p>현재 Market version의 검증된 원장을 복원해 전체 또는 지정 슬롯만 다시 수집합니다.</p>
        <div className="project-form-layout">
        <label>슬롯 ID (쉼표 구분, 비우면 전체)
          <input value={recollectSlots} disabled={busy || active}
            onChange={(event) => setRecollectSlots(event.target.value)} placeholder="S1,S5" />
        </label>
        <label>복원 단계
          <select value={recollectFrom} disabled={busy || active}
            onChange={(event) => setRecollectFrom(event.target.value)}>
            <option value="a4">A4부터</option><option value="extract">추출부터</option>
          </select>
        </label>
        <label>사람 입력 슬롯 기준
          <select value={slotsFrom} disabled={busy || active}
            onChange={(event) => setSlotsFrom(event.target.value)}>
            <option value="source">원본 유지</option><option value="current">현재 값 사용</option>
          </select>
        </label>
        </div>
        <Button disabled={busy || active} onClick={() => triggerAction(() =>
          api.recollectMarketResearch(version.id, {
            asOf: today(), slots: recollectSlots, from: recollectFrom, slotsFrom,
          }))}>원장 복원 후 다시 수집</Button>
      </Accordion> : null}

      {error ? <Alert tone="danger">{error}</Alert> : null}
      {/* ⚠ 「바뀌었습니다」 경고는 **안 세운다**(2026-08-16 사용자 지시). 조사 결과가 이미
          화면에 있는데 그 위에 경고가 서면, 읽는 내내 「이걸 믿어도 되나」가 걸린다.
          ⚠ **잃는 것을 적어 둔다**: 사업안을 고친 뒤 옛 결과를 보고 있다는 사실이 화면에서
          사라진다. 되살릴 자리는 이 배너가 아니라 「다시 조사」 버튼 옆이다. */}
      {active ? <Alert tone="info">조사 중이다 — <strong>{elapsed}초</strong> 경과.
        <MarketProgress events={jobEvents.events} />
      </Alert> : null}
      {/* 실패는 **한 줄로만** 말한다. 오류 코드(「기술 정보」)와 「입력을 확인해야 한다」는
          사용자가 할 수 있는 일을 알려 주지 않으면서 화면만 무겁게 했다. */}
      {run?.state === 'FAILED' ? (
        <Alert tone="danger">{marketRunFailureMessage(run.errorCode)}. 다시 조사해 주세요.</Alert>
      ) : null}

      {!result ? (
        !active ? <Card><p>아직 조사한 적이 없어요. 위의 「시장조사 실행」을 누르면 시작돼요.</p></Card> : null
      ) : (
        <>
          <MarketResultBody result={result} activeId={focus.active} />
          <div className="mr-actions">
            <Button onClick={() => navigate(projectRoutes.businessModel(projectId))}>
              다음 — BM 분석
            </Button>
          </div>
        </>
      )}
    </ProjectWorkspace>
  );
}

export function MarketProgress({ events = [] }) {
  const latest = [...events].reverse().find((event) => event?.messageKey === 'job.market.trace');
  const detail = traceDetailForDisplay(latest);
  return detail ? <span className="market-page__live-progress">{detail}</span> : null;
}

function ConceptPicker({ conceptKey, setConceptKey, disabled }) {
  return (
    <div className="market-concept-picker">
      {SAMPLE_CONCEPTS.map(([key, label]) => (
        <Button
          key={key}
          variant={key === conceptKey ? 'primary' : 'outline'}
          aria-pressed={key === conceptKey}
          disabled={disabled}
          onClick={() => setConceptKey(key)}
        >
          {label}
        </Button>
      ))}
    </div>
  );
}

function today() {
  return new Date().toISOString().slice(0, 10);
}
