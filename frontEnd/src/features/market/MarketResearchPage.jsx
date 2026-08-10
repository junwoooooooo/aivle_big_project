import { useCallback, useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useApiClient } from '../../shared/api/ApiClientProvider.jsx';
import { createMarketApi } from './marketApi.js';
import { projectRoutes } from '../../app/routing/projectRoutes.js';
import { Accordion, Alert, Badge, Button, Card, LoadingState } from '../../shared/ui';
import { GradeBadge, SourceLink } from './BmCanvas.jsx';
import useMarketPolling from './useMarketPolling.js';
import useCellFocus from './useCellFocus.js';
import {
  SCORE_STATE_VIEW,
  abbreviateKrw, bucketEvidence, competitorGaps, formatValue, hostOf,
} from './marketResult.js';
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
  const api = useMemo(() => createMarketApi(client, projectId), [client, projectId]);
  const [conceptKey, setConceptKey] = useState(SAMPLE_CONCEPTS[0][0]);

  const load = useCallback(() => api.currentMarketResearch(), [api]);
  const start = useCallback(() => api.startMarketResearch(conceptKey, today(), null),
    [api, conceptKey]);
  const { run, result, error, busy, loading, active, elapsed, trigger } = useMarketPolling(load, start);
  // KPI → 과목 섹션 착지. 포커스·rAF 함정은 훅 주석에 있다.
  const focus = useCellFocus('sec-');

  if (loading) return <LoadingState label="시장조사 결과를 불러오는 중" />;

  return (
    <section className="market-page">
      <div className="pipeline-page-heading">
        <p>4. 시장분석·기획 확정</p>
        <h2>시장조사 결과</h2>
        {!result ? (
          <span>공개 통계·공시·언론에서 관측된 것만 모은다.</span>
        ) : null}
      </div>

      <div className="market-page__actions">
        <Button onClick={trigger} disabled={busy || active}>
          {active ? '조사 중…' : result ? '다시 조사' : '시장조사 실행'}
        </Button>
      </div>

      {/* 임시 다리 — 컨셉이 DB 에서 오게 되면 이 블록은 통째로 사라진다.
          결과가 있으면 접는다. 첫 화면을 임시 다리가 먹지 않게. */}
      {result ? (
        <Accordion title="견본 컨셉 다시 고르기">
          <ConceptPicker conceptKey={conceptKey} setConceptKey={setConceptKey} disabled={busy || active} />
        </Accordion>
      ) : (
        <Card title="견본 컨셉">
          <ConceptPicker conceptKey={conceptKey} setConceptKey={setConceptKey} disabled={busy || active} />
        </Card>
      )}

      {error ? <Alert tone="danger">{error}</Alert> : null}
      {active ? <Alert tone="info">조사 중이다 — <strong>{elapsed}초</strong> 경과.</Alert> : null}
      {run?.state === 'FAILED' ? (
        <Alert tone="danger">
          실행이 실패했다{run.errorCode ? ` (${run.errorCode})` : ''}{run.errorReason ? `: ${run.errorReason}` : ''}.
          {run.retryable ? ' 다시 시도할 수 있다.' : ' 입력을 확인해야 한다.'}
        </Alert>
      ) : null}

      {!result ? (
        !active ? <Card><p>아직 조사한 적이 없다. 「시장조사 실행」을 눌러라.</p></Card> : null
      ) : (
        <ResultBody result={result} activeId={focus.active} onJump={focus.jump} onNext={() =>
          navigate(projectRoutes.businessModel(projectId))} />
      )}
    </section>
  );
}

function ResultBody({ result, activeId, onJump, onNext }) {
  const market = result.market ?? {};
  const bag = bucketEvidence(result);
  const score = Object.fromEntries((result.scorecard ?? []).map((row) => [row.subject, row]));
  const cited = (ids) => ids.map((id) => result.evidenceById.get(id)).filter(Boolean);

  const section = (n, title, subject, body) => (
    <Section n={n} title={title} id={`sec-${subject}`} row={score[subject]}
      active={activeId === subject}>{body}</Section>
  );

  return (
    <>
      <Kpis market={market} onJump={onJump} />
      <ReadingConditions market={market} />

      {section(1, '시장 크기', 'MARKET_SIZE',
        bag.size.length > 0
          ? <EvidenceTable rows={bag.size} />
          : <p className="bm-cell__none">모집단 관측이 없다.</p>)}

      {section(2, '성장률', 'GROWTH', <GrowthBody growth={market.growth} rows={bag.grow} />)}

      {section(3, '경쟁사', 'COMPETITOR',
        <CompetitorBody rows={bag.comp} gaps={competitorGaps(market.notFound)} />)}

      {section(4, '가격', 'PRICE', <PriceBody price={market.price} cited={cited(market.price?.evidenceIds ?? [])} />)}

      {section(5, '수요 근거', 'DEMAND',
        bag.demand.length > 0
          ? <EvidenceTable rows={bag.demand} quote />
          : <p className="bm-cell__none">수요를 뒷받침하는 관측이 없다.</p>)}

      {/* 요약은 예산이 모자라면 오지 않는다. 왔을 때만 그린다 — 건너뛴 사유는 실행 기록에 있다. */}
      {result.summary?.length ? (
        <Card title="핵심 요약">
          <ul className="market-summary">
            {result.summary.map((line) => (
              <li key={line.sentence}>
                {line.cell ? <span className="market-summary__cell">{line.cell}</span> : null}
                {line.sentence}
                {line.cardIds.map((id) => <code key={id}>{id}</code>)}
              </li>
            ))}
          </ul>
        </Card>
      ) : null}

      {section(6, '시장 규모 계산', 'CALCULATION', <CalcBody cards={bag.calc} />)}

      <div className="mr-actions">
        <Button onClick={onNext}>다음 — BM 분석</Button>
      </div>
    </>
  );
}

/** 결론 숫자를 등급과 **동시에** 준다. 숫자만 먼저 보이면 확정으로 읽힌다. */
function Kpis({ market, onJump }) {
  const price = market.price;
  const tile = (label, figure, to, sub) => {
    if (!figure) return null;
    const short = figure.unit === 'KRW' ? abbreviateKrw(figure.value) : null;
    const full = formatValue(figure.value, figure.unit);
    return (
      <button key={label} type="button" className="mr-kpi" onClick={() => onJump(to)}>
        <span>{label}</span>
        <b className="num">{short ?? full}</b>
        <small className="num">{sub ?? (short ? full : '')}</small>
        <GradeBadge grade={figure.grade} />
      </button>
    );
  };

  return (
    <div className="mr-kpis">
      {tile('TAM', market.tam, 'CALCULATION')}
      {tile('SAM', market.sam, 'CALCULATION')}
      {tile('연 성장률', market.growth, 'GROWTH', '2023 → 2024')}
      {price ? (
        <button type="button" className="mr-kpi" onClick={() => onJump('PRICE')}>
          <span>가격대 (월)</span>
          <b className="num">{abbreviateKrw(price.min) ?? formatValue(price.min, price.currency)}
            {'~'}{abbreviateKrw(price.max) ?? formatValue(price.max, price.currency)}</b>
          <small className="num">
            {formatValue(price.min, price.currency)} ~ {formatValue(price.max, price.currency)}
          </small>
          <GradeBadge grade={price.grade} />
        </button>
      ) : null}
    </div>
  );
}

/**
 * 값을 오독하게 만드는 문장만 모은다.
 * ⚠ **규칙상 지울 수 없다** — 이 문장들이 빠지면 추정이 확정으로 읽힌다.
 */
function ReadingConditions({ market }) {
  const lines = [];
  if (market.tam?.assumptions.length) {
    lines.push(`TAM·SAM 은 가정이 곱해진 추정이다 — ${market.tam.assumptions.join(' · ')}`);
  }
  if (market.price?.baseNote) lines.push(market.price.baseNote);
  if (!market.som) lines.push('SOM 은 산출하지 않았다 — 0 이 아니라 «안 쟀다»다.');
  if (market.growth?.assumptions.length) lines.push(market.growth.assumptions.at(-1));
  if (lines.length === 0) return null;

  return (
    <div className="mr-limits">
      <span>이 숫자를 읽는 조건</span>
      <ul>{lines.map((line) => <li key={line}>{line}</li>)}</ul>
    </div>
  );
}

function Section({ n, title, id, row, active, children }) {
  const view = row ? (SCORE_STATE_VIEW[row.state] ?? { label: row.state, tone: 'neutral' }) : null;
  return (
    <section id={id} className={`mr-sec${active ? ' is-on' : ''}`}>
      <div className="mr-sec__h">
        <span className="mr-sec__n">{n}</span>
        <h3>{title}</h3>
        {view ? <Badge tone={view.tone}>{view.label}</Badge> : null}
        <span>{row?.detail ?? ''}</span>
      </div>
      <div className="mr-sec__b">{children}</div>
    </section>
  );
}

function EvidenceTable({ rows, quote = false }) {
  return (
    <table className="mr-table">
      <thead>
        <tr><th>값</th><th>항목</th><th>기간</th><th>등급</th><th>출처</th></tr>
      </thead>
      <tbody>
        {rows.map((item) => (
          <tr key={item.id}>
            <td className="v num">{formatValue(item.value, item.unit)}</td>
            <td>
              {item.subject} · {item.metric}
              {quote && item.quote ? <div className="mr-quote">“{item.quote}”</div> : null}
              {/* 경계는 값과 한 몸이다. 접지 않는다. */}
              {item.caveats.map((line) => <div key={line} className="mr-caveat">{line}</div>)}
            </td>
            <td className="p num">{item.period ?? '—'}</td>
            <td><GradeBadge grade={item.grade} /></td>
            <td className="s"><SourceLink item={item} /></td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

function GrowthBody({ growth, rows }) {
  if (!growth) return <p className="bm-cell__none">성장률을 산출하지 않았다.</p>;
  return (
    <>
      <div className="mr-figs">
        <div>
          <span>연 성장률</span>
          <b className="num">{formatValue(growth.value, growth.unit)}</b>
          <small>{growth.formula ?? ''}</small>
        </div>
      </div>
      {rows.length > 0 ? <EvidenceTable rows={rows} /> : null}
      {growth.assumptions.length > 0 ? (
        <div className="mr-note">{growth.assumptions.join(' · ')}</div>
      ) : null}
    </>
  );
}

/**
 * 경쟁사 — 회사별 카드. **못 찾은 슬롯도 같은 카드에 세운다.**
 * 관측된 지표만 그리면 「이 회사는 이게 전부다」로 읽힌다.
 */
function CompetitorBody({ rows, gaps }) {
  const names = [...new Set([...rows.map((item) => item.subject), ...gaps.map(([name]) => name)])];
  if (names.length === 0) return <p className="bm-cell__none">경쟁사 관측이 없다.</p>;

  return (
    <>
      <div className="mr-comps">
        {names.map((name) => {
          const mine = rows.filter((item) => item.subject === name);
          const missing = gaps
            .filter(([subject]) => subject === name)
            .map(([, metric]) => metric)
            .filter((metric) => !mine.some((item) => item.metric === metric));
          return (
            <div key={name} className="mr-comp">
              <h4>{name}</h4>
              {mine.map((item) => (
                <div key={item.id}>
                  <span>{item.metric}</span>
                  <b className="num">{formatValue(item.value, item.unit)}</b>
                </div>
              ))}
              {missing.map((metric) => (
                <div key={metric}><span>{metric}</span><span className="none">못 찾음</span></div>
              ))}
              {mine.length > 0 ? (
                <div className="mr-comp__src"><SourceLink item={mine[0]} /></div>
              ) : null}
              {mine.flatMap((item) => item.caveats).map((line) => (
                <div key={line} className="mr-caveat">{line}</div>
              ))}
            </div>
          );
        })}
      </div>
      <div className="mr-note">
        수집 대상은 가입 매장 수·매출액·요금 같은 숫자다.{' '}
        <strong>기능·차별점 비교는 조사 항목에 없다.</strong>
      </div>
    </>
  );
}

/** 가격은 밴드로 읽어야 한다. 대표값 하나만 남으면 확정 단가로 읽힌다. */
function PriceBody({ price, cited }) {
  if (!price) return <p className="bm-cell__none">표시가격 관측이 없다.</p>;
  const hosts = new Set(cited.map((item) => hostOf(item.sourceUrl)).filter(Boolean));

  return (
    <>
      <div className="mr-figs">
        <div><span>최저</span><b className="num">{formatValue(price.min, price.currency)}</b></div>
        <div><span>대표값 (잠정)</span><b className="num">{formatValue(price.base, price.currency)}</b></div>
        <div><span>최고</span><b className="num">{formatValue(price.max, price.currency)}</b></div>
      </div>
      {cited.length > 0 ? <EvidenceTable rows={cited} /> : null}
      {/* 건수와 독립성은 다르다. 한 도메인에서 3건은 3중 확인이 아니다. */}
      {hosts.size === 1 && cited.length > 1 ? (
        <Alert tone="warning">
          {cited.length}건이지만 출처 도메인은 <strong>{[...hosts][0]} 하나</strong>다
          {' — '}{cited.length}중 확인이 아니라 <strong>1중 확인</strong>이다.
        </Alert>
      ) : null}
      {price.caveats.map((line) => <div key={line} className="mr-caveat">{line}</div>)}
    </>
  );
}

/** 계산 카드 — 입력마다 **뒷받침 근거가 있는지**를 같이 그린다. */
function CalcBody({ cards }) {
  if (cards.length === 0) return <p className="bm-cell__none">계산 카드가 없다.</p>;
  return (
    <>
      {cards.map((card) => {
        const inputs = Object.entries(card.inputs ?? {});
        return (
          <div key={card.id}>
            <div className="mr-figs">
              <div>
                <span>{card.metric}</span>
                <b className="num">{formatValue(card.value, card.unit)}</b>
                <small>{card.formula ?? ''}</small>
              </div>
            </div>
            <table className="mr-table">
              <tbody>
                {inputs.map(([name, value], index) => {
                  const backed = index < card.materialIds.length;
                  return (
                    <tr key={name}>
                      <td className="v num">
                        {typeof value === 'number' ? value.toLocaleString('ko-KR') : String(value)}
                      </td>
                      <td>{name}</td>
                      <td className="s">
                        {backed
                          ? <Badge tone="success">{card.materialIds[index]}</Badge>
                          : <Badge tone="warning">뒷받침 근거 없음</Badge>}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
            {card.assumptions.length > 0 ? (
              <div className="mr-note">
                {card.assumptions.map((line) => <div key={line}>{line}</div>)}
              </div>
            ) : null}
          </div>
        );
      })}
    </>
  );
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
