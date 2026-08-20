import { useMemo, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import MarketingCanvas from '../components/MarketingCanvas.jsx';
import MarketingContentList from '../components/MarketingContentList.jsx';
import MarketingCopyEditor from '../components/MarketingCopyEditor.jsx';
import MarketingRevisionList from '../components/MarketingRevisionList.jsx';
import MarketingSetupPanel from '../components/MarketingSetupPanel.jsx';
import MarketingStylePanel from '../components/MarketingStylePanel.jsx';
import MarketingStrategyPanel from '../components/MarketingStrategyPanel.jsx';
import useMarketingContent from '../hooks/useMarketingContent.js';
import useMarketingStrategy from '../hooks/useMarketingStrategy.js';
import { ASYNC_MESSAGES, createSetupModel, editableFromResult, latestRevision, legalSignals,
  marketingFailureMessage, sourceSummary, toCreateRequest } from '../model/marketingContentModel.js';
import { copyMarketingContent, downloadMarketingContent } from '../render/marketingRenderer.js';
import { isUserVisibleJobEvent, jobEventMessage } from '../../../shared/async-events/index.js';
import { ProjectStageHeader, ProjectWorkspace } from '../../../shared/ui/index.js';
import '../styles/marketing-content.css';

export default function MarketingContentPage() {
  const { projectId } = useParams();
  const hook = useMarketingContent(projectId);
  const strategy = useMarketingStrategy(projectId);

  const [step, setStep] = useState(2);
  const [strategyMode, setStrategyMode] = useState('NONE');
  const [pendingContentId, setPendingContentId] = useState(null);
  const [setup, setSetup] = useState(createSetupModel());
  const [draftState, setDraftState] = useState({ key: null, value: null });
  const [revisionType, setRevisionType] = useState('USER_EDITED');
  const [notice, setNotice] = useState('');
  const [downloading, setDownloading] = useState(false);
  const [style, setStyle] = useState({ theme: 'DARK', align: 'LEFT', accent: '#0f8878', scale: '1' });
  const effectiveSetup = { ...setup,
    marketingSourceSnapshotId: setup.marketingSourceSnapshotId || hook.source?.snapshotId || '',
    marketingStrategyReportId: strategyMode === 'CURRENT' && strategy.current
      ? strategy.view?.reportId ?? '' : '' };
  const activeRevision = latestRevision(hook.selected);
  const draftKey = hook.selected && activeRevision ? `${hook.selected.content.contentId}:${activeRevision.revisionNumber}` : null;
  const draft = draftState.key === draftKey ? draftState.value
    : (activeRevision ? editableFromResult(activeRevision.result, hook.selected.content.contentType) : null);
  const setDraft = (value) => setDraftState({ key: draftKey, value });

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
  const historical = selectedStatus === 'STALE';

  const generationStatus = hook.status === 'IDLE' ? selectedStatus : hook.status;
  const progressEvents = (hook.jobEvents?.events ?? []).filter(isUserVisibleJobEvent);
  const latestEvent = progressEvents.at(-1);
  const generationBusy = hook.active || hook.uploading;

  const generatedResultReady =
    step === 3 &&
    Boolean(pendingContentId) &&
    selectedContentId === pendingContentId &&
    selectedStatus === 'COMPLETED';

  const activeStep = generatedResultReady ? 4 : step;

  function moveToStep(nextStep) {
    /*
     * 자동 이동한 결과 화면에서 앞 단계로 돌아가면
     * 자동 이동 조건을 해제합니다.
     */
    if (nextStep !== 4) {
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
      setStep(4);
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
  async function download() {
    setNotice('');
    setDownloading(true);
    try {
      await downloadMarketingContent(draft, hook.imageUrl, style, hook.selected?.content.title);
      setNotice('이미지와 문구를 한 장의 PNG로 저장했습니다.');
    } catch (error) {
      setNotice(error?.message || '콘텐츠 이미지 다운로드에 실패했습니다.');
    } finally {
      setDownloading(false);
    }
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
      <ProjectStageHeader step={5} eyebrow="마케팅" title="전략을 세우거나 바로 콘텐츠를 제작하세요"
        description="마케팅 전략과 콘텐츠 제작은 독립 workspace입니다. 전략이 없어도 현재 사업안을 기준으로 콘텐츠를 만들 수 있습니다."
        actions={<button type="button" onClick={() => { void Promise.resolve(hook.refresh()).catch(() => {}); void Promise.resolve(strategy.refresh()).catch(() => {}); }}>새로고침</button>} />

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

      {!hook.loading && !hook.source && !strategy.ready && (
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
              현재 확정된 컨셉이 준비되면 마케팅 초안을 만들 수 있습니다.
            </span>
          </div>

          <Link
            to={`/app/projects/${projectId}/concepts/compare`}
          >
            사업안 선택으로 이동
          </Link>
        </section>
      )}

      <nav
        className="mk-workspace-tabs"
        aria-label="마케팅 작업 공간"
      >
        <button type="button" aria-pressed={activeStep === 2}
          onClick={() => moveToStep(2)}>
          <span>Strategy</span><strong>마케팅 전략</strong><small>분석 context로 실행 방향 설계</small>
        </button>
        <button type="button" aria-pressed={activeStep >= 3}
          onClick={() => moveToStep(draft ? 4 : 3)}>
          <span>Content</span><strong>콘텐츠 제작</strong><small>사업안 또는 최신 전략으로 초안 제작</small>
        </button>
      </nav>

      {activeStep === 2 && (
        <section className="mk-step" aria-labelledby="mk-step-strategy-title">
          <header className="mk-step__header"><div><p>Strategy workspace</p><h2 id="mk-step-strategy-title">마케팅 전략</h2><span>현재 사업안은 필수이고, 완료된 추가 분석은 존재하는 경우에만 전략 근거로 사용합니다.</span></div></header>
          <MarketingStrategyPanel strategy={strategy} onNext={() => { setStrategyMode('CURRENT'); moveToStep(3); }} />
        </section>
      )}

      {activeStep === 3 && (
        <section
          className="mk-step"
          aria-labelledby="mk-step-setup-title"
        >
          <header className="mk-step__header">
            <div>
              <p>Content workspace</p>
              <h2 id="mk-step-setup-title">
                만들 콘텐츠를 설정하세요
              </h2>
              <span>
                콘텐츠 유형과 채널, 목적, 톤 및 참고
                이미지를 입력합니다.
              </span>
            </div>

          </header>

          <section className="mk-content-basis" aria-labelledby="mk-content-basis-title">
            <header><div><p>콘텐츠 기준</p><h3 id="mk-content-basis-title">현재 사업안 ✓</h3></div><span>사용할 전략을 선택하세요.</span></header>
            <div role="radiogroup" aria-label="마케팅 전략 적용 방식">
              <label data-selected={strategyMode === 'NONE'}><input type="radio" name="strategy-mode" value="NONE" checked={strategyMode === 'NONE'} onChange={() => setStrategyMode('NONE')} />
                <span><strong>전략 없이 현재 사업안으로 제작</strong><small>현재 사업안과 법률 조건만 사용합니다.</small></span></label>
              <label data-selected={strategyMode === 'CURRENT'} data-disabled={!strategy.current}><input type="radio" name="strategy-mode" value="CURRENT" checked={strategyMode === 'CURRENT'} disabled={!strategy.current} onChange={() => setStrategyMode('CURRENT')} />
                <span><strong>최신 마케팅 전략 적용</strong><small>{strategy.current ? '현재 전략을 콘텐츠 생성 context로 사용합니다.' : '사용 가능한 최신 전략이 없습니다.'}</small></span></label>
            </div>
          </section>

          <MarketingSetupPanel
            value={effectiveSetup}
            onChange={setSetup}
            onSubmit={() => void create()}
            disabled={!hook.source}
            busy={generationBusy}
          />

          {!hook.source ? <div className="mk-alert mk-alert--warning" role="status">
            콘텐츠 생성용 사업안 snapshot이 아직 준비되지 않았습니다. 전략은 보존되며, 사업안 기준 저장을 완료한 뒤 이 단계에서 이어갈 수 있습니다.
          </div> : null}

          {showSetupProgress && progressPanel}

          <details className="mk-history">
            <summary>생성 이력 · {hook.list.length}개</summary>
            <MarketingContentList contents={hook.list} onOpen={(contentId) => { void openContent(contentId); }} selectedId={selectedContentId} />
          </details>
        </section>
      )}

      {activeStep === 4 && (
        <section
          className="mk-step mk-step--result"
          aria-labelledby="mk-step-result-title"
        >
          <header className="mk-step__header">
            <div>
              <p>4단계</p>
              <h2 id="mk-step-result-title">
                생성 결과를 확인하세요
              </h2>
              <span>
                AI가 생성한 이미지와 문구를 확인하고 필요한
                부분을 편집합니다.
              </span>
            </div>

            <button
              type="button"
              disabled={generationBusy}
              onClick={() => moveToStep(3)}
            >
              입력 수정하기
            </button>
          </header>

          <div className="mk-alert" role="note">
            AI가 현재 확정된 컨셉을 바탕으로 만든 초안입니다. 게시 전에 내용과 표현을 직접 확인하세요.
          </div>

          <details className="mk-history">
            <summary>생성 이력 · {hook.list.length}개</summary>
            <MarketingContentList contents={hook.list} onOpen={(contentId) => { void openContent(contentId); }} selectedId={selectedContentId} />
          </details>

          {(generationBusy ||
            generationStatus === 'FAILED') &&
            progressPanel}

          {selectedStatus === 'FAILED' && hook.selected?.content.retryable && (
            <div className="mk-step__actions">
              <span>같은 컨셉과 설정으로 기술적 실패를 다시 시도합니다.</span>
              <button className="mk-primary" type="button" disabled={hook.active}
                onClick={() => void hook.retry()}>
                다시 시도
              </button>
            </div>
          )}

          {historical && (
            <div className="mk-alert mk-alert--warning" role="status">
              이전 컨셉을 기준으로 만든 결과입니다. 과거 초안은 그대로 열람할 수 있으며,
              현재 컨셉으로 사용하려면 새 초안을 만들어 주세요.
            </div>
          )}

          <div className="mk-result-workspace">
            <main className="mk-result-workspace__preview">
              <MarketingCanvas
                result={draft}
                style={style}
                artifactUrl={hook.imageUrl}
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
                disabled={!hook.imageUrl || downloading}
                onClick={() => void download()}
              >
                {downloading ? 'PNG 만드는 중…' : '콘텐츠 PNG 다운로드'}
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
                disabled={(!editable && selectedStatus !== 'FINALIZED' && !historical) || hook.active}
                onClick={() =>
                  void hook.regenerate()
                }
              >
                {historical ? '현재 컨셉으로 새 초안 만들기' : '다른 초안 만들기'}
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
