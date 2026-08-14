import { useMemo, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import MarketingCanvas from '../components/MarketingCanvas.jsx';
import MarketingContentList from '../components/MarketingContentList.jsx';
import MarketingCopyEditor from '../components/MarketingCopyEditor.jsx';
import MarketingRevisionList from '../components/MarketingRevisionList.jsx';
import MarketingSetupPanel from '../components/MarketingSetupPanel.jsx';
import MarketingSourceSummary from '../components/MarketingSourceSummary.jsx';
import MarketingStylePanel from '../components/MarketingStylePanel.jsx';
import useMarketingContent from '../hooks/useMarketingContent.js';
import { ASYNC_MESSAGES, createSetupModel, editableFromResult, latestRevision, legalSignals,
  marketingFailureMessage, sourceSummary, toCreateRequest } from '../model/marketingContentModel.js';
import { copyMarketingContent, downloadMarketingContent } from '../render/marketingRenderer.js';
import { isUserVisibleJobEvent, jobEventMessage } from '../../../shared/async-events/index.js';
import { ProjectStageHeader, ProjectWorkspace } from '../../../shared/ui/index.js';
import '../styles/marketing-content.css';

const STEP_ITEMS = [
  { id: 1, label: '컨셉 확인' },
  { id: 2, label: '생성 설정' },
  { id: 3, label: '결과 확인' },
];

export default function MarketingContentPage() {
  const { projectId } = useParams();
  const hook = useMarketingContent(projectId);

  const [step, setStep] = useState(1);
  const [pendingContentId, setPendingContentId] = useState(null);
  const [setup, setSetup] = useState(createSetupModel());
  const [draftState, setDraftState] = useState({ key: null, value: null });
  const [revisionType, setRevisionType] = useState('USER_EDITED');
  const [notice, setNotice] = useState('');
  const [style, setStyle] = useState({ theme: 'DARK', align: 'LEFT', accent: '#0f8878', scale: '1' });
  const effectiveSetup = setup.marketingSourceSnapshotId || !hook.source?.snapshotId
    ? setup : { ...setup, marketingSourceSnapshotId: hook.source.snapshotId };
  const activeRevision = latestRevision(hook.selected);
  const draftKey = hook.selected && activeRevision ? `${hook.selected.content.contentId}:${activeRevision.revisionNumber}` : null;
  const draft = draftState.key === draftKey ? draftState.value
    : (activeRevision ? editableFromResult(activeRevision.result, hook.selected.content.contentType) : null);
  const setDraft = (value) => setDraftState({ key: draftKey, value });

  const currentSource = useMemo(
    () => sourceSummary(hook.source?.snapshot),
    [hook.source?.snapshot],
  );

  const resultSource = useMemo(
    () =>
      sourceSummary(
        hook.selected?.sourceSnapshot ??
          hook.source?.snapshot,
      ),
    [
      hook.selected?.sourceSnapshot,
      hook.source?.snapshot,
    ],
  );

  const signals = useMemo(() => legalSignals(draft, resultSource), [draft, resultSource]);

  const selectedContentId = hook.selected?.content.contentId;
  const selectedStatus = hook.selected?.content.status;
  const editable = selectedStatus === 'COMPLETED';

  const generationStatus = hook.status === 'IDLE' ? selectedStatus : hook.status;
  const progressEvents = (hook.jobEvents?.events ?? []).filter(isUserVisibleJobEvent);
  const latestEvent = progressEvents.at(-1);
  const generationBusy = hook.active || hook.uploading;

  const generatedResultReady =
    step === 2 &&
    Boolean(pendingContentId) &&
    selectedContentId === pendingContentId &&
    selectedStatus === 'COMPLETED';

  const activeStep = generatedResultReady ? 3 : step;

  function moveToStep(nextStep) {
    /*
     * 자동 이동한 결과 화면에서 2단계로 돌아가면
     * 자동 이동 조건을 해제합니다.
     */
    if (nextStep !== 3) {
      setPendingContentId(null);
    }

    setStep(nextStep);
  }

  async function create() {
    setNotice('');
      try { const reference = effectiveSetup.referenceImage ? await hook.uploadReference(effectiveSetup.referenceImage): null;
        const detail = await hook.create(toCreateRequest(effectiveSetup, reference?.artifactId ?? null));
        setPendingContentId(
          detail?.content?.contentId ?? null,
        );
      } catch (error) {
        setPendingContentId(null);
        setNotice(error.message);
        }
  }

  async function openContent(contentId) {
    setNotice('');
    setPendingContentId(null);

    try {
      await hook.open(contentId);
      setStep(3);
    } catch (error) {
      setNotice(error.message);
    }
  }
  async function save() {
    if (!draft) {return;} setNotice('');
    try { await hook.save(draft, revisionType);
      setNotice('편집 내용을 새 수정 이력으로 저장했습니다.'); }
    catch (error) { setNotice(error.message); }
  }
  async function finalize() {
    if (!draft || signals.blocking.length) {return;} setNotice('');
    try { await hook.finalize(); setNotice('최종 저장본을 만들었습니다.'); }
    catch (error) { setNotice(error.message); }
  }
  async function copy() {
    try { await copyMarketingContent(draft); setNotice('클립보드에 복사했습니다.'); }
    catch { setNotice('브라우저의 클립보드 권한을 확인해 주세요.'); }
  }

  const showSetupProgress =
    generationBusy || Boolean(pendingContentId);

  const progressPanel = (
    <section
      className="mk-progress"
      aria-live="polite"
      aria-busy={generationBusy}
    >
      <div>
        <span>
          {hook.uploading
            ? '이미지 업로드 중'
            : hook.active
              ? '생성 중'
              : generationStatus === 'FAILED'
                ? '확인 필요'
                : '생성 완료'}
        </span>

        <strong>
          {hook.uploading
            ? '참고 상품 이미지를 업로드하고 있습니다.'
            : generationStatus === 'FAILED'
              ? marketingFailureMessage(
                  null,
                  latestEvent?.technicalCode,
                )
              : latestEvent
                ? jobEventMessage(latestEvent)
                : ASYNC_MESSAGES[generationStatus] ??
                  '콘텐츠 생성 요청을 처리하고 있습니다.'}
        </strong>
      </div>

      {progressEvents.length > 0 && (
        <ol>
          {progressEvents.map((event) => (
            <li
              key={event.sequence}
              data-active={event === latestEvent}
            >
              {jobEventMessage(event)}
            </li>
          ))}
        </ol>
      )}
    </section>
  );

  return (
    <ProjectWorkspace mode="review" className="mk-page">
      <ProjectStageHeader step={8} eyebrow="마케팅 전략" title="확정한 사업안을 고객에게 보여줄 콘텐츠로 만드세요"
        description="컨셉 확인, 생성 설정, 결과 확인의 세 단계로 문구와 시각 표현을 완성합니다."
        actions={<button type="button" onClick={() => void hook.refresh()}>새로고침</button>} />

      {hook.error && (
        <div
          className="mk-alert mk-alert--danger"
          role="alert"
        >
          {marketingFailureMessage(
            hook.error,
            latestEvent?.technicalCode,
          )}
        </div>
      )}

      {notice && (
        <div className="mk-alert" role="status">
          {notice}
        </div>
      )}

      {!hook.loading && !hook.source && (
        <section className="mk-not-ready">
          <div>
            <p>마케팅에 사용할 기획 자료가 필요합니다.</p>
            <h2>
              확정된 시장 분석 입력이
              없습니다.
            </h2>
            <span>
              사업안 선택과 검증 조건 확인을 완료하면
              마케팅에 사용할 기획 자료가 자동으로 준비됩니다.
              Market Result나 Finalized Planning은 필요하지 않습니다.
            </span>
          </div>

          <Link
            to={`/app/projects/${projectId}/concepts/compare`}
          >
            사업안 선택으로 이동
          </Link>
        </section>
      )}

      <MarketingContentList
        contents={hook.list}
        onOpen={(contentId) => {
          void openContent(contentId);
        }}
        selectedId={selectedContentId}
      />

      <nav
        className="mk-steps"
        aria-label="마케팅 콘텐츠 제작 단계"
      >
        <ol>
          {STEP_ITEMS.map((item) => {
            const available =
              item.id === 1 ||
              (item.id === 2 && Boolean(hook.source)) ||
              (item.id === 3 && Boolean(draft));

            const completed =
              item.id < activeStep ||
              (item.id === 2 && Boolean(draft));

            const state =
              activeStep === item.id
                ? 'current'
                : completed
                  ? 'complete'
                  : 'upcoming';

            return (
              <li key={item.id} data-state={state}>
                <button
                  type="button"
                  disabled={!available || generationBusy}
                  aria-current={
                    activeStep === item.id
                      ? 'step'
                      : undefined
                  }
                  onClick={() => moveToStep(item.id)}
                >
                  <span>
                    {completed &&
                    activeStep !== item.id
                      ? '✓'
                      : item.id}
                  </span>
                  <strong>{item.label}</strong>
                </button>
              </li>
            );
          })}
        </ol>
      </nav>

      {activeStep === 1 && (
        <section
          className="mk-step"
          aria-labelledby="mk-step-source-title"
        >
          <header className="mk-step__header">
            <div>
              <p>1단계</p>
              <h2 id="mk-step-source-title">
                컨셉 정보를 확인하세요
              </h2>
              <span>
                콘텐츠 생성에 사용될 고객, 가치 제안과
                법률 조건을 확인합니다.
              </span>
            </div>
          </header>

          <MarketingSourceSummary
            source={currentSource}
          />

          <div className="mk-step__actions">
            <span>
              컨셉 정보가 맞으면 다음 단계로
              이동하세요.
            </span>

            <button
              className="mk-primary"
              type="button"
              disabled={!hook.source}
              onClick={() => moveToStep(2)}
            >
              이 사업안으로 콘텐츠 만들기
            </button>
          </div>
        </section>
      )}

      {activeStep === 2 && (
        <section
          className="mk-step"
          aria-labelledby="mk-step-setup-title"
        >
          <header className="mk-step__header">
            <div>
              <p>2단계</p>
              <h2 id="mk-step-setup-title">
                만들 콘텐츠를 설정하세요
              </h2>
              <span>
                콘텐츠 유형과 채널, 목적, 톤 및 참고
                이미지를 입력합니다.
              </span>
            </div>

            <button
              type="button"
              disabled={generationBusy}
              onClick={() => moveToStep(1)}
            >
              사업안 다시 보기
            </button>
          </header>

          <MarketingSetupPanel
            value={effectiveSetup}
            onChange={setSetup}
            onSubmit={() => void create()}
            disabled={!hook.source}
            busy={generationBusy}
          />

          {showSetupProgress && progressPanel}
        </section>
      )}

      {activeStep === 3 && (
        <section
          className="mk-step mk-step--result"
          aria-labelledby="mk-step-result-title"
        >
          <header className="mk-step__header">
            <div>
              <p>3단계</p>
              <h2 id="mk-step-result-title">
                생성 결과를 확인하세요
              </h2>
              <span>
                생성된 이미지와 문구를 확인하고 필요한
                부분을 편집합니다.
              </span>
            </div>

            <button
              type="button"
              disabled={generationBusy}
              onClick={() => moveToStep(2)}
            >
              입력 수정하기
            </button>
          </header>

          {(generationBusy ||
            generationStatus === 'FAILED') &&
            progressPanel}

          <div className="mk-result-workspace">
            <main className="mk-result-workspace__preview">
              <MarketingCanvas
                result={draft}
                style={style}
                artifactUrl={
                  hook.selected?.artifactRefs?.at(-1)
                }
              />

              {hook.selected && (
                <MarketingRevisionList
                  revisions={hook.selected.revisions}
                  activeNumber={
                    hook.selected.content
                      .currentRevisionNumber
                  }
                />
              )}
            </main>

            <aside className="mk-result-workspace__editor">
              <MarketingStylePanel
                value={style}
                onChange={setStyle}
              />

              {draft && (
                <>
                  <section
                    className="mk-legal"
                    aria-labelledby="mk-legal-title"
                  >
                    <h2 id="mk-legal-title">
                      법률 표현 확인
                    </h2>

                    {signals.blocking.length > 0 ? (
                      <div
                        className="mk-legal__block"
                        role="alert"
                      >
                        <strong>저장 차단</strong>
                        <ul>
                          {signals.blocking.map(
                            (item) => (
                              <li key={item}>
                                {item}
                              </li>
                            ),
                          )}
                        </ul>
                      </div>
                    ) : (
                      <p className="mk-legal__ok">
                        차단되는 금지 표현이 없습니다.
                      </p>
                    )}

                    {signals.warnings.length > 0 && (
                      <div className="mk-legal__warning">
                        <strong>검토 경고</strong>
                        <ul>
                          {signals.warnings.map(
                            (item) => (
                              <li key={item}>
                                {item}
                              </li>
                            ),
                          )}
                        </ul>
                      </div>
                    )}
                  </section>

                  <MarketingCopyEditor
                    value={draft}
                    source={resultSource}
                    onChange={setDraft}
                    onRevisionType={setRevisionType}
                  />
                </>
              )}
            </aside>
          </div>

          {draft && (
            <footer
              className="mk-actions"
              aria-label="콘텐츠 작업"
            >
              <button
                type="button"
                onClick={() => void copy()}
              >
                복사
              </button>

              <button
                type="button"
                onClick={() =>
                  downloadMarketingContent(
                    draft,
                    hook.selected?.content.title,
                  )
                }
              >
                다운로드
              </button>

              <button
                type="button"
                disabled={
                  !editable ||
                  hook.saving ||
                  signals.blocking.length > 0
                }
                onClick={() => void save()}
              >
                {hook.saving
                  ? '저장 중…'
                  : '편집본 저장'}
              </button>

              <button
                type="button"
                disabled={!editable || hook.active}
                onClick={() =>
                  void hook.regenerate()
                }
              >
                새 초안 생성
              </button>

              <button
                className="mk-primary"
                type="button"
                disabled={
                  !editable ||
                  signals.blocking.length > 0
                }
                onClick={() => void finalize()}
              >
                최종 저장
              </button>
            </footer>
          )}
        </section>
      )}
    </ProjectWorkspace>
  );
}
