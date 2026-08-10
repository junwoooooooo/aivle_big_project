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
import '../styles/marketing-content.css';

export default function MarketingContentPage() {
  const { projectId } = useParams();
  const hook = useMarketingContent(projectId);
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
  const source = useMemo(() => sourceSummary(hook.selected?.sourceSnapshot ?? hook.source?.snapshot),
    [hook.selected?.sourceSnapshot, hook.source?.snapshot]);
  const signals = useMemo(() => legalSignals(draft, source), [draft, source]);
  const selectedStatus = hook.selected?.content.status;
  const editable = selectedStatus === 'COMPLETED';
  const generationStatus = hook.status === 'IDLE' ? selectedStatus : hook.status;
  const progressEvents = (hook.jobEvents?.events ?? []).filter(isUserVisibleJobEvent);
  const latestEvent = progressEvents.at(-1);

  async function create() {
    setNotice(''); try { await hook.create(toCreateRequest(effectiveSetup)); } catch (error) { setNotice(error.message); }
  }
  async function save() {
    if (!draft) return; setNotice('');
    try { await hook.save(draft, revisionType); setNotice('편집 내용을 새 revision으로 저장했습니다.'); }
    catch (error) { setNotice(error.message); }
  }
  async function finalize() {
    if (!draft || signals.blocking.length) return; setNotice('');
    try { await hook.finalize(); setNotice('최종 저장본을 만들었습니다.'); }
    catch (error) { setNotice(error.message); }
  }
  async function copy() {
    try { await copyMarketingContent(draft); setNotice('클립보드에 복사했습니다.'); }
    catch { setNotice('브라우저의 클립보드 권한을 확인해 주세요.'); }
  }

  return <div className="mk-page">
    <header className="mk-page__header"><div><p>Marketing Content</p><h1>확정된 Concept를 실제 콘텐츠로</h1>
      <span>선택 Concept, 최종 가설과 Legal Result를 고정한 Marketing Source로 생성·편집·저장합니다.</span></div>
      <button type="button" onClick={() => void hook.refresh()}>새로고침</button></header>
    {hook.error && <div className="mk-alert mk-alert--danger" role="alert">{marketingFailureMessage(hook.error, latestEvent?.technicalCode)}</div>}
    {notice && <div className="mk-alert" role="status">{notice}</div>}
    {!hook.loading && !hook.source && <section className="mk-not-ready"><div><p>Marketing Source 준비가 필요합니다.</p>
      <h2>확정된 Market Analysis Seed Snapshot이 없습니다.</h2>
      <span>Concept 선택과 가설 결정을 완료하면 Marketing Source가 자동으로 확정됩니다. Market Result나 Finalized Planning은 필요하지 않습니다.</span></div>
      <Link to={`/app/projects/${projectId}/concepts/compare`}>Concept 결정으로 이동</Link></section>}
    <MarketingContentList contents={hook.list} onOpen={(id) => void hook.open(id)} selectedId={hook.selected?.content.contentId} />
    <div className="mk-workspace">
      <aside className="mk-workspace__setup"><MarketingSourceSummary source={source} />
        <MarketingSetupPanel value={effectiveSetup} onChange={setSetup} onSubmit={() => void create()}
          disabled={!hook.source} busy={hook.active} /></aside>
      <main className="mk-workspace__canvas">
        <section className="mk-progress" aria-live="polite" aria-busy={hook.active}><div>
          <span>{hook.active ? '생성 중' : generationStatus === 'FAILED' ? '확인 필요' : 'Preview'}</span>
          <strong>{generationStatus === 'FAILED' ? marketingFailureMessage(null, latestEvent?.technicalCode)
            : latestEvent ? jobEventMessage(latestEvent) : ASYNC_MESSAGES[generationStatus] ?? '콘텐츠를 선택하거나 새로 생성하세요.'}</strong>
        </div>{progressEvents.length > 0 && <ol>{progressEvents.map((event) => <li key={event.sequence}
          data-active={event === latestEvent}>{jobEventMessage(event)}</li>)}</ol>}</section>
        <MarketingCanvas result={draft} style={style} />
        {hook.selected && <MarketingRevisionList revisions={hook.selected.revisions} activeNumber={hook.selected.content.currentRevisionNumber} />}
      </main>
      <aside className="mk-workspace__editor"><MarketingStylePanel value={style} onChange={setStyle} />{draft && <>
        <section className="mk-legal" aria-labelledby="mk-legal-title"><h2 id="mk-legal-title">법률 표현 확인</h2>
          {signals.blocking.length > 0 ? <div className="mk-legal__block" role="alert"><strong>저장 차단</strong><ul>{signals.blocking.map((item) => <li key={item}>{item}</li>)}</ul></div>
            : <p className="mk-legal__ok">차단되는 금지 표현이 없습니다.</p>}
          {signals.warnings.length > 0 && <div className="mk-legal__warning"><strong>검토 경고</strong><ul>{signals.warnings.map((item) => <li key={item}>{item}</li>)}</ul></div>}
        </section><MarketingCopyEditor value={draft} source={source} onChange={setDraft} onRevisionType={setRevisionType} />
      </>}</aside>
    </div>
    {draft && <footer className="mk-actions" aria-label="콘텐츠 작업"><button type="button" onClick={() => void copy()}>복사</button>
      <button type="button" onClick={() => downloadMarketingContent(draft, hook.selected?.content.title)}>다운로드</button>
      <button type="button" disabled={!editable || hook.saving || signals.blocking.length > 0} onClick={() => void save()}>{hook.saving ? '저장 중…' : '편집본 저장'}</button>
      <button type="button" disabled={!editable || hook.active} onClick={() => void hook.regenerate()}>새 초안 생성</button>
      <button className="mk-primary" type="button" disabled={!editable || signals.blocking.length > 0} onClick={() => void finalize()}>최종 저장</button></footer>}
  </div>;
}
