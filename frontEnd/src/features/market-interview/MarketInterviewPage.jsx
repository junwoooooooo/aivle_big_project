import { useCallback, useEffect, useMemo, useState } from 'react';
import { useParams } from 'react-router-dom';

import { useApiClient } from '../../shared/api/ApiClientProvider.jsx';
import { getUserErrorMessage } from '../../shared/api/apiError.js';
import { Alert, Button, Card, LoadingState } from '../../shared/ui';
import ConceptBoardEditor from './ConceptBoardEditor.jsx';
import InterviewCard from './InterviewCard.jsx';
import SampleSizePicker from './SampleSizePicker.jsx';
import { createMarketInterviewApi } from './marketInterviewApi.js';
import {
  COMPREHENSION_VIEW,
  DIFFERENTIATION_VIEW,
  mentionText,
  renderBoard,
} from './marketInterviewResult.js';
import useMarketInterviewPolling from './useMarketInterviewPolling.js';
import './market-interview.css';

const EMPTY_BOARD = Object.freeze({
  conceptName: '', targetUsers: '', problemScenario: '',
  featureSet: [], differentiators: '', priceKrw: null,
});

/** 이해도 막대 순서 — 좋은 쪽부터. 「판정 못 함」은 0 이면 안 그린다. */
const COMPREHENSION_ORDER = Object.freeze(['accurate', 'partial', 'misunderstood', 'unclassified']);
const DIFFERENTIATION_ORDER = Object.freeze(['different', 'similar', 'unclear', 'unclassified']);

/**
 * 세 층 — 이 화면의 정직성 장치다.
 *
 * 어디까지가 집계 그대로이고, 어디부터가 계산이고, 어디까지가 응답자가 실제로 한 말인지를
 * 화면이 <b>스스로 밝힌다</b>. 밝히지 않으면 「AI 가 이렇게 판단했다」로 읽히는데,
 * 이 조사에는 그런 판단이 없다.
 *
 * ⚠ 2026-08-15 부터 <b>띠가 아니라 절마다 붙는 딱지</b>로 쓴다(`LayerTag`). 띠는 절을
 *   층 순서대로 묶어 놓아서 <b>순서를 못 바꾸게</b> 했고, 이 화면의 병이 바로 그 순서였다.
 */
const LAYERS = Object.freeze([
  { key: 'fact', title: 'Fact', detail: '집계 그대로' },
  { key: 'insight', title: 'Insight', detail: '결정론 교차만 · AI 호출 0회' },
  { key: 'sowhat', title: 'So-What', detail: '응답자 발언 범위만' },
]);

/**
 * 시장 인터뷰 화면 — 확정된 사업안을 던지고 사람들의 <b>말</b>을 듣는다.
 *
 * <p>이 화면이 파는 것은 수치가 아니라 언어다. 유일한 수치인 「언급 수」도
 * <b>이 표본에서 몇 명이 그 말을 했는지</b>일 뿐이다. 그래서 세 가지를 화면이 지킨다:
 *
 * <ol>
 *   <li><b>백분율을 쓰지 않는다.</b> 「20명 중 7명」으로만 쓴다 — 「35%」로 쓰는 순간
 *       「시장의 35%」로 읽힌다.</li>
 *   <li><b>이해도를 맨 위에 둔다.</b> 오해가 많으면 아래의 「끌리는 점」은 읽을 값이 아니다.
 *       컨셉이 나쁜 게 아니라 설명이 나쁜 것이고, 고칠 곳이 완전히 다르다.</li>
 *   <li><b>분모는 답한 사람 수다.</b> 뽑은 사람 수가 아니다.</li>
 * </ol>
 */
export default function MarketInterviewPage() {
  const { projectId } = useParams();
  const client = useApiClient();
  const api = useMemo(() => createMarketInterviewApi(client, projectId), [client, projectId]);

  const [board, setBoard] = useState(EMPTY_BOARD);
  const [boardError, setBoardError] = useState(null);
  const [boardLoading, setBoardLoading] = useState(true);
  const [sampleSize, setSampleSize] = useState(40);

  useEffect(() => {
    let alive = true;
    (async () => {
      try {
        const loaded = await api.board();
        if (alive) setBoard({ ...EMPTY_BOARD, ...loaded });
      } catch (failure) {
        // 확정된 사업안이 없으면 404 다. 견본으로 떨어지지 않는다 — 서버가 보낸 문구를
        // 그대로 보인다(문구의 정본은 서버다).
        if (alive) setBoardError(failure?.message ?? getUserErrorMessage(failure));
      } finally {
        if (alive) setBoardLoading(false);
      }
    })();
    return () => { alive = false; };
  }, [api]);

  const load = useCallback(() => api.currentInterview(), [api]);
  const start = useCallback(() => api.startInterview({
    ...board,
    featureSet: board.featureSet.map((f) => f.trim()).filter(Boolean),
  }, sampleSize), [api, board, sampleSize]);

  const { run, result, error, busy, loading, active, elapsed, trigger } =
    useMarketInterviewPolling(load, start);

  // 파생값은 effect 가 아니라 렌더에서 만든다.
  const preview = renderBoard(board);
  const ready = board.conceptName.trim().length > 0 && !boardError;
  const canRun = ready && !busy && !active;

  if (loading || boardLoading) return <LoadingState label="시장 인터뷰를 불러오는 중" />;

  return (
    <section className="mi-page">
      <InterviewSteps ready={ready} active={active} done={Boolean(result)} elapsed={elapsed} />

      {/*
        ⚠ **결과가 있으면 입력 화면을 접는다.** 조사가 끝난 뒤에도 컨셉보드 전문과 표본
           슬라이더가 화면 절반을 차지하고 있었고, 사용자가 「개큰창 이거 왜 띄우는 거야,
           조사 결과 보고 싶은 사람한테」라고 했다. 지우지는 않는다 — 접는다.
      */}
      {result ? (
        <details className="mi-fold">
          <summary>
            다시 조사하기
            <span className="mi-fold__hint">보여준 설명을 고치거나 표본을 바꿔서 다시 돌려요</span>
          </summary>
          <div className="mi-fold__body">
            {boardError ? <Alert tone="danger">{boardError}</Alert> : (
              <ConceptBoardEditor board={board} onChange={setBoard}
                                  disabled={busy || active} preview={preview} />
            )}
            {ready ? (
              <>
                <SampleSizePicker value={sampleSize} onChange={setSampleSize}
                                  disabled={busy || active} />
                <div className="mi-page__actions">
                  {active ? <span className="mi-page__elapsed">{elapsed}초 경과</span> : null}
                  <Button onClick={trigger} disabled={!canRun}>
                    {active ? '인터뷰 중…' : '다시 인터뷰'}
                  </Button>
                </div>
              </>
            ) : null}
          </div>
        </details>
      ) : (
        <>
          <Card title="무엇을 보여줄까">
            {boardError ? <Alert tone="danger">{boardError}</Alert> : (
              <>
                <p className="mi-page__lead">
                  사업 검증에서 다듬어진 최종 컨셉이에요. 응답자에게 이 설명 하나를 보이고
                  정해진 9문항을 물어요.
                </p>
                <ConceptBoardEditor board={board} onChange={setBoard}
                                    disabled={busy || active} preview={preview} />
              </>
            )}
          </Card>

          {ready ? (
            <>
              <Card title="표본">
                <SampleSizePicker value={sampleSize} onChange={setSampleSize}
                                  disabled={busy || active} />
              </Card>
              <div className="mi-page__actions">
                {active ? <span className="mi-page__elapsed">{elapsed}초 경과</span> : null}
                <Button onClick={trigger} disabled={!canRun}>
                  {active ? '인터뷰 중…' : '인터뷰 실행'}
                </Button>
              </div>
            </>
          ) : null}
        </>
      )}

      {error ? <Alert tone="danger">{error}</Alert> : null}
      {run?.state === 'FAILED' && run?.errorCode === 'MARKET_INTERVIEW_NO_TARGET_SAMPLE' ? (
        <NoTargetSampleHelp />
      ) : run?.state === 'FAILED' && run?.errorCode ? (
        <Alert tone="danger">실행이 실패했어요 — {failureText(run.errorCode)}</Alert>
      ) : null}

      {result ? <InterviewResult result={result} /> : null}

      <InterviewFootnote result={result} />
    </section>
  );
}

function failureText(code) {
  if (code === 'TWIN_BANK_UNAVAILABLE') return '카드 뱅크가 서버에 붙어 있지 않아요(운영 설정 문제예요).';
  if (code === 'MARKET_INTERVIEW_NO_USABLE_RESPONSE') {
    return '답이 표본의 절반도 걷히지 않았어요. 줄여서 내보내지 않고 실패시켰어요 — 다시 실행해 보세요.';
  }
  if (code === 'TASK_TIMEOUT') return '예산 안에 끝나지 않았어요. 표본을 줄여 다시 해 보세요.';
  return code;
}

/**
 * 조건에 맞는 응답자가 0명이라 <b>응답을 걷기 전에</b> 멈춘 경우.
 *
 * <p>다시 눌러도 같은 결과다 — 할 일은 재시도가 아니라 <b>조건을 고치는 것</b>이라서
 * 다른 실패와 문구를 따로 쓴다. 그리고 <b>사용자를 탓하지 않는다</b>: 사용자는 사람 말로
 * 썼고, 그것을 패널 조건으로 옮긴 것은 기계이며 그 번역이 어긴 것이다.
 */
function NoTargetSampleHelp() {
  return (
    <Alert tone="danger">
      <strong>조사를 시작하지 않았어요 — 조건에 맞는 사람이 0명이에요.</strong>
      <p>
        헛돈이 나가지 않게 <strong>응답을 걷기 전에</strong> 멈췄어요. 다시 눌러도 같은
        결과라서 <strong>「누구를 위한 것인가」를 고치셔야 해요.</strong>
      </p>
      <p>
        패널에 <strong>기록돼 있지 않아 거를 수 없는 것</strong>들이 있고, 그런 말이 조건에
        들어가면 무엇을 해도 0명이 된다:
      </p>
      <ul>
        <li><strong>맞벌이 여부</strong> — 응답자 카드는 한 사람 것이라 배우자가 버는지 알 수 없어요.</li>
        <li><strong>자녀의 나이·학년</strong> — 「초등 저학년 자녀」는 거를 수 없어요.
          <em> 자녀가 있다는 것까지는 거를 수 있다.</em></li>
        <li><strong>취향·습관·관심사</strong> — 「요리를 자주 하는」·「환경에 관심 있는」 같은 칸이 없어요.</li>
      </ul>
      <p>
        거를 수 있는 것은 <strong>나이 · 성별 · 가구원 수 · 지역 · 개인 소득 · 직업 ·
        자녀 유무 · 가구 안 지위</strong> 여덟 가지다. 사업 검증에서 「누구를 위한 것인가」를
        이 말들로 다시 쓰시거나, <strong>조건을 비워 「누구나」로</strong> 두시면 실행돼요
        (그 경우 타겟의 반응이 아니라는 표시가 붙는다).
      </p>
    </Alert>
  );
}

/**
 * 이 모듈 안에서 사용자가 하는 일은 둘이다. 왼쪽 사이드바가 «어느 모듈인가»를 말하므로
 * 여기는 «그 모듈 안 어디인가»만 말한다.
 */
function InterviewSteps({ ready, active, done, elapsed }) {
  const steps = [
    { title: '보여줄 것 확인', state: ready ? 'done' : 'current', detail: null },
    {
      title: '인터뷰 실행',
      state: done ? 'done' : active ? 'current' : ready ? 'next' : 'waiting',
      detail: done ? '완료' : active ? `${elapsed}초` : null,
    },
  ];
  return (
    <ol className="mi-steps" aria-label="진행 단계">
      {steps.map((step, index) => (
        <li key={step.title} className="mi-steps__step" data-state={step.state}>
          <span className="mi-steps__dot" aria-hidden="true">
            {step.state === 'done' ? '✓' : index + 1}
          </span>
          <span className="mi-steps__title">{step.title}</span>
          {step.detail ? <span className="mi-steps__detail">{step.detail}</span> : null}
        </li>
      ))}
    </ol>
  );
}

function InterviewResult({ result }) {
  const { answered } = result;
  return (
    <div className="mi-result">
      {result.caveatsMissing ? (
        <Alert tone="danger">
          경계 문구가 결과에 실려오지 않았어요 — 이 결과를 그대로 인용하지 마세요.
        </Alert>
      ) : null}

      <Headline headline={result.headline} openQuestions={result.openQuestions}
                answered={answered} />

      <SampleHeader targeting={result.targeting} answered={answered} />


      {/*
        ⚠ 절 순서는 **9문항 순서가 아니라 「무엇을 할 수 있나」 순서**다.
           밖의 정성조사 실무가 보고서 실패의 첫 이유로 든 것이 「findings 를 데이터가
           드러낸 것이 아니라 질문지 순서로 조직하는 것」이었고, 이 화면이 그 모양이었다.
           그리고 JTBD 는 「anxiety(안 사는 이유)와 habit(지금 쓰는 것)부터 없애는 것이
           가장 빠른 승부」라고 순서까지 지정한다 — 그 둘이 여기 맨 위에 온다.
      */}
      <ThemeSection section={result.sections.find((s) => s.axis === 'BARRIER')}
                    answered={answered} showResolved layer="fact"
                    title="왜 안 산다고 하나요" />

      <section className="mi-panel">
        <h3 className="mi-panel__title">
          지금은 이렇게 해결해요 <LayerTag layer="fact" />
        </h3>
        <p className="mi-panel__lead">
          이겨야 할 상대는 경쟁 제품이 아니라 대개 여기 적힌 것들이에요.
        </p>
        {result.alternatives.length > 0 ? (
          <ul className="mi-alts">
            {result.alternatives.map((item) => (
              <li key={item.label}>
                <span className="mi-alts__label">{item.label}</span>
                <span className="mi-alts__count">{mentionText(item.mentionCount, answered)}</span>
              </li>
            ))}
          </ul>
        ) : (
          <p className="mi-panel__empty">
            분류된 답이 없어요
            {result.relevanceAnswered > 0
              ? ` — ${result.relevanceAnswered}명이 답은 썼지만 이름표에 안 붙었어요.`
              : '.'}
          </p>
        )}
      </section>

      {/*
        ★ `SUGGESTION` 축 절은 2026-08-15 까지 **화면에 아예 없었다.**
          `FACT_AXES` 가 LIKE·CONCERN 뿐이고 나머지는 축마다 따로 불렀는데 이 축만 빠져
          있었다. 그래서 「무엇을 해결하면 되나」의 주제도 인용문도 화면에 0개였다 —
          사업가가 가장 쓸 수 있는 절인데.
      */}
      <ThemeSection section={result.sections.find((s) => s.axis === 'SUGGESTION')}
                    answered={answered} layer="fact" title="바꿔 달라는 말" />

      <ThemeSection section={result.sections.find((s) => s.axis === 'LIKE')}
                    answered={answered} layer="fact" />
      <UsageScenePanel section={result.sections.find((s) => s.axis === 'USAGE_SCENE')}
                       answered={answered} />

      {/* ── 여기부터 접는다. **지우는 것이 아니라 접는 것이다** — 펴면 전부 그대로 있다. */}
      <details className="mi-fold">
        <summary>
          이 설명이 읽혔나 · 무엇이 다른가 · 걸리는 점
          <span className="mi-fold__hint">{comprehensionSummary(result.comprehension, answered)}</span>
        </summary>
        <div className="mi-fold__body">
          <ComprehensionPanel comprehension={result.comprehension} answered={answered} />
          <DifferentiationPanel counts={result.differentiation} answered={answered}
                                section={result.sections.find((s) => s.axis === 'DIFFERENTIATION')} />
          <ThemeSection section={result.sections.find((s) => s.axis === 'CONCERN')}
                        answered={answered} layer="fact" />
        </div>
      </details>

      <details className="mi-fold">
        <summary>
          대표 응답자
          <span className="mi-fold__hint">{result.interviews.length}장</span>
        </summary>
        <div className="mi-fold__body">
          <section className="mi-panel">
            <p className="mi-panel__lead">
              이해도가 갈리게 고르되, 못 채우면 남은 사람으로 메워요.
            </p>
            {result.interviews.length > 0 ? (
              result.interviews.map((card) => <InterviewCard key={card.key} card={card} />)
            ) : <p className="mi-panel__empty">보여 줄 응답을 고르지 못했어요.</p>}
          </section>
        </div>
      </details>

      <details className="mi-figures">
        <summary>전원 응답 열람 ({result.transcripts.length}명)</summary>
        <p className="mi-panel__lead">
          위의 모든 수는 여기 있는 답에서만 나왔어요.
        </p>
        {result.transcripts.map((card) => (
          <InterviewCard key={card.id} card={card}
                         badge={card.target ? '타겟' : '비타겟'} />
        ))}
      </details>

      <details className="mi-figures">
        <summary>실행 기록 보기</summary>
        <dl>
          <div><dt>뽑은 사람</dt><dd>{result.sampling.drawn}명 / 요청 {result.sampling.requested}명</dd></div>
          <div><dt>답한 사람</dt><dd>{answered}명</dd></div>
          <div><dt>형식 위반</dt><dd>{result.telemetry.formatViolations ?? '—'}건</dd></div>
          <div><dt>실패</dt><dd>{result.telemetry.failures ?? '—'}건</dd></div>
          <div><dt>모델</dt><dd>{result.telemetry.model ?? '—'}</dd></div>
          <div><dt>걸린 시간</dt><dd>{result.telemetry.seconds ?? '—'}초</dd></div>
        </dl>
        {result.sampling.hasShortCells ? (
          <p className="mi-figures__short">
            층이 얕아 목표를 못 채운 칸이 있다 — 그 층의 목소리는 이 결과에 덜 실렸다:{' '}
            {result.sampling.shortCells.map((cell) => `${cell.cell} ${cell.available}/${cell.quota}`).join(', ')}
          </p>
        ) : null}
      </details>
    </div>
  );
}

/**
 * 이해도 — <b>맨 위에 두는 것이 설계다.</b>
 *
 * 오해한 사람이 많으면 아래의 「끌리는 점」·「걸리는 점」은 이 제품에 대한 반응이 아니라
 * 응답자가 상상한 다른 물건에 대한 반응이다. 그 사실을 먼저 보지 않으면 결과를 통째로
 * 잘못 읽는다.
 */
/**
 * <b>이 조사가 센 것</b> — 화면 맨 위. 스크롤 전에 읽히는 유일한 절이다.
 *
 * <p>왜 생겼나: 이 화면은 <b>9문항 순서대로</b> 주제를 늘어놓고 있었고, 사용자가
 * 「정보가 안 들어온다, 뭐 어쩌라는 건지 1도 모르겠다」고 했다. 밖의 정성조사 실무도
 * 같은 것을 보고서가 망하는 첫 이유로 든다 — 「findings 를 데이터가 드러낸 것이 아니라
 * 질문지 순서로 조직하는 것」.
 *
 * <p>⚠ <b>여기서 판단을 쓰지 않는다.</b> 「가격을 내려라」도 「정보를 보여줘라」도 안 쓴다.
 * 대신 <b>나란히 놓는다</b> — 「안 사는 이유 1위는 가격인데 제안 1위는 가격 인하가 아니다」는
 * 세기만 한 것이고, 그 병치가 권고 없이 방향을 준다.
 */
function Headline({ headline, openQuestions, answered }) {
  if (!headline) return null;
  const { barrier, suggestion, alternative } = headline;
  return (
    <section className="mi-headline">
      <h3 className="mi-headline__title">이 조사가 센 것</h3>

      <p className="mi-headline__line">
        <strong>안 사는 이유</strong> 「{barrier.label}」{' '}
        <strong className="mi-headline__big">{mentionText(barrier.count, answered)}</strong>
        {/* ⚠ 타겟 분모를 «반드시» 병기한다. 타겟이 모자라면 비타겟으로 채우는 표집이라
            (SampleHeader 참조) 「20명 중 19명」이 타겟 수 행세를 한다. */}
        {barrier.targetCount === null ? null : (
          <span className="mi-headline__scope"> · 타겟 {barrier.targetCount}명</span>
        )}
        {barrier.resolved > 0 ? (
          <span className="mi-headline__resolved">
            {' '}→ <strong>{barrier.resolved}명</strong>은 해결되면 사겠대요
          </span>
        ) : null}
      </p>

      {suggestion === null ? null : (
        <p className="mi-headline__line">
          <strong>가장 많은 요청</strong> 「{suggestion.label}」{' '}
          {suggestion.linked ? (
            <>
              <strong>{suggestion.overlap}명</strong>이 위 이유와 함께 말했어요
            </>
          ) : (
            /* 겹침이 제안 인원의 절반에 못 미치면 연결을 «주장하지 않는다». */
            <>{mentionText(suggestion.count, answered)}</>
          )}
        </p>
      )}

      {alternative === null ? null : (
        <p className="mi-headline__line">
          {/* ⚠ 이름표 뒤에 조사를 붙이지 않는다 — AI 가 쓴 자유 문장이라 끝 글자를 알 수
              없고 「…한다«으로»」 같은 틀린 조사가 그대로 나간다. */}
          <strong>지금 쓰는 것</strong> 「{alternative.label}」{' '}
          {mentionText(alternative.count, answered)}
        </p>
      )}

      {openQuestions.length > 0 ? (
        <details className="mi-headline__open">
          <summary>아직 못 물어본 것 {openQuestions.length}가지</summary>
          <ul>{openQuestions.map((row) => <li key={row}>{row}</li>)}</ul>
        </details>
      ) : null}
    </section>
  );
}

function ComprehensionPanel({ comprehension, answered }) {
  const rows = COMPREHENSION_ORDER
    .map((key) => ({ key, count: comprehension[key], ...COMPREHENSION_VIEW[key] }))
    .filter((row) => row.count > 0);
  const width = (count) => (answered > 0 ? `${(count / answered) * 100}%` : '0%');

  return (
    <section className="mi-panel">
      <h3 className="mi-panel__title">이 설명이 읽혔나</h3>
      <p className="mi-panel__lead">
        제품을 본인 말로 다시 설명하게 하고 보여준 설명과 대조했어요.
        오해가 많으면 컨셉이 나쁜 게 아니라 <strong>설명이 나쁜 것</strong>이고, 아래의
        반응은 이 제품이 아니라 응답자가 상상한 물건에 대한 반응이에요.
      </p>

      <div className="mi-bar" role="img"
           aria-label={rows.map((row) => `${row.label} ${row.count}명`).join(', ')}>
        {rows.map((row) => (
          <span key={row.key} className={`mi-bar__part tone-${row.tone}`}
                style={{ width: width(row.count) }} />
        ))}
      </div>
      <ul className="mi-legend">
        {rows.map((row) => (
          <li key={row.key}>
            <span className={`mi-legend__dot tone-${row.tone}`} aria-hidden="true" />
            {row.label} <strong>{mentionText(row.count, answered)}</strong>
          </li>
        ))}
      </ul>

      {comprehension.misreadPoints.length > 0 ? (
        <div className="mi-misread">
          <p className="mi-misread__title">어디를 잘못 읽었나</p>
          <ul>{comprehension.misreadPoints.map((point) => <li key={point}>{point}</li>)}</ul>
        </div>
      ) : null}
    </section>
  );
}

/**
 * 한 축의 주제 목록. 막대 너비는 <b>답한 사람 수에 대한 비</b>이고 값이 아니다 —
 * 숫자는 언제나 「n명 중 x명」으로만 쓴다.
 *
 * <p>축이 6개로 늘어난 뒤로 <b>상위 몇 개만</b> 펼친다(정규화기의 `THEMES_VISIBLE`).
 * 나머지는 접되 개수를 밝힌다 — 접었다는 사실을 숨기면 「다 보여줬다」로 읽힌다.
 */
/**
 * 세 층 딱지. <b>띠(`LayerLegend`)를 절 순서에 묶어 두면 순서를 못 바꾼다</b> —
 * 그래서 띠 대신 절마다 붙는 작은 딱지로 바꿨다. 정직성 장치(「어디까지가 집계이고
 * 어디부터가 계산인가」)는 그대로 남고, 절 순서만 자유로워진다.
 */
function LayerTag({ layer }) {
  const meta = LAYERS.find((item) => item.key === layer);
  if (!meta) return null;
  return (
    <span className="mi-layertag" data-layer={layer} title={meta.detail}>{meta.title}</span>
  );
}

/** 접힌 서랍 머리에 남기는 한 줄. <b>접어도 결정적인 수는 밖에 남긴다.</b> */
function comprehensionSummary(comprehension, answered) {
  const off = comprehension.partial + comprehension.misunderstood;
  if (off === 0) return `설명은 읽혔다 — ${answered}명 전원이 제대로 이해`;
  return `제대로 ${comprehension.accurate} · 반만 ${comprehension.partial} · 오해 ${comprehension.misunderstood}`;
}

function ThemeSection({ section, answered, showResolved = false, title = null, layer = null }) {
  if (!section) return null;
  const hidden = section.hiddenThemes ?? [];
  const row = (theme) => (
    <li key={`${theme.axis}-${theme.label}`} className="mi-theme">
      <div className="mi-theme__head">
        <span className="mi-theme__label">{theme.label}</span>
        <span className="mi-theme__count">{mentionText(theme.mentionCount, answered)}</span>
      </div>
      <div className="mi-theme__track" aria-hidden="true">
        <span className={`mi-theme__fill tone-${section.tone}`}
              style={{ width: answered > 0 ? `${(theme.mentionCount / answered) * 100}%` : '0%' }} />
      </div>
      {theme.quote ? <p className="mi-theme__quote">&ldquo;{theme.quote}&rdquo;</p> : null}
      {showResolved && theme.resolvedCount > 0 ? (
        <p className="mi-theme__resolved">
          이 중 <strong>{theme.resolvedCount}명</strong>은 이게 해결되면 사겠대요
          <span className="mi-theme__aside"> — 물어본 게 아니라 스스로 말한 것만 셌어요</span>
        </p>
      ) : null}
    </li>
  );

  return (
    <section className="mi-panel">
      <h3 className="mi-panel__title">
        {title ?? section.title} {layer === null ? null : <LayerTag layer={layer} />}
      </h3>
      {section.themes.length > 0 ? (
        <>
          {section.thinCoverage ? (
            <p className="mi-panel__aside mi-panel__aside--warn">
              ⚠ 이 축은 <strong>{answered}명 중 {section.classified}명</strong>만 분류됐다.
              아래 수는 표본 전체가 아니라 <strong>분류에 성공한 사람들</strong>의 것이고,
              나머지 {answered - section.classified}명의 답은 어느 이름표에도 안 붙었다 —
              <strong>말을 안 한 게 아니에요.</strong> 전원 응답에서 확인하세요.
            </p>
          ) : null}
          <ul className="mi-themes">{section.themes.map(row)}</ul>
          {hidden.length > 0 ? (
            <details className="mi-more">
              <summary>나머지 {hidden.length}개 보기</summary>
              <ul className="mi-themes">{hidden.map(row)}</ul>
            </details>
          ) : null}
        </>
      ) : (
        <p className="mi-panel__empty">
          {section.empty}
          {answered > 0 ? ` (${answered}명이 이 문항에 답은 썼다.)` : ''}
        </p>
      )}
    </section>
  );
}


/** 표본 머리 — <b>분모를 갈라 적는다.</b> 타겟과 비타겟을 한 수로 합치면 대비가 사라진다. */
function SampleHeader({ targeting, answered }) {
  return (
    <section className="mi-panel mi-panel--flat">
      <p className="mi-panel__lead">
        <strong>{answered}명</strong>이 답했어요 — 타겟 {targeting.targetDrawn}명 ·
        비교용 {targeting.nonTargetDrawn}명
      </p>
      <details className="mi-inline">
        <summary>누구에게 물었는지 보기</summary>
        <p className="mi-panel__aside">
          <code>{targeting.criteriaText}</code>
        </p>
      </details>
      {targeting.targeted && targeting.targetDrawn === 0 ? (
        <Alert tone="danger">
          <strong>이 결과는 타겟의 반응이 아니에요</strong> — 조건에 맞는 사람이 <strong>0명</strong>
          이라 {targeting.nonTargetDrawn}명 전원을 조건 <em>밖</em>에서 뽑았다.
          아래의 모든 수는 <strong>대상이 아닌 사람들의 답</strong>이에요. 결론으로 쓰지 마세요.
          위의 조건 문구에서 <strong>0명짜리 조건</strong>을 찾아 사업 검증의
          「누구를 위한 것인가」를 고친 뒤 다시 돌려라.
        </Alert>
      ) : targeting.targetShort ? (
        <Alert tone="warning">
          <strong>타겟이 모자랐어요</strong> — 조건에 맞는 사람이 {targeting.targetDrawn}명뿐이라
          나머지 {targeting.nonTargetDrawn}명은 조건 밖에서 채웠어요
          (요청한 타겟 {targeting.targetRequested}명). 아래 수의 분모에 대상이 아닌 사람이
          섞여 있어요.
        </Alert>
      ) : null}
    </section>
  );
}

/** 차별성 — <b>「비슷하다」가 다수인 것 자체가 결과다.</b> 실패가 아니라 읽어야 할 신호다. */
function DifferentiationPanel({ counts, answered, section }) {
  const rows = DIFFERENTIATION_ORDER
    .map((key) => ({ key, count: counts[key], ...DIFFERENTIATION_VIEW[key] }))
    .filter((row) => row.count > 0);
  return (
    <section className="mi-panel">
      <h3 className="mi-panel__title">지금 있는 것들과 다른가</h3>
      <p className="mi-panel__lead">
        「다른 게 없으면 없다고 하셔도 된다」고 묻고 받은 답이에요.
        <strong> 「비슷하다」가 많은 것은 조사의 실패가 아니라 결과다.</strong>
      </p>
      <ul className="mi-legend">
        {rows.map((row) => (
          <li key={row.key}>
            <span className={`mi-legend__dot tone-${row.tone}`} aria-hidden="true" />
            {row.label} <strong>{mentionText(row.count, answered)}</strong>
          </li>
        ))}
      </ul>
      {section && section.themes.length > 0 ? (
        <ul className="mi-themes">
          {section.themes.map((theme) => (
            <li key={theme.label} className="mi-theme">
              <div className="mi-theme__head">
                <span className="mi-theme__label">{theme.label}</span>
                <span className="mi-theme__count">
                  {mentionText(theme.mentionCount, answered)}
                </span>
              </div>
              {theme.quote ? <p className="mi-theme__quote">&ldquo;{theme.quote}&rdquo;</p> : null}
            </li>
          ))}
        </ul>
      ) : null}
    </section>
  );
}

/** 사용 장면 — <b>상상 응답이라는 라벨을 뗄 수 없다.</b> 실제 행동이 아니다. */
function UsageScenePanel({ section, answered }) {
  return (
    <>
      <ThemeSection section={section} answered={answered} title="언제 쓸 것 같은가요" />
      {/* ⚠ 이 반 줄은 경계 표시라 지우지 않는다 — 이게 없으면 「퇴근 후 16명」이
          실제 사용 빈도로 읽힌다. 길이만 줄인다. */}
      <p className="mi-panel__aside mi-panel__aside--tight">상상해서 답한 거예요.</p>
    </>
  );
}




/**
 * 맨 아래 각주 — 일반 면책 + **서버가 값과 함께 실어 보낸 경계 문구**.
 *
 * ⚠ 이 저장소는 경계 표시를 지우지 않는다(CLAUDE.md 규칙 7). 문장은 서버가 만든 그대로다
 * (`ai/app/interview/caveats.py`). 빠졌으면 결과 위에서 크게 운다.
 */
export function InterviewFootnote({ result }) {
  const notes = result?.caveats ?? [];
  return (
    <footer className="mi-footnote">
      <p>
        이 결과는 실존 인물의 응답이 아니라 한국미디어패널조사(KISDI) 실측 프로파일로 만든
        디지털 트윈의 시뮬레이션이에요. 숫자는 <strong>이 표본에서 그 말을 한 사람 수</strong>일
        뿐이고 시장 규모도 구매율도 아니에요 — 백분율로 환산하지 마세요. 이 조사 형식은 외적
        타당성 시험을 거치지 않았고, 가격 수용도·지불의사는 답하지 않아요.
      </p>
      {notes.length > 0 ? (
        <details>
          <summary>이 결과를 읽는 법 {notes.length}가지</summary>
          <ul>{notes.map((note) => <li key={note}>{note}</li>)}</ul>
        </details>
      ) : null}
    </footer>
  );
}
