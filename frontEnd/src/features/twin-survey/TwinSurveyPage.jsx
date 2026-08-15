import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link, useOutletContext, useParams } from 'react-router-dom';

import { useApiClient } from '../../shared/api/ApiClientProvider.jsx';
import { Alert, Button, Card, LoadingState, ProjectStageHeader, ProjectWorkspace } from '../../shared/ui';
import { draftFailureText } from './draftFailureText.js';
import PairEditorDialog from './PairEditorDialog.jsx';
import SampleSizePicker from './SampleSizePicker.jsx';
import StimulusDraftPicker from './StimulusDraftPicker.jsx';
import StimulusEditor from './StimulusEditor.jsx';
import { createTwinSurveyApi } from './twinSurveyApi.js';
import { interviewLines } from './twinSurveyResult.js';
import { gateSurvey } from './taskTypeGate.js';
import useTwinSurveyLiveState from './useTwinSurveyPolling.js';
import './twin-survey.css';

/**
 * 견본 컨셉. **정본은 AI 서버**(`ai/app/research/pipeline.py` 의 `CONCEPTS`)이고 여기는
 * 이름표만 든다 — 시장조사 화면(`MarketResearchPage`)이 같은 셋을 같은 방식으로 쓴다.
 *
 * 확정된 컨셉이 있으면 서버가 그것을 쓰고 이 고름은 무시된다. 이 목록이 있는 이유는
 * 컨셉 파이프라인이 아직 안 찬 환경에서도 이 단계를 시연·시험할 수 있어야 하기 때문이다.
 */
const SAMPLE_CONCEPTS = [
  ['beauty-noshow', '미용실 노쇼 관리'],
  ['household-ledger', '가계부 앱'],
  ['pet-treat', '반려동물 수제 간식'],
];
const DEMO_MODE = import.meta.env.DEV && import.meta.env.VITE_TWIN_FIXTURE_MODE === 'true';

/**
 * 손으로 만드는 길에 쓰는 빈 쌍.
 *
 * ⚠ **첫 화면의 기본값이 아니다.** 이 빈 칸이 기본이던 것이 이 기능을 못 쓰게 만든
 * 원인이었다 — 속성명·양쪽 값·라벨·가격을 다 치고 「가격은 양쪽 같게, 속성은 하나만」이라는
 * 규칙까지 사용자가 지켜야 했다. 이제 초안이 그 자리를 채우고, 이 길은 남겨만 둔다.
 */
const BLANK_PAIR = {
  pairId: 'P1',
  X: { label: 'A안', attrs: { 형태: '' }, priceKrw: null },
  Y: { label: 'B안', attrs: { 형태: '' }, priceKrw: null },
};

/**
 * 패널 트윈 조사 화면.
 *
 * <p>이 화면이 파는 것은 <b>방향과 신뢰구간</b>이다. 크기·점유율·선택확률은 이 파이프라인이
 * 산출하지 않는다 — 없는 것이지 0 이 아니다. 그래서 두 가지를 화면이 지킨다:
 *
 * <ol>
 *   <li><b>경계 문구를 값과 같은 카드에 둔다.</b> 배너로 올리면 스크롤 밖으로 나가고,
 *       그러면 경계 없는 수치가 그대로 인용된다.</li>
 *   <li><b>「못 잼」과 「차이 없음」을 다르게 쓴다.</b> 흐리면 없는 결론이 생긴다.</li>
 * </ol>
 *
 * <p>실행 버튼은 <b>화면과 서버가 각자</b> 막는다. 화면 게이트({@code taskTypeGate.js})는
 * 서버({@code ai/app/twin/task_type.py})의 거울이고, 갈리면 서버가 이긴다.
 */
export default function TwinSurveyPage() {
  const { projectId } = useParams();
  const client = useApiClient();
  const { liveRevision = 0 } = useOutletContext() ?? {};
  const api = useMemo(() => createTwinSurveyApi(client, projectId), [client, projectId]);

  const [situation, setSituation] = useState('가게에서 하나를 고릅니다. 아래 두 상품이 있습니다.');
  const [pairs, setPairs] = useState([]);
  const [sampleSize, setSampleSize] = useState(100);
  const [draft, setDraft] = useState(null);
  const [drafting, setDrafting] = useState(false);
  const [draftError, setDraftError] = useState(null);
  const [draftSource, setDraftSource] = useState(null);
  const [conceptKey, setConceptKey] = useState(SAMPLE_CONCEPTS[0][0]);
  /** 편집 중인 쌍의 인덱스. null 이면 창이 닫혀 있다. */
  const [editing, setEditing] = useState(null);

  const savePair = useCallback((next) => {
    setPairs((current) => current.map((pair, index) => (index === editing ? next : pair)));
    setEditing(null);
  }, [editing]);

  const makeDraft = useCallback(async () => {
    setDrafting(true);
    setDraftError(null);
    try {
      await api.draftStimulus();
    } catch (failure) {
      setDraftError(draftFailureText(failure));
    }
  }, [api]);

  useEffect(() => {
    let alive = true;
    api.currentStimulusDraft().then((current) => {
      if (!alive || !current) return;
      setDraftSource(current.sourceConceptName || current.sourceConceptId || null);
      if (['QUEUED', 'READY', 'RUNNING'].includes(current.state)) setDrafting(true);
      else {
        setDrafting(false);
        if (current.state === 'SUCCEEDED' && current.result) {
          setDraft(current.result); setDraftError(null);
        } else if (current.state === 'FAILED') {
          setDraftError(draftFailureText({ code: current.errorCode }));
        }
      }
    }).catch((failure) => { if (alive) setDraftError(draftFailureText(failure)); });
    return () => { alive = false; };
  }, [api, liveRevision]);

  const useDraft = useCallback((draftSituation, chosen) => {
    setSituation(draftSituation);
    setPairs(chosen);
    setDraft(null);
  }, []);

  const load = useCallback(() => api.currentSurvey(), [api]);
  const start = useCallback(() => api.startSurvey(situation, pairs, sampleSize),
    [api, situation, pairs, sampleSize]);
  const { run, result, stale, error, busy, loading, active, elapsed, trigger } =
    useTwinSurveyLiveState(load, start, liveRevision);

  const gate = gateSurvey(pairs);
  const canRun = gate.canRun && situation.trim().length >= 5 && !busy && !active;

  if (loading) return <LoadingState label="트윈 조사 결과를 불러오는 중" />;

  return (
    <ProjectWorkspace as="section" mode="analyze" className="twin-page">
      <ProjectStageHeader step={7} eyebrow="가상 인터뷰" title="두 사업안에 대한 반응을 비교하세요"
        description="질문 준비, 대상 설정, 인터뷰 실행, 결과 확인 순서로 반응의 방향과 반복 패턴을 살펴봅니다." />
      {/* 모듈 이름은 셸(`ProjectLayout`)이 이미 그린다 — 여기서 다시 그리면 껍데기가 두 겹이다.
          그 자리에 산문 대신 «지금 어디까지 왔나»를 둔다. */}
      <TwinSteps pairCount={pairs.length} active={active} done={Boolean(result)} elapsed={elapsed} />

      <Card title="무엇을 비교할까">
        {pairs.length === 0 && !draft ? (
          /* 첫 화면은 버튼 하나다. 빈 표를 먼저 보이면 그 표를 채우는 것이 일이 된다. */
          <div className="twin-page__draft-start">
            <p>
              확정한 컨셉에서 <strong>비교할 두 안</strong>을 뽑아 준다.
              「가격은 양쪽 같게, 속성은 하나만」이라는 규칙은 초안이 지킨 채로 나온다.
            </p>
            {/* 확정된 컨셉이 있으면 서버가 그것을 쓴다 — 이 고름은 그때 무시된다. */}
            {DEMO_MODE ? <div className="twin-page__samples" role="group" aria-label="개발용 견본 컨셉 표시">
              {SAMPLE_CONCEPTS.map(([key, label]) => (
                <Button
                  key={key}
                  variant={key === conceptKey ? 'primary' : 'outline'}
                  aria-pressed={key === conceptKey}
                  disabled={drafting}
                  onClick={() => setConceptKey(key)}
                >
                  {label}
                </Button>
              ))}
            </div> : <p><strong>{draftSource || '현재 프로젝트에서 확정한 사업안'}</strong>을
              기준으로 초안을 만듭니다.</p>}
            <div className="twin-page__draft-actions">
              <Button onClick={makeDraft} disabled={drafting}>
                {drafting ? '초안 만드는 중…' : '자극 초안 만들기'}
              </Button>
              {/* 손으로 만드는 길은 남긴다 — 초안이 안 나오는 컨셉이 있다. */}
              <button type="button" className="twin-page__manual"
                      onClick={() => setPairs([BLANK_PAIR])} disabled={drafting}>
                직접 만들기
              </button>
            </div>
            {draftError ? <Alert tone="danger">{draftError}</Alert> : null}
          </div>
        ) : null}

        {draft ? (
          <StimulusDraftPicker draft={draft} disabled={busy || active} onUse={useDraft} />
        ) : null}

        {pairs.length > 0 ? (
          <StimulusEditor
            situation={situation}
            pairs={pairs}
            onSituationChange={setSituation}
            onEdit={setEditing}
            disabled={busy || active}
          />
        ) : null}
      </Card>

      <PairEditorDialog
        /* 쌍이 바뀌면 다시 마운트돼 그 쌍의 값으로 시작한다 — 창 안의 초기화를 effect 로
           하지 않는 이유다(부품 주석 참조). */
        key={editing}
        open={editing !== null}
        pair={editing === null ? null : pairs[editing]}
        onClose={() => setEditing(null)}
        onSave={savePair}
      />

      {pairs.length > 0 ? (
        <>
          <Card title="표본">
            <SampleSizePicker
              pairs={pairs}
              value={sampleSize}
              onChange={setSampleSize}
              disabled={busy || active}
            />
          </Card>

          <div className="twin-page__actions">
            {active ? <span className="twin-page__elapsed">{elapsed}초 경과</span> : null}
            <Button onClick={trigger} disabled={!canRun}>
              {active ? '조사 중…' : result ? '다시 조사' : '조사 실행'}
            </Button>
          </div>
        </>
      ) : null}

      {error ? <Alert tone="danger">{error}</Alert> : null}
      {stale ? <Alert tone="warning">선택한 사업안 또는 시장 입력이 바뀌었습니다. 최신 내용으로 다시 인터뷰해 주세요.</Alert> : null}
      {result && !stale ? <Link className="ui-button ui-button--primary" to={`/app/projects/${projectId}/marketing`}>다음 - 8. 마케팅 콘텐츠 제작</Link> : null}
      {run?.state === 'FAILED' && run?.errorCode ? (
        <Alert tone="danger">인터뷰를 완료하지 못했습니다. {failureText(run.errorCode)}</Alert>
      ) : null}

      {result ? <TwinResult result={result} /> : null}

      <TwinFootnote result={result} />
    </ProjectWorkspace>
  );
}

/**
 * 맨 아래 각주 — 일반 면책 + **결과가 있으면 쌍별 경계 문구까지**.
 *
 * ⚠ 이 저장소는 경계 표시를 지우지 않는다(CLAUDE.md 규칙 7). 카드 안에서 뺀 것은
 * **지운 게 아니라 중복을 걷어 여기로 모은 것**이다 — 쌍마다 같은 문장이 9줄씩 반복돼
 * 정작 인터뷰와 측정치를 밀어냈다. 문장은 서버가 값과 함께 실어 보낸 그대로다
 * (`ai/app/twin/caveats.py`). 빠진 쌍이 있으면 그 카드 안에서 크게 운다.
 */
export function TwinFootnote({ result }) {
  const notes = [...new Set((result?.pairs ?? []).flatMap((pair) => pair.caveats))];
  return (
    <footer className="twin-footnote">
      <p>
        이 결과는 실존 인물의 응답이 아니라 한국미디어패널조사(KISDI) 실측 프로파일로 만든
        디지털 트윈의 시뮬레이션이다. 답은 방향과 신뢰구간까지이며 크기·점유율·선택확률은
        내지 않는다. 검증 성적이 유지되는 명백한 우열형에서만 제공하고, 가격이 걸린
        질문(지불의사)은 실행 모델에 따라 방향이 뒤집혀 제공하지 않는다.
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

/**
 * 이 모듈 안에서 사용자가 하는 일은 둘이다. 왼쪽 사이드바가 «어느 모듈인가»를 말하므로
 * 여기는 «그 모듈 안 어디인가»만 말한다.
 *
 * 각 단계 밑의 한 줄은 장식이 아니라 **지금 값**이다 — 몇 쌍을 고랐는지, 몇 초 걸렸는지.
 * 단계 이름만 있으면 진행 표시는 읽을 이유가 없는 그림이 된다.
 */
function TwinSteps({ pairCount, active, done, elapsed }) {
  const chosen = pairCount > 0;
  const steps = [
    {
      title: '비교안 정하기',
      state: chosen ? 'done' : 'current',
      detail: chosen ? `${pairCount}쌍` : null,
    },
    {
      title: '조사 실행',
      state: done ? 'done' : active ? 'current' : chosen ? 'next' : 'waiting',
      detail: done ? '완료' : active ? `${elapsed}초` : null,
    },
  ];
  return (
    <ol className="twin-steps" aria-label="진행 단계">
      {steps.map((step, index) => (
        <li key={step.title} className="twin-steps__step" data-state={step.state}>
          <span className="twin-steps__dot" aria-hidden="true">
            {step.state === 'done' ? '✓' : index + 1}
          </span>
          <span className="twin-steps__title">{step.title}</span>
          {step.detail ? <span className="twin-steps__detail">{step.detail}</span> : null}
        </li>
      ))}
    </ol>
  );
}

function failureText(code) {
  if (code === 'TWIN_BANK_UNAVAILABLE') return '카드 뱅크가 서버에 붙어 있지 않다(운영 설정 문제다).';
  if (code === 'TWIN_TASK_TYPE_NOT_SERVICEABLE') return '성적이 없는 유형이라 서버가 거절했다.';
  if (code === 'TASK_TIMEOUT') return '예산 안에 끝나지 않았다. 표본을 줄여 다시 해 보라.';
  return code;
}

function TwinResult({ result }) {
  return (
    <div className="twin-result">
      {result.warnings.length > 0 ? (
        <Alert tone="danger">
          경계 문구가 빠진 쌍이 있다 — 이 결과를 그대로 인용하지 마라: {result.warnings.join(', ')}
        </Alert>
      ) : null}

      {result.pairs.map((pair) => (
        <PairPanel key={pair.pairId} pair={pair} result={result} />
      ))}
    </div>
  );
}

/**
 * 한 쌍의 결과 한 판. 목업(`persona_interview_mockup.html`)의 구조를 따른다 —
 * 머리글 · 판정 · 구성 막대 · 대표 인터뷰 · 경계.
 *
 * <b>수치를 접어 두는 이유</b>는 감추려는 것이 아니라 순서 때문이다. 「어느 쪽이 이겼나」와
 * 「사람들이 왜 그렇게 말했나」가 먼저 오고, Δ·MDE 는 그 판단을 확인하려는 사람이 편다.
 */
export function PairPanel({ pair, result }) {
  const c = pair.composition;
  return (
    <section className="twin-panel">
      <header className="twin-panel__head">
        <div>
          <p className="twin-panel__title">{pair.labels.X} ↔ {pair.labels.Y}</p>
          <p className="twin-panel__subtitle">{result.situation}</p>
        </div>
        <span className="twin-panel__done">{result.sampling.drawn}명 완료</span>
      </header>

      {/* 무엇을 비교했는가. 뷰모델이 `profiles` 를 정규화해 두고도 화면이 한 번도 안 그려서,
          결과가 「A안이 이겼다」라고만 말하고 A안이 무엇이었는지는 어디에도 없었다.
          `data-lead` 를 문자열로 못박는 이유: React 는 `data-*={false}` 를 아예 안 그려서
          「이긴 쪽이 아니다」와 「표시가 없다」가 같아진다. */}
      <dl className="twin-compare">
        <div data-lead={String(pair.measurable && pair.winner === 'X')}>
          <dt>{pair.labels.X}</dt>
          <dd>{pair.profiles.X || '자극 문장이 결과에 실려오지 않았다'}</dd>
        </div>
        <span className="twin-compare__vs" aria-hidden="true">vs</span>
        <div data-lead={String(pair.measurable && pair.winner === 'Y')}>
          <dt>{pair.labels.Y}</dt>
          <dd>{pair.profiles.Y || '자극 문장이 결과에 실려오지 않았다'}</dd>
        </div>
      </dl>

      <div className="twin-verdict">
        <div className="twin-verdict__line">
          <span className="twin-verdict__headline">
            {pair.measurable ? `${c.leadLabel} 우세` : '판정 불가 — 못 잼'}
          </span>
          <span className="twin-verdict__share">
            {c.leadPercent}% vs {c.trailPercent}% · 미결정 {c.undecidedPercent}%
          </span>
        </div>
        {/* 이긴 쪽 / 미결정·위치응답 / 진 쪽. 이 막대는 응답자 구성이지 점유율이 아니다. */}
        <div className="twin-bar" role="img"
             aria-label={`${c.leadLabel} ${c.lead}명, 미결정 ${c.undecided}명, ${c.trailLabel} ${c.trail}명`}>
          <span className="twin-bar__lead" style={{ width: `${c.leadPercent}%` }} />
          <span className="twin-bar__undecided" style={{ width: `${c.undecidedPercent}%` }} />
          <span className="twin-bar__trail" style={{ width: `${c.trailPercent}%` }} />
        </div>
        <p className="twin-verdict__reason">{pair.decisionReason}</p>
      </div>

      <p className="twin-panel__section">대표 응답자 인터뷰</p>
      {pair.interviews.length > 0 ? (
        pair.interviews.map((interview, index) => (
          <InterviewCard key={index} interview={interview} labels={pair.labels} />
        ))
      ) : (
        <p className="twin-panel__empty">인용할 응답을 고르지 못했다.</p>
      )}

      {/* ⚠ 경계 문구는 **페이지 각주로 모았다**(사용자 결정 2026-08-11). 쌍마다 같은 문장이
          9줄씩 반복돼 인터뷰와 측정치를 밀어냈다. 지우지 않고 중복만 걷어 각주로 내렸다 —
          **빠졌을 때는 여기서 크게 운다.** 빈 경계는 「경계 없음」이 아니라 「경계 소실」이다. */}
      {pair.caveatsMissing ? (
        <ul className="twin-panel__caveats is-missing">
          {pair.caveats.map((note) => <li key={note}>{note}</li>)}
        </ul>
      ) : null}

      <details className="twin-panel__figures">
        <summary>측정치 보기</summary>
        <dl>
          <div><dt>Δ(내용 성분)</dt><dd>{pair.deltaText}</dd></div>
          <div><dt>신뢰구간</dt><dd>{pair.intervalText ?? '—'}</dd></div>
          <div><dt>측정 한계(MDE)</dt><dd>{format3(pair.mde)}</dd></div>
          <div><dt>위치 성분</dt><dd>{format3(pair.positionComponent)}</dd></div>
          <div><dt>확정 응답자</dt><dd>{pair.nPaired}명 / {pair.nRespondents}명</dd></div>
          <div><dt>유형</dt><dd>{pair.taskTypeView.label}</dd></div>
        </dl>
        <ul className="twin-panel__classes">
          {pair.classes.map((item) => <li key={item.key}>{item.label} {item.count}명</li>)}
        </ul>
        {result.sampling.hasShortCells ? (
          <p className="twin-panel__short">층이 얕아 목표를 못 채운 셀이 있다 — 실효표본이 작다.</p>
        ) : null}
      </details>
    </section>
  );
}

function InterviewCard({ interview, labels }) {
  const { head, sub, badge } = interviewLines(interview, labels);
  const tone = interview.choiceView.tone;
  return (
    <article className="twin-interview">
      <div className="twin-interview__head">
        <span className={`twin-interview__avatar tone-${tone}`}>
          {interview.profile.age ?? '—'}
        </span>
        <div className="twin-interview__who">
          <p className="twin-interview__line">{head}</p>
          <p className="twin-interview__sub">{sub}</p>
        </div>
        <span className={`twin-interview__badge tone-${tone}`}>{badge}</span>
      </div>
      <p className="twin-interview__quote">&ldquo;{interview.quote}&rdquo;</p>
    </article>
  );
}

function format3(value) {
  return typeof value === 'number' ? value.toFixed(3) : '—';
}
